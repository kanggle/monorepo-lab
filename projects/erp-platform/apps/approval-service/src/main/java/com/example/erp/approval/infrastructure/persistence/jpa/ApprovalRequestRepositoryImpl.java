package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.repository.ApprovalRequestRepository;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.domain.route.ApprovalRouteStage;
import com.example.erp.approval.domain.route.Approver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link ApprovalRequestRepository} port. Transitions use
 * {@code saveAndFlush} so an optimistic-lock conflict (T5) or constraint
 * violation surfaces inside the use-case transaction (BE-335 silent-write-loss
 * lesson). Every query carries {@code tenantId} (defense-in-depth).
 */
@Component
@RequiredArgsConstructor
public class ApprovalRequestRepositoryImpl implements ApprovalRequestRepository {

    private final ApprovalRequestJpaRepository requestJpa;
    private final ApprovalActionJpaRepository actionJpa;
    private final ApprovalRouteStageJpaRepository routeStageJpa;

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        return requestJpa.save(request);
    }

    @Override
    public ApprovalRequest saveAndFlush(ApprovalRequest request) {
        return requestJpa.saveAndFlush(request);
    }

    @Override
    public Optional<ApprovalRequest> findById(String id, String tenantId) {
        return requestJpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public List<ApprovalRequest> findAll(String tenantId, ApprovalStatus status,
                                         int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return status == null
                ? requestJpa.findAllByTenantId(tenantId, pageable)
                : requestJpa.findAllByTenantIdAndStatus(tenantId, status, pageable);
    }

    @Override
    public List<ApprovalRequest> findByParticipant(String tenantId, String participantId,
                                                   ApprovalStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return status == null
                ? requestJpa.findByParticipant(tenantId, participantId, pageable)
                : requestJpa.findByParticipantAndStatus(tenantId, participantId, status, pageable);
    }

    @Override
    public List<ApprovalRequest> findInbox(String tenantId, String approverId,
                                           int page, int size) {
        // Pending = current stage's approver is the caller AND status ∈
        // {SUBMITTED, IN_REVIEW} (TASK-ERP-BE-012). approver_id is denormalized to
        // the current stage's approver.
        return requestJpa.findInboxPending(
                tenantId, approverId, PageRequest.of(page, size));
    }

    @Override
    public ApprovalAction appendAction(ApprovalAction action) {
        return actionJpa.save(action);
    }

    @Override
    public List<ApprovalAction> findActions(String approvalRequestId, String tenantId) {
        return actionJpa.findAllByApprovalRequestIdAndTenantIdOrderByOccurredAtAscIdAsc(
                approvalRequestId, tenantId);
    }

    // ---- multi-stage route (TASK-ERP-BE-012) ----

    @Override
    public List<ApprovalRouteStage> saveStages(List<ApprovalRouteStage> stages) {
        return routeStageJpa.saveAll(stages);
    }

    @Override
    public List<ApprovalRouteStage> findStages(String requestId, String tenantId) {
        return routeStageJpa.findAllByRequestIdAndTenantIdOrderByStageIndexAsc(requestId, tenantId);
    }

    @Override
    public ApprovalRoute loadRoute(String requestId, String tenantId) {
        List<Approver> approvers = findStages(requestId, tenantId).stream()
                .map(ApprovalRouteStage::toApprover)
                .toList();
        if (approvers.isEmpty()) {
            throw new ApprovalRouteInvalidException(
                    "no route stages found for request '" + requestId + "' (E3)");
        }
        return new ApprovalRoute(approvers);
    }
}
