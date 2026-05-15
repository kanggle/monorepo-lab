package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchOperatorRoleUseCase {

    /**
     * Behaviour-preservation constant (TASK-BE-288 review, Finding 1 / Option A).
     *
     * <p>Before the TASK-BE-288 port extraction this use case bound roles via the
     * <em>legacy 4-arg</em> {@code AdminOperatorRoleJpaEntity.create(operatorId,
     * roleId, grantedAt, grantedBy)} overload, which resolves
     * {@code admin_operator_roles.tenant_id} to the hardcoded {@code "fan-platform"}
     * ("Legacy call sites that predate TASK-BE-249"). The port migration must not
     * silently change persisted tenant-scoped state, so the binding stays pinned
     * to that exact legacy value here — keeping TASK-BE-288 strictly
     * behaviour-neutral.
     *
     * <p>This is a known latent inconsistency with {@code CreateOperatorUseCase}
     * (which already stamps the operator's real tenant). The deliberate
     * correctness fix + Flyway backfill + tenant-isolation regression test is
     * tracked separately by {@code TASK-BE-289-fix-TASK-BE-288}. Do NOT change
     * this to {@code operator.tenantId()} without that task's migration.
     */
    private static final String LEGACY_BINDING_TENANT_ID = "fan-platform";

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;

    @Transactional
    public PatchRolesResult patchRoles(String operatorUuid,
                                       List<String> roleNames,
                                       OperatorContext actor,
                                       String reason) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid));

        Map<String, AdminOperatorPort.RoleView> resolvedRoles = operatorPort.resolveRolesByName(roleNames);

        Instant now = Instant.now();
        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());

        operatorPort.deleteOperatorRoles(operator.internalId());

        List<AdminOperatorPort.NewRoleBinding> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminOperatorPort.RoleView role : resolvedRoles.values()) {
            // TASK-BE-288 Finding 1 / Option A: pin to the legacy hardcoded
            // tenant_id (see LEGACY_BINDING_TENANT_ID javadoc). Behaviour-neutral
            // vs origin/main; correctness fix tracked by TASK-BE-289.
            bindings.add(new AdminOperatorPort.NewRoleBinding(
                    operator.internalId(), role.id(), now, actorInternalId, LEGACY_BINDING_TENANT_ID));
        }
        operatorPort.saveOperatorRoles(bindings);

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_ROLE_CHANGE,
                actor,
                "OPERATOR",
                operatorUuid,
                AuditReasons.normalize(reason),
                null,
                "roles:" + auditId,
                Outcome.SUCCESS,
                null,
                now,
                Instant.now()));

        safeInvalidatePermissionCache(operatorUuid);

        List<String> orderedRoles = new ArrayList<>(resolvedRoles.keySet());
        return new PatchRolesResult(operatorUuid, orderedRoles, auditId);
    }

    private void safeInvalidatePermissionCache(String operatorUuid) {
        if (cachingPermissionEvaluator == null) return;
        try {
            cachingPermissionEvaluator.invalidate(operatorUuid);
        } catch (RuntimeException ex) {
            log.warn("Permission cache invalidate failed for operatorId={} cause={}",
                    operatorUuid, ex.getClass().getSimpleName());
        }
    }

    public record PatchRolesResult(
            String operatorId,
            List<String> roles,
            String auditId
    ) {}
}
