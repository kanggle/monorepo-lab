package com.example.account.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InternalApiFilter}.
 *
 * <p>TASK-BE-317 (ADR-005 단계 2): the filter is now <em>non-terminal</em>. It never writes a 401
 * itself — it only authenticates the request (populating the {@link SecurityContext}) when a valid
 * {@code X-Internal-Token} is present, or under the dev/test bypass. The final 401 (fail-closed) is
 * produced by the Spring Security {@code .authenticated()} rule, exercised in the slice/IT tests.
 * Therefore these tests assert: (a) the chain always continues (no self-reject), and (b) whether an
 * {@code Authentication} was set.
 */
class InternalApiFilterTest {

    private static final String INTERNAL_PATH = "/internal/accounts/acc-1/status";
    private static final String PUBLIC_PATH = "/api/accounts/signup";
    private static final String CONFIGURED_TOKEN = "expected-token";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("토큰 설정 + 일치 헤더 → 인증 컨텍스트 설정 + 통과")
    void configuredToken_matchingHeader_authenticates() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", CONFIGURED_TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNotNull();
        assertThat(currentAuth().isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 누락 → 인증 미설정 + 통과(거부는 .authenticated() 가 담당)")
    void configuredToken_missingHeader_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
        // The filter does not write a status itself anymore.
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 불일치 → 인증 미설정 + 통과")
    void configuredToken_wrongHeader_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("토큰 미설정 + bypass=false + /internal/** → 인증 미설정 + 통과(fail-closed 는 .authenticated())")
    void unconfigured_noBypass_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("토큰 미설정 + bypass=true + /internal/** → 인증 설정 + 통과 (dev/test 우회)")
    void unconfigured_withBypass_authenticates() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNotNull();
        assertThat(currentAuth().isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("토큰 설정 + bypass=true 무시 + 헤더 불일치 → 인증 미설정 (bypass 는 토큰 미설정 시에만 의미)")
    void configuredToken_bypassIgnored_wrongHeader_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("/internal/** 외 경로는 토큰 검증 없이 통과하며 인증을 설정하지 않는다")
    void nonInternalPath_skipsAuth() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", PUBLIC_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }
}
