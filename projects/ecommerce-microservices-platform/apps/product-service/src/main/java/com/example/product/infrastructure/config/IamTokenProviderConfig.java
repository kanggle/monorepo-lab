package com.example.product.infrastructure.config;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the shared {@link IamClientCredentialsTokenProvider} (ADR-MONO-058 § D6,
 * {@code libs/java-security}, promoted by {@code TASK-MONO-501}) as product-service's
 * outbound IAM {@code client_credentials} token source for
 * {@link com.example.product.infrastructure.client.AccountServiceSellerProvisioner}
 * (ADR-MONO-042 D2/D4/D5).
 *
 * <p>Replaces this service's own local copy of the class (TASK-BE-568), which carried both
 * defects the shared, already-fixed class closes:
 * <ul>
 *   <li>the Basic-auth credentials were encoded with the JVM platform-default charset
 *       ({@code String.getBytes()}) instead of UTF-8, per RFC 7617;</li>
 *   <li>the token-acquisition {@code RestClient} was built via {@code RestClient.create()},
 *       with no connect/read timeout at all — a hung IAM token endpoint would block
 *       {@code AccountServiceSellerProvisioner}'s {@code synchronized currentBearer()}
 *       indefinitely.</li>
 * </ul>
 *
 * <p>product-service's IAM token endpoint has no registered OAuth2 {@code scope} — its token
 * request body has always been {@code grant_type=client_credentials} only (no {@code scope}
 * parameter). The shared class's {@code scope} constructor argument is passed as {@code null}
 * here, which it treats as "omit the {@code scope} parameter entirely", preserving this
 * service's pre-existing request shape byte-for-byte.
 *
 * <p>Timeout config keys ({@code iam.internal-client.connect-timeout-ms} /
 * {@code read-timeout-ms}) are new — the local copy this class replaces had none. Defaults to
 * 5s/5s, matching {@code ecommerce/batch-worker}'s already-fixed copy of this same class (the
 * concrete before/after reference named by TASK-BE-568). These are a *different* config
 * namespace from {@code iam.downstream.connect-timeout-ms}/{@code read-timeout-ms}, which
 * configure {@code AccountServiceSellerProvisioner}'s own outbound call to account-service,
 * not this token-acquisition call.
 */
@Configuration
class IamTokenProviderConfig {

    @Bean
    IamClientCredentialsTokenProvider iamClientCredentialsTokenProvider(
            @Value("${iam.internal-client.token-uri:http://localhost:8081/oauth2/token}") String tokenUri,
            @Value("${iam.internal-client.client-id:product-service-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret,
            @Value("${iam.internal-client.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${iam.internal-client.read-timeout-ms:5000}") long readTimeoutMs) {
        return new IamClientCredentialsTokenProvider(
                tokenUri,
                clientId,
                clientSecret,
                null, // no registered scope for product-service's IAM token endpoint
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }
}
