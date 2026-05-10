package com.wms.outbound.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.filter.OutboundIdempotencyFilter;
import com.wms.outbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.outbound.adapter.out.idempotency.RedisIdempotencyStore;
import com.wms.outbound.application.port.out.IdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the {@link IdempotencyStore} bean and the
 * {@link OutboundIdempotencyFilter} that enforces the
 * {@code Idempotency-Key} contract end-to-end (TASK-BE-051).
 *
 * <p>Redis-backed store under all real profiles; in-memory under {@code standalone}.
 *
 * <p>The filter runs at {@code HIGHEST_PRECEDENCE + 20} — after Spring Security
 * (which runs at {@code HIGHEST_PRECEDENCE}) but before DispatcherServlet
 * ({@code DEFAULT_FILTER_ORDER}). This mirrors the inbound-service pattern.
 */
@Configuration
public class RedisConfig {

    @Bean
    @Profile("standalone")
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @Profile("!standalone")
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper) {
        return new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<OutboundIdempotencyFilter> outboundIdempotencyFilterRegistration(
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        var filter = new OutboundIdempotencyFilter(
                idempotencyStore, objectMapper, meterRegistryProvider.getIfAvailable());
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/outbound/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
