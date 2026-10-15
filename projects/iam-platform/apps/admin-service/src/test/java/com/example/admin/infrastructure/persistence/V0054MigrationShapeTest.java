package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.DomainGrantSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-773 S3 — non-Docker shape-pin for {@code V0054__operator_domain_set_grant_origin_source.sql} (the
 * {@link V0053MigrationShapeTest} form). The behaviour — an ORIGIN row on a company tenant is admitted, on {@code '*'}
 * refused, an unknown source still refused — is {@code SelfServiceOnboardingTrustGateIntegrationTest
 * #check_admitsOriginOnACompanyTenant_refusesItOnStar} against real MySQL.
 *
 * <p>What a green run on a FRESH database cannot catch, and this pins:
 * <ol>
 *   <li>The drop of the V0052 CHECK is guarded on «its clause does not name ORIGIN yet» — a re-run on a migrated
 *       database must not drop the new CHECK (a fresh-volume CI would never notice).</li>
 *   <li>The re-added vocabulary is exactly DIRECT · MIGRATION · ORIGIN + GROUP:% off '*' — the code constants.</li>
 *   <li>ORIGIN off '*' is its own CHECK; no CHECK references {@code group_origin} (ER 3823).</li>
 *   <li>No data change (no INSERT / UPDATE / DELETE — ORIGIN is never backfilled), no cross-statement variable.</li>
 *   <li>No other admin-service migration uses version 0054.</li>
 * </ol>
 */
class V0054MigrationShapeTest {

    private static final String MIGRATION_RESOURCE = "/db/migration/V0054__operator_domain_set_grant_origin_source.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0054MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0054 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String body() {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (line.stripLeading().startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String flat() {
        return body().replaceAll("\\s+", " ").toUpperCase();
    }

    @Test
    @DisplayName("drop: the V0052 source CHECK only while its clause lacks ORIGIN (re-run never drops the new one)")
    void dropIsGuardedOnTheOldClause() {
        String flat = flat();
        assertThat(flat).contains("'ALTER TABLE OPERATOR_DOMAIN_SET_GRANT DROP CHECK CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE'");
        assertThat(flat).contains("INFORMATION_SCHEMA.CHECK_CONSTRAINTS");
        assertThat(flat).contains("CONSTRAINT_NAME = 'CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE' "
                + "AND CHECK_CLAUSE NOT LIKE '%ORIGIN%') > 0");
        assertThat(flat.split("DROP CHECK", -1)).as("the only drop").hasSize(2);
        assertThat(flat.indexOf("DROP CHECK"))
                .as("dropped before it is re-added")
                .isLessThan(flat.indexOf("ADD CONSTRAINT CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE"));
    }

    @Test
    @DisplayName("re-add: DIRECT · MIGRATION · ORIGIN, or GROUP:% off '*' — the DomainGrantSource constants")
    void sourceVocabulary() {
        assertThat(flat()).contains("ADD CONSTRAINT CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE CHECK (SOURCE_KEY IN ("
                + "''DIRECT'', ''MIGRATION'', ''ORIGIN'') OR (SOURCE_KEY LIKE ''GROUP:%'' AND TENANT_ID <> ''*''))");
        assertThat(List.of(DomainGrantSource.DIRECT, DomainGrantSource.MIGRATION, DomainGrantSource.ORIGIN))
                .containsExactly("DIRECT", "MIGRATION", "ORIGIN");
        assertThat(DomainGrantSource.GROUP_PREFIX).isEqualTo("GROUP:");
        assertThat(flat()).contains("TABLE_NAME = 'OPERATOR_DOMAIN_SET_GRANT' "
                + "AND CONSTRAINT_NAME = 'CK_OPERATOR_DOMAIN_SET_GRANT_SOURCE') = 0");
    }

    @Test
    @DisplayName("ORIGIN off '*' is its own guarded CHECK; no CHECK references group_origin (ER 3823)")
    void originNeverPlatform() {
        String flat = flat();
        assertThat(flat).contains("ADD CONSTRAINT CK_OPERATOR_DOMAIN_SET_GRANT_ORIGIN_NOT_PLATFORM CHECK "
                + "(SOURCE_KEY <> ''ORIGIN'' OR TENANT_ID <> ''*'')");
        assertThat(flat).contains("CONSTRAINT_NAME = 'CK_OPERATOR_DOMAIN_SET_GRANT_ORIGIN_NOT_PLATFORM') = 0");
        Matcher m = Pattern.compile("CHECK \\(([^']|'')*'").matcher(flat);
        int checks = 0;
        while (m.find()) {
            checks++;
            assertThat(m.group()).doesNotContain("GROUP_ORIGIN");
        }
        assertThat(checks).isEqualTo(2);
    }

    @Test
    @DisplayName("no data change, no rollback; each user variable is SET once and read only by its own PREPARE")
    void schemaOnly() {
        String flat = flat();
        assertThat(flat)
                .doesNotContain("INSERT ")
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE ")
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX")
                .doesNotContain("DROP FOREIGN KEY");
        Matcher m = Pattern.compile("@([a-z_]+)").matcher(body());
        Map<String, Integer> uses = new TreeMap<>();
        while (m.find()) {
            uses.merge(m.group(1), 1, Integer::sum);
        }
        assertThat(uses).hasSize(3);
        uses.forEach((var, n) -> {
            assertThat(n).as("@%s: SET + PREPARE only", var).isEqualTo(2);
            assertThat(body()).contains("PREPARE stmt_" + var.substring("ddl_".length()) + " FROM @" + var);
        });
    }

    @Test
    @DisplayName("Flyway: no other admin-service migration (main or dev) uses version 0054")
    void versionIsUnique() throws IOException, URISyntaxException {
        URL main = V0054MigrationShapeTest.class.getResource("/db/migration");
        assertThat(main).isNotNull();
        List<Path> dirs = new java.util.ArrayList<>(List.of(Path.of(main.toURI())));
        URL dev = V0054MigrationShapeTest.class.getResource("/db/migration-dev");
        if (dev != null) {
            dirs.add(Path.of(dev.toURI()));
        }
        long v0054 = 0;
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.list(dir)) {
                v0054 += files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V0054__")).count();
            }
        }
        assertThat(v0054).isEqualTo(1);
    }
}
