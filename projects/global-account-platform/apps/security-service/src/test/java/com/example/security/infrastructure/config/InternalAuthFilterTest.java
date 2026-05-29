package com.example.security.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalAuthFilter} dual-allow behaviour (TASK-BE-317, ADR-005 단계 2):
 * a request to {@code /internal/**} is accepted when it carries a valid {@code X-Internal-Token}
 * OR a valid GAP {@code client_credentials} JWT; otherwise it is rejected with 403 PERMISSION_DENIED
 * (fail-closed, contract preserved). The JWT path is verified against a mocked {@link JwtDecoder}.
 */
class InternalAuthFilterTest {

    private static final String INTERNAL_PATH = "/internal/security/login-history";
    private static final String PUBLIC_PATH = "/api/whatever";
    private static final String CONFIGURED_TOKEN = "expected-token";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Environment profile(String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    private InternalAuthFilter filter(String token, Environment env, JwtDecoder decoder) {
        return new InternalAuthFilter(token, objectMapper, env, decoder);
    }

    @Test
    @DisplayName("토큰 설정 + 일치 X-Internal-Token → 통과")
    void configuredToken_matchingHeader_passes() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", CONFIGURED_TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(decoder, never()).decode(any());
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 누락 + Bearer 없음 → 403 PERMISSION_DENIED")
    void configuredToken_missingHeader_rejected() throws ServletException, IOException {
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PERMISSION_DENIED");
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 불일치 + Bearer 없음 → 403")
    void configuredToken_wrongHeader_rejected() throws ServletException, IOException {
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("유효한 GAP JWT(Bearer) → 통과 (X-Internal-Token 없이)")
    void validBearerJwt_passes() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("good-jwt")).thenReturn(mock(Jwt.class));
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("Authorization", "Bearer good-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(decoder, times(1)).decode("good-jwt");
    }

    @Test
    @DisplayName("유효하지 않은 JWT(Bearer) + X-Internal-Token 없음 → 403")
    void invalidBearerJwt_rejected() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("bad-jwt")).thenThrow(new BadJwtException("invalid signature"));
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("Authorization", "Bearer bad-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("토큰 미설정 + test 프로파일 → 우회(통과)")
    void blankToken_testProfile_bypassPasses() throws ServletException, IOException {
        InternalAuthFilter filter = filter("", profile("test"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("토큰 미설정 + prod 프로파일 + 유효 JWT → 통과 (JWT 경로는 살아있음)")
    void blankToken_prodProfile_validJwtPasses() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("good-jwt")).thenReturn(mock(Jwt.class));
        InternalAuthFilter filter = filter("", profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("Authorization", "Bearer good-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("토큰 미설정 + prod 프로파일 + 자격증명 없음 → 403 (fail-closed)")
    void blankToken_prodProfile_noCredentials_rejected() throws ServletException, IOException {
        InternalAuthFilter filter = filter("", profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("/internal/** 외 경로는 검증 없이 통과")
    void nonInternalPath_passes() throws ServletException, IOException {
        InternalAuthFilter filter = filter(CONFIGURED_TOKEN, profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", PUBLIC_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
