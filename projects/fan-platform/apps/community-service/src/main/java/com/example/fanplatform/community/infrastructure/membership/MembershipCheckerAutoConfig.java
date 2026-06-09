package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 */
@Configuration
public class MembershipCheckerAutoConfig {

    /**
     * Production {@link MembershipChecker}: calls membership-service over
     * workload identity. The {@link RestClient} carries a per-request Bearer from
     * {@link IamClientCredentialsTokenProvider} — a token-acquisition failure
     * surfaces as an exception inside the call and is caught fail-closed by
     * {@link HttpMembershipChecker}.
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
