package com.example.settlement.domain.payout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SellerPayout} aggregate unit tests: PENDING creation, the net-zero guard
 * (decision 7), and the PENDING→PAID|FAILED state machine (the BE-416 execution
 * transitions, present but not wired by the close path).
 */
class SellerPayoutTest {

    private static SellerPayout pending() {
        return SellerPayout.pending("po-1", "p-1", "ecommerce", "seller-1",
                27_000L, 3_000L, 1);
    }

    @Test
    @DisplayName("pending creates a PENDING payout with the folded amounts")
    void pendingOk() {
        SellerPayout p = pending();
        assertThat(p.status()).isEqualTo(PayoutStatus.PENDING);
        assertThat(p.payableNetMinor()).isEqualTo(27_000L);
        assertThat(p.commissionMinor()).isEqualTo(3_000L);
        assertThat(p.accrualCount()).isEqualTo(1);
        assertThat(p.payoutReference()).isNull();
        assertThat(p.paidAt()).isNull();
    }

    @Test
    @DisplayName("payable_net_minor <= 0 → rejected (net-zero sellers skipped, decision 7)")
    void netZeroRejected() {
        assertThatThrownBy(() -> SellerPayout.pending("po-1", "p-1", "ecommerce", "seller-1",
                0L, 0L, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerPayout.pending("po-1", "p-1", "ecommerce", "seller-1",
                -100L, 0L, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markPaid transitions PENDING→PAID stamping reference + paidAt (BE-416 path)")
    void markPaidOk() {
        SellerPayout p = pending();
        Instant paidAt = Instant.parse("2026-07-01T10:00:00Z");
        p.markPaid("SIMULATED-ref-1", paidAt);
        assertThat(p.status()).isEqualTo(PayoutStatus.PAID);
        assertThat(p.payoutReference()).isEqualTo("SIMULATED-ref-1");
        assertThat(p.paidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("markFailed transitions PENDING→FAILED (BE-416 path)")
    void markFailedOk() {
        SellerPayout p = pending();
        p.markFailed();
        assertThat(p.status()).isEqualTo(PayoutStatus.FAILED);
    }

    // ── PENDING-only guard (AC-4) ──────────────────────────────────────────────

    @Test
    @DisplayName("markPaid on already-PAID throws IllegalStateException — no re-transition")
    void markPaid_alreadyPaid_throws() {
        SellerPayout p = pending();
        p.markPaid("ref-1", Instant.parse("2026-07-01T10:00:00Z"));

        assertThatThrownBy(() -> p.markPaid("ref-2", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markPaid on FAILED throws IllegalStateException — no reverse transition")
    void markPaid_onFailed_throws() {
        SellerPayout p = pending();
        p.markFailed();

        assertThatThrownBy(() -> p.markPaid("ref-1", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed on already-PAID throws IllegalStateException — no reverse transition")
    void markFailed_onPaid_throws() {
        SellerPayout p = pending();
        p.markPaid("ref-1", Instant.parse("2026-07-01T10:00:00Z"));

        assertThatThrownBy(() -> p.markFailed())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed on already-FAILED throws IllegalStateException — no double-fail")
    void markFailed_alreadyFailed_throws() {
        SellerPayout p = pending();
        p.markFailed();

        assertThatThrownBy(() -> p.markFailed())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markPaid with null reference throws NullPointerException")
    void markPaid_nullReference_throws() {
        SellerPayout p = pending();
        assertThatThrownBy(() -> p.markPaid(null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
