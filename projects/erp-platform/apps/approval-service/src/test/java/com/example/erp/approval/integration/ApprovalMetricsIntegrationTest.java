package com.example.erp.approval.integration;

import com.example.common.time.KstPeriodBounds;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalRequestJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/erp/approval/metrics against real MySQL (TASK-MONO-809, ADR-MONO-084 P4).
 *
 * <p>Rows are seeded through the real endpoints (so the timestamps are the ones the service
 * writes) except the window-boundary and other-tenant rows, which are saved through the JPA
 * repository with explicit instants. Every test uses employee ids unique to the run and reads
 * through a <em>department-scoped</em> token (participant scope) so absolute values can be
 * asserted in a database shared with the other ITs; the tenant-wide reads assert deltas.
 */
@AutoConfigureMockMvc
class ApprovalMetricsIntegrationTest extends AbstractApprovalIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApprovalRequestJpaRepository requestJpa;

    private String u;

    @BeforeEach
    void setUp() {
        masterStatus = "ACTIVE";
        masterHttpStatus = 200;
        resetPeople();
        u = UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- helpers -----------------------------------------------------------

    private String key(String what) {
        return what + "-" + u + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String createRequest(String submitter, String... approvers) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < approvers.length; i++) {
            ids.append(i > 0 ? "," : "").append('"').append(approvers[i]).append('"');
        }
        MvcResult res = mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + token(submitter, "erp.write"))
                        .header("Idempotency-Key", key("create"))
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverIds\":[" + ids + "]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private void submit(String id, String submitter) throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + token(submitter, "erp.write"))
                        .header("Idempotency-Key", key("submit")))
                .andExpect(status().isOk());
    }

    private void approve(String id, String approver, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + token(approver, "erp.write"))
                        .header("Idempotency-Key", key("approve"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(expectedStatus));
    }

    private void reject(String id, String approver) throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + token(approver, "erp.write"))
                        .header("Idempotency-Key", key("reject"))
                        .contentType("application/json").content("{\"reason\":\"no\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    private void withdraw(String id, String submitter) throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/withdraw")
                        .header("Authorization", "Bearer " + token(submitter, "erp.write"))
                        .header("Idempotency-Key", key("withdraw"))
                        .contentType("application/json").content("{\"reason\":\"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    /** Participant-scoped read: a department org_scope, so the caller sees only own requests. */
    private JsonNode metricsAs(String account) throws Exception {
        return read(token(account, "erp.read", TENANT_ERP, null, "dept-1"));
    }

    /** Platform-scope read ({@code org_scope "*"}): tenant-wide. */
    private JsonNode tenantWideMetrics() throws Exception {
        return read(token("op-" + u, "erp.read"));
    }

    private JsonNode read(String bearer) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/erp/approval/metrics").param("period", "month")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static long cur(JsonNode m, String metricKey) {
        for (JsonNode x : m.get("metrics")) {
            if (x.get("key").asText().equals(metricKey)) {
                return Long.parseLong(x.get("current").asText());
            }
        }
        throw new AssertionError("no metric " + metricKey);
    }

    private static void assertCounts(JsonNode m, long submitted, long approved, long rejected) {
        assertThat(cur(m, "submitted")).as("submitted").isEqualTo(submitted);
        assertThat(cur(m, "approved")).as("approved").isEqualTo(approved);
        assertThat(cur(m, "rejected")).as("rejected").isEqualTo(rejected);
    }

    private ApprovalRequest seed(String tenant, String submitter, String approver, Instant submittedAt) {
        ApprovalRequest r = ApprovalRequest.createDraft("appr-m809-" + UUID.randomUUID(), tenant,
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "seeded", null,
                ApprovalRoute.singleStage(submitter, approver), submitter, submittedAt);
        r.submit(submittedAt);
        return requestJpa.saveAndFlush(r);
    }

    // ---- 200 shape ---------------------------------------------------------

    @Test
    @DisplayName("200: shared shape, three count metrics in order, integer strings, series zero-filled")
    void shape() throws Exception {
        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week")
                        .header("Authorization", "Bearer " + token("op-" + u, "erp.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.metrics[0].key").value("submitted"))
                .andExpect(jsonPath("$.metrics[1].key").value("approved"))
                .andExpect(jsonPath("$.metrics[2].key").value("rejected"))
                .andExpect(jsonPath("$.metrics[0].unit").value("count"))
                .andExpect(jsonPath("$.metrics[0].currency").doesNotExist())
                .andExpect(jsonPath("$.metrics[0].current").isString())
                .andExpect(jsonPath("$.series[0].values.submitted").isString());
    }

    // ---- AC-1 control group ------------------------------------------------

    @Test
    @DisplayName("control: a draft that was never submitted moves nothing")
    void draftNeverSubmitted() throws Exception {
        String sub = "sub-" + u;
        createRequest(sub, "app-" + u);

        assertCounts(metricsAs(sub), 0, 0, 0);
    }

    @Test
    @DisplayName("control: a draft withdrawn before submit has no submitted_at → counts nowhere")
    void draftWithdrawnBeforeSubmit() throws Exception {
        String sub = "sub-" + u;
        String id = createRequest(sub, "app-" + u);
        withdraw(id, sub);

        // withdraw() stamps finalized_at too — only the status filter keeps it out of approved/rejected.
        assertCounts(metricsAs(sub), 0, 0, 0);
    }

    @Test
    @DisplayName("control: submitted then withdrawn → submitted 1 (it happened), approved 0, rejected 0")
    void submittedThenWithdrawn() throws Exception {
        String sub = "sub-" + u;
        String id = createRequest(sub, "app-" + u);
        submit(id, sub);
        withdraw(id, sub);

        assertCounts(metricsAs(sub), 1, 0, 0);
    }

    @Test
    @DisplayName("control: single-stage approval moves approved only")
    void singleStageApproval() throws Exception {
        String sub = "sub-" + u;
        String app = "app-" + u;
        String id = createRequest(sub, app);
        submit(id, sub);
        assertCounts(metricsAs(sub), 1, 0, 0);

        approve(id, app, "APPROVED");

        assertCounts(metricsAs(sub), 1, 1, 0);
    }

    @Test
    @DisplayName("control: 2-stage — stage-1 approval (IN_REVIEW) is NOT approved; the final one is")
    void twoStageApprovedOnlyAtFinalStage() throws Exception {
        String sub = "sub-" + u;
        String a = "appa-" + u;
        String b = "appb-" + u;
        String id = createRequest(sub, a, b);
        submit(id, sub);

        approve(id, a, "IN_REVIEW");
        // approval_action already holds an APPROVED row here — the request is still not approved.
        assertCounts(metricsAs(sub), 1, 0, 0);

        approve(id, b, "APPROVED");
        assertCounts(metricsAs(sub), 1, 1, 0);
    }

    @Test
    @DisplayName("control: rejection at stage 1 of a 2-stage request → rejected 1, approved 0")
    void rejectionAtFirstOfTwoStages() throws Exception {
        String sub = "sub-" + u;
        String a = "appa-" + u;
        String b = "appb-" + u;
        String id = createRequest(sub, a, b);
        submit(id, sub);
        reject(id, a);

        assertCounts(metricsAs(sub), 1, 0, 1);
    }

    // ---- window ------------------------------------------------------------

    @Test
    @DisplayName("window is half-open [from, to): from inclusive, 1µs before excluded, future excluded")
    void halfOpenWindow() throws Exception {
        String sub = "sub-" + u;
        Instant monthStart = KstPeriodBounds.now().monthStartInstant();
        seed(TENANT_ERP, sub, "app-" + u, monthStart);                                    // in (inclusive)
        seed(TENANT_ERP, sub, "app-" + u, monthStart.minus(1, ChronoUnit.MICROS));        // out
        seed(TENANT_ERP, sub, "app-" + u, Instant.now().plus(1, ChronoUnit.HOURS));       // out (>= to)
        seed(TENANT_ERP, sub, "app-" + u, Instant.now().minus(1, ChronoUnit.SECONDS));    // in

        JsonNode m = metricsAs(sub);

        assertThat(cur(m, "submitted")).isEqualTo(2);
    }

    // ---- scope -------------------------------------------------------------

    @Test
    @DisplayName("participant scope: submitter and current approver see it; a stranger sees zeros")
    void participantScope() throws Exception {
        String sub = "sub-" + u;
        String app = "app-" + u;
        String stranger = "stranger-" + u;
        String id = createRequest(sub, app);
        submit(id, sub);

        assertCounts(metricsAs(sub), 1, 0, 0);
        assertCounts(metricsAs(app), 1, 0, 0);
        assertCounts(metricsAs(stranger), 0, 0, 0);
    }

    @Test
    @DisplayName("unlinked non-operator caller → 200 with all zeros, not an error")
    void unlinkedCallerGetsZeros() throws Exception {
        String sub = "sub-" + u;
        String id = createRequest(sub, "app-" + u);
        submit(id, sub);
        String unlinked = "unlinked-" + u;
        UNLINKED_ACCOUNTS.add(unlinked);

        assertCounts(metricsAs(unlinked), 0, 0, 0);
    }

    @Test
    @DisplayName("platform scope is tenant-wide: it sees a request of someone it is no participant of")
    void tenantWideSeesEverything() throws Exception {
        JsonNode before = tenantWideMetrics();
        String sub = "sub-" + u;
        String id = createRequest(sub, "app-" + u);
        submit(id, sub);

        JsonNode after = tenantWideMetrics();

        assertThat(cur(after, "submitted")).isEqualTo(cur(before, "submitted") + 1);
    }

    // ---- tenant isolation --------------------------------------------------

    @Test
    @DisplayName("tenant isolation: another tenant's requests (same participant id) are not counted")
    void tenantIsolation() throws Exception {
        String sub = "sub-" + u;
        JsonNode wideBefore = tenantWideMetrics();
        // Same submitter id, other tenant, submitted just now — must not reach the 'erp' reads.
        seed("other-tenant", sub, "app-" + u, Instant.now().minus(1, ChronoUnit.SECONDS));

        assertCounts(metricsAs(sub), 0, 0, 0);
        assertThat(cur(tenantWideMetrics(), "submitted")).isEqualTo(cur(wideBefore, "submitted"));
    }

    // ---- request validation / auth ----------------------------------------

    @Test
    @DisplayName("period missing or unknown → 400 VALIDATION_ERROR")
    void periodInvalid() throws Exception {
        String bearer = token("op-" + u, "erp.read");
        mockMvc.perform(get("/api/erp/approval/metrics").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "today")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("no token → 401; a token without any ERP read role → 403; cross-tenant token → 403 TENANT_FORBIDDEN")
    void authGates() throws Exception {
        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week")
                        .header("Authorization", "Bearer " + token("nobody-" + u, "profile")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/erp/approval/metrics").param("period", "week")
                        .header("Authorization", "Bearer " + token("u", "erp.read", "wms", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }
}
