package com.example.admin.domain.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.admin.domain.rbac.PartnershipStatus.ACTIVE;
import static com.example.admin.domain.rbac.PartnershipStatus.PENDING;
import static com.example.admin.domain.rbac.PartnershipStatus.SUSPENDED;
import static com.example.admin.domain.rbac.PartnershipStatus.TERMINATED;
import static com.example.admin.domain.rbac.PartnershipStatus.Transition.APPLIED;
import static com.example.admin.domain.rbac.PartnershipStatus.Transition.INVALID;
import static com.example.admin.domain.rbac.PartnershipStatus.Transition.NO_OP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-477 / ADR-MONO-045 D1 — unit tests for the {@link PartnershipStatus} state
 * machine against the admin-api.md transition matrix.
 */
class PartnershipStatusTest {

    @Test
    @DisplayName("accept: only PENDING → ACTIVE")
    void accept() {
        assertThat(PENDING.accept()).isEqualTo(APPLIED);
        assertThat(ACTIVE.accept()).isEqualTo(INVALID);
        assertThat(SUSPENDED.accept()).isEqualTo(INVALID);
        assertThat(TERMINATED.accept()).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("suspend: ACTIVE→APPLIED, SUSPENDED→NO_OP, else INVALID")
    void suspend() {
        assertThat(ACTIVE.suspend()).isEqualTo(APPLIED);
        assertThat(SUSPENDED.suspend()).isEqualTo(NO_OP);
        assertThat(PENDING.suspend()).isEqualTo(INVALID);
        assertThat(TERMINATED.suspend()).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("reactivate: only SUSPENDED → ACTIVE")
    void reactivate() {
        assertThat(SUSPENDED.reactivate()).isEqualTo(APPLIED);
        assertThat(ACTIVE.reactivate()).isEqualTo(INVALID);
        assertThat(PENDING.reactivate()).isEqualTo(INVALID);
        assertThat(TERMINATED.reactivate()).isEqualTo(INVALID);
    }

    @Test
    @DisplayName("terminate: PENDING/ACTIVE/SUSPENDED→APPLIED, TERMINATED→NO_OP (idempotent)")
    void terminate() {
        assertThat(PENDING.terminate()).isEqualTo(APPLIED);
        assertThat(ACTIVE.terminate()).isEqualTo(APPLIED);
        assertThat(SUSPENDED.terminate()).isEqualTo(APPLIED);
        assertThat(TERMINATED.terminate()).isEqualTo(NO_OP);
    }

    @Test
    @DisplayName("derivesReach: only ACTIVE derives cross-org reach")
    void derivesReach() {
        assertThat(ACTIVE.derivesReach()).isTrue();
        assertThat(PENDING.derivesReach()).isFalse();
        assertThat(SUSPENDED.derivesReach()).isFalse();
        assertThat(TERMINATED.derivesReach()).isFalse();
    }
}
