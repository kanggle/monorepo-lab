package com.example.erp.readmodel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for the delegation-fact projection (TASK-ERP-BE-015,
 * Testcontainers MySQL + Kafka + MockWebServer JWKS). Covers AC-2..AC-5: produce
 * delegation events → consume → project → read; grant→ACTIVE; revoke→REVOKED;
 * dedupe; out-of-order revoke-before-grant; org_scope list (in-scope) / detail 404
 * (out-of-scope); activeAt filter.
 */
class DelegationFactProjectionIntegrationTest extends AbstractReadModelIntegrationTest {

    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-06-30T00:00:00Z";

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpResponse<String> getDelegation(String id, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/erp/read-model/delegations/" + id))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode listDelegations(String query, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/erp/read-model/delegations" + query))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return objectMapper.readTree(resp.body());
    }

    private Map<String, Object> deptAfter(String code, String name, String parentId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", code);
        after.put("name", name);
        after.put("parentId", parentId);
        after.put("status", "ACTIVE");
        after.put("effectivePeriod", Map.of("effectiveFrom", "2026-01-01"));
        return after;
    }

    private Map<String, Object> empAfter(String employeeNumber, String name, String deptId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeNumber", employeeNumber);
        after.put("name", name);
        after.put("departmentId", deptId);
        after.put("costCenterId", null);
        after.put("jobGradeId", null);
        after.put("status", "ACTIVE");
        after.put("effectivePeriod", Map.of("effectiveFrom", "2026-01-01"));
        return after;
    }

    @Test
    void grantThenRevokeProjectsActiveThenRevoked() throws Exception {
        String grantId = newId();
        publish(TOPIC_DELEGATED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegated", grantId, "emp-a", "emp-d", FROM, TO, "vacation"));

        String token = erpReadToken();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpResponse<String> resp = getDelegation(grantId, token);
            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode body = objectMapper.readTree(resp.body());
            assertThat(body.at("/data/status").asText()).isEqualTo("ACTIVE");
            assertThat(body.at("/data/delegatorId").asText()).isEqualTo("emp-a");
            assertThat(body.at("/data/delegateId").asText()).isEqualTo("emp-d");
            assertThat(body.at("/data/revokedAt").isMissingNode()).isTrue();
        });

        // Revoke → REVOKED (window preserved).
        publish(TOPIC_DELEGATION_REVOKED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegation.revoked", grantId, "emp-a", "emp-d",
                null, null, "back early"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode body = objectMapper.readTree(getDelegation(grantId, token).body());
            assertThat(body.at("/data/status").asText()).isEqualTo("REVOKED");
            assertThat(body.at("/data/revokedAt").isMissingNode()).isFalse();
            assertThat(body.at("/data/validFrom").isMissingNode()).isFalse();
        });
    }

    @Test
    void outOfOrderRevokeBeforeGrantLeavesWindowAbsentThenStaysRevoked() throws Exception {
        String grantId = newId();
        // Revoke arrives first (replay-from-middle): window ABSENT.
        publish(TOPIC_DELEGATION_REVOKED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegation.revoked", grantId, "emp-a", "emp-d",
                null, null, "back"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(delegationFactJpa.findById(grantId)).get()
                        .satisfies(e -> {
                            assertThat(e.getStatus()).isEqualTo("REVOKED");
                            assertThat(e.getValidFrom()).isNull();
                            assertThat(e.getValidTo()).isNull();
                        }));

        // A late delegated must NOT revert REVOKED → ACTIVE (but fills the window).
        publish(TOPIC_DELEGATED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegated", grantId, "emp-a", "emp-d", FROM, TO, "vacation"));
        Thread.sleep(3000);

        assertThat(delegationFactJpa.findById(grantId)).get()
                .satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo("REVOKED");
                    assertThat(e.getValidFrom()).isNotNull();
                });
    }

    @Test
    void duplicateEventIsDeduped() throws Exception {
        String grantId = newId();
        String eventId = newId();
        String env = delegationEnvelope(eventId,
                "erp.approval.delegated", grantId, "emp-a", "emp-d", FROM, TO, "vacation");
        publish(TOPIC_DELEGATED, grantId, env);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> delegationFactJpa.findById(grantId).isPresent());

        // Re-publish the SAME eventId (at-least-once duplicate) → no-op (dedupe T8).
        publish(TOPIC_DELEGATED, grantId, env);
        Thread.sleep(2000);

        Long processed = processedEventJpa.count();
        assertThat(processed).isPositive();
        assertThat(delegationFactJpa.findById(grantId)).get()
                .extracting(e -> e.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void activeAtFilterMatchesOnlyGrantsActiveAtInstant() throws Exception {
        String inWindow = newId();
        String pastWindow = newId();
        publish(TOPIC_DELEGATED, inWindow, delegationEnvelope(newId(),
                "erp.approval.delegated", inWindow, "emp-a", "emp-d", FROM, TO, "now"));
        publish(TOPIC_DELEGATED, pastWindow, delegationEnvelope(newId(),
                "erp.approval.delegated", pastWindow, "emp-a", "emp-d",
                "2020-01-01T00:00:00Z", "2020-12-31T00:00:00Z", "past"));

        await().atMost(Duration.ofSeconds(30)).until(() ->
                delegationFactJpa.findById(inWindow).isPresent()
                        && delegationFactJpa.findById(pastWindow).isPresent());

        JsonNode list = listDelegations(
                "?activeAt=2026-06-15T00:00:00Z&delegatorId=emp-a&size=100", erpReadToken());
        boolean hasIn = false;
        boolean hasPast = false;
        for (JsonNode node : list.at("/data")) {
            String id = node.at("/grantId").asText();
            if (id.equals(inWindow)) hasIn = true;
            if (id.equals(pastWindow)) hasPast = true;
        }
        assertThat(hasIn).isTrue();
        assertThat(hasPast).isFalse();
    }

    @Test
    void orgScopeListAndDetailFilterByDelegatorDepartment() throws Exception {
        // Two departments; the delegators are employees in different departments.
        String deptIn = newId();
        String deptOut = newId();
        publish(TOPIC_DEPARTMENT, deptIn,
                envelope(newId(), "department", deptIn, "CREATED", deptAfter("IN", "스코프내", null)));
        publish(TOPIC_DEPARTMENT, deptOut,
                envelope(newId(), "department", deptOut, "CREATED", deptAfter("OUT", "스코프밖", null)));

        String delegatorIn = newId();
        String delegatorOut = newId();
        publish(TOPIC_EMPLOYEE, delegatorIn,
                envelope(newId(), "employee", delegatorIn, "CREATED",
                        empAfter("E-IN", "안", deptIn)));
        publish(TOPIC_EMPLOYEE, delegatorOut,
                envelope(newId(), "employee", delegatorOut, "CREATED",
                        empAfter("E-OUT", "밖", deptOut)));

        String grantIn = newId();
        String grantOut = newId();
        publish(TOPIC_DELEGATED, grantIn, delegationEnvelope(newId(),
                "erp.approval.delegated", grantIn, delegatorIn, "emp-d", FROM, TO, "in"));
        publish(TOPIC_DELEGATED, grantOut, delegationEnvelope(newId(),
                "erp.approval.delegated", grantOut, delegatorOut, "emp-d", FROM, TO, "out"));

        await().atMost(Duration.ofSeconds(30)).until(() ->
                delegationFactJpa.findById(grantIn).isPresent()
                        && delegationFactJpa.findById(grantOut).isPresent()
                        && employeeJpa.findById(delegatorIn).isPresent()
                        && employeeJpa.findById(delegatorOut).isPresent());

        // org_scope token scoped to deptIn only.
        String scopedToken = token(c -> c.claim("tenant_id", "erp").claim("scope", "erp.read")
                .claim("org_scope", List.of(deptIn)));

        // List: only the in-scope delegator's grant appears.
        JsonNode list = listDelegations("?size=100", scopedToken);
        boolean hasIn = false;
        boolean hasOut = false;
        for (JsonNode node : list.at("/data")) {
            String id = node.at("/grantId").asText();
            if (id.equals(grantIn)) hasIn = true;
            if (id.equals(grantOut)) hasOut = true;
        }
        assertThat(hasIn).isTrue();
        assertThat(hasOut).isFalse();

        // Detail: in-scope → 200; out-of-scope → 404 (no existence leak).
        assertThat(getDelegation(grantIn, scopedToken).statusCode()).isEqualTo(200);
        assertThat(getDelegation(grantOut, scopedToken).statusCode()).isEqualTo(404);

        // Platform token (no org_scope) sees both (net-zero).
        assertThat(getDelegation(grantOut, erpReadToken()).statusCode()).isEqualTo(200);
    }

    /**
     * Builds a delegation envelope with an explicit tenant — or with <b>none</b>
     * when {@code tenantId} is null. The shared helper deliberately cannot produce
     * either shape.
     */
    private String delegationEnvelopeWithTenant(String grantId, String tenantId)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grantId", grantId);
        payload.put("delegatorId", "emp-a");
        payload.put("delegateId", "emp-d");
        payload.put("validFrom", FROM);
        if (tenantId != null) payload.put("tenantId", tenantId);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("actor", "emp-a");
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", newId());
        env.put("eventType", "erp.approval.delegated");
        env.put("occurredAt", Instant.now().toString());
        if (tenantId != null) env.put("tenantId", tenantId);
        env.put("source", "erp-platform-approval-service");
        env.put("aggregateType", "DelegationGrant");
        env.put("aggregateId", grantId);
        env.put("payload", payload);
        return objectMapper.writeValueAsString(env);
    }

    /**
     * <b>Replaces {@code nonErpTenantEventRoutesToDltAndIsNotProjected}</b>
     * (TASK-ERP-BE-043 / ADR-ERP-001 — D). That test pinned the rejection of any
     * tenant other than {@code erp} — and that rejection is exactly what sent every
     * real event to {@code .DLT}, because no erp row has ever carried the string
     * {@code erp}. The tenant is now <b>carried, not compared</b>: another tenant
     * projects normally and its own value lands on the row, where the single-tenant
     * ratchet — not this consumer — is what notices erp going multi-tenant.
     */
    @Test
    void anotherTenantIsProjectedAndItsValueIsRecorded() throws Exception {
        String grantId = newId();
        try {
            publish(TOPIC_DELEGATED, grantId, delegationEnvelopeWithTenant(grantId, "other-corp"));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(delegationFactJpa.findById(grantId)).isPresent());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM delegation_fact_proj WHERE grant_id = ?",
                    String.class, grantId)).isEqualTo("other-corp");
        } finally {
            // 🔴 This row MUST NOT outlive this test. `SingleTenantRatchetIntegrationTest`
            // asserts a property of the WHOLE schema (distinct tenant_id == 1) against the
            // same shared Testcontainers MySQL, so a foreign-tenant row left behind here
            // reads to it exactly like erp having gone multi-tenant. That is not a
            // hypothetical: the first version of this test had no cleanup, passed when run
            // alone, and turned the ratchet RED in CI where the whole suite runs together —
            // the ratchet caught a test artifact, which is the ratchet working, and the
            // local runs that "verified" each test in isolation never composed them.
            // In `finally` so a failing assertion above cannot poison a sibling class.
            jdbcTemplate.update("DELETE FROM delegation_fact_proj WHERE grant_id = ?", grantId);
        }
    }

    /**
     * The one tenant condition that survives ADR-ERP-001 — D: an envelope naming
     * <b>no</b> tenant anywhere is invalid → immediate DLT, never projected. Kept
     * as a live assertion rather than only a unit test, because it is what stops
     * "carried, not compared" from writing null-tenant rows that the ratchet could
     * not see.
     */
    @Test
    void anEnvelopeThatNamesNoTenantIsNotProjected() throws Exception {
        String grantId = newId();
        publish(TOPIC_DELEGATED, grantId, delegationEnvelopeWithTenant(grantId, null));

        Thread.sleep(3000);
        assertThat(delegationFactJpa.findById(grantId)).isEmpty();
    }

    // ------------------------------------------------------------------------
    // TASK-ERP-BE-018 — scope projection
    // ------------------------------------------------------------------------

    @Test
    void delegatedRequestProjectsScopeAndScopeRequestId_globalLeavesScopeRequestIdNull()
            throws Exception {
        String requestGrant = newId();
        String globalGrant = newId();
        publish(TOPIC_DELEGATED, requestGrant, delegationEnvelope(newId(),
                "erp.approval.delegated", requestGrant, "emp-a", "emp-d", FROM, TO, "vacation",
                "REQUEST", "appr-1"));
        publish(TOPIC_DELEGATED, globalGrant, delegationEnvelope(newId(),
                "erp.approval.delegated", globalGrant, "emp-a", "emp-d", FROM, TO, "vacation",
                "GLOBAL", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(delegationFactJpa.findById(requestGrant)).get().satisfies(e -> {
                assertThat(e.getScope()).isEqualTo("REQUEST");
                assertThat(e.getScopeRequestId()).isEqualTo("appr-1");
            });
            assertThat(delegationFactJpa.findById(globalGrant)).get().satisfies(e -> {
                assertThat(e.getScope()).isEqualTo("GLOBAL");
                assertThat(e.getScopeRequestId()).isNull();
            });
        });

        // Read surface: REQUEST exposes both; GLOBAL omits scopeRequestId (NON_NULL).
        String token = erpReadToken();
        JsonNode requestBody = objectMapper.readTree(getDelegation(requestGrant, token).body());
        assertThat(requestBody.at("/data/scope").asText()).isEqualTo("REQUEST");
        assertThat(requestBody.at("/data/scopeRequestId").asText()).isEqualTo("appr-1");

        JsonNode globalBody = objectMapper.readTree(getDelegation(globalGrant, token).body());
        assertThat(globalBody.at("/data/scope").asText()).isEqualTo("GLOBAL");
        assertThat(globalBody.at("/data/scopeRequestId").isMissingNode()).isTrue();
    }

    @Test
    void outOfOrderRevokeThenGrantFillsScopeWithoutRevertingStatus() throws Exception {
        String grantId = newId();
        // Revoke arrives first → scope ABSENT (NULL), status REVOKED.
        publish(TOPIC_DELEGATION_REVOKED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegation.revoked", grantId, "emp-a", "emp-d",
                null, null, "back"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(delegationFactJpa.findById(grantId)).get().satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo("REVOKED");
                    assertThat(e.getScope()).isNull();
                }));

        // Late delegated(REQUEST) → fills scope, status stays REVOKED (applyGrant
        // sets scope unconditionally, outside the sticky-terminal guard).
        publish(TOPIC_DELEGATED, grantId, delegationEnvelope(newId(),
                "erp.approval.delegated", grantId, "emp-a", "emp-d", FROM, TO, "vacation",
                "REQUEST", "appr-1"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(delegationFactJpa.findById(grantId)).get().satisfies(e -> {
                    assertThat(e.getStatus()).isEqualTo("REVOKED");
                    assertThat(e.getScope()).isEqualTo("REQUEST");
                    assertThat(e.getScopeRequestId()).isEqualTo("appr-1");
                }));
    }

    @Test
    void checkConstraintRejectsBogusScope() {
        // §16/§17: the DB CHECK pins the scope value set. MySQL surfaces a CHECK
        // violation as SQLState HY000 / error 3819 → Spring UncategorizedSQLException
        // (NOT DataIntegrityViolationException) → assert the common DataAccessException
        // parent + the constraint name.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO delegation_fact_proj "
                        + "(grant_id, status, last_event_at, last_event_id, tenant_id, scope) "
                        + "VALUES (?, 'ACTIVE', ?, ?, 'erp', 'BOGUS')",
                newId(), Instant.now(), newId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_delegation_fact_proj_scope");
    }
}
