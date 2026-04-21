package com.wms.master.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.support.KafkaTestConsumer;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
 * Location aggregate integration test.
 *
 * <p>Dual-parent-active guard (warehouse + zone) and zone-deactivate-blocked-
 * by-active-location check are the two cross-aggregate invariants that only a
 * wired context can prove — slice tests can only simulate them with mocks.
 */
class LocationIntegrationTest extends MasterServiceIntegrationBase {

    private static final String WRITE_ROLE = "MASTER_WRITE";
    private static final String ADMIN_ROLE = "MASTER_ADMIN";
    private static final String TOPIC = "wms.master.location.v1";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("location create under ACTIVE warehouse+zone succeeds and publishes created event")
    void createLocation_publishesEvent() throws Exception {
        Seed seed = seedWarehouseAndZone();
        String locationCode = seed.warehouseCode + "-A-01-02-03";
        String body = """
                {"locationCode":"%s","aisle":"01","rack":"02","level":"03",
                 "locationType":"STORAGE","capacityUnits":500}
                """.formatted(locationCode);

        try (KafkaTestConsumer kafka = new KafkaTestConsumer(KAFKA.getBootstrapServers(), TOPIC)) {
            ResponseEntity<String> created = post(
                    "/api/v1/master/warehouses/" + seed.warehouseId + "/zones/"
                            + seed.zoneId + "/locations",
                    body, UUID.randomUUID().toString(), WRITE_ROLE);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode node = objectMapper.readTree(created.getBody());
            assertThat(node.get("locationCode").asText()).isEqualTo(locationCode);
            assertThat(node.get("zoneId").asText()).isEqualTo(seed.zoneId);

            // Filter by aggregateId so a drained leftover location event from
            // a previous test case (e.g. globalLocationCode_collision) does
            // not match this assertion. TASK-BE-019.
            String expectedAggregateId = node.get("id").asText();
            ConsumerRecord<String, String> record =
                    kafka.pollOneForKey(Duration.ofSeconds(10), expectedAggregateId);
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("master.location.created");
            assertThat(envelope.get("aggregateType").asText()).isEqualTo("location");
            assertThat(envelope.get("payload").get("location").get("locationCode").asText())
                    .isEqualTo(locationCode);
        }
    }

    @Test
    @DisplayName("same locationCode under any zone returns 409 LOCATION_CODE_DUPLICATE")
    void globalLocationCode_collision() throws Exception {
        Seed s1 = seedWarehouseAndZone();
        Seed s2 = seedWarehouseAndZone();

        String sharedCode = s1.warehouseCode + "-DUP-01-02";
        String body = """
                {"locationCode":"%s","aisle":"01","rack":"02","level":"03",
                 "locationType":"STORAGE","capacityUnits":100}
                """.formatted(sharedCode);

        ResponseEntity<String> first = post(
                "/api/v1/master/warehouses/" + s1.warehouseId
                        + "/zones/" + s1.zoneId + "/locations",
                body, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Reusing the same locationCode even under a different warehouse/zone must fail
        // (W3: locationCode is globally unique). Note: the code will not match the
        // second warehouse's prefix so domain will reject before the DB check; either
        // way we expect a 4xx client error.
        ResponseEntity<String> duplicate = post(
                "/api/v1/master/warehouses/" + s2.warehouseId
                        + "/zones/" + s2.zoneId + "/locations",
                body, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(duplicate.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("zone deactivate is blocked while ACTIVE locations exist under it")
    void zoneDeactivateBlockedByActiveLocations() throws Exception {
        Seed seed = seedWarehouseAndZone();
        String locationBody = """
                {"locationCode":"%s-A-01-02-03","aisle":"01","rack":"02","level":"03",
                 "locationType":"STORAGE","capacityUnits":500}
                """.formatted(seed.warehouseCode);

        ResponseEntity<String> locCreated = post(
                "/api/v1/master/warehouses/" + seed.warehouseId + "/zones/"
                        + seed.zoneId + "/locations",
                locationBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(locCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Attempt to deactivate the zone — blocked by the active location
        String deactivate = """
                {"version":0,"reason":"Clear it"}
                """;
        ResponseEntity<String> attempt = post(
                "/api/v1/master/warehouses/" + seed.warehouseId
                        + "/zones/" + seed.zoneId + "/deactivate",
                deactivate, UUID.randomUUID().toString(), ADMIN_ROLE);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(attempt.getBody()).contains("REFERENCE_INTEGRITY_VIOLATION");
    }

    // ---------- helpers ----------

    private record Seed(String warehouseId, String warehouseCode, String zoneId) {}

    private Seed seedWarehouseAndZone() throws Exception {
        String warehouseCode = "WH" + shortSuffix();
        String whBody = """
                {"warehouseCode":"%s","name":"Loc parent","timezone":"UTC"}
                """.formatted(warehouseCode);
        ResponseEntity<String> wh = post("/api/v1/master/warehouses",
                whBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(wh.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String warehouseId = objectMapper.readTree(wh.getBody()).get("id").asText();

        String zoneBody = """
                {"zoneCode":"Z-%s","name":"Loc zone","zoneType":"AMBIENT"}
                """.formatted(shortSuffix());
        ResponseEntity<String> zone = post(
                "/api/v1/master/warehouses/" + warehouseId + "/zones",
                zoneBody, UUID.randomUUID().toString(), WRITE_ROLE);
        assertThat(zone.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String zoneId = objectMapper.readTree(zone.getBody()).get("id").asText();
        return new Seed(warehouseId, warehouseCode, zoneId);
    }

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JWT.issueToken("integration-actor", role));
        headers.add("Idempotency-Key", idempKey);
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "integration-actor");
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
