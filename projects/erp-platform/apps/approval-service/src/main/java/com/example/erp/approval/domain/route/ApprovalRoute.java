package com.example.erp.approval.domain.route;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered multi-stage approval route (1~N stages — TASK-ERP-BE-012, E3 /
 * architecture.md § v2.0 amendment). Each stage is one {@link Approver} at a
 * 0-based {@code stageIndex}; stages are approved sequentially (stage 0 first).
 * The single-stage route ({@link #singleStage}) is the N=1 special case — fully
 * backward-compatible with v1.0. Delegation (대결/위임) is v2.1-deferred — NOT
 * modelled here.
 *
 * <p>A route is malformed (→ {@code APPROVAL_ROUTE_INVALID}) when it has no
 * stages, when any stage's approver is blank, when the submitter equals ANY
 * stage's approver (self-approval — E3/I4), or when the same approver appears at
 * two or more stages (duplicate — {@code duplicate_stage_approver}, Separation of
 * Duties). Pure module — no framework imports.
 */
public final class ApprovalRoute {

    private final List<Approver> stages;

    /** Construct from already-built stages (used by the persistence rehydrate path). */
    public ApprovalRoute(List<Approver> stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            throw new ApprovalRouteInvalidException(
                    "route is malformed: at least one stage is required");
        }
        this.stages = List.copyOf(stages);
    }

    /** Single-stage convenience ctor (1 approver). */
    public ApprovalRoute(Approver approver) {
        this(List.of(Objects.requireNonNull(approver, "approver")));
    }

    /**
     * Construct + validate a single-stage route (N=1). Refuses a missing approver
     * or a self-approving route ({@code submitterId == approverId}).
     */
    public static ApprovalRoute singleStage(String submitterId, String approverId) {
        return multiStage(submitterId, List.of(nonNullApprover(approverId)));
    }

    /**
     * Construct + validate an ordered 1~N stage route. Refuses an empty stage
     * list, a blank approver, self-approval (submitter ∈ any stage), or a
     * duplicate approver across stages ({@code duplicate_stage_approver}).
     */
    public static ApprovalRoute multiStage(String submitterId, List<String> approverIds) {
        if (approverIds == null || approverIds.isEmpty()) {
            throw new ApprovalRouteInvalidException(
                    "route is malformed: at least one stage approver is required");
        }
        Set<String> seen = new HashSet<>();
        List<Approver> built = new ArrayList<>(approverIds.size());
        for (String approverId : approverIds) {
            if (approverId == null || approverId.isBlank()) {
                throw new ApprovalRouteInvalidException(
                        "route is malformed: a stage approver is required (blank)");
            }
            // Self-approval (E3 / I4) — submitter must not appear at any stage.
            SelfApprovalGuard.ensureNotSelfApproval(submitterId, approverId);
            if (!seen.add(approverId)) {
                throw new ApprovalRouteInvalidException(
                        "route is malformed: duplicate_stage_approver — approver '"
                                + approverId + "' appears at more than one stage"
                                + " (E3/I4 Separation of Duties)");
            }
            built.add(new Approver(approverId));
        }
        return new ApprovalRoute(built);
    }

    private static String nonNullApprover(String approverId) {
        if (approverId == null || approverId.isBlank()) {
            throw new ApprovalRouteInvalidException(
                    "route is malformed: approver is required");
        }
        return approverId;
    }

    /** The ordered stage approvers (immutable). */
    public List<Approver> stages() {
        return stages;
    }

    public int stageCount() {
        return stages.size();
    }

    /** The approver at the given 0-based stage index. */
    public Approver approverAt(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stages.size()) {
            throw new ApprovalRouteInvalidException(
                    "stage index " + stageIndex + " out of range [0," + stages.size() + ")");
        }
        return stages.get(stageIndex);
    }

    public boolean isLastStage(int stageIndex) {
        return stageIndex == stages.size() - 1;
    }

    /** Stage 0's approver id (back-compat for the denormalized {@code approverId}). */
    public String approverId() {
        return stages.get(0).approverId();
    }

    /** True iff the principal is the approver at the given stage. */
    public boolean isApproverAt(int stageIndex, String principalId) {
        return approverAt(stageIndex).matches(principalId);
    }

    /** Back-compat: true iff the principal is stage 0's approver. */
    public boolean isApprover(String principalId) {
        return stages.get(0).matches(principalId);
    }
}
