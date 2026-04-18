package com.wms.master.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
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
 * <p>Covers both master-service's persistence adapter and the shared messaging
 * outbox entities via {@code basePackageClasses}.
 */
@Configuration
@EntityScan(basePackages = {
        "com.wms.master.adapter.out.persistence",
        "com.example.messaging.outbox"
})
@EnableJpaRepositories(basePackages = {
        "com.wms.master.adapter.out.persistence",
        "com.example.messaging.outbox"
})
public class MasterServicePersistenceConfig {
}
