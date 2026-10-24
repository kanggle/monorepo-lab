package com.example.security.service.infrastructure.config;

import com.example.security.service.query.SecurityMetricsQueryService;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
import com.example.security.service.query.internal.QueryExceptionHandler;
import com.example.security.service.query.internal.SecurityMetricsQueryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-3 (TASK-MONO-811) — {@code GET /internal/security/metrics/daily} is behind the real
 * {@code /internal/**} gate: no {@code test}/{@code standalone} profile is active, so
 * {@link InternalAuthFilter} does not bypass, and the decoder runs the <b>actual</b> validator chain
 * ({@link InternalJwtDecoderConfig#internalTokenValidator}) on the presented token. A valid-issuer
 * token without {@code internal.invoke} (the shape of a user token on the shared IAM issuer) → 403
 * PERMISSION_DENIED and the aggregate is never computed.
 */
@WebMvcTest(controllers = SecurityMetricsQueryController.class)
@Import({QueryExceptionHandler.class, InternalAuthFilter.class, SecurityMetricsInternalAuthSliceTest.Decoder.class})
@DisplayName("/internal/security/metrics/daily — internal.invoke gate (TASK-MONO-811 AC-3)")
class SecurityMetricsInternalAuthSliceTest {

    private static final String ISSUER = "http://localhost:8081";
    private static final String PATH = "/internal/security/metrics/daily";

    /** A decoder whose signature step is a lookup, and whose validation is the production chain. */
    @TestConfiguration
    static class Decoder {
        @Bean
        JwtDecoder internalJwtDecoder() {
            OAuth2TokenValidator<Jwt> chain = InternalJwtDecoderConfig.internalTokenValidator(ISSUER, "internal.invoke");
            return token -> {
                Jwt jwt = switch (token) {
                    case "system-jwt" -> jwt("internal.invoke");
                    case "user-jwt" -> jwt(null);
                    case "other-scope-jwt" -> jwt("openid profile");
                    default -> throw new BadJwtException("unknown token");
                };
                OAuth2TokenValidatorResult r = chain.validate(jwt);
                if (r.hasErrors()) {
                    throw new JwtValidationException("invalid token", r.getErrors());
                }
                return jwt;
            };
        }

        private static Jwt jwt(String scope) {
            Jwt.Builder b = Jwt.withTokenValue("t")
                    .header("alg", "RS256")
                    .subject("admin-service-client")
                    .issuer(ISSUER)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300));
            if (scope != null) {
                b.claim("scope", scope);
            }
            return b.build();
        }
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean SecurityMetricsQueryService metricsQueryService;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return get(PATH)
                .param("tenantId", "fan-platform")
                .param("from", "2026-10-18T15:00:00Z")
                .param("to", "2026-10-23T05:05:00Z");
    }

    @Test
    @DisplayName("workload token with internal.invoke → 200")
    void systemToken200() throws Exception {
        when(metricsQueryService.daily(any(), any(), any())).thenReturn(
                new SecurityMetricsDailyView("fan-platform", "2026-10-18T15:00:00Z", "2026-10-23T05:05:00Z", List.of()));

        mockMvc.perform(request().header("Authorization", "Bearer system-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 valid-issuer token WITHOUT internal.invoke (user token shape) → 403 PERMISSION_DENIED, nothing computed")
    void userToken403() throws Exception {
        mockMvc.perform(request().header("Authorization", "Bearer user-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("token with another scope → 403")
    void otherScope403() throws Exception {
        mockMvc.perform(request().header("Authorization", "Bearer other-scope-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(metricsQueryService);
    }

    @Test
    @DisplayName("no credential → 403")
    void noCredential403() throws Exception {
        mockMvc.perform(request())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(metricsQueryService);
    }
}
