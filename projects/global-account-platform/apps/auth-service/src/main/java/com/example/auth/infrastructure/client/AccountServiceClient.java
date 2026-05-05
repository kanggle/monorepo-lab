package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;
import com.example.common.resilience.ResilienceClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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

    /** Property key for the account-service base URL. */
    static final String BASE_URL_PROPERTY = "auth.account-service.base-url";

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * Cached {@link RestClient} keyed by base URL string. The base URL is resolved
     * from {@link Environment} on every call rather than captured at construction
     * time. This avoids stale URLs when integration tests share a Spring context
     * via {@code ContextCache} but each test class registers a different
     * {@link org.springframework.test.context.DynamicPropertySource} value
     * (TASK-MONO-046-1 Cluster C / TASK-MONO-044c-1 RC#2 residue).
     *
     * <p>Production runtimes use a single static base URL, so the cache only ever
     * holds one entry; the lookup-and-build cost is negligible.
     */
    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    public AccountServiceClient(
            Environment environment,
            @Value("${auth.account-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.account-service.read-timeout-ms:5000}") int readTimeoutMs) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountService");
        this.retry = ResilienceClientFactory.buildRetry("accountService");
    }

    /**
     * Test-only constructor that pins a single {@code baseUrl} without going through
     * {@link Environment}. Used by {@code AccountServiceClientUnitTest} which builds
     * the client outside of a Spring context.
     *
     * <p>Production code uses the {@link Environment}-based constructor and therefore
     * benefits from the lazy URL resolution that Cluster C of TASK-MONO-046-1 added.
     */
    public AccountServiceClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.environment = null;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountService");
        this.retry = ResilienceClientFactory.buildRetry("accountService");
        this.cachedBaseUrl = baseUrl;
        this.cachedRestClient = ResilienceClientFactory.buildRestClient(
                baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Resolves the {@link RestClient} bound to the current value of
     * {@code auth.account-service.base-url}. If the property changes between
     * calls (e.g. integration test contexts), a fresh {@link RestClient} is
     * built; otherwise the cached instance is reused.
     */
    private RestClient restClient() {
        // Test-only constructor pinned a RestClient without an Environment — use it.
        if (environment == null) {
            RestClient pinned = this.cachedRestClient;
            if (pinned == null) {
                throw new IllegalStateException(
                        "Test-only AccountServiceClient created without baseUrl");
            }
            return pinned;
        }
        String baseUrl = environment.getProperty(BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Required property '" + BASE_URL_PROPERTY + "' is not set");
        }
        RestClient existing = this.cachedRestClient;
        if (existing != null && baseUrl.equals(this.cachedBaseUrl)) {
            return existing;
        }
        synchronized (this) {
            if (this.cachedRestClient != null && baseUrl.equals(this.cachedBaseUrl)) {
                return this.cachedRestClient;
            }
            RestClient fresh = ResilienceClientFactory.buildRestClient(
                    baseUrl, connectTimeoutMs, readTimeoutMs);
            this.cachedBaseUrl = baseUrl;
            this.cachedRestClient = fresh;
            return fresh;
        }
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
            Map<String, Object> body = restClient().get()
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

            return restClient().post()
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

    @Override
    public Optional<AccountProfileResult> getAccountProfile(String accountId) {
        Supplier<Optional<AccountProfileResult>> supplier = () -> doGetProfile(accountId);

        Supplier<Optional<AccountProfileResult>> retryingSupplier =
                Retry.decorateSupplier(retry, supplier);
        Supplier<Optional<AccountProfileResult>> resilientSupplier =
                CircuitBreaker.decorateSupplier(circuitBreaker, retryingSupplier);

        try {
            return resilientSupplier.get();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.warn("Account service profile lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("Account service profile lookup failed after retries: {}", e.getMessage());
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<AccountProfileResult> doGetProfile(String accountId) {
        try {
            // account-service returns:
            //   { accountId, email, emailVerified, displayName, preferredUsername, locale,
            //     tenantId, tenantType }
            Map<String, Object> body = restClient().get()
                    .uri("/internal/accounts/{id}/profile", accountId)
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

            return Optional.of(new AccountProfileResult(
                    (String) body.getOrDefault("accountId", accountId),
                    (String) body.get("email"),
                    body.get("emailVerified") instanceof Boolean b ? b :
                            Boolean.parseBoolean(String.valueOf(body.get("emailVerified"))),
                    (String) body.get("displayName"),
                    (String) body.get("preferredUsername"),
                    (String) body.get("locale"),
                    (String) body.get("tenantId"),
                    (String) body.get("tenantType")
            ));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }
}
