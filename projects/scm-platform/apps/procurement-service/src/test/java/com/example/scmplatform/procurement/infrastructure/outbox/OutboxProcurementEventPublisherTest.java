package com.example.scmplatform.procurement.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaEntity;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link OutboxProcurementEventPublisher} write path
 * (TASK-SCM-BE-032, outbox v2).
 *
 * <p>Asserts each domain event persists a {@code procurement_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1 lib
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope
 * ({@code eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey,
 * payload}) in that field order, the row {@code event_id} reused as the envelope
 * {@code eventId}, {@code aggregate_type}/{@code aggregate_id}/{@code event_type}
 * matching the v1 call, and {@code partition_key} left null so the relay falls
 * back to {@code aggregateId} (the v1 Kafka key).
 */
class OutboxProcurementEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);

    private final ProcurementOutboxJpaRepository repository = mock(ProcurementOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxProcurementEventPublisher publisher =
            new OutboxProcurementEventPublisher(repository, objectMapper, CLOCK);

    @Test
    void publishPoSubmitted_persistsV2Row_withCanonicalEnvelopeAndPreservedKeyFields() throws Exception {
        PurchaseOrder po = draftPo();

        publisher.publishPoSubmitted(po);

        ProcurementOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ProcurementEventPublisher.EVENT_PO_SUBMITTED);
        assertThat(row.getAggregateType()).isEqualTo("purchase_order");
        assertThat(row.getAggregateId()).isEqualTo("po-001");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());
        assertThat(row.getPublishedAt()).isNull();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        // envelope eventId == row PK (header/payload identity)
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(ProcurementEventPublisher.EVENT_PO_SUBMITTED);
        assertThat(envelope.get("source").asText()).isEqualTo("scm-platform-procurement-service");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("po-001");
        // payload carries the v1 base() fields
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("poId").asText()).isEqualTo("po-001");
        assertThat(payload.get("poNumber").asText()).isEqualTo("PO-TEST-001");
        assertThat(payload.get("tenantId").asText()).isEqualTo("scm");
        assertThat(payload.get("supplierId").asText()).isEqualTo("sup-001");
        assertThat(payload.get("buyerAccountId").asText()).isEqualTo("buyer-001");
        assertThat(payload.get("currency").asText()).isEqualTo("USD");
        assertThat(payload.has("submittedAt")).isTrue();
    }

    @Test
    void publishAsnReceived_persistsV2Row_withCanonicalEnvelopeAndAsnPayload() throws Exception {
        Instant expected = Instant.parse("2026-06-28T00:00:00Z");
        Instant received = Instant.parse("2026-06-27T12:00:00Z");

        publisher.publishAsnReceived("asn-1", "po-001", "scm", "SUP-ASN-9", expected, received);

        ProcurementOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ProcurementEventPublisher.EVENT_ASN_RECEIVED);
        assertThat(row.getAggregateType()).isEqualTo("asn");
        assertThat(row.getAggregateId()).isEqualTo("asn-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(ProcurementEventPublisher.EVENT_ASN_RECEIVED);
        assertThat(envelope.get("source").asText()).isEqualTo("scm-platform-procurement-service");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("asn-1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("asnId").asText()).isEqualTo("asn-1");
        assertThat(payload.get("poId").asText()).isEqualTo("po-001");
        assertThat(payload.get("tenantId").asText()).isEqualTo("scm");
        assertThat(payload.get("supplierAsnRef").asText()).isEqualTo("SUP-ASN-9");
        assertThat(payload.get("expectedArrivalAt").asText()).isEqualTo(expected.toString());
        assertThat(payload.get("receivedAt").asText()).isEqualTo(received.toString());
    }

    // --- ADR-MONO-050 inbound-expected -----------------------------------------

    @Test
    void publishInboundExpected_persistsRow_withAllAdr050PayloadFields() throws Exception {
        PurchaseOrder po = warehouseAddressedConfirmedPo();

        publisher.publishInboundExpected(po);

        ProcurementOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ProcurementEventPublisher.EVENT_INBOUND_EXPECTED);
        assertThat(row.getAggregateType()).isEqualTo("purchase_order");
        assertThat(row.getAggregateId()).isEqualTo("po-050");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(ProcurementEventPublisher.EVENT_INBOUND_EXPECTED);
        assertThat(envelope.get("source").asText()).isEqualTo("scm-platform-procurement-service");
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("po-050");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("poId").asText()).isEqualTo("po-050");
        assertThat(payload.get("poNumber").asText()).isEqualTo("PO-INB-050");
        assertThat(payload.get("supplierId").asText()).isEqualTo("sup-050");
        assertThat(payload.get("destinationWarehouseId").asText()).isEqualTo("wh-seoul-01");
        assertThat(payload.get("destinationNodeType").asText()).isEqualTo("WMS_WAREHOUSE");
        assertThat(payload.get("currency").asText()).isEqualTo("KRW");
        // expectedArrivalDate = confirmedAt (UTC date) + lead_time_days(7)
        String expectedDate = po.getConfirmedAt().atZone(ZoneOffset.UTC)
                .toLocalDate().plusDays(7).toString();
        assertThat(payload.get("expectedArrivalDate").asText()).isEqualTo(expectedDate);

        JsonNode line = payload.get("lines").get(0);
        assertThat(line.get("skuCode").asText()).isEqualTo("SKU-APPLE-001");
        assertThat(line.get("expectedQty").asText()).isEqualTo("100");
        assertThat(line.get("uom").asText()).isEqualTo("EA");
    }

    @Test
    void publishInboundExpectedCancelled_persistsRow_withPoAndLineSkus() throws Exception {
        PurchaseOrder po = warehouseAddressedConfirmedPo();

        publisher.publishInboundExpectedCancelled(po);

        ProcurementOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(ProcurementEventPublisher.EVENT_INBOUND_EXPECTED_CANCELLED);
        assertThat(row.getAggregateType()).isEqualTo("purchase_order");
        assertThat(row.getAggregateId()).isEqualTo("po-050");

        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("poId").asText()).isEqualTo("po-050");
        assertThat(payload.get("poNumber").asText()).isEqualTo("PO-INB-050");
        assertThat(payload.get("lines").get(0).get("skuCode").asText()).isEqualTo("SKU-APPLE-001");
    }

    private PurchaseOrder draftPo() {
        PurchaseOrder po = PurchaseOrder.createDraft(
                "po-001", "scm", "PO-TEST-001", "sup-001", "buyer-001", "USD");
        po.addLine(PurchaseOrderLine.create(
                "line-001", po.getId(), "scm", 1, "sku-001", "sup-sku-001",
                new BigDecimal("10"), new BigDecimal("5.00")));
        return po;
    }

    private PurchaseOrder warehouseAddressedConfirmedPo() {
        PurchaseOrder po = PurchaseOrder.createDraftFromSuggestion(
                "po-050", "scm", "PO-INB-050", "sup-050", "buyer-050", "KRW",
                "sugg-050", "wh-seoul-01", "WMS_WAREHOUSE", 7);
        po.addLine(PurchaseOrderLine.create(
                "line-050", po.getId(), "scm", 1, "SKU-APPLE-001", null,
                new BigDecimal("100"), BigDecimal.ZERO));
        po.submit(com.example.scmplatform.procurement.domain.po.status.ActorType.BUYER);
        po.acknowledge(com.example.scmplatform.procurement.domain.po.status.ActorType.SUPPLIER);
        po.confirm(com.example.scmplatform.procurement.domain.po.status.ActorType.OPERATOR);
        return po;
    }

    private ProcurementOutboxJpaEntity capturedRow() {
        ArgumentCaptor<ProcurementOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(ProcurementOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
