package com.example.admin.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    AdminActionAuditor auditor;

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
}
