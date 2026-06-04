package com.example.erp.readmodel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * org_scope read-filter IT (TASK-ERP-BE-008 / ADR-MONO-020 D3 amendment).
 * Builds a real department tree + employees via the Kafka projection pipeline
 * (Testcontainers MySQL + Kafka), then queries the org-view with operator
 * tokens carrying different {@code org_scope} claims and asserts the symmetric
 * subtree narrowing through the FULL HTTP stack (real ObjectMapper envelope):
 * <ul>
 *   <li>org_scope=[sales-root] → list returns ONLY sales-subtree employees; an
 *       out-of-scope employee detail → 404 {@code MASTERDATA_NOT_FOUND}.</li>
 *   <li>org_scope=["*"] / absent → ALL employees (net-zero — BE-007 behavior).</li>
 * </ul>
 *
 * <p>Tree:  sales-root ─ sales-east ;  eng-root ─ eng-platform.
 */
class OrgScopeReadFilterIntegrationTest extends AbstractReadModelIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private String salesEmpId;
    private String engEmpId;

    private void seedTreeAndEmployees() {
        String salesRoot = "ds-sales-root";
        String salesEast = "ds-sales-east";
        String engRoot = "ds-eng-root";
        String engPlatform = "ds-eng-platform";
        salesEmpId = newId();
        engEmpId = newId();

        publish(TOPIC_DEPARTMENT, salesRoot,
                envelope(newId(), "department", salesRoot, "CREATED", dept("SALES-ROOT", "영업", null)));
        publish(TOPIC_DEPARTMENT, salesEast,
                envelope(newId(), "department", salesEast, "CREATED", dept("SALES-EAST", "동부", salesRoot)));
        publish(TOPIC_DEPARTMENT, engRoot,
                envelope(newId(), "department", engRoot, "CREATED", dept("ENG-ROOT", "기술", null)));
        publish(TOPIC_DEPARTMENT, engPlatform,
                envelope(newId(), "department", engPlatform, "CREATED", dept("ENG-PLAT", "플랫폼", engRoot)));
        publish(TOPIC_EMPLOYEE, salesEmpId, envelope(newId(), "employee", salesEmpId, "CREATED",
                emp("E-SALES", "영업직원", salesEast)));
        publish(TOPIC_EMPLOYEE, engEmpId, envelope(newId(), "employee", engEmpId, "CREATED",
                emp("E-ENG", "기술직원", engPlatform)));

        // Wait until both employees + the tree are projected.
        await().atMost(Duration.ofSeconds(30)).until(() ->
                employeeJpa.findById(salesEmpId).isPresent()
                        && employeeJpa.findById(engEmpId).isPresent()
                        && departmentJpa.findById(salesEast).isPresent()
                        && departmentJpa.findById(engPlatform).isPresent());
    }

    @Test
    void scopedOperatorSeesOnlySubtreeAndOutOfScopeDetailIs404() throws Exception {
        seedTreeAndEmployees();

        // org_scope=[sales-root] → list contains only the sales-subtree employee.
        String salesScopedToken = token(c -> c.claim("tenant_id", "erp")
                .claim("scope", "erp.read").claim("org_scope", List.of("ds-sales-root")));

        JsonNode list = getList(salesScopedToken);
        JsonNode data = list.at("/data");
        assertThat(data.isArray()).isTrue();
        // Every returned employee id must be the sales employee, never the eng one.
        boolean sawSales = false;
        for (JsonNode row : data) {
            String id = row.at("/id").asText();
            assertThat(id).isNotEqualTo(engEmpId);
            if (id.equals(salesEmpId)) sawSales = true;
        }
        assertThat(sawSales).isTrue();

        // Out-of-scope employee detail → 404 (existence not leaked).
        assertThat(getStatus("/api/erp/read-model/employees/" + engEmpId, salesScopedToken))
                .isEqualTo(404);
        // In-scope employee detail → 200.
        assertThat(getStatus("/api/erp/read-model/employees/" + salesEmpId, salesScopedToken))
                .isEqualTo(200);
    }

    @Test
    void platformScopeAndAbsentScopeSeeAllNetZero() throws Exception {
        seedTreeAndEmployees();

        // org_scope=["*"] → no narrowing.
        String wildcardToken = token(c -> c.claim("tenant_id", "erp")
                .claim("scope", "erp.read").claim("org_scope", List.of("*")));
        assertContainsBothEmployees(getList(wildcardToken));
        assertThat(getStatus("/api/erp/read-model/employees/" + engEmpId, wildcardToken)).isEqualTo(200);

        // absent org_scope (the BE-007 erp.read token) → no narrowing.
        String absentToken = erpReadToken();
        assertContainsBothEmployees(getList(absentToken));
        assertThat(getStatus("/api/erp/read-model/employees/" + engEmpId, absentToken)).isEqualTo(200);
    }

    private void assertContainsBothEmployees(JsonNode list) {
        JsonNode data = list.at("/data");
        boolean sawSales = false;
        boolean sawEng = false;
        for (JsonNode row : data) {
            String id = row.at("/id").asText();
            if (id.equals(salesEmpId)) sawSales = true;
            if (id.equals(engEmpId)) sawEng = true;
        }
        assertThat(sawSales).isTrue();
        assertThat(sawEng).isTrue();
    }

    private JsonNode getList(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/erp/read-model/employees?page=0&size=100"))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return objectMapper.readTree(resp.body());
    }

    private int getStatus(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private Map<String, Object> dept(String code, String name, String parentId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", code);
        after.put("name", name);
        after.put("parentId", parentId);
        after.put("status", "ACTIVE");
        after.put("effectivePeriod", Map.of("effectiveFrom", "2026-01-01"));
        return after;
    }

    private Map<String, Object> emp(String employeeNumber, String name, String deptId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeNumber", employeeNumber);
        after.put("name", name);
        after.put("departmentId", deptId);
        after.put("status", "ACTIVE");
        after.put("effectivePeriod", Map.of("effectiveFrom", "2026-01-01"));
        return after;
    }
}
