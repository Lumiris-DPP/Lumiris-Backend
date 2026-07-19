package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "affiliate_clicks")
@Getter
public class AffiliateClick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private String source;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "dpp_public_code")
    private String dppPublicCode;

    private String referrer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AffiliateClick() {}

    public AffiliateClick(UUID productId, String source, String targetUrl, String dppPublicCode, String referrer) {
        this.productId = productId;
        this.source = source;
        this.targetUrl = targetUrl;
        this.dppPublicCode = dppPublicCode;
        this.referrer = referrer;
        this.createdAt = Instant.now();
    }
}
