package com.example.fanplatform.artist.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-748 AC-1 — the free-text → {@code agencies} move, run against an
 * <b>existing volume</b>: the schema is migrated only up to V3 (the state every real
 * {@code fanplatform_artist} database is in), free-text rows are written the way the
 * old seed / API wrote them, and THEN V4 runs over them.
 *
 * <p>A fresh-database test cannot see this defect class: on an empty database V4's
 * data move has nothing to move, so it passes whether the move is right or not.
 *
 * <p>Same shape as {@code wms-platform admin-service ExistingVolumeMigrationOrderIT}.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V4 agencies — existing volume (free text present before V4)")
class AgencyMigrationExistingVolumeIT {

    private static final String LOCATION = "classpath:db/migration/artist";
    private static final String T1 = "fan-platform";
    private static final String T2 = "other-tenant";

    @Test
    @DisplayName("AC-1: identical text (after the rule) → one agency row; blank → NULL; nothing left behind")
    void existingVolume_freeTextMovesIntoAgencies() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            migrate(ds, "3");

            // --- the pre-V4 world: free text, written as people typed it ----------
            insertArtist(ds, "a-1", T1, "LUMI", "Aurora Entertainment");
            insertArtist(ds, "a-2", T1, "NOAH", "  Aurora   Entertainment ");   // same after the rule
            insertArtist(ds, "a-3", T1, "SEA", "Aurora\tEntertainment");        // tab = whitespace
            insertArtist(ds, "a-4", T1, "CAPS", "AURORA ENTERTAINMENT");        // case differs → separate
            insertArtist(ds, "a-5", T1, "SMOL", "SM");                          // Edge Case: no auto-merge
            insertArtist(ds, "a-6", T1, "SMENT", "SM Entertainment");
            insertArtist(ds, "a-7", T1, "SOLO1", null);                         // Edge Case 2: unaffiliated
            insertArtist(ds, "a-8", T1, "SOLO2", "");
            insertArtist(ds, "a-9", T1, "SOLO3", "   ");
            insertArtist(ds, "b-1", T2, "OTHER", "Aurora Entertainment");       // other tenant
            insertGroup(ds, "g-1", T1, "STELLAR", "Aurora Entertainment");      // shares a-1's row
            insertGroup(ds, "g-2", T1, "NOLABEL", null);

            migrate(ds, null); // → latest (V4)

            Map<String, String> artistAgency = agencyIdByRow(ds, "artists");
            Map<String, String> groupAgency = agencyIdByRow(ds, "artist_groups");

            // identical (after normalisation) → the SAME agencies row, artists AND groups
            String aurora = artistAgency.get("a-1");
            assertThat(aurora).isNotNull();
            assertThat(artistAgency.get("a-2")).isEqualTo(aurora);
            assertThat(artistAgency.get("a-3")).isEqualTo(aurora);
            assertThat(groupAgency.get("g-1")).isEqualTo(aurora);
            assertThat(nameOf(ds, aurora)).isEqualTo("Aurora Entertainment");

            // outside the rule → separate rows (case, «SM» vs «SM Entertainment»)
            assertThat(artistAgency.get("a-4")).isNotNull().isNotEqualTo(aurora);
            assertThat(artistAgency.get("a-5")).isNotNull();
            assertThat(artistAgency.get("a-6")).isNotNull().isNotEqualTo(artistAgency.get("a-5"));

            // blank / absent → NULL
            assertThat(artistAgency.get("a-7")).isNull();
            assertThat(artistAgency.get("a-8")).isNull();
            assertThat(artistAgency.get("a-9")).isNull();
            assertThat(groupAgency.get("g-2")).isNull();

            // tenant-scoped: the same text in another tenant is another tenant's row
            assertThat(artistAgency.get("b-1")).isNotNull().isNotEqualTo(aurora);
            assertThat(tenantOf(ds, artistAgency.get("b-1"))).isEqualTo(T2);

