package com.example.auth.integration;

import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.infrastructure.security.LoginBranding;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-613 (ADR-007) — {@code V0040} gives three clients their login-page branding, and the
 * values survive the whole path: SQL literal → MySQL JSON column → {@code OAuthClientMapper}
 * (SAS default-typed Jackson) → {@link RegisteredClient} → {@link LoginBranding}.
 *
 * <p>The Korean values are compared <b>character for character</b>. That is the only place the
 * migration's two quiet failure modes surface: a single backslash before {@code u} (MySQL drops
 * it, so {@code uB85C} is stored as text), and an UPDATE that matches no row (Flyway still says
 * SUCCESS). Both would otherwise first appear as the wrong words on the demo's login page.
 *
 * <p>Skipped automatically when Docker is unavailable; CI {@code :auth-service:integrationTest}
 * is authoritative.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginPageBrandingSeedIntegrationTest extends AbstractIntegrationTest {

    private static final String PLR_KEY = OAuthClientMapper.SETTING_POST_LOGOUT_REDIRECT_URIS;

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.account-service.base-url", () -> "http://localhost:19998");
    }

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LoginBranding brandingOf(String clientId) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        assertThat(client).as("client %s must resolve", clientId).isNotNull();
        return LoginBranding.from(client.getClientSettings());
    }

    @Test
    @DisplayName("V0040: platform-console-web → IAM, operator subtitle, console logo, #171717")
    void consoleBranding() {
        assertThat(brandingOf("platform-console-web")).isEqualTo(new LoginBranding(
                "IAM", "IAM 로그인", "운영자 계정으로 로그인합니다", "console", "#171717"));
    }

    @Test
    @DisplayName("V0040: fan-platform-user-flow-client → IAM (owner change from GAP), no logo, #9333ea")
    void fanBranding() {
        assertThat(brandingOf("fan-platform-user-flow-client")).isEqualTo(new LoginBranding(
                "IAM", "IAM 로그인", "IAM으로 안전하게 로그인합니다", null, "#9333ea"));
    }

    @Test
    @DisplayName("V0040: ecommerce-web-store-client → Global Account, the store's own subtitle, #1a1a2e")
    void storeBranding() {
        assertThat(brandingOf("ecommerce-web-store-client")).isEqualTo(new LoginBranding(
                "Global Account", "Global Account로 로그인",
                "Global Account로 로그인하여 쇼핑을 계속하세요.", null, "#1a1a2e"));
    }

    @Test
    @DisplayName("V0040 touches exactly three rows — every other client carries no branding key")
    void onlyTheThreeClientsAreBranded() {
        List<String> branded = jdbcTemplate.queryForList(
                "SELECT client_id FROM oauth_clients "
                        + "WHERE JSON_CONTAINS_PATH(client_settings, 'one', "
                        + "'$.\"custom.branding.service-name\"') = 1 ORDER BY client_id",
                String.class);

        assertThat(branded).containsExactly(
                "ecommerce-web-store-client", "fan-platform-user-flow-client", "platform-console-web");
    }

    @Test
    @DisplayName("JSON_MERGE_PATCH left the existing settings alone (type-tagged list, PKCE, tenant)")
    void existingSettingsSurvive() {
        RegisteredClient fan = registeredClientRepository.findByClientId("fan-platform-user-flow-client");
        List<String> fanPostLogout = fan.getClientSettings().getSetting(PLR_KEY);
        assertThat(fanPostLogout).contains("http://localhost:3000/", "https://fan.hubwang.com/");
        assertThat(fan.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(fan.getClientSettings().<String>getSetting(OAuthClientMapper.SETTING_TENANT_ID))
                .isEqualTo("fan-platform");

        RegisteredClient store = registeredClientRepository.findByClientId("ecommerce-web-store-client");
        List<String> storePostLogout = store.getClientSettings().getSetting(PLR_KEY);
        assertThat(storePostLogout).contains("https://store.hubwang.com/");

        RegisteredClient console = registeredClientRepository.findByClientId("platform-console-web");
        List<String> consolePostLogout = console.getClientSettings().getSetting(PLR_KEY);
        assertThat(consolePostLogout).isNotEmpty();
        assertThat(console.getClientSettings().isRequireProofKey()).isTrue();
    }
}
