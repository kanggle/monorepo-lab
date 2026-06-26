package com.example.finance.account.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.finance.account.application.event.AccountEventPublisher;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link AccountOutboxPublisher#topicFor(String)} (TASK-FIN-BE-045).
 *
 * <p>Pins the v1 topic mapping ({@code <eventType>.v1}) and the reject-unknown
 * whitelist, preserved from the deleted {@code AccountOutboxPollingScheduler}
 * (AC-1 / F2). Mirrors ledger's {@code LedgerOutboxPublisherTopicTest}.
 */
class AccountOutboxPublisherTopicTest {

    @Test
    void mapsEachKnownEventTypeToItsV1Topic() {
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_ACCOUNT_OPENED))
                .isEqualTo("finance.account.opened.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_ACCOUNT_KYC_UPGRADED))
                .isEqualTo("finance.account.kyc.upgraded.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_ACCOUNT_STATUS_CHANGED))
                .isEqualTo("finance.account.status.changed.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_BALANCE_HELD))
                .isEqualTo("finance.balance.held.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_BALANCE_CAPTURED))
                .isEqualTo("finance.balance.captured.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_BALANCE_RELEASED))
                .isEqualTo("finance.balance.released.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_TRANSACTION_SETTLED))
                .isEqualTo("finance.transaction.settled.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_TRANSACTION_COMPLETED))
                .isEqualTo("finance.transaction.completed.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_TRANSACTION_FAILED))
                .isEqualTo("finance.transaction.failed.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_TRANSACTION_REVERSED))
                .isEqualTo("finance.transaction.reversed.v1");
        assertThat(AccountOutboxPublisher.topicFor(AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT))
                .isEqualTo("finance.compliance.sanction.hit.v1");
    }

    @Test
    void rejectsUnknownOrNullEventType() {
        assertThatThrownBy(() -> AccountOutboxPublisher.topicFor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountOutboxPublisher.topicFor("finance.unknown.event"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountOutboxPublisher.topicFor("inventory.stock.moved"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
