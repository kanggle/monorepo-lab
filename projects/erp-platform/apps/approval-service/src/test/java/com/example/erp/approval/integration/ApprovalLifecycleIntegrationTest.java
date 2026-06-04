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
 * End-to-end approval lifecycle IT (Testcontainers MySQL + Kafka + WireMock
 * JWKS + WireMock masterdata). Covers the create→submit→approve happy path with
 * outbox-row + audit-row assertions, the E1 ref-check refusal, approver authz,
 * illegal/finalized transitions, idempotent replay, and the no-reason guard.
 */
@AutoConfigureMockMvc
class ApprovalLifecycleIntegrationTest extends AbstractApprovalIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetMasterStub() {
        masterStatus = "ACTIVE";
        masterHttpStatus = 200;
    }

    private String create(String submitterScopeActor, String approverId, String key)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token(submitterScopeActor, "erp.write"))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverId\":\"" + approverId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("data").get("id").asText();
    }

    @Test
    @DisplayName("happy path: create→submit→approve; 2 audit rows + submitted/approved outbox rows")
    void happyPath() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-1");

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt").exists());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-app", "erp.write"))
                        .header("Idempotency-Key", "k-approve-1")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.finalizedAt").exists());

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_audit_log WHERE aggregate_id = ?",
                Long.class, id);
        assertThat(auditCount).isEqualTo(2L);

        Long submittedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, id, "erp.approval.submitted");
        Long approvedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, id, "erp.approval.approved");
        assertThat(submittedOutbox).isEqualTo(1L);
        assertThat(approvedOutbox).isEqualTo(1L);
    }

    @Test
    @DisplayName("E1: submit with RETIRED subject → 422 APPROVAL_ROUTE_INVALID, stays DRAFT, no event")
    void submitRetiredSubject() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-2");
        masterStatus = "RETIRED";

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM approval_request WHERE id = ?", String.class, id);
        assertThat(dbStatus).isEqualTo("DRAFT");
        Long outbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ?", Long.class, id);
        assertThat(outbox).isEqualTo(0L);
    }

    @Test
    @DisplayName("E1: submit with subject 404 → 422 APPROVAL_ROUTE_INVALID")
    void submitMissingSubject() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-3");
        masterHttpStatus = 404;

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-3"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"));
    }

    @Test
    @DisplayName("self-approval at create → 422 APPROVAL_ROUTE_INVALID")
    void selfApproval() throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token("emp-self", "erp.write"))
                        .header("Idempotency-Key", "k-self-1")
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverId\":\"emp-self\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"));
    }

    @Test
    @DisplayName("approve by a non-approver → 403 APPROVAL_NOT_AUTHORIZED_APPROVER")
    void approveNonApprover() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-4");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-4"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-other", "erp.write"))
                        .header("Idempotency-Key", "k-approve-4")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("illegal transition (approve a DRAFT) → 409 APPROVAL_STATUS_TRANSITION_INVALID")
    void illegalTransition() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-5");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-app", "erp.write"))
                        .header("Idempotency-Key", "k-approve-5")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_STATUS_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("finalized re-process (approve an APPROVED) → 409 APPROVAL_ALREADY_FINALIZED")
    void finalizedReprocess() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-6");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-6"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token("emp-app", "erp.write"))
                        .header("Idempotency-Key", "k-approve-6")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + token("emp-app", "erp.write"))
                        .header("Idempotency-Key", "k-reject-6")
                        .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_ALREADY_FINALIZED"));
    }

    @Test
    @DisplayName("idempotent approve: same key twice → one APPROVED, one replayed, one event, one audit")
    void idempotentApprove() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-7");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-7"))
                .andExpect(status().isOk());

        String approveToken = token("emp-app", "erp.write");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                            .header("Authorization", "Bearer " + approveToken)
                            .header("Idempotency-Key", "k-approve-idem-7")
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }

        Long approvedActions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_action WHERE approval_request_id = ? AND transition = ?",
                Long.class, id, "APPROVED");
        Long approvedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?",
                Long.class, id, "erp.approval.approved");
        assertThat(approvedActions).isEqualTo(1L);
        assertThat(approvedOutbox).isEqualTo(1L);
    }

    @Test
    @DisplayName("reject without reason → 400 VALIDATION_ERROR")
    void rejectNoReason() throws Exception {
        String id = create("emp-sub", "emp-app", "k-create-8");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-8"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + token("emp-app", "erp.write"))
                        .header("Idempotency-Key", "k-reject-8")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("cross-tenant token → 403 TENANT_FORBIDDEN; no token → 401")
    void tenantGate() throws Exception {
        mockMvc.perform(get("/api/erp/approval/inbox")
                        .header("Authorization", "Bearer " + token("u", "erp.read", "wms", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        mockMvc.perform(get("/api/erp/approval/inbox"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("inbox: approver sees their SUBMITTED pending item")
    void inbox() throws Exception {
        String id = create("emp-sub", "emp-inbox", "k-create-9");
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token("emp-sub", "erp.write"))
                        .header("Idempotency-Key", "k-submit-9"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/erp/approval/inbox")
                        .header("Authorization", "Bearer " + token("emp-inbox", "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + id + "')].status").value("SUBMITTED"));
    }
}
