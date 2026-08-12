package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the {@link MembershipChecker} bean.
 *
 * <p><strong>There is exactly one implementation, and it cannot be switched off.</strong>
 * {@link HttpMembershipChecker} is the only {@code MembershipChecker} {@code @Bean}
 * declared here, unconditionally.
 *
 * <p><strong>Why the escape hatch is gone (TASK-FAN-INT-006).</strong> Until this
 * ticket the class declared a second bean — an inert {@code AlwaysAllowMembershipChecker}
 * behind {@code @ConditionalOnMissingBean}, reachable by setting
 * {@code community.membership-service.enabled=false}. It existed because the v1
 * live-trio e2e (gateway+community+artist) had no membership-service, so the real
 * gate had nothing to call.
 *
 * <p>That shape was worse than the artist-side hatch TASK-FAN-INT-005 deleted, and
 * {@code ADR-004} § Decision Drivers 3 named why: the artist hatch selected the
 * permissive bean only on an <em>explicit</em> {@code havingValue="false"}, while
 * this one selected it as a {@code @ConditionalOnMissingBean} <em>fallback</em>.
 * A fallback is chosen by ABSENCE, so any failure to register the real bean — for
 * any reason, including ones nobody predicted — lands on "allow everything" and the
 * service still starts green. That is the wrong side for an accident to land on.
 *
 * <p>TASK-FAN-INT-006 put membership-service into the live stack and made the e2e
 * subscribe through the product path, so the hatch has no remaining caller. It is
 * deleted rather than left unused: an unused permissive fallback is a defect waiting
 * for a bean-registration failure, and it costs nothing until the day it costs
 * everything. {@code MembershipCheckerAutoConfigTest} now asserts <em>structurally</em>
 * that this class declares exactly one {@code MembershipChecker} {@code @Bean}
 * method — a property-keyed test cannot catch a hatch that returns behind a
 * different key, which TASK-FAN-INT-005 measured on the artist side (all 7
 * property cases stayed green against an injected hatch; only the structural one
 * went red).
 *
 * <p>Tests may still override the bean with a {@code @Primary @TestConfiguration}
 * {@code MembershipChecker} (e.g. {@code MembershipGateIntegrationTest}'s deny-all).
 * That is a test-scope override, not a production-reachable switch.
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
     * <p>Unconditional since TASK-FAN-INT-006 — there is no deployment shape left
     * that needs a reachable-membership-service opt-out (see the class javadoc).
     */
    @Bean
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
}
