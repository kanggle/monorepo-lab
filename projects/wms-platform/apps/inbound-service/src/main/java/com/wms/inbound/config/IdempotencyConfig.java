package com.wms.inbound.config;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.filter.InboundIdempotencyErrorWriter;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.adapter.out.idempotency.RedisIdempotencyStore;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the {@link IdempotencyStore} bean and registers the shared
 * {@link IdempotencyKeyFilter} (ADR-MONO-038) for {@code inbound-service}.
 *
 * <p>Redis-backed store under all real profiles; in-memory under {@code standalone}.
 *
 * <p>The filter applies to {@code POST /api/v1/inbound/**} (webhooks excluded),
 * canonicalizes the body via the Family-A {@link JsonValueBodyCanonicalizer},
 * and runs at {@code HIGHEST_PRECEDENCE + 20} — after Spring Security (which
 * runs at {@code HIGHEST_PRECEDENCE}) but before DispatcherServlet
 * ({@code DEFAULT_FILTER_ORDER}). Behavior is unchanged from the former
 * {@code InboundIdempotencyFilter}; inbound emits no idempotency metrics.
 */
@Configuration
public class IdempotencyConfig {

    static final String API_PREFIX = "/api/v1/inbound/";
    static final String WEBHOOK_PREFIX = "/webhooks/";

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
    public FilterRegistrationBean<IdempotencyKeyFilter> idempotencyFilterRegistration(
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper) {
        IdempotencyFilterConfig config = IdempotencyFilterConfig.builder()
                .methods("POST")
                .applyToPrefixSkippingWebhook(API_PREFIX, WEBHOOK_PREFIX)
                .lockTtl(Duration.ofSeconds(30))
                .entryTtl(Duration.ofHours(24))
                .build();
        var filter = new IdempotencyKeyFilter(
                idempotencyStore,
                new JsonValueBodyCanonicalizer(objectMapper),
                new InboundIdempotencyErrorWriter(objectMapper),
                null,   // no metrics for inbound -> NO_OP
                config);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/inbound/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
