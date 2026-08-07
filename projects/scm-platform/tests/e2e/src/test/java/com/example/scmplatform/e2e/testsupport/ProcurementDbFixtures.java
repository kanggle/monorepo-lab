package com.example.scmplatform.e2e.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Direct JDBC read helpers for the procurement-service Postgres database.
 *
 * <p><b>Supplier seeding no longer lives here</b> (TASK-SCM-BE-059 AC-4). This
 * class used to carry {@code insertActiveSupplier}, the suite's only fixture
 * that bypassed the production HTTP API — it existed because procurement-service
 * v1 had no "register supplier" endpoint at all, so {@code POST
 * /api/procurement/po} could not resolve a {@code supplierId} any other way.
 * {@code ADR-SCM-001} (ACCEPTED 2026-08-07, option A) decided that absence
 * against the fixture; the endpoint exists now and
 * {@link SupplierApiFixtures#registerActiveSupplier} is the only supported path.
 * The JDBC insert was <b>deleted rather than deprecated</b>: two live paths to
 * the same row is how a fixture and the product drift apart without anyone
 * noticing.
 *
 * <p>What remains is read-only assertion support, which does not duplicate any
 * product write path.
 *
 * <p>Historical note, kept because it cost two investigations: an earlier
 * version of this Javadoc asserted the absence was "a deliberate trade-off
 * recorded in the task spec § Failure Scenarios". That citation was dangling —
 * {@code TASK-SCM-INT-001}'s Failure Scenarios section covers Docker,
 * cross-project consumption and CI cost, and says nothing about suppliers. An
 * inference had hardened into a citation inside a comment. The fixture was
 * never evidence that the question was settled; it is what raised it.
 */
public final class ProcurementDbFixtures {

    private ProcurementDbFixtures() {}

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
