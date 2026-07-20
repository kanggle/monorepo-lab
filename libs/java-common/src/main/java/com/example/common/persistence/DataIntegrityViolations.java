package com.example.common.persistence;

import java.sql.SQLException;

/**
 * Discriminates the cause of a data-integrity failure so a global exception handler can map a
 * <em>unique</em> violation (a client-visible conflict → 409) apart from FK / NOT NULL / CHECK
 * violations (server defects → 500, kept loud in logs and alerting).
 *
 * <p><strong>Why SQLSTATE and not the exception type or message.</strong> Spring maps
 * <em>every</em> Hibernate {@code ConstraintViolationException} to a plain
 * {@code org.springframework.dao.DataIntegrityViolationException} (verified against spring-orm
 * 6.2.1 — {@code DuplicateKeyException} is produced only from {@code NonUniqueObjectException},
 * a session-level error, never from a DB unique violation), so the exception <em>type</em> cannot
 * discriminate. The message is vendor- and driver-dependent and breaks silently on upgrade. The
 * JDBC {@link SQLException#getSQLState() SQLSTATE} is the stable signal: {@code 23505} =
 * {@code unique_violation} on both PostgreSQL and H2.
 *
 * <p>Pure JDK utility — takes {@link Throwable} rather than the Spring exception type, so it adds
 * no Spring coupling to {@code java-common} and every consumer already on this library can call it
 * without a new dependency (TASK-MONO-450 AC-5: the discriminant is promoted as a pure function,
 * <em>not</em> as a shared handler base — that shared-base route was rejected as non-drop-in by
 * TASK-FIN-BE-058, and this is a different thing).
 *
 * <p><strong>Two database vendors, one discriminant.</strong> The fleet is mostly PostgreSQL, but
 * finance-platform runs MySQL 8, and the two report a unique violation differently — so a
 * SQLSTATE-{@code 23505}-only check (correct for Postgres/H2) silently mis-routes every MySQL
 * unique violation to 500 (TASK-MONO-450, caught while wiring finance account-service). MySQL folds
 * unique / FK / NOT NULL / CHECK all under SQLSTATE {@code 23000} and distinguishes them by
 * <em>vendor error code</em> ({@code 1062} = {@code ER_DUP_ENTRY}). This utility therefore checks
 * both signals; they cannot collide, because the PostgreSQL driver leaves {@code getErrorCode() == 0}
 * and MySQL does not use SQLSTATE {@code 23505}.
 */
public final class DataIntegrityViolations {

    /** PostgreSQL / H2 SQLSTATE for a unique-constraint violation. */
    public static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /** MySQL / MariaDB vendor error code for a duplicate-key (unique) violation ({@code ER_DUP_ENTRY}). */
    public static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;

    private DataIntegrityViolations() {
    }

    /**
     * Returns {@code true} iff the throwable's cause chain contains a {@link SQLException} that
     * signals a <em>unique</em> constraint violation on either supported vendor — PostgreSQL/H2
     * (SQLSTATE {@code 23505}) or MySQL/MariaDB (vendor error code {@code 1062}). Walks the chain
     * rather than inspecting only the top exception, because Spring wraps the JDBC/Hibernate cause
     * several layers deep. Guards against a self-referential cause cycle.
     *
     * @param t the caught throwable (typically a Spring {@code DataIntegrityViolationException});
     *          {@code null} yields {@code false}
     */
    public static boolean isUniqueViolation(Throwable t) {
        for (Throwable cause = t; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (SQLSTATE_UNIQUE_VIOLATION.equals(sql.getSQLState())
                    || MYSQL_DUPLICATE_ENTRY_ERROR_CODE == sql.getErrorCode())) {
                return true;
            }
        }
        return false;
    }
}
