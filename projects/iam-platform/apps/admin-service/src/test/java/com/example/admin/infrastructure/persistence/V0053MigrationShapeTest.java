package com.example.admin.infrastructure.persistence;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
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
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-633 — non-Docker shape-pin for {@code V0053__admin_operator_roles_source_key.sql} (same form as
 * {@link V0051MigrationShapeTest}) plus the application half of the source invariant that MySQL cannot hold as a
 * CHECK ({@code source_key = 'GROUP:' || group_origin} — ER 3823, {@code group_origin} carries ON DELETE CASCADE).
 *
 * <p>What a green Testcontainers run on a FRESH database cannot catch, and this pins:
 * <ol>
 *   <li>The column is {@code NOT NULL DEFAULT 'DIRECT'} — every hand-written / seed INSERT that names no source
 *       keeps meaning «a direct grant» (R__ seeds, console-demo and e2e fixtures).</li>
 *   <li>The existing rows' sources are derived BEFORE the PK swap, from the columns that already recorded them
 *       (node wins, then group), and only rows still at the default with an origin are touched (re-run safe).</li>
 *   <li>The PK swap is one statement, guarded, {@code operator_id} leading, {@code source_key} fourth.</li>
 *   <li>The re-fan-out (Failure Scenario 1) exists, is NOT EXISTS-guarded on the full new key, lands in the
 *       GROUP's tenant, requires a membership there (784 D-1) and skips {@code '*'} operators.</li>
 *   <li>Forward-only: no DELETE, no DROP of a column / index / constraint; no cross-statement user variable.</li>
 *   <li>No other admin-service migration uses version 0053 (Flyway collision).</li>
 * </ol>
 * The behavioural checks are {@code V0053RoleSourceBackfillIntegrationTest} and
 * {@code AdminRoleSourceIntegrationTest}.
 */
class V0053MigrationShapeTest {

    private static final String MIGRATION_RESOURCE = "/db/migration/V0053__admin_operator_roles_source_key.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0053MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0053 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
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
    @DisplayName("column: source_key VARCHAR(48) NOT NULL DEFAULT 'DIRECT', guarded on information_schema.columns")
    void columnIsDefaultedDirect() {
        String flat = flat();
        assertThat(flat).contains("'ALTER TABLE ADMIN_OPERATOR_ROLES ADD COLUMN SOURCE_KEY VARCHAR(48) NOT NULL "
                + "DEFAULT ''DIRECT'' AFTER GROUP_ORIGIN'");
        assertThat(flat).contains("INFORMATION_SCHEMA.COLUMNS");
        assertThat(flat).contains("COLUMN_NAME = 'SOURCE_KEY') = 0");
    }

    @Test
    @DisplayName("sources of existing rows: node first, then group, only rows still at the default with an origin — before the PK swap")
    void existingRowsDeriveTheirSourceBeforeThePkSwap() {
        String flat = flat();
        String update = "UPDATE ADMIN_OPERATOR_ROLES SET SOURCE_KEY = CASE WHEN ORG_NODE_ID IS NOT NULL THEN 'NODE' "
                + "ELSE CONCAT('GROUP:', GROUP_ORIGIN) END WHERE SOURCE_KEY = 'DIRECT' "
                + "AND (ORG_NODE_ID IS NOT NULL OR GROUP_ORIGIN IS NOT NULL)";
        assertThat(flat).contains(update);
        assertThat(flat.indexOf(update))
                .as("derived while the key is still 3 columns (no two rows can collide)")
                .isLessThan(flat.indexOf("DROP PRIMARY KEY"));
        assertThat(flat.split("UPDATE ", -1)).as("the only UPDATE").hasSize(2);
    }

    @Test
    @DisplayName("PK: one guarded ALTER → (operator_id, role_id, tenant_id, source_key)")
    void pkSwap() {
        assertThat(body()).contains("'ALTER TABLE admin_operator_roles DROP PRIMARY KEY, "
                + "ADD PRIMARY KEY (operator_id, role_id, tenant_id, source_key)'");
        assertThat(flat().split("DROP PRIMARY KEY", -1)).hasSize(2);
        assertThat(flat()).contains("CONSTRAINT_NAME = 'PRIMARY' AND COLUMN_NAME = 'SOURCE_KEY') = 0");
    }

    @Test
    @DisplayName("CHECKs: the source vocabulary (GROUP never in '*') and NODE ⟺ org_node_id — none on group_origin (ER 3823)")
    void checks() {
        String flat = flat();
        assertThat(flat).contains("ADD CONSTRAINT CK_ADMIN_OPERATOR_ROLES_SOURCE CHECK (SOURCE_KEY IN (''DIRECT'', "
                + "''NODE'') OR (SOURCE_KEY LIKE ''GROUP:%'' AND TENANT_ID <> ''*''))");
        assertThat(flat).contains("ADD CONSTRAINT CK_ADMIN_OPERATOR_ROLES_NODE_SOURCE CHECK ((SOURCE_KEY = ''NODE'') = "
                + "(ORG_NODE_ID IS NOT NULL))");
        // no CHECK may reference group_origin: MySQL refuses it (FK with a referential action — ER 3823)
        Matcher m = Pattern.compile("CHECK \\(([^']|'')*'").matcher(flat);
        while (m.find()) {
            assertThat(m.group()).doesNotContain("GROUP_ORIGIN");
        }
    }

