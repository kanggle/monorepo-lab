package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 D4 — a participant-add targets an operator whose home
 * {@code tenant_id} is not the partnership's {@code partner_tenant_id}. The partner
 * may only assign its OWN operators (the host never names individual B-people).
 * Maps to HTTP 422 {@code PARTICIPANT_NOT_OWN_OPERATOR}.
 */
public class ParticipantNotOwnOperatorException extends RuntimeException {

    public ParticipantNotOwnOperatorException(String message) {
        super(message);
    }
}
