package com.example.finance.ledger;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import com.example.messaging.outbox.OutboxMetricsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * <p><b>Terminal consumer</b>: this service holds the authoritative double-entry
 * ledger but never re-emits or writes back (the wallet single-entry balance is
 * account-service's authority; the GL/AP feed is a deferred increment). It
 * therefore runs <b>no transactional outbox</b> — {@link OutboxAutoConfiguration}
 * (and its metrics companion) from {@code libs/java-messaging} are excluded so no
 * outbox schema / publish path is required. Consumer idempotency (F1) lives
 * entirely in this service's own {@code processed_events} dedupe table.
 */
@SpringBootApplication(exclude = {
        OutboxAutoConfiguration.class,
        OutboxMetricsAutoConfiguration.class
})
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
