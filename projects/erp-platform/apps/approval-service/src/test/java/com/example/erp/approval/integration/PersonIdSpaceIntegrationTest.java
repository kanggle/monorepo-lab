package com.example.erp.approval.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-776 — every person field is an EMPLOYEE id, the caller side is «the employee
 * linked to my sub» (approval-api.md § v2.4). Unlike the pre-776 ITs, which run on the stub's
 * identity convention (sub "emp-x" ↔ employee "emp-x"), this class registers DISTINCT account
 * ({@code acc-…}) and employee ({@code emp-…}) ids — the convention would make the two id
 * spaces coincide and hide exactly the defect this ticket closes.
 *
 * <p>Real MySQL (Testcontainers) for the inbox predicate — the unit tests use an in-memory
 * copy of it.
 */
@AutoConfigureMockMvc
class PersonIdSpaceIntegrationTest extends AbstractApprovalIntegrationTest {

    private static final String PERSON_COUNTER = "approval_person_resolve_failures_total";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void people() {
        masterStatus = "ACTIVE";
        masterHttpStatus = 200;
        resetPeople();
        employee("emp-776-sub", "ACTIVE", "acc-776-sub");
        employee("emp-776-app", "ACTIVE", "acc-776-app");
        employee("emp-776-app2", "ACTIVE", "acc-776-app2");
        employee("emp-776-dlg", "ACTIVE", "acc-776-dlg");
        employee("emp-776-retired", "RETIRED", "acc-776-retired");
        employee("emp-776-nolink", "ACTIVE", null);
        MISSING_EMPLOYEES.add("emp-776-ghost");
        UNLINKED_ACCOUNTS.add("acc-776-stranger");
    }

    @AfterEach
    void backToConvention() {
        // The registry is JVM-static and the context is shared: leave the identity convention
        // behind so a sibling class running next is not reading this class's people.
        resetPeople();
    }

    // ---- AC-1 ----

