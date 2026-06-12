package com.kanggle.platformconsole.bff.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Factory for per-domain {@link RestClient} instances.
 *
 * <p>OTel {@code traceparent} propagation is enabled automatically via Spring Boot 3.4
 * auto-configuration when {@code micrometer-tracing-bridge-otel} is on the classpath:
 * the {@code ObservationRegistry} is injected into {@code RestClient.Builder} which
 * instruments outbound calls with W3C trace context (architecture.md § Observability).
 *
 * <p><b>6 per-domain {@link RestClient} beans (TASK-PC-FE-011 + TASK-MONO-241)</b>:
 * <pre>
 *   gapRestClient       → consolebff.outbound.gap.base-url
 *   wmsRestClient       → consolebff.outbound.wms.base-url
 *   scmRestClient       → consolebff.outbound.scm.base-url
 *   financeRestClient   → consolebff.outbound.finance.base-url
 *   erpRestClient       → consolebff.outbound.erp.base-url
 *   ecommerceRestClient → consolebff.outbound.ecommerce.base-url  (Domain Health leg only — § 2.4.9.2)
 * </pre>
 *
 * <p>Each bean shares the OTel-traced builder baseline ({@link #restClientBuilder})
 * and applies a per-leg request/read timeout aligned with the composition's 5s
 * total budget (per-leg 2s; § 2.4.9.1 Implementation guidance).
 *
 * <p>The per-leg circuit-breaker / retry primitives from {@code libs/java-web}
 * (Resilience4j) are applied at the call site (see the composition use case)
 * since the use case decides per-leg vs composition-level error classification.
 */
@Configuration
public class RestClientConfig {

    private static final Duration PER_LEG_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Shared {@link RestClient.Builder} with OTel observation wiring.
     * Used as the baseline for every per-domain client below.
     */
    @Bean
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .observationRegistry(observationRegistry);
    }

    @Bean(name = "gapRestClient")
    public RestClient gapRestClient(RestClient.Builder builder,
                                    @Value("${consolebff.outbound.gap.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean(name = "wmsRestClient")
    public RestClient wmsRestClient(RestClient.Builder builder,
                                    @Value("${consolebff.outbound.wms.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean(name = "scmRestClient")
    public RestClient scmRestClient(RestClient.Builder builder,
                                    @Value("${consolebff.outbound.scm.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean(name = "financeRestClient")
    public RestClient financeRestClient(RestClient.Builder builder,
                                        @Value("${consolebff.outbound.finance.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean(name = "erpRestClient")
    public RestClient erpRestClient(RestClient.Builder builder,
                                    @Value("${consolebff.outbound.erp.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean(name = "ecommerceRestClient")
    public RestClient ecommerceRestClient(RestClient.Builder builder,
                                          @Value("${consolebff.outbound.ecommerce.base-url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    /**
     * Per-leg timeout-bounded request factory. Both connect and read timeouts
     * are capped at {@link #PER_LEG_TIMEOUT} so a slow producer leg degrades
     * within the composition's per-leg budget (§ 2.4.9.1).
     */
    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) PER_LEG_TIMEOUT.toMillis());
        factory.setReadTimeout((int) PER_LEG_TIMEOUT.toMillis());
        return factory;
    }
}
