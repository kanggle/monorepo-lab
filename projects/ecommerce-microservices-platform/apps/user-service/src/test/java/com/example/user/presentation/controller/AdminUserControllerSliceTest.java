package com.example.user.presentation.controller;

import com.example.user.application.result.UserListPageResult;
import com.example.user.application.result.UserProfileResult;
import com.example.user.application.result.UserProfileSummaryResult;
import com.example.user.application.service.UserProfileService;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminUserController 슬라이스 테스트")
class AdminUserControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @Nested
    @DisplayName("GET /api/admin/users")
    class ListUsers {

        @Test
        @DisplayName("ECOMMERCE_OPERATOR 권한으로 사용자 목록을 페이지네이션하여 반환한다")
        void listUsers_withAdminRole_returns200() throws Exception {
            var summary = new UserProfileSummaryResult(
                    UUID.randomUUID(), "test@example.com", "홍길동", "길동이",
                    "ACTIVE", Instant.parse("2026-01-01T00:00:00Z")
            );
            UserListPageResult page = new UserListPageResult(List.of(summary), 1L, 1, 0, 20);
            given(userProfileService.listUsers(isNull(), isNull(), eq(0), eq(20))).willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].email").value("test@example.com"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("ECOMMERCE_OPERATOR 권한 없이 요청하면 403을 반환한다")
        void listUsers_withoutAdminRole_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("잘못된 역할로 요청하면 403을 반환한다")
        void listUsers_withWrongRole_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("status 필터로 사용자 목록을 조회한다")
        void listUsers_statusFilter_returns200() throws Exception {
            UserListPageResult page = new UserListPageResult(List.of(), 0L, 0, 0, 20);
            given(userProfileService.listUsers(eq(ProfileStatus.ACTIVE), isNull(), eq(0), eq(20)))
                    .willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("email 부분 검색으로 사용자 목록을 조회한다")
        void listUsers_emailFilter_returns200() throws Exception {
            UserListPageResult page = new UserListPageResult(List.of(), 0L, 0, 0, 20);
            given(userProfileService.listUsers(isNull(), eq("test"), eq(0), eq(20)))
                    .willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR")
                            .param("email", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("페이지 번호와 사이즈를 지정하여 조회한다")
        void listUsers_withPagination_returns200() throws Exception {
            UserListPageResult page = new UserListPageResult(List.of(), 0L, 0, 2, 10);
            given(userProfileService.listUsers(isNull(), isNull(), eq(2), eq(10)))
                    .willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users/{userId}")
    class GetUser {

        @Test
        @DisplayName("ECOMMERCE_OPERATOR 권한으로 특정 사용자 프로필을 조회하면 200을 반환한다")
        void getUser_withAdminRole_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            var result = new UserProfileResult(
                    userId, "test@example.com", "홍길동", "길동이",
                    "010-1234-5678", null, "ACTIVE",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-02T00:00:00Z")
            );
            given(userProfileService.getProfile(userId)).willReturn(result);

            mockMvc.perform(get("/api/admin/users/{userId}", userId)
                            .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        @DisplayName("ECOMMERCE_OPERATOR 권한 없이 특정 사용자 조회 시 403을 반환한다")
        void getUser_withoutAdminRole_returns403() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(get("/api/admin/users/{userId}", userId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 404를 반환한다")
        void getUser_nonExistingUser_returns404() throws Exception {
            UUID userId = UUID.randomUUID();
            given(userProfileService.getProfile(userId))
                    .willThrow(new UserProfileNotFoundException(userId));

            mockMvc.perform(get("/api/admin/users/{userId}", userId)
                            .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Nested
    @DisplayName("X-User-Role 멀티값 게이팅 (BE-393)")
    class MultiValueRoleGating {

        @Test
        @DisplayName("멀티롤 헤더에 ECOMMERCE_OPERATOR 포함 시 200 (multi-domain operator)")
        void listUsers_multiRoleContainingAdmin_returns200() throws Exception {
            var page = new UserListPageResult(List.of(), 0L, 0, 0, 20);
            given(userProfileService.listUsers(isNull(), isNull(), eq(0), eq(20))).willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR,ERP_OPERATOR,SCM_OPERATOR"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("단일 ECOMMERCE_OPERATOR 롤 헤더는 계속 200 (회귀 방지)")
        void listUsers_singleAdminRole_returns200_regressionGuard() throws Exception {
            var page = new UserListPageResult(List.of(), 0L, 0, 0, 20);
            given(userProfileService.listUsers(isNull(), isNull(), eq(0), eq(20))).willReturn(page);

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ECOMMERCE_OPERATOR 없는 멀티롤은 403")
        void listUsers_multiRoleWithoutAdmin_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "SCM_OPERATOR,ERP_OPERATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("빈 헤더는 403")
        void listUsers_emptyRoleHeader_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", ""))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("SUPERADMIN 서브스트링만 있는 헤더는 403 (부분일치 방지)")
        void listUsers_superadminSubstringOnly_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "SUPERADMIN"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }
}
