package com.example.account.integration;

import com.example.account.application.exception.SubscriptionDomainOutOfCeilingException;
import com.example.account.application.service.OrgNodeCommandUseCase;
import com.example.account.application.service.OrgNodeQueryUseCase;
import com.example.account.application.service.TenantDomainSubscriptionMutationUseCase;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import com.example.account.application.service.TenantEntitledDomainsQueryUseCase;
import com.example.account.application.service.TenantProvisionUseCase;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeCeilingNotSubsetException;
import com.example.account.domain.orgnode.OrgNodeCycleException;
import com.example.account.domain.orgnode.OrgNodeDepthExceededException;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.orgnode.OrgNodeNotEmptyException;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.infrastructure.outbox.AccountOutboxPublisher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-491 (ADR-MONO-047): the org-node tree and the deny-only ceiling, against a real
 * MySQL (Testcontainers + Flyway through V0027).
 *
 * <p>What is actually being proven here, in order of importance:
 * <ol>
 *   <li><b>D7 net-zero</b> — a tenant with {@code org_node_id = NULL} resolves exactly the
 *       domains it resolved before ADR-047 existed. This is the property that lets the
 *       migration be a no-op and lets it be skipped entirely.</li>
 *   <li><b>D6 seam</b> — an <i>ancestor</i> node's ceiling narrows a tenant's effective
 *       entitled domains, with no change to any token-issuance code.</li>
 *   <li><b>Write invariants I1–I4</b> — cycle, depth cap (on re-parent too, not just create),
 *       {@code child ⊆ parent}, and delete-guard.</li>
 *   <li><b>Entitlement-plane gate</b> — activating an out-of-ceiling subscription is refused,
 *       while deactivating one is always allowed.</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("ADR-MONO-047 — org-node hierarchy, deny-only ceiling, D7 net-zero")
class OrgNodeHierarchyIntegrationTest extends AbstractIntegrationTest {

    /** Seeded ACTIVE for finance+wms by V0020; ungrouped until a test groups it. */
    private static final String ACME = "acme-corp";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private OrgNodeCommandUseCase commandUseCase;
    @Autowired private OrgNodeQueryUseCase queryUseCase;
    @Autowired private TenantEntitledDomainsQueryUseCase entitledDomainsUseCase;
    @Autowired private TenantDomainSubscriptionQueryUseCase rawSubscriptionsUseCase;
    @Autowired private TenantDomainSubscriptionMutationUseCase subscriptionMutationUseCase;
    @Autowired private TenantProvisionUseCase tenantProvisionUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AccountOutboxPublisher accountOutboxPublisher;
    @MockitoBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;

    /**
     * Restores the shared, Flyway-seeded state this class mutates.
     *
     * <p>Nothing here is transactional: the use-cases commit, and {@code tenants} /
     * {@code tenant_domain_subscription} / {@code org_node} are seeded once per container.
     * So this runs BOTH before and after every test.
     *
     * <p>Running it only in {@code @BeforeEach} is not enough. The last test in this class
     * would leave {@code acme-corp} attached to a node whose ceiling excludes {@code scm},
     * and the next class to run — {@code SubscriptionPlaneSeparationIntegrationTest}, which
     * subscribes {@code acme-corp} to {@code scm} — would then fail with a
     * {@code SubscriptionDomainOutOfCeilingException} that has nothing to do with its own
     * subject. Cross-class pollution through a shared container is the failure mode; the
     * {@code @AfterEach} is what prevents it.
     */
    private void resetSharedState() {
        jdbcTemplate.update("DELETE FROM account_outbox");
        // Detach every tenant, then drop the tree (children before parents via depth DESC).
        jdbcTemplate.update("UPDATE tenants SET org_node_id = NULL");
        jdbcTemplate.update("DELETE FROM org_node ORDER BY depth DESC");

        // Restore acme-corp's V0020 seed exactly: ACTIVE {finance, wms}, and nothing else.
        jdbcTemplate.update(
                "DELETE FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key NOT IN ('finance','wms')",
                ACME);
        jdbcTemplate.update(
                "UPDATE tenant_domain_subscription SET status = 'ACTIVE' WHERE tenant_id = ?", ACME);
    }

