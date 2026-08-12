package com.example.erp.notification.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * <b>ADR-ERP-001 — D ratchet, notification half (TASK-ERP-BE-043 AC-7).</b>
 * The read-model half is
 * {@code com.example.erp.readmodel.integration.SingleTenantRatchetIntegrationTest};
 * both exist because the gate D removes existed in <b>two</b> services and the
 * original count only looked inside one of them. A ratchet installed in one copy
 * would inherit exactly the blind spot that produced this ADR.
 *
 * <p>Lane: {@code erp-integration-tests} in {@code ci.yml} (real MySQL + Kafka via
 * Testcontainers), on every erp change. Blind spot, stated rather than implied: a
 * tenant introduced at <b>runtime</b> through the API is invisible here — no CI job
 * has a live erp database. {@code scripts/check-erp-single-tenant-ratchet.sh} is
 * that half, and it spans all four erp schemas rather than one.
 */
class SingleTenantRatchetIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deliveringRealEventsIntroducesNoSecondTenant() {
        String approvalId = newId();
        publish(TOPIC_SUBMITTED, approvalId, approvalEnvelope(newId(),
                "erp.approval.submitted", approvalId, "emp-approver", "emp-submitter",
                null, null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationJpa.count()).isPositive());

        // Both tables carry tenant_id and both are written on the same path; a
        // zero-row table would make the DISTINCT assertions vacuous, so the
        // population is asserted first.
        for (String table : List.of("notification", "notification_delivery")) {
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(rows).as("%s is empty — the ratchet measured nothing", table)
                    .isNotNull().isPositive();

            List<String> tenants = jdbcTemplate.queryForList(
                    "SELECT DISTINCT tenant_id FROM " + table, String.class);
            assertThat(tenants)
                    .as("ADR-ERP-001 — D ratchet on %s: distinct tenant_id >= 2 anywhere in "
                            + "erp is RED and means Option B must be reopened", table)
                    .hasSize(1);
            assertThat(tenants.get(0))
                    .as("%s must record the event's own tenant, not the column's legacy "
                            + "DEFAULT 'erp'", table)
                    .isEqualTo(TENANT);
        }
    }
}
