package com.example.erp.readmodel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

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
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for the approval-fact projection (TASK-ERP-BE-010,
 * Testcontainers MySQL + Kafka + MockWebServer JWKS). Covers AC-1..AC-5:
 * produce approval events → consume → project → read; terminal-once; out-of-order
 * (terminal before submitted); org_scope list (in-scope) / detail 404
 * (out-of-scope); subject read-time resolution; and net-zero employee org-view
 * regression (the masterdata consumers still project an employee view).
 */
class ApprovalFactProjectionIntegrationTest extends AbstractReadModelIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getApproval(String id, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/erp/read-model/approvals/" + id))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode listApprovals(String query, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/erp/read-model/approvals" + query))
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

    @Test
    void submittedThenApprovedProjectsTerminalFactAndResolvesSubject() throws Exception {
        String deptId = newId();
        String apprId = newId();
        publish(TOPIC_DEPARTMENT, deptId,
                envelope(newId(), "department", deptId, "CREATED", deptAfter("SALES", "영업본부", null)));

        publish(TOPIC_APPROVAL_SUBMITTED, apprId, approvalEnvelope(newId(),
                "erp.approval.submitted", apprId, "DEPARTMENT", deptId,
                "emp-appr", "emp-sub", null, null));

        String token = erpReadToken();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpResponse<String> resp = getApproval(apprId, token);
            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode body = objectMapper.readTree(resp.body());
            assertThat(body.at("/data/status").asText()).isEqualTo("SUBMITTED");
            assertThat(body.at("/data/subject/code").asText()).isEqualTo("SALES");
            assertThat(body.at("/data/finalizedAt").isMissingNode()).isTrue();
        });

        // Approve → terminal.
        publish(TOPIC_APPROVAL_APPROVED, apprId, approvalEnvelope(newId(),
                "erp.approval.approved", apprId, "DEPARTMENT", deptId,
                "emp-appr", "emp-sub", Instant.now().toString(), null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode body = objectMapper.readTree(getApproval(apprId, token).body());
            assertThat(body.at("/data/status").asText()).isEqualTo("APPROVED");
            assertThat(body.at("/data/finalizedAt").isMissingNode()).isFalse();
        });
    }

    @Test
    void terminalOnce_lateSubmittedDoesNotRevertTerminal() throws Exception {
        String apprId = newId();
        // Approve first (terminal).
        publish(TOPIC_APPROVAL_APPROVED, apprId, approvalEnvelope(newId(),
                "erp.approval.approved", apprId, "EMPLOYEE", "emp-1",
                "emp-appr", "emp-sub", Instant.now().toString(), null));
        await().atMost(Duration.ofSeconds(30))
                .until(() -> approvalFactJpa.findById(apprId).isPresent());

        // A late SUBMITTED for the same request must NOT revert to SUBMITTED.
        publish(TOPIC_APPROVAL_SUBMITTED, apprId, approvalEnvelope(newId(),
                "erp.approval.submitted", apprId, "EMPLOYEE", "emp-1",
                "emp-appr", "emp-sub", null, null));
        Thread.sleep(3000);

        assertThat(approvalFactJpa.findById(apprId)).get()
                .extracting(e -> e.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void outOfOrderTerminalBeforeSubmittedLeavesSubmittedAtNull() throws Exception {
        String apprId = newId();
        // Terminal arrives with no prior submitted (replay-from-middle).
        publish(TOPIC_APPROVAL_REJECTED, apprId, approvalEnvelope(newId(),
                "erp.approval.rejected", apprId, "EMPLOYEE", "emp-1",
                "emp-appr", "emp-sub", Instant.now().toString(), "예산 근거 부족"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(approvalFactJpa.findById(apprId)).get()
                    .satisfies(e -> {
                        assertThat(e.getStatus()).isEqualTo("REJECTED");
                        assertThat(e.getSubmittedAt()).isNull();
                        assertThat(e.getLastReason()).isEqualTo("예산 근거 부족");
                    });
        });
    }

    @Test
    void orgScopeListAndDetailFilterBySubjectDepartment() throws Exception {
        // Two department subtrees; the operator is scoped to dept-in only.
        String deptIn = newId();
        String deptOut = newId();
        publish(TOPIC_DEPARTMENT, deptIn,
                envelope(newId(), "department", deptIn, "CREATED", deptAfter("IN", "스코프내", null)));
        publish(TOPIC_DEPARTMENT, deptOut,
                envelope(newId(), "department", deptOut, "CREATED", deptAfter("OUT", "스코프밖", null)));

        String apprIn = newId();
        String apprOut = newId();
        publish(TOPIC_APPROVAL_SUBMITTED, apprIn, approvalEnvelope(newId(),
                "erp.approval.submitted", apprIn, "DEPARTMENT", deptIn,
                "emp-appr", "emp-sub", null, null));
        publish(TOPIC_APPROVAL_SUBMITTED, apprOut, approvalEnvelope(newId(),
                "erp.approval.submitted", apprOut, "DEPARTMENT", deptOut,
                "emp-appr", "emp-sub", null, null));

        await().atMost(Duration.ofSeconds(30)).until(() ->
                approvalFactJpa.findById(apprIn).isPresent()
                        && approvalFactJpa.findById(apprOut).isPresent());

        // org_scope token scoped to dept-in only.
        String scopedToken = token(c -> c.claim("tenant_id", "erp").claim("scope", "erp.read")
                .claim("org_scope", List.of(deptIn)));

        // List: only the in-scope fact appears.
        JsonNode list = listApprovals("?subjectType=DEPARTMENT&size=100", scopedToken);
        JsonNode data = list.at("/data");
        boolean hasIn = false;
        boolean hasOut = false;
        for (JsonNode node : data) {
            String id = node.at("/approvalRequestId").asText();
            if (id.equals(apprIn)) hasIn = true;
            if (id.equals(apprOut)) hasOut = true;
        }
        assertThat(hasIn).isTrue();
        assertThat(hasOut).isFalse();

        // Detail: in-scope → 200; out-of-scope → 404 (no existence leak).
        assertThat(getApproval(apprIn, scopedToken).statusCode()).isEqualTo(200);
        assertThat(getApproval(apprOut, scopedToken).statusCode()).isEqualTo(404);

        // Platform token (no org_scope) sees both (net-zero).
        String platformToken = erpReadToken();
        assertThat(getApproval(apprOut, platformToken).statusCode()).isEqualTo(200);
    }

    @Test
    void netZeroEmployeeOrgViewStillProjects() throws Exception {
        // The masterdata consumers are unaffected by the approval additions.
        String empId = newId();
        publish(TOPIC_EMPLOYEE, empId, envelope(newId(), "employee", empId, "CREATED",
                empAfter("E-9001", "net-zero", null, null, null)));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> employeeJpa.findById(empId).isPresent());
        assertThat(employeeJpa.findById(empId)).get()
                .extracting(e -> e.getName()).isEqualTo("net-zero");
    }

    private Map<String, Object> empAfter(String employeeNumber, String name,
                                         String deptId, String ccId, String jgId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeNumber", employeeNumber);
        after.put("name", name);
        after.put("departmentId", deptId);
        after.put("costCenterId", ccId);
        after.put("jobGradeId", jgId);
        after.put("status", "ACTIVE");
        after.put("effectivePeriod", Map.of("effectiveFrom", "2026-01-01"));
        return after;
    }
}
