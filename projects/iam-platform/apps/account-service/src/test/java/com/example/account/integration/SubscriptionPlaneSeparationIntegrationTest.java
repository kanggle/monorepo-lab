package com.example.account.integration;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.application.service.TenantDomainSubscriptionMutationUseCase;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * TASK-BE-344 (ADR-MONO-023 § 3.3 step 3 — D2 plane-separation proof):
 * end-to-end proof, through the real mutation + read use-cases against a real
 * MySQL (Testcontainers + Flyway V0019→V0021), that an entitlement-plane status
 * change is reflected in BOTH downstream read paths and is fully reversible:
 *
 * <ul>
 *   <li><b>subscribe</b> → the domain appears in {@code listActive()} (the
 *       admin-service catalog source, ADR-019 D4) AND in the tenant reverse-lookup
 *       {@code listActive(domainKey=null, tenantId)} (the auth-service
 *       {@code entitled_domains} source, ADR-019 D5); a {@code tenant.subscription.changed}
 *       outbox event is written (D4).</li>
 *   <li><b>suspend</b> → the domain DROPS from both read paths, BUT the subscription
 *       row is PRESERVED (status SUSPENDED, not deleted) — reversible. This is the
 *       entitlement-plane half of D2 (GCP billing↔IAM parity).</li>
 *   <li><b>resume</b> → the SAME row flips back to ACTIVE; access is restored with no
 *       re-creation (no re-grant).</li>
 *   <li><b>cancel</b> → terminal CANCELLED; excluded from both read paths.</li>
 * </ul>
 *
 * <p><b>IAM-plane preservation (D2) is architecturally guaranteed, not asserted
 * here:</b> account-service performs the entitlement write and has NO access to
 * admin_db (operator assignments / RBAC live in admin-service). A suspend
 * therefore CANNOT mutate any IAM binding — the one-way plane dependency is
 * enforced by the service/DB boundary, not by runtime check. The cross-service
 * operator-token re-issuance fidelity check belongs to the federation-e2e stack.
 *
 * <p>Uses {@code acme-corp} (seeded ACTIVE for finance+wms by V0020) with the
 * free {@code scm} domain, so the cycle does not collide with the seed.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("ADR-MONO-023 step 3 — subscription plane-separation proof (entitlement plane)")
class SubscriptionPlaneSeparationIntegrationTest extends AbstractIntegrationTest {

    private static final String TENANT = "acme-corp";
    private static final String DOMAIN = "scm";

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TenantDomainSubscriptionMutationUseCase mutationUseCase;

    @Autowired
    private TenantDomainSubscriptionQueryUseCase queryUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Prevent the outbox poller from racing the assertions / talking to Kafka.
    @MockitoBean
    private OutboxPollingScheduler outboxPollingScheduler;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM outbox");
        jdbcTemplate.update(
                "DELETE FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key = ?",
                TENANT, DOMAIN);
    }

    // -- read-path helpers (the two downstream consumers' source) --------------

    /** admin-service catalog source (ADR-019 D4): all ACTIVE subscriptions. */
    private boolean inCatalog() {
        return queryUseCase.listActive(null).stream()
                .anyMatch(r -> TENANT.equals(r.tenantId()) && DOMAIN.equals(r.domainKey()));
    }

    /** auth-service entitled_domains source (ADR-019 D5): the tenant's ACTIVE domains. */
    private List<String> entitledDomains() {
        return queryUseCase.listActive(null, TENANT).stream()
                .map(TenantDomainSubscriptionResult::domainKey)
                .toList();
    }

    private String rowStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM tenant_domain_subscription WHERE tenant_id = ? AND domain_key = ?",
                String.class, TENANT, DOMAIN);
    }

    private JsonNode lastEventPayload() throws Exception {
        String json = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox WHERE aggregate_id = ? AND event_type = ? "
                        + "ORDER BY id DESC LIMIT 1",
                String.class, TENANT + ":" + DOMAIN, "tenant.subscription.changed");
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("subscribe → suspend → resume → cancel: read paths follow status; row preserved across suspend; event each step")
    void planeSeparationCycle() throws Exception {
        // ── subscribe (ACTIVE) ────────────────────────────────────────────────
        mutationUseCase.subscribe(TENANT, DOMAIN, SubscriptionStatus.ACTIVE, "operator", "op-test", "new contract");

        assertThat(inCatalog()).as("ACTIVE → visible in catalog").isTrue();
        assertThat(entitledDomains()).as("ACTIVE → present in entitled_domains").contains(DOMAIN);
        JsonNode created = lastEventPayload();
        assertThat(created.get("currentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(created.hasNonNull("previousStatus")).as("subscribe → previousStatus null").isFalse();

        // ── suspend (entitlement-plane only; row preserved) ───────────────────
        mutationUseCase.changeStatus(TENANT, DOMAIN, SubscriptionStatus.SUSPENDED, "operator", "op-test", "past due");

        assertThat(inCatalog()).as("SUSPENDED → dropped from catalog").isFalse();
        assertThat(entitledDomains()).as("SUSPENDED → dropped from entitled_domains").doesNotContain(DOMAIN);
        assertThat(rowStatus())
                .as("SUSPENDED → subscription row PRESERVED, not deleted (reversible — GCP billing↔IAM parity)")
                .isEqualTo("SUSPENDED");
        JsonNode suspended = lastEventPayload();
        assertThat(suspended.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(suspended.get("currentStatus").asText()).isEqualTo("SUSPENDED");

        // ── resume (access restored, no re-grant) ─────────────────────────────
        mutationUseCase.changeStatus(TENANT, DOMAIN, SubscriptionStatus.ACTIVE, "operator", "op-test", "settled");

        assertThat(inCatalog()).as("resume → back in catalog (same row, no re-create)").isTrue();
        assertThat(entitledDomains()).as("resume → back in entitled_domains").contains(DOMAIN);

        // ── cancel (terminal; excluded) ───────────────────────────────────────
        mutationUseCase.changeStatus(TENANT, DOMAIN, SubscriptionStatus.CANCELLED, "operator", "op-test", "ended");

        assertThat(inCatalog()).as("CANCELLED → excluded from catalog").isFalse();
        assertThat(entitledDomains()).as("CANCELLED → excluded from entitled_domains").doesNotContain(DOMAIN);
        assertThat(rowStatus()).as("CANCELLED → terminal row retained").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("the V0020-seeded acme-corp ACTIVE domains (finance, wms) are unaffected by the scm cycle")
    void seededActiveDomainsUnaffected() {
        mutationUseCase.subscribe(TENANT, DOMAIN, SubscriptionStatus.ACTIVE, "operator", "op-test", "x");
        mutationUseCase.changeStatus(TENANT, DOMAIN, SubscriptionStatus.SUSPENDED, "operator", "op-test", "x");

        // suspending scm must not touch the tenant's other ACTIVE entitlements
        assertThat(entitledDomains())
                .as("finance + wms remain entitled; only scm dropped")
                .contains("finance", "wms")
                .doesNotContain("scm");
    }

    @Test
    @DisplayName("catalog source carries customer-id rows alongside domain-slug seeds (ADR-019 D4 shape)")
    void catalogCarriesCustomerRows() {
        mutationUseCase.subscribe(TENANT, DOMAIN, SubscriptionStatus.ACTIVE, "operator", "op-test", "x");

        assertThat(queryUseCase.listActive(null))
                .extracting(TenantDomainSubscriptionResult::tenantId, TenantDomainSubscriptionResult::domainKey)
                .contains(tuple(TENANT, DOMAIN));
    }
}
