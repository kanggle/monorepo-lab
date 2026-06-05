package com.example.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for TASK-BE-237.
 *
 * <p>Verifies that the three {@link OAuthClient} implementations
 * ({@link GoogleOAuthClient}, {@link KakaoOAuthClient},
 * {@link MicrosoftOAuthClient}) are successfully instantiated and registered
 * in a Spring {@link ApplicationContext}. The original failure mode was
 * Spring 6.x raising {@code "No default constructor found"} when a
 * {@code @Component} declared more than one constructor without an
 * {@code @Autowired} hint — Spring's single-public-constructor auto-detection
 * only triggers when the class declares exactly one constructor.
 * {@link GoogleOAuthClient} and {@link MicrosoftOAuthClient} each declare two
 * (a public 2-arg production constructor and a package-private 3-arg
 * constructor for testability), so an explicit {@code @Autowired} on the
 * production constructor is required.
 *
 * <p><b>TASK-BE-241:</b> the original guard extended {@code AbstractIntegrationTest}
 * which required MySQL + Kafka + Redis containers. When any container failed to
 * start (or Docker was unavailable), the {@code DockerAvailableCondition}
 * silently skipped all three tests, defeating their purpose. This rewrite uses
 * a narrow {@link SpringBootTest} pointing at a minimal {@code @Configuration}
 * that only enables {@link OAuthProperties} and imports the three OAuth client
 * components plus an {@link ObjectMapper}. No {@code DataSource}, {@code Kafka},
 * or {@code Redis} bean is touched, so the test runs without Docker and never
 * silently skips. The test's purpose is purely to verify Spring can construct
 * each OAuth client bean — no actual OAuth or DB calls are made.
 */
@SpringBootTest(
        classes = OAuthClientBeanRegistrationTest.OAuthBeanTestConfig.class,
        webEnvironment = WebEnvironment.NONE,
        properties = {
                "oauth.google.client-id=test-google-client-id",
                "oauth.google.client-secret=test-google-client-secret",
                "oauth.google.token-uri=https://oauth2.googleapis.com/token",
                "oauth.google.jwks-uri=https://www.googleapis.com/oauth2/v3/certs",
                "oauth.google.expected-issuer-pattern=^(https://)?accounts\\.google\\.com$",
                "oauth.kakao.client-id=test-kakao-client-id",
                "oauth.kakao.client-secret=test-kakao-client-secret",
                "oauth.kakao.token-uri=https://kauth.kakao.com/oauth/token",
                "oauth.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
                "oauth.microsoft.client-id=test-microsoft-client-id",
                "oauth.microsoft.client-secret=test-microsoft-client-secret",
                "oauth.microsoft.token-uri=https://login.microsoftonline.com/common/oauth2/v2.0/token",
                "oauth.microsoft.jwks-uri=https://login.microsoftonline.com/common/discovery/v2.0/keys",
                "oauth.microsoft.expected-issuer-pattern=^https://login\\.microsoftonline\\.com/[^/]+/v2\\.0$"
        }
)
class OAuthClientBeanRegistrationTest {

    /**
     * Minimal Spring configuration scoped to this test only. It avoids the
     * full {@code AuthApplication} auto-configuration (DataSource, Kafka,
     * Redis, JPA, Flyway) so no Docker container is required. The
     * configuration enables the {@link OAuthProperties}
     * {@code @ConfigurationProperties} binding and imports the three OAuth
     * client {@code @Component} classes directly, plus an {@link ObjectMapper}
     * dependency.
     */
    @Configuration
    @EnableConfigurationProperties(OAuthProperties.class)
    @Import({GoogleOAuthClient.class, KakaoOAuthClient.class, MicrosoftOAuthClient.class})
    static class OAuthBeanTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private GoogleOAuthClient googleOAuthClient;

    @Autowired
    private KakaoOAuthClient kakaoOAuthClient;

    @Autowired
    private MicrosoftOAuthClient microsoftOAuthClient;

    @Test
    @DisplayName("GoogleOAuthClient bean is registered (TASK-BE-237 regression)")
    void googleOAuthClientBeanIsRegistered() {
        assertThat(applicationContext.getBean(GoogleOAuthClient.class)).isNotNull();
        assertThat(googleOAuthClient).isNotNull();
    }

    @Test
    @DisplayName("KakaoOAuthClient bean is registered")
    void kakaoOAuthClientBeanIsRegistered() {
        assertThat(applicationContext.getBean(KakaoOAuthClient.class)).isNotNull();
        assertThat(kakaoOAuthClient).isNotNull();
    }

    @Test
    @DisplayName("MicrosoftOAuthClient bean is registered (TASK-BE-237 regression)")
    void microsoftOAuthClientBeanIsRegistered() {
        assertThat(applicationContext.getBean(MicrosoftOAuthClient.class)).isNotNull();
        assertThat(microsoftOAuthClient).isNotNull();
    }
}
