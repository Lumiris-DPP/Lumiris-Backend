package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dpp-forms")
@RequiredArgsConstructor
public class DppFormController {

    private final DppFormService dppFormService;

    @PostMapping
    ResponseEntity<DppFormResponse> create(@RequestBody(required = false) DppFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dppFormService.create(request != null ? request : new DppFormRequest(null, null, null, null, null, null)));
    }

    @GetMapping
    ResponseEntity<List<DppFormResponse>> findAll() {
        return ResponseEntity.ok(dppFormService.findAll());
    }

    @GetMapping("/{id}")
    ResponseEntity<DppFormResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(dppFormService.findById(id));
    }

    @PatchMapping("/{id}")
    ResponseEntity<DppFormResponse> patch(@PathVariable UUID id, @RequestBody DppFormRequest request) {
        return ResponseEntity.ok(dppFormService.patch(id, request));
    }
}
