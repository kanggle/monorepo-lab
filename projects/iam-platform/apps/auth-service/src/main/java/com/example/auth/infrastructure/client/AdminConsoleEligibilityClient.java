package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.OperatorEligibilityUnavailableException;
import com.example.auth.application.port.OperatorConsoleEligibilityPort;
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
 * TASK-MONO-772 S4 (auth-to-admin.md § GET /internal/operators/console-eligibility) — the console-eligibility
 * read the issuer makes for every console {@code authorization_code} / {@code refresh_token} issuance to a
 * consumer-pool principal.
 *
 * <p>Same base URL ({@code auth.admin-service.base-url}), timeouts and GAP {@code client_credentials} Bearer as
 * {@link AdminAssignmentClient} / {@link AdminOperatorFacetClient}, but its <b>own</b> circuit breaker / retry
 * ({@code adminConsoleEligibility}): a console-login outage must not open the breaker that gates assume-tenant
 * issuance or the pool-move batch (contract «별도 breaker 이름»).
 *
 * <p><b>fail-CLOSED</b>: anything but a parseable {@code {"eligible": bool}} throws
 * {@link OperatorEligibilityUnavailableException}. A missing / non-boolean field is not «no» — it is «no answer».
 */
@Slf4j
@Component
public class AdminConsoleEligibilityClient implements OperatorConsoleEligibilityPort {

    static final String PATH = "/internal/operators/console-eligibility";

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final IamClientCredentialsTokenProvider tokenProvider;

    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    public AdminConsoleEligibilityClient(
            Environment environment,
            @Value("${auth.admin-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.admin-service.read-timeout-ms:5000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("adminConsoleEligibility");
        this.retry = ResilienceClientFactory.buildRetry("adminConsoleEligibility");
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
    public boolean isConsoleEligible(String accountId) {
        Supplier<Boolean> supplier = () -> doCheck(accountId);
        Supplier<Boolean> retrying = Retry.decorateSupplier(retry, supplier);
        Supplier<Boolean> resilient = CircuitBreaker.decorateSupplier(circuitBreaker, retrying);
        try {
            return resilient.get();
        } catch (RuntimeException e) {
            log.warn("console-eligibility check failed (fail-closed — no console token): type={} cause={}",
                    e.getClass().getName(), e.getCause() == null ? "null" : e.getCause().getClass().getName());
            throw new OperatorEligibilityUnavailableException("admin-service console-eligibility check failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Boolean doCheck(String accountId) {
        Map<String, Object> body = restClient().get()
                .uri(uriBuilder -> uriBuilder.path(PATH).queryParam("accountId", accountId).build())
                .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                .retrieve()
                .body(Map.class);
        Object value = body == null ? null : body.get("eligible");
        if (!(value instanceof Boolean eligible)) {
            throw new IllegalStateException("admin console-eligibility returned no boolean eligible");
        }
        return eligible;
    }
}