    @Test
    @DisplayName("AC-1: approver = employee id → visible in the LINKED account's inbox, meta.actorEmployeeId set; history/audit split")
    void inboxOfTheLinkedAccount() throws Exception {
        String id = create("acc-776-sub", "emp-776-app", "k776-1");
        submit("acc-776-sub", id, "k776-1s").andExpect(status().isOk());

        JsonNode inbox = json(mockMvc.perform(get("/api/erp/approval/inbox?size=100")
                        .header("Authorization", "Bearer " + token("acc-776-app", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.actorEmployeeId").value("emp-776-app"))
                .andReturn());
        assertThat(ids(inbox)).contains(id);

        // A different account sees nothing — the predicate is the link, not «any operator».
        JsonNode other = json(mockMvc.perform(get("/api/erp/approval/inbox?size=100")
                        .header("Authorization", "Bearer " + token("acc-776-app2", "erp.read")))
                .andExpect(status().isOk()).andReturn());
        assertThat(ids(other)).doesNotContain(id);

        // Person fields carry employee ids; audit_log.actor stays the authenticated sub.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT submitter_id FROM approval_request WHERE id = ?", String.class, id))
                .isEqualTo("emp-776-sub");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor FROM approval_action WHERE approval_request_id = ?", String.class, id))
                .isEqualTo("emp-776-sub");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor FROM approval_audit_log WHERE aggregate_id = ?", String.class, id))
                .isEqualTo("acc-776-sub");
    }

    @Test
    @DisplayName("AC-1 (approve): the linked account approves; history actor = its employee")
    void linkedAccountApproves() throws Exception {
        String id = create("acc-776-sub", "emp-776-app", "k776-2");
        submit("acc-776-sub", id, "k776-2s").andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("acc-776-app", "erp.write"))
                        .header("Idempotency-Key", "k776-2a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.history[1].actor").value("emp-776-app"));
    }

    // ---- AC-2 ----

    @Test
    @DisplayName("AC-2: non-existent approver → 422 approver_unresolved, stays DRAFT")
    void missingApprover() throws Exception {
        String id = create("acc-776-sub", "emp-776-ghost", "k776-3");
        submit("acc-776-sub", id, "k776-3s")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("approver_unresolved"));
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("AC-2: RETIRED approver → 422 approver_unresolved, stays DRAFT")
    void retiredApprover() throws Exception {
        String id = create("acc-776-sub", "emp-776-retired", "k776-4");
        submit("acc-776-sub", id, "k776-4s")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.cause").value("approver_unresolved"));
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("AC-2: approver lookup 401 → 422 approver_unresolved AND counted (lookup=approver, cause=auth)")
    void approverLookupUnauthorizedIsCountedNotSilent() throws Exception {
        String id = create("acc-776-sub", "emp-776-app", "k776-5");
        double before = personFailures("approver", "auth");
        approverRefHttpStatus = 401;

        submit("acc-776-sub", id, "k776-5s")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.cause").value("approver_unresolved"));

        assertThat(personFailures("approver", "auth")).isEqualTo(before + 1);
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("AC-2 control: a 404 approver is an ANSWER — refused, but no failure counter moves")
    void missingApproverIsNotCountedAsAFailure() throws Exception {
        String id = create("acc-776-sub", "emp-776-ghost", "k776-5b");
        double before = totalPersonFailures();

        submit("acc-776-sub", id, "k776-5bs").andExpect(status().isUnprocessableEntity());

        assertThat(totalPersonFailures()).isEqualTo(before);
    }

    @Test
    @DisplayName("«who am I» lookup 503 → 503 SERVICE_UNAVAILABLE (never «not linked»), counted lookup=actor")
    void callerLookupUnavailableIsNotNotLinked() throws Exception {
        String id = create("acc-776-sub", "emp-776-app", "k776-5c");
        double before = personFailures("actor", "unreachable");
        meHttpStatus = 503;

        submit("acc-776-sub", id, "k776-5cs")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

        assertThat(personFailures("actor", "unreachable")).isEqualTo(before + 1);
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    // ---- AC-3 ----

    @Test
    @DisplayName("AC-3: approver = the employee linked to MY account → 422 self_approval")
    void selfApprovalThroughTheLink() throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token("acc-776-sub", "erp.write"))
                        .header("Idempotency-Key", "k776-6")
                        .contentType("application/json")
                        .content(body("emp-776-sub")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("self_approval"));
    }

    // ---- AC-4 ----

    @Test
    @DisplayName("AC-4: ACTIVE approver without a linked account → 422 APPROVAL_APPROVER_UNLINKED + stageIndex")
    void unlinkedApprover() throws Exception {
        String id = createMulti("acc-776-sub", "k776-7", "emp-776-app", "emp-776-nolink");
        submit("acc-776-sub", id, "k776-7s")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_APPROVER_UNLINKED"))
                .andExpect(jsonPath("$.details.stageIndex").value(1));
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("AC-4: unlinked caller → create 403 APPROVAL_ACTOR_NOT_LINKED; inbox 200 empty, meta.actorEmployeeId ABSENT")
    void unlinkedCaller() throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token("acc-776-stranger", "erp.write"))
                        .header("Idempotency-Key", "k776-8")
                        .contentType("application/json")
                        .content(body("emp-776-app")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_ACTOR_NOT_LINKED"));

        mockMvc.perform(get("/api/erp/approval/inbox")
                        .header("Authorization", "Bearer " + token("acc-776-stranger", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0))
                .andExpect(jsonPath("$.meta.actorEmployeeId").doesNotExist());

        mockMvc.perform(get("/api/erp/approval/requests?role=APPROVER")
                        .header("Authorization", "Bearer " + token("acc-776-stranger", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    // ---- AC-5 ----

    @Test
    @DisplayName("AC-5: delegator = my employee; the delegate's linked account approves on my behalf")
    void delegationInTheEmployeeIdSpace() throws Exception {
        String id = create("acc-776-sub", "emp-776-app", "k776-9");
        submit("acc-776-sub", id, "k776-9s").andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("acc-776-app", "erp.write"))
                        .header("Idempotency-Key", "k776-9d")
                        .contentType("application/json")
                        .content(delegation("emp-776-dlg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.delegatorId").value("emp-776-app"))
                .andExpect(jsonPath("$.data.delegateId").value("emp-776-dlg"));

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("acc-776-dlg", "erp.write"))
                        .header("Idempotency-Key", "k776-9a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("AC-5: delegate = the submitter's employee → SoD refusal (employee id vs employee id)")
    void delegateIsSubmitterRefused() throws Exception {
        employee("emp-776-sod-app", "ACTIVE", "acc-776-sod-app");
        String id = create("acc-776-sub", "emp-776-sod-app", "k776-10");
        submit("acc-776-sub", id, "k776-10s").andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("acc-776-sod-app", "erp.write"))
                        .header("Idempotency-Key", "k776-10d")
                        .contentType("application/json")
                        .content(delegation("emp-776-sub")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("acc-776-sub", "erp.write"))
                        .header("Idempotency-Key", "k776-10a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("AC-5: delegate that is not an ACTIVE employee → 422 DELEGATION_INVALID delegate_unresolved")
    void delegateMustBeActive() throws Exception {
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("acc-776-app", "erp.write"))
                        .header("Idempotency-Key", "k776-11")
                        .contentType("application/json")
                        .content(delegation("emp-776-retired")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELEGATION_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("delegate_unresolved"));
    }

    // ---- helpers ----

    private String body(String approverId) {
        return "{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-776\","
                + "\"title\":\"t776\",\"approverId\":\"" + approverId + "\"}";
    }

    private static String delegation(String delegateId) {
        return "{\"delegateId\":\"" + delegateId + "\",\"validFrom\":\"2026-01-01T00:00:00Z\","
                + "\"validTo\":\"2099-01-01T00:00:00Z\",\"reason\":\"r\",\"scope\":\"GLOBAL\"}";
    }

    private String create(String sub, String approverId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token(sub, "erp.write"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body(approverId)))
                .andExpect(status().isCreated())
                .andReturn()).get("data").get("id").asText();
    }

    private String createMulti(String sub, String key, String a0, String a1) throws Exception {
        return json(mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token(sub, "erp.write"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-776\","
                                + "\"title\":\"t776\",\"approverIds\":[\"" + a0 + "\",\"" + a1
                                + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn()).get("data").get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions submit(String sub, String id,
                                                                      String key)
            throws Exception {
        return mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + token(sub, "erp.write"))
                .header("Idempotency-Key", key));
    }

    private String statusOf(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM approval_request WHERE id = ?", String.class, id);
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static java.util.List<String> ids(JsonNode page) {
        java.util.List<String> out = new java.util.ArrayList<>();
        page.get("data").forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    private double personFailures(String lookup, String cause) {
        var c = Search.in(meterRegistry).name(PERSON_COUNTER)
                .tag("lookup", lookup).tag("cause", cause).counter();
        return c == null ? 0 : c.count();
    }

    private double totalPersonFailures() {
        return Search.in(meterRegistry).name(PERSON_COUNTER).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }
}
