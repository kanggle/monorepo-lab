package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.SelfSuspendForbiddenException;
import com.example.admin.application.exception.StateTransitionInvalidException;
import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchOperatorStatusUseCase {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminActionAuditor auditor;
    private final AdminRefreshTokenPort refreshTokenPort;

    @Transactional
    public PatchStatusResult patchStatus(String operatorUuid,
                                         String newStatus,
                                         OperatorContext actor,
                                         String reason) {
        if (!STATUS_ACTIVE.equals(newStatus) && !STATUS_SUSPENDED.equals(newStatus)) {
            throw new StateTransitionInvalidException(
                    "Unsupported operator status: " + newStatus);
        }
        if (STATUS_SUSPENDED.equals(newStatus)
                && Objects.equals(actor == null ? null : actor.operatorId(), operatorUuid)) {
            throw new SelfSuspendForbiddenException(
                    "Operators cannot suspend their own account");
        }

        AdminOperatorJpaEntity entity = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid));

        String previousStatus = entity.getStatus();
        if (Objects.equals(previousStatus, newStatus)) {
            throw new StateTransitionInvalidException(
                    "Operator status is already " + newStatus);
        }

        Instant now = Instant.now();
        entity.changeStatus(newStatus, now);
        operatorRepository.save(entity);

        if (STATUS_SUSPENDED.equals(newStatus)) {
            int revoked = refreshTokenPort.revokeAllForOperator(
                    entity.getId(), now, AdminRefreshTokenPort.REASON_FORCE_LOGOUT);
            log.info("Suspended operator {} — revoked {} refresh token(s)", operatorUuid, revoked);
        }

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_STATUS_CHANGE,
                actor,
                "OPERATOR",
                operatorUuid,
                normalizeReason(reason),
                null,
                "status:" + auditId,
                Outcome.SUCCESS,
                "status:" + previousStatus + "->" + newStatus,
                now,
                Instant.now()));

        return new PatchStatusResult(operatorUuid, previousStatus, newStatus, auditId);
    }

    private static String normalizeReason(String reason) {
        return (reason == null || reason.isBlank()) ? "<not_provided>" : reason;
    }

    public record PatchStatusResult(
            String operatorId,
            String previousStatus,
            String currentStatus,
            String auditId
    ) {}
}
