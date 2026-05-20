package com.kanggle.platformconsole.bff.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Factory for per-domain {@link RestClient} instances.
 *
 * <p>OTel {@code traceparent} propagation is enabled automatically via Spring Boot 3.4
 * auto-configuration when {@code micrometer-tracing-bridge-otel} is on the classpath:
 * the {@code ObservationRegistry} is injected into {@code RestClient.Builder} which
 * instruments outbound calls with W3C trace context (architecture.md § Observability).
 *
 * <p>Per-domain concrete clients (with base URL + credential injection) are
 * wired by TASK-PC-FE-011 when the first composition route is added. This config
 * provides the shared builder baseline.
 */
@Configuration
public class RestClientConfig {

    /**
     * Shared {@link RestClient.Builder} with OTel observation wiring.
     *
     * <p>TASK-PC-FE-011 derives per-domain builders from this baseline:
     * {@code restClientBuilder.baseUrl(...).build()}.
     */
    @Bean
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .observationRegistry(observationRegistry);
    }
}
