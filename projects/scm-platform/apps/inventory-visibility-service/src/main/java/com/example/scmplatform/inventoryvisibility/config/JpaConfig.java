package com.example.scmplatform.inventoryvisibility.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for inventory-visibility-service's own persistence package.
 *
 * <p>Binds the four service-owned JpaRepository interfaces (InventoryNode /
 * InventorySnapshot / NodeStaleness / EventDedupe) explicitly to the JPA module. This
 * used to be forced by {@code java-messaging}'s {@code OutboxJpaConfig}, which declared
 * an app-wide {@code @EnableJpaRepositories} that made Spring Boot's default JPA
 * repository auto-scanning back off — with Spring Data Redis also on the classpath,
 * Spring enters strict repository configuration mode and the four interfaces would bind
 * to neither module ("no bean of type ...JpaRepository found"). TASK-MONO-406 deleted
 * that config, so the declaration below is now this service's own choice; it still
 * keeps the JPA/Redis repository split unambiguous under strict mode.
 *
 * <p>Mirrors the same pattern used in procurement-service/JpaConfig and
 * fan-platform/community-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa")
@EntityScan(basePackages = "com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa")
public class JpaConfig {
}
