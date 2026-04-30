package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;
import com.example.common.resilience.ResilienceClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Internal HTTP client for account-service.
 *
 * <p>TASK-BE-063: the login hot path no longer calls account-service for credential
 * lookup — auth-service owns credentials locally. This client is now a
 * status-and-social-signup adapter only. Configured with timeouts (connect=3s,
 * read=5s), retry (2 retries, exponential backoff + jitter, no retry on 4xx),
 * and circuit breaker (50% failure rate / 10s sliding window).</p>
 */
@Slf4j
@Component
public class AccountServiceClient implements AccountServicePort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AccountServiceClient(
            @Value("${auth.account-service.base-url}") String baseUrl,
            @Value("${auth.account-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.account-service.read-timeout-ms:5000}") int readTimeoutMs) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountService");
        this.retry = ResilienceClientFactory.buildRetry("accountService");
    }

    @Override
    public Optional<AccountStatusLookupResult> getAccountStatus(String accountId) {
        Supplier<Optional<AccountStatusLookupResult>> supplier = () -> doGetStatus(accountId);

        Supplier<Optional<AccountStatusLookupResult>> retryingSupplier =
                Retry.decorateSupplier(retry, supplier);
        Supplier<Optional<AccountStatusLookupResult>> resilientSupplier =
                CircuitBreaker.decorateSupplier(circuitBreaker, retryingSupplier);

        try {
            return resilientSupplier.get();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.warn("Account service status lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("Account service status lookup failed after retries: {}", e.getMessage());
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    private Optional<AccountStatusLookupResult> doGetStatus(String accountId) {
        try {
            // account-service returns { accountId, status, statusChangedAt } — map the
            // "status" field onto our port's accountStatus slot.
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/internal/accounts/{id}/status", accountId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw HttpClientErrorException.create(
                                    response.getStatusCode(), "Not Found",
                                    response.getHeaders(), new byte[0], null);
                        }
                        throw HttpClientErrorException.create(
                                response.getStatusCode(), "Client Error",
                                response.getHeaders(), new byte[0], null);
                    })
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }
            String returnedId = (String) body.getOrDefault("accountId", accountId);
            String status = (String) body.get("status");
            if (status == null) {
                return Optional.empty();
            }
            return Optional.of(new AccountStatusLookupResult(returnedId, status));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @Override
    public SocialSignupResult socialSignup(String email, String provider,
                                            String providerUserId, String displayName) {
        Supplier<SocialSignupResult> supplier = () -> doSocialSignup(email, provider, providerUserId, displayName);

        Supplier<SocialSignupResult> retryingSupplier =
                Retry.decorateSupplier(retry, supplier);
        Supplier<SocialSignupResult> resilientSupplier =
                CircuitBreaker.decorateSupplier(circuitBreaker, retryingSupplier);

        try {
            return resilientSupplier.get();
        } catch (HttpClientErrorException e) {
            log.warn("Account service social-signup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AccountServiceUnavailableException("Account service social-signup failed", e);
        } catch (RuntimeException e) {
            log.error("Account service social-signup failed after retries: {}", e.getMessage());
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    private SocialSignupResult doSocialSignup(String email, String provider,
                                               String providerUserId, String displayName) {
        try {
            Map<String, String> requestBody = Map.of(
                    "email", email,
                    "provider", provider,
                    "providerUserId", providerUserId,
                    "displayName", displayName != null ? displayName : ""
            );

            return restClient.post()
                    .uri("/internal/accounts/social-signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(SocialSignupResult.class);
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }
}
