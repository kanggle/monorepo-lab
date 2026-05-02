package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.gap.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateOperatorUseCase {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminOperatorRoleJpaRepository operatorRoleRepository;
    private final AdminActionAuditor auditor;
    private final PasswordHasher passwordHasher;
    private final OperatorRoleResolver operatorRoleResolver;
    private final OperatorLookupPort operatorLookupPort;

    /**
     * Creates a new operator.
     *
     * <p>TASK-BE-249: {@code tenantId} is now required. Defense-in-depth rule:
     * a non-platform-scope operator cannot create a platform-scope ({@code "*"})
     * operator (spec §Implementation Notes).
     */
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason,
                                               String tenantId) {
        // --- TASK-BE-249: resolve actor's tenantId for scope guard ---
        String actorTenantId = operatorLookupPort.findByOperatorId(actor.operatorId())
                .map(OperatorLookupPort.OperatorSummary::tenantId)
                .orElse("fan-platform");
        boolean actorIsPlatformScope = AdminOperator.PLATFORM_TENANT_ID.equals(actorTenantId);

        // Defense-in-depth: non-platform-scope operators cannot create platform-scope operators.
        // TASK-BE-262: record a best-effort DENIED audit row before throwing.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(tenantId) && !actorIsPlatformScope) {
            auditor.recordCrossTenantDenied(
                    actor,
                    actorTenantId,
                    ActionCode.OPERATOR_CREATE,
                    "operator.create",
                    tenantId);
            throw new TenantScopeDeniedException(
                    "Only platform-scope operators may create operators with tenantId='*'");
        }

        // TASK-BE-262: use per-tenant check matching the (tenant_id, email) composite unique index
        // introduced by V0025. Same email in a different tenant is a separate, valid operator.
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        if (normalizedEmail != null && operatorRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }

        Map<String, AdminRoleJpaEntity> resolvedRoles = operatorRoleResolver.resolveRoles(roleNames);

        Instant now = Instant.now();
        String operatorUuid = AdminOperatorJpaEntity.newOperatorId();
        String passwordHash = passwordHasher.hash(password);

        // TASK-BE-249: pass tenantId to the entity factory
        AdminOperatorJpaEntity entity = AdminOperatorJpaEntity.create(
                operatorUuid, normalizedEmail, passwordHash, displayName, STATUS_ACTIVE, tenantId, now);

        try {
            entity = operatorRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }

        Long actorInternalId = operatorRoleResolver.resolveActorInternalId(actor);
        List<AdminOperatorRoleJpaEntity> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminRoleJpaEntity role : resolvedRoles.values()) {
            // TASK-BE-249: pass tenantId to role bindings
            bindings.add(AdminOperatorRoleJpaEntity.create(
                    entity.getId(), role.getId(), now, actorInternalId, tenantId));
        }
        if (!bindings.isEmpty()) {
            operatorRoleRepository.saveAll(bindings);
        }

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_CREATE,
                actor,
                "OPERATOR",
                operatorUuid,
                OperatorRoleResolver.normalizeReason(reason),
                null,
                "create:" + auditId,
                Outcome.SUCCESS,
                null,
                now,
                Instant.now(),
                tenantId));  // TASK-BE-249: targetTenantId = created operator's tenant

        List<String> roleNamesOut = new ArrayList<>(resolvedRoles.keySet());
        return new CreateOperatorResult(
                operatorUuid,
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getStatus(),
                roleNamesOut,
                false,
                entity.getCreatedAt(),
                auditId,
                tenantId);
    }

    /**
     * Backward-compat overload for call sites predating TASK-BE-249.
     * Defaults {@code tenantId} to {@code "fan-platform"}.
     *
     * @deprecated Supply {@code tenantId} explicitly.
     */
    @Deprecated
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason) {
        return createOperator(email, displayName, password, roleNames, actor, reason, "fan-platform");
    }

    public record CreateOperatorResult(
            String operatorId,
            String email,
            String displayName,
            String status,
            List<String> roles,
            boolean totpEnrolled,
            Instant createdAt,
            String auditId,
            String tenantId
    ) {
        /** Backward-compat 8-arg constructor for test fixtures predating TASK-BE-249. */
        public CreateOperatorResult(String operatorId, String email, String displayName,
                                    String status, List<String> roles, boolean totpEnrolled,
                                    Instant createdAt, String auditId) {
            this(operatorId, email, displayName, status, roles, totpEnrolled,
                    createdAt, auditId, "fan-platform");
        }
    }
}
