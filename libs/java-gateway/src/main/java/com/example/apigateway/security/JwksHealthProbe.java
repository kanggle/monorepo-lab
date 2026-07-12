package com.example.apigateway.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Probes the configured JWKS endpoint at boot and fails the application fast if it stays
 * unreachable past the timeout window.
 *
 * <p>{@code NimbusReactiveJwtDecoder.withJwkSetUri(...).build()} fetches JWKS <em>lazily</em>,
 * on the first protected request — so an IdP outage at startup stays invisible until a real
 * caller eats a 401/500. This probe runs once on {@link ApplicationReadyEvent}, retries with
 * exponential backoff (1s → 2s → 4s → 8s → 16s, ~31s total), and on final failure logs ERROR
 * and closes the context so Spring Boot exits non-zero and an operator sees it immediately.
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
     * Issues a single GET against the JWKS URI, retrying with exponential backoff until either
     * success or {@link #overallTimeout} elapses. 4xx responses are terminal — the URI is
     * misconfigured and retrying cannot help.
     */
    public Mono<String> probe() {
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
     * 4xx is a configuration error (wrong URL, auth problem) — retrying will not help, so give
     * up at once. Everything else (connection refused, 5xx, timeout) is transient.
     */
    public static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            return !wcre.getStatusCode().is4xxClientError();
        }
        return true;
    }
}
