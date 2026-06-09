package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.CheckAccessUseCase;
import com.example.fanplatform.membership.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.membership.testsupport.JwtTestHelper;
import com.example.fanplatform.membership.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link InternalAccessController} — param binding, the
 * workload-identity security chain (200 / 403 / 401), and the {@code allowed}
 * boolean shape.
 */
@WebMvcTest(controllers = InternalAccessController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class InternalAccessControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean CheckAccessUseCase checkAccessUseCase;

    private String workloadBearer() {
        return "Bearer " + jwt.signWorkloadToken("svc-community");
    }

    @Test
    @DisplayName("workload-identity token → 200 { allowed: true }; params bind 1:1")
    void workloadGrant() throws Exception {
        when(checkAccessUseCase.hasAccess(eq("acc1"), eq("MEMBERS_ONLY"), eq("fan-platform")))
                .thenReturn(true);

        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", workloadBearer())
                        .param("accountId", "acc1")
                        .param("tier", "MEMBERS_ONLY")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    @DisplayName("domain deny → 200 { allowed: false } (NOT an error status)")
    void workloadDenyIs200() throws Exception {
        when(checkAccessUseCase.hasAccess(eq("acc1"), eq("PREMIUM"), eq("fan-platform")))
                .thenReturn(false);

        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", workloadBearer())
                        .param("accountId", "acc1")
                        .param("tier", "PREMIUM")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    @DisplayName("end-user token on /internal/** → 403 FORBIDDEN")
    void endUserTokenForbidden() throws Exception {
        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", "Bearer " + jwt.signFanToken("acc1"))
                        .param("accountId", "acc1")
                        .param("tier", "MEMBERS_ONLY")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("no token on /internal/** → 401")
    void noTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/membership/access")
                        .param("accountId", "acc1")
                        .param("tier", "MEMBERS_ONLY")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isUnauthorized());
    }
}
