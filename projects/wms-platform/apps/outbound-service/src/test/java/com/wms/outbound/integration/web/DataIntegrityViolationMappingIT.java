package com.wms.outbound.integration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wiring-reachability regression for TASK-MONO-450 / TASK-BE-542: the fleet error
 * handler must map a {@link DataIntegrityViolationException} <em>selectively</em>, not
 * unconditionally to 409.
 *
 * <p><b>Why a real database.</b> The discriminant
 * ({@code com.example.common.persistence.DataIntegrityViolations#isUniqueViolation})
 * keys on the JDBC {@code SQLSTATE} carried in the exception's cause chain. Only a real
 * PostgreSQL driver stamps the authentic {@code SQLSTATE} — {@code 23505} for a unique
 * violation, {@code 23503} for a foreign-key violation. An H2 slice or a hand-built mock
 * exception would let the discriminant pass on a fabricated code and prove nothing about
 * the delivered signal. So this boots the full context (Postgres + Flyway schema) via
 * {@link OutboundServiceIntegrationBase}, provokes each violation against the real schema,
 * and feeds the driver-produced exception through the <em>actual autowired</em>
 * {@link GlobalExceptionHandler} bean — not a hand-instantiated copy — so the assertion
 * covers the discriminant AND the handler's runtime status decision together.
 *
 * <p>Tagged {@code integration} (via the base) so it runs only under the {@code
 * integrationTest} Gradle task, which is the {@code --no-parallel} lane for wms
 * (resource-exhaustion flake history, {@code platform/testing-strategy.md} §
 * Integration lane serialisation). Auto-skips without Docker; CI Linux is authoritative.
 */
@DisplayName("DataIntegrityViolationException → selective mapping (unique=409, FK=500)")
class DataIntegrityViolationMappingIT extends OutboundServiceIntegrationBase {

    @Autowired
    private GlobalExceptionHandler handler;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        // Only the successful first insert of the unique scenario persists a row; the
        // FK scenario's insert never commits. Scoped delete keeps the shared IT database
        // clean for the next serialised class without a broad TRUNCATE CASCADE.
        jdbc.update("DELETE FROM outbound_order WHERE erp_order_number LIKE 'IT-DIV-%'");
    }

    @Test
    @DisplayName("unique violation (SQLSTATE 23505) → 409 CONFLICT")
    void uniqueViolation_mapsTo409() {
        String erp = "IT-DIV-UNIQUE-" + UUID.randomUUID();
        insertOrder(UUID.randomUUID(), erp);

        // Second row with the SAME erp_order_number → the NOT NULL UNIQUE constraint
        // (V2) fires 23505 in the real driver.
        Throwable thrown = catchThrowable(() -> insertOrder(UUID.randomUUID(), erp));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        // Prove the authentic SQLSTATE was delivered from the real DB (Test Requirement).
        assertThat(sqlStateOf(thrown)).isEqualTo("23505");

        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleIntegrity((DataIntegrityViolationException) thrown);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("foreign-key violation (SQLSTATE 23503) → 500 INTERNAL_ERROR (stays loud)")
    void foreignKeyViolation_mapsTo500() {
        // outbound_order_line.order_id REFERENCES outbound_order(id); a non-existent
        // parent id is a SERVER defect, not a client conflict. The real driver fires
        // 23503 — not 23505 — so the discriminant must NOT downgrade it to a 409 that
        // monitoring never sees.
        Throwable thrown = catchThrowable(() -> jdbc.update(
                "INSERT INTO outbound_order_line "
                        + "(id, order_id, sku_id, requested_qty, line_number) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlStateOf(thrown)).isEqualTo("23503");

        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleIntegrity((DataIntegrityViolationException) thrown);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    /** Minimal insert — only the NOT NULL / no-default columns of outbound_order (V2). */
    private void insertOrder(UUID id, String erpOrderNumber) {
        jdbc.update(
                "INSERT INTO outbound_order (id, erp_order_number, warehouse_id, partner_id) "
                        + "VALUES (?, ?, ?, ?)",
                id, erpOrderNumber, UUID.randomUUID(), UUID.randomUUID());
    }

    /** Walks the cause chain for the JDBC SQLSTATE the real driver stamped. */
    private static String sqlStateOf(Throwable t) {
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
