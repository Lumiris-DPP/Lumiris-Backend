package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.service.DppEventService;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/dpp_forms")
@RequiredArgsConstructor
public class PublicDppController {

    private final DppFormService dppFormService;
    private final DppEventService dppEventService;

    /**
     * @param k token de laissez-passer, optionnel. Porté par le QR d'accès élargi.
     *          Si absent alors redirection vers le DPP du périmètre public.
     */
    @GetMapping("/{code}")
    public ResponseEntity<DppFormPublicResponse> findByPublicCode(
            @PathVariable String code,
            @RequestParam(required = false) String k
    ) {
        return ResponseEntity.ok(dppFormService.findByPublicCode(code, k));
    }

    @GetMapping("/{code}/events")
    public ResponseEntity<List<DppEventResponse>> findEventsByPublicCode(@PathVariable String code) {
        return ResponseEntity.ok(dppEventService.findAllByPublicCode(code));
    }
}
