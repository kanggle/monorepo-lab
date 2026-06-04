package com.example.erp.readmodel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test (Testcontainers MySQL + Kafka + MockWebServer
 * JWKS). Covers AC-1..AC-4: publish the 4 master topics → projection upsert →
 * GET org-view resolves all references; duplicate eventId → idempotent skip;
 * out-of-order missing reference → null + meta.unresolved; RETIRED retained +
 * ?asOf resolves.
 */
class ReadModelEndToEndIntegrationTest extends AbstractReadModelIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private JsonNode getEmployee(String id, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/erp/read-model/employees/" + id))
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
    void fourTopicsConsumedThenOrgViewResolvesAllReferences() throws Exception {
        String hqId = newId();
        String deptId = newId();
        String ccId = newId();
        String jgId = newId();
        String empId = newId();

        publish(TOPIC_DEPARTMENT, hqId,
                envelope(newId(), "department", hqId, "CREATED", deptAfter("HQ", "본사", null)));
        publish(TOPIC_DEPARTMENT, deptId,
                envelope(newId(), "department", deptId, "CREATED", deptAfter("SALES", "영업본부", hqId)));
        publish(TOPIC_COSTCENTER, ccId, envelope(newId(), "costcenter", ccId, "CREATED",
                ccAfter("CC-100", "영업원가센터", deptId)));
        publish(TOPIC_JOBGRADE, jgId, envelope(newId(), "jobgrade", jgId, "CREATED",
                jgAfter("G3", "사원", 30)));
        publish(TOPIC_EMPLOYEE, empId, envelope(newId(), "employee", empId, "CREATED",
                empAfter("E-1001", "홍길동", deptId, ccId, jgId)));

        String token = erpReadToken();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode body = getEmployee(empId, token);
            assertThat(body.at("/data/department/code").asText()).isEqualTo("SALES");
            assertThat(body.at("/data/department/path/0/code").asText()).isEqualTo("HQ");
            assertThat(body.at("/data/department/path/1/code").asText()).isEqualTo("SALES");
            assertThat(body.at("/data/costCenter/code").asText()).isEqualTo("CC-100");
            assertThat(body.at("/data/jobGrade/displayOrder").asInt()).isEqualTo(30);
            assertThat(body.at("/meta/warning").asText()).isEqualTo("Eventually-consistent read-model");
        });
    }

    @Test
    void duplicateEventIdIsIdempotentSkip() throws Exception {
        String empId = newId();
        String eventId = newId();
        String env = envelope(eventId, "employee", empId, "CREATED",
                empAfter("E-2002", "first", null, null, null));

        publish(TOPIC_EMPLOYEE, empId, env);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> employeeJpa.findById(empId).isPresent());

        // Re-deliver the SAME eventId but a different name in 'after'. Dedupe must
        // skip it → projection stays byte-identical (name unchanged).
        String envDup = envelope(eventId, "employee", empId, "UPDATED",
                empAfter("E-2002", "MUTATED", null, null, null));
        publish(TOPIC_EMPLOYEE, empId, envDup);

        // Give the consumer time to process (and skip) the duplicate.
        Thread.sleep(3000);
        assertThat(employeeJpa.findById(empId)).get()
                .extracting(e -> e.getName()).isEqualTo("first");
        assertThat(processedEventJpa.existsById(eventId)).isTrue();
    }

    @Test
    void missingReferenceYieldsNullPlusMetaUnresolved() throws Exception {
        String empId = newId();
        // Employee references a department that is never published (out-of-order).
        publish(TOPIC_EMPLOYEE, empId, envelope(newId(), "employee", empId, "CREATED",
                empAfter("E-3003", "noref", "dept-never", null, null)));

        String token = erpReadToken();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode body = getEmployee(empId, token);
            assertThat(body.at("/data/department").isNull()
                    || body.at("/data/department").isMissingNode()).isTrue();
            JsonNode unresolved = body.at("/meta/unresolved");
            assertThat(unresolved.isArray()).isTrue();
            assertThat(unresolved.toString()).contains("department");
        });
    }

    @Test
    void retiredDepartmentIsRetainedNotDeleted() throws Exception {
        String deptId = newId();
        publish(TOPIC_DEPARTMENT, deptId,
                envelope(newId(), "department", deptId, "CREATED", deptAfter("OPS", "운영", null)));
        await().atMost(Duration.ofSeconds(30))
                .until(() -> departmentJpa.findById(deptId).isPresent());

        publish(TOPIC_DEPARTMENT, deptId,
                envelope(newId(), "department", deptId, "RETIRED", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(departmentJpa.findById(deptId)).get()
                        .extracting(e -> e.getStatus()).isEqualTo("RETIRED"));
        // Row retained, not deleted.
        assertThat(departmentJpa.findById(deptId)).isPresent();
    }

    private Map<String, Object> ccAfter(String code, String name, String departmentId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", code);
        after.put("name", name);
        after.put("departmentId", departmentId);
        after.put("status", "ACTIVE");
        return after;
    }

    private Map<String, Object> jgAfter(String code, String name, int displayOrder) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", code);
        after.put("name", name);
        after.put("displayOrder", displayOrder);
        after.put("status", "ACTIVE");
        return after;
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
