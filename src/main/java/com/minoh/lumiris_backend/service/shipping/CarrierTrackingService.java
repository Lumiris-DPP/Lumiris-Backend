package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Suivi transporteur réel. Jusqu'ici le suivi était un numéro saisi une fois, jamais rafraîchi, et
// la livraison était PRÉSUMÉE à J+7 : les fonds partaient au vendeur sur une supposition et la
// fenêtre de rétractation courait depuis une date inventée. Le webhook remplace la supposition par
// le fait.
//
// Le balayage horaire reste en place et reste indispensable : un colis remis en main propre, un
// transporteur hors agrégateur ou un webhook jamais délivré ne doivent pas laisser une commande
// ouverte à vie.
@Service
@RequiredArgsConstructor
public class CarrierTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CarrierTrackingService.class);

    private final ShippingProvider provider;
    private final MarketplaceOrderRepository orderRepository;
    private final OrderLifecycleService lifecycleService;

    @Transactional
    public void handle(String payload, String signature) {
        Optional<CarrierEvent> event = provider.readWebhook(payload, signature);
        if (event.isEmpty()) {
            return;
        }
        CarrierEvent carrierEvent = event.get();
        // Un colis inconnu n'est pas une erreur : le même compte agrégateur peut servir à des
        // expéditions hors Lumiris. On l'ignore silencieusement plutôt que de renvoyer un échec
        // qui déclencherait des rejeux indéfinis côté prestataire.
        MarketplaceOrder order = orderRepository.findByCarrierParcelId(carrierEvent.parcelId()).orElse(null);
        if (order == null) {
            log.debug("Événement transporteur ignoré : colis {} inconnu", carrierEvent.parcelId());
            return;
        }
        lifecycleService.applyTrackingUpdate(order, carrierEvent.status(), carrierEvent.label(),
                carrierEvent.trackingNumber(), carrierEvent.trackingUrl());
    }
}
