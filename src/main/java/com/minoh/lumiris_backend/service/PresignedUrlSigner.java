package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PresignedUrlSigner {

    private static final int URL_TTL_HOURS = 1;

    private final MinioClient signingClient;

    PresignedUrlSigner(MinioProperties properties, MinioClient internalClient) {
        this.signingClient = hasPublicEndpoint(properties)
                ? MinioClient.builder()
                        .endpoint(properties.publicEndpoint())
                        .credentials(properties.accessKey(), properties.secretKey())
                        .build()
                : internalClient;
    }

    private static boolean hasPublicEndpoint(MinioProperties properties) {
        return properties.publicEndpoint() != null && !properties.publicEndpoint().isBlank();
    }

    public String sign(String bucket, String objectKey) {
        try {
            return signingClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(URL_TTL_HOURS, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
