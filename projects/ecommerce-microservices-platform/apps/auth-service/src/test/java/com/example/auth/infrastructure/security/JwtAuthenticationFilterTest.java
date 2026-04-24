package com.example.auth.infrastructure.security;

import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.ParsedToken;
import com.example.auth.domain.service.TokenParser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위 테스트")
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private TokenParser tokenParser;

    @Mock
    private AccessTokenBlocklist accessTokenBlocklist;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContextAfter() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보를 설정한다")
    void validBearerToken_setsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        given(tokenParser.parse("valid-token")).willReturn(new ParsedToken(userId, "test@example.com"));
        given(accessTokenBlocklist.isBlocked("valid-token")).willReturn(false);
        given(accessTokenBlocklist.isUserBlocked(userId)).willReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isEqualTo(userId.toString());
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("blacklist에 등록된 토큰이면 SecurityContext를 설정하지 않고 필터를 통과한다")
    void blockedToken_clearsContextAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        given(tokenParser.parse("blocked-token")).willReturn(new ParsedToken(userId, "test@example.com"));
        given(accessTokenBlocklist.isBlocked("blocked-token")).willReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blocked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 정보를 설정하지 않고 필터를 통과한다")
    void noAuthorizationHeader_skipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(tokenParser).shouldHaveNoInteractions();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 인증 정보를 설정하지 않고 필터를 통과한다")
    void noBearer_prefix_skipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic sometoken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(tokenParser).shouldHaveNoInteractions();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 JWT이면 SecurityContext를 초기화하고 필터를 통과한다")
    void invalidJwtToken_clearsContextAndContinues() throws Exception {
        given(tokenParser.parse("invalid-token")).willThrow(new JwtException("bad token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("IllegalArgumentException이면 SecurityContext를 초기화하고 필터를 통과한다")
    void illegalArgumentToken_clearsContextAndContinues() throws Exception {
        given(tokenParser.parse(any())).willThrow(new IllegalArgumentException("invalid UUID"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-uuid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("accessTokenBlocklist 조회 실패 시 fail-open으로 인증 컨텍스트를 설정한다")
    void blocklist_queryFails_failsOpenAndSetsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        given(tokenParser.parse("valid-token")).willReturn(new ParsedToken(userId, "test@example.com"));
        given(accessTokenBlocklist.isBlocked("valid-token"))
            .willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isEqualTo(userId.toString());
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("userId가 차단된 사용자이면 SecurityContext를 설정하지 않고 필터를 통과한다")
    void blockedUser_clearsContextAndContinues() throws Exception {
        UUID userId = UUID.randomUUID();
        given(tokenParser.parse("valid-token")).willReturn(new ParsedToken(userId, "test@example.com"));
        given(accessTokenBlocklist.isBlocked("valid-token")).willReturn(false);
        given(accessTokenBlocklist.isUserBlocked(userId)).willReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(filterChain).should().doFilter(request, response);
    }

    @Test
    @DisplayName("userId 차단 조회 실패 시 fail-open으로 인증 컨텍스트를 설정한다")
    void userBlocklist_queryFails_failsOpenAndSetsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        given(tokenParser.parse("valid-token")).willReturn(new ParsedToken(userId, "test@example.com"));
        given(accessTokenBlocklist.isBlocked("valid-token")).willReturn(false);
        given(accessTokenBlocklist.isUserBlocked(userId))
            .willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isEqualTo(userId.toString());
        then(filterChain).should().doFilter(request, response);
    }
}