            // row count: T1 = {Aurora Entertainment, AURORA ENTERTAINMENT, SM, SM Entertainment}, T2 = 1
            assertThat(count(ds, "SELECT count(*) FROM agencies WHERE tenant_id = '" + T1 + "'")).isEqualTo(4);
            assertThat(count(ds, "SELECT count(*) FROM agencies WHERE tenant_id = '" + T2 + "'")).isEqualTo(1);
            assertThat(count(ds, "SELECT count(*) FROM agencies WHERE status <> 'ACTIVE' OR store_seller_id IS NOT NULL"))
                    .isZero();

            // Failure Scenario 1: no row with non-blank text is left without an agency
            assertThat(count(ds, "SELECT count(*) FROM artists WHERE agency_id IS NULL AND btrim(coalesce(agency,'')) <> ''"))
                    .isZero();

            // the free-text columns are KEPT, byte-identical
            assertThat(scalar(ds, "SELECT agency FROM artists WHERE id = 'a-2'")).isEqualTo("  Aurora   Entertainment ");
        }
    }

    @Test
    @DisplayName("the composite FK refuses a cross-tenant agency link")
    void compositeFk_refusesCrossTenantLink() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            migrate(ds, "3");
            insertArtist(ds, "b-1", T2, "OTHER", "Aurora Entertainment");
            insertArtist(ds, "a-1", T1, "LUMI", null);
            migrate(ds, null);

            String t2Agency = agencyIdByRow(ds, "artists").get("b-1");
            assertThatThrownBy(() -> exec(ds,
                    "UPDATE artists SET agency_id = '" + t2Agency + "' WHERE id = 'a-1'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("fk_artists_agency");
        }
    }

    @Test
    @DisplayName("a fresh database migrates with nothing to move")
    void freshDatabase() throws Exception {
        try (PostgreSQLContainer<?> pg = postgres()) {
            pg.start();
            DataSource ds = dataSource(pg);
            migrate(ds, null);
            assertThat(count(ds, "SELECT count(*) FROM agencies")).isZero();
        }
    }

    // --- helpers -------------------------------------------------------------

    private static void migrate(DataSource ds, String target) {
        var cfg = Flyway.configure().dataSource(ds).locations(LOCATION).baselineOnMigrate(true);
        if (target != null) {
            cfg.target(MigrationVersion.fromVersion(target));
        }
        cfg.load().migrate();
    }

    private static void insertArtist(DataSource ds, String id, String tenant, String stage, String agency)
            throws SQLException {
        exec(ds, "INSERT INTO artists (id, tenant_id, account_id, artist_type, status, stage_name, agency, "
                + "created_at, updated_at, version) VALUES ('" + id + "', '" + tenant + "', '" + id
                + "', 'SOLO', 'PUBLISHED', '" + stage + "', " + literal(agency) + ", now(), now(), 0)");
    }

    private static void insertGroup(DataSource ds, String id, String tenant, String name, String agency)
            throws SQLException {
        exec(ds, "INSERT INTO artist_groups (id, tenant_id, name, agency, status, created_at, updated_at, version) "
                + "VALUES ('" + id + "', '" + tenant + "', '" + name + "', " + literal(agency)
                + ", 'ACTIVE', now(), now(), 0)");
    }

    /** E'' literal so the tab in a test value reaches the database as a real tab. */
    private static String literal(String v) {
        if (v == null) return "NULL";
        return "E'" + v.replace("\\", "\\\\").replace("'", "\\'").replace("\t", "\\t") + "'";
    }

    private static Map<String, String> agencyIdByRow(DataSource ds, String table) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, agency_id FROM " + table)) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        }
        return out;
    }

    private static String nameOf(DataSource ds, String agencyId) throws SQLException {
        return scalar(ds, "SELECT name FROM agencies WHERE id = '" + agencyId + "'");
    }

    private static String tenantOf(DataSource ds, String agencyId) throws SQLException {
        return scalar(ds, "SELECT tenant_id FROM agencies WHERE id = '" + agencyId + "'");
    }

    private static String scalar(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static long count(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void exec(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("fanplatform_artist")
                .withUsername("test")
                .withPassword("test");
    }

    private static DataSource dataSource(PostgreSQLContainer<?> pg) {
        return DataSourceBuilder.create()
                .url(pg.getJdbcUrl())
                .username(pg.getUsername())
                .password(pg.getPassword())
                .build();
    }
}
