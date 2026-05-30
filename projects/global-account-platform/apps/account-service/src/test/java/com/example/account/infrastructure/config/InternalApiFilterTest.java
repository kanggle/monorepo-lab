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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InternalApiFilter}.
 *
 * <p>TASK-BE-319b (ADR-005 단계 4b): the filter is JWT-only — the static {@code X-Internal-Token}
 * acceptance path was removed. It is non-terminal (never writes a 401 itself) and authenticates
 * {@code /internal/**} <em>only</em> under the dev/test/standalone bypass. The final 401
 * (fail-closed) is produced by the Spring Security {@code .authenticated()} rule against the
 * oauth2 resource-server, exercised in the slice/IT tests. These tests assert: (a) the chain always
 * continues (no self-reject), and (b) whether an {@code Authentication} was set.
 */
class InternalApiFilterTest {

    private static final String INTERNAL_PATH = "/internal/accounts/acc-1/status";
    private static final String PUBLIC_PATH = "/api/accounts/signup";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("bypass=true + /internal/** → 인증 설정 + 통과 (dev/test 우회)")
    void bypass_authenticates() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNotNull();
        assertThat(currentAuth().isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("TASK-BE-319b: bypass=false → X-Internal-Token 가 있어도 인증 미설정 + 통과 (X-token 경로 제거)")
    void noBypass_xInternalTokenIgnored_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "any-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        // The filter never reads X-Internal-Token and never rejects — the .authenticated() gate does.
        assertThat(currentAuth()).isNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("bypass=false + 자격증명 없음 → 인증 미설정 + 통과 (fail-closed 는 .authenticated())")
    void noBypass_noCredentials_passesUnauthenticated() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("bypass=true 라도 /internal/** 외 경로는 인증을 설정하지 않는다")
    void bypass_nonInternalPath_skipsAuth() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", PUBLIC_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(currentAuth()).isNull();
    }
}
