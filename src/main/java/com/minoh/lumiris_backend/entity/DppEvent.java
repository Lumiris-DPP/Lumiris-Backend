package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dpp_events")
@Getter
public class DppEvent extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false, updatable = false)
    private DppForm dppForm;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false)
    private DppEventActorType actorType;

    protected DppEvent() {}

    public DppEvent(DppForm dppForm, Instant occurredAt, String description, DppEventActorType actorType) {
        this.dppForm = dppForm;
        this.occurredAt = occurredAt;
        this.description = description;
        this.actorType = actorType;
    }
}
