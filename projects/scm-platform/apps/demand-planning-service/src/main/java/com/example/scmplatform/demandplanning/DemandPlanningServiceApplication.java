package com.example.scmplatform.demandplanning;

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
 * events and runs <b>no transactional outbox</b>: {@link OutboxMetricsAutoConfiguration}
 * from {@code libs/java-messaging} stays excluded (nothing here publishes, so a
 * publish-failure counter would be dead weight).
 *
 * <p>The companion {@code exclude = OutboxAutoConfiguration.class} was dropped by
 * TASK-MONO-406, which deleted the library's {@code ProcessedEventJpaRepository} — it used
 * to be registered into every consumer's context and collided by bean name with this
 * service's own {@code dp_processed_events} repository. ADR-MONO-004 already placed
 * per-service dedupe entities outside the shared library; this service always owned its
 * dedupe table locally, and now nothing contests it.
 */
@SpringBootApplication(exclude = OutboxMetricsAutoConfiguration.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class DemandPlanningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandPlanningServiceApplication.class, args);
    }
}
