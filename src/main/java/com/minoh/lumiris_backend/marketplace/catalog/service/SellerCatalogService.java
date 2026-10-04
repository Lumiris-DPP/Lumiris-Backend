package com.minoh.lumiris_backend.marketplace.catalog.service;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.ConvertDppRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.mapper.MarketplaceProductMapper;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.catalog.service.stripe.MarketplaceStripeService;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Gère les annonces appartenant à l'artisan connecté.
@Service
@RequiredArgsConstructor
public class SellerCatalogService {

    private static final int MIN_SELLABLE_PRICE_CENTS = 50;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DppFormRepository dppFormRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MarketplaceProductMapper mapper;
    private final MarketplaceItemAssembler assembler;
    private final MarketplaceVariantService variantService;
    private final MarketplaceStripeService marketplaceStripeService;

    // Liste les annonces de l'artisan connecté.
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> listMine(String email) {
        User artisan = requireArtisan(email);
        Map<UUID, Long> salesByProduct = orderRepository
                .salesCountByProduct(artisan.getId(), OrderStatus.sold())
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return assembler.toResponses(
                assembler.fetch(productRepository.findScoredByArtisanProfileId(artisan.getArtisanProfile().getId())),
                salesByProduct);
    }

    // Retrouve une annonce appartenant à l'artisan connecté.
    @Transactional(readOnly = true)
    public MarketplaceItemResponse getMine(String email, UUID id) {
        User artisan = requireArtisan(email);
        return toItem(requireOwnedProduct(id, artisan));
    }

    // Met en vente une pièce depuis son passeport.
    @Transactional
    public MarketplaceItemResponse convertFromDpp(String email, UUID dppFormId, ConvertDppRequest req) {
        User artisan = requireArtisan(email);

        requireSellingSubscription(artisan);
        DppForm dpp = resolveOwnedDpp(dppFormId, artisan);
        if (dpp == null) {
            throw new ResourceNotFoundException("DPP introuvable");
        }

        if (dpp.getStatus() != DppStatus.VALID) {
            throw new BillingValidationException("Seules les pièces à passeport LUMIRIS valide sont vendables.");
        }
        MarketplaceProduct product = productRepository.findByDppFormId(dppFormId).orElseGet(MarketplaceProduct::new);
        if (product.getId() == null) {
            product.setArtisanProfile(artisan.getArtisanProfile());
            product.setDppForm(dpp);
        }
        product.setName(dpp.getProductName());
        product.setCategory(dpp.getProductCategory());
        product.setOriginCountry(dpp.getOriginCountry());
        product.setDescription(req.description() != null ? req.description() : dpp.getProductDescription());
        product.setMaterial(req.material());
        product.setPriceCents(req.priceCents());
        product.setCurrency(req.currency() != null && !req.currency().isBlank()
                ? req.currency().toUpperCase(Locale.ROOT) : "EUR");
        product.setExternalOrderUrl(req.externalOrderUrl());
        product.setPhotoUrl(req.photoUrl());
        product.setShippingCents(req.shippingCents() != null && req.shippingCents() >= 0 ? req.shippingCents() : 0);
        product.setReturnPolicy(req.returnPolicy());
        product.setPreparationDays(req.preparationDays() != null ? req.preparationDays() : 0);
        product.setWeightGrams(req.weightGrams() != null ? Math.max(0, req.weightGrams()) : 0);
        product.setStatus(req.status() != null ? req.status() : MarketplaceProductStatus.PUBLISHED);
        assertSellablePrice(product);
        MarketplaceProduct saved = productRepository.save(product);

        if (req.variants() != null && !req.variants().isEmpty()) {
            variantService.replaceVariantsAndSizeGuide(saved, req.variants(), req.sizeGuide());
        } else {
            variantService.seedDefaultVariant(saved, req.stock() != null ? req.stock() : dpp.getQuantity());
        }

        marketplaceStripeService.ensureStripeProduct(saved);
        return toItem(saved);
    }

    // Modifie l'annonce et ses déclinaisons dans la même transaction.
    @Transactional
    public MarketplaceItemResponse update(String email, UUID id, UpdateProductRequest req) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);
        mapper.applyUpdate(product, req, resolveOwnedDpp(req.dppFormId(), artisan));
        assertSellablePrice(product);
        MarketplaceProduct saved = productRepository.save(product);
        variantService.replaceVariantsAndSizeGuide(saved, req.variants(), req.sizeGuide());
        return toItem(saved);
    }

    // Supprime une annonce sans commande appartenant à l'atelier.
    @Transactional
    public void delete(String email, UUID id) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);

        if (orderRepository.existsByProduct_Id(id)) {
            throw new ConflictException(
                    "Cette annonce a déjà des commandes : archivez-la (elle sera retirée de la vente) "
                            + "plutôt que de la supprimer, pour préserver l'historique d'achat.");
        }
        productRepository.delete(product);
    }

    // Refuse un prix inférieur au minimum accepté au paiement.
    private static void assertSellablePrice(MarketplaceProduct product) {
        if (product.getStatus() == MarketplaceProductStatus.PUBLISHED
                && product.getPriceCents() < MIN_SELLABLE_PRICE_CENTS) {
            throw new BillingValidationException(
                    "Un produit en vente doit coûter au moins 0,50 € (prix minimum encaissable).");
        }
    }

    // Vérifie que l'abonnement de l'atelier permet la vente.
    private void requireSellingSubscription(User artisan) {
        boolean active = subscriptionRepository.findByUserId(artisan.getId())
                .filter(UserSubscription::isActive)
                .isPresent();
        if (!active) {
            throw new BillingValidationException(
                    "Un abonnement ATELIER actif est requis pour mettre une pièce en vente.");
        }
    }

    // Présente l'annonce avec les données du catalogue.
    private MarketplaceItemResponse toItem(MarketplaceProduct p) {
        IrisScore score = p.getDppForm() != null
                ? irisScoreRepository.findByDppFormId(p.getDppForm().getId()).orElse(null)
                : null;
        return assembler.toResponse(new ScoredProduct(p, score));
    }

    // Vérifie que l'utilisateur connecté possède le rôle artisan.
    private User requireArtisan(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ARTISAN || user.getArtisanProfile() == null) {
            throw new RoleNotAllowedException("Un profil artisan est requis pour gérer un catalogue produit.");
        }
        return user;
    }

    // Vérifie que l'annonce appartient à l'atelier connecté.
    private MarketplaceProduct requireOwnedProduct(UUID id, User artisan) {
        return productRepository.findByIdAndArtisanProfileId(id, artisan.getArtisanProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
    }

    // Retrouve le passeport désigné et vérifie son propriétaire.
    private DppForm resolveOwnedDpp(UUID dppFormId, User artisan) {
        if (dppFormId == null) {
            return null;
        }
        DppForm dpp = dppFormRepository.findById(dppFormId)
                .orElseThrow(() -> new ResourceNotFoundException("DPP introuvable"));
        if (!dpp.getUser().getId().equals(artisan.getId())) {
            throw new ResourceNotFoundException("DPP introuvable");
        }
        return dpp;
    }
}
