package com.example.observability.config;

import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-836: p99 latency alerts query {@code http_server_requests_seconds_bucket},
 * which Spring Boot does not export unless histogram buckets are turned on.
 *
 * <p>Every case scrapes a real {@link PrometheusMeterRegistry} built by Spring Boot's own
 * metrics auto-configuration (so its property-driven {@code MeterFilter} takes part, and
 * names go through the Prometheus naming convention), not a {@code SimpleMeterRegistry}.
 */
class HttpServerRequestsHistogramTest {

    private static final AutoConfigurations BOOT_METRICS = AutoConfigurations.of(
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            PrometheusMetricsExportAutoConfiguration.class);

    private final ApplicationContextRunner withLibrary = new ApplicationContextRunner()
            .withConfiguration(BOOT_METRICS)
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfig.class))
            .withPropertyValues("spring.application.name=test-service");

    private final ApplicationContextRunner withoutLibrary = new ApplicationContextRunner()
            .withConfiguration(BOOT_METRICS);

    private static String recordAndScrape(PrometheusMeterRegistry registry, String timerName) {
        Timer.builder(timerName)
                .tag("uri", "/x")
                .register(registry)
                .record(Duration.ofMillis(42));
        return registry.scrape();
    }

    @Test
    @DisplayName("① 라이브러리가 있으면 http.server.requests 의 _bucket 이 scrape 에 나온다")
    void bucketsExported_withLibrary() {
        withLibrary.run(context -> {
            String scrape = recordAndScrape(context.getBean(PrometheusMeterRegistry.class),
                    "http.server.requests");
            assertThat(scrape).contains("http_server_requests_seconds_bucket{");
        });
    }

    @Test
    @DisplayName("② 대조군 — 라이브러리 없이는 같은 기록에서 _bucket 이 없다(Spring Boot 기본)")
    void noBuckets_withoutLibrary_control() {
        withoutLibrary.run(context -> {
            String scrape = recordAndScrape(context.getBean(PrometheusMeterRegistry.class),
                    "http.server.requests");
            assertThat(scrape)
                    .contains("http_server_requests_seconds_count{")
                    .doesNotContain("http_server_requests_seconds_bucket");
        });
    }

    @Test
    @DisplayName("③ 이름이 다른 타이머(접두사가 같아도)에는 _bucket 이 생기지 않는다")
    void otherTimers_untouched() {
        withLibrary.run(context -> {
            PrometheusMeterRegistry registry = context.getBean(PrometheusMeterRegistry.class);
            String scrape = recordAndScrape(registry, "http.server.requests.other");
            scrape = scrape + recordAndScrape(registry, "outbox.publish");
            assertThat(scrape)
                    .contains("http_server_requests_other_seconds_count{")
                    .doesNotContain("http_server_requests_other_seconds_bucket")
                    .doesNotContain("outbox_publish_seconds_bucket");
        });
    }

    @Test
    @DisplayName("④ 명시적 percentiles-histogram.http.server.requests=false 는 존중된다")
    void explicitFalse_wins() {
        withLibrary
                .withPropertyValues(
                        "management.metrics.distribution.percentiles-histogram.http.server.requests=false")
                .run(context -> {
                    String scrape = recordAndScrape(context.getBean(PrometheusMeterRegistry.class),
                            "http.server.requests");
                    assertThat(scrape)
                            .contains("http_server_requests_seconds_count{")
                            .doesNotContain("http_server_requests_seconds_bucket");
                });
    }

    @Test
    @DisplayName("④' percentiles-histogram.all=false 도 존중된다")
    void explicitAllFalse_wins() {
        withLibrary
                .withPropertyValues("management.metrics.distribution.percentiles-histogram.all=false")
                .run(context -> {
                    String scrape = recordAndScrape(context.getBean(PrometheusMeterRegistry.class),
                            "http.server.requests");
                    assertThat(scrape).doesNotContain("http_server_requests_seconds_bucket");
                });
    }
}
