package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorAssignmentCheckUseCase;
import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.config.SecurityConfig;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2) — verifies the production
 * {@code @Order(0)} {@code /internal/**} resource-server chain on admin-service
 * (AC-4): a missing/invalid GAP JWT → 401 {@code UNAUTHORIZED}; a valid GAP
 * {@code client_credentials} JWT → 200. The decoder is mocked so no real JWKS is
 * needed; the {@code "test"} profile is intentionally NOT active and the bypass
 * stays off so the real fail-closed resource-server path runs.
 *
 * <p>Mirrors account-service {@code InternalDualAuthSliceTest}.
 */
@WebMvcTest(OperatorAssignmentCheckController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("admin /internal/** JWT-only chain slice tests (TASK-BE-327, fail-closed)")
class OperatorAssignmentInternalChainSliceTest {

    private static final String SUB = "00000000-0000-7000-8000-0000000000a1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorAssignmentCheckUseCase checkUseCase;

    // Overrides the real internalJwtDecoder so the JWT path runs without a JWKS.
    @MockitoBean
    private JwtDecoder internalJwtDecoder;

    // Dependencies of the @Order(2) operator chain's @Bean filters.
    @MockitoBean
    private JwtVerifier operatorJwtVerifier;
    @MockitoBean
    private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean
    private BootstrapTokenService bootstrapTokenService;

    @Test
    @DisplayName("자격증명 없음 → 401 UNAUTHORIZED (fail-closed)")
    void noCredentials_returns401() throws Exception {
        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "acme-corp"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("유효 GAP JWT(Bearer) → 200 {assigned:true}")
    void validBearerJwt_returns200() throws Exception {
        given(checkUseCase.check(eq(SUB), eq("acme-corp")))
                .willReturn(new com.example.admin.application.OperatorAssignmentCheckUseCase.Result(true, null, null));
        Jwt jwt = Jwt.withTokenValue("good-jwt")
                .header("alg", "RS256")
                .subject("auth-service-client")
                .claim("scope", "internal.invoke")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(internalJwtDecoder.decode("good-jwt")).willReturn(jwt);

        mockMvc.perform(get("/internal/operator-assignments/check")
                        .param("oidcSubject", SUB)
                        .param("tenantId", "acme-corp")
                        .header("Authorization", "Bearer good-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(true));
    }
}
