package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @GetMapping("/{id}/url")
    ResponseEntity<Map<String, String>> getUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("url", storageService.getPresignedUrl(id)));
    }
}
