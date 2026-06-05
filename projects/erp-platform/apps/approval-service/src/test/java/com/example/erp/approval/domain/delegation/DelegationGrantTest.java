package com.example.erp.approval.domain.delegation;

import com.example.erp.approval.domain.error.ApprovalErrors.DelegationInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests for the {@link DelegationGrant} aggregate (TASK-ERP-BE-013):
 * create validity (self-delegation / invalid window), revoke (ACTIVE→REVOKED +
 * idempotent re-revoke), and the {@code isActiveAt} status/window matrix.
 */
class DelegationGrantTest {

    private static final String TENANT = "erp";
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");

    private DelegationGrant grant(Instant from, Instant to) {
        return DelegationGrant.create("dgr-1", TENANT, "emp-a", "emp-d", from, to,
                "vacation", "emp-a", from);
    }

    @Test
    @DisplayName("create A→D → ACTIVE, fields set")
    void createActive() {
        DelegationGrant g = grant(FROM, TO);
        assertThat(g.getStatus()).isEqualTo(DelegationStatus.ACTIVE);
        assertThat(g.getDelegatorId()).isEqualTo("emp-a");
        assertThat(g.getDelegateId()).isEqualTo("emp-d");
        assertThat(g.getValidTo()).isEqualTo(TO);
        assertThat(g.getCreatedBy()).isEqualTo("emp-a");
        assertThat(g.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("create open-ended (validTo null) → ACTIVE")
    void createOpenEnded() {
        DelegationGrant g = DelegationGrant.create("dgr-2", TENANT, "emp-a", "emp-d",
                FROM, null, null, "emp-a", FROM);
        assertThat(g.getValidTo()).isNull();
        assertThat(g.getStatus()).isEqualTo(DelegationStatus.ACTIVE);
    }

    @Test
    @DisplayName("self-delegation (A == D) → DELEGATION_INVALID")
    void selfDelegation() {
        assertThatThrownBy(() -> DelegationGrant.create("dgr-3", TENANT, "emp-a", "emp-a",
                FROM, TO, null, "emp-a", FROM))
                .isInstanceOf(DelegationInvalidException.class);
    }

    @Test
    @DisplayName("invalid window (validTo < validFrom) → DELEGATION_INVALID")
    void invalidWindow() {
        assertThatThrownBy(() -> DelegationGrant.create("dgr-4", TENANT, "emp-a", "emp-d",
                TO, FROM, null, "emp-a", FROM))
                .isInstanceOf(DelegationInvalidException.class);
    }

    @Test
    @DisplayName("revoke: ACTIVE → REVOKED (true); idempotent re-revoke (false), keeps first revoker")
    void revokeIdempotent() {
        DelegationGrant g = grant(FROM, TO);
        Instant r1 = Instant.parse("2026-06-10T00:00:00Z");
        assertThat(g.revoke("emp-admin", r1)).isTrue();
        assertThat(g.getStatus()).isEqualTo(DelegationStatus.REVOKED);
        assertThat(g.getRevokedBy()).isEqualTo("emp-admin");
        assertThat(g.getRevokedAt()).isEqualTo(r1);

        Instant r2 = Instant.parse("2026-06-11T00:00:00Z");
        assertThat(g.revoke("emp-other", r2)).isFalse();   // no-op
        assertThat(g.getRevokedBy()).isEqualTo("emp-admin");   // unchanged
        assertThat(g.getRevokedAt()).isEqualTo(r1);
    }

    @Test
    @DisplayName("isActiveAt: within window → true; before/after → false")
    void isActiveAtWindow() {
        DelegationGrant g = grant(FROM, TO);
        assertThat(g.isActiveAt(FROM)).isTrue();                                  // inclusive start
        assertThat(g.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isTrue();
        assertThat(g.isActiveAt(TO)).isTrue();                                    // inclusive end
        assertThat(g.isActiveAt(Instant.parse("2026-05-31T23:59:59Z"))).isFalse(); // before
        assertThat(g.isActiveAt(Instant.parse("2026-07-01T00:00:01Z"))).isFalse(); // after
    }

    @Test
    @DisplayName("isActiveAt: open-ended grant is active for any now ≥ validFrom")
    void isActiveAtOpenEnded() {
        DelegationGrant g = DelegationGrant.create("dgr-5", TENANT, "emp-a", "emp-d",
                FROM, null, null, "emp-a", FROM);
        assertThat(g.isActiveAt(Instant.parse("2030-01-01T00:00:00Z"))).isTrue();
        assertThat(g.isActiveAt(Instant.parse("2026-05-01T00:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("isActiveAt: REVOKED grant is never active (even inside its window)")
    void isActiveAtRevoked() {
        DelegationGrant g = grant(FROM, TO);
        g.revoke("emp-a", Instant.parse("2026-06-05T00:00:00Z"));
        assertThat(g.isActiveAt(Instant.parse("2026-06-15T00:00:00Z"))).isFalse();
    }
}
