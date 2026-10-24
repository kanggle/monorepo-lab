package com.example.account.infrastructure.config;

import com.example.account.application.result.AccountMetricsDailyResult;
import com.example.account.application.service.AccountMetricsQueryService;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.AccountMetricsController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-3 (TASK-MONO-811) — {@code GET /internal/account-metrics/daily} behind the real
 * {@code /internal/**} chain (bypass OFF, like {@link InternalDualAuthSliceTest}). A token the decoder
 * rejects — what the production validator chain does to a valid-issuer token without
 * {@code internal.invoke} ({@code SecurityConfigInternalValidatorTest}) — never reaches the aggregate.
 * 🔵 account-service answers that with <b>401 UNAUTHORIZED</b>, its existing {@code /internal/**}
 * contract (TASK-BE-319b), not security-service's 403.
 */
@WebMvcTest(AccountMetricsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("/internal/account-metrics/daily — internal.invoke gate (TASK-MONO-811 AC-3)")
class AccountMetricsInternalAuthSliceTest {

    private static final String PATH = "/internal/account-metrics/daily"
            + "?tenantId=fan-platform&from=2026-09-30T15:00:00Z&to=2026-10-23T05:05:00Z";

    @Autowired MockMvc mockMvc;

    @MockitoBean AccountMetricsQueryService metricsQueryService;
    @MockitoBean JwtDecoder internalJwtDecoder;

    @Test
    @DisplayName("workload JWT with internal.invoke → 200")
    void systemToken200() throws Exception {
        given(internalJwtDecoder.decode("system-jwt")).willReturn(Jwt.withTokenValue("system-jwt")
                .header("alg", "RS256")
                .subject("admin-service-client")
                .claim("scope", "internal.invoke")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
        given(metricsQueryService.daily(any(), any(), any())).willReturn(new AccountMetricsDailyResult(List.of()));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer system-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 token without internal.invoke (rejected by the validator chain) → 401 UNAUTHORIZED, nothing computed")
    void scopelessToken401() throws Exception {
        given(internalJwtDecoder.decode("user-jwt")).willThrow(new JwtValidationException(
                "missing internal.invoke", List.of(new OAuth2Error("insufficient_scope"))));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer user-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("no credential → 401 UNAUTHORIZED")
    void noCredential401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(metricsQueryService);
    }
}