    @Test
    @DisplayName("re-fan-out: ROLE templates × members → GROUP:<id> row in the group's tenant, NOT EXISTS on the full key, member of that tenant, no '*'")
    void reFanOut() {
        String flat = flat();
        assertThat(flat).contains("INSERT INTO ADMIN_OPERATOR_ROLES (OPERATOR_ID, ROLE_ID, TENANT_ID, GRANTED_AT, "
                + "GRANTED_BY, ORG_NODE_ID, GROUP_ORIGIN, SOURCE_KEY) SELECT M.OPERATOR_ID, GG.ROLE_ID, G.TENANT_ID, "
                + "NOW(6), GG.GRANTED_BY, NULL, G.ID, CONCAT('GROUP:', G.ID)");
        assertThat(flat).contains("WHERE GG.GRANT_TYPE = 'ROLE'");
        assertThat(flat).contains("AND O.TENANT_ID <> '*'");
        assertThat(flat).contains("AND EXISTS ( SELECT 1 FROM OPERATOR_TENANT_ASSIGNMENT A "
                + "WHERE A.OPERATOR_ID = M.OPERATOR_ID AND A.TENANT_ID = G.TENANT_ID )");
        assertThat(flat).contains("AND NOT EXISTS ( SELECT 1 FROM (SELECT OPERATOR_ID, ROLE_ID, TENANT_ID, SOURCE_KEY "
                + "FROM ADMIN_OPERATOR_ROLES) X WHERE X.OPERATOR_ID = M.OPERATOR_ID AND X.ROLE_ID = GG.ROLE_ID "
                + "AND X.TENANT_ID = G.TENANT_ID AND X.SOURCE_KEY = CONCAT('GROUP:', G.ID) )");
        assertThat(flat).doesNotContain("INSERT IGNORE").doesNotContain("REPLACE INTO").doesNotContain("ON DUPLICATE KEY");
    }

    @Test
    @DisplayName("forward-only: no DELETE, no DROP of a column / index / FK / CHECK, no rollback; no cross-statement user variable")
    void forwardOnly() {
        String flat = flat();
        assertThat(flat)
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX")
                .doesNotContain("DROP FOREIGN KEY")
                .doesNotContain("DROP CONSTRAINT")
                .doesNotContain("DROP CHECK")
                .doesNotContain("DELETE ");
        // each user variable is set once and read only by its own PREPARE (the self-contained quartet)
        Matcher m = Pattern.compile("@([a-z_]+)").matcher(body());
        java.util.Map<String, Integer> uses = new java.util.TreeMap<>();
        while (m.find()) {
            uses.merge(m.group(1), 1, Integer::sum);
        }
        assertThat(uses).isNotEmpty();
        uses.forEach((var, n) -> {
            assertThat(n).as("@%s: SET + PREPARE only", var).isEqualTo(2);
            assertThat(body()).contains("PREPARE stmt_" + var.substring("ddl_".length()) + " FROM @" + var);
        });
    }

    @Test
    @DisplayName("Flyway: no other admin-service migration (main or dev) uses version 0053")
    void versionIsUnique() throws IOException, URISyntaxException {
        URL main = V0053MigrationShapeTest.class.getResource("/db/migration");
        assertThat(main).isNotNull();
        List<Path> dirs = new java.util.ArrayList<>(List.of(Path.of(main.toURI())));
        URL dev = V0053MigrationShapeTest.class.getResource("/db/migration-dev");
        if (dev != null) {
            dirs.add(Path.of(dev.toURI()));
        }
        long v0053 = 0;
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.list(dir)) {
                v0053 += files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V0053__")).count();
            }
        }
        assertThat(v0053).isEqualTo(1);
    }

    // ── the application half of the source invariant (the entity factories) ──

    @Test
    @DisplayName("entity: direct → DIRECT · node → NODE (with org_node_id) · group → GROUP:<group_origin> (with group_origin)")
    void factoriesPairSourceWithOrigin() {
        Instant now = Instant.EPOCH;
        AdminOperatorRoleJpaEntity direct = AdminOperatorRoleJpaEntity.create(1L, 2L, now, null, "acme");
        assertThat(direct.getSourceKey()).isEqualTo("DIRECT");
        assertThat(direct.getGroupOrigin()).isNull();
        assertThat(direct.getOrgNodeId()).isNull();
        assertThat(direct.isDirect()).isTrue();

        AdminOperatorRoleJpaEntity node = AdminOperatorRoleJpaEntity.createNodeScoped(1L, 2L, now, null, "acme", "n1");
        assertThat(node.getSourceKey()).isEqualTo("NODE");
        assertThat(node.getOrgNodeId()).isEqualTo("n1");
        assertThat(node.isDirect()).isFalse();

        AdminOperatorRoleJpaEntity group = AdminOperatorRoleJpaEntity.createGroupScoped(1L, 2L, now, null, "acme", 42L);
        assertThat(group.getSourceKey()).isEqualTo("GROUP:42").isEqualTo(AdminOperatorRoleJpaEntity.groupSource(42L));
        assertThat(group.getGroupOrigin()).isEqualTo(42L);
        assertThat(group.isDirect()).isFalse();

        assertThatThrownBy(() -> AdminOperatorRoleJpaEntity.groupSource(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat("GROUP:" + Long.MAX_VALUE).as("fits VARCHAR(48)").hasSizeLessThanOrEqualTo(48);
    }

    @Test
    @DisplayName("entity: the PK carries the source — the same (operator, role, tenant) from two sources are two keys")
    void pkCarriesTheSource() {
        var direct = new AdminOperatorRoleJpaEntity.PK(1L, 2L, "acme", "DIRECT");
        var group = new AdminOperatorRoleJpaEntity.PK(1L, 2L, "acme", "GROUP:42");
        assertThat(direct).isNotEqualTo(group);
        assertThat(direct).isEqualTo(new AdminOperatorRoleJpaEntity.PK(1L, 2L, "acme", "DIRECT"));
        assertThat(direct.hashCode()).isEqualTo(new AdminOperatorRoleJpaEntity.PK(1L, 2L, "acme", "DIRECT").hashCode());
    }
}
