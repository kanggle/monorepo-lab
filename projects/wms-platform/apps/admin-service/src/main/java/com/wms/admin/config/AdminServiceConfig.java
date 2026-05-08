package com.wms.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.port.IdempotencyStore;
import com.wms.admin.infra.idempotency.IdempotencyFilter;
import com.wms.admin.infra.idempotency.InMemoryIdempotencyStore;
import com.wms.admin.infra.idempotency.RedisIdempotencyStore;
import com.wms.admin.infra.idempotency.RequestBodyCanonicalizer;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-cutting beans: clock, idempotency wiring, transaction template.
 */
@Configuration
public class AdminServiceConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    @Bean
    @Profile("standalone")
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore inMemoryIdempotencyStore(Clock clock) {
        return new InMemoryIdempotencyStore(clock);
    }

    @Bean
    @Profile("!standalone")
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore redisIdempotencyStore(StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper) {
        return new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Bean
    RequestBodyCanonicalizer requestBodyCanonicalizer(ObjectMapper objectMapper) {
        return new RequestBodyCanonicalizer(objectMapper);
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyStore store,
            RequestBodyCanonicalizer canonicalizer,
            ObjectMapper objectMapper,
            @Value("${admin.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        IdempotencyFilter filter = new IdempotencyFilter(
                store, canonicalizer, objectMapper, Duration.ofSeconds(ttlSeconds));
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/admin/*");
        // After Spring Security so unauthenticated requests short-circuit.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
