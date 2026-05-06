package com.example.scmplatform.inventoryvisibility;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * inventory-visibility-service — cross-node read-model for wms / supplier /
 * 3PL / in-transit stock. Consumes wms-platform inventory events (cross-project)
 * and exposes a read-only REST API.
 *
 * <p>Service Type: rest-api + event-consumer (PROJECT.md Service Map).
 * Architecture: Hexagonal (domain / application / adapter).
 * batch-heavy trait: {@link EnableScheduling} + ShedLock for clustered staleness detection.
 */
@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class InventoryVisibilityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryVisibilityServiceApplication.class, args);
    }
}
