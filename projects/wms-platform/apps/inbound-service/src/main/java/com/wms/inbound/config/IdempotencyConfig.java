package com.wms.inbound.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.adapter.out.idempotency.RedisIdempotencyStore;
import com.wms.inbound.application.port.out.IdempotencyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the {@link IdempotencyStore} bean.
 *
 * <p>Redis-backed under all real profiles; in-memory under {@code standalone}.
 * The future filter that consumes the store ({@code IdempotencyFilter}) lives
 * downstream — this bootstrap task only stands the infrastructure up.
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
}
