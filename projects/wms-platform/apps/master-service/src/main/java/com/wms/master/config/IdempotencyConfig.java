package com.wms.master.config;

import com.example.web.idempotency.BodyCanonicalizer;
import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.JsonTreeBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.in.web.filter.IdempotencyFilter;
import com.wms.master.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.master.adapter.out.idempotency.RedisIdempotencyStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

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
    BodyCanonicalizer requestBodyCanonicalizer(ObjectMapper objectMapper) {
        return new JsonTreeBodyCanonicalizer(objectMapper);
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyStore store,
            BodyCanonicalizer canonicalizer,
            ObjectMapper objectMapper,
            @Value("${master.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        IdempotencyFilter filter = new IdempotencyFilter(
                store, canonicalizer, objectMapper, Duration.ofSeconds(ttlSeconds));
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/master/*");
        // Runs after Spring Security (default order HIGHEST_PRECEDENCE + 50)
        // so unauthenticated requests short-circuit before touching the store.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
