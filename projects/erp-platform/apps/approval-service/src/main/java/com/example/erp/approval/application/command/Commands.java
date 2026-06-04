package com.example.erp.approval.application.command;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.domain.request.SubjectType;

/**
 * Use-case command objects ({@code {UseCase}Command} convention). Each carries
 * the {@link ActorContext} so the application service authorizes against the
 * caller without touching Spring Security.
 */
public final class Commands {

    private Commands() {
    }

    public record CreateDraftCommand(ActorContext actor, SubjectType subjectType,
                                     String subjectId, String title, String reason,
                                     String approverId) {
    }

    public record SubmitCommand(ActorContext actor, String id) {
    }

    public record ApproveCommand(ActorContext actor, String id, String reason) {
    }

    public record RejectCommand(ActorContext actor, String id, String reason) {
    }

    public record WithdrawCommand(ActorContext actor, String id, String reason) {
    }
}
