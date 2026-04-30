package com.example.account.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InternalApiFilterTest {

    private static final String INTERNAL_PATH = "/internal/accounts/acc-1/status";
    private static final String PUBLIC_PATH = "/api/accounts/signup";
    private static final String CONFIGURED_TOKEN = "expected-token";

    @Test
    @DisplayName("토큰 미설정 + bypass=false + /internal/** → 401 (fail-closed)")
    void blankToken_noBypass_internalPath_rejected() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("UNAUTHORIZED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("null 토큰 + bypass=false + /internal/** → 401")
    void nullToken_noBypass_internalPath_rejected() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(null, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("토큰 미설정 + bypass=true + /internal/** → 통과 (dev/test 우회)")
    void blankToken_withBypass_internalPath_passes() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("토큰 설정 + 일치 헤더 → 통과")
    void configuredToken_matchingHeader_passes() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", CONFIGURED_TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 누락 → 401")
    void configuredToken_missingHeader_rejected() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("토큰 설정 + 헤더 불일치 → 401")
    void configuredToken_wrongHeader_rejected() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("토큰 설정 + bypass=true 무시 + 헤더 불일치 → 401 (bypass 는 토큰 미설정 시에만 의미)")
    void configuredToken_bypassIgnored_wrongHeader_rejected() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter(CONFIGURED_TOKEN, true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", INTERNAL_PATH);
        req.addHeader("X-Internal-Token", "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("/internal/** 외 경로는 토큰 검증 없이 통과 (token 미설정/bypass=false 라도)")
    void nonInternalPath_skipsAuth_evenWhenFailClosed() throws ServletException, IOException {
        InternalApiFilter filter = new InternalApiFilter("", false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", PUBLIC_PATH);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }
}
