package com.example.auth.application.exception;

/**
 * TASK-MONO-772 S4 — admin-service could not answer the console-eligibility question (4xx / 5xx / timeout /
 * circuit-open / IO / a body without a boolean {@code eligible}). Issuance of a pool principal's console token is
 * fail-CLOSED on it: {@code invalid_grant} with the fixed {@code error_description}
 * {@code operator_eligibility_unavailable} — never the «no facet» refusal text, so an outage is not shown to the
 * operator as «you are signed in with another (consumer) account» (772 AC-0 F5).
 */
public class OperatorEligibilityUnavailableException extends RuntimeException {

    public OperatorEligibilityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
