package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, AdminProductController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminProductController PATCH/DELETE 슬라이스 테스트")
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryProductService queryProductService;

    @MockitoBean
    private RegisterProductService registerProductService;

    @MockitoBean
    private UpdateProductService updateProductService;

    @MockitoBean
    private DeleteProductService deleteProductService;

    @MockitoBean
    private AdjustStockService adjustStockService;

    @MockitoBean
    private VariantManagementService variantManagementService;

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - 수정 성공 시 200과 id 반환")
    void update_success_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        given(updateProductService.update(any())).willReturn(productId);

        mockMvc.perform(patch("/api/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "수정된 상품명", "price": 20000 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - 깨진 JSON 본문 시 400 / VALIDATION_ERROR")
    void update_malformedBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/products/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - 빈 body 수정 시 200 반환")
    void update_emptyBody_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        given(updateProductService.update(any())).willReturn(productId);

        mockMvc.perform(patch("/api/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - 존재하지 않는 상품 수정 시 404 반환")
    void update_notFound_returns404() throws Exception {
        UUID productId = UUID.randomUUID();
        given(updateProductService.update(any())).willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(patch("/api/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "이름" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/admin/products/{id} - 삭제 성공 시 204 반환")
    void delete_success_returns204() throws Exception {
        UUID productId = UUID.randomUUID();
        willDoNothing().given(deleteProductService).delete(productId);

        mockMvc.perform(delete("/api/admin/products/{id}", productId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/admin/products/{id} - 존재하지 않는 상품 삭제 시 404 반환")
    void delete_notFound_returns404() throws Exception {
        UUID productId = UUID.randomUUID();
        willThrow(new ProductNotFoundException(productId)).given(deleteProductService).delete(productId);

        mockMvc.perform(delete("/api/admin/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - 음수 가격 수정 시 400 반환")
    void update_negativePrice_returns400() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "상품명", "price": -1000 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - 재고 조정 성공 시 200과 variantId, currentStock 반환")
    void adjustStock_success_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any())).willReturn(new AdjustStockResult(variantId, 150));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.currentStock").value(150));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - variantId 누락 시 400 반환")
    void adjustStock_missingVariantId_returns400() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantity": 50, "reason": "RESTOCK" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - 존재하지 않는 variantId 요청 시 404 반환")
    void adjustStock_variantNotFound_returns404() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any())).willThrow(new VariantNotFoundException(variantId));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - 존재하지 않는 productId 요청 시 404 반환")
    void adjustStock_productNotFound_returns404() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any())).willThrow(new ProductNotFoundException(productId));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - OptimisticLockingFailureException 발생 시 409 Conflict 반환")
    void adjustStock_optimisticLockConflict_returns409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any()))
                .willThrow(new OptimisticLockingFailureException("Version conflict"));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
