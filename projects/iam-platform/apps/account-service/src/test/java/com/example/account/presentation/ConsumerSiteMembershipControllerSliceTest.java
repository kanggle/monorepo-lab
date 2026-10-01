package com.example.account.presentation;

import com.example.account.application.result.ConsumerSiteMembershipResult;
import com.example.account.application.service.ConsentToConsumerSiteUseCase;
import com.example.account.application.service.GetConsumerSiteMembershipUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.ConsumerSiteMembershipController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-BE-615 — {@code GET /internal/tenants/{tenantId}/consumer-members/{accountId}} (auth-to-account.md). */
@WebMvcTest(ConsumerSiteMembershipController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("ConsumerSiteMembershipController slice (TASK-BE-615)")
class ConsumerSiteMembershipControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000615000";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GetConsumerSiteMembershipUseCase useCase;
    /** TASK-BE-616 — the consent write on the same resource. */
    @MockitoBean private ConsentToConsumerSiteUseCase consentUseCase;

    @Test
    @DisplayName("TASK-BE-616: PUT(동의) → 200 · 읽기와 같은 본문 · membershipStatus ACTIVE")
    void consent_returns200_withMembership() throws Exception {
        given(consentUseCase.execute("fan-platform", ACCOUNT)).willReturn(new ConsumerSiteMembershipResult(
                ACCOUNT, "fan-platform", true, "B2C_CONSUMER", "ACTIVE", List.of()));

        mockMvc.perform(put("/internal/tenants/{t}/consumer-members/{a}", "fan-platform", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.siteTenantId").value("fan-platform"))
                .andExpect(jsonPath("$.consumerSite").value(true))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles").isEmpty());
    }

    @Test
    @DisplayName("TASK-BE-616: PUT 경합(PK 충돌) → 롤백된 쪽은 읽기로 답한다 — 200 · ACTIVE")
    void consent_concurrentDuplicate_answersWithRead() throws Exception {
        given(consentUseCase.execute("fan-platform", ACCOUNT))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for PRIMARY"));
        given(useCase.execute("fan-platform", ACCOUNT)).willReturn(new ConsumerSiteMembershipResult(
                ACCOUNT, "fan-platform", true, "B2C_CONSUMER", "ACTIVE", List.of()));

        mockMvc.perform(put("/internal/tenants/{t}/consumer-members/{a}", "fan-platform", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("TASK-BE-616: PUT 에 다른 테넌트 X-Tenant-Id → 403 · 쓰기 없음")
    void consent_foreignTenantHeader_403() throws Exception {
        mockMvc.perform(put("/internal/tenants/{t}/consumer-members/{a}", "fan-platform", ACCOUNT)
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(consentUseCase);
    }

    @Test
    @DisplayName("ACTIVE 멤버 → 200 · status · 사이트 역할")
    void member_returns200() throws Exception {
        given(useCase.execute("ecommerce", ACCOUNT)).willReturn(new ConsumerSiteMembershipResult(
                ACCOUNT, "ecommerce", true, "B2C_CONSUMER", "ACTIVE", List.of("SELLER")));

        mockMvc.perform(get("/internal/tenants/{t}/consumer-members/{a}", "ecommerce", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.siteTenantId").value("ecommerce"))
                .andExpect(jsonPath("$.consumerSite").value(true))
                .andExpect(jsonPath("$.siteTenantType").value("B2C_CONSUMER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.siteRoles[0]").value("SELLER"));
    }

    @Test
    @DisplayName("멤버십 없음 → 여전히 200 · membershipStatus null (404 는 «엔드포인트 없음» 뿐)")
    void nonMember_stillReturns200() throws Exception {
        given(useCase.execute("fan-platform", ACCOUNT)).willReturn(new ConsumerSiteMembershipResult(
                ACCOUNT, "fan-platform", true, "B2C_CONSUMER", null, List.of()));

        mockMvc.perform(get("/internal/tenants/{t}/consumer-members/{a}", "fan-platform", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipStatus").doesNotExist())
                .andExpect(jsonPath("$.siteRoles").isEmpty());
    }

    @Test
    @DisplayName("X-Tenant-Id 가 다른 테넌트를 말하면 403 TENANT_SCOPE_DENIED — 조회 없음")
    void foreignTenantHeader_403() throws Exception {
        mockMvc.perform(get("/internal/tenants/{t}/consumer-members/{a}", "ecommerce", ACCOUNT)
                        .header("X-Tenant-Id", "fan-platform"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }
}
