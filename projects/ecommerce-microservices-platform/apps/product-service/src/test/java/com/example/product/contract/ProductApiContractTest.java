package com.example.product.contract;

import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.dto.ProductDetail;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.application.dto.VariantDetail;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.ProductStatus;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import com.example.product.presentation.controller.AdminProductController;
import com.example.product.presentation.controller.ProductController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.product.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * product-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/product-api.md
 */
@WebMvcTest(controllers = {ProductController.class, AdminProductController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("Product API 컨트랙트 테스트 — specs/contracts/http/product-api.md")
class ProductApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private static final String SPEC_REF = "specs/contracts/http/product-api.md";

    // ─── GET /api/products — 200 ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products 응답은 {content, page, size, totalElements}만 포함한다")
    void getProducts_response_containsSpecFields() throws Exception {
        UUID prodId = UUID.randomUUID();
        ProductSummary summary = new ProductSummary(prodId, "노트북", ProductStatus.ON_SALE, 1000000L, null);
        given(queryProductService.findAll(any(), any(), any(int.class), any(int.class)))
                .willReturn(new ProductListResult(List.of(summary), 0, 20, 1));

        MvcResult result = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFieldsMatch(json, Set.of("content", "page", "size", "totalElements"),
                SPEC_REF + " GET /api/products 200");

        JsonNode item = objectMapper.readTree(json).get("content").get(0);
        assertFieldsMatch(item, Set.of("id", "name", "status", "price", "thumbnailUrl", "categoryId"),
                SPEC_REF + " GET /api/products 200 content[]");
    }

    // ─── GET /api/products/{productId} — 200 ────────────────────────────

    @Test
    @DisplayName("GET /api/products/{productId} 응답은 스펙 정의 필드만 포함한다")
    void getProductDetail_response_containsSpecFields() throws Exception {
        UUID prodId = UUID.randomUUID();
        UUID varId = UUID.randomUUID();
        ProductDetail detail = new ProductDetail(prodId, "노트북", "상세 설명", ProductStatus.ON_SALE,
                1000000L, null, List.of(new VariantDetail(varId, "기본", 100, 0L)));
        given(queryProductService.findById(any())).willReturn(detail);

        MvcResult result = mockMvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        assertFieldsMatch(root, Set.of("id", "name", "description", "status", "price", "categoryId", "variants"),
                SPEC_REF + " GET /api/products/{productId} 200");

        JsonNode variant = root.get("variants").get(0);
        assertFieldsMatch(variant, Set.of("id", "optionName", "stock", "additionalPrice"),
                SPEC_REF + " GET /api/products/{productId} 200 variants[]");
    }

    // ─── POST /api/admin/products — 201 ─────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/products 응답은 {id}만 포함한다")
    void registerProduct_response_containsSpecFields() throws Exception {
        UUID prodId = UUID.randomUUID();
        given(registerProductService.register(any())).willReturn(prodId);

        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "노트북", "description", "설명",
                                "price", 1000000, "categoryId", UUID.randomUUID().toString(),
                                "variants", List.of(Map.of("optionName", "기본", "stock", 100, "additionalPrice", 0))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("id"), SPEC_REF + " POST /api/admin/products 201");
    }

    // ─── PATCH /api/admin/products/{productId} — 200 ────────────────────

    @Test
    @DisplayName("PATCH /api/admin/products/{productId} 응답은 {id}만 포함한다")
    void updateProduct_response_containsSpecFields() throws Exception {
        UUID prodId = UUID.randomUUID();
        given(updateProductService.update(any())).willReturn(prodId);

        MvcResult result = mockMvc.perform(patch("/api/admin/products/" + prodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새 이름"))))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("id"), SPEC_REF + " PATCH /api/admin/products/{productId} 200");
    }

    // ─── PATCH /api/admin/products/{productId}/stock — 200 ──────────────

    @Test
    @DisplayName("PATCH /api/admin/products/{productId}/stock 응답은 {variantId, currentStock}만 포함한다")
    void adjustStock_response_containsSpecFields() throws Exception {
        UUID prodId = UUID.randomUUID();
        UUID varId = UUID.randomUUID();
        given(adjustStockService.adjust(any())).willReturn(new AdjustStockResult(varId, 150));

        MvcResult result = mockMvc.perform(patch("/api/admin/products/" + prodId + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "variantId", varId.toString(), "quantity", 50, "reason", "RESTOCK"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("variantId", "currentStock"),
                SPEC_REF + " PATCH /api/admin/products/{productId}/stock 200");
    }

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        UUID prodId = UUID.randomUUID();
        given(queryProductService.findById(any())).willThrow(new ProductNotFoundException(prodId));

        MvcResult result = mockMvc.perform(get("/api/products/" + prodId))
                .andExpect(status().isNotFound())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                "specs/platform/error-handling.md error format");
    }
}
