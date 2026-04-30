package com.example.community.infrastructure.security;

import com.example.community.application.ActorContext;
import com.gap.security.jwt.JwtVerificationException;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAuthenticationFilter 단위 테스트")
class AccountAuthenticationFilterUnitTest {

    @Mock
    private JwtVerifier jwtVerifier;

    private AccountAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AccountAuthenticationFilter(jwtVerifier);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 토큰 + sub 포함 → SecurityContext에 ActorContext 설정 후 체인 통과")
    void doFilter_validToken_setsActorContextAndPassesChain() throws Exception {
        when(jwtVerifier.verify("valid.token")).thenReturn(Map.of("sub", "user-1"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        filter.doFilter(request, response, (req, resp) ->
                capturedAuth.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getPrincipal()).isInstanceOf(ActorContext.class);
        assertThat(((ActorContext) capturedAuth.get().getPrincipal()).accountId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → 401")
    void doFilter_missingAuthHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("'Bearer ' 로 시작하지 않는 헤더 → 401")
    void doFilter_malformedAuthHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        request.addHeader("Authorization", "Token xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("서명 검증 실패(JwtVerificationException) → 401")
    void doFilter_invalidToken_returns401() throws Exception {
        when(jwtVerifier.verify("bad.token"))
                .thenThrow(new JwtVerificationException("invalid signature"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        request.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("sub claim 없음 → 401")
    void doFilter_missingSubClaim_returns401() throws Exception {
        when(jwtVerifier.verify("no.sub.token")).thenReturn(Map.of("roles", List.of("USER")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        request.addHeader("Authorization", "Bearer no.sub.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("/api/community/ 로 시작하지 않는 경로 → 필터 바이패스(체인 통과, 401 아님)")
    void doFilter_pathOutsideCommunityApi_bypassesFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("roles 클레임 배열 → ROLE_ 접두사 권한 설정")
    void doFilter_rolesArrayClaim_setsGrantedAuthorities() throws Exception {
        when(jwtVerifier.verify("role.token"))
                .thenReturn(Map.of("sub", "user-2", "roles", List.of("USER", "MODERATOR")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/community/posts");
        request.addHeader("Authorization", "Bearer role.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        filter.doFilter(request, response, (req, resp) ->
                capturedAuth.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_MODERATOR");
    }
}
