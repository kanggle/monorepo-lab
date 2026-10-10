package com.example.admin.application.exception;

/**
 * TASK-MONO-772 S7 — 422 {@code OPERATOR_INVITATION_REQUIRED} (admin-api.md § POST /api/admin/operators, implementer
 * decision D-7): {@code POST /api/admin/operators} with a non-platform ({@code tenantId != '*'}) tenant. Company
 * operators are created only by invitation → verified self-acceptance (ADR-MONO-080 D6). Nothing is created and no
 * downstream service is called. Replaces the TASK-MONO-334 "signed-up account required" precondition.
 *
 * <p>Part of the {@link OperatorInvitationException} family so the one existing handler renders it.
 */
public class OperatorInvitationRequiredException extends OperatorInvitationException {

    public OperatorInvitationRequiredException(String message) {
        super("OPERATOR_INVITATION_REQUIRED", 422, message);
    }
}
