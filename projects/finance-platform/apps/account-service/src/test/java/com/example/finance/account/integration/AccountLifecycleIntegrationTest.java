package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.command.CaptureHoldCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.ReleaseHoldCommand;
import com.example.finance.account.application.command.TransferCommand;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.domain.error.DomainErrors.SanctionHitException;
import com.example.finance.account.infrastructure.persistence.jpa.AuditLogJpaRepository;
import com.example.finance.account.infrastructure.persistence.jpa.ComplianceReviewQueueJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests (Testcontainers MySQL + Redis + Kafka) for the
 * account-service fund-movement core. Drives the single application command
 * boundary and asserts persisted state.
 */
class AccountLifecycleIntegrationTest extends AbstractAccountIntegrationTest {

    @Autowired
    AccountApplicationService service;
    @Autowired
    AuditLogJpaRepository auditLogJpa;
    @Autowired
    ComplianceReviewQueueJpaRepository reviewQueueJpa;

    @Test
    @DisplayName("open → KYC upgrade → ACTIVE; audit_log rows written")
    void openKycActivate() {
        long auditBefore = auditLogJpa.count();
        AccountView v = openActiveFullKyc(service, "cust-open-1");
        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThat(v.kycLevel()).isEqualTo("FULL");
        // F6: OPEN_ACCOUNT + UPGRADE_KYC audit rows committed.
        assertThat(auditLogJpa.count()).isGreaterThanOrEqualTo(auditBefore + 2);
    }

    @Test
    @DisplayName("F2: hold → capture (partial) → remainder released; balances consistent")
    void holdCapturePartial() {
        AccountView acc = openActiveFullKyc(service, "cust-hcp-1");
        // Seed ledger via the v1 internal/stub funding source.
        service.topUp(HOLDER, acc.accountId(), 10_000L);

        var hold = service.placeHold(new PlaceHoldCommand(
                HOLDER, acc.accountId(), "4000", "KRW", 3600, "checkout"));
        var cap = service.captureHold(new CaptureHoldCommand(
                HOLDER, acc.accountId(), hold.hold().holdId(), "2500", "KRW"));

        assertThat(cap.captured().minorUnits()).isEqualTo(2500L);
        assertThat(cap.released().minorUnits()).isEqualTo(1500L);
        var balances = service.getBalances(acc.accountId(), HOLDER);
        // ledger 10000 − 2500 = 7500; held 0 → available 7500
        assertThat(balances.get(0).ledger()).isEqualTo("7500");
        assertThat(balances.get(0).held()).isEqualTo("0");
        assertThat(balances.get(0).available()).isEqualTo("7500");
    }

    @Test
    @DisplayName("hold → release returns funds to available")
    void holdRelease() {
        AccountView acc = openActiveFullKyc(service, "cust-hr-1");
        service.topUp(HOLDER, acc.accountId(), 5000L);

        var hold = service.placeHold(new PlaceHoldCommand(
                HOLDER, acc.accountId(), "3000", "KRW", 3600, "x"));
        service.releaseHold(new ReleaseHoldCommand(
                HOLDER, acc.accountId(), hold.hold().holdId()));

        var b = service.getBalances(acc.accountId(), HOLDER).get(0);
        assertThat(b.ledger()).isEqualTo("5000");
        assertThat(b.held()).isEqualTo("0");
        assertThat(b.available()).isEqualTo("5000");
    }

    @Test
    @DisplayName("F1: transfer is atomic — source debited + target credited exactly once")
    void transferAtomicity() {
        AccountView from = openActiveFullKyc(service, "cust-tf-from");
        AccountView to = openActiveFullKyc(service, "cust-tf-to");
        service.topUp(HOLDER, from.accountId(), 10_000L);

        var v = service.transfer(new TransferCommand(
                HOLDER, from.accountId(), to.accountId(), "4000", "KRW", "p2p"));
        assertThat(v.status()).isEqualTo("COMPLETED");

        assertThat(service.getBalances(from.accountId(), HOLDER).get(0).ledger())
                .isEqualTo("6000");
        assertThat(service.getBalances(to.accountId(), HOLDER).get(0).ledger())
                .isEqualTo("4000");
    }

    @Test
    @DisplayName("F4: sanction hit → SanctionHitException; compliance_review_queue row persisted; funds not moved")
    void sanctionHit() {
        // owner ref is registered as sanctioned via the IT-only property; the
        // gate rejects BEFORE any balance check so no funding is required.
        AccountView acc = openActiveFullKyc(service, "SANCTIONED-OWNER");
        long queueBefore = reviewQueueJpa.count();

        assertThatThrownBy(() -> service.placeHold(new PlaceHoldCommand(
                HOLDER, acc.accountId(), "1000", "KRW", 3600, "x")))
                .isInstanceOf(SanctionHitException.class);

        // F4: operator-queue row persisted in its own Tx even though the
        // fund-movement Tx rolled back.
        assertThat(reviewQueueJpa.count()).isEqualTo(queueBefore + 1);
        // F2/F4: balance unchanged (no hold placed; account never funded).
        var b = service.getBalances(acc.accountId(), HOLDER).get(0);
        assertThat(b.held()).isEqualTo("0");
        assertThat(b.available()).isEqualTo("0");
    }
}
