package com.example.erp.readmodel.domain.delegation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link DelegationFactProjection} sticky-terminal + out-of-order
 * transition rules (the correctness core of TASK-ERP-BE-015). Pure domain — no
 * Spring / Mockito.
 */
class DelegationFactProjectionTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");
    private static final Instant T_GRANT = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant T_REVOKE = Instant.parse("2026-06-10T00:00:00Z");
    private static final Instant T_LATE = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void grantedReachesActiveWithWindow() {
        DelegationFactProjection fact = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(fact.delegatorId()).isEqualTo("emp-a");
        assertThat(fact.delegateId()).isEqualTo("emp-d");
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
        assertThat(fact.revokedAt()).isNull();
        assertThat(fact.isTerminal()).isFalse();
    }

    @Test
    void grantThenRevokeReachesRevoked() {
        DelegationFactProjection fact = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1");

        fact.applyRevoke("emp-a", "emp-d", null, T_REVOKE, T_REVOKE, "evt-2");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(fact.revokedAt()).isEqualTo(T_REVOKE);
        // The validity window is preserved (revoke does not restate it).
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
        assertThat(fact.isTerminal()).isTrue();
    }

    @Test
    void outOfOrder_revokeBeforeGrantLeavesWindowAbsent() {
        // Revoke arrives first (replay-from-middle): row created, window ABSENT.
        DelegationFactProjection fact = DelegationFactProjection.ofRevoked(
                "dgr-1", "emp-a", "emp-d", "no longer away", T_REVOKE, T_REVOKE, "evt-2");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(fact.validFrom()).isNull();
        assertThat(fact.validTo()).isNull();
        assertThat(fact.revokedAt()).isEqualTo(T_REVOKE);
        assertThat(fact.reason()).isEqualTo("no longer away");
    }

    @Test
    void stickyTerminal_lateGrantAfterRevokeStaysRevoked() {
        DelegationFactProjection fact = DelegationFactProjection.ofRevoked(
                "dgr-1", "emp-a", "emp-d", "back", T_REVOKE, T_REVOKE, "evt-2");

        // A late / out-of-contract delegated must NOT revert REVOKED → ACTIVE.
        fact.applyGrant("emp-a", "emp-d", FROM, TO, "vacation", T_LATE, "evt-3");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(fact.isTerminal()).isTrue();
        // The late grant DID fill in the previously-absent window (no fabrication —
        // it is the authoritative window from the producer), but status stays REVOKED.
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
    }

    @Test
    void isActiveAt_windowAndStatus() {
        DelegationFactProjection active = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, null, T_GRANT, "evt-1");

        assertThat(active.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
        assertThat(active.isActiveAt(Instant.parse("2026-05-01T00:00:00Z"))).isFalse();
        assertThat(active.isActiveAt(Instant.parse("2026-07-01T00:00:00Z"))).isFalse();

        // Open-ended grant (validTo absent) → active forever after validFrom.
        DelegationFactProjection openEnded = DelegationFactProjection.ofGranted(
                "dgr-2", "emp-a", "emp-d", FROM, null, null, T_GRANT, "evt-1");
        assertThat(openEnded.isActiveAt(Instant.parse("2030-01-01T00:00:00Z"))).isTrue();

        // Revoked grant is never active.
        DelegationFactProjection revoked = DelegationFactProjection.ofRevoked(
                "dgr-3", "emp-a", "emp-d", null, T_REVOKE, T_REVOKE, "evt-2");
        assertThat(revoked.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isFalse();
    }
}
