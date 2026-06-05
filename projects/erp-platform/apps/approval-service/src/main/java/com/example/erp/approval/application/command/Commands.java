package com.example.erp.approval.application.command;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.erp.approval.domain.request.SubjectType;

import java.time.Instant;
import java.util.List;

/**
 * Use-case command objects ({@code {UseCase}Command} convention). Each carries
 * the {@link ActorContext} so the application service authorizes against the
 * caller without touching Spring Security.
 */
public final class Commands {

    private Commands() {
    }

    /**
     * Create a 1~N stage approval request. {@code approverIds} is the ordered
     * stage list (TASK-ERP-BE-012); a 1-element list is the legacy single-stage
     * route. The presentation layer maps the legacy {@code approverId} body field
     * to a 1-element list, so this command always carries the resolved list.
     */
    public record CreateDraftCommand(ActorContext actor, SubjectType subjectType,
                                     String subjectId, String title, String reason,
                                     List<String> approverIds) {

        /** Legacy single-stage convenience ctor (back-compat for callers/tests). */
        public CreateDraftCommand(ActorContext actor, SubjectType subjectType,
                                  String subjectId, String title, String reason,
                                  String approverId) {
            this(actor, subjectType, subjectId, title, reason, List.of(approverId));
        }
    }

    public record SubmitCommand(ActorContext actor, String id) {
    }

    public record ApproveCommand(ActorContext actor, String id, String reason) {
    }

    public record RejectCommand(ActorContext actor, String id, String reason) {
    }

    public record WithdrawCommand(ActorContext actor, String id, String reason) {
    }

    // ---- delegation (TASK-ERP-BE-013, 대결/위임) ----

    /**
     * Create a delegation grant. The delegator A = the caller's {@code sub}
     * ({@code actor.actorId()}); D = {@code delegateId}. {@code validTo} null =
     * open-ended. TASK-ERP-BE-017 — {@code scope} ({@code null} → GLOBAL) +
     * {@code scopeRequestId} (REQUEST-scoped grant target; the controller parses the
     * scope string and an unknown value is a 400 before the command is built).
     */
    public record CreateDelegationCommand(ActorContext actor, String delegateId,
                                          Instant validFrom, Instant validTo,
                                          String reason, DelegationScope scope,
                                          String scopeRequestId) {
    }

    public record RevokeDelegationCommand(ActorContext actor, String id, String reason) {
    }
}
