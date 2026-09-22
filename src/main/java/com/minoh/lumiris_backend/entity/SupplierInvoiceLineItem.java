package com.minoh.lumiris_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_invoice_line_items")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_invoice_id", nullable = false)
    private SupplierInvoice supplierInvoice;

    // Valeur de l'enum front Fiber (wool, linen, cotton, ...), ou null si non reconnue dans le
    // libellé — une fibre non détectée n'est pas assimilée à "other" (incertain != autre).
    @Column
    private String fiber;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false)
    private String unit;
}
