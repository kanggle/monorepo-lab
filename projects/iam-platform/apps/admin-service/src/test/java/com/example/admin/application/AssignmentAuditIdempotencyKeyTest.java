package com.example.admin.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-631 — the assignment audit row's idempotency key must fit {@code admin_actions.idempotency_key
 * VARCHAR(100)} for EVERY legal tenant id ({@code tenants.tenant_id VARCHAR(32)}). The old form overflowed past an
 * 11-character tenant id, and the fail-closed audit turned «remove from this company» into {@code 500
 * AUDIT_FAILURE} (found by {@code GroupDomainPermissionSetIntegrationTest} AC-4 in CI).
 */
class AssignmentAuditIdempotencyKeyTest {

    private static final int COLUMN = 100;
    private static final String UUID_36 = "0192f3a0-0000-7000-8000-000000000001";
    private static final String TENANT_32 = "t".repeat(32);
    private static final Instant FAR_FUTURE = Instant.parse("2286-11-20T17:46:39.999Z"); // 13-digit epoch millis

    @Test
    @DisplayName("the longest legal key (36-char operator UUID · 32-char tenant id · 13-digit millis) fits the 100-char column")
    void longestKeyFitsTheColumn() {
        String key = ManageOperatorAssignmentUseCase.auditIdempotencyKey(UUID_36, TENANT_32, FAR_FUTURE);
        assertThat(String.valueOf(FAR_FUTURE.toEpochMilli())).hasSize(13);
        assertThat(key).hasSizeLessThanOrEqualTo(COLUMN);
    }

    @Test
    @DisplayName("control: the pre-fix form overflows the column for the same inputs — the reason for the change")
    void oldFormOverflowed() {
        String old = "assignment:OPERATOR_ASSIGNMENT_DELETE:" + UUID_36 + ":" + TENANT_32 + ":" + FAR_FUTURE.toEpochMilli();
        assertThat(old.length()).isGreaterThan(COLUMN);
    }

    @Test
    @DisplayName("the key still separates operator · tenant · instant (the unique index adds actor and action code)")
    void keyCarriesOperatorTenantAndInstant() {
        Instant at = Instant.parse("2026-10-14T00:00:00Z");
        assertThat(ManageOperatorAssignmentUseCase.auditIdempotencyKey(UUID_36, "acme-corp", at))
                .isEqualTo("asg:" + UUID_36 + ":acme-corp:" + at.toEpochMilli())
                .isNotEqualTo(ManageOperatorAssignmentUseCase.auditIdempotencyKey(UUID_36, "globex", at));
    }
}
