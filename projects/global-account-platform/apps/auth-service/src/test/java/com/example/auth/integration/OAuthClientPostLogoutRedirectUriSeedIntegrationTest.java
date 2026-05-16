package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
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
 * TASK-BE-297 regression — proves the corrective Flyway migration
 * {@code V0016__fix_post_logout_redirect_uris_default_typing.sql} repairs the
 * pre-existing latent <b>production</b> defect surfaced during the TASK-BE-296
 * review (PR #568 CI).
 *
 * <h3>Defect recap</h3>
 *
 * <p>{@code OAuthClientMapper.buildSasMapper()} enables
 * {@code SecurityJackson2Modules.enableDefaultTyping}. V0011
 * ({@code fan-platform-user-flow-client}) and V0012
 * ({@code ecommerce-web-store-client} / {@code ecommerce-admin-dashboard-client})
 * hand-wrote the custom {@code settings.client.post-logout-redirect-uris}
 * setting as a <b>plain JSON array</b> with no SAS {@code [typeId, value]}
 * wrapper-array envelope. Under default typing, element 0 of that array
 * ({@code "http://localhost:3000/"}) is read as a Java type id, fails the SAS
 * allow-list, and {@code OAuthClientMapper.toRegisteredClient} throws
 * {@code OAuthClientMappingException}.
 *
 * <p>{@code JpaRegisteredClientRepository} is the production
 * {@link RegisteredClientRepository} bean wired into the {@code @Order(1)} SAS
 * filter chain, so EVERY real {@code authorization_code} / {@code refresh_token}
 * / {@code /oauth2/authorize} request from these three clients hit the throwing
 * path. It stayed latent only because no integration test ever loaded these
 * rows through the full {@code RegisteredClient} mapping path (only the clean
 * V0008 {@code demo-spa-client} / {@code test-internal-client} were exercised).
 *
 * <h3>What this test asserts</h3>
 *
 * <ol>
 *   <li>Each affected client ({@code fan-platform-user-flow-client},
 *       {@code ecommerce-web-store-client},
 *       {@code ecommerce-admin-dashboard-client}) now resolves via
 *       {@code findByClientId} WITHOUT {@code OAuthClientMappingException} —
 *       the exact assertion the BE-296 narrowed regression deliberately
 *       avoided — and the {@code post-logout-redirect-uris} custom
 *       ClientSettings deserializes to the byte-equivalent {@code List<String>}
 *       of the originally-seeded URIs (effective-settings equivalence proof).</li>
 *   <li>Standard SAS ClientSettings (PKCE) and core fields (grants, redirect
 *       URIs, scopes, tenant carrier) on the repaired rows are intact.</li>
 *   <li>Regression: the clean clients V0016 never touches —
 *       {@code demo-spa-client} (V0008), {@code wms-user-flow-client} /
 *       {@code wms-internal-services-client} (V0010),
 *       {@code scm-platform-internal-services-client} (V0013),
 *       {@code platform-console-web} (V0015) — still resolve through the full
 *       mapping path unchanged.</li>
 * </ol>
 *
 * <p>Skipped automatically when Docker is unavailable
 * ({@code AbstractIntegrationTest} DockerAvailableCondition); CI Linux
 * {@code :auth-service:integrationTest} is authoritative.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OAuthClientPostLogoutRedirectUriSeedIntegrationTest extends AbstractIntegrationTest {

    private static final String PLR_KEY = "settings.client.post-logout-redirect-uris";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // No real account-service is contacted by findByClientId mapping path.
        registry.add("auth.account-service.base-url", () -> "http://localhost:19998");
    }

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    // -----------------------------------------------------------------------
    // 1. Affected clients now resolve + post-logout-redirect-uris correct
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("V0016: fan-platform-user-flow-client resolves; post-logout-redirect-uris == seeded URIs")
    void fanPlatformClient_resolvesWithPostLogoutRedirectUris() {
        RegisteredClient client =
                registeredClientRepository.findByClientId("fan-platform-user-flow-client");

        assertThat(client)
                .as("fan-platform-user-flow-client must now deserialize (V0016 corrective migration)")
                .isNotNull();
        assertThat(client.getClientId()).isEqualTo("fan-platform-user-flow-client");

        List<String> postLogout = client.getClientSettings().getSetting(PLR_KEY);
        assertThat(postLogout)
                .as("post-logout-redirect-uris must round-trip to the exact seeded List<String>")
                .containsExactly("http://localhost:3000/", "http://fan-platform.local/");

        // Standard SAS settings + core fields intact on the repaired row.
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactlyInAnyOrder(
                        AuthorizationGrantType.AUTHORIZATION_CODE,
                        AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getRedirectUris()).contains(
                "http://localhost:3000/api/auth/callback/gap",
                "http://fan-platform.local/api/auth/callback/gap");
        assertThat(client.getScopes()).contains("openid", "profile", "email", "tenant.read");
        assertThat(client.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("V0016: ecommerce-web-store-client resolves; post-logout-redirect-uris == seeded URIs")
    void ecommerceWebStoreClient_resolvesWithPostLogoutRedirectUris() {
        RegisteredClient client =
                registeredClientRepository.findByClientId("ecommerce-web-store-client");

        assertThat(client).as("ecommerce-web-store-client must now deserialize").isNotNull();

        List<String> postLogout = client.getClientSettings().getSetting(PLR_KEY);
        assertThat(postLogout)
                .containsExactly("http://localhost:3000/", "http://web.ecommerce.local/");

        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getScopes())
                .contains("openid", "profile", "email", "tenant.read", "ecommerce.consumer");
        assertThat(client.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("V0016: ecommerce-admin-dashboard-client resolves; post-logout-redirect-uris == seeded URIs")
    void ecommerceAdminDashboardClient_resolvesWithPostLogoutRedirectUris() {
        RegisteredClient client =
                registeredClientRepository.findByClientId("ecommerce-admin-dashboard-client");

        assertThat(client).as("ecommerce-admin-dashboard-client must now deserialize").isNotNull();

        List<String> postLogout = client.getClientSettings().getSetting(PLR_KEY);
        assertThat(postLogout)
                .containsExactly("http://localhost:3001/", "http://admin.ecommerce.local/");

        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getScopes())
                .contains("openid", "profile", "email", "tenant.read", "ecommerce.operator");
        assertThat(client.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("ecommerce");
    }

    // -----------------------------------------------------------------------
    // 2. Regression — clients V0016 never touches still resolve unchanged
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("regression: clean clients (demo-spa/wms/scm/platform-console-web) still resolve unchanged")
    void cleanClients_stillResolveUnchanged() {
        // V0008 public client — no array-valued custom setting.
        RegisteredClient demoSpa =
                registeredClientRepository.findByClientId("demo-spa-client");
        assertThat(demoSpa).as("demo-spa-client must still resolve").isNotNull();
        assertThat(demoSpa.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(demoSpa.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("fan-platform");
        assertThat(demoSpa.getClientSettings().<Object>getSetting(PLR_KEY))
                .as("clean V0008 client carries no post-logout-redirect-uris")
                .isNull();

        // V0010 wms clients.
        RegisteredClient wmsUser =
                registeredClientRepository.findByClientId("wms-user-flow-client");
        assertThat(wmsUser).as("wms-user-flow-client must still resolve").isNotNull();
        assertThat(wmsUser.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("wms");

        RegisteredClient wmsInternal =
                registeredClientRepository.findByClientId("wms-internal-services-client");
        assertThat(wmsInternal).as("wms-internal-services-client must still resolve").isNotNull();
        assertThat(wmsInternal.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.CLIENT_CREDENTIALS);

        // V0013 scm client.
        RegisteredClient scm =
                registeredClientRepository.findByClientId("scm-platform-internal-services-client");
        assertThat(scm).as("scm-platform-internal-services-client must still resolve").isNotNull();
        assertThat(scm.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("scm");

        // V0015 platform-console-web public client (BE-296).
        RegisteredClient console =
                registeredClientRepository.findByClientId("platform-console-web");
        assertThat(console).as("platform-console-web must still resolve").isNotNull();
        assertThat(console.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(console.getClientSettings().<String>getSetting("custom.tenant_id"))
                .isEqualTo("gap");
        assertThat(console.getClientSettings().<Object>getSetting(PLR_KEY))
                .as("clean V0015 client carries no post-logout-redirect-uris")
                .isNull();
    }
}
