package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.ProductPeriodSummary;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.ProductImageService;
import com.example.product.application.service.ProductSummaryService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, AdminProductController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("GET /api/admin/products/summary 슬라이스 테스트")
class AdminProductSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSummaryService productSummaryService;

    // Required by the WebMvcTest context (sibling beans in the controller)
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

    @MockitoBean
    private ProductImageService productImageService;

    @MockitoBean
    private MediaUrlResolver mediaUrlResolver;

    @Test
    @DisplayName("GET /api/admin/products/summary - 상품 없을 때 모든 필드 0")
    void summary_empty_allZeros() throws Exception {
        given(productSummaryService.getSummary())
                .willReturn(new ProductPeriodSummary(0L, 0L, 0L, 0L));

        mockMvc.perform(get("/api/admin/products/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(0))
                .andExpect(jsonPath("$.week").value(0))
                .andExpect(jsonPath("$.month").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/admin/products/summary - 오늘 등록 상품 있을 때 today/week/month/total 모두 반영")
    void summary_todayProduct_allPeriodsReflected() throws Exception {
        given(productSummaryService.getSummary())
                .willReturn(new ProductPeriodSummary(1L, 1L, 1L, 5L));

        mockMvc.perform(get("/api/admin/products/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("GET /api/admin/products/summary - 역할 헤더 없이도 200 (auth는 게이트웨이 위임)")
    void summary_noRoleHeader_returns200() throws Exception {
        given(productSummaryService.getSummary())
                .willReturn(new ProductPeriodSummary(0L, 0L, 0L, 0L));

        // No X-User-Role header — AdminProductController does not locally validate role
        mockMvc.perform(get("/api/admin/products/summary"))
                .andExpect(status().isOk());
    }
}