    @BeforeEach
    void clean() {
        resetSharedState();
    }

    @AfterEach
    void restore() {
        resetSharedState();
    }

    private void attach(String tenantId, OrgNodeId nodeId) {
        jdbcTemplate.update("UPDATE tenants SET org_node_id = ? WHERE tenant_id = ?",
                nodeId == null ? null : nodeId.value(), tenantId);
    }

    // ── 1. D7 net-zero ────────────────────────────────────────────────────────

    @Test
    @DisplayName("D7: an ungrouped tenant (org_node_id = NULL) is UNBOUNDED — effective == raw ACTIVE, byte-identical")
    void ungroupedTenantIsNetZero() {
        assertThat(queryUseCase.effectiveCeilingForTenant(ACME).isUnbounded())
                .as("NULL org_node_id ⟹ UNBOUNDED, NOT the empty set")
                .isTrue();

        List<String> raw = rawSubscriptionsUseCase.listActive(null, ACME).stream()
                .map(r -> r.domainKey())
                .toList();

        assertThat(entitledDomainsUseCase.effectiveEntitledDomains(ACME))
                .as("the D6 endpoint's output equals the pre-ADR-047 raw ACTIVE list, in order")
                .isEqualTo(raw)
                .contains("finance", "wms");
    }

    @Test
    @DisplayName("a plain displayName PATCH does not un-group the tenant (save→merge must carry org_node_id)")
    void tenantUpdateDoesNotWipeOrgNodeId() {
        OrgNode company = commandUseCase.create("Acme Corp", null, EntitlementCeiling.unbounded());
        attach(ACME, company.getId());

        // TenantRepositoryImpl.save() rebuilds a DETACHED entity from the domain object and
        // merges it. If org_node_id were not carried through Tenant/TenantJpaEntity.fromDomain,
        // this ordinary rename would silently NULL the grouping link.
        tenantProvisionUseCase.update(ACME, "Acme Corporation", null);

        String orgNodeId = jdbcTemplate.queryForObject(
                "SELECT org_node_id FROM tenants WHERE tenant_id = ?", String.class, ACME);
        assertThat(orgNodeId).isEqualTo(company.getId().value());
    }

    // ── 2. D6 seam: an ancestor ceiling narrows the tenant's effective domains ─

    @Test
    @DisplayName("D6: an ANCESTOR node's ceiling narrows effective entitled domains; the raw subscription rows are untouched")
    void ancestorCeilingNarrowsEffectiveDomains() {
        // company(BOUNDED{finance}) → division(BOUNDED{finance}) ; acme-corp hangs off division.
        OrgNode company = commandUseCase.create("Acme Corp", null,
                EntitlementCeiling.bounded(List.of("finance")));
        OrgNode division = commandUseCase.create("Acme Finance 사업부", company.getId(),
                EntitlementCeiling.bounded(List.of("finance")));
        attach(ACME, division.getId());

        assertThat(entitledDomainsUseCase.effectiveEntitledDomains(ACME))
                .as("wms is subscribed-ACTIVE but lies outside the ancestor chain's ceiling")
                .containsExactly("finance");

        assertThat(rawSubscriptionsUseCase.listActive(null, ACME))
                .as("the RAW subscription read is NOT narrowed — the console must keep managing the wms row")
                .extracting(r -> r.domainKey())
                .contains("finance", "wms");
    }

    @Test
    @DisplayName("a BOUNDED(∅) node locks its tenants out of every domain (fail-closed, not a bug)")
    void emptyCeilingLocksOutEverything() {
        OrgNode company = commandUseCase.create("Locked Corp", null, EntitlementCeiling.bounded(List.of()));
        attach(ACME, company.getId());

        assertThat(entitledDomainsUseCase.effectiveEntitledDomains(ACME)).isEmpty();
    }

    // ── 3. Write invariants ───────────────────────────────────────────────────

