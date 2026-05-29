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
 * TASK-BE-317 (ADR-005 단계 2) — dual-allow on account-service {@code /internal/**}.
 *
 * <p>Verifies the full Spring Security chain (InternalApiFilter + oauth2ResourceServer +
 * {@code .authenticated()}): a request is accepted with <em>either</em> a valid
 * {@code X-Internal-Token} (AC-4) <em>or</em> a valid GAP {@code client_credentials} JWT (AC-3),
 * and rejected with 401 when neither is present (AC-5, fail-closed). The JWT decoder is mocked so
 * no real JWKS is needed; the {@code "test"} profile is intentionally NOT active so the bypass stays
 * off and the real token check runs.
 */
@WebMvcTest(AccountStatusQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.api.token=expected-token",
        "internal.api.bypass-when-unconfigured=false"
})
@DisplayName("Internal /internal/** dual-allow slice tests (TASK-BE-317)")
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
    @DisplayName("AC-4: 유효 X-Internal-Token → 200 (기존 호출자 회귀 0)")
    void validInternalToken_returns200() throws Exception {
        stubStatus();
        mockMvc.perform(get("/internal/accounts/acc-1/status")
                        .header("X-Internal-Token", "expected-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("AC-3: 유효 GAP JWT(Bearer) → 200")
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
