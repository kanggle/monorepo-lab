package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-581 AC-2 — every {@code oauth_clients.tenant_id} resolves to something known.
 *
 * <h2>The defect this exists to prevent recurring</h2>
 * {@code platform-console-web} pointed at {@code tenant_id='iam'}, for which
 * {@code account_db.tenants} has — correctly and permanently — no row. Nothing detected that,
 * because the reference crosses two databases owned by two services: a foreign key cannot
 * span them and the two migration chains run independently. The console's browser signup path
 * therefore returned {@code 404 TENANT_NOT_FOUND} 100% of the time, and the second dangling
 * value ({@code global-account-platform}) is still latent today.
 *
 * <h2>🔴 Why this runs Flyway instead of grepping the SQL</h2>
 * Measured before it was written: a static scan of the migration files finds <b>nine</b>
 * distinct {@code tenant_id} literals, one of which ({@code gap}) no row actually holds —
 * {@code V0024} renames it to {@code iam} with an {@code UPDATE}, which no grep replays. The
 * population a text scan reports is wrong in both directions, so the guard would be reasoning
 * about a database that does not exist. Applying the migrations and reading the tables is the
 * only way to obtain the real population.
 *
 * <h2>🔴 Why the tenant population is prod-only, and how that is proven rather than assumed</h2>
 * {@code account-service} seeds tenants from two locations: {@code db/migration} (production)
 * and {@code db/migration-dev} (demo/e2e only). A guard that reads a dev-seeded database is
 * <b>green while production is broken</b> — the dev-only tenants would satisfy references that
 * production cannot. So the verdict is computed against production migrations alone.
 *
 * <p>That claim is not left as a comment: {@link #devOnlyTenantsExistAndAreExcluded()} applies
 * the dev migrations to a <i>separate</i> database and derives the dev-only set by difference.
 * If the two locations ever collapse into one, that control goes empty and fails, rather than
 * silently turning this guard into the dev-seeded version it was written to avoid.
 *
 * <h2>Scope — the other half of AC-1 lives elsewhere</h2>
 * This is the <b>reference-integrity</b> axis: does every client point at something real or
 * knowingly-reserved. It says nothing about whether a browser can reach an impossible signup;
 * that is {@code LoginPageSignupLinkSliceTest} + {@code SignupPageBlockedSliceTest}. Both axes
 * are required — a reserved slug is legitimately dangling forever, so this guard must not fail
 * on {@code iam}, which means it alone would be green on the reported defect.
 *
 * <p><b>Runner</b>: {@code @Tag("integration")} → CI job
 * {@code Integration (iam <shard>, Testcontainers)}, shard B
 * ({@code :projects:iam-platform:apps:auth-service:integrationTest}).
 */
class OAuthClientTenantReferenceIntegrationTest extends AbstractIntegrationTest {

    /** Client-row {@code tenant_type} marking a GAP-internal workload identity (V0019). */
    private static final String INTERNAL_TENANT_TYPE = "INTERNAL";

    private static final String AUTH_DB = "be581_auth";
    private static final String ACCOUNT_PROD_DB = "be581_account_prod";
    private static final String ACCOUNT_DEV_DB = "be581_account_dev";

    /** client_id -> row. */
    private static Map<String, ClientRow> clients;
    /** tenant_id set from production migrations only. */
    private static Set<String> prodTenants;
    /** tenant_id set from production + dev migrations. */
    private static Set<String> devTenants;
    /** Reserved slugs, read from the code that enforces them. */
    private static Set<String> reservedSlugs;

    private record ClientRow(String clientId, String tenantId, String tenantType, String grants) {
        boolean isInternalWorkload() {
            return INTERNAL_TENANT_TYPE.equalsIgnoreCase(tenantType);
        }

        boolean isBrowserReachable() {
            return grants != null && grants.contains("authorization_code");
        }
    }

    @BeforeAll
    static void migrateAndRead() throws Exception {
        Path repoRoot = repoRoot();
        Path authMigrations = repoRoot.resolve(
                "projects/iam-platform/apps/auth-service/src/main/resources/db/migration");
        Path accountMigrations = repoRoot.resolve(
                "projects/iam-platform/apps/account-service/src/main/resources/db/migration");
        Path accountDevMigrations = repoRoot.resolve(
                "projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev");

        // Fail loudly rather than silently measuring an empty directory — a moved path must not
        // read as "nothing dangling".
        assertThat(authMigrations).as("auth-service production migrations").isDirectory();
        assertThat(accountMigrations).as("account-service production migrations").isDirectory();
        assertThat(accountDevMigrations).as("account-service dev migrations").isDirectory();

        createDatabase(AUTH_DB);
        createDatabase(ACCOUNT_PROD_DB);
        createDatabase(ACCOUNT_DEV_DB);

        migrate(AUTH_DB, authMigrations);
        migrate(ACCOUNT_PROD_DB, accountMigrations);
        migrate(ACCOUNT_DEV_DB, accountMigrations, accountDevMigrations);

        clients = readClients(AUTH_DB);
        prodTenants = readTenants(ACCOUNT_PROD_DB);
        devTenants = readTenants(ACCOUNT_DEV_DB);
        reservedSlugs = readReservedSlugs(repoRoot);
    }

    // ------------------------------------------------------------------
    // Extraction controls — a guard that measured nothing must not pass.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONTROL — every population is non-empty and the migrations really ran")
    void populationsAreNonEmpty() {
        assertThat(clients).as("oauth_clients rows after auth-service migrations").isNotEmpty();
        assertThat(prodTenants).as("tenants rows after account-service prod migrations").isNotEmpty();
        assertThat(reservedSlugs).as("reserved slugs parsed from CreateTenantUseCase").isNotEmpty();

        // The rename this guard exists to survive: V0024 turned 'gap' into 'iam'. If 'gap' shows
        // up here, the migrations were not applied and something is reading the files instead.
        assertThat(tenantIdsInUse())
                .as("`gap` is a pre-V0024 literal, not a live value — seeing it means the "
                        + "population came from text, not from a migrated database")
                .doesNotContain("gap");
        assertThat(tenantIdsInUse()).contains("iam");
    }

    @Test
    @DisplayName("CONTROL — the dev-only tenants exist and are NOT in the population this "
            + "guard judges against")
    void devOnlyTenantsExistAndAreExcluded() {
        Set<String> devOnly = new TreeSet<>(devTenants);
        devOnly.removeAll(prodTenants);

        assertThat(devOnly)
                .as("account-service seeds demo/e2e tenants from db/migration-dev. If this set "
                        + "is empty the two locations have merged, and this guard has silently "
                        + "become the dev-seeded version it was written not to be")
                .isNotEmpty();
        assertThat(prodTenants)
                .as("the verdict population must contain no dev-only tenant")
                .doesNotContainAnyElementsOf(devOnly);
    }

    // ------------------------------------------------------------------
    // The verdict.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Every oauth_clients.tenant_id is a real tenant, a reserved slug, or an "
            + "INTERNAL workload sentinel — nothing else")
    void noDanglingTenantReference() {
        Map<String, List<String>> unknown = new LinkedHashMap<>();
        for (ClientRow row : clients.values()) {
            if (classify(row) == Classification.UNKNOWN) {
                unknown.computeIfAbsent(row.tenantId(), k -> new ArrayList<>()).add(row.clientId());
            }
        }

        assertThat(unknown)
                .as("""
                        An oauth_clients row points at a tenant_id that has no tenants row, is \
                        not a reserved slug, and is not an INTERNAL workload sentinel. Every \
                        browser flow started by such a client creates accounts in a tenant that \
                        does not exist — signup returns 404 TENANT_NOT_FOUND, permanently. \
                        Either seed the tenant, or reserve the slug and gate the browser signup \
                        path for it (TASK-BE-581).
                        Full classification: %s""".formatted(classificationReport()))
                .isEmpty();
    }

    @Test
    @DisplayName("A reserved or INTERNAL tenant_id genuinely has no tenants row — the "
            + "reservation is real, not merely asserted")
    void reservedAndInternalSlugsHaveNoTenantRow() {
        Set<String> contradictions = new TreeSet<>();
        for (String tenantId : tenantIdsInUse()) {
            Classification c = classifyTenantId(tenantId);
            if ((c == Classification.RESERVED || c == Classification.INTERNAL)
                    && prodTenants.contains(tenantId)) {
                contradictions.add(tenantId);
            }
        }

        assertThat(contradictions)
                .as("""
                        A slug is classified as reserved/INTERNAL AND has a tenants row. Those \
                        two facts cannot both be true: V0024 and multi-tenancy.md reserve `iam` \
                        precisely so no consumer can register it, and CreateTenantUseCase \
                        returns 400 TENANT_ID_RESERVED for it. Seeding it anyway (the option \
                        TASK-BE-581 AC-0 rejected) would make this fire.""")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-3 — global-account-platform classifies as an INTERNAL workload sentinel, "
            + "and no INTERNAL client can reach a browser flow")
    void internalWorkloadSentinelIsClassifiedAndUnreachableFromABrowser() {
        List<ClientRow> internalClients = clients.values().stream()
                .filter(ClientRow::isInternalWorkload)
                .toList();

        assertThat(internalClients)
                .as("V0019 seeds four client_credentials workload clients with "
                        + "tenant_type=INTERNAL; if none are found the classifier's input changed")
                .isNotEmpty();
        assertThat(internalClients).allSatisfy(row ->
                assertThat(classify(row))
                        .as("client %s", row.clientId())
                        .isEqualTo(Classification.INTERNAL));

        // "It did not blow up on this round trip" is not evidence. This is what keeps
        // global-account-platform latent: the moment one of these clients gains a browser grant
        // it becomes today's `iam` — a signup surface pointed at a tenant that does not exist.
        List<String> browserReachable = internalClients.stream()
                .filter(ClientRow::isBrowserReachable)
                .map(ClientRow::clientId)
                .toList();
        assertThat(browserReachable)
                .as("""
                        An INTERNAL workload client gained an authorization_code grant. Its \
                        tenant_id (e.g. global-account-platform) has no tenants row by design \
                        (V0019: "GAP platform infrastructure, not bound to a product tenant"), \
                        so a browser flow started by it hits exactly the TASK-BE-581 defect. \
                        Either give the client a real tenant or keep it out of browser flows.""")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // AC-2 control group: does the guard actually bite?
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CONTROL — a client row with a nonexistent tenant_id, INSERTED into the "
            + "migrated database, is reported as dangling by the same read+classify path")
    void injectedDanglingClientIsDetected() throws SQLException {
        // 🔴 The weak version of this control calls classify() on a hand-built record. That
        // proves the if-statement, not the guard: it never touches the SQL projection, so a
        // SELECT that dropped tenant_id, or a read that silently returned zero rows, would
        // still let it pass. Inject into the database the verdict reads, then re-read.
        String injectedClientId = "be581-control-dangling-client";
        try {
            insertClient(injectedClientId, "no-such-tenant-be581", "B2C");

            Map<String, ClientRow> reread = readClients(AUTH_DB);
            assertThat(reread)
                    .as("the injection must be visible to the reader before its verdict means "
                            + "anything — 'did not bite' is meaningless if nothing was injected")
                    .containsKey(injectedClientId);

            List<String> dangling = reread.values().stream()
                    .filter(r -> classify(r) == Classification.UNKNOWN)
                    .map(ClientRow::clientId)
                    .toList();

            assertThat(dangling)
                    .as("the guard must fail on an unknown tenant_id; if this is empty the "
                            + "predicate is wrong and the guard protects nothing")
                    .contains(injectedClientId);
        } finally {
            deleteClient(injectedClientId);
        }

        // And the shapes that must NOT be reported as dangling, so the predicate is not simply
        // "everything is unknown" — a guard that fires on all four buckets is equally useless.
        assertThat(classify(new ClientRow("c", "wms", "B2B_ENTERPRISE", "[]")))
                .isEqualTo(Classification.EXISTS);
        assertThat(classify(new ClientRow("c", "iam", "B2B_ENTERPRISE", "[]")))
                .isEqualTo(Classification.RESERVED);
        assertThat(classify(new ClientRow("c", "global-account-platform", "INTERNAL", "[]")))
                .isEqualTo(Classification.INTERNAL);
    }

    private static void insertClient(String clientId, String tenantId, String tenantType)
            throws SQLException {
        try (Connection c = connect(AUTH_DB);
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO oauth_clients (
                         id, client_id, tenant_id, tenant_type, client_name,
                         client_authentication_methods, authorization_grant_types,
                         redirect_uris, scopes, client_settings, token_settings)
                     VALUES (?, ?, ?, ?, ?, '["none"]', '["authorization_code"]',
                             JSON_ARRAY(), '["openid"]', JSON_OBJECT(), JSON_OBJECT())""")) {
            ps.setString(1, clientId + "-id");
            ps.setString(2, clientId);
            ps.setString(3, tenantId);
            ps.setString(4, tenantType);
            ps.setString(5, "TASK-BE-581 control row");
            ps.executeUpdate();
        }
    }

    private static void deleteClient(String clientId) throws SQLException {
        try (Connection c = connect(AUTH_DB);
             PreparedStatement ps =
                     c.prepareStatement("DELETE FROM oauth_clients WHERE client_id = ?")) {
            ps.setString(1, clientId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    private enum Classification { EXISTS, RESERVED, INTERNAL, UNKNOWN }

    private static Classification classify(ClientRow row) {
        if (prodTenants.contains(row.tenantId())) {
            return Classification.EXISTS;
        }
        if (reservedSlugs.contains(row.tenantId())) {
            return Classification.RESERVED;
        }
        if (row.isInternalWorkload()) {
            return Classification.INTERNAL;
        }
        return Classification.UNKNOWN;
    }

    /** Classification of a bare tenant_id, using any client row that carries it. */
    private static Classification classifyTenantId(String tenantId) {
        return clients.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .map(OAuthClientTenantReferenceIntegrationTest::classify)
                .findFirst()
                .orElse(Classification.UNKNOWN);
    }

    private static Set<String> tenantIdsInUse() {
        Set<String> ids = new TreeSet<>();
        clients.values().forEach(r -> ids.add(r.tenantId()));
        return ids;
    }

    private static String classificationReport() {
        Map<String, String> report = new LinkedHashMap<>();
        tenantIdsInUse().forEach(t -> report.put(t, classifyTenantId(t).name()));
        return report.toString();
    }

    // ------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------

    /**
     * Reads the reserved slugs from {@code CreateTenantUseCase} — the code that actually returns
     * {@code 400 TENANT_ID_RESERVED}.
     *
     * <p>Deliberately not a literal list in this file. A copy would answer "is this one of the
     * values I thought about when I wrote the guard", and would keep passing after the real set
     * changed. It is cross-checked against {@code multi-tenancy.md} below so the enforced set
     * and the documented set cannot drift apart unnoticed.
     */
    private static Set<String> readReservedSlugs(Path repoRoot) throws IOException {
        Path source = repoRoot.resolve("projects/iam-platform/apps/admin-service/src/main/java/"
                + "com/example/admin/application/tenant/CreateTenantUseCase.java");
        assertThat(source).as("CreateTenantUseCase source").isRegularFile();

        String body = Files.readString(source, StandardCharsets.UTF_8);
        Matcher block = Pattern.compile("RESERVED\\s*=\\s*Set\\.of\\((.*?)\\);", Pattern.DOTALL)
                .matcher(body);
        assertThat(block.find()).as("RESERVED Set.of(...) in CreateTenantUseCase").isTrue();

        Set<String> slugs = new LinkedHashSet<>();
        Matcher word = Pattern.compile("\"([a-z][a-z0-9-]*)\"").matcher(block.group(1));
        while (word.find()) {
            slugs.add(word.group(1));
        }

        Path spec = repoRoot.resolve("projects/iam-platform/specs/features/multi-tenancy.md");
        String specText = Files.readString(spec, StandardCharsets.UTF_8);
        for (String slug : slugs) {
            assertThat(specText)
                    .as("reserved slug `%s` is enforced by CreateTenantUseCase but missing from "
                            + "multi-tenancy.md — the enforced set and the documented set have "
                            + "drifted, and readers of the spec would not know", slug)
                    .contains("`" + slug + "`");
        }
        return slugs;
    }

    private static Map<String, ClientRow> readClients(String database) throws SQLException {
        Map<String, ClientRow> rows = new LinkedHashMap<>();
        try (Connection c = connect(database);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT client_id, tenant_id, tenant_type, authorization_grant_types "
                             + "FROM oauth_clients ORDER BY client_id")) {
            while (rs.next()) {
                rows.put(rs.getString(1), new ClientRow(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return rows;
    }

    private static Set<String> readTenants(String database) throws SQLException {
        Set<String> ids = new TreeSet<>();
        try (Connection c = connect(database);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT tenant_id FROM tenants")) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    private static void createDatabase(String name) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + name);
            s.execute("CREATE DATABASE " + name
                    + " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
    }

    private static void migrate(String database, Path... locations) {
        String[] urls = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            urls[i] = "filesystem:" + locations[i].toAbsolutePath();
        }
        Flyway.configure()
                .dataSource(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(urls)
                .load()
                .migrate();
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static String jdbcUrl(String database) {
        // MYSQL.getJdbcUrl() targets the container's default database; swap the path segment.
        return MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(\\?|$)",
                "/" + database + "$1");
    }

    /**
     * Walks up from the working directory to the repository root.
     *
     * <p>Gradle's working directory for a {@code Test} task is the module directory, but that is
     * a default a build edit can change; anchoring on a marker keeps the guard from reading an
     * empty directory and reporting "nothing dangling".
     */
    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("projects/iam-platform/apps"))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("repository root (an ancestor containing projects/iam-platform/apps)")
                .isNotNull();
        return dir;
    }
}
