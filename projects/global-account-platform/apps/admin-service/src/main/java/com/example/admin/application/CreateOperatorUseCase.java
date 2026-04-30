package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
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

    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        if (normalizedEmail != null && operatorRepository.existsByEmail(normalizedEmail)) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }

        Map<String, AdminRoleJpaEntity> resolvedRoles = operatorRoleResolver.resolveRoles(roleNames);

        Instant now = Instant.now();
        String operatorUuid = AdminOperatorJpaEntity.newOperatorId();
        String passwordHash = passwordHasher.hash(password);

        AdminOperatorJpaEntity entity = AdminOperatorJpaEntity.create(
                operatorUuid, normalizedEmail, passwordHash, displayName, STATUS_ACTIVE, now);

        try {
            entity = operatorRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }

        Long actorInternalId = operatorRoleResolver.resolveActorInternalId(actor);
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
                Instant.now()));

        List<String> roleNamesOut = new ArrayList<>(resolvedRoles.keySet());
        return new CreateOperatorResult(
                operatorUuid,
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getStatus(),
                roleNamesOut,
                false,
                entity.getCreatedAt(),
                auditId);
    }

    public record CreateOperatorResult(
            String operatorId,
            String email,
            String displayName,
            String status,
            List<String> roles,
            boolean totpEnrolled,
            Instant createdAt,
            String auditId
    ) {}
}
