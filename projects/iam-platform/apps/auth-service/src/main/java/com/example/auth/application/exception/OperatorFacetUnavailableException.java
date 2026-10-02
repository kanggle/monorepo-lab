package com.example.auth.application.exception;

/**
 * TASK-BE-618 — admin-service could not answer the operator-facet question (4xx / 5xx / timeout /
 * circuit-open / IO / malformed body). The consumer-pool credential move is fail-CLOSED on it: nothing is
 * moved and the internal endpoint answers {@code 503 SERVICE_UNAVAILABLE}.
 */
public class OperatorFacetUnavailableException extends RuntimeException {

    public OperatorFacetUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
