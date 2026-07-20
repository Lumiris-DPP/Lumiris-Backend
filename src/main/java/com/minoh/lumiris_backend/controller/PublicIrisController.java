package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse;
import com.minoh.lumiris_backend.service.scoring.IrisMethodologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/public/iris")
@RequiredArgsConstructor
public class PublicIrisController {

    private final IrisMethodologyService irisMethodologyService;

    /**
     * Contenu statique : on laisse le CDN et le navigateur le garder une heure, TanStack Query
     * prenant le relais côté client.
     */
    @GetMapping("/methodology")
    public ResponseEntity<IrisMethodologyResponse> getMethodology() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(irisMethodologyService.getMethodology());
    }
}
