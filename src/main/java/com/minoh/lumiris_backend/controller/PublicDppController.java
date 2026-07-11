package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/dpp_forms")
@RequiredArgsConstructor
public class PublicDppController {

    private final DppFormService dppFormService;

    @GetMapping("/{code}")
    public ResponseEntity<DppFormPublicResponse> findByPublicCode(@PathVariable String code) {
        return ResponseEntity.ok(dppFormService.findByPublicCode(code));
    }
}
