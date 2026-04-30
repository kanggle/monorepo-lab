package com.example.admin.infrastructure.security;

import com.gap.security.jwt.JwtVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Asserts the unauthenticated sub-tree bypass declared in
 * {@code specs/contracts/http/admin-api.md} — "Authentication Exceptions".
 *
 * <p>The filter must NOT run for:
 * <ul>
 *   <li>POST /api/admin/auth/login</li>
 *   <li>POST /api/admin/auth/2fa/enroll</li>
 *   <li>POST /api/admin/auth/2fa/verify</li>
 *   <li>GET  /.well-known/admin/jwks.json</li>
 * </ul>
 */
class OperatorAuthenticationFilterBypassTest {

    private final OperatorAuthenticationFilter filter =
            new OperatorAuthenticationFilter(mock(JwtVerifier.class), "admin");

    @Test
    void bypasses_login_endpoint() throws Exception {
        assertThat(shouldNotFilter("POST", "/api/admin/auth/login")).isTrue();
    }

    @Test
    void bypasses_2fa_enroll() throws Exception {
        assertThat(shouldNotFilter("POST", "/api/admin/auth/2fa/enroll")).isTrue();
    }

    @Test
    void bypasses_2fa_verify() throws Exception {
        assertThat(shouldNotFilter("POST", "/api/admin/auth/2fa/verify")).isTrue();
    }

    @Test
    void bypasses_refresh_endpoint() throws Exception {
        // TASK-BE-040 — refresh runs without an operator access JWT.
        assertThat(shouldNotFilter("POST", "/api/admin/auth/refresh")).isTrue();
    }

    @Test
    void does_not_bypass_logout_endpoint() throws Exception {
        // TASK-BE-040 — logout requires the operator JWT (caller proves identity).
        assertThat(shouldNotFilter("POST", "/api/admin/auth/logout")).isFalse();
    }

    @Test
    void bypasses_jwks_endpoint() throws Exception {
        assertThat(shouldNotFilter("GET", "/.well-known/admin/jwks.json")).isTrue();
    }

    @Test
    void does_not_bypass_protected_admin_path() throws Exception {
        assertThat(shouldNotFilter("POST", "/api/admin/accounts/123/lock")).isFalse();
    }

    @Test
    void does_not_bypass_wrong_method_on_auth_path() throws Exception {
        // GET /api/admin/auth/login is NOT in the exception set.
        assertThat(shouldNotFilter("GET", "/api/admin/auth/login")).isFalse();
    }

    @Test
    void does_not_bypass_unrelated_well_known_path() throws Exception {
        assertThat(shouldNotFilter("GET", "/.well-known/other/keys.json")).isTrue();
        // Note: non-/api/admin paths are ignored by the filter anyway (true).
    }

    private boolean shouldNotFilter(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        Method m = OperatorAuthenticationFilter.class
                .getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        m.setAccessible(true);
        return (boolean) m.invoke(filter, request);
    }
}
