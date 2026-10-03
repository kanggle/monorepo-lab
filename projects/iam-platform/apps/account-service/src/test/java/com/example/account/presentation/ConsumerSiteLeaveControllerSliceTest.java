package com.example.account.presentation;

import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.application.service.LeaveConsumerSiteUseCase;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-619 — {@code DELETE /api/accounts/me/site-membership} («이 사이트 탈퇴») is a different route from
 * {@code DELETE /api/accounts/me} (계정 삭제): the site is the gateway's {@code X-Tenant-Id}, the actor is the
 * person ({@code SELF}), and the account-delete use case is never reached.
 */
@WebMvcTest({ConsumerSiteLeaveController.class, AccountStatusController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ConsumerSiteLeaveController slice (TASK-BE-619)")
class ConsumerSiteLeaveControllerSliceTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LeaveConsumerSiteUseCase leaveConsumerSiteUseCase;
    @MockitoBean private AccountStatusUseCase accountStatusUseCase;

    @Test
    @DisplayName("사이트 토큰(ecommerce)으로 탈퇴 → 200 · 그 사이트만 LEFT(SELF) · 계정 삭제 use case 는 불리지 않는다")
    void leave_thisSiteOnly() throws Exception {
        given(leaveConsumerSiteUseCase.execute("ecommerce", "acc-1", ConsumerSiteLeftBy.SELF, "acc-1"))
                .willReturn(new LeaveConsumerSiteResult("acc-1", "ecommerce", "LEFT", "SELF",
                        Instant.parse("2026-10-03T00:00:00Z"), true, "ACTIVE"));

        mockMvc.perform(delete("/api/accounts/me/site-membership")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteTenantId").value("ecommerce"))
                .andExpect(jsonPath("$.membershipStatus").value("LEFT"))
                .andExpect(jsonPath("$.leftBy").value("SELF"));

        verifyNoInteractions(accountStatusUseCase);
    }

    @Test
    @DisplayName("사이트 계정 · 멤버십 없음 → 409 SITE_MEMBERSHIP_REQUIRED")
    void notAMember_409() throws Exception {
        given(leaveConsumerSiteUseCase.execute(any(), any(), any(), any()))
                .willThrow(new SiteMembershipRequiredException("no membership"));

        mockMvc.perform(delete("/api/accounts/me/site-membership")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_MEMBERSHIP_REQUIRED"));
    }
}
