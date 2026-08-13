package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-514 (ADR-MONO-061 option C): guards {@link WorkloadRoleCatalog} against the
 * Flyway seeds it is supposed to enumerate.
 *
 * <p><b>Why a migration-parsing test and not a hand-kept list.</b> The ADR's binding clause is
 * that every {@code client_credentials} client has an <em>explicit</em> role decision and that
 * an unlisted client receives none. The second half is guaranteed by the code
 * ({@code getOrDefault(..., List.of())}) and needs no test. The first half is guaranteed by
 * nothing — a workload client registered next month gets the safe default and silently
 * becomes a client nobody decided about. That is the exact failure this repo keeps re-finding:
 * a declaration nothing compares against the machine truth (MONO-345 service map, -352 error
 * registry, -360 gateway declarations, -363 ADR index, -371 JWT claims). So the catalog is
 * compared against the seeds, in <b>both</b> directions.
 *
 * <p><b>Non-vacuity is structural here.</b> A parser that finds nothing cannot pass: the
 * assertion is set <em>equality</em> against a non-empty catalog, so an empty parse fails
 * loudly rather than reporting agreement with nobody. That is deliberate — a detector's zero
 * is not evidence of absence.
 */
class WorkloadRoleCatalogTest {

    /**
     * Resolved from the module directory (Gradle's test working directory) with an explicit
     * walk up to the module root, so the test does not depend on where it is invoked from.
     */
    private static final String MIGRATION_DIR = "src/main/resources/db/migration";

    // -----------------------------------------------------------------------
    // The catalog vs the seeds
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("every seeded client_credentials client has an explicit catalog entry, and the catalog names no client that is not seeded")
    void catalogEnumeratesExactlyTheSeededWorkloadClients() {
        Map<String, String> registered = parseRegisteredClients();

        List<String> seededWorkloadClients = registered.entrySet().stream()
                .filter(e -> e.getValue().contains("client_credentials"))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        // Equality, not containment. A missing entry means a workload client nobody decided
        // about; a stale entry means a decision about a client that no longer exists, which
        // reads as coverage it does not have.
        assertThat(WorkloadRoleCatalog.enumeratedClientIds())
                .containsExactlyInAnyOrderElementsOf(seededWorkloadClients);
    }

    @Test
    @DisplayName("the parse is not vacuous — it recovers both grant families from the seeds")
    void migrationParseRecoversBothGrantFamilies() {
        Map<String, String> registered = parseRegisteredClients();

        long cc = registered.values().stream().filter(g -> g.contains("client_credentials")).count();
        long browser = registered.values().stream().filter(g -> g.contains("authorization_code")).count();

        // The control: if the parser matched everything (or nothing), one of these two is
        // wrong. Recounted at statement level 2026-08-13 — 16 clients, 10 cc, 6 browser.
        // membership-service-client is absent because V0029 revoked it, which is also the
        // only reason this test needs to honour DELETE at all.
        assertThat(registered).hasSize(16);
        assertThat(cc).isEqualTo(10);
        assertThat(browser).isEqualTo(6);
        assertThat(registered).doesNotContainKey("membership-service-client");
    }

    // -----------------------------------------------------------------------
    // What the catalog grants
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("the wms workload client receives MASTER_WRITE — the grant this ticket exists for")
    void wmsWorkloadClientReceivesMasterWrite() {
        assertThat(WorkloadRoleCatalog.rolesFor("wms-internal-services-client"))
                .containsExactly("MASTER_WRITE");
    }

    @Test
    @DisplayName("MASTER_ADMIN is NOT granted — deactivate/reactivate is a higher decision than 'create master data through the API'")
    void wmsWorkloadClientDoesNotReceiveMasterAdmin() {
        assertThat(WorkloadRoleCatalog.rolesFor("wms-internal-services-client"))
                .doesNotContain("MASTER_ADMIN");
    }

    @Test
    @DisplayName("no workload client holds an admin-tier role — TASK-MONO-522 has not answered whether workload identity may reach fan's admin surface")
    void noWorkloadClientHoldsAnAdminTierRole() {
        for (String clientId : WorkloadRoleCatalog.enumeratedClientIds()) {
            assertThat(WorkloadRoleCatalog.rolesFor(clientId))
                    .as("client %s", clientId)
                    .doesNotContainAnyElementsOf(WorkloadRoleCatalog.ADMIN_TIER_ROLES);
        }
    }

    @Test
    @DisplayName("exactly one workload client is granted anything — the change is a no-op for the other nine")
    void exactlyOneWorkloadClientIsGrantedAnything() {
        List<String> granted = WorkloadRoleCatalog.enumeratedClientIds().stream()
                .filter(id -> !WorkloadRoleCatalog.rolesFor(id).isEmpty())
                .sorted()
                .toList();

        // This is the blast-radius assertion. The roles claim is read by 19 services across 6
        // projects; the ADR's fail-closed default is what keeps that number from being the
        // reach of this change. Widening it should require editing this line.
        assertThat(granted).containsExactly("wms-internal-services-client");
    }

