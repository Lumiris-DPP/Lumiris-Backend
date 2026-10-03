package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.WardrobeSyncRequest;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.marketplace.order.service.BuyerOrderService;
import com.minoh.lumiris_backend.service.WardrobeSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Expose les pièces de la garde-robe de l’acheteur authentifié. */
@RestController
@RequiredArgsConstructor
public class WardrobeController {

    private final BuyerOrderService buyerOrderService;
    private final WardrobeSyncService wardrobeSyncService;

    /** Liste les pièces achetées et présentes dans la garde-robe. */
    @GetMapping("/api/wardrobe")
    ResponseEntity<List<WardrobeItemResponse>> wardrobe(@CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getWardrobe(email));
    }

    /** Synchronise les passeports scannés de la garde-robe de cet acheteur. */
    @PostMapping("/api/wardrobe/sync")
    ResponseEntity<List<WardrobeItemResponse>> syncWardrobe(@Valid @RequestBody WardrobeSyncRequest request,
                                                            @CurrentUserEmail String email) {
        return ResponseEntity.ok(wardrobeSyncService.sync(email, request));
    }
}
