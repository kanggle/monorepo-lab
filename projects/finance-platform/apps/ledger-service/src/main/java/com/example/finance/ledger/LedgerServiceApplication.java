package com.example.finance.ledger;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * finance-platform ledger-service entry point (TASK-FIN-BE-007).
 *
 * <p>The double-entry general ledger, first increment. Consumes account-service
 * transaction events ({@code finance.transaction.completed.v1} /
 * {@code .reversed.v1}) and posts balanced journal entries per the fixed Posting
 * Policy, then serves a read-only API (entry detail / per-account balance / trial
 * balance).
 *
 * <p>Service Type: {@code rest-api} + {@code event-consumer} (event-driven
 * derivation with a read API — same dual-type as erp read-model-service).
 * Architecture: Hexagonal + DDD (domain / application / infrastructure /
 * messaging / presentation).
 *
 * <p><b>Publishing consumer</b> (3rd increment, TASK-FIN-BE-009 — the GL/AP feed):
 * this service holds the authoritative double-entry ledger and now also emits a
 * one-way GL/AP feed ({@code finance.ledger.entry.posted.v1} /
 * {@code finance.ledger.period.closed.v1}) via a <b>per-service transactional
 * outbox</b> (the {@code OutboxRow} path — {@code ledger_outbox} +
 * {@code LedgerOutboxPublisher}). It never writes back (the wallet single-entry
 * balance is account-service's authority; the feed is downstream-only).
 *
 * <p>The libs {@link OutboxAutoConfiguration} (and its metrics companion) stay
 * <b>excluded</b>: that auto-config entity-scans the libs
 * {@code ProcessedEventJpaEntity} (mapped to {@code processed_events}), which would
 * collide with this service's OWN {@code processed_events} consumer-dedupe table.
 * Using the per-service {@code OutboxRow} path keeps the dedupe table untouched (F1)
 * while still emitting the feed. {@code @EnableScheduling} drives the outbox relay.
 */
@SpringBootApplication(exclude = {
        OutboxAutoConfiguration.class,
        OutboxMetricsAutoConfiguration.class
})
@EnableScheduling
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
