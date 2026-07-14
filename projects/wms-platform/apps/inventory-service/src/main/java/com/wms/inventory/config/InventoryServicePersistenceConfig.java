package com.wms.inventory.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for inventory-service.
 *
 * <p>Scopes repository / entity scanning to this service's own persistence package
 * ({@code com.wms.inventory.adapter.out.persistence}). It used to be mandatory:
 * {@code libs/java-messaging}'s {@code OutboxJpaConfig} shipped an app-wide
 * {@code @EnableJpaRepositories} that backed off Spring Boot's default
 * JPA-repositories autoconfig, so without this declaration the service's own
 * repositories were not scanned. TASK-MONO-406 deleted that config — the library now
 * contributes no {@code @Entity} and no repository at all — so this is the service's
 * own explicit choice, not a workaround.
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
