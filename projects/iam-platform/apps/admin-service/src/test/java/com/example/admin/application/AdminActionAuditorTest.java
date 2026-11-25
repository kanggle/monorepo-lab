package com.example.admin.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Facade-level test for {@link AdminActionAuditor} after the TASK-BE-314 split.
 * The auditor is now a thin delegator over {@link AdminActionAuditWriter};
 * full write-path behavior (DB persistence, outbox publish, meta-audit
 * fail-closed) is covered by {@link AdminActionAuditWriterTest}.
 *
 * <p>These cases assert ONLY that the facade forwards each public API call to
 * the writer 1:1 with the same arguments — preserving the Spring AOP
 * cross-bean call boundary that activates {@code @Transactional(REQUIRES_NEW)}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AdminActionAuditorTest {

    @Mock
    AdminActionAuditWriter writer;

    @Mock
    AdminActionDenyWriter denyWriter;

    SimpleMeterRegistry meterRegistry;

    AdminActionAuditor auditor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditor = new AdminActionAuditor(writer, denyWriter, meterRegistry);
    }

    private OperatorContext op() {
        return new OperatorContext("op-1", "jti-1");
    }

    @Test
    void newAuditId_returns_unique_uuid_v4_strings() {
        String a = auditor.newAuditId();
        String b = auditor.newAuditId();
        assertThat(a).isNotBlank().isNotEqualTo(b);
        verifyNoInteractions(writer, denyWriter);
    }

    @Test
    @SuppressWarnings("deprecation") // intentional: verifies the @Deprecated backcompat shim
    void reserveAuditId_delegates_to_newAuditId_for_backcompat() {
        String reserved = auditor.reserveAuditId();
        assertThat(reserved).isNotBlank();
        verifyNoInteractions(writer, denyWriter);
    }

    @Test
    void recordStart_forwards_to_writer_unchanged() {
        AdminActionAuditor.StartRecord start = new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Instant.now());

        auditor.recordStart(start);

        verify(writer).recordStart(start);
    }

    @Test
    void recordCompletion_forwards_to_writer_unchanged() {
        AdminActionAuditor.CompletionRecord done = new AdminActionAuditor.CompletionRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp",
                Outcome.SUCCESS, null, Instant.now(), Instant.now());

        auditor.recordCompletion(done);

        verify(writer).recordCompletion(done);
    }

    @Test
    void record_delegates_to_writer_with_null_permission_override() {
        AdminActionAuditor.AuditRecord rec = new AdminActionAuditor.AuditRecord(
                "audit-1", ActionCode.AUDIT_QUERY, op(),
                "AUDIT_QUERY", "-", "<meta>", null, "idemp",
                Outcome.SUCCESS, null, Instant.now(), Instant.now());

        auditor.record(rec);

        verify(writer).recordWithPermission(rec, null);
    }

    @Test
    void recordWithPermission_forwards_explicit_override_to_writer() {
        AdminActionAuditor.AuditRecord rec = new AdminActionAuditor.AuditRecord(
                "audit-2", ActionCode.OPERATOR_PROFILE_UPDATE, op(),
                "OPERATOR", "op-9", "fraud", null, "idemp",
                Outcome.SUCCESS, null, Instant.now(), Instant.now());

        auditor.recordWithPermission(rec, "operator.manage");

        verify(writer).recordWithPermission(rec, "operator.manage");
    }

    @Test
    void recordDenied_forwards_every_arg_to_deny_writer_unchanged() {
        auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1");

        verify(denyWriter).recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1");
    }

    @Test
    void recordCrossTenantDenied_forwards_every_arg_to_deny_writer_unchanged() {
        OperatorContext actor = op();

        auditor.recordCrossTenantDenied(actor, "fan-platform",
                ActionCode.AUDIT_QUERY, "audit.read", "other-tenant");

        verify(denyWriter).recordCrossTenantDenied(actor, "fan-platform",
                ActionCode.AUDIT_QUERY, "audit.read", "other-tenant");
    }

    @Test
    void recordLogin_forwards_login_record_to_writer_unchanged() {
        AdminActionAuditor.LoginAuditRecord rec = new AdminActionAuditor.LoginAuditRecord(
                "audit-login", op(),
                "OPERATOR", "op-1",
                AdminActionAuditor.REASON_SELF_LOGIN,
                "login:audit-login",
                Outcome.SUCCESS, null, true,
                Instant.now(), Instant.now());

        auditor.recordLogin(rec);

        verify(writer).recordLogin(rec);
    }

    // ── TASK-BE-662: admin_command_total{action_code,outcome} ─────────────────

    private AdminActionAuditor.CompletionRecord completion(ActionCode code, Outcome outcome) {
        return new AdminActionAuditor.CompletionRecord(
                "audit-1", code, op(), "ACCOUNT", "acc-1", "fraud", null, "idemp",
                outcome, null, Instant.now(), Instant.now());
    }

    private AdminActionAuditor.AuditRecord auditRecord(ActionCode code, Outcome outcome) {
        return new AdminActionAuditor.AuditRecord(
                "audit-2", code, op(), "AUDIT_QUERY", "-", "<meta>", null, "idemp",
                outcome, null, Instant.now(), Instant.now());
    }

    /** Exactly one admin_command_total series exists, with these tags and count 1. */
    private void assertOnlyCommand(String actionCode, String outcome) {
        var counters = meterRegistry.find(AdminActionAuditor.COMMAND_METRIC_NAME).counters();
        assertThat(counters).hasSize(1);
        Counter only = counters.iterator().next();
        assertThat(only.getId().getTag("action_code")).isEqualTo(actionCode);
        assertThat(only.getId().getTag("outcome")).isEqualTo(outcome);
        assertThat(only.count()).isEqualTo(1.0);
    }

    private void assertNoCommandCounted() {
        assertThat(meterRegistry.find(AdminActionAuditor.COMMAND_METRIC_NAME).counters()).isEmpty();
    }

    @Test
    void metric_recordCompletion_success_counts_outcome_success() {
        auditor.recordCompletion(completion(ActionCode.ACCOUNT_LOCK, Outcome.SUCCESS));

        assertOnlyCommand("ACCOUNT_LOCK", "success");
    }

    @Test
    void metric_recordCompletion_failure_counts_outcome_failure() {
        auditor.recordCompletion(completion(ActionCode.ACCOUNT_UNLOCK, Outcome.FAILURE));

        assertOnlyCommand("ACCOUNT_UNLOCK", "failure");
    }

    @Test
    void metric_recordCompletion_write_throws_is_not_counted() {
        AdminActionAuditor.CompletionRecord done = completion(ActionCode.ACCOUNT_LOCK, Outcome.SUCCESS);
        doThrow(new IllegalStateException("db down")).when(writer).recordCompletion(done);

        assertThatThrownBy(() -> auditor.recordCompletion(done)).isInstanceOf(IllegalStateException.class);

        assertNoCommandCounted();
    }

    @Test
    void metric_record_success_counts_once() {
        auditor.record(auditRecord(ActionCode.AUDIT_QUERY, Outcome.SUCCESS));

        assertOnlyCommand("AUDIT_QUERY", "success");
    }

    @Test
    void metric_recordWithPermission_failure_counts_once() {
        auditor.recordWithPermission(auditRecord(ActionCode.OPERATOR_PROFILE_UPDATE, Outcome.FAILURE),
                "operator.manage");

        assertOnlyCommand("OPERATOR_PROFILE_UPDATE", "failure");
    }

    @Test
    void metric_record_write_throws_is_not_counted() {
        AdminActionAuditor.AuditRecord rec = auditRecord(ActionCode.AUDIT_QUERY, Outcome.SUCCESS);
        doThrow(new IllegalStateException("db down")).when(writer).recordWithPermission(rec, null);

        assertThatThrownBy(() -> auditor.record(rec)).isInstanceOf(IllegalStateException.class);

        assertNoCommandCounted();
    }

    @Test
    void metric_in_progress_outcome_is_not_counted() {
        auditor.record(auditRecord(ActionCode.AUDIT_QUERY, Outcome.IN_PROGRESS));

        assertNoCommandCounted();
    }

    @Test
    void metric_null_action_code_is_tagged_UNKNOWN() {
        auditor.recordCompletion(completion(null, Outcome.SUCCESS));

        assertOnlyCommand("UNKNOWN", "success");
    }

    @Test
    void metric_recordDenied_counts_outcome_denied() {
        auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock",
                "/api/admin/accounts/acc-1/lock", "POST", "acc-1");

        assertOnlyCommand("ACCOUNT_LOCK", "denied");
    }

    @Test
    void metric_recordDenied_null_action_code_is_tagged_UNKNOWN() {
        auditor.recordDenied(null, "account.read", "/api/admin/accounts", "GET", null);

        assertOnlyCommand("UNKNOWN", "denied");
    }

    @Test
    void metric_recordDenied_fail_closed_write_throws_is_not_counted() {
        doThrow(new IllegalStateException("audit failure")).when(denyWriter)
                .recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock", "/x", "POST", null);

        assertThatThrownBy(() -> auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock", "/x", "POST", null))
                .isInstanceOf(IllegalStateException.class);

        assertNoCommandCounted();
    }

    @Test
    void metric_recordCrossTenantDenied_counts_outcome_denied() {
        auditor.recordCrossTenantDenied(op(), "fan-platform", ActionCode.AUDIT_QUERY, "audit.read", "other");

        assertOnlyCommand("AUDIT_QUERY", "denied");
    }

    @Test
    void metric_recordOrgNodeScopeDenied_counts_outcome_denied() {
        auditor.recordOrgNodeScopeDenied(op(), ActionCode.ACCOUNT_LOCK, "node-1");

        assertOnlyCommand("ACCOUNT_LOCK", "denied");
    }

    @Test
    void metric_recordTenantPlacementDenied_counts_outcome_denied() {
        auditor.recordTenantPlacementDenied(op(), ActionCode.ACCOUNT_LOCK, "wms", "SOURCE", "n1", "n2");

        assertOnlyCommand("ACCOUNT_LOCK", "denied");
    }

    @Test
    void metric_recordRoleGrantForbidden_counts_outcome_denied() {
        auditor.recordRoleGrantForbidden(op(), ActionCode.OPERATOR_ROLE_CHANGE, "SUPER_ADMIN");

        assertOnlyCommand("OPERATOR_ROLE_CHANGE", "denied");
    }

    @Test
    void metric_best_effort_deny_counts_denied_even_when_deny_writer_throws() {
        // The best-effort writer swallows its own failures; this pins the facade side: if anything
        // still escapes (e.g. the REQUIRES_NEW commit), the refusal itself is still counted.
        OperatorContext actor = op();
        doThrow(new IllegalStateException("commit failed")).when(denyWriter)
                .recordRoleGrantForbidden(actor, ActionCode.OPERATOR_ROLE_CHANGE, "SUPER_ADMIN");

        assertThatThrownBy(() -> auditor.recordRoleGrantForbidden(actor, ActionCode.OPERATOR_ROLE_CHANGE, "SUPER_ADMIN"))
                .isInstanceOf(IllegalStateException.class);

        assertOnlyCommand("OPERATOR_ROLE_CHANGE", "denied");
    }

    @Test
    void metric_recordStart_is_not_counted() {
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp", Instant.now()));

        assertNoCommandCounted();
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void metric_recordLogin_is_never_counted(Outcome outcome) {
        auditor.recordLogin(new AdminActionAuditor.LoginAuditRecord(
                "audit-login", op(), "OPERATOR", "op-1", AdminActionAuditor.REASON_SELF_LOGIN,
                "login:audit-login", outcome, null, false, Instant.now(), Instant.now()));

        assertNoCommandCounted();
    }

    // ── TASK-BE-668 — admin_audit_write_total{result}: one per audit-ledger write ─────────

    private double auditWrite(String result) {
        return meterRegistry.get("admin_audit_write_total").tag("result", result).counter().count();
    }

    private void assertAuditWrites(double success, double failure) {
        assertThat(auditWrite("success")).as("admin_audit_write_total{result=success}").isEqualTo(success);
        assertThat(auditWrite("failure")).as("admin_audit_write_total{result=failure}").isEqualTo(failure);
    }

    @Test
    void auditWrite_both_series_registered_at_zero_from_construction() {
        assertThat(meterRegistry.find("admin_audit_write_total").counters())
                .extracting(c -> c.getId().getTag("result"))
                .containsExactlyInAnyOrder("success", "failure");
        assertAuditWrites(0, 0);
    }

    @Test
    void auditWrite_every_fail_closed_write_that_returns_counts_success() {
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp", Instant.now()));
        auditor.recordCompletion(completion(ActionCode.ACCOUNT_LOCK, Outcome.SUCCESS));
        auditor.record(auditRecord(ActionCode.AUDIT_QUERY, Outcome.SUCCESS));
        auditor.recordWithPermission(auditRecord(ActionCode.AUDIT_QUERY, Outcome.SUCCESS), "audit.read");
        auditor.recordDenied(ActionCode.ACCOUNT_LOCK, "account.lock", "/x", "POST", null);
        auditor.recordLogin(new AdminActionAuditor.LoginAuditRecord(
                "audit-login", op(), "OPERATOR", "op-1", AdminActionAuditor.REASON_SELF_LOGIN,
                "login:audit-login", Outcome.SUCCESS, null, false, Instant.now(), Instant.now()));

        assertAuditWrites(6, 0);
    }

    @Test
    void auditWrite_fail_closed_write_that_throws_counts_failure_and_rethrows() {
        AdminActionAuditor.CompletionRecord done = completion(ActionCode.ACCOUNT_LOCK, Outcome.SUCCESS);
        doThrow(new IllegalStateException("db down")).when(writer).recordCompletion(done);

        assertThatThrownBy(() -> auditor.recordCompletion(done)).isInstanceOf(IllegalStateException.class);

        assertAuditWrites(0, 1);
    }

    @Test
    void auditWrite_recordStart_failure_counts_failure() {
        AdminActionAuditor.StartRecord start = new AdminActionAuditor.StartRecord(
                "audit-1", ActionCode.ACCOUNT_LOCK, op(),
                "ACCOUNT", "acc-1", "fraud", null, "idemp", Instant.now());
        doThrow(new IllegalStateException("db down")).when(writer).recordStart(start);

        assertThatThrownBy(() -> auditor.recordStart(start)).isInstanceOf(IllegalStateException.class);

        assertAuditWrites(0, 1);
    }

    @Test
    void auditWrite_best_effort_deny_written_counts_success() {
        OperatorContext actor = op();
        when(denyWriter.recordCrossTenantDenied(actor, "fan-platform", ActionCode.AUDIT_QUERY, "audit.read", "other"))
                .thenReturn(true);

        auditor.recordCrossTenantDenied(actor, "fan-platform", ActionCode.AUDIT_QUERY, "audit.read", "other");

        assertAuditWrites(1, 0);
    }

    @Test
    void auditWrite_best_effort_deny_swallowed_failure_counts_failure() {
        OperatorContext actor = op();
        when(denyWriter.recordOrgNodeScopeDenied(actor, ActionCode.ACCOUNT_LOCK, "node-1")).thenReturn(false);

        auditor.recordOrgNodeScopeDenied(actor, ActionCode.ACCOUNT_LOCK, "node-1");

        assertAuditWrites(0, 1);
        assertOnlyCommand("ACCOUNT_LOCK", "denied");  // the refusal itself still stands
    }

    @Test
    void auditWrite_best_effort_deny_escaping_failure_counts_failure_once() {
        OperatorContext actor = op();
        doThrow(new IllegalStateException("commit failed")).when(denyWriter)
                .recordTenantPlacementDenied(actor, ActionCode.ACCOUNT_LOCK, "wms", "SOURCE", "n1", "n2");

        assertThatThrownBy(() -> auditor.recordTenantPlacementDenied(
                actor, ActionCode.ACCOUNT_LOCK, "wms", "SOURCE", "n1", "n2"))
                .isInstanceOf(IllegalStateException.class);

        assertAuditWrites(0, 1);
    }

    @Test
    void auditWrite_role_grant_forbidden_written_counts_success() {
        OperatorContext actor = op();
        when(denyWriter.recordRoleGrantForbidden(actor, ActionCode.OPERATOR_ROLE_CHANGE, "SUPER_ADMIN"))
                .thenReturn(true);

        auditor.recordRoleGrantForbidden(actor, ActionCode.OPERATOR_ROLE_CHANGE, "SUPER_ADMIN");

        assertAuditWrites(1, 0);
    }

    @Test
    void auditWrite_prometheus_scrape_carries_the_spec_name() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AdminActionAuditor scraped = new AdminActionAuditor(writer, denyWriter, prometheus);
        scraped.record(auditRecord(ActionCode.AUDIT_QUERY, Outcome.SUCCESS));

        String scrape = prometheus.scrape();

        assertThat(scrape).as(scrape).contains("admin_audit_write_total{result=\"success\"} 1.0");
        assertThat(scrape).contains("admin_audit_write_total{result=\"failure\"} 0.0");
    }
}
