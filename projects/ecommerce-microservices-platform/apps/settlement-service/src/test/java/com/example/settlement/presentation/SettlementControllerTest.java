package com.example.settlement.presentation;

import com.example.settlement.TestSettlementServiceApplication;
import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.application.service.CommissionRateAdminService;
import com.example.settlement.application.service.SettlementQueryService;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionRate;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.InvalidCommissionRateException;
import com.example.settlement.domain.model.SellerBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
@ContextConfiguration(classes = TestSettlementServiceApplication.class)
@Import(GlobalExceptionHandler.class)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService queryService;
    @MockitoBean
    private CommissionRateAdminService rateAdminService;

    // ── GET /accruals ──────────────────────────────────────────────────────

    @Test
    void listAccruals_admin_returns200() throws Exception {
        CommissionAccrual a = new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, Instant.now());
        given(queryService.listAccruals(any(), any(), any()))
                .willReturn(new PageResult<>(List.of(a), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/admin/settlements/accruals").header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accrualId").value("a1"))
                .andExpect(jsonPath("$.items[0].type").value("ACCRUAL"))
                .andExpect(jsonPath("$.items[0].commissionMinor").value(3000))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listAccruals_missingRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/accruals"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listAccruals_crossSellerFilter_returns404() throws Exception {
        given(queryService.listAccruals(eq("seller-2"), any(), any()))
                .willThrow(new SellerScopeForbiddenException("seller-2"));

        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR").param("sellerId", "seller-2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    // ── GET /sellers/{id}/balance ──────────────────────────────────────────

    @Test
    void sellerBalance_admin_returns200() throws Exception {
        given(queryService.sellerBalance("seller-1"))
                .willReturn(new SellerBalance("seller-1", 27_000L, 3_000L, 30_000L, 1L));

        mockMvc.perform(get("/api/admin/settlements/sellers/seller-1/balance")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("seller-1"))
                .andExpect(jsonPath("$.accruedNetMinor").value(27000))
                .andExpect(jsonPath("$.platformCommissionMinor").value(3000));
    }

    @Test
    void sellerBalance_crossSeller_returns404() throws Exception {
        given(queryService.sellerBalance("seller-2"))
                .willThrow(new SellerScopeForbiddenException("seller-2"));

        mockMvc.perform(get("/api/admin/settlements/sellers/seller-2/balance")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    // ── GET / PUT /commission-rates/{id} ───────────────────────────────────

    @Test
    void getRate_admin_returns200() throws Exception {
        given(rateAdminService.getEffectiveRate("seller-1"))
                .willReturn(CommissionRate.platformDefault(0));

        mockMvc.perform(get("/api/admin/settlements/commission-rates/seller-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("seller-1"))
                .andExpect(jsonPath("$.rateBps").value(0))
                .andExpect(jsonPath("$.source").value("PLATFORM_DEFAULT"));
    }

    @Test
    void setRate_admin_returns200() throws Exception {
        given(rateAdminService.setRate("seller-1", 1200))
                .willReturn(CommissionRate.sellerOverride(1200));

        mockMvc.perform(put("/api/admin/settlements/commission-rates/seller-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rateBps": 1200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateBps").value(1200))
                .andExpect(jsonPath("$.source").value("SELLER_OVERRIDE"));
    }

    @Test
    void setRate_outOfRange_returns422_commissionRateInvalid() throws Exception {
        doThrow(new InvalidCommissionRateException(10_001))
                .when(rateAdminService).setRate(eq("seller-1"), eq(10_001));

        mockMvc.perform(put("/api/admin/settlements/commission-rates/seller-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rateBps": 10001}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMISSION_RATE_INVALID"));
    }

    @Test
    void setRate_missingBody_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/settlements/commission-rates/seller-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void setRate_missingRole_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/settlements/commission-rates/seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rateBps": 1000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Test
    void listAccruals_multiRoleContainingAdmin_returns200() throws Exception {
        given(queryService.listAccruals(any(), any(), any()))
                .willReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ERP_OPERATOR,SCM_OPERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    void listAccruals_singleAdminRole_returns200_regressionGuard() throws Exception {
        given(queryService.listAccruals(any(), any(), any()))
                .willReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    void listAccruals_multiRoleWithoutAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", "SCM_OPERATOR,ERP_OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listAccruals_emptyRoleHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listAccruals_superadminSubstringOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/settlements/accruals")
                        .header("X-User-Role", "SUPERADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
