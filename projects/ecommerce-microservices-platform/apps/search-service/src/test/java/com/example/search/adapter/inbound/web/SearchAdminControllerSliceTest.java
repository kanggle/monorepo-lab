package com.example.search.adapter.inbound.web;

import com.example.search.application.port.in.ReindexAllUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("SearchAdminController 슬라이스 테스트")
class SearchAdminControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReindexAllUseCase reindexAllUseCase;

    // ─── POST /api/search/admin/reindex ─────────────────────────────────

    @Test
    @DisplayName("reindex - ECOMMERCE_OPERATOR 역할 시 200 반환")
    void reindex_adminRole_returns200() throws Exception {
        given(reindexAllUseCase.reindexAll(anyInt())).willReturn(42);

        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(42));
    }

    @Test
    @DisplayName("reindex - USER 역할 시 403 반환")
    void reindex_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("reindex - X-User-Role 헤더 미포함 시 403 반환")
    void reindex_missingRoleHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("reindex - batchSize 0 이하 시 400 반환")
    void reindex_invalidBatchSize_returns400() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .param("batchSize", "0"))
                .andExpect(status().isBadRequest());
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Test
    @DisplayName("reindex - 멀티롤 헤더에 ECOMMERCE_OPERATOR 포함 시 200 (multi-domain operator)")
    void reindex_multiRoleHeaderContainingAdmin_returns200() throws Exception {
        given(reindexAllUseCase.reindexAll(anyInt())).willReturn(10);

        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR,ERP_OPERATOR,SCM_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(10));
    }

    @Test
    @DisplayName("reindex - ECOMMERCE_OPERATOR만 있는 단일 롤 헤더 200 (회귀 방지)")
    void reindex_singleAdminRole_returns200_regressionGuard() throws Exception {
        given(reindexAllUseCase.reindexAll(anyInt())).willReturn(5);

        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reindex - ECOMMERCE_OPERATOR 없는 멀티롤은 403")
    void reindex_multiRoleWithoutAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "SCM_OPERATOR,ERP_OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("reindex - 빈 헤더는 403")
    void reindex_emptyRoleHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("reindex - SUPERADMIN(서브스트링)만 있는 헤더는 403")
    void reindex_superadminSubstringOnly_returns403() throws Exception {
        mockMvc.perform(post("/api/search/admin/reindex")
                        .header("X-User-Role", "SUPERADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
