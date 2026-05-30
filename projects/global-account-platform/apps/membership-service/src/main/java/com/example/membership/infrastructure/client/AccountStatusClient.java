package com.example.membership.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.membership.application.exception.AccountStatusUnavailableException;
import com.example.membership.domain.account.AccountStatus;
import com.example.membership.domain.account.AccountStatusChecker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * Calls account-service /internal/accounts/{id}/status. Wrapped in a circuit breaker;
 * any failure (timeout / 5xx / open circuit) is mapped to
 * {@link AccountStatusUnavailableException} (fail-closed).
 */
@Slf4j
@Component
public class AccountStatusClient implements AccountStatusChecker {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final GapClientCredentialsTokenProvider tokenProvider;

    public AccountStatusClient(
            @Value("${membership.account-service.base-url}") String baseUrl,
            @Value("${membership.account-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${membership.account-service.read-timeout-ms:3000}") int readTimeoutMs,
            GapClientCredentialsTokenProvider tokenProvider) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountStatus");
        this.tokenProvider = tokenProvider;
    }

    @Override
    public AccountStatus check(String accountId) {
        Supplier<AccountStatus> supplier = () -> doCheck(accountId);
        Supplier<AccountStatus> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        try {
            return resilient.get();
        } catch (CallNotPermittedException e) {
            log.warn("Account status circuit OPEN for accountId={}", accountId);
            throw new AccountStatusUnavailableException("account-service circuit open", e);
        } catch (AccountStatusUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Account status lookup failed for accountId={}: {}", accountId, e.getMessage());
            throw new AccountStatusUnavailableException("account-service unavailable", e);
        }
    }

    private AccountStatus doCheck(String accountId) {
        try {
            AccountStatusResponse body = restClient.get()
                    .uri("/internal/accounts/{id}/status", accountId)
                    .headers(this::applyAuth)
                    .retrieve()
                    .body(AccountStatusResponse.class);
            if (body == null || body.status() == null) {
                throw new AccountStatusUnavailableException("empty status response");
            }
            return AccountStatus.valueOf(body.status());
        } catch (AccountStatusUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("account-service call failure", e);
        }
    }

    // TASK-BE-318d: authenticate via GAP client_credentials Bearer JWT
    // (account /internal/** dual-allows JWT or X-Internal-Token, BE-317).
    private void applyAuth(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.currentBearer());
    }

    public record AccountStatusResponse(String accountId, String status) {
    }
}
