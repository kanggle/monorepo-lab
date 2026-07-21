package com.example.product.presentation.controller;

import com.example.common.summary.PeriodSummary;
import com.example.product.TestProductServiceApplication;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
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

@WebMvcTest(controllers = AdminSellerController.class)
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("GET /api/admin/sellers/summary 슬라이스 테스트")
class AdminSellerSummaryControllerSliceTest {

    private static final String ROLE_HEADER = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerQueryService sellerQueryService;

    @MockitoBean
    private RegisterSellerService registerSellerService;

    @Test
    @DisplayName("GET /api/admin/sellers/summary - ECOMMERCE_OPERATOR 헤더로 셀러 없을 때 모든 필드 0")
    void summary_empty_allZeros() throws Exception {
        given(sellerQueryService.getPeriodSummary())
                .willReturn(new PeriodSummary(0L, 0L, 0L, 0L));

        mockMvc.perform(get("/api/admin/sellers/summary")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(0))
                .andExpect(jsonPath("$.week").value(0))
                .andExpect(jsonPath("$.month").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/summary - 오늘 등록 셀러 있을 때 today/week/month/total 모두 반영")
    void summary_todaySeller_allPeriodsReflected() throws Exception {
        given(sellerQueryService.getPeriodSummary())
                .willReturn(new PeriodSummary(1L, 1L, 1L, 3L));

        mockMvc.perform(get("/api/admin/sellers/summary")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.week").value(1))
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/summary - 비-ECOMMERCE_OPERATOR 역할은 403")
    void summary_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers/summary")
                        .header(ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/summary - 역할 헤더 부재 시 403")
    void summary_noRoleHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/summary - 멀티롤 헤더에 ECOMMERCE_OPERATOR 포함 시 200")
    void summary_multiRoleContainingAdmin_returns200() throws Exception {
        given(sellerQueryService.getPeriodSummary())
                .willReturn(new PeriodSummary(0L, 0L, 0L, 0L));

        mockMvc.perform(get("/api/admin/sellers/summary")
                        .header(ROLE_HEADER, "ECOMMERCE_OPERATOR,ERP_OPERATOR"))
                .andExpect(status().isOk());
    }
}
