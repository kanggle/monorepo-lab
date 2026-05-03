package com.example.fanplatform.artist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean wiring for the Redis-backed artist directory cache. Spring Boot's
 * default {@code StringRedisTemplate} auto-configuration is sufficient — we
 * only need to ensure an {@link ObjectMapper} is available for serialization
 * (Spring Boot's default mapper is wired automatically; this bean is here so
 * future test profiles can override it without breaking auto-config).
 */
@Configuration
public class RedisCacheConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
