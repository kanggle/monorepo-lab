package com.example.product.infrastructure.storage;

import com.example.product.domain.port.ProductImageBucketResolver;
import org.springframework.stereotype.Component;

@Component
public class S3ProductImageBucketResolver implements ProductImageBucketResolver {

    private final StorageProperties storageProperties;

    public S3ProductImageBucketResolver(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public String resolveBucket() {
        return storageProperties.getBuckets().getProductImages();
    }
}
