package com.example.finance.ledger.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topic-resolution unit for the GL/AP-feed relay: the outbox row's fully dotted
 * {@code eventType} maps to {@code <eventType>.v1} (the {@code .v1} version
 * suffix), per {@code finance-ledger-events.md} § Published — emitted.
 */
class LedgerOutboxPublisherTopicTest {

    @Test
    @DisplayName("finance.ledger.entry.posted → finance.ledger.entry.posted.v1")
    void entryPostedTopic() {
        assertThat(LedgerOutboxPublisher.topicFor("finance.ledger.entry.posted"))
                .isEqualTo("finance.ledger.entry.posted.v1");
    }

    @Test
    @DisplayName("finance.ledger.period.closed → finance.ledger.period.closed.v1")
    void periodClosedTopic() {
        assertThat(LedgerOutboxPublisher.topicFor("finance.ledger.period.closed"))
                .isEqualTo("finance.ledger.period.closed.v1");
    }

    @Test
    @DisplayName("(4th incr) the generic resolver covers the reconciliation event types — no relay change")
    void reconciliationTopics() {
        assertThat(LedgerOutboxPublisher.topicFor("finance.ledger.reconciliation.completed"))
                .isEqualTo("finance.ledger.reconciliation.completed.v1");
        assertThat(LedgerOutboxPublisher.topicFor(
                "finance.ledger.reconciliation.discrepancy.detected"))
                .isEqualTo("finance.ledger.reconciliation.discrepancy.detected.v1");
    }
}
