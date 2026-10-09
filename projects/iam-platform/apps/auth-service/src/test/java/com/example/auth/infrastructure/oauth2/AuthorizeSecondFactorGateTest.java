package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.AccountSecondFactorService;
import com.example.auth.domain.session.PrincipalDetailKeys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S2b — the one point after BOTH login producers where an enrolled account is held at the second
 * step (F6), and where an account without an enrollment is untouched (AC-3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("authorize 2단계 게이트 (TASK-MONO-771 S2b)")
class AuthorizeSecondFactorGateTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000771";
    private static final String AUTHORIZE = "/oauth2/authorize";

    @Mock
    private AccountSecondFactorService service;

    @Mock
    private FilterChain chain;

    private AuthorizeSecondFactorGate gate;

    @BeforeEach
    void setUp() {
        gate = new AuthorizeSecondFactorGate(AUTHORIZE, service);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("🔴 AC-3: 등록 없는 계정 · 폼 로그인 → 지금과 같다 (그대로 통과, 리다이렉트 없음)")
    void noEnrollment_passwordSession_untouched() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(false);

        MockHttpServletResponse response = run(authorize());

        verify(chain).doFilter(any(), any());
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("확정 등록 + 폼 세션(pwd) → 302 /mfa/challenge · code 미발급 · authorize 요청을 표준 캐시에 보관")
    void enrolled_passwordSession_heldAtChallenge() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(true);
        MockHttpServletRequest request = authorize();

        MockHttpServletResponse response = run(request);

        assertThat(response.getRedirectedUrl()).isEqualTo("/mfa/challenge");
        verify(chain, never()).doFilter(any(), any());
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        assertThat(saved).isNotNull();
        assertThat(saved.getRedirectUrl()).contains("/oauth2/authorize").contains("client_id=fan-platform-web");
    }

    @Test
    @DisplayName("🔴 F6: 확정 등록 + 소셜 세션(amr []) → 같은 /mfa/challenge (소셜이 옆문이 아니다)")
    void enrolled_socialSession_heldAtSameChallenge() throws Exception {
        signIn(list());
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(true);

        assertThat(run(authorize()).getRedirectedUrl()).isEqualTo("/mfa/challenge");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("세션 amr 에 mfa 가 이미 있으면 화면 없이 통과 (평소 SSO) — 등록 조회도 안 한다")
    void secondStepPassed_passes() throws Exception {
        signIn(list("pwd", "otp", "mfa"));

        MockHttpServletResponse response = run(authorize("&acr_values=mfa"));

        verify(chain).doFilter(any(), any());
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("단계 상승 acr_values=mfa · 등록 없음 → /mfa/setup")
    void stepUp_notEnrolled_goesToSetup() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(false);

        assertThat(run(authorize("&acr_values=openid%20mfa")).getRedirectedUrl()).isEqualTo("/mfa/setup");
    }

    @Test
    @DisplayName("단계 상승 acr_values=mfa · 등록 있음 → /mfa/challenge")
    void stepUp_enrolled_goesToChallenge() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(true);

        assertThat(run(authorize("&acr_values=mfa")).getRedirectedUrl()).isEqualTo("/mfa/challenge");
    }

    @Test
    @DisplayName("등록 조회 실패 → challenge 로 보낸다 (fail-closed — code 없음)")
    void lookupFails_failClosed() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenThrow(new IllegalStateException("db down"));

        assertThat(run(authorize()).getRedirectedUrl()).isEqualTo("/mfa/challenge");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("prompt=none + 등록 → 화면을 못 보이므로 이 요청의 principal 을 비운다 (SAS → login_required, code 없음)")
    void promptNone_enrolled_principalEmptied() throws Exception {
        signIn(list("pwd"));
        when(service.hasConfirmedEnrollment(ACCOUNT)).thenReturn(true);

        MockHttpServletResponse response = run(authorize("&prompt=none"));

        verify(chain).doFilter(any(), any());
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("익명 요청 · authorize 가 아닌 경로 → 무관")
    void anonymousOrOtherPath_untouched() throws Exception {
        run(authorize());
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        gate.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest authorize() {
        return authorize("");
    }

    private static MockHttpServletRequest authorize(String extraQuery) {
        String query = "response_type=code&client_id=fan-platform-web&redirect_uri=http://localhost:3000/cb&state=s1"
                + extraQuery;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AUTHORIZE);
        request.setServletPath(AUTHORIZE);
        request.setQueryString(query);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            request.addParameter(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return request;
    }

    private static void signIn(ArrayList<String> amr) {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, "fan-platform");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, ACCOUNT);
        details.put(PrincipalDetailKeys.EMAIL, "member@example.com");
        details.put(PrincipalDetailKeys.AMR, amr);
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.authenticated(
                "member@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        token.setDetails(details);
        Authentication auth = token;
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static ArrayList<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }
}
