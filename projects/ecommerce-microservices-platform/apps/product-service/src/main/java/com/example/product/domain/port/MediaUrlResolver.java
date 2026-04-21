package com.example.product.domain.port;

/**
 * Port interface to resolve an objectKey to a full URL.
 */
public interface MediaUrlResolver {

    /**
     * Resolves an object key to a publicly accessible URL.
     *
     * @param objectKey the object key in the storage bucket
     * @return the full URL (e.g., CDN base URL + bucket + objectKey)
     */
    String resolve(String objectKey);
}
