package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Mesure relevée par l'atelier pour une taille donnée d'une annonce — un artisan ne suit aucun
// standard de taille industriel, ce sont ses cotes réelles que l'acheteur compare à une pièce
// qu'il possède déjà. Étiquette libre, valeur en millimètres entiers.
@Entity
@Table(name = "marketplace_size_measurements")
@Getter
@Setter
public class MarketplaceSizeMeasurement extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketplaceProduct product;

    @Column(name = "size_label", nullable = false, length = 40)
    private String sizeLabel;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "value_mm", nullable = false)
    private int valueMm;

    @Column(nullable = false)
    private int position = 0;
}
