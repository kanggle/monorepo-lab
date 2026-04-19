package com.wms.master.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * No-Docker unit test that proves every contract schema loads from the
 * classpath and that {@link ContractSchema#assertValid} rejects mismatched
 * payloads loudly. Runs in the default {@code test} phase so a bad schema
 * or harness regression surfaces even when Docker is unavailable.
 */
class ContractSchemaSelfTest {

    @Test
    void warehouseResponseSchema_loadsAndAcceptsValidPayload() {
        ContractSchema schema = ContractSchema.load(
                "/contracts/http/warehouse-response.schema.json");

        String valid = """
                {
                  "id":"01900000-0000-7000-8000-000000000001",
                  "warehouseCode":"WH01",
                  "name":"Sample",
                  "address":null,
                  "timezone":"UTC",
                  "status":"ACTIVE",
                  "version":0,
                  "createdAt":"2026-04-18T10:00:00Z",
                  "createdBy":"actor",
                  "updatedAt":"2026-04-18T10:00:00Z",
                  "updatedBy":"actor"
                }
                """;
        schema.assertValid(valid);
    }

    @Test
    void warehouseResponseSchema_rejectsExtraField() {
        ContractSchema schema = ContractSchema.load(
                "/contracts/http/warehouse-response.schema.json");

        String invalid = """
                {
                  "id":"01900000-0000-7000-8000-000000000001",
                  "warehouseCode":"WH01",
                  "name":"Sample",
                  "timezone":"UTC",
                  "status":"ACTIVE",
                  "version":0,
                  "createdAt":"2026-04-18T10:00:00Z",
                  "createdBy":"actor",
                  "updatedAt":"2026-04-18T10:00:00Z",
                  "updatedBy":"actor",
                  "unexpected":"boom"
                }
                """;
        assertThatThrownBy(() -> schema.assertValid(invalid))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unexpected");
    }

    @Test
    void errorEnvelopeSchema_acceptsMinimalEnvelope() {
        ContractSchema schema = ContractSchema.load(
                "/contracts/http/error-envelope.schema.json");

        schema.assertValid("""
                {"error":{"code":"UNAUTHORIZED","message":"x"}}
                """);
    }

    @Test
    void eventEnvelopeSchema_rejectsBadEventType() {
        ContractSchema schema = ContractSchema.load(
                "/contracts/events/event-envelope.schema.json");

        String invalid = """
                {
                  "eventId":"0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234",
                  "eventType":"inventory.stock.moved",
                  "eventVersion":1,
                  "occurredAt":"2026-04-18T10:00:00Z",
                  "producer":"master-service",
                  "aggregateType":"warehouse",
                  "aggregateId":"uuid",
                  "actorId":"u",
                  "payload":{}
                }
                """;
        assertThatThrownBy(() -> schema.assertValid(invalid))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void allSchemas_loadWithoutError() {
        assertThat(ContractSchema.load("/contracts/http/warehouse-response.schema.json"))
                .isNotNull();
        assertThat(ContractSchema.load("/contracts/http/zone-response.schema.json"))
                .isNotNull();
        assertThat(ContractSchema.load("/contracts/http/location-response.schema.json"))
                .isNotNull();
        assertThat(ContractSchema.load("/contracts/http/error-envelope.schema.json"))
                .isNotNull();
        assertThat(ContractSchema.load("/contracts/events/event-envelope.schema.json"))
                .isNotNull();
    }
}
