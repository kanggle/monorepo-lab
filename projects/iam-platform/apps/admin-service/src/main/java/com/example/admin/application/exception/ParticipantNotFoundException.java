package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 D4 — a participant-remove targets an (partnership,
 * operator) pair that has no {@code tenant_partnership_participant} row. Maps to
 * HTTP 404 {@code PARTICIPANT_NOT_FOUND}.
 */
public class ParticipantNotFoundException extends RuntimeException {

    public ParticipantNotFoundException(String message) {
        super(message);
    }
}
