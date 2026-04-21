package com.example.product.application.service;

import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductImagesUpdatedPayload;
import com.example.product.domain.exception.ImageLimitExceededException;
import com.example.product.domain.exception.ImageNotFoundException;
import com.example.product.domain.exception.MediaNotFoundException;
import com.example.product.domain.exception.MediaValidationException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.domain.port.PresignedUploadResult;
import com.example.product.domain.port.StorageClient;
import com.example.product.domain.repository.ProductImageRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.infrastructure.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final int MAX_IMAGES_PER_PRODUCT = 10;
    private static final long MAX_CONTENT_LENGTH = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final StorageClient storageClient;
    private final MediaUrlResolver mediaUrlResolver;
    private final StorageProperties storageProperties;
    private final EventPublishingHelper eventPublishingHelper;

    public PresignedUploadResult generateUploadUrl(UUID productId, String contentType, long contentLength) {
        validateProductExists(productId);
        validateContentType(contentType);
        validateContentLength(contentLength);

        String bucket = storageProperties.getBuckets().getProductImages();
        String ext = extensionFromContentType(contentType);
        String objectKey = String.format("products/%s/0-%s.%s", productId, UUID.randomUUID(), ext);

        return storageClient.generatePresignedPutUrl(bucket, objectKey, contentType, contentLength);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", allEntries = true),
            @CacheEvict(value = "product-list", allEntries = true)
    })
    public ProductImage registerImage(UUID productId, String objectKey, int sortOrder, boolean isPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // Validate objectKey prefix
        if (!objectKey.startsWith("products/" + productId + "/")) {
            throw new MediaValidationException("Object key does not belong to product: " + productId);
        }

        // Check image limit
        int currentCount = productImageRepository.countByProductId(productId);
        if (currentCount >= MAX_IMAGES_PER_PRODUCT) {
            throw new ImageLimitExceededException(productId);
        }

        // Verify object exists in storage (HEAD check)
        String bucket = storageProperties.getBuckets().getProductImages();
        if (!storageClient.headObject(bucket, objectKey)) {
            throw new MediaNotFoundException(objectKey);
        }

        // Demote existing primary if setting new primary
        if (isPrimary) {
            demoteExistingPrimary(productId);
        }

        ProductImage image = ProductImage.create(productId, objectKey, sortOrder, isPrimary);
        productImageRepository.save(image);

        // Update product thumbnailUrl if primary
        updateProductThumbnailUrl(product, productId);

        // Publish event
        publishImagesUpdatedEvent(product, productId);

        return image;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", allEntries = true),
            @CacheEvict(value = "product-list", allEntries = true)
    })
    public ProductImage updateImage(UUID productId, UUID imageId, Integer sortOrder, Boolean isPrimary) {
        validateProductExists(productId);

        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException(imageId));

        if (!image.getProductId().equals(productId)) {
            throw new ImageNotFoundException(imageId);
        }

        if (isPrimary != null && isPrimary && !image.isPrimary()) {
            demoteExistingPrimary(productId);
            image.markPrimary();
        } else if (isPrimary != null && !isPrimary && image.isPrimary()) {
            image.demotePrimary();
        }

        if (sortOrder != null) {
            image.updateSortOrder(sortOrder);
        }

        productImageRepository.save(image);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        updateProductThumbnailUrl(product, productId);
        publishImagesUpdatedEvent(product, productId);

        return image;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-detail", allEntries = true),
            @CacheEvict(value = "product-list", allEntries = true)
    })
    public void deleteImage(UUID productId, UUID imageId) {
        validateProductExists(productId);

        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException(imageId));

        if (!image.getProductId().equals(productId)) {
            throw new ImageNotFoundException(imageId);
        }

        boolean wasPrimary = image.isPrimary();
        productImageRepository.delete(image);

        // Delete from storage (best-effort)
        String bucket = storageProperties.getBuckets().getProductImages();
        try {
            storageClient.deleteObject(bucket, image.getObjectKey());
        } catch (Exception e) {
            log.warn("Failed to delete object from storage: bucket={}, key={} — orphan will be cleaned by lifecycle",
                    bucket, image.getObjectKey(), e);
        }

        // If deleted image was primary, promote lowest sortOrder
        if (wasPrimary) {
            promoteLowestSortOrderImage(productId);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        updateProductThumbnailUrl(product, productId);
        publishImagesUpdatedEvent(product, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> getImages(UUID productId) {
        validateProductExists(productId);
        return productImageRepository.findByProductIdOrderBySortOrder(productId);
    }

    private void validateProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new MediaValidationException(
                    "Unsupported content type: " + contentType + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }
    }

    private void validateContentLength(long contentLength) {
        if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new MediaValidationException(
                    "Content length must be between 1 and " + MAX_CONTENT_LENGTH + " bytes");
        }
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private void demoteExistingPrimary(UUID productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(productId);
        for (ProductImage img : images) {
            if (img.isPrimary()) {
                img.demotePrimary();
                productImageRepository.save(img);
            }
        }
    }

    private void promoteLowestSortOrderImage(UUID productId) {
        List<ProductImage> remaining = productImageRepository.findByProductIdOrderBySortOrder(productId);
        if (!remaining.isEmpty()) {
            ProductImage promoted = remaining.get(0);
            promoted.markPrimary();
            productImageRepository.save(promoted);
        }
    }

    private void updateProductThumbnailUrl(Product product, UUID productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrder(productId);
        String thumbnailUrl = images.stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .map(img -> mediaUrlResolver.resolve(img.getObjectKey()))
                .orElse(null);

        product.updateThumbnailUrl(thumbnailUrl);
        productRepository.save(product);
    }

    private void publishImagesUpdatedEvent(Product product, UUID productId) {
        List<ProductImage> allImages = productImageRepository.findByProductIdOrderBySortOrder(productId);
        List<ProductImagesUpdatedPayload.ImageSnapshot> imageSnapshots = allImages.stream()
                .map(img -> new ProductImagesUpdatedPayload.ImageSnapshot(
                        img.getId().toString(),
                        img.getObjectKey(),
                        mediaUrlResolver.resolve(img.getObjectKey()),
                        img.getSortOrder(),
                        img.isPrimary()
                ))
                .toList();

        String thumbnailUrl = product.getThumbnailUrl();
        ProductImagesUpdatedPayload payload = ProductImagesUpdatedPayload.of(
                product.getId().toString(), thumbnailUrl, imageSnapshots);
        eventPublishingHelper.publishSafely(
                ProductEvent.imagesUpdated(payload),
                "product-image", product.getId());
    }
}
