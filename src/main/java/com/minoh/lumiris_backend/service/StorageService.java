package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MinioProperties;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final PresignedUrlSigner presignedUrlSigner;
    private final MinioProperties minioProperties;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;

    @Transactional
    public FileUploadResponse upload(MultipartFile file, String userEmail) {
        User uploader = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        String extension = extractExtension(file.getOriginalFilename());
        String objectKey = UUID.randomUUID() + extension;
        String bucket = minioProperties.bucket();

        try (InputStream content = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(content, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }

        StoredFile stored = new StoredFile();
        stored.setBucketName(bucket);
        stored.setObjectKey(objectKey);
        stored.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : objectKey);
        stored.setContentType(file.getContentType());
        stored.setSizeBytes(file.getSize());
        stored.setUploadedBy(uploader);

        stored = storedFileRepository.save(stored);

        return new FileUploadResponse(
                stored.getId(),
                stored.getOriginalFilename(),
                stored.getContentType(),
                stored.getSizeBytes(),
                stored.getCreatedAt()
        );
    }

    // Dépôt d'un document PRODUIT par la plateforme (bordereau d'expédition, facture) : même
    // stockage et même table que les téléversements, mais sans requête HTTP ni utilisateur
    // téléversant — l'auteur du fichier est le système.
    @Transactional
    public StoredFile store(byte[] content, String filename, String contentType) {
        String objectKey = UUID.randomUUID() + extractExtension(filename);
        String bucket = minioProperties.bucket();

        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(stream, content.length, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to store generated file in MinIO", e);
        }

        StoredFile stored = new StoredFile();
        stored.setBucketName(bucket);
        stored.setObjectKey(objectKey);
        stored.setOriginalFilename(filename);
        stored.setContentType(contentType);
        stored.setSizeBytes(content.length);

        return storedFileRepository.save(stored);
    }

    public String getPresignedUrl(UUID fileId) {
        StoredFile stored = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        return sign(stored);
    }

    // Une seule lecture de `files` pour tout un écran : `getPresignedUrl` appelé dans une boucle
    // coûte une requête par fichier, la signature MinIO elle-même étant purement locale.
    public Map<UUID, String> getPresignedUrls(Collection<UUID> fileIds) {
        Set<UUID> distinctIds = fileIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return storedFileRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, this::sign));
    }

    private String sign(StoredFile stored) {
        return presignedUrlSigner.sign(stored.getBucketName(), stored.getObjectKey());
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
