package com.example.erp.approval.application;

import com.example.common.page.PageResult;
import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.repository.ApprovalRequestRepository;
import com.example.erp.approval.domain.route.Approver;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.domain.route.ApprovalRouteStage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Test-only in-memory {@link ApprovalRequestRepository} (TASK-MONO-776). Its
 * {@link #findInbox} / {@link #findByParticipant} predicates are copied from
 * {@code ApprovalRequestJpaRepository} — {@code approverId = :approverId AND status IN
 * (SUBMITTED, IN_REVIEW)} and {@code submitterId = :p OR approverId = :p} — so a unit test
 * can show which id the use case hands to that predicate. The predicate itself is pinned
 * against real MySQL by {@code ApprovalLifecycleIntegrationTest}; this fake only lets the
 * «which id space is compared» question run without Docker.
 */
class InMemoryApprovalRequestRepository implements ApprovalRequestRepository {

    private final Map<String, ApprovalRequest> rows = new LinkedHashMap<>();
    private final List<ApprovalAction> actions = new ArrayList<>();
    private final List<ApprovalRouteStage> stages = new ArrayList<>();

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        rows.put(request.getId(), request);
        return request;
    }

    @Override
    public ApprovalRequest saveAndFlush(ApprovalRequest request) {
        return save(request);
    }

    @Override
    public Optional<ApprovalRequest> findById(String id, String tenantId) {
        ApprovalRequest r = rows.get(id);
        return r != null && r.getTenantId().equals(tenantId) ? Optional.of(r) : Optional.empty();
    }

    @Override
    public PageResult<ApprovalRequest> findAll(String tenantId, ApprovalStatus status,
                                               int page, int size) {
        return page(r -> r.getTenantId().equals(tenantId)
                && (status == null || r.getStatus() == status), page, size);
    }

    @Override
    public PageResult<ApprovalRequest> findByParticipant(String tenantId, String participantId,
                                                         ApprovalStatus status, int page, int size) {
        return page(r -> r.getTenantId().equals(tenantId)
                && (Objects.equals(r.getSubmitterId(), participantId)
                        || Objects.equals(r.getApproverId(), participantId))
                && (status == null || r.getStatus() == status), page, size);
    }

    @Override
    public PageResult<ApprovalRequest> findInbox(String tenantId, String approverId,
                                                 int page, int size) {
        return page(r -> r.getTenantId().equals(tenantId)
                && Objects.equals(r.getApproverId(), approverId)
                && (r.getStatus() == ApprovalStatus.SUBMITTED
                        || r.getStatus() == ApprovalStatus.IN_REVIEW), page, size);
    }

    @Override
    public ApprovalAction appendAction(ApprovalAction action) {
        actions.add(action);
        return action;
    }

    @Override
    public List<ApprovalAction> findActions(String approvalRequestId, String tenantId) {
        return actions.stream()
                .filter(a -> a.getApprovalRequestId().equals(approvalRequestId))
                .toList();
    }

    @Override
    public List<ApprovalRouteStage> saveStages(List<ApprovalRouteStage> newStages) {
        stages.addAll(newStages);
        return newStages;
    }

    @Override
    public List<ApprovalRouteStage> findStages(String requestId, String tenantId) {
        return stages.stream()
                .filter(s -> s.getRequestId().equals(requestId))
                .sorted(Comparator.comparingInt(ApprovalRouteStage::getStageIndex))
                .toList();
    }

    @Override
    public ApprovalRoute loadRoute(String requestId, String tenantId) {
        return new ApprovalRoute(findStages(requestId, tenantId).stream()
                .map(s -> new Approver(s.getApproverId()))
                .toList());
    }

    /** All persisted actions (history rows) — lets a test read {@code history[].actor}. */
    List<ApprovalAction> actions() {
        return List.copyOf(actions);
    }

    private PageResult<ApprovalRequest> page(Predicate<ApprovalRequest> p, int page, int size) {
        List<ApprovalRequest> all = rows.values().stream().filter(p).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / size);
        return new PageResult<>(all.subList(from, to), page, size, all.size(), totalPages);
    }
}
