package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// Trace immuable (append-only, garantie par trigger) de chaque décision de tri
// suggest/search : entrée, ordre retenu, et la certification que la commission
// n'a jamais été un critère. Immutable : aucun setter, aucune UPDATE possible.
@Entity
@Table(name = "marketplace_decision_logs")
@Getter
public class MarketplaceDecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String context;

    @Column(name = "sort_key", nullable = false)
    private String sortKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String request;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "commission_considered", nullable = false)
    private boolean commissionConsidered;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MarketplaceDecisionLog() {}

    public MarketplaceDecisionLog(String context, String sortKey, String request, String result) {
        this.context = context;
        this.sortKey = sortKey;
        this.request = request;
        this.result = result;
        this.commissionConsidered = false; // invariant : le tri ne dépend jamais de la commission
        this.createdAt = Instant.now();
    }
}
