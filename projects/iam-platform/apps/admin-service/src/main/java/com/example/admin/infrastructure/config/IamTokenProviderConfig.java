package com.example.admin.infrastructure.config;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the canonical {@link IamClientCredentialsTokenProvider} ({@code libs/java-security},
 * ADR-MONO-058 § D6, TASK-BE-568) as a Spring bean for admin-service's outbound
 * {@code client_credentials} calls to account-service ({@code AccountServiceClient},
 * {@code AccountServiceTenantClient}, {@code AccountServiceOrgNodeClient}), auth-service
 * ({@code AuthServiceClient}) and security-service ({@code SecurityServiceClient}).
 *
 * <p>The provider class itself is a plain, framework-neutral POJO carrying no Spring
 * stereotype (per its own javadoc) — each consuming service wires it from its own
 * {@code @Configuration}, supplying property-sourced values. This replaces the previous
 * per-service local copy (TASK-BE-318b), which used {@code RestClient.create()} (no timeout)
 * and platform-default-charset Basic-auth encoding — both defects the canonical class closes.
 *
 * <p>Deliberately a distinct timeout knob from {@code admin.downstream.*} (used by the
 * business-call {@code RestClient}s these clients build themselves) — conflating the
 * token-fetch timeout with the downstream-call timeout would obscure which leg a
 * production timeout tuning change is targeting.
 */
@Configuration
public class IamTokenProviderConfig {

    /** Scope requested for every {@code internal.invoke}-gated {@code /internal/**} call (TASK-BE-514/MONO-422). */
    private static final String INTERNAL_INVOKE_SCOPE = "internal.invoke";

    @Bean
    public IamClientCredentialsTokenProvider iamClientCredentialsTokenProvider(
            @Value("${iam.internal-client.token-uri}") String tokenUri,
            @Value("${iam.internal-client.client-id}") String clientId,
            @Value("${iam.internal-client.client-secret}") String clientSecret,
            @Value("${iam.internal-client.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${iam.internal-client.read-timeout-ms:5000}") long readTimeoutMs) {
        return new IamClientCredentialsTokenProvider(
                tokenUri, clientId, clientSecret, INTERNAL_INVOKE_SCOPE,
                Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
    }
}
