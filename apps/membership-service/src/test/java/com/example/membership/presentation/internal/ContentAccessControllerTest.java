package com.example.membership.presentation.internal;

import com.example.membership.application.CheckContentAccessUseCase;
import com.example.membership.application.result.AccessCheckResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.infrastructure.config.SecurityConfig;
import com.example.membership.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentAccessController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.token=test-token")
@DisplayName("ContentAccessController slice tests")
class ContentAccessControllerTest {

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
                        .header("X-Internal-Token", "test-token")
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
                        .header("X-Internal-Token", "test-token")
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "FAN_CLUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.activePlanLevel").value("FREE"));
    }

    @Test
    @DisplayName("GET /internal/membership/access without internal token returns 401")
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
                        .header("X-Internal-Token", "test-token")
                        .param("accountId", "acc-1")
                        .param("requiredPlanLevel", "VIP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
