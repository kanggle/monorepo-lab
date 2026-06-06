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
 * Unit tests for {@link InternalAuthFilter} JWT-only behaviour (TASK-BE-319a, ADR-005 단계 4a):
 * a request to {@code /internal/**} is accepted only when it carries a valid GAP
 * {@code client_credentials} JWT ({@code Authorization: Bearer}); the legacy {@code X-Internal-Token}
 * path was removed. Otherwise it is rejected with 403 PERMISSION_DENIED (fail-closed). The 'test' and
 * 'standalone' profiles bypass the check. The JWT path is verified against a mocked {@link JwtDecoder}.
 */
class InternalAuthFilterTest {

    private static final String INTERNAL_PATH = "/internal/security/login-history";
    private static final String PUBLIC_PATH = "/api/whatever";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Environment profile(String... profiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        return env;
    }

    private InternalAuthFilter filter(Environment env, JwtDecoder decoder) {
        return new InternalAuthFilter(objectMapper, env, decoder);
    }

    @Test
    @DisplayName("유효한 GAP JWT(Bearer) → 통과")
    void validBearerJwt_passes() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("good-jwt")).thenReturn(mock(Jwt.class));
        InternalAuthFilter filter = filter(profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("Authorization", "Bearer good-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(decoder, times(1)).decode("good-jwt");
    }

    @Test
    @DisplayName("TASK-BE-319a: X-Internal-Token 만 보낸 요청 → 403 (X-token 경로 제거됨)")
    void xInternalTokenOnly_rejected() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        InternalAuthFilter filter = filter(profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "any-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(decoder, never()).decode(any());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PERMISSION_DENIED");
    }

    @Test
    @DisplayName("자격증명 없음 → 403 PERMISSION_DENIED (fail-closed)")
    void noCredentials_rejected() throws ServletException, IOException {
        InternalAuthFilter filter = filter(profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PERMISSION_DENIED");
    }

    @Test
    @DisplayName("유효하지 않은 JWT(Bearer) → 403")
    void invalidBearerJwt_rejected() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("bad-jwt")).thenThrow(new BadJwtException("invalid signature"));
        InternalAuthFilter filter = filter(profile("prod"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("Authorization", "Bearer bad-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("test 프로파일 → 우회(통과, JWT 불필요)")
    void testProfile_bypassPasses() throws ServletException, IOException {
        JwtDecoder decoder = mock(JwtDecoder.class);
        InternalAuthFilter filter = filter(profile("test"), decoder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(decoder, never()).decode(any());
    }

    @Test
    @DisplayName("standalone 프로파일 → 우회(통과)")
    void standaloneProfile_bypassPasses() throws ServletException, IOException {
        InternalAuthFilter filter = filter(profile("standalone"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("/internal/** 외 경로는 검증 없이 통과")
    void nonInternalPath_passes() throws ServletException, IOException {
        InternalAuthFilter filter = filter(profile("prod"), mock(JwtDecoder.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", PUBLIC_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
