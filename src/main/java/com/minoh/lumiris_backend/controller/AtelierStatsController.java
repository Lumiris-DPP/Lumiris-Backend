package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.AtelierStatsResponse;
import com.minoh.lumiris_backend.service.AtelierStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/atelier/stats")
@RequiredArgsConstructor
public class AtelierStatsController {

    private final AtelierStatsService atelierStatsService;

    @GetMapping
    ResponseEntity<AtelierStatsResponse> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @CurrentUserEmail String email
    ) {
        return ResponseEntity.ok(atelierStatsService.getStats(email, from, to));
    }
}
