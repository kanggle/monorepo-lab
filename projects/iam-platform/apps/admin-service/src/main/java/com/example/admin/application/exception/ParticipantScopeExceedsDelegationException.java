package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 D3/D4 — a participant-add's {@code participantScope}
 * is not a subset of the partnership's {@code delegatedScope}. A participant narrowing
 * may never exceed the host's delegation. Maps to HTTP 422
 * {@code PARTICIPANT_SCOPE_EXCEEDS_DELEGATION}.
 */
public class ParticipantScopeExceedsDelegationException extends RuntimeException {

    public ParticipantScopeExceedsDelegationException(String message) {
        super(message);
    }
}
