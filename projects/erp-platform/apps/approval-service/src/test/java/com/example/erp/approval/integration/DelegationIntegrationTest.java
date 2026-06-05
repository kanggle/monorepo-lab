package com.example.erp.approval.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 대결/위임 (delegation) integration test (TASK-ERP-BE-013, Testcontainers MySQL +
 * Kafka + WireMock JWKS + WireMock masterdata). Covers: create A→D grant
 * (delegated.v1 published + audit) → multi-stage request where A is a stage
 * approver → D approves that stage (onBehalfOf=A audit, actingForApproverId event,
 * stage advance preserved) / non-delegate other principal → 403 / expired grant
 * → 403 / revoked grant → 403 / delegate==submitter refused / withdraw still
 * submitter-only / DELEGATION_INVALID + DELEGATION_NOT_FOUND.
 */
@AutoConfigureMockMvc
class DelegationIntegrationTest extends AbstractApprovalIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private static final String FROM = "2026-06-01T00:00:00Z";
    private static final String TO = "2026-12-31T00:00:00Z";

    @BeforeEach
    void resetMasterStub() {
        masterStatus = "ACTIVE";
        masterHttpStatus = 200;
    }

    // ---- helpers ----

    private String createMultiStage(String submitter, String key, String... approverIds)
            throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < approverIds.length; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(approverIds[i]).append('"');
        }
        MvcResult res = mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token(submitter, "erp.write"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverIds\":[" + ids + "]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private void submitOk(String id, String submitter, String key) throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token(submitter, "erp.write"))
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk());
    }

    /** Create a GLOBAL grant delegator→delegate (delegator = token sub). Returns grant id. */
    private String createGrant(String delegator, String delegate, String validFrom,
                               String validTo, String key) throws Exception {
        return createGrant(delegator, delegate, validFrom, validTo, key, null, null);
    }

    /**
     * Create a grant delegator→delegate with an optional scope ({@code null} =
     * GLOBAL, request body omits the field) + scopeRequestId. Returns grant id.
     */
    private String createGrant(String delegator, String delegate, String validFrom,
                               String validTo, String key, String scope,
                               String scopeRequestId) throws Exception {
        String body = "{\"delegateId\":\"" + delegate + "\",\"validFrom\":\"" + validFrom + "\""
                + (validTo == null ? "" : ",\"validTo\":\"" + validTo + "\"")
                + (scope == null ? "" : ",\"scope\":\"" + scope + "\"")
                + (scopeRequestId == null ? "" : ",\"scopeRequestId\":\"" + scopeRequestId + "\"")
                + ",\"reason\":\"away\"}";
        MvcResult res = mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token(delegator, "erp.write"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.delegatorId").value(delegator))
                .andExpect(jsonPath("$.data.delegateId").value(delegate))
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    // ---- AC-1: grant lifecycle ----

    @Test
    @DisplayName("AC-1: create grant → ACTIVE + delegated.v1 outbox + audit row")
    void createGrantEmitsEventAndAudit() throws Exception {
        String grantId = createGrant("emp-a", "emp-d", FROM, TO, "k-dg-create-1");

        Long delegatedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, grantId, "erp.approval.delegated");
        assertThat(delegatedOutbox).isEqualTo(1L);
        Long auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_audit_log WHERE aggregate_id = ? AND aggregate_type = ?",
                Long.class, grantId, "DelegationGrant");
        assertThat(auditRows).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-1: self-delegation → 422 DELEGATION_INVALID; bad window → 422")
    void invalidGrants() throws Exception {
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-dg-self")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-a\",\"validFrom\":\"" + FROM + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELEGATION_INVALID"));

        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-dg-window")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-d\",\"validFrom\":\"" + TO
                                + "\",\"validTo\":\"" + FROM + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELEGATION_INVALID"));
    }

    @Test
    @DisplayName("AC-1: revoke → REVOKED + audit + revoked.v1 outbox (TASK-ERP-BE-015); "
            + "unknown id → 404 DELEGATION_NOT_FOUND")
    void revokeAndNotFound() throws Exception {
        String grantId = createGrant("emp-a", "emp-d", FROM, TO, "k-dg-create-rev");
        mockMvc.perform(post("/api/erp/approval/delegations/" + grantId + "/revoke")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-dg-revoke")
                        .contentType("application/json").content("{\"reason\":\"back\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        Long revokedAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_audit_log WHERE aggregate_id = ? AND action = ?",
                Long.class, grantId, "approval.delegation.revoked");
        assertThat(revokedAudit).isEqualTo(1L);
        // The create still produced exactly one delegated.v1 (unchanged).
        Long delegatedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, grantId, "erp.approval.delegated");
        assertThat(delegatedOutbox).isEqualTo(1L);
        // TASK-ERP-BE-015: an actual ACTIVE→REVOKED transition writes the revoke event
        // to the outbox inside the same Tx (A7).
        Long revokedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, grantId, "erp.approval.delegation.revoked");
        assertThat(revokedOutbox).isEqualTo(1L);

        // Idempotent re-revoke: not a transition → no second revoke event, no second audit.
        mockMvc.perform(post("/api/erp/approval/delegations/" + grantId + "/revoke")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-dg-revoke-again")
                        .contentType("application/json").content("{\"reason\":\"again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        Long revokedOutboxAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, grantId, "erp.approval.delegation.revoked");
        assertThat(revokedOutboxAfter).isEqualTo(1L);

        mockMvc.perform(post("/api/erp/approval/delegations/dgr-missing/revoke")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-dg-revoke-missing")
                        .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELEGATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-1: list → caller's grants as delegator + delegate")
    void listGrants() throws Exception {
        createGrant("emp-list-a", "emp-list-d", FROM, TO, "k-dg-list-1");
        mockMvc.perform(get("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-list-a", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.delegateId=='emp-list-d')].status").value("ACTIVE"));
        // the delegate also sees it
        mockMvc.perform(get("/api/erp/approval/delegations?role=DELEGATE")
                        .header("Authorization", "Bearer " + token("emp-list-d", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.delegatorId=='emp-list-a')].status").value("ACTIVE"));
    }

    // ---- AC-2: delegated transition ----

    @Test
    @DisplayName("AC-2: 2-stage, grant A→D, D approves A's stage → onBehalfOf=A audit, "
            + "actingForApproverId event field, stage advance preserved")
    void delegatedApprovePreservesStageAdvance() throws Exception {
        // stage0 = emp-app1, stage1 = emp-app2; D delegates for emp-app1.
        createGrant("emp-app1", "emp-d", FROM, TO, "k-dg-ms-grant");
        String id = createMultiStage("emp-sub", "k-dg-ms-create", "emp-app1", "emp-app2");
        submitOk(id, "emp-sub", "k-dg-ms-submit");

        // D approves stage 0 on behalf of emp-app1 → IN_REVIEW, advance to emp-app2.
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-d", "erp.write"))
                        .header("Idempotency-Key", "k-dg-ms-approve-d")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.currentStage").value(1))
                .andExpect(jsonPath("$.data.approverId").value("emp-app2"));

        // audit/action row records actor=emp-d, on_behalf_of=emp-app1.
        String onBehalfOf = jdbcTemplate.queryForObject(
                "SELECT on_behalf_of FROM approval_action WHERE approval_request_id = ? "
                        + "AND actor = ? AND transition = ?",
                String.class, id, "emp-d", "APPROVED");
        assertThat(onBehalfOf).isEqualTo("emp-app1");

        // final stage approved by emp-app2 directly → APPROVED + approved event carrying NO
        // actingForApproverId (direct approver). The intermediate stage emitted no event.
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-app2", "erp.write"))
                        .header("Idempotency-Key", "k-dg-ms-approve-final")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // the detail history exposes actingForApproverId on the delegated entry.
        MvcResult detail = mockMvc.perform(get("/api/erp/approval/requests/" + id)
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.read")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode history = objectMapper.readTree(detail.getResponse().getContentAsString())
                .get("data").get("history");
        boolean foundDelegated = false;
        for (JsonNode h : history) {
            if (h.has("actingForApproverId")
                    && "emp-app1".equals(h.get("actingForApproverId").asText())) {
                foundDelegated = true;
            }
        }
        assertThat(foundDelegated).isTrue();
    }

    // ---- AC-3: unauthorized / expired / revoked / SoD ----

    @Test
    @DisplayName("AC-3: non-delegate other principal → 403 APPROVAL_NOT_AUTHORIZED_APPROVER")
    void nonDelegateForbidden() throws Exception {
        String id = createMultiStage("emp-sub", "k-dg-other-create", "emp-app1", "emp-app2");
        submitOk(id, "emp-sub", "k-dg-other-submit");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-nobody", "erp.write"))
                        .header("Idempotency-Key", "k-dg-other-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("AC-3: expired grant → 403; revoked grant → 403")
    void expiredAndRevokedForbidden() throws Exception {
        // expired grant: window entirely in the past.
        createGrant("emp-exp-app", "emp-exp-d", "2020-01-01T00:00:00Z",
                "2020-12-31T00:00:00Z", "k-dg-exp-grant");
        String idExp = createMultiStage("emp-sub", "k-dg-exp-create", "emp-exp-app", "emp-app2");
        submitOk(idExp, "emp-sub", "k-dg-exp-submit");
        mockMvc.perform(post("/api/erp/approval/requests/" + idExp + "/approve")
                        .header("Authorization", "Bearer " + token("emp-exp-d", "erp.write"))
                        .header("Idempotency-Key", "k-dg-exp-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));

        // revoked grant.
        String revGrant = createGrant("emp-rev-app", "emp-rev-d", FROM, TO, "k-dg-rev-grant");
        mockMvc.perform(post("/api/erp/approval/delegations/" + revGrant + "/revoke")
                        .header("Authorization", "Bearer " + token("emp-rev-app", "erp.write"))
                        .header("Idempotency-Key", "k-dg-rev-revoke")
                        .contentType("application/json").content("{\"reason\":\"back\"}"))
                .andExpect(status().isOk());
        String idRev = createMultiStage("emp-sub", "k-dg-rev-create", "emp-rev-app", "emp-app2");
        submitOk(idRev, "emp-sub", "k-dg-rev-submit");
        mockMvc.perform(post("/api/erp/approval/requests/" + idRev + "/approve")
                        .header("Authorization", "Bearer " + token("emp-rev-d", "erp.write"))
                        .header("Idempotency-Key", "k-dg-rev-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("AC-3: delegate == submitter → refused (self-approval via delegation, SoD)")
    void delegateIsSubmitterRefused() throws Exception {
        // grant emp-app → emp-sub; emp-sub is the request submitter → must be refused.
        createGrant("emp-app", "emp-sub", FROM, TO, "k-dg-sod-grant");
        String id = createMultiStage("emp-sub", "k-dg-sod-create", "emp-app");
        submitOk(id, "emp-sub", "k-dg-sod-submit");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-dg-sod-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    // ---- AC-4: withdraw never delegable ----

    @Test
    @DisplayName("AC-4: withdraw stays submitter-only — a delegate cannot withdraw")
    void withdrawNotDelegable() throws Exception {
        createGrant("emp-app", "emp-d", FROM, TO, "k-dg-wd-grant");
        String id = createMultiStage("emp-sub", "k-dg-wd-create", "emp-app");
        submitOk(id, "emp-sub", "k-dg-wd-submit");
        // the delegate of the approver tries to withdraw → not the submitter → 403.
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/withdraw")
                        .header("Authorization", "Bearer " + token("emp-d", "erp.write"))
                        .header("Idempotency-Key", "k-dg-wd-withdraw")
                        .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));

        // the submitter can still withdraw.
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/withdraw")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-dg-wd-withdraw-sub")
                        .contentType("application/json").content("{\"reason\":\"stop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    @DisplayName("AC-4 regression: a request with NO delegation behaves exactly as v2.0 "
            + "(direct approver, no actingForApproverId)")
    void noDelegationRegression() throws Exception {
        String id = createMultiStage("emp-sub", "k-dg-reg-create", "emp-app1", "emp-app2");
        submitOk(id, "emp-sub", "k-dg-reg-submit");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-app1", "erp.write"))
                        .header("Idempotency-Key", "k-dg-reg-approve-0")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));

        String onBehalfOf = jdbcTemplate.queryForObject(
                "SELECT on_behalf_of FROM approval_action WHERE approval_request_id = ? AND actor = ?",
                String.class, id, "emp-app1");
        assertThat(onBehalfOf).isNull();   // direct approver → no delegation recorded
    }

    // ---- TASK-ERP-BE-017: per-request scope ----

    @Test
    @DisplayName("BE-017 AC-1: REQUEST-scoped grant authorizes the delegate for R1 only; "
            + "R2 of the same approver → 403 APPROVAL_NOT_AUTHORIZED_APPROVER")
    void requestScopedAuthorizesOneRequestOnly() throws Exception {
        // two requests, same approver emp-rq-app; grant scoped to R1 only.
        String r1 = createMultiStage("emp-sub", "k-rq-r1-create", "emp-rq-app");
        submitOk(r1, "emp-sub", "k-rq-r1-submit");
        String r2 = createMultiStage("emp-sub", "k-rq-r2-create", "emp-rq-app");
        submitOk(r2, "emp-sub", "k-rq-r2-submit");

        createGrant("emp-rq-app", "emp-rq-d", FROM, TO, "k-rq-grant", "REQUEST", r1);

        // delegate approves R1 → authorized (scope covers R1).
        mockMvc.perform(post("/api/erp/approval/requests/" + r1 + "/approve")
                        .header("Authorization", "Bearer " + token("emp-rq-d", "erp.write"))
                        .header("Idempotency-Key", "k-rq-r1-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // delegate approves R2 → fail-closed (scope does not cover R2).
        mockMvc.perform(post("/api/erp/approval/requests/" + r2 + "/approve")
                        .header("Authorization", "Bearer " + token("emp-rq-d", "erp.write"))
                        .header("Idempotency-Key", "k-rq-r2-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("BE-017 AC-2 regression: a GLOBAL grant still authorizes any request "
            + "(byte-unchanged blanket behavior)")
    void globalGrantUnchangedAcrossRequests() throws Exception {
        createGrant("emp-gl-app", "emp-gl-d", FROM, TO, "k-gl-grant");
        // two distinct requests of the same approver — GLOBAL covers both.
        String rA = createMultiStage("emp-sub", "k-gl-rA-create", "emp-gl-app");
        submitOk(rA, "emp-sub", "k-gl-rA-submit");
        String rB = createMultiStage("emp-sub", "k-gl-rB-create", "emp-gl-app");
        submitOk(rB, "emp-sub", "k-gl-rB-submit");

        mockMvc.perform(post("/api/erp/approval/requests/" + rA + "/approve")
                        .header("Authorization", "Bearer " + token("emp-gl-d", "erp.write"))
                        .header("Idempotency-Key", "k-gl-rA-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(post("/api/erp/approval/requests/" + rB + "/approve")
                        .header("Authorization", "Bearer " + token("emp-gl-d", "erp.write"))
                        .header("Idempotency-Key", "k-gl-rB-approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("BE-017 AC-4: REQUEST grant view carries scope=REQUEST + scopeRequestId; "
            + "GLOBAL view scopeRequestId ABSENT")
    void viewCarriesScope() throws Exception {
        MvcResult reqRes = mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-vw-app", "erp.write"))
                        .header("Idempotency-Key", "k-vw-req")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-vw-d\",\"validFrom\":\"" + FROM
                                + "\",\"scope\":\"REQUEST\",\"scopeRequestId\":\"appr-vw-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("REQUEST"))
                .andExpect(jsonPath("$.data.scopeRequestId").value("appr-vw-1"))
                .andReturn();
        assertThat(reqRes.getResponse().getContentAsString()).contains("\"scope\":\"REQUEST\"");

        // GLOBAL (default) → scope present, scopeRequestId ABSENT.
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-vw-app2", "erp.write"))
                        .header("Idempotency-Key", "k-vw-global")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-vw-d2\",\"validFrom\":\"" + FROM + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.data.scopeRequestId").doesNotExist());
    }

    @Test
    @DisplayName("BE-017 AC-3: REQUEST scope with blank scopeRequestId → 422 DELEGATION_INVALID; "
            + "GLOBAL with a scopeRequestId → 422; unknown scope string → 400 VALIDATION_ERROR")
    void scopeCoherenceAndUnknownScope() throws Exception {
        // REQUEST without scopeRequestId → 422 (coherence).
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-sc-req-noid")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-d\",\"validFrom\":\"" + FROM
                                + "\",\"scope\":\"REQUEST\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELEGATION_INVALID"));

        // GLOBAL with a scopeRequestId → 422 (coherence).
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-sc-global-id")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-d\",\"validFrom\":\"" + FROM
                                + "\",\"scope\":\"GLOBAL\",\"scopeRequestId\":\"appr-x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DELEGATION_INVALID"));

        // unknown scope string → 400 (client error, not 422).
        mockMvc.perform(post("/api/erp/approval/delegations")
                        .header("Authorization", "Bearer " + token("emp-a", "erp.write"))
                        .header("Idempotency-Key", "k-sc-unknown")
                        .contentType("application/json")
                        .content("{\"delegateId\":\"emp-d\",\"validFrom\":\"" + FROM
                                + "\",\"scope\":\"PER_ROUTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("BE-017 AC-7 (§16): DB CHECK rejects scope=REQUEST with null scope_request_id "
            + "(coherence enforced at the database, bypassing the factory)")
    void checkConstraintRejectsIncoherentRow() {
        // A native insert bypassing the domain factory must be rejected by
        // ck_delegation_grant_scope_req (Docker-free :check would falsely pass).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO delegation_grant (id, tenant_id, delegator_id, delegate_id, "
                        + "valid_from, status, created_at, created_by, version, scope, scope_request_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                "dgr-bad-1", TENANT_ERP, "emp-a", "emp-d",
                java.sql.Timestamp.from(java.time.Instant.parse(FROM)),
                "ACTIVE", java.sql.Timestamp.from(java.time.Instant.parse(FROM)),
                "emp-a", 0L, "REQUEST", null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // Symmetric: GLOBAL with a non-null scope_request_id is also rejected.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO delegation_grant (id, tenant_id, delegator_id, delegate_id, "
                        + "valid_from, status, created_at, created_by, version, scope, scope_request_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                "dgr-bad-2", TENANT_ERP, "emp-a", "emp-d",
                java.sql.Timestamp.from(java.time.Instant.parse(FROM)),
                "ACTIVE", java.sql.Timestamp.from(java.time.Instant.parse(FROM)),
                "emp-a", 0L, "GLOBAL", "appr-x"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
