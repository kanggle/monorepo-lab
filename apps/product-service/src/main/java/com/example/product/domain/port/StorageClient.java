package com.example.product.domain.port;

/**
 * Port interface for object storage operations.
 * Adapters must implement this to integrate with S3-compatible storage.
 */
public interface StorageClient {

    /**
     * Generates a presigned PUT URL for uploading an object.
     *
     * @param bucket the bucket name
     * @param objectKey the object key
     * @param contentType the MIME type of the object
     * @param contentLength the size of the object in bytes
     * @return a presigned upload result containing uploadUrl, objectKey, and expiresAt
     */
    PresignedUploadResult generatePresignedPutUrl(String bucket, String objectKey, String contentType, long contentLength);

    /**
     * Checks if an object exists in the storage.
     *
     * @param bucket the bucket name
     * @param objectKey the object key
     * @return true if the object exists
     */
    boolean headObject(String bucket, String objectKey);

    /**
     * Deletes an object from the storage.
     *
     * @param bucket the bucket name
     * @param objectKey the object key
     */
    void deleteObject(String bucket, String objectKey);
}
