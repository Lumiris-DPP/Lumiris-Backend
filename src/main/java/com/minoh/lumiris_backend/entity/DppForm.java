package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dpp_forms")
@Getter
@Setter
public class DppForm extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "internal_reference")
    private String internalReference;

    @Column(name = "retail_price")
    private BigDecimal retailPrice;

    @Column(name = "currency", nullable = false)
    private String currency = "EUR";

    @Column(name = "status", nullable = false)
    private String status = "Draft";
}
