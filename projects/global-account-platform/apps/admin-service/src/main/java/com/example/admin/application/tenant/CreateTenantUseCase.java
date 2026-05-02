package com.example.admin.application.tenant;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.Outcome;
import com.example.admin.application.event.TenantEventPublisher;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TASK-BE-250: Creates a new tenant via admin-service.
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate tenantId format (regex) and reserved word list.</li>
 *   <li>Validate displayName length and tenantType enum.</li>
 *   <li>Write IN_PROGRESS audit row (fail-closed: abort on audit failure).</li>
 *   <li>Call {@link TenantProvisioningPort#create} (account-service internal API).</li>
 *   <li>On success: finalize audit to SUCCESS + publish {@code tenant.created} outbox event.</li>
 *   <li>On port failure: finalize audit to FAILURE and rethrow.</li>
 * </ol>
 *
 * <p>Idempotency-Key: no IdempotencyStore exists in admin-service for this path;
 * accepted limitation (spec says "권장" — recommended, not required). The account-service
 * side returns 409 on duplicate tenantId which is propagated as-is.
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
            "public", "gap", "auth", "oauth", "me"
    );

    private final TenantProvisioningPort provisioningPort;
    private final AdminActionAuditor auditor;
    private final TenantEventPublisher tenantEventPublisher;

    @Transactional
    public TenantSummary execute(String tenantId, String displayName,
                                 String tenantType, OperatorContext operator,
                                 String reason, String idempotencyKey) {
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

        Instant now = Instant.now();
        String auditId = auditor.newAuditId();

        // 5. Write IN_PROGRESS audit row (fail-closed per A10)
        auditor.recordStart(new AdminActionAuditor.StartRecord(
                auditId, ActionCode.TENANT_CREATE, operator,
                "TENANT", tenantId,
                reason != null ? reason : "<not_provided>",
                null, idempotencyKey, now,
                tenantId /* targetTenantId = the new tenant */));

        try {
            // 6. Delegate to account-service
            TenantSummary result = provisioningPort.create(tenantId, displayName.trim(), tenantType);

            // 7. Finalize audit SUCCESS + publish outbox event (same transaction)
            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_CREATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey,
                    Outcome.SUCCESS, null, now, Instant.now()));

            tenantEventPublisher.publishTenantCreated(
                    result.tenantId(), result.displayName(), result.tenantType(),
                    operator.operatorId(), result.createdAt());

            return result;

        } catch (TenantAlreadyExistsException ex) {
            // 409 — no audit row for duplicates (spec: "audit row NOT created" on conflict)
            // The IN_PROGRESS row was already written; finalize as no-op FAILURE
            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_CREATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey,
                    Outcome.FAILURE, "TENANT_ALREADY_EXISTS", now, Instant.now()));
            throw ex;

        } catch (RuntimeException ex) {
            auditor.recordCompletion(new AdminActionAuditor.CompletionRecord(
                    auditId, ActionCode.TENANT_CREATE, operator,
                    "TENANT", tenantId,
                    reason != null ? reason : "<not_provided>",
                    null, idempotencyKey,
                    Outcome.FAILURE, ex.getMessage(), now, Instant.now()));
            throw ex;
        }
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
}
