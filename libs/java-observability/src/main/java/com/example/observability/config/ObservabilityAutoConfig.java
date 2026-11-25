package com.example.observability.config;

import com.example.observability.filter.MdcTraceFilter;
import com.example.observability.filter.MdcTraceWebFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
public class ObservabilityAutoConfig {

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletFilterConfig {

        @Bean
        public MdcTraceFilter mdcTraceFilter() {
            return new MdcTraceFilter();
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveFilterConfig {

        @Bean
        public MdcTraceWebFilter mdcTraceWebFilter() {
            return new MdcTraceWebFilter();
        }
    }

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerCommonTagsConfig {

        @Bean
        public io.micrometer.common.KeyValues commonTags(
                @Value("${spring.application.name:unknown}") String serviceName) {
            return io.micrometer.common.KeyValues.of("service", serviceName);
        }

        @Bean
        public io.micrometer.core.instrument.config.MeterFilter commonTagsMeterFilter(
                @Value("${spring.application.name:unknown}") String serviceName) {
            return io.micrometer.core.instrument.config.MeterFilter.commonTags(
                    io.micrometer.core.instrument.Tags.of("service", serviceName));
        }
    }

    /**
     * Publishes histogram buckets for {@code http.server.requests} so that
     * {@code http_server_requests_seconds_bucket} exists and p99 latency alerts
     * ({@code histogram_quantile(0.99, ...)}) have data. Spring Boot exports only
     * {@code _count}, {@code _sum} and {@code _max} for this timer by default, and
     * no consumer of this library turned buckets on (TASK-MONO-836) — so every such
     * alert was permanently no-data and could never fire.
     *
     * <p>Exact name match only: {@code http.server.requests.active} and every other
     * meter keep their default (bucket series multiply cardinality).
     *
     * <p>An explicit setting wins: when
     * {@code management.metrics.distribution.percentiles-histogram.http.server.requests}
     * (or {@code ...percentiles-histogram.all}) is set, Spring Boot's property filter
     * has already put a non-null value into the config and this filter leaves it alone,
     * whichever order the two filters run in.
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class HttpServerRequestsHistogramConfig {

        static final String HTTP_SERVER_REQUESTS = "http.server.requests";

        @Bean
        public io.micrometer.core.instrument.config.MeterFilter httpServerRequestsHistogramMeterFilter() {
            return new io.micrometer.core.instrument.config.MeterFilter() {
                @Override
                public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                        io.micrometer.core.instrument.Meter.Id id,
                        io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {
                    if (!HTTP_SERVER_REQUESTS.equals(id.getName())
                            || config.isPercentileHistogram() != null) {
                        return config;
                    }
                    return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
            };
        }
    }
}
