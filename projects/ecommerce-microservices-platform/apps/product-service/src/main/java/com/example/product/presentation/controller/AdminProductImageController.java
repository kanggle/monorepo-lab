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
import com.example.web.exception.AccessDeniedException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
public class AdminProductImageController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ProductImageService productImageService;
    private final MediaUrlResolver mediaUrlResolver;

    @GetMapping
    public ResponseEntity<ImageListResponse> listImages(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId) {
        validateAdminRole(userRole);
        List<ProductImage> images = productImageService.getImages(productId);
        List<ImageResponse> responses = images.stream()
                .map(img -> ImageResponse.from(img, mediaUrlResolver.resolve(img.getObjectKey())))
                .toList();
        return ResponseEntity.ok(new ImageListResponse(responses));
    }

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUrlResponse> generateUploadUrl(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody PresignedUrlRequest request) {
        validateAdminRole(userRole);
        PresignedUploadResult result = productImageService.generateUploadUrl(
                productId, request.contentType(), request.contentLength());
        return ResponseEntity.ok(PresignedUrlResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<RegisterImageResponse> registerImage(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody RegisterImageRequest request) {
        validateAdminRole(userRole);
        ProductImage image = productImageService.registerImage(
                productId, request.objectKey(), request.sortOrder(), request.isPrimary());
        String resolvedUrl = mediaUrlResolver.resolve(image.getObjectKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegisterImageResponse.from(image, resolvedUrl));
    }

    @PatchMapping("/{imageId}")
    public ResponseEntity<ImageResponse> updateImage(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @Valid @RequestBody UpdateImageRequest request) {
        validateAdminRole(userRole);
        ProductImage image = productImageService.updateImage(
                productId, imageId, request.sortOrder(), request.isPrimary());
        String resolvedUrl = mediaUrlResolver.resolve(image.getObjectKey());
        return ResponseEntity.ok(ImageResponse.from(image, resolvedUrl));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        validateAdminRole(userRole);
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    private void validateAdminRole(String userRole) {
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException();
        }
    }
}
