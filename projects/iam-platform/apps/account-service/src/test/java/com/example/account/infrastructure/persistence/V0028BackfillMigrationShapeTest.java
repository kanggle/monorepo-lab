package com.example.account.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-493 / ADR-MONO-047 D7 — non-Docker shape-pin for
 * {@code V0028__backfill_org_node_per_tenant.sql}.
 *
 * <p>Every assertion here is one of the task's <b>Failure Scenarios</b>, turned into a
 * syntactic invariant. They are pinned in <i>text</i> rather than only in
 * {@code OrgNodeBackfillIntegrationTest} for two reasons:
 *
 * <ol>
 *   <li><b>They run without Docker.</b> The integration proof needs Testcontainers, which is
 *       skipped on the plain {@code :test} task (and is unreliable on local Windows). A
 *       migration that enumerates today's domains would then reach review unchallenged on
 *       every machine that cannot start a container.</li>
 *   <li><b>They survive an edit to the integration test.</b> "The ceiling is UNBOUNDED" is a
 *       behavioural assertion someone can weaken while keeping the suite green. "The file
 *       contains no domain-key literal, ever" is not.</li>
 * </ol>
 *
 * <p>The one that matters most is {@link #neverEnumeratesTodaysDomains()}. A ceiling written
 * as {@code 'wms,scm,erp,finance,…'} is indistinguishable from UNBOUNDED today and denies
 * every legacy tenant the next domain we add — a bug that surfaces months later as "why
 * can't acme-corp subscribe to the new domain".
 */
@DisplayName("V0028 backfill — shape guards (no Docker)")
class V0028BackfillMigrationShapeTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V0028__backfill_org_node_per_tenant.sql";

    /**
     * The deterministic node id, recomputed identically by the INSERT and the UPDATE. If the
     * two ever drift, the UPDATE writes an org_node_id with no matching org_node row and
     * {@code fk_tenants_org_node} aborts the migration — loud, not silent. This pin makes the
     * drift fail at build time instead.
     */
    private static final Pattern NODE_ID_EXPRESSION = Pattern.compile(
            Pattern.quote("CONCAT('00000000-0000-5000-8000-', "
                    + "RIGHT(SHA2(CONCAT('adr047:org-node:', t.tenant_id), 256), 12))"));

    private static String sql;

    /** The SQL body with {@code --} comments stripped: the guards must inspect code, not prose. */
    private static String code;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0028BackfillMigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("migration resource %s must exist", MIGRATION_RESOURCE).isNotNull();
            // Normalise CRLF: a Windows checkout (or a .gitattributes eol=crlf) would otherwise
            // break every multi-line pattern below on one platform and not the other.
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        code = stripLineComments(sql);
    }

    /** Drops everything from a {@code --} to end-of-line. No string literal here contains {@code --}. */
    private static String stripLineComments(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            int comment = line.indexOf("--");
            out.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return out.toString();
    }

    private static int countOf(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    // ── AC-2 / Failure Scenario 1: the ceiling is the identity, never an enumeration ──

    @Test
    @DisplayName("AC-2: the backfilled ceiling is UNBOUNDED — the intersection identity")
    void backfillsAnUnboundedCeiling() {
        assertThat(code)
                .as("the backfilled node must carry ceiling_mode = 'UNBOUNDED'")
                .contains("'UNBOUNDED'");

        // 'UNBOUNDED' does not contain the QUOTED token 'BOUNDED' (the leading quote precedes
        // the U), so this catches a BOUNDED literal without a false positive on UNBOUNDED.
        assertThat(code)
                .as("a BOUNDED ceiling — even BOUNDED(∅) — would make the backfill deny something")
                .doesNotContain("'BOUNDED'");
    }

    @Test
    @DisplayName("AC-2: the migration never enumerates today's domain keys (UNBOUNDED ≠ 'all known domains')")
    void neverEnumeratesTodaysDomains() {
        // Word boundaries, NOT `'(wms|…)'`. A ceiling is stored as a CSV inside ONE literal —
        // `ceiling_domains = 'wms,scm,erp,finance'` — so a pattern demanding a quote on each
        // side of the domain misses the exact regression this test exists to catch. (It did:
        // that version of this assertion passed against a deliberately enumerated ceiling.)
        // The migration's real SQL mentions no domain key anywhere, so a bare word match here
        // has nothing to false-positive on.
        Matcher enumerated = Pattern
                .compile("\\b(wms|scm|erp|finance|ecommerce|fan|iam)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(code);
        String found = enumerated.find() ? enumerated.group() : null;

        assertThat(found)
                .as("found the domain literal %s — an enumerated ceiling is indistinguishable from "
                        + "UNBOUNDED today and silently excludes every domain added after this "
                        + "migration. UNBOUNDED means NO ceiling.", found)
                .isNull();
    }

    // ── AC-6 / Failure Scenario 2: idempotence ───────────────────────────────────────

    @Test
    @DisplayName("AC-6: both statements are guarded by `org_node_id IS NULL`, so a re-run is a zero-row no-op")
    void isIdempotentByConstruction() {
        assertThat(countOf(code, "t.org_node_id IS NULL"))
                .as("the INSERT and the UPDATE must BOTH skip already-grouped tenants; without the "
                        + "guard a re-run duplicates every node and re-parents every tenant")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the node id is deterministic and recomputed identically by both statements")
    void nodeIdExpressionIsDuplicatedVerbatim() {
        Matcher m = NODE_ID_EXPRESSION.matcher(code);
        int occurrences = 0;
        while (m.find()) {
            occurrences++;
        }
        assertThat(occurrences)
                .as("the INSERT computes the node id and the UPDATE must recompute the SAME one; "
                        + "UUID() would be non-deterministic and a join on display_name would merge "
                        + "two same-named companies into one node")
                .isEqualTo(2);
    }

    // ── Failure Scenario 3: the ungrouped escape hatch must survive ──────────────────

    @Test
    @DisplayName("Failure Scenario 3: the migration never promotes org_node_id to NOT NULL, and alters no schema")
    void neverTouchesSchema() {
        assertThat(code)
                .as("`org_node_id` NULL = ungrouped singleton is a legal PERMANENT state (D7). "
                        + "Promoting it to NOT NULL kills the lazy-migration escape hatch and the "
                        + "NULL-safe code paths BE-491 built.")
                .doesNotContain("NOT NULL")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("MODIFY");
    }

    // ── AC-4: nothing else is touched ────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4: forward-only — no DROP/DELETE, and no table other than org_node/tenants is written")
    void touchesOnlyOrgNodeAndTenants() {
        assertThat(code)
                .as("a backfill must never destroy rows")
                .doesNotContain("DROP")
                .doesNotContain("DELETE")
                .doesNotContain("TRUNCATE");

        assertThat(code)
                .as("subscriptions carry the entitlement state; rewriting one would make this "
                        + "migration a behaviour change rather than a grouping")
                .doesNotContain("tenant_domain_subscription");

        assertThat(code)
                .as("admin_operator_roles lives in the admin-service schema — an account-service "
                        + "migration must not reach for it")
                .doesNotContain("admin_operator_roles");
    }

    @Test
    @DisplayName("AC-1/AC-2: the INSERT's column list and the SELECT's projection line up positionally")
    void insertsRootNodesNamedAfterTheTenant() {
        // The SELECT is positional. Reordering the column list without reordering the
        // projection can still type-check in MySQL (name/ceiling_mode/ceiling_domains are all
        // strings) and would write a node named 'UNBOUNDED' with the display_name as its
        // ceiling mode. Pin both, together.
        assertThat(code)
                .as("the INSERT column list is load-bearing: the SELECT below it is positional")
                .contains("INSERT INTO org_node "
                        + "(id, parent_id, name, ceiling_mode, ceiling_domains, depth, created_at, updated_at)");

        assertThat(code)
                .as("projection after the node id must be: parent NULL, name = the tenant's "
                        + "display_name (the node is the COMPANY, the tenant is the SERVICE), "
                        + "ceiling UNBOUNDED with an empty domain CSV, depth 1 (a root)")
                .containsPattern("NULL,\\s*t\\.display_name,\\s*'UNBOUNDED',\\s*'',\\s*1,");
    }
}
