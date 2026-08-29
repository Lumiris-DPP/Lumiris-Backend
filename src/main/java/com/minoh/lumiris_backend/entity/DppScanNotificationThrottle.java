package com.minoh.lumiris_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Une ligne par passeport ayant déjà notifié un scan — hors de la ligne dpp_forms elle-même, que
// fn_dpp_forms_immutable() (V29) protège une fois publiée. Voir DppScanNotificationThrottleRepository.
@Entity
@Table(name = "dpp_scan_notification_throttle")
@Getter
@Setter
public class DppScanNotificationThrottle {

    @Id
    @Column(name = "dpp_form_id")
    private UUID dppFormId;

    @Column(name = "last_notified_at", nullable = false)
    private Instant lastNotifiedAt;
}
