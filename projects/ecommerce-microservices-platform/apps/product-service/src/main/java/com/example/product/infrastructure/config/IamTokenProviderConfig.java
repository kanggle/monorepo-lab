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
 * <p><b>TASK-MONO-717 (owner decision ⓐ) — the scope is now requested explicitly.</b> This
 * paragraph used to say the token endpoint had "no registered OAuth2 {@code scope}", and that
 * {@code null} preserved the request shape "byte-for-byte". That shape had never worked:
 * {@code product-service-client} was not registered in the IdP at all (measured — the
 * provisioning call has never succeeded in this repository), so there was no shape worth
 * preserving. V0036 registers it with {@code ["internal.invoke"]}, and account-service PINS
 * that scope on {@code /internal/**} via {@code internalTokenValidator()} (TASK-BE-514) — a
 * token without it is refused even though its signature and issuer are valid.
 *
 * <p>🔴 It is requested rather than left to the server's default. Spring Authorization Server
 * grants every registered scope when a {@code client_credentials} request omits {@code scope},
 * so omitting it would <em>probably</em> also work — but then this service's token contents
 * would depend on a server-side default that nothing here states, and the next scope added to
 * the registration would silently widen this token. account-service's sibling config requests
 * {@code internal.invoke} by name for the same reason.
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

    /**
     * The workload scope {@code /internal/**} demands (TASK-BE-514, seeded to this client by
     * {@code V0036}). 🔴 Not decoration — account-service refuses a GAP JWT that lacks it.
     */
    private static final String INTERNAL_INVOKE_SCOPE = "internal.invoke";

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
                INTERNAL_INVOKE_SCOPE,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }
}
