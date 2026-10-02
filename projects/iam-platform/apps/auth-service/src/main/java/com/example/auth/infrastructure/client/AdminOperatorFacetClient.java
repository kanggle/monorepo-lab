package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.OperatorFacetUnavailableException;
import com.example.auth.application.port.OperatorFacetPort;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

/**
 * TASK-BE-618 (auth-to-admin.md § GET /internal/operators/facet) — the operator-facet read, made before a
 * single-site consumer account's credential is moved into the consumer pool.
 *
 * <p>Same base URL ({@code auth.admin-service.base-url}), timeouts and GAP {@code client_credentials}
 * Bearer as {@link AdminAssignmentClient}, but its <b>own</b> circuit breaker / retry ({@code adminOperatorFacet}):
 * a failing maintenance batch must not open the breaker that gates assume-tenant issuance.
 *
 * <p><b>fail-CLOSED</b>: anything but a parseable {@code {"operatorFaceted": bool}} throws
 * {@link OperatorFacetUnavailableException}.
 */
@Slf4j
@Component
public class AdminOperatorFacetClient implements OperatorFacetPort {

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final IamClientCredentialsTokenProvider tokenProvider;

    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    public AdminOperatorFacetClient(
            Environment environment,
            @Value("${auth.admin-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.admin-service.read-timeout-ms:5000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("adminOperatorFacet");
        this.retry = ResilienceClientFactory.buildRetry("adminOperatorFacet");
        this.tokenProvider = tokenProvider;
    }

    /** Resolved per call (like {@link AdminAssignmentClient}) so a test-time property is honoured. */
    private RestClient restClient() {
        String baseUrl = environment.getProperty(AdminAssignmentClient.BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Required property '" + AdminAssignmentClient.BASE_URL_PROPERTY + "' is not set");
        }
        RestClient existing = this.cachedRestClient;
        if (existing != null && baseUrl.equals(this.cachedBaseUrl)) {
            return existing;
        }
        synchronized (this) {
            if (this.cachedRestClient != null && baseUrl.equals(this.cachedBaseUrl)) {
                return this.cachedRestClient;
            }
            RestClient fresh = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
            this.cachedBaseUrl = baseUrl;
            this.cachedRestClient = fresh;
            return fresh;
        }
    }

    @Override
    public boolean isOperatorFaceted(String accountId, String identityId) {
        Supplier<Boolean> supplier = () -> doFacet(accountId, identityId);
        Supplier<Boolean> retrying = Retry.decorateSupplier(retry, supplier);
        Supplier<Boolean> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retrying);
        try {
            return resilient.get();
        } catch (RuntimeException e) {
            log.warn("operator-facet check failed (fail-closed — nothing is moved): type={} cause={}",
                    e.getClass().getName(), e.getCause() == null ? "null" : e.getCause().getClass().getName());
            throw new OperatorFacetUnavailableException("admin-service operator-facet check failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Boolean doFacet(String accountId, String identityId) {
        Map<String, Object> body = restClient().get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/operators/facet").queryParam("accountId", accountId);
                    if (identityId != null && !identityId.isBlank()) {
                        uriBuilder.queryParam("identityId", identityId);
                    }
                    return uriBuilder.build();
                })
                .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                .retrieve()
                .body(Map.class);
        Object value = body == null ? null : body.get("operatorFaceted");
        if (!(value instanceof Boolean faceted)) {
            // A missing / non-boolean field is not an answer — fail closed.
            throw new IllegalStateException("admin operator-facet returned no boolean operatorFaceted");
        }
        return faceted;
    }
}
