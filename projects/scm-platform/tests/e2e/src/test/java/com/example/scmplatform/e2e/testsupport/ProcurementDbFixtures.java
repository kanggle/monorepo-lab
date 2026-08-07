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
 * <p>procurement-service v1 has no "register supplier" endpoint, so the e2e
 * suite seeds an ACTIVE supplier into the {@code suppliers} table via JDBC —
 * otherwise {@code POST /api/procurement/po} cannot resolve a real
 * {@code supplierId}. This row is the only fixture that bypasses the
 * production HTTP API; everything downstream (PO draft, submit, ack, ASN,
 * inventory snapshots) flows through real REST + Kafka surfaces.
 *
 * <p><b>The absence is now decided, and decided AGAINST this fixture</b> — see
 * {@code docs/adr/ADR-001-supplier-master-write-surface.md} (ADR-SCM-001,
 * ACCEPTED 2026-08-07, option A) and {@code TASK-SCM-BE-059}. v1 gets an
 * operator-facing supplier registration endpoint, so <b>this fixture is
 * scheduled for removal</b>: BE-059 AC-4 converts every caller to the real API.
 * Do not add new callers. Supplier credentials stay out of v1 entirely
 * (deferred to the v2 {@code supplier-service}), so a supplier created through
 * the new endpoint carries no credentials — that is the normal v1 state, not a
 * fixture shortcut.
 *
 * <p>Historical note, kept because it cost two investigations: an earlier
 * version of this Javadoc asserted the absence was "a deliberate trade-off
 * recorded in the task spec § Failure Scenarios". That citation was dangling —
 * {@code TASK-SCM-INT-001}'s Failure Scenarios section covers Docker,
 * cross-project consumption and CI cost, and says nothing about suppliers. An
 * inference had hardened into a citation inside a comment. This fixture was
 * never evidence that the question was settled; it is what raised it.
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
        // Build the URL explicitly — getJdbcUrl() returns the admin DB ("postgres")
        // with a trailing query string ("?loggerLevel=OFF") that breaks the
        // naive `/\w+$` rewrite. Hit the per-service DB directly via host:port.
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/scm_procurement";
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
        // Build the URL explicitly — getJdbcUrl() returns the admin DB ("postgres")
        // with a trailing query string ("?loggerLevel=OFF") that breaks the
        // naive `/\w+$` rewrite. Hit the per-service DB directly via host:port.
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/scm_procurement";
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
