package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.dto.out.DppPublicJsonLdResponse;
import com.minoh.lumiris_backend.service.DppEventService;
import com.minoh.lumiris_backend.service.DppFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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

    private static final String JSON_LD_MEDIA_TYPE = "application/ld+json";

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

    @GetMapping(value = "/{code}/jsonld", produces = JSON_LD_MEDIA_TYPE)
    @Operation(
            operationId = "getPublicDppJsonLd",
            summary = "Retourne la représentation JSON-LD publique d'un DPP"
    )
    @ApiResponse(
            responseCode = "200",
            description = "DPP public trouvé",
            content = @Content(
                    mediaType = JSON_LD_MEDIA_TYPE,
                    schema = @Schema(implementation = DppPublicJsonLdResponse.class)
            )
    )
    @ApiResponse(responseCode = "404", description = "Code public inconnu")
    public ResponseEntity<DppPublicJsonLdResponse> findPublicJsonLd(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(dppFormService.findPublicJsonLd(code, request.getRequestURL().toString()));
    }

    @GetMapping("/{code}/events")
    public ResponseEntity<List<DppEventResponse>> findEventsByPublicCode(@PathVariable String code) {
        return ResponseEntity.ok(dppEventService.findAllByPublicCode(code));
    }
}
