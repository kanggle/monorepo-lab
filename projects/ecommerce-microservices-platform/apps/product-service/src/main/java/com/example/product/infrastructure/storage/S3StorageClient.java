package com.example.product.infrastructure.storage;

import com.example.product.domain.exception.StorageUnavailableException;
import com.example.product.domain.port.PresignedUploadResult;
import com.example.product.domain.port.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@Profile("!standalone & !test")
@RequiredArgsConstructor
public class S3StorageClient implements StorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public PresignedUploadResult generatePresignedPutUrl(String bucket, String objectKey, String contentType, long contentLength) {
        try {
            int expirationMinutes = storageProperties.getS3().getPresignedUrlExpirationMinutes();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .putObjectRequest(putRequest)
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(expirationMinutes));

            return new PresignedUploadResult(
                    presigned.url().toString(),
                    objectKey,
                    expiresAt
            );
        } catch (SdkException e) {
            log.error("Failed to generate presigned URL for bucket={}, key={}", bucket, objectKey, e);
            throw new StorageUnavailableException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public boolean headObject(String bucket, String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            log.error("Failed to HEAD object bucket={}, key={}", bucket, objectKey, e);
            throw new StorageUnavailableException("Failed to check object existence", e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (SdkException e) {
            log.warn("Failed to delete object bucket={}, key={} — orphan will be cleaned by lifecycle", bucket, objectKey, e);
        }
    }
}
