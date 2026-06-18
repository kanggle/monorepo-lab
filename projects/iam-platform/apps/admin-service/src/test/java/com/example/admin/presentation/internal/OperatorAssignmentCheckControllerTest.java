package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorAssignmentCheckUseCase;
import com.example.admin.infrastructure.config.InternalApiFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — controller slice tests for
 * {@code GET /internal/operator-assignments/check}.
 *
 * <p>Uses a minimal test {@code /internal/**} chain with the dev/test bypass
 * filter so the controller logic + response shape are asserted without a JWKS
 * (the production fail-closed chain is asserted separately in
 * {@link OperatorAssignmentInternalChainSliceTest}).
 */
@WebMvcTest(OperatorAssignmentCheckController.class)
@Import(OperatorAssignmentCheckControllerTest.BypassInternalSecurity.class)
@DisplayName("OperatorAssignmentCheckController slice tests (TASK-BE-327)")
class OperatorAssignmentCheckControllerTest {

    private static final String SUB = "00000000-0000-7000-8000-0000000000a1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorAssignmentCheckUseCase checkUseCase;

    @Test
    @DisplayName("assigned=true + org_scope 설정 → 200 {assigned:true, orgScope:[...]}")
    void assignedTrue_withOrgScope() throws Exception {
        given(checkUseCase.check(eq(SUB), any(), eq("acme-corp")))
                .willReturn(new OperatorAssignmentCheckUseCase.Result(true, java.util.List.of("dept-sales")));

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "acme-corp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.orgScope[0]").value("dept-sales"));
    }

    @Test
    @DisplayName("assigned=true + org_scope 미설정(NULL) → 200 {assigned:true, orgScope:null} (net-zero)")
    void assignedTrue_nullOrgScope() throws Exception {
        given(checkUseCase.check(eq(SUB), any(), eq("acme-corp")))
                .willReturn(new OperatorAssignmentCheckUseCase.Result(true, null));

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "acme-corp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.orgScope").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("TASK-MONO-295: X-Subject-Email 헤더 → use case 의 subjectEmail 인자로 전달 (dual-key)")
    void subjectEmailHeader_threadedToUseCase() throws Exception {
        given(checkUseCase.check(eq(SUB), eq("acme-operator@example.com"), eq("acme-corp")))
                .willReturn(new OperatorAssignmentCheckUseCase.Result(true, null));

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "acme-corp")
                        .header("X-Subject-Email", "acme-operator@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }

    @Test
    @DisplayName("assigned=false (미할당/unknown/non-ACTIVE 모두) → 200 {assigned:false, orgScope:null}")
    void assignedFalse() throws Exception {
        given(checkUseCase.check(eq(SUB), any(), eq("globex")))
                .willReturn(new OperatorAssignmentCheckUseCase.Result(false, null));

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "globex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(false));
    }

    @Test
    @DisplayName("tenantId 파라미터 누락 → 400")
    void missingTenantId_returns400() throws Exception {
        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB))
                .andExpect(status().isBadRequest());
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
