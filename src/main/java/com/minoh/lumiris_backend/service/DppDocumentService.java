package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.entity.CertificateType;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppDocumentVisibility;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.DppFormDocumentRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pièces jointes d'un DPP : upload MinIO, rattachement au formulaire, et exposition filtrée par
 * visibilité. Les fichiers sont indexés par nom de part multipart (voir {@link DocumentType#partName}).
 */
@Service
@RequiredArgsConstructor
public class DppDocumentService {

    private static final String PRODUCT_PHOTO_PART = "productPhoto";

    private final StorageService storageService;
    private final StoredFileRepository storedFileRepository;
    private final DppFormDocumentRepository dppFormDocumentRepository;
    private final CertificateLibraryService certificateLibraryService;

    /**
     * Envoie les fichiers vers MinIO, hors transaction : un upload lent ne doit pas garder une
     * connexion base ouverte. La map rendue est mutable, complétée ensuite par
     * {@link #resolveCertificateLibraryRefs}.
     */
    public Map<String, UUID> upload(Map<String, MultipartFile> files, String userEmail) {
        Map<String, UUID> uploadedIds = new LinkedHashMap<>();
        files.forEach((partName, file) -> {
            if (file != null && !file.isEmpty()) {
                uploadedIds.put(partName, storageService.upload(file, userEmail).id());
            }
        });
        return uploadedIds;
    }

    // Rattache un certificat de la bibliothèque au lieu d'un nouvel upload : résout l'id de
    // bibliothèque en fileId et l'ajoute à uploadedIds, indiscernable ensuite d'un fichier
    // fraîchement uploadé pour saveDocuments (même principe que copyDocuments réutilisant un
    // StoredFile existant).
    public void resolveCertificateLibraryRefs(DppFormRequest request, Map<String, MultipartFile> files,
                                              Map<String, UUID> uploadedIds, User user) {
        resolveCertificateLibraryRef(CertificateType.TRANSACTION, request.transactionCertLibraryId(),
                files, uploadedIds, user);
        resolveCertificateLibraryRef(CertificateType.ORIGIN, request.originCertLibraryId(),
                files, uploadedIds, user);
    }

    private void resolveCertificateLibraryRef(CertificateType type, UUID libraryId,
                                              Map<String, MultipartFile> files,
                                              Map<String, UUID> uploadedIds, User user) {
        if (libraryId == null) {
            return;
        }
        String partName = type.documentType.partName;
        MultipartFile raw = files.get(partName);
        if (raw != null && !raw.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choisissez soit un nouveau fichier, soit un certificat de votre bibliothèque, pas les deux.");
        }
        uploadedIds.put(partName, certificateLibraryService.resolveForAttach(libraryId, type, user).getId());
    }

    /** The main photo is carried by the form itself, so it must be applied before the form is saved. */
    public void attachMainPhoto(DppForm form, Map<String, UUID> uploadedIds) {
        UUID photoId = uploadedIds.get(PRODUCT_PHOTO_PART);
        if (photoId != null) {
            form.setMainPhotoFile(storedFileRepository.getReferenceById(photoId));
        }
    }

    /**
     * Persist uploaded documents straight through their repo (the child @ManyToOne owns the FK);
     * when {@code replaceExisting}, a part supersedes the stored document of the same type.
     * Going through {@code form.getDocuments()} instead would insert an empty row with a null
     * dpp_form_id: on an unloaded lazy collection Hibernate queues the add and flushes it without
     * the entity's state — the same trap materials and care instructions already avoid.
     * The form must already be persisted.
     */
    public void saveDocuments(DppForm form, Map<String, UUID> uploadedIds, boolean replaceExisting) {
        uploadedIds.forEach((partName, fileId) -> {
            if (PRODUCT_PHOTO_PART.equals(partName)) return;
            DocumentType.fromPartName(partName).ifPresent(docType -> {
                if (replaceExisting) {
                    dppFormDocumentRepository.deleteByDppFormAndDocumentType(form, docType);
                }
                DppFormDocument doc = new DppFormDocument();
                doc.setDppForm(form);
                doc.setFile(storedFileRepository.getReferenceById(fileId));
                doc.setDocumentType(docType);
                doc.setVisibility(docType.defaultVisibility());
                dppFormDocumentRepository.save(doc);
            });
        });
    }

    /** La copie réutilise les mêmes StoredFile : aucun blob n'est dupliqué dans MinIO. */
    public void copyDocuments(DppForm source, DppForm copy) {
        for (DppFormDocument document : source.getDocuments()) {
            DppFormDocument documentCopy = new DppFormDocument();
            documentCopy.setDppForm(copy);
            documentCopy.setFile(document.getFile());
            documentCopy.setDocumentType(document.getDocumentType());
            documentCopy.setVisibility(document.getVisibility());
            dppFormDocumentRepository.save(documentCopy);
        }
    }

    public void deleteDocuments(DppForm form) {
        dppFormDocumentRepository.deleteByDppForm(form);
    }

    public String mainPhotoUrl(DppForm form) {
        return form.getMainPhotoFile() != null
                ? storageService.getPresignedUrl(form.getMainPhotoFile().getId())
                : null;
    }

    /**
     * Ne cartographie que les documents dont la visibilité est couverte par {@code scopes}, et ne
     * signe une URL MinIO que pour ceux-là. Le filtrage doit rester ici : une URL présignée émise
     * est un accès accordé, qu'un front la masque ensuite ou non.
     */
    public List<DppFormDocumentResponse> visibleDocuments(DppForm form, Set<DppDocumentVisibility> scopes) {
        List<DppFormDocument> visible = form.getDocuments().stream()
                .filter(d -> scopes.contains(d.getVisibility()))
                .toList();
        Map<UUID, String> urlsByFileId = storageService.getPresignedUrls(
                visible.stream().map(d -> d.getFile().getId()).toList());

        return visible.stream()
                .map(d -> new DppFormDocumentResponse(
                        d.getFile().getId(),
                        d.getDocumentType().name(),
                        d.getVisibility().name(),
                        d.getFile().getOriginalFilename(),
                        urlsByFileId.get(d.getFile().getId())
                ))
                .toList();
    }
}
