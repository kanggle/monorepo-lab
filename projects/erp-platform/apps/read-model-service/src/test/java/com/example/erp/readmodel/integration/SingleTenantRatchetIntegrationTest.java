package com.example.erp.readmodel.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * <b>ADR-ERP-001 — D ratchet, read-model half (TASK-ERP-BE-043 AC-7).</b>
 *
 * <p>D removed the event plane's tenant <b>rejection</b> and put an after-the-fact
 * detector in its place: <i>"erp going multi-tenant (distinct {@code tenant_id}
 * ≥ 2) is RED — and that is the moment to reopen Option B."</i> The rejection was
 * a real (if wrongly-aimed) alarm, so if the detector does not actually run, only
 * half of the trade the ADR authorised has been executed. This repository has
 * shipped a predicate with no lane twice ({@code TASK-MONO-518}, {@code -524}) and
 * both were green forever.
 *
 * <p><b>The lane.</b> {@code erp-integration-tests} in {@code ci.yml}, which runs
 * on every erp change and on shared-lib/workflow changes. It boots real MySQL +
 * Kafka (Testcontainers), so the predicate is evaluated against an actual database
 * rather than against source text.
 *
 * <p><b>What it can and cannot see.</b> It sees a second tenant introduced by
 * <i>code</i>: any write path that stamps a constant next to the fact — the exact
 * shape this task found, where {@code delegation_fact_proj.tenant_id} was unmapped
 * and would have taken its DDL {@code DEFAULT 'erp'} while the grant it projects
 * carries {@code demo-corp}. It cannot see a second tenant introduced at
 * <i>runtime</i> (an operator assuming another erp-entitled tenant and writing
 * through the API), because no CI job has a live erp database. That half is
 * {@code scripts/check-erp-single-tenant-ratchet.sh}, run against a running stack;
 * the two are not substitutes and neither is described as covering the other.
 */
class SingleTenantRatchetIntegrationTest extends AbstractReadModelIntegrationTest {

    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-06-30T00:00:00Z";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void projectingRealEventsIntroducesNoSecondTenant() {
        String grantId = newId();
        publish(TOPIC_DELEGATED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegated", grantId, "emp-a", "emp-d", FROM, TO, "ratchet"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM delegation_fact_proj WHERE grant_id = ?",
                        Integer.class, grantId)).isEqualTo(1));

        // A zero-row table would make every DISTINCT assertion below vacuously true,
        // so the population is asserted first — an empty measurement is a failed
        // measurement, not a pass.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delegation_fact_proj", Integer.class);
        assertThat(rows).as("nothing was projected — the ratchet measured an empty table")
                .isNotNull().isPositive();

        List<String> tenants = jdbcTemplate.queryForList(
                "SELECT DISTINCT tenant_id FROM delegation_fact_proj", String.class);

        assertThat(tenants)
                .as("ADR-ERP-001 — D ratchet: distinct tenant_id >= 2 anywhere in erp is RED "
                        + "and means Option B (multi-tenant promotion) must be reopened. "
                        + "🔴 If this fires in CI but not when run alone, check first whether a "
                        + "SIBLING test left a foreign-tenant row in this shared schema — that "
                        + "is a test artifact, not erp going multi-tenant. "
                        + "(DelegationFactProjectionIntegrationTest#anotherTenantIsProjected… "
                        + "deletes its row in a finally block for exactly this reason.)")
                .hasSize(1);
        assertThat(tenants.get(0))
                .as("the projection must carry the grant's own tenant, not the column's "
                        + "legacy DEFAULT 'erp' — a constant here makes erp look multi-tenant "
                        + "all by itself")
                .isEqualTo(TENANT);
    }
}
