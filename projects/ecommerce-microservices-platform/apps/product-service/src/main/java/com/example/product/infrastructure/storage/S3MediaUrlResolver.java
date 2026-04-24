package com.example.product.infrastructure.storage;

import com.example.product.domain.port.MediaUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3MediaUrlResolver implements MediaUrlResolver {

    private final StorageProperties storageProperties;

    @Override
    public String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String baseUrl = storageProperties.getCdn().getBaseUrl();
        String bucket = storageProperties.getBuckets().getProductImages();
        return baseUrl + "/" + bucket + "/" + objectKey;
    }
}
