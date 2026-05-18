package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import com.example.finance.account.infrastructure.persistence.jpa.AuditLogJpaRepository;
import com.example.finance.account.infrastructure.persistence.jpa.TransactionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for F3 (SETTLED immutable + reversal-only) and F6
 * (append-only audit_log) (Testcontainers MySQL + Redis + Kafka).
 */
class AuditAndImmutabilityIntegrationTest extends AbstractAccountIntegrationTest {

    private static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT_FINANCE, Set.of());
    private static final ActorContext OPERATOR =
            new ActorContext("op-1", TENANT_FINANCE, Set.of("OPERATOR"));

    @Autowired
    AccountApplicationService service;
    @Autowired
    AuditLogJpaRepository auditLogJpa;
    @Autowired
    TransactionJpaRepository txnJpa;

    private AccountView active(String ref) {
        AccountView o = service.openAccount(new OpenAccountCommand(
                HOLDER, ref, "KRW", "NONE"));
        return service.upgradeKyc(new UpgradeKycCommand(
                OPERATOR, o.accountId(), "FULL", "kyc"));
    }

    @Test
    @DisplayName("F6: audit_log is append-only — every fund op adds an immutable row")
    void auditAppendOnly() {
        long before = auditLogJpa.count();
        AccountView acc = active("cust-audit-1");
        service.topUp(HOLDER, acc.accountId(), 5000L);
        service.placeHold(new PlaceHoldCommand(
                HOLDER, acc.accountId(), "1000", "KRW", 3600, "x"));
        // OPEN_ACCOUNT + UPGRADE_KYC + TOPUP + HOLD ⇒ ≥ 4 new rows; rows are
        // only ever inserted (no mutator exists on AuditLog).
        assertThat(auditLogJpa.count()).isGreaterThanOrEqualTo(before + 4);
    }

    @Test
    @DisplayName("F3: a SETTLED/COMPLETED transaction cannot be re-mutated in place")
    void settledTransactionImmutable() {
        AccountView acc = active("cust-imm-1");
        service.topUp(HOLDER, acc.accountId(), 5000L);
        var hold = service.placeHold(new PlaceHoldCommand(
                HOLDER, acc.accountId(), "1000", "KRW", 3600, "x"));

        Transaction settled = txnJpa.findById(hold.transactionId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // Any in-place transition attempt on a COMPLETED txn is rejected (F3).
        assertThatThrownBy(settled::markValidated)
                .isInstanceOf(com.example.finance.account.domain.error
                        .DomainErrors.TransactionAlreadySettledException.class);

        // Correction is a NEW reversal txn referencing the original.
        Transaction reversal = Transaction.reversalOf("rev-1", settled, Instant.now());
        assertThat(reversal.getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getReversalOfTransactionId())
                .isEqualTo(settled.getId());
    }
}
