package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorFacetQueryUseCase;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-618 — controller slice for {@code GET /internal/operators/facet} (auth-to-admin.md). Same
 * bypass {@code /internal/**} chain as {@link OperatorAssignmentCheckControllerSliceTest}; the production
 * fail-closed chain is the same chain that test's sibling asserts.
 */
@WebMvcTest(OperatorFacetController.class)
@Import(OperatorFacetControllerSliceTest.BypassInternalSecurity.class)
@DisplayName("OperatorFacetController slice tests (TASK-BE-618)")
class OperatorFacetControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000618";
    private static final String IDENTITY = "0199de71-0000-7000-8000-000000000618";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorFacetQueryUseCase facetQueryUseCase;

    @Test
    @DisplayName("accountId + identityId → 200 {operatorFaceted: true}")
    void faceted_true() throws Exception {
        given(facetQueryUseCase.isOperatorFaceted(eq(ACCOUNT), eq(IDENTITY))).willReturn(true);

        mockMvc.perform(get("/internal/operators/facet")
                        .param("accountId", ACCOUNT)
                        .param("identityId", IDENTITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(true));
    }

    @Test
    @DisplayName("identityId 생략 → null 로 전달 · 200 {operatorFaceted: false}")
    void identityOmitted_false() throws Exception {
        given(facetQueryUseCase.isOperatorFaceted(eq(ACCOUNT), isNull())).willReturn(false);

        mockMvc.perform(get("/internal/operators/facet").param("accountId", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorFaceted").value(false));
    }

    @Test
    @DisplayName("accountId 누락 → 400")
    void missingAccountId_400() throws Exception {
        mockMvc.perform(get("/internal/operators/facet").param("identityId", IDENTITY))
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
