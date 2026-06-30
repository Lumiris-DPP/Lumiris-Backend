package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MinioProperties;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
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

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
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

    public String getPresignedUrl(UUID fileId) {
        StoredFile stored = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(stored.getBucketName())
                            .object(stored.getObjectKey())
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
