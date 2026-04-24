package com.example.product.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@Profile("!standalone & !test")
@RequiredArgsConstructor
public class S3Config {

    private final StorageProperties storageProperties;

    @Bean
    public StaticCredentialsProvider s3CredentialsProvider() {
        var props = storageProperties.getS3();
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
    }

    @Bean
    public S3Client s3Client(StaticCredentialsProvider s3CredentialsProvider) {
        var props = storageProperties.getS3();
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(s3CredentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StaticCredentialsProvider s3CredentialsProvider) {
        var props = storageProperties.getS3();
        // Presigned URLs are consumed by the browser, so use the CDN/public
        // endpoint (localhost:9000) instead of the Docker-internal endpoint
        // (minio:9000) that s3Client uses.
        String presignerEndpoint = storageProperties.getCdn().getBaseUrl();
        return S3Presigner.builder()
                .endpointOverride(URI.create(presignerEndpoint))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(s3CredentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .build();
    }
}
