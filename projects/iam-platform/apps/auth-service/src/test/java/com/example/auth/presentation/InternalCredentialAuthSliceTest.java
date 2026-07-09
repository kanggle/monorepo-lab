package com.example.auth.presentation;

import com.example.auth.application.BackfillCredentialIdentityUseCase;
import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.ResolveCredentialAccountIdUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.jwt.JwksEndpointProvider;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-487 (ADR-005 단계 4 — auth-service receiver) — JWT-only auth on the credential/action
 * {@code /internal/auth/**} endpoints, verified through the full Spring Security chain
 * (InternalApiFilter + oauth2ResourceServer + {@code .authenticated()}).
 *
 * <p>The bypass is intentionally OFF ({@code internal.api.bypass-when-unconfigured=false}) so the real
 * resource-server path runs. The JWT decoder is mocked so no real JWKS is needed. Covers:
 * <ul>
 *   <li>a credential/action request with no Bearer JWT → 401 (fail-closed, the closed gap);</li>
 *   <li>the same with a valid GAP {@code client_credentials} JWT → handler runs (201);</li>
 *   <li>{@code /internal/auth/jwks} stays public — the gateway fetches keys to validate tokens and
 *       cannot present one (regression guard for the matcher ordering).</li>
 * </ul>
 */
@WebMvcTest({InternalCredentialController.class, JwksController.class})
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("Internal /internal/auth/** JWT-only slice tests (TASK-BE-487)")
class InternalCredentialAuthSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCredentialUseCase createCredentialUseCase;

    @MockitoBean
    private ForceLogoutUseCase forceLogoutUseCase;

    @MockitoBean
    private BackfillCredentialIdentityUseCase backfillCredentialIdentityUseCase;

    @MockitoBean
    private ResolveCredentialAccountIdUseCase resolveCredentialAccountIdUseCase;

    @MockitoBean
    private JwksEndpointProvider jwksEndpointProvider;

    // Overrides the real internalJwtDecoder bean so the JWT path is exercised without a live JWKS.
    @MockitoBean
    private JwtDecoder internalJwtDecoder;

    private static final String CREDENTIALS_BODY = """
            { "accountId": "acc-1", "email": "user@example.com", "password": "pass1234" }
            """;

    @Test
    @DisplayName("Bearer 없음 → 401 UNAUTHORIZED (fail-closed, ADR-005 단계 4 갭 폐쇄)")
    void noBearer_returns401() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("유효 GAP client_credentials JWT(Bearer) → 201 (핸들러 실행)")
    void validBearerJwt_returns201() throws Exception {
        Instant createdAt = Instant.parse("2026-07-09T10:00:00Z");
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-1", createdAt));

        Jwt jwt = Jwt.withTokenValue("good-jwt")
                .header("alg", "RS256")
                .subject("account-service-client")
                .issuer("http://localhost:8081")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(internalJwtDecoder.decode("good-jwt")).willReturn(jwt);

        mockMvc.perform(post("/internal/auth/credentials")
                        .header("Authorization", "Bearer good-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("잘못된 Bearer JWT → 401 (fail-closed)")
    void invalidBearerJwt_returns401() throws Exception {
        given(internalJwtDecoder.decode("bad-jwt"))
                .willThrow(new org.springframework.security.oauth2.jwt.BadJwtException("invalid"));

        mockMvc.perform(post("/internal/auth/credentials")
                        .header("Authorization", "Bearer bad-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /internal/auth/jwks — Bearer 없이도 200 (공개 키 배포, 게이트웨이 검증용)")
    void jwks_publicWithoutBearer_returns200() throws Exception {
        given(jwksEndpointProvider.getJwks()).willReturn(Map.of("keys", java.util.List.of()));

        mockMvc.perform(get("/internal/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").exists());
    }
}
