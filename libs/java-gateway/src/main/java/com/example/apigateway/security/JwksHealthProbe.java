package com.example.apigateway.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Probes the configured JWKS endpoint at boot and fails the application fast if it stays
 * unreachable past the timeout window.
 *
 * <p>{@code NimbusReactiveJwtDecoder.withJwkSetUri(...).build()} fetches JWKS <em>lazily</em>,
 * on the first protected request — so an IdP outage at startup stays invisible until a real
 * caller eats a 401/500. This probe runs once on {@link ApplicationReadyEvent}, retries with
 * exponential backoff (1s → 2s → 4s → 8s → 16s, ~31s total) — plus a small bounded extra budget
 * specifically for a 404 (see {@link #probe()} and {@link #isTransient}, TASK-MONO-489) — and on
 * final failure logs ERROR and closes the context so Spring Boot exits non-zero and an operator
 * sees it immediately.
 *
 * <h2>Not a {@code @Component}, and that is the point</h2>
 *
 * The scm and fan copies of this class carried {@code @Component}. Moving it here with the
 * annotation intact would have registered it in <strong>every</strong> gateway that scans
 * {@code com.example.apigateway} — including <strong>wms, which has never had a JWKS startup
 * probe.</strong> wms would silently gain a boot-time dependency on the IdP being up: a
 * behaviour change, arriving under the banner of de-duplication, which is precisely what
 * ADR-MONO-048 § D6 exists to forbid.
 *
 * <p>So registration is opt-in: a gateway that wants the probe declares it as a {@code @Bean}.
 * {@code JwksHealthProbeWiringTest} asserts that wms does not (TASK-MONO-357). ADR-MONO-048
 * § D4 previously listed this class as "single-consumer"; it had two, and now four.
 *
 * <p>Consumers should guard the bean with
 * {@code @ConditionalOnProperty("gateway.jwks.startup-probe.enabled")} so slice tests, which
 * stand up no JWKS endpoint, can switch it off.
 */
public class JwksHealthProbe implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(JwksHealthProbe.class);

    /**
     * Bounded extra-retry budget for a 404 specifically (TASK-MONO-489, incident
     * 2026-07-29): a 404 can mean either "the URI is permanently wrong" or "the URI is right,
     * the route resolved, but the downstream IdP is mid-restart and has not registered the
     * route yet". {@value #CLIENT_ERROR_RETRY_ATTEMPTS} attempts,
     * {@link #CLIENT_ERROR_RETRY_DELAY} apart — a few seconds total — is enough to ride out the
     * observed restart window's tail without meaningfully delaying detection of a genuinely
     * wrong URL, which is still the more likely cause of a 404 and must keep failing fast.
     * Deliberately narrower than "any 4xx": 401/403 are auth misconfiguration, not a restart
     * symptom, and get no special treatment here — see {@link #isTransient} for why they (and an
     * exhausted 404 budget) go straight to terminal.
     */
    private static final int CLIENT_ERROR_RETRY_ATTEMPTS = 3;

    /** Delay between {@link #CLIENT_ERROR_RETRY_ATTEMPTS} — see that constant's javadoc. */
    private static final Duration CLIENT_ERROR_RETRY_DELAY = Duration.ofSeconds(1);

    private final String jwkSetUri;
    private final Duration overallTimeout;
    private final ConfigurableApplicationContext applicationContext;
    private final WebClient webClient;

    public JwksHealthProbe(
            String jwkSetUri,
            long timeoutSeconds,
            ConfigurableApplicationContext applicationContext,
            WebClient.Builder webClientBuilder) {
        this.jwkSetUri = jwkSetUri;
        this.overallTimeout = Duration.ofSeconds(timeoutSeconds);
        this.applicationContext = applicationContext;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Probing JWKS endpoint at startup: uri='{}', timeout={}s",
                jwkSetUri, overallTimeout.getSeconds());
        try {
            probe().block(overallTimeout.plusSeconds(2));
            log.info("JWKS endpoint probe succeeded.");
        } catch (Exception e) {
            log.error("JWKS endpoint probe failed after {}s for uri='{}'. "
                            + "Closing application context to fail fast. Cause: {}",
                    overallTimeout.getSeconds(), jwkSetUri, e.toString());
            applicationContext.close();
        }
    }

    /**
     * Issues a single GET against the JWKS URI, retrying until either success or
     * {@link #overallTimeout} elapses. Two retry tiers, both nested inside
     * {@link #overallTimeout} — neither extends the probe's total deadline, they only change
     * what counts as retryable within it:
     *
     * <ol>
     *   <li><b>Client-error tier</b> (innermost, {@link #isRetryableClientError}): a 404 gets
     *       {@value #CLIENT_ERROR_RETRY_ATTEMPTS} bounded extra attempts for the
     *       backend-mid-restart case (see that constant's javadoc).</li>
     *   <li><b>General tier</b> (outer, pre-existing, {@link #isTransient}): exponential backoff
     *       (1s → 2s → 4s → 8s → 16s, ~31s) for connection-refused / 5xx / timeout — and, once
     *       the client-error tier's budget is exhausted, for the 404 too, which by then is
     *       terminal rather than retried further.</li>
     * </ol>
     */
    public Mono<String> probe() {
        return webClient.get()
                .uri(jwkSetUri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(CLIENT_ERROR_RETRY_ATTEMPTS, CLIENT_ERROR_RETRY_DELAY)
                        .filter(JwksHealthProbe::isRetryableClientError)
                        .doBeforeRetry(rs -> log.warn(
                                "JWKS probe retrying possibly-restarting backend ({} of {}) "
                                        + "after error: {}",
                                rs.totalRetries() + 1, CLIENT_ERROR_RETRY_ATTEMPTS,
                                rs.failure().toString())))
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(16))
                        .filter(JwksHealthProbe::isTransient)
                        .doBeforeRetry(rs -> log.warn(
                                "JWKS probe retry {} after error: {}",
                                rs.totalRetries() + 1, rs.failure().toString())))
                .timeout(overallTimeout);
    }

    /**
     * Filter for the client-error retry tier in {@link #probe()}: only a 404 qualifies — it is
     * the status code the 2026-07-29 incident actually observed for "route not registered yet".
     * Other 4xx (401/403/etc.) are not given this treatment; they read as auth misconfiguration,
     * not a restart symptom, and fall straight through to {@link #isTransient} where they are
     * (as before this task) immediately terminal.
     */
    private static boolean isRetryableClientError(Throwable t) {
        return t instanceof WebClientResponseException wcre && wcre.getStatusCode().value() == 404;
    }

    /**
     * Classifies an error for the general retry tier in {@link #probe()}. A 404 reaching this
     * filter has already exhausted the client-error tier's {@value #CLIENT_ERROR_RETRY_ATTEMPTS}
     * bounded attempts (see {@link #isRetryableClientError}) — at that point it is no longer
     * "backend mid-restart", it is a configuration error (wrong URL), and retrying further would
     * only delay fail-fast. {@link Exceptions#isRetryExhausted} recognises that boundary: Reactor
     * wraps the original 404 in a retry-exhausted marker when the client-error tier's own budget
     * runs out, and it is that marker — not a fresh 404 — that reaches this filter, so it is
     * classified as terminal here. A non-404 4xx (401/403/etc., never retried by the tier above)
     * is terminal for the same original reason as before this task: it is a configuration error,
     * not a restart symptom. Everything else (connection refused, 5xx, timeout) is transient, as
     * before.
     */
    public static boolean isTransient(Throwable t) {
        if (Exceptions.isRetryExhausted(t)) {
            return false;
        }
        if (t instanceof WebClientResponseException wcre) {
            return !wcre.getStatusCode().is4xxClientError();
        }
        return true;
    }
}
