package com.example.account.presentation;

import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.exception.SiteRoleEmailMismatchException;
import com.example.account.application.exception.SiteRoleNotGrantableException;
import com.example.account.application.exception.SiteRoleRequiresPoolAccountException;
import com.example.account.application.result.SiteRoleMutationResult;
import com.example.account.application.service.ConsumerSiteRoleWriteUseCase;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.ConsumerSiteRoleController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-MONO-752 — {@code PATCH .../site-roles:grant} · {@code :revoke} (consumer-site-roles.md). */
@WebMvcTest(ConsumerSiteRoleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("ConsumerSiteRoleController slice (TASK-MONO-752)")
class ConsumerSiteRoleControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000752";
    private static final String GRANT = "/internal/tenants/{t}/accounts/{a}/site-roles:grant";
    private static final String REVOKE = "/internal/tenants/{t}/accounts/{a}/site-roles:revoke";
    private static final String GRANT_BODY =
            "{\"roleName\":\"SELLER\",\"expectedEmail\":\"member@example.com\",\"operatorId\":\"product-service\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ConsumerSiteRoleWriteUseCase useCase;
    @MockitoBean private ConsumerSiteMembershipRepository membershipRepository;

    @Test
    @DisplayName("grant → 200 · roles · changed")
    void grant_200() throws Exception {
        given(useCase.grant("ecommerce", ACCOUNT, "SELLER", "member@example.com", "product-service"))
                .willReturn(new SiteRoleMutationResult(ACCOUNT, "ecommerce", List.of("SELLER"), true));

        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.tenantId").value("ecommerce"))
                .andExpect(jsonPath("$.roles[0]").value("SELLER"))
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    @DisplayName("이메일 불일치 → 403 SITE_ROLE_EMAIL_MISMATCH")
    void grant_emailMismatch_403() throws Exception {
        given(useCase.grant("ecommerce", ACCOUNT, "SELLER", "member@example.com", "product-service"))
                .willThrow(new SiteRoleEmailMismatchException("mismatch"));

        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SITE_ROLE_EMAIL_MISMATCH"));
    }

    @Test
    @DisplayName("사이트 계정 → 409 SITE_ROLE_REQUIRES_POOL_ACCOUNT · 멤버십 없음 → 409 SITE_MEMBERSHIP_REQUIRED")
    void grant_notEligible_409() throws Exception {
        given(useCase.grant("ecommerce", ACCOUNT, "SELLER", "member@example.com", "product-service"))
                .willThrow(new SiteRoleRequiresPoolAccountException("site"))
                .willThrow(new SiteMembershipRequiredException("membership"));

        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_ROLE_REQUIRES_POOL_ACCOUNT"));
        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_MEMBERSHIP_REQUIRED"));
    }

    @Test
    @DisplayName("닫힌 목록 밖 → 400 SITE_ROLE_NOT_GRANTABLE")
    void grant_notGrantable_400() throws Exception {
        given(useCase.grant("ecommerce", ACCOUNT, "ECOMMERCE_OPERATOR", "member@example.com", null))
                .willThrow(new SiteRoleNotGrantableException("no"));

        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"ECOMMERCE_OPERATOR\",\"expectedEmail\":\"member@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SITE_ROLE_NOT_GRANTABLE"));
    }

    @Test
    @DisplayName("expectedEmail 없음 → 400 VALIDATION_ERROR · 유스케이스 호출 없음")
    void grant_missingEmail_400() throws Exception {
        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"SELLER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("다른 테넌트 X-Tenant-Id → 403 · 쓰기 없음")
    void grant_foreignTenantHeader_403() throws Exception {
        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("동시 grant 의 PK 충돌 → 200 · changed=false (읽기로 답)")
    void grant_concurrentDuplicate_answersWithRead() throws Exception {
        given(useCase.grant("ecommerce", ACCOUNT, "SELLER", "member@example.com", "product-service"))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for PRIMARY"));
        given(membershipRepository.findSiteRoles(new TenantId("ecommerce"), ACCOUNT)).willReturn(List.of("SELLER"));

        mockMvc.perform(patch(GRANT, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON).content(GRANT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SELLER"))
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    @DisplayName("revoke → 200 · changed")
    void revoke_200() throws Exception {
        given(useCase.revoke("ecommerce", ACCOUNT, "SELLER", "product-service"))
                .willReturn(new SiteRoleMutationResult(ACCOUNT, "ecommerce", List.of(), true));

        mockMvc.perform(patch(REVOKE, "ecommerce", ACCOUNT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"SELLER\",\"operatorId\":\"product-service\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.changed").value(true));
    }
}