    @Test
    @DisplayName("I1: a node cannot be re-parented under itself or under its own descendant")
    void cycleRejected() {
        OrgNode root = commandUseCase.create("Root", null, EntitlementCeiling.unbounded());
        OrgNode child = commandUseCase.create("Child", root.getId(), EntitlementCeiling.unbounded());
        OrgNode grandchild = commandUseCase.create("Grandchild", child.getId(), EntitlementCeiling.unbounded());

        assertThatThrownBy(() -> commandUseCase.reparent(root.getId(), root.getId()))
                .isInstanceOf(OrgNodeCycleException.class);
        assertThatThrownBy(() -> commandUseCase.reparent(root.getId(), grandchild.getId()))
                .as("a parent that lives inside the moved subtree is a cycle in disguise")
                .isInstanceOf(OrgNodeCycleException.class);
    }

    @Test
    @DisplayName("I2: depth cap is enforced on create AND on re-parent (the deepest DESCENDANT decides)")
    void depthCapRejected() {
        OrgNodeId cursor = null;
        for (int i = 1; i <= OrgNode.MAX_DEPTH; i++) {
            cursor = commandUseCase.create("L" + i, cursor, EntitlementCeiling.unbounded()).getId();
        }
        final OrgNodeId deepest = cursor;
        assertThatThrownBy(() -> commandUseCase.create("L6", deepest, EntitlementCeiling.unbounded()))
                .isInstanceOf(OrgNodeDepthExceededException.class);

        // A two-node subtree at depth 1..2 cannot move under a depth-4 node: the CHILD would
        // land at depth 6. Checking only the moved node would let this through.
        OrgNode movableRoot = commandUseCase.create("M1", null, EntitlementCeiling.unbounded());
        commandUseCase.create("M2", movableRoot.getId(), EntitlementCeiling.unbounded());
        OrgNodeId depth4 = queryUseCase.tree().stream()
                .filter(n -> n.getDepth() == 4).findFirst().orElseThrow().getId();

        assertThatThrownBy(() -> commandUseCase.reparent(movableRoot.getId(), depth4))
                .as("the deepest descendant, not the moved node, breaches the cap")
                .isInstanceOf(OrgNodeDepthExceededException.class);
    }

    @Test
    @DisplayName("I3: child ⊆ parent on create, on set-ceiling (both directions), and on re-parent (every descendant)")
    void ceilingSubsetInvariant() {
        OrgNode company = commandUseCase.create("Corp", null, EntitlementCeiling.bounded(List.of("wms", "erp")));

        assertThatThrownBy(() -> commandUseCase.create("Wider", company.getId(),
                EntitlementCeiling.bounded(List.of("wms", "finance"))))
                .isInstanceOf(OrgNodeCeilingNotSubsetException.class);

        assertThatThrownBy(() -> commandUseCase.create("Unbounded child", company.getId(),
                EntitlementCeiling.unbounded()))
                .as("UNBOUNDED is the TOP element — it is not a subset of a BOUNDED parent")
                .isInstanceOf(OrgNodeCeilingNotSubsetException.class);

        OrgNode child = commandUseCase.create("Child", company.getId(), EntitlementCeiling.bounded(List.of("wms", "erp")));
        assertThatThrownBy(() -> commandUseCase.setCeiling(company.getId(), EntitlementCeiling.bounded(List.of("wms"))))
                .as("narrowing a parent below an existing child's ceiling breaks I3 for a node the caller never named")
                .isInstanceOf(OrgNodeCeilingNotSubsetException.class);

        // Re-parent under a narrower ancestor must check the whole moved subtree.
        OrgNode narrow = commandUseCase.create("Narrow Corp", null, EntitlementCeiling.bounded(List.of("wms")));
        assertThatThrownBy(() -> commandUseCase.reparent(child.getId(), narrow.getId()))
                .isInstanceOf(OrgNodeCeilingNotSubsetException.class);
    }

    @Test
    @DisplayName("I4: a node with children or with tenants cannot be deleted")
    void deleteGuard() {
        OrgNode company = commandUseCase.create("Corp", null, EntitlementCeiling.unbounded());
        OrgNode child = commandUseCase.create("Child", company.getId(), EntitlementCeiling.unbounded());

        assertThatThrownBy(() -> commandUseCase.delete(company.getId()))
                .isInstanceOf(OrgNodeNotEmptyException.class);

        attach(ACME, child.getId());
        assertThatThrownBy(() -> commandUseCase.delete(child.getId()))
                .as("deleting a node with tenants would strand service-tenants")
                .isInstanceOf(OrgNodeNotEmptyException.class);

        attach(ACME, null);
        commandUseCase.delete(child.getId());
        commandUseCase.delete(company.getId());
        assertThat(queryUseCase.tree()).isEmpty();
    }

