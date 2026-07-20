package com.example.finance.account.integration;

import com.example.finance.account.presentation.advice.GlobalExceptionHandler;
import com.example.finance.account.presentation.dto.ApiErrorBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Real-MySQL proof (Testcontainers; <b>CI Linux is authoritative</b> — Docker is unstable on the
 * dev host, so this is authored but not run there) that the JDBC signals the selective handler
 * keys on actually reach it from a live MySQL 8 driver (TASK-MONO-450 test requirement:
 * "실 DB 로 판별식이 실제로 전달되는지 최소 1건 증명").
 *
 * <p>The unit + MockMvc tests synthesise the SQLSTATE / vendor code; this IT proves MySQL really
 * emits vendor error code {@code 1062} (SQLSTATE {@code 23000}) for a duplicate and {@code 1048}
 * for a NOT NULL violation — the exact reason the shared, PostgreSQL/H2-only 23505 discriminant is
 * insufficient here and {@code GlobalExceptionHandler} OR-s in a MySQL arm. The handler is fed the
 * REAL translated exception; wiring/reachability is proven separately by
 * {@code GlobalExceptionHandlerDataIntegrityWiringTest}, so here we prove the live driver's
 * discriminant is classified correctly.
 *
 * <p>Uses the libs/java-messaging {@code processed_events} table (a bare PK + two NOT NULL columns,
 * no CHECK constraints) so a duplicate primary key and a NULL column give clean, minimal triggers.
 * {@code NOW(6)} is used for the timestamp column to avoid binding a host-timezone
 * {@code java.sql.Timestamp} in a fixture.
 */
@DisplayName("real MySQL DataIntegrityViolation 선별 매핑 (Testcontainers)")
class DataIntegrityViolationMappingIntegrationTest extends AbstractAccountIntegrationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("real MySQL duplicate key → vendor code 1062 → 409 CONCURRENT_MODIFICATION")
    void realDuplicateKeyMapsTo409() {
        assertThat(jdbcTemplate).as("JdbcTemplate must be wired for this IT").isNotNull();
        String eventId = "dup-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, NOW(6))",
                eventId, "TASK-MONO-450-IT");

        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, NOW(6))",
                eventId, "TASK-MONO-450-IT"));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        DataIntegrityViolationException div = (DataIntegrityViolationException) thrown;

        // The live MySQL driver carries ER_DUP_ENTRY (1062) under SQLSTATE 23000 — NOT the
        // PostgreSQL-specific 23505 the shared java-common discriminant looks for. This is
        // precisely why the handler needs the MySQL arm (TASK-MONO-450 AC-5).
        assertThat(sqlErrorCodeOf(div))
                .as("MySQL duplicate must surface vendor code 1062").isEqualTo(1062);

        ResponseEntity<ApiErrorBody> response = handler.handleIntegrity(div);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("real MySQL NOT NULL violation → vendor code 1048 → stays a loud 500")
    void realNotNullViolationStays500() {
        assertThat(jdbcTemplate).as("JdbcTemplate must be wired for this IT").isNotNull();
        String eventId = "nn-" + UUID.randomUUID();

        // event_type is NOT NULL; binding NULL provokes ER_BAD_NULL_ERROR (1048), a server defect.
        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, NOW(6))",
                eventId, null));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        DataIntegrityViolationException div = (DataIntegrityViolationException) thrown;
        assertThat(sqlErrorCodeOf(div))
                .as("MySQL NOT NULL violation must surface vendor code 1048, not 1062").isEqualTo(1048);

        ResponseEntity<ApiErrorBody> response = handler.handleIntegrity(div);
        assertThat(response.getStatusCode())
                .as("a NOT NULL violation is a server defect and must stay a loud 500")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    /** First SQLException vendor error code in the cause chain (self-cycle guarded), or -1. */
    private static int sqlErrorCodeOf(Throwable e) {
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return -1;
    }
}
