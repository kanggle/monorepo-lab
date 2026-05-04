package com.example.scmplatform.gateway.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Probes the configured JWKS endpoint at boot time and fails the application fast
 * if it is unreachable for longer than the configured timeout window.
 *
 * <p>{@link org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder}'s
 * {@code .withJwkSetUri(...).build()} fetches JWKS lazily on the first protected
 * request — meaning a GAP outage at startup is not surfaced until the first
 * caller hits a 401/500. The task spec § Edge Cases mandates the opposite:
 *
 * <blockquote>OIDC discovery 실패 (GAP 다운): gateway 시작 시 retry. 30초 후에도
 * 실패하면 fail-fast (Spring Boot 부트 실패) — 운영자가 즉시 인지.</blockquote>
 *
 * <p>This probe runs once on {@link ApplicationReadyEvent}, retries with
 * exponential backoff (1s, 2s, 4s, 8s, 16s — total ~31s window), and on final
 * failure logs ERROR + calls {@link ConfigurableApplicationContext#close()} so
 * Spring Boot exits with a non-zero status code.
 *
 * <p>Disabled by setting {@code gateway.jwks.startup-probe.enabled=false} —
 * required for slice tests and unit tests that do not stand up a JWKS endpoint.
 */
@Component
@ConditionalOnProperty(value = "gateway.jwks.startup-probe.enabled", havingValue = "true", matchIfMissing = true)
public class JwksHealthProbe implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(JwksHealthProbe.class);

    private final String jwkSetUri;
    private final Duration overallTimeout;
    private final ConfigurableApplicationContext applicationContext;
    private final WebClient webClient;

    public JwksHealthProbe(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${gateway.jwks.startup-probe.timeout-seconds:30}") long timeoutSeconds,
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
     * Issues a single GET against the JWKS URI, retrying with exponential backoff
     * (1s → 2s → 4s → 8s → 16s) until either success or the {@link #overallTimeout}
     * elapses. 4xx responses are treated as terminal (probe gives up immediately —
     * the URI is misconfigured, retrying will not help).
     */
    Mono<String> probe() {
        return webClient.get()
                .uri(jwkSetUri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(16))
                        .filter(JwksHealthProbe::isTransient)
                        .doBeforeRetry(rs -> log.warn(
                                "JWKS probe retry {} after error: {}",
                                rs.totalRetries() + 1, rs.failure().toString())))
                .timeout(overallTimeout);
    }

    /**
     * 4xx responses are configuration errors (wrong URL, auth issue) — retrying
     * will not help, fail fast immediately. Other failures (connection refused,
     * 5xx, timeout) are transient.
     */
    static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return !wcre.getStatusCode().is4xxClientError();
        }
        return true;
    }
}
