package com.wms.outbound.config;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.IdempotencyMetrics;
import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.filter.OutboundIdempotencyErrorWriter;
import com.wms.outbound.adapter.in.web.filter.OutboundIdempotencyMetrics;
import com.wms.outbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.outbound.adapter.out.idempotency.RedisIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the {@link IdempotencyStore} bean and registers the shared
 * {@link IdempotencyKeyFilter} (ADR-MONO-038) that enforces the
 * {@code Idempotency-Key} contract end-to-end (TASK-BE-051).
 *
 * <p>Redis-backed store under all real profiles; in-memory under {@code standalone}.
 *
 * <p>The filter applies to {@code POST/PATCH/PUT/DELETE /api/v1/outbound/**}
 * (webhooks excluded), enforces a 255-char Idempotency-Key limit, canonicalizes
 * the body via the Family-A {@link JsonValueBodyCanonicalizer}, and emits the
 * outbound idempotency metrics when a {@link MeterRegistry} is present. It runs
 * at {@code HIGHEST_PRECEDENCE + 20} — after Spring Security but before
 * DispatcherServlet. Behavior is unchanged from the former
 * {@code OutboundIdempotencyFilter}.
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
    public FilterRegistrationBean<IdempotencyKeyFilter> outboundIdempotencyFilterRegistration(
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        IdempotencyMetrics metrics = meterRegistry != null
                ? new OutboundIdempotencyMetrics(meterRegistry)
                : IdempotencyMetrics.NO_OP;
        IdempotencyFilterConfig config = IdempotencyFilterConfig.builder()
                .methods("POST", "PATCH", "PUT", "DELETE")
                .applyToPrefixSkippingWebhook("/api/v1/outbound/", "/webhooks/")
                .maxKeyLength(255)
                .lockTtl(Duration.ofSeconds(30))
                .entryTtl(Duration.ofHours(24))
                .build();
        var filter = new IdempotencyKeyFilter(
                idempotencyStore,
                new JsonValueBodyCanonicalizer(objectMapper),
                new OutboundIdempotencyErrorWriter(objectMapper),
                metrics,
                config);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/outbound/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
