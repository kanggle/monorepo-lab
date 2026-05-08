package com.wms.admin.application.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.projection.fakes.InMemoryDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.master.LocationRefEntity;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefEntity;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefEntity;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefEntity;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MasterProjectionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock WarehouseRefRepository warehouseRepo;
    @Mock ZoneRefRepository zoneRepo;
    @Mock LocationRefRepository locationRepo;
    @Mock SkuRefRepository skuRepo;
    @Mock LotRefRepository lotRepo;
    @Mock PartnerRefRepository partnerRepo;

    private InMemoryDedupePort dedupe;
    private ProjectionMetrics metrics;
    private MasterProjectionService service;

    @BeforeEach
    void setUp() {
        dedupe = new InMemoryDedupePort();
        metrics = new ProjectionMetrics(new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new MasterProjectionService(warehouseRepo, zoneRepo, locationRepo, skuRepo,
                lotRepo, partnerRepo, dedupe, metrics);
    }

    @Test
    void warehouseCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        when(warehouseRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.warehouse.created",
                "wms.master.warehouse.v1",
                "{\"warehouse\":{\"id\":\"" + id + "\",\"warehouseCode\":\"WH01\","
                        + "\"name\":\"Seoul\",\"timezone\":\"Asia/Seoul\",\"status\":\"ACTIVE\"}}");

        DedupeOutcome outcome = service.project(env);

        assertThat(outcome).isEqualTo(DedupeOutcome.APPLIED);
        verify(warehouseRepo, times(1)).save(any(WarehouseRefEntity.class));
    }

    @Test
    void warehouseUpdated_existingRowApplied() throws Exception {
        UUID id = UUID.randomUUID();
        WarehouseRefEntity existing = new WarehouseRefEntity(id, "WH01", "Old", "Asia/Seoul",
                "ACTIVE", NOW.minusSeconds(60));
        when(warehouseRepo.findById(id)).thenReturn(Optional.of(existing));

        ProjectionEnvelope env = envelope("master.warehouse.updated",
                "wms.master.warehouse.v1",
                "{\"warehouse\":{\"id\":\"" + id + "\",\"warehouseCode\":\"WH01\","
                        + "\"name\":\"New\",\"status\":\"ACTIVE\"}}");

        DedupeOutcome outcome = service.project(env);

        assertThat(outcome).isEqualTo(DedupeOutcome.APPLIED);
        assertThat(existing.getName()).isEqualTo("New");
        verify(warehouseRepo, never()).save(any());
    }

    @Test
    void duplicateEventId_skipsMutation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ProjectionEnvelope env = envelope(eventId, "master.warehouse.created",
                "wms.master.warehouse.v1",
                "{\"warehouse\":{\"id\":\"" + id + "\",\"warehouseCode\":\"WH01\","
                        + "\"name\":\"x\",\"status\":\"ACTIVE\"}}");
        when(warehouseRepo.findById(id)).thenReturn(Optional.empty());

        service.project(env);  // first time
        DedupeOutcome second = service.project(env);  // duplicate

        assertThat(second).isEqualTo(DedupeOutcome.DUPLICATE);
        verify(warehouseRepo, times(1)).save(any());  // only first delivery wrote
    }

    @Test
    void staleEvent_skipsMutation_marksLate() throws Exception {
        UUID id = UUID.randomUUID();
        Instant rowEventAt = NOW.plusSeconds(60);  // future event already projected
        WarehouseRefEntity existing = new WarehouseRefEntity(id, "WH01", "New", null,
                "ACTIVE", rowEventAt);
        when(warehouseRepo.findById(id)).thenReturn(Optional.of(existing));

        UUID eventId = UUID.randomUUID();
        ProjectionEnvelope env = envelope(eventId, "master.warehouse.updated",
                "wms.master.warehouse.v1",
                "{\"warehouse\":{\"id\":\"" + id + "\",\"warehouseCode\":\"WH01\","
                        + "\"name\":\"Old\",\"status\":\"ACTIVE\"}}");

        DedupeOutcome outcome = service.project(env);

        assertThat(outcome).isEqualTo(DedupeOutcome.IGNORED_DUPLICATE_LATE);
        assertThat(dedupe.isStale(eventId)).isTrue();
        verify(warehouseRepo, never()).save(any());
        assertThat(existing.getName()).isEqualTo("New");
    }

    @Test
    void zoneCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(zoneRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.zone.created", "wms.master.zone.v1",
                "{\"zone\":{\"id\":\"" + id + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"zoneCode\":\"Z-A\",\"name\":\"Zone A\","
                        + "\"zoneType\":\"AMBIENT\",\"status\":\"ACTIVE\"}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(zoneRepo, times(1)).save(any(ZoneRefEntity.class));
    }

    @Test
    void locationCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        UUID wh = UUID.randomUUID();
        UUID zone = UUID.randomUUID();
        when(locationRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.location.created",
                "wms.master.location.v1",
                "{\"location\":{\"id\":\"" + id + "\",\"locationCode\":\"WH01-A-01\","
                        + "\"warehouseId\":\"" + wh + "\",\"zoneId\":\"" + zone
                        + "\",\"locationType\":\"STORAGE\",\"status\":\"ACTIVE\"}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(locationRepo, times(1)).save(any(LocationRefEntity.class));
    }

    @Test
    void skuCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        when(skuRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.sku.created", "wms.master.sku.v1",
                "{\"sku\":{\"id\":\"" + id + "\",\"skuCode\":\"SKU-1\",\"name\":\"Apple\","
                        + "\"baseUom\":\"EA\",\"trackingType\":\"LOT\",\"status\":\"ACTIVE\"}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(skuRepo, times(1)).save(any(SkuRefEntity.class));
    }

    @Test
    void partnerCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        when(partnerRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.partner.created", "wms.master.partner.v1",
                "{\"partner\":{\"id\":\"" + id + "\",\"partnerCode\":\"SUP-001\","
                        + "\"name\":\"ACME\",\"partnerType\":\"SUPPLIER\",\"status\":\"ACTIVE\"}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(partnerRepo, times(1)).save(any(PartnerRefEntity.class));
    }

    @Test
    void lotCreated_insertsNewRow() throws Exception {
        UUID id = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        when(lotRepo.findById(id)).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("master.lot.created", "wms.master.lot.v1",
                "{\"lot\":{\"id\":\"" + id + "\",\"skuId\":\"" + skuId
                        + "\",\"lotNo\":\"L-2026\",\"expiryDate\":\"2027-01-01\","
                        + "\"status\":\"ACTIVE\"}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(lotRepo, times(1)).save(any(LotRefEntity.class));
    }

    @Test
    void unknownEventType_throws() throws Exception {
        ProjectionEnvelope env = envelope("master.unknown.action", "wms.master.warehouse.v1",
                "{\"warehouse\":{}}");
        assertThatThrownBy(() -> service.project(env))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    private ProjectionEnvelope envelope(String eventType, String topic, String payloadJson)
            throws Exception {
        return envelope(UUID.randomUUID(), eventType, topic, payloadJson);
    }

    private ProjectionEnvelope envelope(UUID eventId, String eventType, String topic,
                                        String payloadJson) throws Exception {
        JsonNode payload = MAPPER.readTree(payloadJson);
        return new ProjectionEnvelope(eventId, eventType, NOW, "agg", topic, payload);
    }
}