    @Test
    @DisplayName("an unknown, null or blank client id yields no roles")
    void unknownClientYieldsNoRoles() {
        assertThat(WorkloadRoleCatalog.rolesFor("some-client-registered-tomorrow")).isEmpty();
        assertThat(WorkloadRoleCatalog.rolesFor(null)).isEmpty();
        assertThat(WorkloadRoleCatalog.rolesFor("   ")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Migration parsing
    // -----------------------------------------------------------------------

    /**
     * Replays the {@code oauth_clients} seeds in migration order and returns the surviving
     * rows as {@code client_id -> authorization_grant_types}. Statement-level, because a seed
     * spans many lines; DELETE-aware, because a line-level or insert-only reading reports rows
     * the database does not have.
     */
    private static Map<String, String> parseRegisteredClients() {
        Path dir = resolveMigrationDir();
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted((a, b) -> Integer.compare(version(a), version(b)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(files).as("migration files under %s", dir).isNotEmpty();

        Map<String, String> clients = new LinkedHashMap<>();
        Pattern deleteById = Pattern.compile("client_id\\s*=\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);

        for (Path f : files) {
            String sql = read(f).replaceAll("(?m)--.*$", "");
            for (String raw : sql.split(";")) {
                String st = raw.trim();
                if (st.isEmpty()) {
                    continue;
                }
                String upper = st.toUpperCase(Locale.ROOT);
                if (upper.startsWith("INSERT INTO OAUTH_CLIENTS")) {
                    parseInsert(st, clients);
                } else if (upper.startsWith("DELETE FROM OAUTH_CLIENTS")) {
                    Matcher m = deleteById.matcher(st);
                    if (m.find()) {
                        clients.remove(m.group(1));
                    }
                }
            }
        }
        return clients;
    }

    private static void parseInsert(String statement, Map<String, String> out) {
        int valuesAt = indexOfIgnoreCase(statement, "VALUES");
        if (valuesAt < 0) {
            return;
        }
        int colsOpen = statement.indexOf('(');
        if (colsOpen < 0 || colsOpen > valuesAt) {
            return;
        }
        List<String> cols = new ArrayList<>();
        for (String c : statement.substring(colsOpen + 1, statement.lastIndexOf(')', valuesAt)).split(",")) {
            cols.add(c.trim().toLowerCase(Locale.ROOT));
        }
        int idIdx = cols.indexOf("client_id");
        int grantIdx = cols.indexOf("authorization_grant_types");
        if (idIdx < 0 || grantIdx < 0) {
            return;
        }

        for (String tuple : topLevelTuples(statement.substring(valuesAt + "VALUES".length()))) {
            List<String> vals = topLevelSplit(tuple);
            if (vals.size() <= Math.max(idIdx, grantIdx)) {
                continue;
            }
            out.put(unquote(vals.get(idIdx)), unquote(vals.get(grantIdx)));
        }
    }

    /** Splits a VALUES body into its top-level {@code (...)} tuples, string-literal aware. */
    private static List<String> topLevelTuples(String body) {
        List<String> tuples = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'') {
                inString = !inString;
            }
            if (!inString) {
                if (ch == '(') {
                    depth++;
                    if (depth == 1) {
                        cur.setLength(0);
                        continue;
                    }
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        tuples.add(cur.toString());
                        continue;
                    }
                }
            }
            if (depth >= 1) {
                cur.append(ch);
            }
        }
        return tuples;
    }

    /** Splits one tuple on its top-level commas, string-literal aware. */
    private static List<String> topLevelSplit(String tuple) {
        List<String> vals = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < tuple.length(); i++) {
            char ch = tuple.charAt(i);
            if (ch == '\'') {
                inString = !inString;
            }
            if (!inString) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (ch == ',' && depth == 0) {
                    vals.add(cur.toString().trim());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(ch);
        }
        vals.add(cur.toString().trim());
        return vals;
    }

    private static String unquote(String v) {
        String t = v.trim();
        if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toUpperCase(Locale.ROOT).indexOf(needle.toUpperCase(Locale.ROOT));
    }

    private static int version(Path p) {
        String name = p.getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Locates the migration directory relative to the module root. Gradle runs tests with the
     * module directory as the working directory; the walk up tolerates a runner that does not.
     * Failing to find it is an error rather than an empty result — a guard whose input silently
     * became empty reports agreement it never checked.
     */
    private static Path resolveMigrationDir() {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && cur != null; i++, cur = cur.getParent()) {
            Path candidate = cur.resolve(MIGRATION_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "cannot locate " + MIGRATION_DIR + " from " + Paths.get("").toAbsolutePath()
                        + " — refusing to report catalog/seed agreement without reading the seeds");
    }
}
