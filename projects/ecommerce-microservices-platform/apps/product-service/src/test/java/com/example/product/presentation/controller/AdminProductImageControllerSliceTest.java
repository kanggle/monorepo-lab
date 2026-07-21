package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.service.ProductImageService;
import com.example.product.domain.exception.ImageLimitExceededException;
import com.example.product.domain.exception.ImageNotFoundException;
import com.example.product.domain.exception.MediaNotFoundException;
import com.example.product.domain.exception.MediaValidationException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.StorageUnavailableException;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.domain.port.PresignedUploadResult;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminProductImageController.class)
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminProductImageController 슬라이스 테스트")
class AdminProductImageControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductImageService productImageService;

    @MockitoBean
    private MediaUrlResolver mediaUrlResolver;

    private final UUID productId = UUID.randomUUID();

    // ─── GET / (listImages) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET / - X-User-Role 헤더 없이 200 (operator-plane, 게이트 위임)")
    void listImages_noRoleHeader_returns200() throws Exception {
        given(productImageService.getImages(productId)).willReturn(java.util.List.of());

        // No X-User-Role header — operator-plane: authz is the gateway's
        // OPERATOR + tenant_id + WHERE tenant_id, not this controller.
        mockMvc.perform(get("/api/admin/products/{productId}/images", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isArray());
    }

    // ─── POST /upload-url ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /upload-url - 성공 시 200과 uploadUrl, objectKey, expiresAt 반환")
    void generateUploadUrl_success() throws Exception {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        Instant expiresAt = Instant.now().plusSeconds(900);
        given(productImageService.generateUploadUrl(eq(productId), eq("image/jpeg"), eq(1024L)))
                .willReturn(new PresignedUploadResult("https://s3.example.com/presigned", objectKey, expiresAt));

        mockMvc.perform(post("/api/admin/products/{productId}/images/upload-url", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "image/jpeg", "contentLength": 1024 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/presigned"))
                .andExpect(jsonPath("$.objectKey").value(objectKey))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /upload-url - 허용되지 않은 contentType 시 400")
    void generateUploadUrl_invalidContentType_returns400() throws Exception {
        given(productImageService.generateUploadUrl(eq(productId), eq("application/pdf"), anyLong()))
                .willThrow(new MediaValidationException("Unsupported content type"));

        mockMvc.perform(post("/api/admin/products/{productId}/images/upload-url", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "application/pdf", "contentLength": 1024 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /upload-url - 스토리지 장애 시 503")
    void generateUploadUrl_storageUnavailable_returns503() throws Exception {
        given(productImageService.generateUploadUrl(eq(productId), anyString(), anyLong()))
                .willThrow(new StorageUnavailableException("S3 down", new RuntimeException()));

        mockMvc.perform(post("/api/admin/products/{productId}/images/upload-url", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "image/jpeg", "contentLength": 1024 }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    }

    // ─── POST / (registerImage) ──────────────────────────────────────────

    @Test
    @DisplayName("POST / - 이미지 등록 성공 시 201")
    void registerImage_success_returns201() throws Exception {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        ProductImage image = ProductImage.create(productId, objectKey, 0, true);
        given(productImageService.registerImage(eq(productId), eq(objectKey), eq(0), eq(true)))
                .willReturn(image);
        given(mediaUrlResolver.resolve(objectKey)).willReturn("http://cdn/img.jpg");

        mockMvc.perform(post("/api/admin/products/{productId}/images", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "objectKey": "%s", "sortOrder": 0, "isPrimary": true }
                                """.formatted(objectKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageId").isNotEmpty())
                .andExpect(jsonPath("$.url").value("http://cdn/img.jpg"))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST / - objectKey HEAD 실패 시 404 MEDIA_NOT_FOUND")
    void registerImage_mediaNotFound_returns404() throws Exception {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        given(productImageService.registerImage(eq(productId), eq(objectKey), anyInt(), anyBoolean()))
                .willThrow(new MediaNotFoundException(objectKey));

        mockMvc.perform(post("/api/admin/products/{productId}/images", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "objectKey": "%s", "sortOrder": 0, "isPrimary": false }
                                """.formatted(objectKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST / - 10장 초과 시 422 IMAGE_LIMIT_EXCEEDED")
    void registerImage_limitExceeded_returns422() throws Exception {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        given(productImageService.registerImage(eq(productId), eq(objectKey), anyInt(), anyBoolean()))
                .willThrow(new ImageLimitExceededException(productId));

        mockMvc.perform(post("/api/admin/products/{productId}/images", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "objectKey": "%s", "sortOrder": 0, "isPrimary": false }
                                """.formatted(objectKey)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_EXCEEDED"));
    }

    // ─── PATCH /{imageId} ───────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /{imageId} - 업데이트 성공 시 200")
    void updateImage_success_returns200() throws Exception {
        UUID imageId = UUID.randomUUID();
        String objectKey = "products/" + productId + "/0-abc.jpg";
        ProductImage image = ProductImage.create(productId, objectKey, 5, true);
        given(productImageService.updateImage(eq(productId), eq(imageId), eq(5), eq(true)))
                .willReturn(image);
        given(mediaUrlResolver.resolve(objectKey)).willReturn("http://cdn/img.jpg");

        mockMvc.perform(patch("/api/admin/products/{productId}/images/{imageId}", productId, imageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sortOrder": 5, "isPrimary": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortOrder").value(5));
    }

    // ─── DELETE /{imageId} ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{imageId} - 삭제 성공 시 204")
    void deleteImage_success_returns204() throws Exception {
        UUID imageId = UUID.randomUUID();
        willDoNothing().given(productImageService).deleteImage(productId, imageId);

        mockMvc.perform(delete("/api/admin/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{imageId} - 존재하지 않는 이미지 시 404")
    void deleteImage_notFound_returns404() throws Exception {
        UUID imageId = UUID.randomUUID();
        willThrow(new ImageNotFoundException(imageId))
                .given(productImageService).deleteImage(productId, imageId);

        mockMvc.perform(delete("/api/admin/products/{productId}/images/{imageId}", productId, imageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST / - 상품 미존재 시 404 PRODUCT_NOT_FOUND")
    void registerImage_productNotFound_returns404() throws Exception {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        given(productImageService.registerImage(eq(productId), eq(objectKey), anyInt(), anyBoolean()))
                .willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(post("/api/admin/products/{productId}/images", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "objectKey": "%s", "sortOrder": 0, "isPrimary": false }
                                """.formatted(objectKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
