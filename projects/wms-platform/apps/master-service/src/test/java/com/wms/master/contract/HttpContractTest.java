package com.wms.master.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.MasterServiceIntegrationBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Per-endpoint contract test: replays a representative request against the
 * wired application and validates the response body against the published
 * JSON schema. Error responses are validated against the error-envelope
 * schema.
 *
 * <p>Each endpoint listed in {@code specs/contracts/http/master-service-api.md}
 * §§1-3 has a positive case; error-path cases cover the envelope shape per
 * {@code platform/error-handling.md}.
 *
 * <p>Inherits the Docker-backed infrastructure from {@link
 * MasterServiceIntegrationBase} so the contract is exercised against the real
 * Postgres + Kafka + Redis stack.
 */
class HttpContractTest extends MasterServiceIntegrationBase {

    private static final ContractSchema WAREHOUSE_RESPONSE =
            ContractSchema.load("/contracts/http/warehouse-response.schema.json");
    private static final ContractSchema ZONE_RESPONSE =
            ContractSchema.load("/contracts/http/zone-response.schema.json");
    private static final ContractSchema LOCATION_RESPONSE =
            ContractSchema.load("/contracts/http/location-response.schema.json");
    private static final ContractSchema ERROR_ENVELOPE =
            ContractSchema.load("/contracts/http/error-envelope.schema.json");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /warehouses 201 response conforms to warehouse-response.schema")
    void createWarehouse_matchesSchema() {
        String body = """
                {"warehouseCode":"WH%s","name":"Contract WH","timezone":"UTC"}
                """.formatted(shortSuffix());
        ResponseEntity<String> response = post("/api/v1/master/warehouses", body,
                UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WAREHOUSE_RESPONSE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("GET /warehouses/{id} 200 response conforms to warehouse-response.schema")
    void getWarehouse_matchesSchema() throws Exception {
        String body = """
                {"warehouseCode":"WH%s","name":"Contract Get","timezone":"UTC"}
                """.formatted(shortSuffix());
        ResponseEntity<String> created = post("/api/v1/master/warehouses", body,
                UUID.randomUUID().toString(), "MASTER_WRITE");
        JsonNode createdNode = objectMapper.readTree(created.getBody());

        ResponseEntity<String> response = get(
                "/api/v1/master/warehouses/" + createdNode.get("id").asText(), "MASTER_READ");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        WAREHOUSE_RESPONSE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("POST /warehouses/{warehouseId}/zones 201 conforms to zone-response.schema")
    void createZone_matchesSchema() throws Exception {
        String warehouseId = seedWarehouse();
        String zoneBody = """
                {"zoneCode":"Z-%s","name":"Contract Zone","zoneType":"AMBIENT"}
                """.formatted(shortSuffix());
        ResponseEntity<String> response = post(
                "/api/v1/master/warehouses/" + warehouseId + "/zones",
                zoneBody, UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ZONE_RESPONSE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("POST /warehouses/{whId}/zones/{zoneId}/locations 201 conforms to location-response.schema")
    void createLocation_matchesSchema() throws Exception {
        Seed seed = seedWarehouseZone();
        String locationCode = seed.warehouseCode + "-CC-01-02";
        String body = """
                {"locationCode":"%s","aisle":"01","rack":"02",
                 "locationType":"STORAGE","capacityUnits":10}
                """.formatted(locationCode);
        ResponseEntity<String> response = post(
                "/api/v1/master/warehouses/" + seed.warehouseId
                        + "/zones/" + seed.zoneId + "/locations",
                body, UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LOCATION_RESPONSE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("401 response conforms to error-envelope.schema")
    void unauthorizedResponse_matchesErrorSchema() {
        // No Authorization header → 401
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/master/warehouses", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ERROR_ENVELOPE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("400 validation error response conforms to error-envelope.schema")
    void validationErrorResponse_matchesErrorSchema() {
        // Missing required warehouseCode → 400 VALIDATION_ERROR
        String body = """
                {"name":"Missing code","timezone":"UTC"}
                """;
        ResponseEntity<String> response = post("/api/v1/master/warehouses",
                body, UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ERROR_ENVELOPE.assertValid(response.getBody());
    }

    @Test
    @DisplayName("404 not-found response conforms to error-envelope.schema")
    void notFoundResponse_matchesErrorSchema() {
        ResponseEntity<String> response = get(
                "/api/v1/master/warehouses/" + UUID.randomUUID(), "MASTER_READ");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ERROR_ENVELOPE.assertValid(response.getBody());
    }

    // ---------- helpers ----------

    private record Seed(String warehouseId, String warehouseCode, String zoneId) {}

    private String seedWarehouse() throws Exception {
        String body = """
                {"warehouseCode":"WH%s","name":"Seed","timezone":"UTC"}
                """.formatted(shortSuffix());
        ResponseEntity<String> response = post("/api/v1/master/warehouses",
                body, UUID.randomUUID().toString(), "MASTER_WRITE");
        return objectMapper.readTree(response.getBody()).get("id").asText();
    }

    private Seed seedWarehouseZone() throws Exception {
        String warehouseCode = "WH" + shortSuffix();
        String whBody = """
                {"warehouseCode":"%s","name":"Seed WH","timezone":"UTC"}
                """.formatted(warehouseCode);
        ResponseEntity<String> wh = post("/api/v1/master/warehouses",
                whBody, UUID.randomUUID().toString(), "MASTER_WRITE");
        String warehouseId = objectMapper.readTree(wh.getBody()).get("id").asText();
        String zoneBody = """
                {"zoneCode":"Z-%s","name":"Seed Zone","zoneType":"AMBIENT"}
                """.formatted(shortSuffix());
        ResponseEntity<String> zone = post(
                "/api/v1/master/warehouses/" + warehouseId + "/zones",
                zoneBody, UUID.randomUUID().toString(), "MASTER_WRITE");
        String zoneId = objectMapper.readTree(zone.getBody()).get("id").asText();
        return new Seed(warehouseId, warehouseCode, zoneId);
    }

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JWT.issueToken("contract-actor", role));
        headers.add("Idempotency-Key", idempKey);
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "contract-actor");
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JWT.issueToken("contract-actor", role));
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "contract-actor");
        return rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
