package com.example.erp.approval.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-ERP-BE-041 regression guard for the submit-time subject reference check.
 *
 * <p>Two independent defects are pinned here, because fixing one without the other
 * reproduces the original operator experience:
 *
 * <ol>
 *   <li><strong>Identity</strong> — the masterdata call carries the caller's bearer token.
 *       Before the fix it carried nothing, masterdata-service answered 401, and submit was
 *       refused for every request in the demo stack. <em>Bite</em>: delete the
 *       {@code .header(AUTHORIZATION, …)} line in {@code MasterDataRestAdapter} and
 *       {@link #propagatesTheCallersBearerToken()} plus every happy-path IT go RED,
 *       because the stub now answers 401 to an anonymous call exactly as the real resource
 *       server does.</li>
 *   <li><strong>Classification</strong> — an authentication failure and an absent subject
 *       no longer collapse into the same silent {@code false}. Only the first is counted on
 *       {@code approval_subject_resolve_failures_total{cause}}. The 404 case in
 *       {@link #anAbsentSubjectIsAnAnswerNotAResolveFailure()} is the control: it takes the
 *       same refusal path and must leave every failure counter untouched, so a test that
 *       merely asserted "the counter moved" could not pass by accident.</li>
 * </ol>
 *
 * <p>Both refusals still surface as 422 {@code APPROVAL_ROUTE_INVALID}
 * ({@code details.cause = "subject_unresolved"}) — architecture.md § Reference Integrity
 * model states that a non-resolvable subject, unreachable masterdata included, refuses the
 * submit under that one code. What TASK-ERP-BE-041 changes is that an operator can now tell
 * the two apart, which was the whole failure: a 401 was being reported as a verdict about
 * the customer's data.
 */
@AutoConfigureMockMvc
class SubjectResolveIdentityIntegrationTest extends AbstractApprovalIntegrationTest {

    private static final String COUNTER = "approval_subject_resolve_failures_total";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void resetMasterStub() {
        masterStatus = "ACTIVE";
        masterHttpStatus = 200;
        masterSeenAuthorization = null;
    }

    @Test
    @DisplayName("the masterdata reference call carries the caller's own bearer token")
    void propagatesTheCallersBearerToken() throws Exception {
        String callerToken = token("emp-sub-041a", "erp.write");
        String id = create(callerToken, "emp-app-041a", "k-041-a");

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + callerToken)
                        .header("Idempotency-Key", "k-041-a-submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // Not merely "some Authorization header": the caller's exact token. A workload
        // token would satisfy a non-null assertion and still be the wrong identity.
        assertThat(masterSeenAuthorization).isEqualTo("Bearer " + callerToken);
    }

    @Test
    @DisplayName("masterdata 401 → refused AND counted as cause=auth (not a silent subject verdict)")
    void anAuthenticationFailureIsCountedUnderItsOwnCause() throws Exception {
        String callerToken = token("emp-sub-041b", "erp.write");
        String id = create(callerToken, "emp-app-041b", "k-041-b");

        double authBefore = failures("auth");
        masterHttpStatus = 401;

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + callerToken)
                        .header("Idempotency-Key", "k-041-b-submit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("subject_unresolved"));

        assertThat(failures("auth")).isEqualTo(authBefore + 1);
        assertThat(statusOf(id, callerToken)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("masterdata 404 → refused but NOT counted: an absent subject is an answer")
    void anAbsentSubjectIsAnAnswerNotAResolveFailure() throws Exception {
        String callerToken = token("emp-sub-041c", "erp.write");
        String id = create(callerToken, "emp-app-041c", "k-041-c");

        double totalBefore = totalFailures();
        masterHttpStatus = 404;

        mockMvc.perform(post("/api/erp/approval/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + callerToken)
                        .header("Idempotency-Key", "k-041-c-submit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.cause").value("subject_unresolved"));

        assertThat(totalFailures()).isEqualTo(totalBefore);
        assertThat(statusOf(id, callerToken)).isEqualTo("DRAFT");
    }

    // ---- helpers ----

    private double failures(String cause) {
        return Search.in(meterRegistry).name(COUNTER).tag("cause", cause)
                .counter().count();
    }

    /** Every {@code cause} series summed — the 404 control must not move any of them. */
    private double totalFailures() {
        return Search.in(meterRegistry).name(COUNTER).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    private String create(String callerToken, String approverId, String key) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Authorization", "Bearer " + callerToken)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-041\","
                                + "\"title\":\"t\",\"approverId\":\"" + approverId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private String statusOf(String id, String callerToken) throws Exception {
        MvcResult res = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/erp/approval/requests/" + id)
                                .header("Authorization", "Bearer " + callerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("data").get("status").asText();
    }
}