    @Test
    @DisplayName("re-parenting shifts the whole subtree's stored depth")
    void reparentShiftsSubtreeDepth() {
        OrgNode a = commandUseCase.create("A", null, EntitlementCeiling.unbounded());
        OrgNode b = commandUseCase.create("B", null, EntitlementCeiling.unbounded());
        OrgNode bChild = commandUseCase.create("B-child", b.getId(), EntitlementCeiling.unbounded());

        commandUseCase.reparent(b.getId(), a.getId());

        assertThat(queryUseCase.get(b.getId()).getDepth()).isEqualTo(2);
        assertThat(queryUseCase.get(bChild.getId()).getDepth())
                .as("the descendant's depth must follow its ancestor's move")
                .isEqualTo(3);
    }

    // ── 4. Subtree expansion (backs admin-service's ORG_ADMIN scope) ───────────

    @Test
    @DisplayName("D5: subtreeTenantIds returns self + descendants' tenants, and nothing outside")
    void subtreeTenantExpansion() {
        OrgNode company = commandUseCase.create("Corp", null, EntitlementCeiling.unbounded());
        OrgNode division = commandUseCase.create("Division", company.getId(), EntitlementCeiling.unbounded());
        OrgNode other = commandUseCase.create("Other Corp", null, EntitlementCeiling.unbounded());

        attach(ACME, division.getId());
        attach("wms", other.getId());

        assertThat(queryUseCase.subtreeTenantIds(company.getId())).containsExactly(ACME);
        assertThat(queryUseCase.subtreeTenantIds(division.getId())).containsExactly(ACME);
        assertThat(queryUseCase.subtreeTenantIds(other.getId())).containsExactly("wms");
    }

    // ── 5. Entitlement-plane write gate ───────────────────────────────────────

    @Test
    @DisplayName("D2: activating an out-of-ceiling domain is refused; deactivating one is always allowed")
    void subscriptionActivationCeilingGate() {
        OrgNode company = commandUseCase.create("Corp", null, EntitlementCeiling.bounded(List.of("finance")));
        attach(ACME, company.getId());

        assertThatThrownBy(() -> subscriptionMutationUseCase.subscribe(
                ACME, "scm", SubscriptionStatus.ACTIVE, "operator", "op-test", "new contract"))
                .isInstanceOf(SubscriptionDomainOutOfCeilingException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key = 'scm'",
                Integer.class, ACME))
                .as("the refused row must not be written")
                .isZero();

        // `wms` is already ACTIVE from the V0020 seed but now sits outside the ceiling.
        // Narrowing it further must stay possible, or a tightened ceiling would strand the very
        // row it invalidated.
        subscriptionMutationUseCase.changeStatus(ACME, "wms", SubscriptionStatus.SUSPENDED,
                "operator", "op-test", "outside ceiling");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key = 'wms'",
                String.class, ACME)).isEqualTo("SUSPENDED");

        // ...and re-activating it is refused while the ceiling still excludes it.
        assertThatThrownBy(() -> subscriptionMutationUseCase.changeStatus(
                ACME, "wms", SubscriptionStatus.ACTIVE, "operator", "op-test", "oops"))
                .isInstanceOf(SubscriptionDomainOutOfCeilingException.class);
    }

    @Test
    @DisplayName("the ceiling gate is inert for an ungrouped tenant (D7 net-zero on the write path too)")
    void ceilingGateInertWhenUngrouped() {
        jdbcTemplate.update("DELETE FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key = 'scm'", ACME);

        subscriptionMutationUseCase.subscribe(ACME, "scm", SubscriptionStatus.ACTIVE,
                "operator", "op-test", "no ceiling anywhere");

        assertThat(entitledDomainsUseCase.effectiveEntitledDomains(ACME)).contains("scm");
    }
}
