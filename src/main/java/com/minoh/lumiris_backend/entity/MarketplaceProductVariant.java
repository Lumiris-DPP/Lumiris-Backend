package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Déclinaison vendable d'une annonce (taille, couleur). Porte le SEUL stock du système : c'est
// elle qu'on réserve au checkout et qu'on remet en rayon au remboursement. Elle ne porte jamais de
// prix — une annonce a un prix unique, adossé à un seul Price Stripe.
@Entity
@Table(name = "marketplace_product_variants")
@Getter
@Setter
public class MarketplaceProductVariant extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketplaceProduct product;

    @Column(name = "size_label", length = 40)
    private String sizeLabel;

    @Column(name = "color_label", length = 40)
    private String colorLabel;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(length = 64)
    private String sku;

    @Column(nullable = false)
    private int stock = 0;

    @Column(nullable = false)
    private int position = 0;

    // Version de ligne, incrémentée par chaque écriture de stock. Le formulaire artisan renvoie
    // celle qu'il a lue : si une vente est passée entre-temps, la sauvegarde est refusée en conflit
    // au lieu d'écraser le stock réel. Volontairement pas @Version — l'incrément appartient aux
    // requêtes de stock atomiques, pas au cycle de vie Hibernate.
    @Column(nullable = false)
    private long version;
}
