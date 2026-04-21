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
}
