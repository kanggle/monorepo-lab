package com.example.admin.application.exception;

/**
 * TASK-BE-631 (auth-to-admin.md § check rule 7 · ADR-MONO-083 D6) — the set rows / set roles needed for
 * {@code domainRoles} could not be read. Fail-closed: maps to {@code 500 INTERNAL_ERROR}, which auth-service turns
 * into an assume refusal. 🔴 Never filled in as {@code []} — «could not read» and «no sets» must not be the same
 * answer (BE-631 Failure Scenario 3).
 */
public class DomainAccessUnavailableException extends RuntimeException {
    public DomainAccessUnavailableException(Throwable cause) {
        super("Effective domain roles could not be determined", cause);
    }
}
