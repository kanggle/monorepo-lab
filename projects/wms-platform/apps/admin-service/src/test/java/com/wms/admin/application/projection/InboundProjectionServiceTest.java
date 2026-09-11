package com.wms.admin.application.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.projection.fakes.InMemoryDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryEntity;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundProjectionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock AsnSummaryRepository asnRepo;
    @Mock InspectionSummaryRepository inspectionRepo;
    @Mock ThroughputInboundDailyRepository throughputRepo;
    @Mock PartnerRefRepository partnerRepo;
    @Mock WarehouseRefRepository warehouseRepo;

    private InMemoryDedupePort dedupe;
    private InboundProjectionService service;

    @BeforeEach
    void setUp() {
        dedupe = new InMemoryDedupePort();
        ProjectionMetrics metrics = new ProjectionMetrics(new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new InboundProjectionService(asnRepo, inspectionRepo, throughputRepo,
                partnerRepo, warehouseRepo, dedupe, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void asnReceived_insertsRow() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(asnRepo.findById(asnId)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inbound.asn.received", "wms.inbound.asn.received.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\",\"source\":\"WEBHOOK_ERP\",\"lines\":[]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(asnRepo, times(1)).save(any(AsnSummaryEntity.class));
    }

    @Test
    void asnCancelled_updatesStatus() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        AsnSummaryEntity existing = new AsnSummaryEntity(asnId, "ASN-001", warehouseId, null, null,
                null, "CREATED", "MANUAL", null, 0, NOW.minusSeconds(60), null, NOW.minusSeconds(60));
        when(asnRepo.findById(asnId)).thenReturn(Optional.of(existing));

        ProjectionEnvelope env = envelope("inbound.asn.cancelled", "wms.inbound.asn.cancelled.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\"}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        assertThat(existing.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void inspectionCompleted_aggregatesLineQuantities() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(inspectionRepo.findById(asnId)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inbound.inspection.completed",
                "wms.inbound.inspection.completed.v1",
                "{\"asnId\":\"" + asnId + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"inspectorId\":\"alice\",\"discrepancyCount\":1,"
                        + "\"lines\":[{\"expectedQty\":10,\"qtyPassed\":8,\"qtyDamaged\":1,\"qtyShort\":1}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(inspectionRepo, times(1)).save(any(InspectionSummaryEntity.class));
    }

    @Test
    void putawayCompleted_callsAtomicUpsertIncrement() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(throughputRepo.upsertIncrement(any(LocalDate.class), eq(warehouseId), anyInt(),
                any(Instant.class))).thenReturn(1);

        ProjectionEnvelope env = envelope("inbound.putaway.completed",
                "wms.inbound.putaway.completed.v1",
                "{\"warehouseId\":\"" + warehouseId + "\",\"completedAt\":\"2026-05-09T13:45:00Z\","
                        + "\"lines\":[{\"qtyReceived\":10},{\"qtyReceived\":15}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(throughputRepo, times(1)).upsertIncrement(
                eq(LocalDate.of(2026, 5, 9)), eq(warehouseId), eq(25), any(Instant.class));
    }

    @Test
    void putawayCompleted_zeroAffectedRowsMappedToIgnoredDuplicateLate() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(throughputRepo.upsertIncrement(any(LocalDate.class), any(UUID.class), anyInt(),
                any(Instant.class))).thenReturn(0);

        ProjectionEnvelope env = envelope("inbound.putaway.completed",
                "wms.inbound.putaway.completed.v1",
                "{\"warehouseId\":\"" + warehouseId + "\",\"completedAt\":\"2026-05-09T10:00:00Z\","
                        + "\"lines\":[{\"qtyReceived\":10}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.IGNORED_DUPLICATE_LATE);
    }

    @Test
    void duplicateEventId_skipsMutation() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(asnRepo.findById(asnId)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope(eventId, "inbound.asn.received",
                "wms.inbound.asn.received.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\",\"lines\":[]}");

        service.project(env);
        DedupeOutcome second = service.project(env);

        assertThat(second).isEqualTo(DedupeOutcome.DUPLICATE);
        verify(asnRepo, times(1)).save(any());
        verify(throughputRepo, never()).upsertIncrement(any(), any(), anyInt(), any());
    }

    private ProjectionEnvelope envelope(String eventType, String topic, String payloadJson)
            throws Exception {
        return envelope(UUID.randomUUID(), eventType, topic, payloadJson);
    }

    private ProjectionEnvelope envelope(UUID eventId, String eventType, String topic,
                                        String payloadJson) throws Exception {
        JsonNode payload = MAPPER.readTree(payloadJson);
        return new ProjectionEnvelope(eventId, eventType, NOW, "agg", topic, null, payload);
    }

    // =========================================================================
    // TASK-MONO-659 — warehouseCode 비정규화
    // =========================================================================
    // 이 DTO 는 `supplierPartnerId` 옆에 `supplierName` 을 이미 풀고 있었다. 창고만
    // UUID 로 남아 콘솔이 그것을 그대로 그렸고, 콘솔에서는 고칠 수 없었다.

    @Test
    void asnReceived_carriesWarehouseCode_resolvedFromRef() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(asnRepo.findById(asnId)).thenReturn(Optional.empty());
        when(warehouseRepo.findById(warehouseId)).thenReturn(Optional.of(
                new WarehouseRefEntity(warehouseId, "WH-BUSAN", "Busan DC", "Asia/Seoul",
                        "ACTIVE", NOW)));

        ProjectionEnvelope env = envelope("inbound.asn.received", "wms.inbound.asn.received.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\",\"source\":\"WEBHOOK_ERP\",\"lines\":[]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);

        ArgumentCaptor<AsnSummaryEntity> captor = ArgumentCaptor.forClass(AsnSummaryEntity.class);
        verify(asnRepo).save(captor.capture());
        assertThat(captor.getValue().getWarehouseCode()).isEqualTo("WH-BUSAN");
        // 대조군: UUID 는 그대로 남는다 — 더하는 변경이지 바꾸는 변경이 아니다.
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(warehouseId);
    }

    @Test
    void asnReceived_warehouseCodeIsNull_whenRefNotProjectedYet() throws Exception {
        // 대조군 — 참조 미도착은 결함이 아니다. null 로 남는 것이 옳다.
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(asnRepo.findById(asnId)).thenReturn(Optional.empty());
        when(warehouseRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inbound.asn.received", "wms.inbound.asn.received.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\",\"source\":\"WEBHOOK_ERP\",\"lines\":[]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);

        ArgumentCaptor<AsnSummaryEntity> captor = ArgumentCaptor.forClass(AsnSummaryEntity.class);
        verify(asnRepo).save(captor.capture());
        assertThat(captor.getValue().getWarehouseCode()).isNull();
    }

    @Test
    void asnReceived_doesNotEraseExistingCode_whenRefGoesMissing() throws Exception {
        // 🔴 「null 이면 덮지 않는다」 규칙의 칸. 이것이 없으면 참조가 잠깐 사라진 사이에
        //    재수신된 이벤트가 이미 채워 둔 코드를 **지운다**.
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        AsnSummaryEntity existing = new AsnSummaryEntity(asnId, "ASN-001", warehouseId,
                "WH-BUSAN", null, null, "CREATED", "MANUAL", null, 0,
                NOW.minusSeconds(60), null, NOW.minusSeconds(60));
        when(asnRepo.findById(asnId)).thenReturn(Optional.of(existing));
        when(warehouseRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inbound.asn.received", "wms.inbound.asn.received.v1",
                "{\"asnId\":\"" + asnId + "\",\"asnNo\":\"ASN-001\",\"warehouseId\":\""
                        + warehouseId + "\",\"source\":\"WEBHOOK_ERP\",\"lines\":[]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        assertThat(existing.getWarehouseCode()).isEqualTo("WH-BUSAN");
    }

}
