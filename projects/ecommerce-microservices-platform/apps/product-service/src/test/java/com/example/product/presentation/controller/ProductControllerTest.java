package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.ProductDetail;
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
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.port.MediaUrlResolver;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, AdminProductController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ProductController 슬라이스 테스트")
class ProductControllerTest {

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

    @Test
    @DisplayName("GET /api/products - 목록 조회 성공")
    void getProducts_success_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ProductSummary summary = new ProductSummary(id, "상품", ProductStatus.ON_SALE, 10000L, null);
        ProductListResult result = new ProductListResult(List.of(summary), 0, 20, 1L);
        given(queryProductService.findAll(any(), any(), any(), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("GET /api/products?size=500 - 과도한 page size 는 100 으로 clamp 된다 (M7, TASK-BE-405)")
    void getProducts_oversizedSize_clampedToMax() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 100, 0L);
        org.mockito.ArgumentCaptor<Integer> sizeCaptor = org.mockito.ArgumentCaptor.forClass(Integer.class);
        given(queryProductService.findAll(any(), any(), any(), anyInt(), sizeCaptor.capture())).willReturn(result);

        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk());

        // M7: no LIMIT-less / oversized list reachable by a tenant — clamped to 100.
        org.assertj.core.api.Assertions.assertThat(sizeCaptor.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("GET /api/products?size=50 - 정상 page size 는 그대로 전달된다 (backward-compatible)")
    void getProducts_normalSize_passedThrough() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 50, 0L);
        org.mockito.ArgumentCaptor<Integer> sizeCaptor = org.mockito.ArgumentCaptor.forClass(Integer.class);
        given(queryProductService.findAll(any(), any(), any(), anyInt(), sizeCaptor.capture())).willReturn(result);

        mockMvc.perform(get("/api/products").param("size", "50"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(sizeCaptor.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("GET /api/products?name=셔츠 - name 필터가 서비스로 전달된다 (TASK-BE-420)")
    void getProducts_withNameFilter_passedThrough() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 20, 0L);
        org.mockito.ArgumentCaptor<String> nameCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        given(queryProductService.findAll(any(), any(), nameCaptor.capture(), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/products").param("name", "셔츠"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(nameCaptor.getValue()).isEqualTo("셔츠");
    }

    @Test
    @DisplayName("GET /api/products - name 파라미터 없으면 null 로 전달된다")
    void getProducts_noNameParam_passesNull() throws Exception {
        ProductListResult result = new ProductListResult(List.of(), 0, 20, 0L);
        org.mockito.ArgumentCaptor<String> nameCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        given(queryProductService.findAll(any(), any(), nameCaptor.capture(), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(nameCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("GET /api/products/{productId} - 상세 조회 성공")
    void getProduct_success_returns200WithVariants() throws Exception {
        UUID id = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        VariantDetail variant = new VariantDetail(variantId, "기본", 10, 0L);
        ProductDetail detail = new ProductDetail(id, "상품", "설명", ProductStatus.ON_SALE, 10000L, null, List.of(variant));
        given(queryProductService.findById(id)).willReturn(detail);

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.variants").isArray())
                .andExpect(jsonPath("$.variants[0].optionName").value("기본"));
    }

    @Test
    @DisplayName("GET /api/products/{productId} - CONSUMER 응답에 seller_id 가 읽기 전용으로 노출된다 (AC-4/AC-7)")
    void getProduct_exposesSellerIdReadOnly() throws Exception {
        UUID id = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        VariantDetail variant = new VariantDetail(variantId, "기본", 10, 0L);
        // full ProductDetail carrying sellerId (seller-owned product)
        ProductDetail detail = new ProductDetail(id, "상품", "설명", ProductStatus.ON_SALE, 10000L,
                null, null, "seller-a1", List.of(variant));
        given(queryProductService.findById(id)).willReturn(detail);

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("seller-a1"));
    }

    @Test
    @DisplayName("GET /api/products - 목록 content[]에 seller_id 가 노출된다 (AC-7)")
    void getProducts_listExposesSellerId() throws Exception {
        UUID id = UUID.randomUUID();
        ProductSummary summary = new ProductSummary(id, "상품", ProductStatus.ON_SALE, 10000L, null, null, "seller-a1");
        ProductListResult result = new ProductListResult(List.of(summary), 0, 20, 1L);
        given(queryProductService.findAll(any(), any(), any(), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sellerId").value("seller-a1"));
    }

    @Test
    @DisplayName("POST /api/admin/products - request.sellerId 가 command 로 전달된다 (OPERATOR 표면)")
    void registerProduct_forwardsSellerId() throws Exception {
        UUID newId = UUID.randomUUID();
        org.mockito.ArgumentCaptor<com.example.product.application.command.RegisterProductCommand> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.product.application.command.RegisterProductCommand.class);
        given(registerProductService.register(captor.capture())).willReturn(newId);

        String requestBody = """
                {
                  "name": "셀러 상품",
                  "price": 10000,
                  "sellerId": "seller-a1",
                  "variants": [ { "optionName": "기본", "stock": 10, "additionalPrice": 0 } ]
                }
                """;

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().sellerId()).isEqualTo("seller-a1");
    }

    @Test
    @DisplayName("GET /api/products/{productId} - 존재하지 않으면 404 반환")
    void getProduct_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryProductService.findById(id)).willThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/admin/products - 필수 필드 누락 시 400 반환")
    void registerProduct_missingFields_returns400() throws Exception {
        String requestBody = """
                {
                  "description": "설명만 있음"
                }
                """;

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/admin/products - variants 누락 시 400 반환")
    void registerProduct_missingVariants_returns400() throws Exception {
        String requestBody = """
                {
                  "name": "상품명",
                  "price": 10000,
                  "variants": []
                }
                """;

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/admin/products - 등록 성공 시 201 반환")
    void registerProduct_success_returns201() throws Exception {
        UUID newId = UUID.randomUUID();
        given(registerProductService.register(any())).willReturn(newId);

        String requestBody = """
                {
                  "name": "새 상품",
                  "description": "설명",
                  "price": 10000,
                  "variants": [
                    { "optionName": "기본", "stock": 10, "additionalPrice": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(newId.toString()));
    }

    @Test
    @DisplayName("POST /api/admin/products - price가 0이면 400 반환")
    void registerProduct_zeroPriceFails_returns400() throws Exception {
        String requestBody = """
                {
                  "name": "상품명",
                  "price": 0,
                  "variants": [
                    { "optionName": "기본", "stock": 10, "additionalPrice": 0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
