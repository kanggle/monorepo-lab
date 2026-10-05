package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-623 — the single "provider configured" predicate the login page and the
 * direct {@code GET /login/oauth/{provider}} entry point both read
 * ({@link OAuthProperties.ProviderProperties#isConfigured()}, exposed per-provider via
 * {@link OAuthProperties#isConfigured(OAuthProvider)}).
 *
 * <p>Lives here, not in a template or a controller, per the task's Scope decision
 * ("화면이 설정 문자열 규칙을 알지 않게").
 */
class OAuthPropertiesTest {

    private static OAuthProperties.ProviderProperties provider(String clientId, String clientSecret) {
        OAuthProperties.ProviderProperties props = new OAuthProperties.ProviderProperties();
        props.setClientId(clientId);
        props.setClientSecret(clientSecret);
        return props;
    }

    @Test
    @DisplayName("real client-id AND real client-secret → configured")
    void bothReal_isConfigured() {
        assertThat(provider("real-google-id", "real-google-secret").isConfigured()).isTrue();
    }

    @Test
    @DisplayName("demo-default pair (test-*) → NOT configured")
    void demoDefaultPair_isNotConfigured() {
        assertThat(provider("test-google-client-id", "test-google-client-secret").isConfigured())
                .isFalse();
    }

    @Test
    @DisplayName("AC-2 control — real client-id but demo-default secret → NOT configured "
            + "(half a config cannot finish the token exchange)")
    void realIdTestSecret_isNotConfigured() {
        assertThat(provider("real-google-id", "test-google-client-secret").isConfigured())
                .isFalse();
    }

    @Test
    @DisplayName("demo-default id but real secret → NOT configured (the symmetric half-config)")
    void testIdRealSecret_isNotConfigured() {
        assertThat(provider("test-google-client-id", "real-google-secret").isConfigured())
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("edge case — null/blank/whitespace-only client-id → NOT configured")
    void blankClientId_isNotConfigured(String clientId) {
        assertThat(provider(clientId, "real-google-secret").isConfigured()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("edge case — null/blank/whitespace-only client-secret → NOT configured")
    void blankClientSecret_isNotConfigured(String clientSecret) {
        assertThat(provider("real-google-id", clientSecret).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("OAuthProperties.isConfigured(provider) delegates to the right per-provider block")
    void outerClass_delegatesPerProvider() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGoogle().setClientId("real-google-id");
        properties.getGoogle().setClientSecret("real-google-secret");
        // kakao / microsoft / naver left at their field defaults (blank) — unconfigured.

        assertThat(properties.isConfigured(OAuthProvider.GOOGLE)).isTrue();
        assertThat(properties.isConfigured(OAuthProvider.KAKAO)).isFalse();
        assertThat(properties.isConfigured(OAuthProvider.MICROSOFT)).isFalse();
        assertThat(properties.isConfigured(OAuthProvider.NAVER)).isFalse();
    }

    @Test
    @DisplayName("TASK-BE-623 AC-4 bite — a predicate rewritten to 'always true' is caught by this "
            + "suite's demo-default cell (the same cell AC-1's MVC test relies on)")
    void biteGuard_demoDefaultMustNotReadAsConfigured() {
        // This is the same assertion as demoDefaultPair_isNotConfigured(); kept as a separate,
        // explicitly-named cell so the bite record (see task file) can point at one test by name.
        assertThat(provider("test-google-client-id", "test-google-client-secret").isConfigured())
                .as("if this is ever true, LoginPageController would draw a button for a "
                        + "provider that cannot complete a real login (AC-1 regression)")
                .isFalse();
    }
}
