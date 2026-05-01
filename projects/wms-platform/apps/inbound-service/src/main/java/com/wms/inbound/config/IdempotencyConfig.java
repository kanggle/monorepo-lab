package com.wms.inbound.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.filter.InboundIdempotencyFilter;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.adapter.out.idempotency.RedisIdempotencyStore;
import com.wms.inbound.application.port.out.IdempotencyStore;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the {@link IdempotencyStore} bean and registers the
 * {@link InboundIdempotencyFilter}.
 *
 * <p>Redis-backed store under all real profiles; in-memory under {@code standalone}.
 *
 * <p>The filter runs at {@code HIGHEST_PRECEDENCE + 20} — after Spring Security
 * (which runs at {@code HIGHEST_PRECEDENCE}) but before DispatcherServlet
 * ({@code DEFAULT_FILTER_ORDER}).
 */
@Configuration
public class IdempotencyConfig {

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
    public FilterRegistrationBean<InboundIdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper) {
        var filter = new InboundIdempotencyFilter(idempotencyStore, objectMapper);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/inbound/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
