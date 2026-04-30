package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
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

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminOperatorRoleJpaRepository operatorRoleRepository;
    private final AdminActionAuditor auditor;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;
    private final OperatorRoleResolver operatorRoleResolver;

    @Transactional
    public PatchRolesResult patchRoles(String operatorUuid,
                                       List<String> roleNames,
                                       OperatorContext actor,
                                       String reason) {
        AdminOperatorJpaEntity entity = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid));

        Map<String, AdminRoleJpaEntity> resolvedRoles = operatorRoleResolver.resolveRoles(roleNames);

        Instant now = Instant.now();
        Long actorInternalId = operatorRoleResolver.resolveActorInternalId(actor);

        operatorRoleRepository.deleteByOperatorId(entity.getId());

        List<AdminOperatorRoleJpaEntity> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminRoleJpaEntity role : resolvedRoles.values()) {
            bindings.add(AdminOperatorRoleJpaEntity.create(entity.getId(), role.getId(), now, actorInternalId));
        }
        if (!bindings.isEmpty()) {
            operatorRoleRepository.saveAll(bindings);
        }

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_ROLE_CHANGE,
                actor,
                "OPERATOR",
                operatorUuid,
                OperatorRoleResolver.normalizeReason(reason),
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
