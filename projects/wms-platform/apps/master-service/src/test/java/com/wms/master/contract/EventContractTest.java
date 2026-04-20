package com.wms.master.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.integration.MasterServiceIntegrationBase;
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
 * Per-event contract test: performs a mutation that produces an event on each
 * of the three master-service topics and validates every received envelope
 * against the event-envelope schema.
 */
class EventContractTest extends MasterServiceIntegrationBase {

    private static final ContractSchema ENVELOPE =
            ContractSchema.load("/contracts/events/event-envelope.schema.json");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("wms.master.warehouse.v1 envelope validates against the event schema")
    void warehouseEvent_envelopeValidates() throws Exception {
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(
                KAFKA.getBootstrapServers(), "wms.master.warehouse.v1")) {
            createWarehouse();
            ConsumerRecord<String, String> record = kafka.pollOne(Duration.ofSeconds(15));
            ENVELOPE.assertValid(record.value());
        }
    }

    @Test
    @DisplayName("wms.master.zone.v1 envelope validates against the event schema")
    void zoneEvent_envelopeValidates() throws Exception {
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(
                KAFKA.getBootstrapServers(), "wms.master.zone.v1")) {
            String whId = createWarehouse();
            String zoneBody = """
                    {"zoneCode":"Z-%s","name":"Event Contract","zoneType":"AMBIENT"}
                    """.formatted(shortSuffix());
            ResponseEntity<String> zone = post("/api/v1/master/warehouses/" + whId + "/zones",
                    zoneBody, UUID.randomUUID().toString(), "MASTER_WRITE");
            assertThat(zone.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ConsumerRecord<String, String> record = kafka.pollOne(Duration.ofSeconds(15));
            ENVELOPE.assertValid(record.value());
        }
    }

    @Test
    @DisplayName("wms.master.location.v1 envelope validates against the event schema")
    void locationEvent_envelopeValidates() throws Exception {
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(
                KAFKA.getBootstrapServers(), "wms.master.location.v1")) {
            String whId = createWarehouse();
            String warehouseCode = lastWarehouseCode;
            String zoneBody = """
                    {"zoneCode":"Z-%s","name":"Loc event","zoneType":"AMBIENT"}
                    """.formatted(shortSuffix());
            ResponseEntity<String> zone = post("/api/v1/master/warehouses/" + whId + "/zones",
                    zoneBody, UUID.randomUUID().toString(), "MASTER_WRITE");
            String zoneId = objectMapper.readTree(zone.getBody()).get("id").asText();

            String locationCode = warehouseCode + "-EV-01-02";
            String locBody = """
                    {"locationCode":"%s","aisle":"01","rack":"02",
                     "locationType":"STORAGE","capacityUnits":10}
                    """.formatted(locationCode);
            ResponseEntity<String> loc = post(
                    "/api/v1/master/warehouses/" + whId + "/zones/" + zoneId + "/locations",
                    locBody, UUID.randomUUID().toString(), "MASTER_WRITE");
            assertThat(loc.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ConsumerRecord<String, String> record = kafka.pollOne(Duration.ofSeconds(15));
            ENVELOPE.assertValid(record.value());
        }
    }

    @Test
    @DisplayName("wms.master.lot.v1 envelope validates against the event schema")
    void lotEvent_envelopeValidates() throws Exception {
        try (KafkaTestConsumer kafka = new KafkaTestConsumer(
                KAFKA.getBootstrapServers(), "wms.master.lot.v1")) {
            String skuId = createLotTrackedSku();
            String lotBody = """
                    {"lotNo":"LOT-%s"}
                    """.formatted(shortSuffix());
            ResponseEntity<String> lot = post("/api/v1/master/skus/" + skuId + "/lots",
                    lotBody, UUID.randomUUID().toString(), "MASTER_WRITE");
            assertThat(lot.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ConsumerRecord<String, String> record = kafka.pollOne(Duration.ofSeconds(15));
            ENVELOPE.assertValid(record.value());
        }
    }

    // ---------- helpers ----------

    private String lastWarehouseCode;

    private String createWarehouse() throws Exception {
        String code = "WH" + shortSuffix();
        String body = """
                {"warehouseCode":"%s","name":"Event contract","timezone":"UTC"}
                """.formatted(code);
        ResponseEntity<String> response = post("/api/v1/master/warehouses",
                body, UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        lastWarehouseCode = code;
        return objectMapper.readTree(response.getBody()).get("id").asText();
    }

    private String createLotTrackedSku() throws Exception {
        String skuCode = "SKU-LOT-" + shortSuffix();
        String body = """
                {"skuCode":"%s","name":"Lot-Tracked SKU","baseUom":"EA","trackingType":"LOT"}
                """.formatted(skuCode);
        ResponseEntity<String> response = post("/api/v1/master/skus",
                body, UUID.randomUUID().toString(), "MASTER_WRITE");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asText();
    }

    private ResponseEntity<String> post(String path, String body, String idempKey, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JWT.issueToken("event-contract-actor", role));
        headers.add("Idempotency-Key", idempKey);
        headers.add("X-Request-Id", UUID.randomUUID().toString());
        headers.add("X-Actor-Id", "event-contract-actor");
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private static String shortSuffix() {
        return String.valueOf(10 + (int) (Math.random() * 890));
    }
}
