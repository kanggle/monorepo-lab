package com.example.scmplatform.e2e.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Direct JDBC fixture helpers for the procurement-service Postgres database.
 *
 * <p>procurement-service v1 has no public "register supplier" endpoint
 * (suppliers are an internal master in v1; supplier-service is a v2
 * deferred). The e2e suite still needs an ACTIVE supplier in the
 * {@code suppliers} table so {@code POST /api/procurement/po} can resolve
 * a real {@code supplierId}. Until v2 lands we seed via JDBC.
 *
 * <p>This is a deliberate trade-off recorded in the task spec § Failure
 * Scenarios — the supplier master row is the only fixture that bypasses the
 * production HTTP API. Everything downstream (PO draft, submit, ack, ASN,
 * inventory snapshots) flows through real REST + Kafka surfaces.
 */
public final class ProcurementDbFixtures {

    private ProcurementDbFixtures() {}

    /**
     * Inserts a single ACTIVE supplier row into the procurement service's
     * Postgres database. Returns the generated supplier id (UUID v4 string).
     *
     * <p>The Testcontainers {@link PostgreSQLContainer} exposes the
     * {@code scm_procurement} database on the published port; this method
     * connects directly via the JDBC driver bundled with Testcontainers.
     */
    public static String insertActiveSupplier(PostgreSQLContainer<?> postgres,
                                              String tenantId,
                                              String name) throws SQLException {
        String supplierId = UUID.randomUUID().toString();
        // Override the container default db name (which is the first DB —
        // scm_procurement in our setup) to be explicit; supports both
        // current setup and a future multi-DB driver.
        String jdbcUrl = postgres.getJdbcUrl().replaceAll("/\\w+$", "/scm_procurement");
        String sql = """
                INSERT INTO suppliers (
                    id, tenant_id, name, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)
                """;
        Instant now = Instant.now();
        try (Connection conn = java.sql.DriverManager.getConnection(
                jdbcUrl, postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, supplierId);
            ps.setString(2, tenantId);
            ps.setString(3, name);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            ps.executeUpdate();
        }
        return supplierId;
    }

    /**
     * Counts audit_log rows for a given (tenant, aggregate_type, aggregate_id)
     * triple. Used by AsnReceiveE2E to verify the transaction history depth
     * after the full DRAFT -> SUBMITTED -> ACKNOWLEDGED -> CONFIRMED ->
     * RECEIVED arc (>=5 audit rows expected).
     */
    public static int countAuditRows(PostgreSQLContainer<?> postgres,
                                     String tenantId,
                                     String aggregateType,
                                     String aggregateId) throws SQLException {
        String jdbcUrl = postgres.getJdbcUrl().replaceAll("/\\w+$", "/scm_procurement");
        String sql = """
                SELECT COUNT(*) FROM audit_log
                WHERE tenant_id = ? AND aggregate_type = ? AND aggregate_id = ?
                """;
        try (Connection conn = java.sql.DriverManager.getConnection(
                jdbcUrl, postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, aggregateType);
            ps.setString(3, aggregateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
