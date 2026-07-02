package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DppFormService {

    private final DppFormRepository dppFormRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final StorageService storageService;
    private final DppFormMapper dppFormMapper;

    @Transactional
    public DppFormResponse create(DppFormRequest request, Map<String, MultipartFile> files, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DppFormRequest r = request != null ? request : emptyRequest();
        DppForm form = dppFormMapper.toEntity(r, user);

        files.forEach((partName, file) -> {
            if (file == null || file.isEmpty()) return;

            var uploaded = storageService.upload(file, userEmail);
            StoredFile storedFile = storedFileRepository.getReferenceById(uploaded.id());

            if ("productPhoto".equals(partName)) {
                form.setMainPhotoFile(storedFile);
                return;
            }

            DocumentType.fromPartName(partName).ifPresent(docType -> {
                DppFormDocument doc = new DppFormDocument();
                doc.setDppForm(form);
                doc.setFile(storedFile);
                doc.setDocumentType(docType);
                doc.setVisibility(docType.defaultVisibility());
                form.getDocuments().add(doc);
            });
        });

        DppForm saved = dppFormRepository.save(form);
        return buildResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DppFormSummaryResponse> findAllByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return dppFormRepository.findByUserId(user.getId()).stream()
                .map(dppFormMapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DppFormResponse findById(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());
        return buildResponse(form);
    }

    private DppFormResponse buildResponse(DppForm form) {
        String mainPhotoUrl = null;
        if (form.getMainPhotoFile() != null) {
            mainPhotoUrl = storageService.getPresignedUrl(form.getMainPhotoFile().getId());
        } else {
            mainPhotoUrl = form.getMainPhotoUrl();
        }

        List<DppFormDocumentResponse> documents = form.getDocuments().stream()
                .map(d -> new DppFormDocumentResponse(
                        d.getFile().getId(),
                        d.getDocumentType().name(),
                        d.getVisibility().name(),
                        d.getFile().getOriginalFilename(),
                        storageService.getPresignedUrl(d.getFile().getId())
                ))
                .toList();

        return dppFormMapper.toResponse(form, mainPhotoUrl, documents);
    }

    private static DppFormRequest emptyRequest() {
        return new DppFormRequest(
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null
        );
    }
}
