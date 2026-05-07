package com.example.scmplatform.inventoryvisibility.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for inventory-visibility-service's own persistence package.
 *
 * <p>Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's
 * default JPA repository auto-scanning. With Spring Data Redis also on the
 * classpath, Spring enters strict repository configuration mode and the four
 * service-owned JpaRepository interfaces (InventoryNode / InventorySnapshot /
 * NodeStaleness / EventDedupe) are not bound to either module — boot fails
 * with "no bean of type ...JpaRepository found".
 *
 * <p>Mirrors the same pattern used in procurement-service/JpaConfig and
 * fan-platform/community-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa")
@EntityScan(basePackages = "com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa")
public class JpaConfig {
}
