package com.example.account.infrastructure.config;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.application.service.SocialSignupUseCase;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.AccountStatusQueryController;
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
 * TASK-BE-319b (ADR-005 단계 4b) — JWT-only auth on account-service {@code /internal/**}.
 *
 * <p>Verifies the full Spring Security chain (InternalApiFilter + oauth2ResourceServer +
 * {@code .authenticated()}): a request is accepted <em>only</em> with a valid GAP
 * {@code client_credentials} JWT, and rejected with 401 when it is absent — or when only the
 * (now-removed) {@code X-Internal-Token} is presented (fail-closed). The JWT decoder is mocked so no
 * real JWKS is needed; the {@code "test"} profile is intentionally NOT active and the bypass stays
 * off so the real resource-server path runs.
 */
@WebMvcTest(AccountStatusQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("Internal /internal/** JWT-only slice tests (TASK-BE-319b)")
class InternalDualAuthSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountStatusUseCase accountStatusUseCase;

    @MockitoBean
    private SocialSignupUseCase socialSignupUseCase;

    // Overrides the real internalJwtDecoder bean so the JWT path can be exercised without a JWKS.
    @MockitoBean
    private JwtDecoder internalJwtDecoder;

    private void stubStatus() {
        given(accountStatusUseCase.getStatus(eq("acc-1")))
                .willReturn(new AccountStatusResult("acc-1", "ACTIVE", Instant.now(), null));
    }

    @Test
    @DisplayName("TASK-BE-319b: X-Internal-Token 만 → 401 (X-token 경로 제거됨)")
    void xInternalTokenOnly_returns401() throws Exception {
        mockMvc.perform(get("/internal/accounts/acc-1/status")
                        .header("X-Internal-Token", "expected-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("유효 GAP JWT(Bearer) → 200")
    void validBearerJwt_returns200() throws Exception {
        stubStatus();
        Jwt jwt = Jwt.withTokenValue("good-jwt")
                .header("alg", "RS256")
                .subject("account-service-client")
                .claim("scope", "internal.invoke")
                .claim("tenant_id", "global-account-platform")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(internalJwtDecoder.decode("good-jwt")).willReturn(jwt);

        mockMvc.perform(get("/internal/accounts/acc-1/status")
                        .header("Authorization", "Bearer good-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("AC-5: 자격증명 없음 → 401 UNAUTHORIZED (fail-closed)")
    void noCredentials_returns401() throws Exception {
        mockMvc.perform(get("/internal/accounts/acc-1/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("잘못된 X-Internal-Token + Bearer 없음 → 401")
    void wrongInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/accounts/acc-1/status")
                        .header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
