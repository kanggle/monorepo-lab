package com.example.community.infrastructure.client;

import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.infrastructure.config.OAuth2WebClientConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Synchronous access check to membership-service.
 *
 * <p>TASK-BE-253: outbound auth switched to standard OAuth2 (see
 * {@link OAuth2WebClientConfig}). Fail-closed semantics preserved — on any error
 * (network, 5xx, CB OPEN), returns {@code false}.
 */
@Slf4j
@Component
public class MembershipAccessClient implements ContentAccessChecker {

    private final WebClient webClient;

    public MembershipAccessClient(
            @Qualifier(OAuth2WebClientConfig.MEMBERSHIP_WEB_CLIENT) WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @CircuitBreaker(name = "membershipService", fallbackMethod = "denyFallback")
    public boolean check(String accountId, String requiredPlanLevel) {
        try {
            AccessResponse resp = webClient.get()
                    .uri(uri -> uri.path("/internal/membership/access")
                            .queryParam("accountId", accountId)
                            .queryParam("requiredPlanLevel", requiredPlanLevel)
                            .build())
                    .retrieve()
                    .bodyToMono(AccessResponse.class)
                    .block();
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
