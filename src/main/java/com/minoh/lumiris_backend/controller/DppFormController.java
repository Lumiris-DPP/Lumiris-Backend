package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dpp-forms")
@RequiredArgsConstructor
public class DppFormController {

    private final DppFormService dppFormService;

    @PostMapping
    ResponseEntity<DppFormResponse> create(
            @RequestBody(required = false) DppFormRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dppFormService.create(request, principal.getUsername()));
    }
}
