package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.OperatorInvitationUseCase;
import com.example.admin.application.OperatorInvitationWriter;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TASK-BE-250: Creates a new tenant via admin-service — TASK-MONO-773 S4: «tenant + first admin» as ONE bundle
 * (admin-api.md § POST /api/admin/tenants «처리 순서 (773 · 구현 S4)» · ADR-MONO-044 D3 · ADR-MONO-080 D9 = T1).
 *
 * <ol>
 *   <li>validate — slug · reserved word · displayName · tenantType · 🔴 {@code firstAdmin} (email / display name,
 *       after trim — the 772 issuance's rules). Nothing is created on a refusal;</li>
 *   <li>{@code TENANT_CREATE} IN_PROGRESS audit row (fail-closed, A10 — unchanged);</li>
 *   <li>account-service {@code POST /internal/tenants} with {@code initialStatus = PENDING} — the tenant is born
 *       «관리자 대기», never {@code ACTIVE} (a failure finalises the audit row FAILURE and creates nothing);</li>
 *   <li>ONE admin transaction ({@link Bundle}): the first-admin invitation row + its {@code OPERATOR_INVITATION_CREATE}
 *       audit row + outbox {@code tenant.created(status = PENDING)};</li>
 *   <li>🔴 compensation: if 4 fails, the PENDING tenant is moved {@code PENDING → SUSPENDED} at account-service and
 *       the error is returned — whatever happens, no {@code ACTIVE} tenant exists (a failed compensation leaves an
 *       invitation-less PENDING tenant, summary {@code NONE});</li>
 *   <li>{@code TENANT_CREATE} SUCCESS, then the invitation mail after commit (a failed mail is {@code 201} +
 *       {@code delivery.status = FAILED_*}).</li>
 * </ol>
 *
 * <p>Not {@code @Transactional} as a whole: step 4 must commit before the mail, and a rolled-back step 4 must be
 * compensable. Idempotency-Key: no IdempotencyStore for this path (accepted limitation since TASK-BE-250) — a
 * repeated slug answers account-service's {@code 409 TENANT_ALREADY_EXISTS} and writes no second invitation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTenantUseCase {

    /** Regex from contract: ^[a-z][a-z0-9-]{1,31}$ */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    /** Reserved words per specs/contracts/http/admin-api.md §Tenant ID 규칙. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "internal", "system", "null", "default",
            "public", "gap", "iam", "auth", "oauth", "me"
    );

    static final String INITIAL_STATUS = "PENDING";

    private final TenantProvisioningPort provisioningPort;
    private final AdminActionAuditor auditor;
    private final Bundle bundle;
    private final OperatorInvitationUseCase invitationUseCase;

    public CreateTenantResult execute(CreateTenantCommand cmd) {
        String tenantId = cmd.tenantId();
        String displayName = cmd.displayName();
        String tenantType = cmd.tenantType();
        OperatorContext operator = cmd.operator();
        String reason = cmd.reason();
        String idempotencyKey = cmd.idempotencyKey();

        // 1. Validate slug
        if (tenantId == null || !SLUG_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException(
                    "tenantId must match ^[a-z][a-z0-9-]{1,31}$: " + tenantId);
        }
        // 2. Validate reserved
        if (RESERVED.contains(tenantId)) {
            throw new TenantIdReservedException(tenantId);
        }
        // 3. Validate displayName
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new IllegalArgumentException("displayName must be 1–100 characters");
        }
        // 4. Validate tenantType enum (throws IllegalArgumentException on bad value)
        validateTenantType(tenantType);
        // 5. TASK-MONO-773 S4 — the first admin (required, B2B and B2C alike): the 772 issuance's rules, after trim.
        String firstAdminEmail = OperatorInvitationUseCase.normalizeEmail(cmd.firstAdminEmail());
        String firstAdminName;
        try {
            firstAdminName = OperatorInvitationUseCase.requireDisplayName(cmd.firstAdminDisplayName());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("firstAdmin.displayName is required (1–64 characters)");
        }

        Instant now = Instant.now();
        String auditId = auditor.newAuditId();
        String auditReason = reason != null ? reason : "<not_provided>";

        // 6. Write IN_PROGRESS audit row (fail-closed per A10)
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.TENANT_CREATE, operator,
                "TENANT", tenantId,
                auditReason,
                null, idempotencyKey, now,
                tenantId /* targetTenantId = the new tenant */));

        // 7. account-service — born PENDING («관리자 대기»), never ACTIVE.
        TenantSummary created;
        try {
            created = provisioningPort.create(tenantId, displayName.trim(), tenantType, INITIAL_STATUS);
        } catch (TenantAlreadyExistsException ex) {
            // 409 — nothing was created; the IN_PROGRESS row is finalised FAILURE.
            fail(auditId, operator, tenantId, auditReason, idempotencyKey, "TENANT_ALREADY_EXISTS", now);
            throw ex;
        } catch (RuntimeException ex) {
            fail(auditId, operator, tenantId, auditReason, idempotencyKey, ex.getMessage(), now);
            throw ex;
        }
        if (created != null && !INITIAL_STATUS.equals(created.status())) {
            // account-service did not honour initialStatus (an older account-service). Logged loudly; the bundle
            // still runs so the tenant gets its first-admin invitation (admin-api § POST «호환»).
            log.warn("tenant {} was created {} — expected {} (account-service initialStatus not honoured)",
                    tenantId, created.status(), INITIAL_STATUS);
        }

        // 8. ONE admin transaction: first-admin invitation + its audit row + tenant.created — or compensate.
        OperatorInvitationWriter.Issued issued;
        try {
            issued = bundle.write(operator, created, tenantId, firstAdminEmail, firstAdminName,
                    idempotencyKey, reason);
        } catch (RuntimeException ex) {
            compensate(tenantId);
            fail(auditId, operator, tenantId, auditReason, idempotencyKey, ex.getMessage(), now);
            throw ex;
        }

        // 9. TENANT_CREATE SUCCESS, then the mail (after commit — a rolled-back invitation never mails a link).
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.TENANT_CREATE, operator,
                "TENANT", tenantId,
                auditReason,
                null, idempotencyKey,
                Outcome.SUCCESS, null, now, Instant.now()));
        OperatorInvitationResult invitation = invitationUseCase.deliverCommitted(issued, operator);
        return new CreateTenantResult(created, invitation);
    }

    /** 🔴 ADR-044 D3 direction: the tenant that has no first-admin invitation is closed, never left to be opened. */
    private void compensate(String tenantId) {
        try {
            provisioningPort.update(tenantId, null, "SUSPENDED");
            log.warn("tenant {} compensated PENDING → SUSPENDED (first-admin bundle failed)", tenantId);
        } catch (RuntimeException e) {
            // Left PENDING without an invitation (summary NONE) — still not ACTIVE; SUPER_ADMIN re-invites or cleans up.
            log.error("tenant {} compensation (SUSPEND) failed — left PENDING without a first-admin invitation: {}",
                    tenantId, e.getClass().getSimpleName());
        }
    }

    private void fail(String auditId, OperatorContext operator, String tenantId, String reason,
                      String idempotencyKey, String detail, Instant startedAt) {
        auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                auditId, ActionCode.TENANT_CREATE, operator,
                "TENANT", tenantId,
                reason,
                null, idempotencyKey,
                Outcome.FAILURE, detail, startedAt, Instant.now()));
    }

    private static void validateTenantType(String tenantType) {
        if (tenantType == null) {
            throw new IllegalArgumentException("tenantType is required");
        }
        try {
            // Validate via enum — throws if unknown
            com.example.admin.application.tenant.TenantTypeEnum.valueOf(tenantType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "tenantType must be B2C_CONSUMER or B2B_ENTERPRISE: " + tenantType);
        }
    }

    /**
     * Step 8 — its own bean so the {@code @Transactional} boundary is a real proxy call: the invitation row, its
     * audit row and the {@code tenant.created} outbox row commit together or not at all.
     */
    @Component
    @RequiredArgsConstructor
    public static class Bundle {

        private final OperatorInvitationWriter invitationWriter;
        private final TenantEventPublisher tenantEventPublisher;

        @Transactional
        public OperatorInvitationWriter.Issued write(OperatorContext operator, TenantSummary created,
                                                     String tenantId, String email, String displayName,
                                                     String idempotencyKey, String reason) {
            OperatorInvitationWriter.Issued issued = invitationWriter.createFirstAdminPending(
                    operator, tenantId, email, displayName, idempotencyKey, reason);
            tenantEventPublisher.publishTenantCreated(
                    tenantId,
                    created != null ? created.displayName() : null,
                    created != null ? created.tenantType() : null,
                    created != null && created.status() != null ? created.status() : INITIAL_STATUS,
                    operator.operatorId(),
                    created != null && created.createdAt() != null ? created.createdAt() : Instant.now());
            return issued;
        }
    }
}
