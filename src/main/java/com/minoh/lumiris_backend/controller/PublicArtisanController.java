package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.ArtisanPublicProfileResponse;
import com.minoh.lumiris_backend.service.ArtisanVitrineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/artisans")
@RequiredArgsConstructor
public class PublicArtisanController {

    private final ArtisanVitrineService vitrineService;

    @GetMapping
    ResponseEntity<List<ArtisanPublicProfileResponse>> list() {
        return ResponseEntity.ok(vitrineService.listPublic());
    }

    @GetMapping("/{slug}")
    ResponseEntity<ArtisanPublicProfileResponse> findBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(vitrineService.findPublicBySlug(slug));
    }
}
