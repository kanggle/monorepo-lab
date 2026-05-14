package com.example.product.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3ProductImageBucketResolver 단위 테스트")
class S3ProductImageBucketResolverTest {

    @Test
    @DisplayName("StorageProperties 의 product-images bucket 이름을 그대로 반환한다")
    void resolveBucket_returnsProductImagesBucketName() {
        StorageProperties storageProperties = new StorageProperties();
        StorageProperties.BucketProperties buckets = new StorageProperties.BucketProperties();
        buckets.setProductImages("product-images-test");
        storageProperties.setBuckets(buckets);

        S3ProductImageBucketResolver resolver = new S3ProductImageBucketResolver(storageProperties);

        assertThat(resolver.resolveBucket()).isEqualTo("product-images-test");
    }
}
