package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.TrackEventRequest;
import com.minoh.lumiris_backend.service.AtelierStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Public, anonymous analytics ingestion (passport scans/views/clicks/conversions from
// WEB/VISION). No PII accepted or stored — see AtelierStatsService / dpp_events table.
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final AtelierStatsService atelierStatsService;

    @PostMapping
    ResponseEntity<Void> track(@Valid @RequestBody TrackEventRequest request) {
        atelierStatsService.track(request.publicCode(), request.type());
        return ResponseEntity.accepted().build();
    }
}
