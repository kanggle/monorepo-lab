package com.example.membership.presentation.internal;

import com.example.membership.application.CheckContentAccessUseCase;
import com.example.membership.application.result.AccessCheckResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.presentation.exception.GlobalExceptionHandler;
import com.example.membership.support.MembershipJwtTestFixture;
import com.example.membership.support.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentAccessController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ContentAccessController slice tests")
class ContentAccessControllerTest {

    private static final MembershipJwtTestFixture JWT;

    static {
        // Class-init: must run before Spring constructs the JwtDecoder bean.
        JWT = new MembershipJwtTestFixture();
        SliceTestSecurityConfig.useFixture(JWT);
    }

    private static String bearer() {
        return "Bearer " + JWT.token("community-service-client", List.of());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckContentAccessUseCase checkContentAccessUseCase;

    @Test
    @DisplayName("GET /internal/membership/access (FAN_CLUB) returns allowed=true")
    void access_fanClubMatch_allowed() throws Exception {
        given(checkContentAccessUseCase.check(eq("acc-1"), eq(PlanLevel.FAN_CLUB)))
                .willReturn(new AccessCheckResult("acc-1", PlanLevel.FAN_CLUB, true, PlanLevel.FAN_CLUB));

        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", bearer())
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "FAN_CLUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.activePlanLevel").value("FAN_CLUB"));
    }

    @Test
    @DisplayName("GET /internal/membership/access (FREE vs FAN_CLUB) returns allowed=false")
    void access_freeBelowFanClub_denied() throws Exception {
        given(checkContentAccessUseCase.check(eq("acc-1"), eq(PlanLevel.FAN_CLUB)))
                .willReturn(new AccessCheckResult("acc-1", PlanLevel.FAN_CLUB, false, PlanLevel.FREE));

        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", bearer())
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "FAN_CLUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.activePlanLevel").value("FREE"));
    }

    @Test
    @DisplayName("GET /internal/membership/access without bearer token returns 401")
    void access_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/membership/access")
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "FAN_CLUB"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /internal/membership/access with unknown planLevel returns 400")
    void access_invalidPlan_returns400() throws Exception {
        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", bearer())
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "VIP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /internal/membership/access with cross-tenant token returns 403")
    void access_crossTenantToken_returns403() throws Exception {
        // Token signed with tenant_id=wms must be rejected.
        String wmsToken = "Bearer " + JWT.tokenWithTenant("wms-service", List.of(), "wms");

        mockMvc.perform(get("/internal/membership/access")
                        .header("Authorization", wmsToken)
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "FAN_CLUB"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }
}
