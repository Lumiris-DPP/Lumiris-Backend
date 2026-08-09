package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    // Téléversement autonome, préalable à l'envoi d'un message ou d'une demande de retour :
    // l'utilisateur voit sa photo avant de valider, et le formulaire n'a plus qu'à transmettre
    // des identifiants.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FileUploadResponse> upload(@RequestPart("file") MultipartFile file,
                                              @CurrentUserEmail String email) {
        return ResponseEntity.ok(storageService.upload(file, email));
    }

    @GetMapping("/{id}/url")
    ResponseEntity<Map<String, String>> getUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("url", storageService.getPresignedUrl(id)));
    }
}
