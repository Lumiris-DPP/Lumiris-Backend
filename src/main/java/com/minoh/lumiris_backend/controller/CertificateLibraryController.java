package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.CertificateLibraryItemResponse;
import com.minoh.lumiris_backend.entity.CertificateType;
import com.minoh.lumiris_backend.service.CertificateLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Bibliothèque de certificats de l'artisan courant — page Certifications côté front, réutilisée
// dans le formulaire DPP pour attacher un certificat déjà uploadé (voir DppFormController/Service).
@RestController
@RequestMapping("/api/certificate-library")
@RequiredArgsConstructor
public class CertificateLibraryController {

    private final CertificateLibraryService certificateLibraryService;

    @GetMapping
    ResponseEntity<List<CertificateLibraryItemResponse>> list(@CurrentUserEmail String email) {
        return ResponseEntity.ok(certificateLibraryService.list(email));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CertificateLibraryItemResponse upload(@RequestPart("file") MultipartFile file,
                                          @RequestParam CertificateType type,
                                          @CurrentUserEmail String email) {
        return certificateLibraryService.upload(file, type, email);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @CurrentUserEmail String email) {
        certificateLibraryService.delete(id, email);
    }
}
