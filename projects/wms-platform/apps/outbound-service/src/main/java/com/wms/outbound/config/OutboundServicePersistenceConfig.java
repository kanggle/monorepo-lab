package com.wms.outbound.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for outbound-service.
 *
 * <p>Explicit because {@code libs/java-messaging} ships an
 * {@code @EnableJpaRepositories} of its own that backs off Spring Boot's
 * default JPA-repositories autoconfig. Without this declaration the service's
 * own repositories under {@code com.wms.outbound.adapter.out.persistence}
 * would not be scanned.
 */
@Configuration
@EntityScan(basePackages = "com.wms.outbound.adapter.out.persistence")
@EnableJpaRepositories(basePackages = "com.wms.outbound.adapter.out.persistence")
public class OutboundServicePersistenceConfig {

    /**
     * Translates Hibernate's {@code ConstraintViolationException} (and family)
     * into Spring's {@code DataIntegrityViolationException} on
     * {@code @Repository} beans.
     */
    @Bean
    static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}
