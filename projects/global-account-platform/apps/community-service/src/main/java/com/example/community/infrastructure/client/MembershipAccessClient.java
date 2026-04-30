package com.example.community.infrastructure.client;

import com.example.community.domain.access.ContentAccessChecker;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Synchronous access check to membership-service.
 * Fail-closed: on any error (network, 5xx, CB OPEN), returns {@code false}.
 */
@Slf4j
@Component
public class MembershipAccessClient implements ContentAccessChecker {

    private final RestClient restClient;

    public MembershipAccessClient(
            @Value("${community.membership-service.base-url}") String baseUrl,
            @Value("${community.membership-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${community.membership-service.read-timeout-ms:3000}") int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    @CircuitBreaker(name = "membershipService", fallbackMethod = "denyFallback")
    public boolean check(String accountId, String requiredPlanLevel) {
        try {
            AccessResponse resp = restClient.get()
                    .uri(uri -> uri.path("/internal/membership/access")
                            .queryParam("accountId", accountId)
                            .queryParam("requiredPlanLevel", requiredPlanLevel)
                            .build())
                    .retrieve()
                    .body(AccessResponse.class);
            return resp != null && Boolean.TRUE.equals(resp.allowed());
        } catch (Exception e) {
            log.warn("membership-service access check failed: {}", e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private boolean denyFallback(String accountId, String requiredPlanLevel, Throwable t) {
        log.warn("membership-service fail-closed fallback engaged (accountId={}, plan={}): {}",
                accountId, requiredPlanLevel, t.toString());
        return false;
    }

    public record AccessResponse(String accountId, String requiredPlanLevel,
                                 Boolean allowed, String activePlanLevel) {}
}
