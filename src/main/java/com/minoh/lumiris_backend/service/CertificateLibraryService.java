package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.CertificateLibraryItemResponse;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.CertificateLibraryItem;
import com.minoh.lumiris_backend.entity.CertificateType;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.CertificateLibraryRepository;
import com.minoh.lumiris_backend.repository.DppFormDocumentRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Bibliothèque de certificats réutilisables sur plusieurs DppForm — voir DppFormService pour le
// point d'attachement (resolveCertificateLibraryRefs).
@Service
@RequiredArgsConstructor
public class CertificateLibraryService {

    private final CertificateLibraryRepository certificateLibraryRepository;
    private final DppFormDocumentRepository dppFormDocumentRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<CertificateLibraryItemResponse> list(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        List<CertificateLibraryItem> items = certificateLibraryRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        List<UUID> fileIds = items.stream().map(item -> item.getFile().getId()).toList();
        Map<UUID, Long> usageByFileId = dppFormDocumentRepository.countDistinctDppFormsByFileIds(fileIds).stream()
                .collect(Collectors.toMap(DppFormDocumentRepository.FileUsageCount::getFileId,
                        DppFormDocumentRepository.FileUsageCount::getCount));

        return items.stream().map(item -> toResponse(item, usageByFileId)).toList();
    }

    @Transactional
    public CertificateLibraryItemResponse upload(MultipartFile file, CertificateType type, String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        FileUploadResponse uploaded = storageService.upload(file, userEmail);

        CertificateLibraryItem item = new CertificateLibraryItem();
        item.setUser(user);
        item.setFile(storedFileRepository.getReferenceById(uploaded.id()));
        item.setType(type);
        item = certificateLibraryRepository.save(item);

        return toResponse(item, Map.of());
    }

    @Transactional
    public void delete(UUID id, String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        CertificateLibraryItem item = certificateLibraryRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certificat introuvable : " + id));
        // Retire uniquement l'entrée de bibliothèque : le StoredFile et les DppFormDocument déjà
        // créés ne sont pas touchés (mêmes garanties que le nettoyage des vieux fichiers sur
        // DppFormService.update — le blob reste, seule la possibilité de réutilisation future part).
        certificateLibraryRepository.delete(item);
    }

    // Résolution ownership-checked pour l'attachement à un DPP (voir DppFormService). Le type
    // doit correspondre au champ ciblé (origine/transaction) — sinon 400, pas un simple mismatch
    // silencieux.
    @Transactional(readOnly = true)
    public StoredFile resolveForAttach(UUID libraryId, CertificateType expectedType, User user) {
        CertificateLibraryItem item = certificateLibraryRepository.findByIdAndUser_Id(libraryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certificat introuvable : " + libraryId));
        if (item.getType() != expectedType) {
            throw new IllegalArgumentException(
                    "Ce certificat est de type " + item.getType() + ", attendu " + expectedType + ".");
        }
        return item.getFile();
    }

    private CertificateLibraryItemResponse toResponse(CertificateLibraryItem item, Map<UUID, Long> usageByFileId) {
        UUID fileId = item.getFile().getId();
        return new CertificateLibraryItemResponse(
                item.getId(),
                item.getType().name(),
                item.getFile().getOriginalFilename(),
                storageService.getPresignedUrl(fileId),
                usageByFileId.getOrDefault(fileId, 0L),
                item.getCreatedAt()
        );
    }
}
