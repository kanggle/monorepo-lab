package com.wms.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for notification-service. Explicit because we need
 * Spring's persistence-exception translation in @DataJpaTest slices, and
 * to keep a clean scope for the {@code @EntityScan} should the service
 * later import an autoconfig that brings additional entities (e.g.
 * libs/java-messaging in v2 if we adopt the standard outbox).
 */
@Configuration
@EntityScan(basePackages = "com.wms.notification.adapter.outbound.persistence")
@EnableJpaRepositories(basePackages = "com.wms.notification.adapter.outbound.persistence")
public class NotificationServicePersistenceConfig {

    @Bean
    static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}
