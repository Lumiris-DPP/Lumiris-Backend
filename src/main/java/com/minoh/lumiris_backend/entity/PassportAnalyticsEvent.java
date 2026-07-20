package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// No @LastModifiedDate / user reference on purpose: events are immutable, anonymous facts.
// Not to be confused with DppEvent (supply-chain custody history, e.g. MANUFACTURER->CONSUMER).
@Entity
@Table(name = "passport_analytics_events")
public class PassportAnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PassportAnalyticsEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public PassportAnalyticsEvent() {}

    public PassportAnalyticsEvent(DppForm dppForm, PassportAnalyticsEventType eventType) {
        this.dppForm = dppForm;
        this.eventType = eventType;
    }

    public UUID getId() { return id; }
    public DppForm getDppForm() { return dppForm; }
    public PassportAnalyticsEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
}
