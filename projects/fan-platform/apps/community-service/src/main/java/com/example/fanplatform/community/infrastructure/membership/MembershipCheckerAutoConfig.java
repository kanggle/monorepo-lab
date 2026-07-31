package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the {@link MembershipChecker} bean.
 *
 * <p><strong>Bean ordering is deterministic by design.</strong> The real
 * {@link HttpMembershipChecker} {@code @Bean} is declared FIRST; the v1
 * {@link AlwaysAllowMembershipChecker} {@code @Bean} is declared SECOND with
 * {@code @ConditionalOnMissingBean}. Within a single {@code @Configuration} class
 * Spring processes {@code @Bean} methods top-to-bottom, so the stub's condition
 * always evaluates AFTER the real bean is registered → the stub backs off. This
 * avoids the {@code @ConditionalOnMissingBean}-against-component-scan
 * non-determinism (memory §19): the conditional only ever sees beans defined
 * earlier in the same class.
 *
 * <p>Tests override the production bean with a {@code @Primary @TestConfiguration}
 * {@code MembershipChecker} (e.g. {@code MembershipGateIntegrationTest}'s
 * deny-all), so the gate can be exercised without a live membership-service.
 *
 * <p><strong>Escape hatch.</strong> Setting
 * {@code community.membership-service.enabled=false} (env
 * {@code COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false}) excludes the HTTP bean, so
 * the {@link AlwaysAllowMembershipChecker} fallback is selected instead. This is
 * how the v1 live-trio e2e (gateway+community+artist, TASK-FAN-INT-001) runs
 * without membership-service / iam in the stack — the real gate is covered
 * deterministically by {@code MembershipGateIntegrationTest} (MockWebServer) and
 * end-to-end by federation-hardening-e2e. Default ({@code matchIfMissing=true})
 * keeps production on {@link HttpMembershipChecker}.
 */
@Configuration
public class MembershipCheckerAutoConfig {

    /**
     * The shared {@link IamClientCredentialsTokenProvider} (ADR-MONO-058 § D6,
     * {@code libs/java-security}) is a plain, framework-neutral POJO — it carries
     * no Spring stereotype annotation and is never itself component-scanned.
     * community-service constructs it directly here, threading its own
     * property-sourced config values through unchanged (TASK-FAN-BE-041; this
     * replaced a local, now-deleted copy of the same class that carried two
     * defects the shared class fixes: platform-default-charset Basic-auth
     * encoding instead of explicit UTF-8, and no connect/read timeout at all).
     *
     * <p>{@code connect-timeout-ms}/{@code read-timeout-ms} default to the same
     * 2000/3000ms values already used for the downstream membership-service call
     * below, for consistency within this outbound-call chain — there was no prior
     * value to preserve (the deleted local copy had no timeout configured at
     * all), so these are a fresh, sane, non-zero choice, not a preserved default.
     */
    @Bean
    public IamClientCredentialsTokenProvider iamClientCredentialsTokenProvider(
            @Value("${iam.internal-client.token-uri:http://iam.local/oauth2/token}") String tokenUri,
            @Value("${iam.internal-client.client-id:community-service-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret,
            @Value("${iam.internal-client.scope:membership.read}") String scope,
            @Value("${iam.internal-client.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${iam.internal-client.read-timeout-ms:3000}") long readTimeoutMs) {
        return new IamClientCredentialsTokenProvider(
                tokenUri,
                clientId,
                clientSecret,
                scope,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
    }

    /**
     * Production {@link MembershipChecker}: calls membership-service over
     * workload identity. The {@link RestClient} carries a per-request Bearer from
     * {@link IamClientCredentialsTokenProvider} — a token-acquisition failure
     * surfaces as an exception inside the call and is caught fail-closed by
     * {@link HttpMembershipChecker}.
     *
     * <p>Gated by {@code community.membership-service.enabled} (default true) so
     * deployments without a reachable membership-service / iam (the e2e
     * live-trio) can opt out and fall back to {@link AlwaysAllowMembershipChecker}.
     */
    @Bean
    @ConditionalOnProperty(
            name = "community.membership-service.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MembershipChecker httpMembershipChecker(
            IamClientCredentialsTokenProvider tokenProvider,
            @Value("${community.membership-service.base-url:http://membership-service:8080}") String baseUrl,
            @Value("${community.membership-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${community.membership-service.read-timeout-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.currentBearer());
                    return execution.execute(request, body);
                })
                .build();
        return new HttpMembershipChecker(restClient);
    }

    /**
     * v1 fallback — selected ONLY when no other {@link MembershipChecker} is
     * present. Declared after {@link #httpMembershipChecker} so this never wins in
     * production; retained as an explicit escape hatch (e.g. profiles that exclude
     * the HTTP bean) and to keep the documented {@code @ConditionalOnMissingBean}
     * seam.
     */
    @Bean
    @ConditionalOnMissingBean(MembershipChecker.class)
    public MembershipChecker defaultMembershipChecker() {
        return new AlwaysAllowMembershipChecker();
    }
}
