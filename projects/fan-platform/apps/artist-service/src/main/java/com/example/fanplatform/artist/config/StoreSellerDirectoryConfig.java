package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.adapter.out.store.HttpStoreSellerDirectory;
import com.example.fanplatform.artist.adapter.out.store.StoreTenantTokenProvider;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the one and only {@link StoreSellerDirectory} — TASK-MONO-759.
 *
 * <h2>AC-4: there is no permissive bean to fall into</h2>
 *
 * {@code UnwiredStoreSellerDirectory} is deleted, and nothing replaced it as a fallback. This
 * class declares exactly one {@code StoreSellerDirectory} {@code @Bean} method and it builds
 * the HTTP adapter, whose every failure is «cannot verify» (503, nothing saved). So:
 * <ul>
 *   <li>misconfigured (blank URL / client) → the bean refuses to build and <b>startup fails</b>
 *       — loud, and nothing is ever saved by a half-wired service;</li>
 *   <li>configured but the store or IdP is down → every link is 503, nothing saved;</li>
 *   <li>this class removed or excluded → zero {@code StoreSellerDirectory} beans →
 *       {@code AgencyService} cannot be constructed → startup fails.</li>
 * </ul>
 * None of the three ends in an always-«ACTIVE» directory. {@code StoreSellerDirectoryConfigTest}
 * asserts all three plus the structural cardinality (one {@code @Bean} method), the shape
 * community-service's {@code ArtistAccountCheckerConfigTest} uses for the same property.
 *
 * <p>{@code @ConditionalOnMissingBean} is kept as the test seam (integration tests substitute a
 * deterministic directory with {@code @MockitoBean}), not as a production alternative.
 *
 * <h2>Configuration</h2>
 *
 * The token half reuses this service's first IdP client ({@code artist-service-client}, IdP
 * {@code V0042}): {@code iam.internal-client.*}, the same keys community-service uses. The store
 * half is {@code artist.store-seller.*}. {@code base-url} is the <b>ecommerce gateway</b>
 * (reach path R1), not product-service — the two projects share no network.
 */
@Configuration
public class StoreSellerDirectoryConfig {

    @Bean
    @ConditionalOnMissingBean(StoreSellerDirectory.class)
    public StoreSellerDirectory httpStoreSellerDirectory(
            @Value("${iam.internal-client.token-uri:http://iam.local/oauth2/token}") String tokenUri,
            @Value("${iam.internal-client.client-id:artist-service-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret,
            @Value("${artist.store-seller.scope:store.seller.read}") String scope,
            @Value("${artist.store-seller.tenant-id:ecommerce}") String storeTenant,
            @Value("${artist.store-seller.base-url:http://ecommerce.local}") String baseUrl,
            @Value("${artist.store-seller.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${artist.store-seller.read-timeout-ms:3000}") int readTimeoutMs) {
        requireNonBlank("iam.internal-client.token-uri", tokenUri);
        requireNonBlank("iam.internal-client.client-id", clientId);
        requireNonBlank("iam.internal-client.client-secret", clientSecret);
        requireNonBlank("artist.store-seller.scope", scope);
        requireNonBlank("artist.store-seller.tenant-id", storeTenant);
        requireNonBlank("artist.store-seller.base-url", baseUrl);

        Duration connect = Duration.ofMillis(connectTimeoutMs);
        Duration read = Duration.ofMillis(readTimeoutMs);

        IamClientCredentialsTokenProvider ccToken = new IamClientCredentialsTokenProvider(
                tokenUri, clientId, clientSecret, scope, connect, read);
        StoreTenantTokenProvider storeToken = new StoreTenantTokenProvider(
                ccToken, restClient(null, connect, read), tokenUri, clientId, clientSecret,
                scope, storeTenant);
        return new HttpStoreSellerDirectory(restClient(baseUrl, connect, read), storeToken);
    }

    private static RestClient restClient(String baseUrl, Duration connect, Duration read) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        RestClient.Builder b = RestClient.builder().requestFactory(factory);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }

    private static void requireNonBlank(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set — the store seller lookup has no "
                    + "permissive fallback, so a half-configured adapter fails startup instead "
                    + "(TASK-MONO-759 AC-4)");
        }
    }
}
