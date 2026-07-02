package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.application.service.SellerSummaryService;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.SellerStatus;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSellerController.class)
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminSellerController 읽기 표면(list + detail) 슬라이스 테스트")
class AdminSellerControllerTest {

    private static final String ROLE_HEADER = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerSummaryService sellerSummaryService;

    @MockitoBean
    private SellerQueryService sellerQueryService;

    @MockitoBean
    private RegisterSellerService registerSellerService;

    private static SellerSummary summary(String sellerId, String name) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new SellerSummary(sellerId, name, SellerStatus.ACTIVE, now, now);
    }

    // ─── GET /api/admin/sellers ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/sellers - ADMIN 헤더로 200 + paged 요약 반환")
    void list_admin_returns200WithPagedSummary() throws Exception {
        SellerListResult result = new SellerListResult(
                List.of(summary("seller-a1", "셀러 A1"), summary("default", "Default Seller")),
                0, 20, 2L);
        given(sellerQueryService.listSellers(anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sellerId").value("seller-a1"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].sellerId").value("default"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("GET /api/admin/sellers - size 가 100 으로 cap 된다")
    void list_oversizedPage_isCappedAt100() throws Exception {
        SellerListResult result = new SellerListResult(List.of(), 0, 100, 0L);
        given(sellerQueryService.listSellers(eq(0), eq(100))).willReturn(result);

        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("GET /api/admin/sellers - 비-ADMIN 역할은 403")
    void list_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers - 역할 헤더 부재 시 403")
    void list_noRoleHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── GET /api/admin/sellers/{sellerId} ─────────────────────────────

    @Test
    @DisplayName("GET /api/admin/sellers/{id} - ADMIN 헤더로 200 + detail 반환")
    void detail_admin_returns200() throws Exception {
        given(sellerQueryService.getSeller("seller-a1")).willReturn(summary("seller-a1", "셀러 A1"));

        mockMvc.perform(get("/api/admin/sellers/{id}", "seller-a1")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("seller-a1"))
                .andExpect(jsonPath("$.displayName").value("셀러 A1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/{id} - cross-tenant/missing 셀러는 404")
    void detail_missing_returns404() throws Exception {
        given(sellerQueryService.getSeller("ghost"))
                .willThrow(new SellerNotFoundException("ghost"));

        mockMvc.perform(get("/api/admin/sellers/{id}", "ghost")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers/{id} - 비-ADMIN 역할은 403")
    void detail_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers/{id}", "seller-a1")
                        .header(ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/sellers - 멀티롤 헤더에 ADMIN 포함 시 200 (multi-domain operator)")
    void list_multiRoleHeaderContainingAdmin_returns200() throws Exception {
        SellerListResult result = new SellerListResult(List.of(), 0, 20, 0L);
        given(sellerQueryService.listSellers(anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN,ERP_OPERATOR,SCM_OPERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/sellers - ADMIN만 있는 단일 롤 헤더 200 (회귀 방지)")
    void list_singleAdminRole_returns200_regressionGuard() throws Exception {
        SellerListResult result = new SellerListResult(List.of(), 0, 20, 0L);
        given(sellerQueryService.listSellers(anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/sellers - ADMIN 없는 멀티롤은 403")
    void list_multiRoleWithoutAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "SCM_OPERATOR,ERP_OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers - 빈 헤더는 403")
    void list_emptyRoleHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/sellers - SUPERADMIN(서브스트링)만 있는 헤더는 403")
    void list_superadminSubstringOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/sellers")
                        .header(ROLE_HEADER, "SUPERADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─── lifecycle endpoints (ADR-MONO-042) ────────────────────────────

    @Test
    @DisplayName("POST /api/admin/sellers/{id}/provision - ADMIN → 204 + provisionPending 위임")
    void provision_admin_returns204() throws Exception {
        mockMvc.perform(post("/api/admin/sellers/{id}/provision", "seller-a1")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNoContent());
        verify(registerSellerService).provisionPending("seller-a1");
    }

    @Test
    @DisplayName("POST /api/admin/sellers/{id}/suspend - ADMIN → 204 + suspend 위임")
    void suspend_admin_returns204() throws Exception {
        mockMvc.perform(post("/api/admin/sellers/{id}/suspend", "seller-a1")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNoContent());
        verify(registerSellerService).suspend("seller-a1");
    }

    @Test
    @DisplayName("POST /api/admin/sellers/{id}/close - ADMIN → 204 + close 위임")
    void close_admin_returns204() throws Exception {
        mockMvc.perform(post("/api/admin/sellers/{id}/close", "seller-a1")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNoContent());
        verify(registerSellerService).close("seller-a1");
    }

    @Test
    @DisplayName("POST /api/admin/sellers/{id}/suspend - 비-ADMIN 은 403")
    void suspend_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/sellers/{id}/suspend", "seller-a1")
                        .header(ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/admin/sellers/{id}/provision - 부재 셀러는 404")
    void provision_missing_returns404() throws Exception {
        willThrow(new SellerNotFoundException("ghost"))
                .given(registerSellerService).provisionPending("ghost");

        mockMvc.perform(post("/api/admin/sellers/{id}/provision", "ghost")
                        .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
    }
}
