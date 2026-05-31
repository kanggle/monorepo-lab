package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AssumeTenantDeniedException;
import com.example.auth.application.port.OperatorAssignmentPort;
import com.example.common.resilience.ResilienceClientFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.Supplier;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — internal HTTP client for the
 * admin-service assume-tenant assignment gate. Calls
 * {@code GET /internal/operator-assignments/check?oidcSubject=&tenantId=} with a
 * GAP {@code client_credentials} Bearer JWT (reuses
 * {@link GapClientCredentialsTokenProvider} + {@link ResilienceClientFactory},
 * the {@code AccountServiceClient} blueprint).
 *
 * <p><b>⚠️ fail-CLOSED</b> (the defining correctness property — opposite of the
 * account-service {@code entitled_domains} fail-soft): a token is minted ONLY
 * when admin-service returns {@code assigned=true}. Every other outcome —
 * {@code assigned=false}, 4xx (incl. not-assigned/unauthorized), 5xx,
 * circuit-open, timeout, IO, malformed body — throws
 * {@link AssumeTenantDeniedException}. The account fail-soft
 * ({@code AccountServiceUnavailableException} swallowed by the customizer) MUST
 * NOT leak here.
 */
@Slf4j
@Component
public class AdminAssignmentClient implements OperatorAssignmentPort {

    /** Property key for the admin-service base URL. */
    static final String BASE_URL_PROPERTY = "auth.admin-service.base-url";

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final GapClientCredentialsTokenProvider tokenProvider;

    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    @Autowired
    public AdminAssignmentClient(
            Environment environment,
            @Value("${auth.admin-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.admin-service.read-timeout-ms:5000}") int readTimeoutMs,
            GapClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("adminAssignment");
        this.retry = ResilienceClientFactory.buildRetry("adminAssignment");
        this.tokenProvider = tokenProvider;
    }

    /**
     * Test-only constructor pinning a single {@code baseUrl} without an
     * {@link Environment}. Mirrors {@code AccountServiceClient}.
     */
    public AdminAssignmentClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs,
                                 GapClientCredentialsTokenProvider tokenProvider) {
        this.environment = null;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("adminAssignment");
        this.retry = ResilienceClientFactory.buildRetry("adminAssignment");
        this.tokenProvider = tokenProvider;
        this.cachedBaseUrl = baseUrl;
        this.cachedRestClient = ResilienceClientFactory.buildRestClient(
                baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    private RestClient restClient() {
        if (environment == null) {
            RestClient pinned = this.cachedRestClient;
            if (pinned == null) {
                throw new IllegalStateException("Test-only AdminAssignmentClient created without baseUrl");
            }
            return pinned;
        }
        String baseUrl = environment.getProperty(BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Required property '" + BASE_URL_PROPERTY + "' is not set");
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
    public boolean isAssigned(String oidcSubject, String tenantId) {
        Supplier<Boolean> supplier = () -> doCheck(oidcSubject, tenantId);
        Supplier<Boolean> retrying = Retry.decorateSupplier(retry, supplier);
        Supplier<Boolean> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retrying);

        boolean assigned;
        try {
            assigned = resilient.get();
        } catch (RuntimeException e) {
            // FAIL-CLOSED: any failure (4xx incl. not-assigned, 5xx, circuit-open,
            // timeout, IO) denies the assumed token. Do NOT translate to a soft
            // "false-and-continue" — this is an authorization gate.
            log.warn("assume-tenant assignment check failed (fail-closed deny): "
                            + "msg={} type={} cause={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName());
            throw new AssumeTenantDeniedException(
                    "assignment check failed — admin-service unavailable or denied (fail-closed)", e);
        }

        if (!assigned) {
            // Explicit {assigned:false} → operator not assigned to the selected tenant.
            throw new AssumeTenantDeniedException(
                    "operator is not assigned to the selected tenant");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean doCheck(String oidcSubject, String tenantId) {
        // admin-service returns { "assigned": true|false }.
        Map<String, Object> body = restClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/operator-assignments/check")
                        .queryParam("oidcSubject", oidcSubject)
                        .queryParam("tenantId", tenantId)
                        .build())
                .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                .retrieve()
                .body(Map.class);
        if (body == null) {
            // Malformed/empty response → treat as a hard failure (caught above).
            throw new IllegalStateException("admin assignment-check returned no body");
        }
        Object assigned = body.get("assigned");
        return assigned instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(assigned));
    }
}
