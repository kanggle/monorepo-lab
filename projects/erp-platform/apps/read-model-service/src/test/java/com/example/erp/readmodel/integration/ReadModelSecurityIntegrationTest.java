package com.example.erp.readmodel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security integration test (AC-5): the four-branch tenant + READ gate verdict
 * over the full chain (decode validator + TenantClaimEnforcer filter + READ
 * gate), using real RS256 tokens verified against the MockWebServer JWKS.
 */
class ReadModelSecurityIntegrationTest extends AbstractReadModelIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getEmployees(String authHeader) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/erp/read-model/employees"))
                .GET();
        if (authHeader != null) {
            b.header("Authorization", authHeader);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String codeOf(HttpResponse<String> resp) throws Exception {
        JsonNode body = objectMapper.readTree(resp.body());
        return body.path("code").asText("");
    }

    @Test
    void erpReadTokenIs200() throws Exception {
        HttpResponse<String> resp = getEmployees("Bearer " + erpReadToken());
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    void crossTenantWithoutEntitlementIs403TenantForbidden() throws Exception {
        String token = token(c -> c.claim("tenant_id", "scm").claim("scope", "erp.read"));
        HttpResponse<String> resp = getEmployees("Bearer " + token);
        assertThat(resp.statusCode()).isEqualTo(403);
        assertThat(codeOf(resp)).isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void entitledCrossTenantIs200() throws Exception {
        // tenant_id=acme but entitled_domains ∋ erp → dual-accept passes; the
        // entitlement also satisfies the READ gate.
        String token = token(c -> c.claim("tenant_id", "acme")
                .claim("entitled_domains", List.of("erp")));
        HttpResponse<String> resp = getEmployees("Bearer " + token);
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    void noReadAuthorizationIs403PermissionDenied() throws Exception {
        // Valid erp tenant but no erp.read scope, not operator, not entitled.
        String token = token(c -> c.claim("tenant_id", "erp"));
        HttpResponse<String> resp = getEmployees("Bearer " + token);
        assertThat(resp.statusCode()).isEqualTo(403);
        assertThat(codeOf(resp)).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void noTokenIs401() throws Exception {
        HttpResponse<String> resp = getEmployees(null);
        assertThat(resp.statusCode()).isEqualTo(401);
    }
}
