package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.application.dto.VariantDetail;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.ProductImageService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.exception.DuplicateVariantOptionException;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.port.MediaUrlResolver;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private ProductImageService productImageService;

    @MockitoBean
    private MediaUrlResolver mediaUrlResolver;

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

    // ─── GET /api/admin/products (operator-plane read — TASK-MONO-243) ──

    @Test
    @DisplayName("GET /api/admin/products - ECOMMERCE_OPERATOR-role 헤더 없이 200 + paged 요약 반환 (게이트 entitlement-trust, controller 미검사)")
    void list_noRoleHeader_returns200WithPagedSummary() throws Exception {
        UUID id = UUID.randomUUID();
        ProductSummary summary = new ProductSummary(id, "상품", ProductStatus.ON_SALE, 10000L, null, null, "seller-a1");
        ProductListResult result = new ProductListResult(List.of(summary), 0, 1, 42L);
        given(queryProductService.findAll(any(), any(), any(), anyInt(), anyInt())).willReturn(result);

        // No X-User-Role header at all — the read MUST NOT require ECOMMERCE_OPERATOR (the
        // operator-overview leg presents an IAM OIDC token with no ecommerce
        // ECOMMERCE_OPERATOR role claim; authz is the gateway's OPERATOR + entitlement-trust).
        mockMvc.perform(get("/api/admin/products")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/products - status 필터 없이 ?page=0&size=1 호출 시 totalElements = tenant 총 상품 수")
    void list_pageSize1_surfacesTotalCatalogCount() throws Exception {
        // metric semantics: console-bff calls ?page=0&size=1 (no status filter);
        // totalElements is the tenant's full catalog size.
        ProductListResult result = new ProductListResult(List.of(), 0, 1, 7L);
        given(queryProductService.findAll(isNull(), isNull(), isNull(), eq(0), eq(1))).willReturn(result);

        mockMvc.perform(get("/api/admin/products")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    @DisplayName("GET /api/admin/products - size 가 MAX_PAGE_SIZE(100) 로 cap 된다 (공개 컨트롤러 미러)")
    void list_oversizedPage_isCappedAt100() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 100, 0L);
        given(queryProductService.findAll(isNull(), isNull(), isNull(), eq(0), eq(100))).willReturn(result);

        mockMvc.perform(get("/api/admin/products")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("GET /api/admin/products?name=셔츠 - name 필터가 서비스로 전달된다 (공개 컨트롤러 미러, TASK-BE-420)")
    void list_withNameFilter_passedThrough() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 20, 0L);
        org.mockito.ArgumentCaptor<String> nameCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        given(queryProductService.findAll(any(), any(), nameCaptor.capture(), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/admin/products").param("name", "셔츠"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(nameCaptor.getValue()).isEqualTo("셔츠");
    }

    // ─── POST /api/admin/products (operator-plane write — TASK-BE-366) ──

    @Test
    @DisplayName("POST /api/admin/products - X-User-Role 헤더 없이 201 (게이트 위임, controller 미검사)")
    void register_noRoleHeader_returns201() throws Exception {
        given(registerProductService.register(any())).willReturn(UUID.randomUUID());

        // No X-User-Role header — operator-plane: authz is the gateway's
        // OPERATOR + tenant_id + WHERE tenant_id, not this controller.
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "테스트 상품", "price": 10000, "variants": [{ "optionName": "기본", "stock": 10, "additionalPrice": 0 }] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // ── TASK-BE-536: Idempotency-Key on POST /api/admin/products ──────────

    @Test
    @DisplayName("POST /api/admin/products - Idempotency-Key 헤더가 커맨드로 전달된다")
    void register_idempotencyKey_reachesCommand() throws Exception {
        org.mockito.ArgumentCaptor<com.example.product.application.command.RegisterProductCommand> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.product.application.command.RegisterProductCommand.class);
        given(registerProductService.register(captor.capture())).willReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/admin/products")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "테스트 상품", "price": 10000, "variants": [{ "optionName": "기본", "stock": 10, "additionalPrice": 0 }] }
                                """))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().idempotencyKey()).isEqualTo("idem-key-1");
    }

    @Test
    @DisplayName("POST /api/admin/products - Idempotency-Key 헤더 누락 시 400 / IDEMPOTENCY_KEY_REQUIRED 반환")
    void register_missingIdempotencyKey_returns400() throws Exception {
        given(registerProductService.register(any()))
                .willThrow(new IdempotencyKeyRequiredException("required"));

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "테스트 상품", "price": 10000, "variants": [{ "optionName": "기본", "stock": 10, "additionalPrice": 0 }] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/admin/products - 같은 키를 다른 상품명으로 재사용하면 409 / IDEMPOTENCY_KEY_CONFLICT 반환")
    void register_reusedIdempotencyKey_returns409() throws Exception {
        given(registerProductService.register(any()))
                .willThrow(new IdempotencyKeyConflictException("conflict"));

        mockMvc.perform(post("/api/admin/products")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "테스트 상품", "price": 10000, "variants": [{ "optionName": "기본", "stock": 10, "additionalPrice": 0 }] }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    // ─── POST /api/admin/products/{id}/variants ────────────────────────

    @Test
    @DisplayName("POST /api/admin/products/{id}/variants - X-User-Role 헤더 없이 201 (게이트 위임)")
    void addVariant_noRoleHeader_returns201() throws Exception {
        UUID productId = UUID.randomUUID();
        given(variantManagementService.addVariant(any(), any(), anyInt(), anyLong()))
                .willReturn(new VariantDetail(UUID.randomUUID(), "옵션A", 10, 0L));

        mockMvc.perform(post("/api/admin/products/{id}/variants", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "optionName": "옵션A", "stock": 10, "additionalPrice": 0 }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TASK-BE-536 POST /api/admin/products/{id}/variants - 중복 optionName 시 409 / DUPLICATE_VARIANT_OPTION 반환")
    void addVariant_duplicateOptionName_returns409() throws Exception {
        UUID productId = UUID.randomUUID();
        given(variantManagementService.addVariant(any(), any(), anyInt(), anyLong()))
                .willThrow(new DuplicateVariantOptionException(productId, "옵션A", null));

        mockMvc.perform(post("/api/admin/products/{id}/variants", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "optionName": "옵션A", "stock": 10, "additionalPrice": 0 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VARIANT_OPTION"));
    }

    // ─── PATCH /api/admin/products/{id}/variants/{variantId} ───────────

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/variants/{variantId} - X-User-Role 헤더 없이 200 (게이트 위임)")
    void updateVariant_noRoleHeader_returns200() throws Exception {
        given(variantManagementService.updateVariant(any(), any(), any(), anyLong()))
                .willReturn(new VariantDetail(UUID.randomUUID(), "옵션A", 10, 0L));

        mockMvc.perform(patch("/api/admin/products/{id}/variants/{variantId}", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "optionName": "옵션A", "additionalPrice": 0 }
                                """))
                .andExpect(status().isOk());
    }

    // ─── DELETE /api/admin/products/{id}/variants/{variantId} ──────────

    @Test
    @DisplayName("DELETE /api/admin/products/{id}/variants/{variantId} - X-User-Role 헤더 없이 204 (게이트 위임)")
    void deleteVariant_noRoleHeader_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/products/{id}/variants/{variantId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    // ─── PATCH /api/admin/products/{id} ────────────────────────────────

    @Test
    @DisplayName("PATCH /api/admin/products/{id} - X-User-Role 헤더 없이 수정 성공 시 200과 id 반환 (게이트 위임)")
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

    // ─── DELETE /api/admin/products/{id} ───────────────────────────────

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

    // ─── PATCH /api/admin/products/{id}/stock ──────────────────────────

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - 재고 조정 성공 시 200과 variantId, currentStock 반환")
    void adjustStock_success_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any())).willReturn(new AdjustStockResult(variantId, 150));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .header("Idempotency-Key", "adj-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.currentStock").value(150));
    }

    // ── TASK-BE-536: Idempotency-Key on PATCH /api/admin/products/{id}/stock ──

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - Idempotency-Key 헤더가 커맨드로 전달된다")
    void adjustStock_idempotencyKey_reachesCommand() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        org.mockito.ArgumentCaptor<com.example.product.application.command.AdjustStockCommand> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.product.application.command.AdjustStockCommand.class);
        given(adjustStockService.adjust(captor.capture())).willReturn(new AdjustStockResult(variantId, 150));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .header("Idempotency-Key", "adj-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().idempotencyKey()).isEqualTo("adj-key-2");
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - Idempotency-Key 헤더 누락 시 400 / IDEMPOTENCY_KEY_REQUIRED 반환")
    void adjustStock_missingIdempotencyKey_returns400() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any()))
                .willThrow(new IdempotencyKeyRequiredException("required"));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("PATCH /api/admin/products/{id}/stock - 같은 키를 다른 수량으로 재사용하면 409 / IDEMPOTENCY_KEY_CONFLICT 반환")
    void adjustStock_reusedIdempotencyKey_returns409() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        given(adjustStockService.adjust(any()))
                .willThrow(new IdempotencyKeyConflictException("conflict"));

        mockMvc.perform(patch("/api/admin/products/{id}/stock", productId)
                        .header("Idempotency-Key", "adj-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "variantId": "%s", "quantity": 50, "reason": "RESTOCK" }
                                """.formatted(variantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
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
