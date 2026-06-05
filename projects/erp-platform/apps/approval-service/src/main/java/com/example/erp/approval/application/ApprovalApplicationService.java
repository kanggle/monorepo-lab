package com.example.erp.approval.application;

import com.example.common.id.UuidV7;
import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.RejectCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.command.Commands.WithdrawCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.application.view.ApprovalRequestView;
import com.example.erp.approval.application.view.ApprovalSummaryView;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;
import com.example.erp.approval.domain.delegation.DelegationResolution;
import com.example.erp.approval.domain.delegation.DelegationResolver;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRequestNotFoundException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.DataScopeForbiddenException;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import com.example.erp.approval.domain.request.repository.ApprovalRequestRepository;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.domain.route.ApprovalRouteStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * approval-service application service — the SINGLE {@code @Transactional}
 * command boundary (architecture.md § Layer Structure / § Boundary rules).
 *
 * <p>Every transition use case: (1) invokes {@link AuthorizationPort} BEFORE any
 * repository call (E6, fail-closed); (2) loads the aggregate (404 if absent);
 * (3) for submit, runs the {@link MasterDataPort} subject ref-check (E1) before
 * the state mutation; (4) drives the transition through the aggregate's
 * state-machine method (T4 — no direct status UPDATE); (5) appends an
 * {@link ApprovalAction} history row + an append-only {@link ApprovalAuditLog}
 * row + an outbox event — all in the SAME Tx (A7 atomicity). The approver-
 * eligibility / submitter / reason guards live in the aggregate (Separation of
 * Duties + audit completeness).
 *
 * <p>Controllers never carry {@code @Transactional} and never touch JPA
 * repositories. Idempotency is enforced at the presentation layer
 * ({@code IdempotentExecution}) which wraps each mutating call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalApplicationService {

    private static final String ENTITLED_DOMAIN = "erp";

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalAuditLogRepository auditLogRepository;
    private final ApprovalEventPublisher eventPublisher;
    private final MasterDataPort masterDataPort;
    private final AuthorizationPort authorizationPort;
    private final DelegationResolver delegationResolver;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;

    // ====================================================================
    // create (DRAFT)
    // ====================================================================

    @Transactional
    public ApprovalRequestView createDraft(CreateDraftCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        Instant now = clock.now();

        // Route validity (E3 / I4): empty/blank/self-approval/duplicate-approver
        // refused at create (the route is fixed at create time per approval-api.md).
        // A 1-element list is the legacy single-stage route (backward-compatible).
        ApprovalRoute route = ApprovalRoute.multiStage(actor.actorId(), cmd.approverIds());
        ApprovalSubject subject = new ApprovalSubject(cmd.subjectType(), cmd.subjectId());

        String requestId = "appr-" + UuidV7.randomString();
        ApprovalRequest request = ApprovalRequest.createDraft(
                requestId, actor.tenantId(), subject,
                cmd.title(), cmd.reason(), route, actor.actorId(), now);
        ApprovalRequest saved = requestRepository.save(request);
        persistStages(saved, route, now);
        // No event on create (a draft is not yet a workflow fact —
        // erp-approval-events.md § Topics); the first published fact is submitted.
        return view(saved);
    }

    /** Persist one approval_route_stage row per stage (ordered) at create time. */
    private void persistStages(ApprovalRequest request, ApprovalRoute route, Instant now) {
        List<ApprovalRouteStage> stages = new java.util.ArrayList<>(route.stageCount());
        for (int i = 0; i < route.stageCount(); i++) {
            stages.add(ApprovalRouteStage.of(
                    "ars-" + UuidV7.randomString(), request.getTenantId(), request.getId(),
                    i, route.approverAt(i).approverId(), now));
        }
        requestRepository.saveStages(stages);
    }

    // ====================================================================
    // transitions
    // ====================================================================

    @Transactional
    public ApprovalRequestView submit(SubmitCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        ApprovalRequest request = loadOrThrow(cmd.id(), actor.tenantId());
        Instant now = clock.now();

        // E1 reference integrity — the subject must resolve ACTIVE before the
        // DRAFT → SUBMITTED mutation. A non-resolvable subject aborts the Tx
        // before any state change (request stays DRAFT) → APPROVAL_ROUTE_INVALID.
        if (!masterDataPort.isSubjectActive(request.subject(), actor.tenantId())) {
            throw new ApprovalRouteInvalidException(
                    "subject " + request.getSubjectType() + " '" + request.getSubjectId()
                            + "' does not resolve to an ACTIVE master (E1)");
        }

        ApprovalStatus before = request.getStatus();
        request.submit(now);
        ApprovalRequest saved = requestRepository.saveAndFlush(request);

        recordTransition(saved, ApprovalStatus.SUBMITTED, actor.actorId(), null,
                before, saved.getCurrentStageIndex(), null, now);
        eventPublisher.publishSubmitted(saved, actor.actorId());
        return view(saved);
    }

    @Transactional
    public ApprovalRequestView approve(ApproveCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        ApprovalRequest request = loadOrThrow(cmd.id(), actor.tenantId());
        ApprovalRoute route = requestRepository.loadRoute(cmd.id(), actor.tenantId());
        Instant now = clock.now();

        ApprovalStatus before = request.getStatus();
        int actingStage = request.getCurrentStageIndex();
        // TASK-ERP-BE-013 — resolve the acting principal against the current stage's
        // approver: direct, active delegate, or fail-closed (not authorized).
        String onBehalfOf = resolveActingApprover(request, route, actor, now);
        request.approve(actor.actorId(), route, onBehalfOf, now);
        ApprovalRequest saved = requestRepository.saveAndFlush(request);

        // The action records the stage that approved (APPROVED action), even when
        // the resulting state is IN_REVIEW (intermediate stage advance). onBehalfOf
        // (= A) is recorded when a delegate acted (대결).
        recordTransition(saved, ApprovalStatus.APPROVED, actor.actorId(), cmd.reason(),
                before, actingStage, onBehalfOf, now);
        // erp-approval-events.md § v2.0: approved.v1 fires ONLY on the FINAL-stage
        // approval (→ APPROVED). An intermediate-stage approval (→ IN_REVIEW)
        // writes the audit row but emits NO outbox event (terminal-once preserved).
        if (saved.getStatus() == ApprovalStatus.APPROVED) {
            eventPublisher.publishApproved(saved, actor.actorId(), cmd.reason(), onBehalfOf);
        }
        return view(saved);
    }

    @Transactional
    public ApprovalRequestView reject(RejectCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        ApprovalRequest request = loadOrThrow(cmd.id(), actor.tenantId());
        ApprovalRoute route = requestRepository.loadRoute(cmd.id(), actor.tenantId());
        Instant now = clock.now();

        ApprovalStatus before = request.getStatus();
        int actingStage = request.getCurrentStageIndex();
        // TASK-ERP-BE-013 — same delegate resolution as approve (대결 reject).
        String onBehalfOf = resolveActingApprover(request, route, actor, now);
        request.reject(actor.actorId(), route, onBehalfOf, cmd.reason(), now);
        ApprovalRequest saved = requestRepository.saveAndFlush(request);

        recordTransition(saved, ApprovalStatus.REJECTED, actor.actorId(), cmd.reason(),
                before, actingStage, onBehalfOf, now);
        eventPublisher.publishRejected(saved, actor.actorId(), cmd.reason(), onBehalfOf);
        return view(saved);
    }

    @Transactional
    public ApprovalRequestView withdraw(WithdrawCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        ApprovalRequest request = loadOrThrow(cmd.id(), actor.tenantId());
        Instant now = clock.now();

        ApprovalStatus before = request.getStatus();
        int actingStage = request.getCurrentStageIndex();
        request.withdraw(actor.actorId(), cmd.reason(), now);
        ApprovalRequest saved = requestRepository.saveAndFlush(request);

        recordTransition(saved, ApprovalStatus.WITHDRAWN, actor.actorId(), cmd.reason(),
                before, actingStage, null, now);
        eventPublisher.publishWithdrawn(saved, actor.actorId(), cmd.reason());
        return view(saved);
    }

    // ====================================================================
    // reads
    // ====================================================================

    @Transactional(readOnly = true)
    public ApprovalRequestView detail(String id, ActorContext actor) {
        authorizeRead(actor);
        ApprovalRequest request = loadOrThrow(id, actor.tenantId());
        return view(request);
    }

    @Transactional(readOnly = true)
    public List<ApprovalSummaryView> list(ActorContext actor, ApprovalStatus status,
                                          ParticipantRole role, int page, int size) {
        authorizeRead(actor);
        List<ApprovalRequest> rows;
        if (role == null && (actor.isOperator() || actor.isPlatformScope())) {
            // Operator / platform scope sees the tenant-wide list.
            rows = requestRepository.findAll(actor.tenantId(), status, page, size);
        } else {
            // Scope-aware: requests where the caller is submitter OR approver.
            rows = requestRepository.findByParticipant(
                    actor.tenantId(), actor.actorId(), status, page, size);
        }
        return rows.stream().map(ApprovalSummaryView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalSummaryView> inbox(ActorContext actor, int page, int size) {
        authorizeRead(actor);
        return requestRepository.findInbox(actor.tenantId(), actor.actorId(), page, size)
                .stream().map(ApprovalSummaryView::from).toList();
    }

    /** Scope filter for the list endpoint (?role=SUBMITTER|APPROVER). */
    public enum ParticipantRole { SUBMITTER, APPROVER }

    // ====================================================================
    // helpers
    // ====================================================================

    private void authorizeWrite(ActorContext actor) {
        // WRITE/transition: scope or operator. Entitlement-trust NEVER widens a
        // transition (architecture.md § Approver authorization — entitlement
        // grants READ only).
        if (actor.isOperator()
                || actor.hasScope("erp.write")
                || actor.hasScope("erp.approval.create")
                || actor.hasScope("erp.approval.approve")) {
            return;
        }
        AuthorizationDecision d = authorizationPort.evaluate(actor, RequiredScope.WRITE, null);
        denyToException(d);
    }

    private void authorizeRead(ActorContext actor) {
        // READ dual-accept: scope OR operator OR entitlement-trust (ADR-MONO-019).
        if (actor.isOperator()
                || actor.hasScope("erp.read")
                || actor.hasScope("erp.write")
                || actor.hasScope("erp.approval.create")
                || actor.hasScope("erp.approval.approve")
                || actor.isEntitledTo(ENTITLED_DOMAIN)) {
            return;
        }
        AuthorizationDecision d = authorizationPort.evaluate(actor, RequiredScope.READ, null);
        denyToException(d);
    }

    private void denyToException(AuthorizationDecision d) {
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_ROLE) {
            throw new PermissionDeniedException(
                    d.reason() == null ? "required role not present" : d.reason());
        }
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_SCOPE) {
            throw new DataScopeForbiddenException(
                    d.reason() == null ? "target outside data scope" : d.reason());
        }
    }

    private ApprovalRequest loadOrThrow(String id, String tenantId) {
        return requestRepository.findById(id, tenantId)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(
                        "approval request not found: " + id));
    }

    private ApprovalRequestView view(ApprovalRequest request) {
        List<ApprovalAction> actions =
                requestRepository.findActions(request.getId(), request.getTenantId());
        List<ApprovalRouteStage> stages =
                requestRepository.findStages(request.getId(), request.getTenantId());
        return ApprovalRequestView.from(request, actions, stages);
    }

    /**
     * Append the transition's history action + the immutable audit row in the
     * SAME Tx as the state change + outbox event (A7 atomicity). Audit-fail-closed
     * (A10): if the append throws, the whole transition Tx rolls back.
     *
     * <p>{@code action} is the command's history label ({@code SUBMITTED} /
     * {@code APPROVED} / {@code REJECTED} / {@code WITHDRAWN}); for an
     * intermediate-stage approve it is {@code APPROVED} (the stage approved) while
     * the request's resulting status is {@code IN_REVIEW}. The audit
     * {@code after_state} snapshot records the request's ACTUAL resulting status
     * (which may be {@code IN_REVIEW}); {@code stage} is the 0-based stage that
     * acted.
     */
    private void recordTransition(ApprovalRequest request, ApprovalStatus action,
                                  String actor, String reason, ApprovalStatus before,
                                  int stage, String onBehalfOf, Instant now) {
        requestRepository.appendAction(ApprovalAction.of(
                request.getTenantId(), request.getId(), action, actor, reason, stage,
                onBehalfOf, now));
        auditLogRepository.append(ApprovalAuditLog.of(
                "evt-" + UuidV7.randomString(), request.getTenantId(), request.getId(),
                auditAction(action), actor, snapshotJson(before),
                snapshotJson(request.getStatus()), reason, now));
    }

    /**
     * Resolve the acting principal of an approve/reject against the current stage's
     * approver A (TASK-ERP-BE-013, architecture.md § v2.1). Returns {@code null}
     * for a direct action (actor IS the approver) or the approver A when an active
     * delegate acts. A non-approver with no active grant → fail-closed
     * {@code APPROVAL_NOT_AUTHORIZED_APPROVER}. <b>Separation of Duties</b>: a
     * delegate who is the request's submitter is refused (no self-approval via
     * delegation). The aggregate re-validates the effective approver against the
     * stage (independent gate). Only meaningful from SUBMITTED/IN_REVIEW; the
     * finalized/illegal-edge guards in the aggregate take precedence (this resolves
     * the stage approver only when the transition is otherwise legal).
     */
    private String resolveActingApprover(ApprovalRequest request, ApprovalRoute route,
                                         ActorContext actor, Instant now) {
        // Only SUBMITTED/IN_REVIEW have a meaningful current-stage approver; for a
        // finalized or pre-submit request let the aggregate raise the precise
        // status error (do not mask it with an authz error).
        if (request.isFinalized() || request.getStatus() == ApprovalStatus.DRAFT) {
            return null;
        }
        String stageApprover = route.approverAt(request.getCurrentStageIndex()).approverId();
        DelegationResolution resolution =
                delegationResolver.resolve(stageApprover, actor.actorId(), actor.tenantId(), now);
        if (!resolution.authorized()) {
            throw new ApprovalNotAuthorizedApproverException(
                    "principal '" + actor.actorId() + "' is not the current stage ("
                            + request.getCurrentStageIndex() + ") approver '" + stageApprover
                            + "' and holds no active delegation for them");
        }
        // SoD — a delegate (effective actor D) who is the request's submitter may not
        // approve via delegation (self-approval-via-delegation is refused). A direct
        // approver can never be the submitter (route construction forbids it).
        if (resolution.isDelegated()
                && Objects.equals(actor.actorId(), request.getSubmitterId())) {
            throw new ApprovalNotAuthorizedApproverException(
                    "delegate '" + actor.actorId() + "' is the request's submitter — "
                            + "self-approval via delegation is refused (Separation of Duties)");
        }
        return resolution.onBehalfOf();
    }

    private static String auditAction(ApprovalStatus transition) {
        return switch (transition) {
            case SUBMITTED -> "approval.submitted";
            case APPROVED -> "approval.approved";
            case REJECTED -> "approval.rejected";
            case WITHDRAWN -> "approval.withdrawn";
            default -> "approval." + transition.name().toLowerCase();
        };
    }

    private String snapshotJson(ApprovalStatus status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.name());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit snapshot", e);
        }
    }

    /** Map an optional contract {@code role} filter string to the enum (null = both). */
    public static ParticipantRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return ParticipantRole.valueOf(role.trim().toUpperCase());
    }

    /** Map an optional contract {@code status} filter string to the enum (null = all). */
    public static ApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return ApprovalStatus.valueOf(status.trim().toUpperCase());
    }

    /** Map an optional contract {@code subjectType} string to the enum. */
    public static SubjectType parseSubjectType(String subjectType) {
        return SubjectType.valueOf(subjectType.trim().toUpperCase());
    }
}
