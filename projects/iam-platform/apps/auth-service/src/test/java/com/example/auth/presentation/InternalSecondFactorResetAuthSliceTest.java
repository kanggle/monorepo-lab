package com.example.auth.presentation;

import com.example.auth.application.AccountSecondFactorResetUseCase;
import com.example.auth.application.AccountSecondFactorResetUseCase.Outcome;
import com.example.auth.application.AccountSecondFactorResetUseCase.Result;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S6 — the reset command is a destructive security operation, so the {@code /internal/auth/**}
 * fail-closed gate is re-proved on THIS path (same setup as {@code InternalCredentialAuthSliceTest}: bypass OFF,
 * the real resource-server chain, the JWT decoder mocked).
 *
 * <ul>
 *   <li>no Bearer → 401, the handler never runs;</li>
 *   <li>a token the internal validator rejects — a user token without {@code internal.invoke} is rejected by
 *       {@code SecurityConfig.internalTokenValidator} inside the decoder ({@code SecurityConfigInternalValidatorTest}
 *       pins that predicate) — → 401, the handler never runs;</li>
 *   <li>a valid workload token → the handler runs.</li>
 * </ul>
 */
@WebMvcTest(InternalSecondFactorResetController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("Internal second-factor reset — /internal/auth/** JWT gate (TASK-MONO-771 S6)")
class InternalSecondFactorResetAuthSliceTest {

    private static final String PATH = "/internal/auth/accounts/acc-s6/second-factor/reset";
    private static final String BODY = "{\"reason\":\"lost device\",\"operatorId\":\"op-1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSecondFactorResetUseCase useCase;

    @MockitoBean
    private JwtDecoder internalJwtDecoder;

    @Test
    @DisplayName("🔴 Bearer 없음 → 401 UNAUTHORIZED · 리셋 미실행")
    void noBearer_401() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(useCase, never()).reset(anyString(), any());
    }

    @Test
    @DisplayName("🔴 internal.invoke 없는 토큰(사용자 토큰 — 검증기가 거절) → 401 · 리셋 미실행")
    void userTokenWithoutInternalScope_401() throws Exception {
        given(internalJwtDecoder.decode("user-jwt")).willThrow(new JwtValidationException(
                "missing internal.invoke", List.of(new OAuth2Error("invalid_token", "scope", null))));

        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer user-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(useCase, never()).reset(anyString(), any());
    }

    @Test
    @DisplayName("유효 워크로드 JWT → 핸들러 실행 200")
    void workloadToken_200() throws Exception {
        Jwt jwt = Jwt.withTokenValue("good-jwt")
                .header("alg", "RS256")
                .subject("admin-service-client")
                .issuer("http://localhost:8081")
                .claim("scope", "internal.invoke")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(internalJwtDecoder.decode("good-jwt")).willReturn(jwt);
        given(useCase.reset("acc-s6", "op-1")).willReturn(
                new Result(Outcome.RESET, "acc-s6", Instant.parse("2026-10-09T03:00:00Z"), true));

        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer good-jwt")
                        .header("X-Operator-ID", "op-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasConfirmed").value(true));
    }
}
