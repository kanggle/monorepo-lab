package com.example.auth.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-609 — the {@code @Order(2)} chain reads a Bearer token only where it authenticates by
 * one ({@code /internal/**}). On the gateway-authenticated user paths the forwarded user token is
 * ignored, so it can no longer be rejected as a failed workload credential.
 */
@DisplayName("SecurityConfig.internalOnlyBearerTokenResolver() — Bearer 는 /internal/** 에서만 읽는다 (TASK-BE-609)")
class SecurityConfigBearerResolverTest {

    private static final BearerTokenResolver RESOLVER = SecurityConfig.internalOnlyBearerTokenResolver();

    private static MockHttpServletRequest withBearer(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        return request;
    }

    @ParameterizedTest(name = "{0} → 토큰을 읽는다")
    @ValueSource(strings = {"/internal/auth/credentials", "/internal/auth/jwks", "/internal/x"})
    void internalPaths_resolveTheToken(String uri) {
        assertThat(RESOLVER.resolve(withBearer(uri))).isEqualTo("abc.def.ghi");
    }

    @ParameterizedTest(name = "{0} → 무시(null)")
    @ValueSource(strings = {
            "/api/auth/password",
            "/api/auth/password-reset/confirm",
            "/api/auth/logout",
            "/api/accounts/me/sessions",
            "/internalx/foo"
    })
    void userPaths_ignoreTheForwardedToken(String uri) {
        assertThat(RESOLVER.resolve(withBearer(uri))).isNull();
    }
}
