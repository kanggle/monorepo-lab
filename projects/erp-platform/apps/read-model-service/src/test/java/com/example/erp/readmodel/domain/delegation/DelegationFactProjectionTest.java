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
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1",
                "GLOBAL", null);

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.ACTIVE);
        assertThat(fact.delegatorId()).isEqualTo("emp-a");
        assertThat(fact.delegateId()).isEqualTo("emp-d");
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
        assertThat(fact.revokedAt()).isNull();
        assertThat(fact.isTerminal()).isFalse();
    }

    @Test
    void grantedCarriesScope_globalLeavesScopeRequestIdNull_requestSetsBoth() {
        DelegationFactProjection global = DelegationFactProjection.ofGranted(
                "dgr-g", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-g",
                "GLOBAL", null);
        assertThat(global.scope()).isEqualTo("GLOBAL");
        assertThat(global.scopeRequestId()).isNull();

        DelegationFactProjection request = DelegationFactProjection.ofGranted(
                "dgr-r", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-r",
                "REQUEST", "appr-1");
        assertThat(request.scope()).isEqualTo("REQUEST");
        assertThat(request.scopeRequestId()).isEqualTo("appr-1");
    }

    @Test
    void revokedFactoryLeavesScopeAbsent() {
        DelegationFactProjection fact = DelegationFactProjection.ofRevoked(
                "dgr-1", "emp-a", "emp-d", "back", T_REVOKE, T_REVOKE, "evt-2");

        assertThat(fact.scope()).isNull();
        assertThat(fact.scopeRequestId()).isNull();
    }

    @Test
    void grantThenRevokeReachesRevoked() {
        DelegationFactProjection fact = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1",
                "GLOBAL", null);

        fact.applyRevoke("emp-a", "emp-d", null, T_REVOKE, T_REVOKE, "evt-2");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(fact.revokedAt()).isEqualTo(T_REVOKE);
        // The validity window is preserved (revoke does not restate it).
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
        assertThat(fact.isTerminal()).isTrue();
    }

    @Test
    void applyRevokePreservesScope() {
        DelegationFactProjection fact = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, "vacation", T_GRANT, "evt-1",
                "REQUEST", "appr-1");

        fact.applyRevoke("emp-a", "emp-d", "back", T_REVOKE, T_REVOKE, "evt-2");

        // scope is grant-time immutable — the revoke does not restate or clear it.
        assertThat(fact.scope()).isEqualTo("REQUEST");
        assertThat(fact.scopeRequestId()).isEqualTo("appr-1");
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
        fact.applyGrant("emp-a", "emp-d", FROM, TO, "vacation", T_LATE, "evt-3",
                "REQUEST", "appr-1");

        assertThat(fact.status()).isEqualTo(DelegationFactStatus.REVOKED);
        assertThat(fact.isTerminal()).isTrue();
        // The late grant DID fill in the previously-absent window (no fabrication —
        // it is the authoritative window from the producer), but status stays REVOKED.
        assertThat(fact.validFrom()).isEqualTo(FROM);
        assertThat(fact.validTo()).isEqualTo(TO);
        // AC-3: scope is filled UNCONDITIONALLY (outside the sticky-terminal guard) —
        // the out-of-order revoke created a scope-NULL row, the later grant fills it
        // WITHOUT reverting the REVOKED status.
        assertThat(fact.scope()).isEqualTo("REQUEST");
        assertThat(fact.scopeRequestId()).isEqualTo("appr-1");
    }

    @Test
    void isActiveAt_windowAndStatus() {
        DelegationFactProjection active = DelegationFactProjection.ofGranted(
                "dgr-1", "emp-a", "emp-d", FROM, TO, null, T_GRANT, "evt-1", "GLOBAL", null);

        assertThat(active.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
        assertThat(active.isActiveAt(Instant.parse("2026-05-01T00:00:00Z"))).isFalse();
        assertThat(active.isActiveAt(Instant.parse("2026-07-01T00:00:00Z"))).isFalse();

        // Open-ended grant (validTo absent) → active forever after validFrom.
        DelegationFactProjection openEnded = DelegationFactProjection.ofGranted(
                "dgr-2", "emp-a", "emp-d", FROM, null, null, T_GRANT, "evt-1", "GLOBAL", null);
        assertThat(openEnded.isActiveAt(Instant.parse("2030-01-01T00:00:00Z"))).isTrue();

        // Revoked grant is never active.
        DelegationFactProjection revoked = DelegationFactProjection.ofRevoked(
                "dgr-3", "emp-a", "emp-d", null, T_REVOKE, T_REVOKE, "evt-2");
        assertThat(revoked.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isFalse();
    }
}
