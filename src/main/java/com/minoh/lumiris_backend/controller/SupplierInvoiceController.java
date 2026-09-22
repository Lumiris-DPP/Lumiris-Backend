package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.SupplierInvoiceResponse;
import com.minoh.lumiris_backend.service.SupplierInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier-invoices")
@RequiredArgsConstructor
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<SupplierInvoiceResponse> upload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierInvoiceService.upload(principal.getUsername(), file));
    }

    @GetMapping("/mine")
    ResponseEntity<List<SupplierInvoiceResponse>> mine(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(supplierInvoiceService.findMine(principal.getUsername()));
    }

    @GetMapping("/{id}")
    ResponseEntity<SupplierInvoiceResponse> one(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(supplierInvoiceService.findOne(principal.getUsername(), id));
    }
}
