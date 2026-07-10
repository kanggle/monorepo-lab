package com.example.account.integration;

import com.example.account.application.service.OrgNodeCommandUseCase;
import com.example.account.application.service.OrgNodeQueryUseCase;
import com.example.account.application.service.TenantEntitledDomainsQueryUseCase;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-493 (ADR-MONO-047 § D7, § 4 step 4): the org-node backfill is a
 * <b>proven</b> behavioural no-op, not an asserted one.
 *
 * <p>The migration itself is twelve lines of SQL. The substance of the task is this proof:
 * every tenant's resolved {@code entitled_domains} — the exact list auth-service turns into
 * the {@code entitled_domains} claim and derives the operator {@code roles} from — is
 * <b>byte-identical before and after</b> the backfill runs.
 *
 * <p><b>How the before/after snapshot is taken.</b> Flyway has already applied V0028 by the
 * time this context starts, so the test first restores the pre-migration state
 * ({@code org_node_id = NULL} for every tenant, empty {@code org_node}) — which is <i>exactly</i>
 * what the table looked like before V0028, not an approximation of it — snapshots, then
 * executes {@link #MIGRATION_RESOURCE} <b>read from the classpath</b> and snapshots again.
 * Reading the real file rather than re-typing its SQL is what keeps the proof and the
 * migration from drifting apart. Its two statements are connection-independent (no temporary
 * table, no session variable), so executing them one at a time here is equivalent to what
 * Flyway does at startup.
 *
 * <p><b>Why this also settles AC-5</b> (an assume-tenant token's {@code entitled_domains} and
 * derived {@code roles} are unchanged) without minting a token here: on the assume-tenant path
 * {@code TenantClaimTokenCustomizer} sets the claim to the list account-service returns and
 * computes {@code roles = OperatorRoleDerivation.fromEntitledDomains(thatSameList)} — a pure
 * function of it (pinned by {@code OperatorRoleDerivationTest}). An identical list therefore
 * yields an identical pair of claims. The cross-org path caps the same list against the
 * delegated scope, so it too is a function of it alone. AC-5 reduces to AC-3, proven below.
 *
 * <p>State is shared across test classes through one static MySQL container, so every test
 * restores what it touched (see {@link #restore()}).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ADR-MONO-047 D7 / TASK-BE-493 — the org-node backfill is a proven no-op")
class OrgNodeBackfillIntegrationTest extends AbstractIntegrationTest {

    /** The real migration, executed verbatim — never a hand-copy of its SQL. */
    private static final String MIGRATION_RESOURCE = "db/migration/V0028__backfill_org_node_per_tenant.sql";

    private static final String ACME = "acme-corp";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantEntitledDomainsQueryUseCase entitledDomainsUseCase;
    @Autowired private OrgNodeQueryUseCase orgNodeQueryUseCase;
    @Autowired private OrgNodeCommandUseCase orgNodeCommandUseCase;

    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;

    // ── migration execution ───────────────────────────────────────────────────────────

    /**
     * Splits the migration into executable statements. Line comments are dropped first (no
     * string literal in the file contains {@code --}), then the body is split on {@code ;} —
     * safe because no literal contains a semicolon either. Both properties are pinned by
     * {@code V0028BackfillMigrationShapeTest}.
     */
    private static List<String> migrationStatements() throws IOException {
        String raw = new String(new ClassPathResource(MIGRATION_RESOURCE).getContentAsByteArray(),
                StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            int comment = line.indexOf("--");
            code.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return Arrays.stream(code.toString().split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void applyMigration() throws IOException {
        List<String> statements = migrationStatements();
        assertThat(statements)
                .as("V0028 must be the INSERT (nodes) followed by the UPDATE (links); the INSERT "
                        + "has to run first or fk_tenants_org_node would reject the link")
                .hasSize(2);
        statements.forEach(jdbcTemplate::execute);
    }

    /**
     * Restores the exact pre-V0028 shape of the two tables the migration writes: every tenant
     * ungrouped, no org nodes. This is not a simulation of the old state — {@code org_node_id
     * IS NULL} for every row IS the state V0028 finds on a real database.
     */
    private void resetToPreMigration() {
        jdbcTemplate.update("UPDATE tenants SET org_node_id = NULL");
        jdbcTemplate.update("DELETE FROM org_node ORDER BY depth DESC");
    }

    @BeforeEach
    void toPreMigrationState() {
        resetToPreMigration();
    }

    /**
     * Leaves the database as a fresh Flyway run would leave it (backfilled), so no later test
     * class inherits a half-migrated shape from this one.
     */
    @AfterEach
    void restore() throws IOException {
        resetToPreMigration();
        applyMigration();
    }

    // ── snapshots ─────────────────────────────────────────────────────────────────────

    private List<String> tenantIds() {
        return jdbcTemplate.queryForList("SELECT tenant_id FROM tenants ORDER BY tenant_id", String.class);
    }

    /** tenant → its resolved entitled domains, in the order the endpoint returns them. */
    private Map<String, List<String>> snapshotEntitledDomains() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (String tenantId : tenantIds()) {
            snapshot.put(tenantId, entitledDomainsUseCase.effectiveEntitledDomains(tenantId));
        }
        return snapshot;
    }

    private List<Map<String, Object>> snapshotSubscriptions() {
        return jdbcTemplate.queryForList(
                "SELECT tenant_id, domain_key, status FROM tenant_domain_subscription "
                        + "ORDER BY tenant_id, domain_key");
    }

    /** Every tenant column EXCEPT the grouping link the migration is allowed to write. */
    private List<Map<String, Object>> snapshotTenantsWithoutGroupingLink() {
        return jdbcTemplate.queryForList(
                "SELECT tenant_id, display_name, tenant_type, status, created_at, updated_at "
                        + "FROM tenants ORDER BY tenant_id");
    }

    private Map<String, String> tenantToNodeMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        jdbcTemplate.queryForList("SELECT tenant_id, org_node_id FROM tenants ORDER BY tenant_id")
                .forEach(row -> mapping.put((String) row.get("tenant_id"), (String) row.get("org_node_id")));
        return mapping;
    }

    private int orgNodeCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM org_node", Integer.class);
    }

    // ── AC-3 (⟹ AC-5): the no-op proof ───────────────────────────────────────────────

    @Test
    @DisplayName("AC-3: every tenant's resolved entitled_domains is byte-identical before and after the backfill")
    void backfillDoesNotMoveAnyTenantsEntitledDomains() throws IOException {
        Map<String, List<String>> before = snapshotEntitledDomains();

        // Without this the whole proof is vacuous: if every tenant resolved [] both times,
        // "byte-identical" would hold for a migration that locked the platform shut.
        assertThat(before.values().stream().anyMatch(domains -> !domains.isEmpty()))
                .as("at least one seeded tenant must resolve a NON-EMPTY domain list, or this test "
                        + "would pass even for a BOUNDED(∅) backfill that denies everything")
                .isTrue();
        assertThat(before).as("the seed must contain several tenants to compare").hasSizeGreaterThan(1);

        applyMigration();

        assertThat(snapshotEntitledDomains())
                .as("the backfill groups tenants; it must not change what any of them may reach. "
                        + "auth-service derives both entitled_domains and roles from this exact list.")
                .isEqualTo(before);
    }

    // ── AC-1: structure ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1: every tenant ends up on its own depth-1 root node named after its display_name")
    void everyTenantGetsItsOwnRootNode() throws IOException {
        List<String> tenants = tenantIds();

        applyMigration();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE org_node_id IS NULL", Integer.class))
                .as("no tenant may be left ungrouped by an eager backfill")
                .isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT org_node_id) FROM tenants", Integer.class))
                .as("one node per tenant — never two tenants sharing a node (that would be a merge, "
                        + "and merging two companies is a business decision, not a migration's)")
                .isEqualTo(tenants.size());

        assertThat(orgNodeCount()).isEqualTo(tenants.size());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM org_node WHERE parent_id IS NOT NULL OR depth <> 1", Integer.class))
                .as("a backfilled company is a root at depth 1")
                .isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenants t JOIN org_node n ON n.id = t.org_node_id "
                        + "WHERE n.name <> t.display_name", Integer.class))
                .as("the node is the COMPANY and takes the tenant's display_name, not its tenant_id")
                .isZero();
    }

    // ── AC-2: the ceiling is the identity, not an enumeration ────────────────────────

    @Test
    @DisplayName("AC-2: every backfilled ceiling is UNBOUNDED — it permits a domain that does not exist yet")
    void backfilledCeilingIsTheIntersectionIdentity() throws IOException {
        applyMigration();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM org_node WHERE ceiling_mode <> 'UNBOUNDED' OR ceiling_domains <> ''",
                Integer.class))
                .as("a BOUNDED ceiling — even one listing every domain we ship today — would deny "
                        + "the next domain we add")
                .isZero();

        for (String tenantId : tenantIds()) {
            EntitlementCeiling ceiling = orgNodeQueryUseCase.effectiveCeilingForTenant(tenantId);

            assertThat(ceiling.isUnbounded())
                    .as("%s must resolve UNBOUNDED after the backfill", tenantId)
                    .isTrue();
            assertThat(ceiling.domains())
                    .as("UNBOUNDED carries no domain set — the two encodings must not be conflated")
                    .isEmpty();
            assertThat(ceiling.permits("a-domain-invented-next-year"))
                    .as("this is what UNBOUNDED actually means: not 'all domains known today', but "
                            + "no ceiling at all. A domain added tomorrow must still be permitted.")
                    .isTrue();
        }
    }

    // ── AC-4: nothing else moves ─────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-4: no tenant_id, no other tenant column, and no subscription row is modified")
    void backfillTouchesOnlyTheGroupingLink() throws IOException {
        List<Map<String, Object>> tenantsBefore = snapshotTenantsWithoutGroupingLink();
        List<Map<String, Object>> subscriptionsBefore = snapshotSubscriptions();

        applyMigration();

        assertThat(snapshotTenantsWithoutGroupingLink())
                .as("tenant_id is the flat isolation key (M1) and must be untouched; updated_at must "
                        + "not be bumped either — the tenant's own state did not change, only the "
                        + "node it now hangs from")
                .isEqualTo(tenantsBefore);

        assertThat(snapshotSubscriptions())
                .as("the entitlement rows carry what a tenant subscribed to; the ceiling narrows the "
                        + "READ, it never rewrites the store")
                .isEqualTo(subscriptionsBefore);
    }

    // ── AC-6: idempotence, and the pre-grouped tenant ────────────────────────────────

    @Test
    @DisplayName("AC-6: a second run is a zero-row no-op — same nodes, same links")
    void migrationIsIdempotent() throws IOException {
        applyMigration();
        int nodesAfterFirstRun = orgNodeCount();
        Map<String, String> mappingAfterFirstRun = tenantToNodeMapping();

        applyMigration();

        assertThat(orgNodeCount())
                .as("re-running must not duplicate a single node (the `org_node_id IS NULL` guard)")
                .isEqualTo(nodesAfterFirstRun);
        assertThat(tenantToNodeMapping())
                .as("re-running must not re-parent a single tenant")
                .isEqualTo(mappingAfterFirstRun);
    }

    @Test
    @DisplayName("AC-6: a tenant grouped by hand before the migration runs is skipped, never re-parented")
    void preGroupedTenantIsLeftAlone() throws IOException {
        int tenantCount = tenantIds().size();
        OrgNode handMade = orgNodeCommandUseCase.create(
                "Hand-grouped Holdings", null, EntitlementCeiling.unbounded());
        jdbcTemplate.update("UPDATE tenants SET org_node_id = ? WHERE tenant_id = ?",
                handMade.getId().value(), ACME);

        applyMigration();

        assertThat(tenantToNodeMapping().get(ACME))
                .as("an already-grouped tenant keeps the node an operator chose for it")
                .isEqualTo(handMade.getId().value());

        assertThat(orgNodeCount())
                .as("the hand-made node plus one backfilled node for each of the remaining tenants — "
                        + "acme-corp must NOT also receive a second, backfilled node")
                .isEqualTo(1 + (tenantCount - 1));
    }

    // ── Failure Scenario 3: the ungrouped escape hatch outlives the backfill ─────────

    @Test
    @DisplayName("D7: after the backfill a tenant can still be un-grouped — NULL stays legal and stays UNBOUNDED")
    void ungroupedRemainsALegalPermanentState() throws IOException {
        applyMigration();
        List<String> whileGrouped = entitledDomainsUseCase.effectiveEntitledDomains(ACME);

        // The escape hatch D7 relies on: `org_node_id` is nullable, and NULL means "ungrouped
        // singleton" (UNBOUNDED), not "unmigrated". Anyone tempted to promote the column to
        // NOT NULL "now that everything is backfilled" breaks this and the lazy-migration path.
        jdbcTemplate.update("UPDATE tenants SET org_node_id = NULL WHERE tenant_id = ?", ACME);

        assertThat(orgNodeQueryUseCase.effectiveCeilingForTenant(ACME).isUnbounded())
                .as("NULL org_node_id ⟹ UNBOUNDED, NOT the empty set")
                .isTrue();
        assertThat(entitledDomainsUseCase.effectiveEntitledDomains(ACME))
                .as("grouping under an UNBOUNDED node and being ungrouped are behaviourally the same "
                        + "— which is precisely why the backfill is a no-op")
                .isEqualTo(whileGrouped);
    }
}
