package com.example.product.presentation.controller;

import com.example.product.application.service.ProductImageService;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.domain.port.PresignedUploadResult;
import com.example.product.presentation.dto.ImageListResponse;
import com.example.product.presentation.dto.ImageResponse;
import com.example.product.presentation.dto.PresignedUrlRequest;
import com.example.product.presentation.dto.PresignedUrlResponse;
import com.example.product.presentation.dto.RegisterImageRequest;
import com.example.product.presentation.dto.RegisterImageResponse;
import com.example.product.presentation.dto.UpdateImageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Operator-plane product-image administration (ADR-MONO-031 Phase 1a, TASK-BE-366).
 *
 * <p><b>Authorization is enforced at the ecommerce gateway, not in this
 * controller</b> (extension of the read-leg pattern established in
 * TASK-MONO-243 for {@code AdminProductController.list}):
 * {@code AccountTypeEnforcementFilter} requires {@code roles ∋ ADMIN}
 * for {@code /api/admin/**}, {@code TenantClaimValidator} requires a non-blank
 * {@code tenant_id}, and the repository {@code WHERE tenant_id} chokepoint
 * (Step 2 / M6) enforces tenant isolation. The platform-console operator
 * carries the {@code ADMIN} domain role via the ADR-MONO-035 4a assume-tenant
 * derivation (ecommerce-entitled tenant → {@code ADMIN}); the service applies
 * no additional ecommerce-local RBAC — the gateway is the single admission
 * point (header-trust service). (ADR-MONO-035 4b removed the legacy
 * {@code account_type=OPERATOR} gateway leg.)
 */
@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
public class AdminProductImageController {

    private final ProductImageService productImageService;
    private final MediaUrlResolver mediaUrlResolver;

    @GetMapping
    public ResponseEntity<ImageListResponse> listImages(
            @PathVariable UUID productId) {
        List<ProductImage> images = productImageService.getImages(productId);
        List<ImageResponse> responses = images.stream()
                .map(img -> ImageResponse.from(img, mediaUrlResolver.resolve(img.getObjectKey())))
                .toList();
        return ResponseEntity.ok(new ImageListResponse(responses));
    }

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUrlResponse> generateUploadUrl(
            @PathVariable UUID productId,
            @Valid @RequestBody PresignedUrlRequest request) {
        PresignedUploadResult result = productImageService.generateUploadUrl(
                productId, request.contentType(), request.contentLength());
        return ResponseEntity.ok(PresignedUrlResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<RegisterImageResponse> registerImage(
            @PathVariable UUID productId,
            @Valid @RequestBody RegisterImageRequest request) {
        ProductImage image = productImageService.registerImage(
                productId, request.objectKey(), request.sortOrder(), request.isPrimary());
        String resolvedUrl = mediaUrlResolver.resolve(image.getObjectKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegisterImageResponse.from(image, resolvedUrl));
    }

    @PatchMapping("/{imageId}")
    public ResponseEntity<ImageResponse> updateImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @Valid @RequestBody UpdateImageRequest request) {
        ProductImage image = productImageService.updateImage(
                productId, imageId, request.sortOrder(), request.isPrimary());
        String resolvedUrl = mediaUrlResolver.resolve(image.getObjectKey());
        return ResponseEntity.ok(ImageResponse.from(image, resolvedUrl));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
