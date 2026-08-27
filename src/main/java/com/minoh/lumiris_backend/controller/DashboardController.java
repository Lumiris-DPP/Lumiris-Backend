package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUser;
import com.minoh.lumiris_backend.dto.out.DashboardInfoResponse;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/info")
    ResponseEntity<DashboardInfoResponse> info(@CurrentUser User user) {
        return ResponseEntity.ok(dashboardService.getInfo(user));
    }
}
