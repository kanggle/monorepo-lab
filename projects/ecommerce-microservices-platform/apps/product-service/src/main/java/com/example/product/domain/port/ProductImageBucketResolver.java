package com.example.product.domain.port;

/**
 * Port interface to resolve the S3 bucket name for product images.
 */
public interface ProductImageBucketResolver {

    /**
     * Resolves the name of the S3 bucket used for product images.
     *
     * @return the bucket name
     */
    String resolveBucket();
}
