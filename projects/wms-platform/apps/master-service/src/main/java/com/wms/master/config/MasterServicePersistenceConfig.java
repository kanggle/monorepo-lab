package com.wms.master.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for master-service.
 *
 * <p>Scopes repository / entity scanning to <strong>only</strong> master-service's own
 * persistence package ({@code com.wms.master.adapter.out.persistence}). It used to be
 * mandatory: {@code libs/java-messaging} shipped an {@code OutboxJpaConfig} with an
 * app-wide {@code @EnableJpaRepositories}, which made Spring Boot's default
 * {@code JpaRepositoriesAutoConfiguration} back off, so without an explicit declaration
 * here the service's own repositories were not scanned (and re-scanning the lib's
 * {@code com.example.messaging.outbox} package here triggered
 * {@code BeanDefinitionOverrideException}). TASK-MONO-406 deleted that config — the
 * library now contributes no {@code @Entity} and no repository at all — so this is the
 * service's own explicit choice, not a workaround.
 */
@Configuration
@EntityScan(basePackages = "com.wms.master.adapter.out.persistence")
@EnableJpaRepositories(basePackages = "com.wms.master.adapter.out.persistence")
public class MasterServicePersistenceConfig {

    /**
     * Translates Hibernate's {@code ConstraintViolationException} and friends
     * into Spring's {@code DataIntegrityViolationException} on {@code @Repository}
     * beans. Always registered explicitly so the adapter's duplicate-to-domain
     * mapping works under {@code @DataJpaTest} slices, not just full app context.
     */
    @Bean
    static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}
