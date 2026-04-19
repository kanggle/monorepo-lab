package com.wms.master.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for master-service.
 *
 * <p>Required because {@code libs/java-messaging} ships its own
 * {@code OutboxJpaConfig} with {@code @EnableJpaRepositories}, which causes
 * Spring Boot's default {@code JpaRepositoriesAutoConfiguration} to back off.
 * Without an explicit declaration here, this service's own repositories under
 * {@code com.wms.master.adapter.out.persistence} would not be scanned.
 *
 * <p>Scope is <strong>only</strong> master-service's persistence package. The
 * lib's {@code OutboxJpaConfig} already scans {@code com.example.messaging.outbox};
 * including it here too triggers {@code BeanDefinitionOverrideException}.
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
