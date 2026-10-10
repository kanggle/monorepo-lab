package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorConsoleEligibilityQueryUseCase;
import com.example.admin.infrastructure.config.InternalApiFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S4 — controller slice for {@code GET /internal/operators/console-eligibility}
 * (auth-to-admin.md). Same bypass {@code /internal/**} chain as {@link OperatorFacetControllerSliceTest}; the
 * production fail-closed chain (401 without the IAM workload JWT) is the same chain its siblings assert.
 */
@WebMvcTest(OperatorConsoleEligibilityController.class)
@Import(OperatorConsoleEligibilityControllerSliceTest.BypassInternalSecurity.class)
@DisplayName("OperatorConsoleEligibilityController slice tests (TASK-MONO-772 S4)")
class OperatorConsoleEligibilityControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorConsoleEligibilityQueryUseCase eligibilityQueryUseCase;

    @Test
    @DisplayName("eligible → 200 {eligible: true}")
    void eligible_true() throws Exception {
        given(eligibilityQueryUseCase.isConsoleEligible(ACCOUNT)).willReturn(true);

        mockMvc.perform(get("/internal/operators/console-eligibility").param("accountId", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    @DisplayName("«아니오» 도 200 {eligible: false} — 오류 아님")
    void notEligible_200_false() throws Exception {
        given(eligibilityQueryUseCase.isConsoleEligible(ACCOUNT)).willReturn(false);

        mockMvc.perform(get("/internal/operators/console-eligibility").param("accountId", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    @DisplayName("accountId 누락 → 400 · 판정 호출 없음")
    void missingAccountId_400() throws Exception {
        mockMvc.perform(get("/internal/operators/console-eligibility"))
                .andExpect(status().isBadRequest());
        then(eligibilityQueryUseCase).should(never()).isConsoleEligible(anyString());
    }

    @Test
    @DisplayName("accountId 공백 → 400 VALIDATION_ERROR · 판정 호출 없음")
    void blankAccountId_400() throws Exception {
        mockMvc.perform(get("/internal/operators/console-eligibility").param("accountId", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        then(eligibilityQueryUseCase).should(never()).isConsoleEligible(anyString());
    }

    @TestConfiguration
    @EnableWebSecurity
    static class BypassInternalSecurity {
        @Bean
        InternalApiFilter internalApiFilter() {
            return new InternalApiFilter(true); // dev/test bypass
        }

        @Bean
        SecurityFilterChain testInternalChain(HttpSecurity http, InternalApiFilter internalApiFilter)
                throws Exception {
            http
                    .securityMatcher("/internal/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
