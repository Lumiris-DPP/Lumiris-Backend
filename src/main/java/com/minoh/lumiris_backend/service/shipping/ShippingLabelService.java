package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.config.ShippingProperties;
import com.minoh.lumiris_backend.dto.out.ShippingLabelResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Étiquette d'expédition en un clic. Remplace trois allers-retours par colis — recopier l'adresse
// chez le transporteur, imprimer, revenir saisir le suivi — par une action unique : le bordereau
// est fabriqué depuis l'adresse DÉJÀ stockée sur la commande, le suivi revient rempli, et la
// commande passe expédiée sans aucune saisie.
//
// Rien n'est irréversible pour l'atelier : si l'agrégateur n'est pas configuré, ou refuse, la
// saisie manuelle du suivi reste ouverte et reste de toute façon le chemin d'une remise en main
// propre ou d'un transporteur hors agrégateur.
@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private static final Logger log = LoggerFactory.getLogger(ShippingLabelService.class);

    private final ShippingProvider provider;
    private final ShippingProperties properties;
    private final MarketplaceOrderRepository orderRepository;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final OrderLifecycleService lifecycleService;

    // Ce que l'UI vendeur doit savoir AVANT de proposer le bouton : l'intégration est-elle active,
    // et l'atelier a-t-il de quoi expédier. Sans cet état, l'atelier découvrirait qu'il lui manque
    // une adresse au moment précis où il essaie d'imprimer.
    @Transactional(readOnly = true)
    public ShippingLabelResponse.Availability availability(String sellerEmail) {
        if (!provider.configured()) {
            return ShippingLabelResponse.Availability.unavailable();
        }
        User seller = userRepository.getByEmail(sellerEmail);
        boolean addressReady = artisanProfileRepository.findByUser(seller)
                .map(ArtisanProfile::hasShipFromAddress)
                .orElse(false);
        return new ShippingLabelResponse.Availability(true, provider.name(), addressReady);
    }

    @Transactional
    public ShippingLabelResponse generate(String sellerEmail, UUID orderId) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = lifecycleService.requireSellerOrder(seller, orderId);
        lifecycleService.requireShippable(order);

        if (!provider.configured()) {
            throw new BillingValidationException(
                    "L'impression d'étiquette n'est pas activée — saisis le suivi à la main.");
        }
        ArtisanProfile profile = artisanProfileRepository.findByUser(seller)
                .filter(ArtisanProfile::hasShipFromAddress)
                .orElseThrow(() -> new BillingValidationException(
                        "Renseigne l'adresse d'enlèvement de ton atelier avant d'imprimer une étiquette."));
        requireDeliverable(order);

        ShippingLabel label = provider.createLabel(parcelFor(order, profile));
        StoredFile pdf = storageService.store(label.pdf(),
                "bordereau-" + shortReference(order) + ".pdf", "application/pdf");

        order.setCarrier(label.carrier());
        order.setTrackingNumber(label.trackingNumber());
        order.setTrackingUrl(label.trackingUrl());
        order.setCarrierParcelId(label.parcelId());
        order.setShippingLabelFile(pdf);
        orderRepository.save(order);

        lifecycleService.recordLabelGenerated(order, seller);
        lifecycleService.markShipped(order, seller);
        log.info("Bordereau {} généré pour la commande {} ({})",
                label.parcelId(), order.getId(), provider.name());

        return new ShippingLabelResponse(
                storageService.getPresignedUrl(pdf.getId()),
                label.carrier(),
                label.trackingNumber(),
                label.trackingUrl());
    }

    // L'adresse de livraison est saisie au checkout et validée à ce moment-là ; une commande
    // antérieure à cette validation peut néanmoins être incomplète. Autant le dire ici plutôt que
    // de laisser le transporteur renvoyer une erreur illisible.
    private void requireDeliverable(MarketplaceOrder order) {
        if (order.getShipToLine1() == null || order.getShipToPostalCode() == null
                || order.getShipToCity() == null) {
            throw new BillingValidationException(
                    "L'adresse de livraison de cette commande est incomplète — saisis le suivi à la main.");
        }
    }

    private ParcelRequest parcelFor(MarketplaceOrder order, ArtisanProfile profile) {
        MarketplaceProduct product = order.getProduct();
        int declaredWeight = product != null ? product.getWeightGrams() * Math.max(1, order.getQuantity()) : 0;
        return new ParcelRequest(
                new ParcelRequest.Address(
                        profile.getDisplayName(),
                        profile.getShipFromLine1(),
                        profile.getShipFromLine2(),
                        profile.getShipFromPostalCode(),
                        profile.getShipFromCity(),
                        profile.getShipFromCountry(),
                        profile.getShipFromPhone()),
                new ParcelRequest.Address(
                        order.getShipToName(),
                        order.getShipToLine1(),
                        order.getShipToLine2(),
                        order.getShipToPostalCode(),
                        order.getShipToCity(),
                        order.getShipToCountry(),
                        order.getShipToPhone()),
                order.getBuyer() != null ? order.getBuyer().getEmail() : null,
                properties.weightGramsOr(declaredWeight),
                shortReference(order));
    }

    // Le numéro de facture est la référence que l'atelier et l'acheteur partagent déjà ; à défaut
    // (facture émise au paiement seulement), un préfixe de l'identifiant de commande suffit à
    // retrouver le colis côté agrégateur.
    private static String shortReference(MarketplaceOrder order) {
        return order.getInvoiceNumber() != null
                ? order.getInvoiceNumber()
                : order.getId().toString().substring(0, 8).toUpperCase();
    }
}
