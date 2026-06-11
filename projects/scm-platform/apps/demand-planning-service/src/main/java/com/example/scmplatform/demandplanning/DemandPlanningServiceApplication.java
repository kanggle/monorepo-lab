package com.example.scmplatform.demandplanning;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * demand-planning-service — scm's 4th domain service (ADR-027 Phase 1).
 * Consumes wms low-stock alerts → evaluates reorder policy → raises reorder suggestions.
 * Nightly batch sweep via ShedLock. REST surface for operator inspection + admin seed.
 *
 * <p>Service Type: event-consumer + batch-job + rest-api.
 * Architecture: Hexagonal (domain / application / adapter).
 *
 * <p>This is a <b>terminal consumer</b> — it raises local {@code reorder_suggestion}
 * aggregates and (in BE-025) calls procurement synchronously; it publishes no domain
 * events and runs <b>no transactional outbox</b>. {@link OutboxAutoConfiguration} (and
 * its metrics companion) from {@code libs/java-messaging} are excluded so the lib's
 * {@code ProcessedEventJpaRepository} bean is not registered — otherwise it collides
 * with this service's own {@code dp_processed_events} repository (same bean name).
 * The service owns its dedupe table locally (feedback §13, fan notification-service precedent).
 */
@SpringBootApplication(exclude = {
        OutboxAutoConfiguration.class,
        OutboxMetricsAutoConfiguration.class
})
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class DemandPlanningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandPlanningServiceApplication.class, args);
    }
}
