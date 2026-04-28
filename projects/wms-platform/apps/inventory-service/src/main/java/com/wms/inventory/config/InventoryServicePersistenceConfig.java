package com.wms.inventory.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for inventory-service.
 *
 * <p>Explicit because {@code libs/java-messaging} ships an
 * {@code @EnableJpaRepositories} of its own that backs off Spring Boot's
 * default JPA-repositories autoconfig. Without this declaration the service's
 * own repositories under {@code com.wms.inventory.adapter.out.persistence}
 * would not be scanned.
 *
 * <p>Scope is limited to this service's persistence package — the lib's
 * {@code OutboxJpaConfig} already scans {@code com.example.messaging.outbox}.
 */
@Configuration
@EntityScan(basePackages = "com.wms.inventory.adapter.out.persistence")
@EnableJpaRepositories(basePackages = "com.wms.inventory.adapter.out.persistence")
public class InventoryServicePersistenceConfig {

    /**
     * Translates Hibernate's {@code ConstraintViolationException} (and family)
     * into Spring's {@code DataIntegrityViolationException} on
     * {@code @Repository} beans. Required for the EventDedupe adapter's
     * "duplicate signaled by PK violation" path under {@code @DataJpaTest} slices.
     */
    @Bean
    static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}
