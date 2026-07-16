package com.wms.inventory.config;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.IdempotencyMetrics;
import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.in.web.filter.InventoryIdempotencyErrorWriter;
import com.wms.inventory.adapter.in.web.filter.InventoryIdempotencyMetrics;
import com.wms.inventory.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inventory.adapter.out.idempotency.RedisIdempotencyStore;
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
 * {@code Idempotency-Key} contract for {@code inventory-service}'s mutating REST
 * endpoints (TASK-BE-505).
 *
 * <p>Redis-backed store under all real profiles; in-memory under {@code standalone}.
 *
 * <p>Every mutating inventory endpoint is a {@code POST} under
 * {@code /api/v1/inventory/**} (adjustments, mark-/write-off-damaged, transfers,
 * reservation create/confirm/release); the service has no webhooks, so a plain
 * {@code POST} prefix filter needs no webhook exclusion. The filter canonicalizes
 * the body via the Family-A {@link JsonValueBodyCanonicalizer}, caps the key at
 * 100 chars (matching the {@code idempotency_key VARCHAR(100)} columns so an
 * over-length key returns a clean 400 rather than failing at INSERT), and runs at
 * {@code HIGHEST_PRECEDENCE + 20} — after Spring Security but before
 * DispatcherServlet. Same-key/same-body replays the cached 2xx; same-key/
 * different-body returns 409 {@code DUPLICATE_REQUEST}; an absent key is left for
 * the controller's {@code requireIdempotencyKey} 400.
 */
@Configuration
public class IdempotencyConfig {

    static final String API_PREFIX = "/api/v1/inventory/";

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
            ObjectMapper objectMapper,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        IdempotencyMetrics metrics = meterRegistry != null
                ? new InventoryIdempotencyMetrics(meterRegistry)
                : IdempotencyMetrics.NO_OP;
        IdempotencyFilterConfig config = IdempotencyFilterConfig.builder()
                .methods("POST")
                .pathPredicate(uri -> uri != null && uri.startsWith(API_PREFIX))
                .maxKeyLength(100)
                .lockTtl(Duration.ofSeconds(30))
                .entryTtl(Duration.ofHours(24))
                .build();
        var filter = new IdempotencyKeyFilter(
                idempotencyStore,
                new JsonValueBodyCanonicalizer(objectMapper),
                new InventoryIdempotencyErrorWriter(objectMapper, meterRegistry),
                metrics,
                config);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/inventory/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
