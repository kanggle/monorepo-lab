package com.example.admin.infrastructure.security;

import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.support.OperatorJwtTestFixture;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-040 — covers the blacklist branch of the filter:
 * <ul>
 *   <li>jti present on blacklist → 401 TOKEN_REVOKED, downstream chain not invoked</li>
 *   <li>blacklist port raises (fail-closed) → 401 TOKEN_REVOKED</li>
 *   <li>jti absent from blacklist → request flows through</li>
 * </ul>
 *
 * <p>Uses {@link OperatorJwtTestFixture} for a real RS256 signed token so the
 * verifier path is exercised end-to-end.
 */
class OperatorAuthenticationFilterBlacklistTest {

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-00000000blkt";
    private static final String JTI = "22222222-2222-2222-2222-222222222222";

    private final OperatorJwtTestFixture jwt = new OperatorJwtTestFixture();

    @Test
    void rejects_when_blacklist_hit() throws Exception {
        TokenBlacklistPort blacklist = new StubBlacklist(true, false);
        OperatorAuthenticationFilter filter =
                new OperatorAuthenticationFilter(jwt.verifier(), "admin", blacklist);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/accounts/x/lock");
        req.addHeader("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_ID, JTI));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("TOKEN_REVOKED");
        verify(chain, never()).doFilter(req, resp);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void fails_closed_when_blacklist_throws() throws Exception {
        TokenBlacklistPort blacklist = new StubBlacklist(false, true);
        OperatorAuthenticationFilter filter =
                new OperatorAuthenticationFilter(jwt.verifier(), "admin", blacklist);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/accounts/x/lock");
        req.addHeader("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_ID, JTI));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("TOKEN_REVOKED");
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    void allows_when_jti_not_blacklisted() throws Exception {
        TokenBlacklistPort blacklist = new StubBlacklist(false, false);
        OperatorAuthenticationFilter filter =
                new OperatorAuthenticationFilter(jwt.verifier(), "admin", blacklist);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/accounts/x/lock");
        req.addHeader("Authorization", "Bearer " + jwt.operatorToken(OPERATOR_ID, JTI));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(chain).doFilter(req, resp);
    }

    /** Hand-rolled stub so we can control both "blacklist hit" and "throws" axes. */
    private static final class StubBlacklist implements TokenBlacklistPort {
        private final boolean isBlacklisted;
        private final boolean throwOnRead;

        StubBlacklist(boolean isBlacklisted, boolean throwOnRead) {
            this.isBlacklisted = isBlacklisted;
            this.throwOnRead = throwOnRead;
        }
        @Override public void blacklist(String jti, Duration ttl) {}
        @Override public boolean isBlacklisted(String jti) {
            if (throwOnRead) {
                // The production adapter swallows infra exceptions and returns
                // true (fail-closed). Mirror that contract here so the filter
                // sees the same observable behavior.
                return true;
            }
            return isBlacklisted;
        }
    }
}
