package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import com.wms.inbound.application.exception.InboundExpectationRejectedException;
import com.wms.inbound.application.port.out.AsnNoSequencePort;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.domain.event.AsnReceivedEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateScmInboundExpectationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-19T04:12:00Z");

    @Mock AsnPersistencePort asnPersistence;
    @Mock AsnNoSequencePort asnNoSequence;
    @Mock InboundEventPort eventPort;
    @Mock MasterReadModelPort masterReadModel;

    CreateScmInboundExpectationService sut;

    @BeforeEach
    void setUp() {
        sut = new CreateScmInboundExpectationService(asnPersistence, asnNoSequence, eventPort,
                masterReadModel, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private void stubSaveEcho() {
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ happy

    @Test
    void create_happyPath_createsExpectedAsnPreservingPoTrace() {
        UUID whId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        stubWarehouse("WH-SEOUL-01", whId, WarehouseSnapshot.Status.ACTIVE);
        stubSupplier("SUP-0043", supplierId);
        stubSkuByCode("SKU-A", skuId);
        stubSkuById(skuId, "SKU-A");
        stubSaveEcho();
        when(asnPersistence.existsOpenByPoNumber("SCM-PO-2026-00187")).thenReturn(false);
        when(asnNoSequence.nextAsnNo()).thenReturn("ASN-20260719-0001");

        Optional<UUID> result = sut.create(command("SCM-PO-2026-00187", poId, "WH-SEOUL-01",
                "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 100));

        assertThat(result).isPresent();
        Asn saved = captureSaved();
        assertThat(saved.getSource()).isEqualTo(AsnSource.SCM_PROCUREMENT);
        assertThat(saved.getStatus()).isEqualTo(AsnStatus.CREATED);
        assertThat(saved.getWarehouseId()).isEqualTo(whId);
        assertThat(saved.getSupplierPartnerId()).isEqualTo(supplierId);
        assertThat(saved.getPoNumber()).isEqualTo("SCM-PO-2026-00187");
        assertThat(saved.getPoId()).isEqualTo(poId);
        assertThat(saved.getExpectedArriveDate()).isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(saved.getLines()).singleElement().satisfies(l -> {
            assertThat(l.getSkuId()).isEqualTo(skuId);
            assertThat(l.getExpectedQty()).isEqualTo(100);
            assertThat(l.getLotId()).isNull();
        });
        verify(eventPort).publish(any(AsnReceivedEvent.class));
    }

    // ------------------------------------------------ D3 warehouse addressing

    @Test
    void create_multiWarehouse_routesEachEventToItsAddressedWarehouse() {
        UUID whA = UUID.randomUUID();
        UUID whB = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        stubWarehouse("WH-A", whA, WarehouseSnapshot.Status.ACTIVE);
        stubWarehouse("WH-B", whB, WarehouseSnapshot.Status.ACTIVE);
        stubSupplier("SUP-0043", supplierId);
        stubSkuByCode("SKU-A", skuId);
        stubSkuById(skuId, "SKU-A");
        stubSaveEcho();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        when(asnNoSequence.nextAsnNo()).thenReturn("ASN-A", "ASN-B");

        sut.create(command("PO-A", UUID.randomUUID(), "WH-A", "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 10));
        sut.create(command("PO-B", UUID.randomUUID(), "WH-B", "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 20));

        ArgumentCaptor<Asn> captor = ArgumentCaptor.forClass(Asn.class);
        verify(asnPersistence, org.mockito.Mockito.times(2)).save(captor.capture());
        List<Asn> saved = captor.getAllValues();
        // Different addressed warehouseIds → different destinations, same code path (no branch).
        assertThat(saved.get(0).getWarehouseId()).isEqualTo(whA);
        assertThat(saved.get(1).getWarehouseId()).isEqualTo(whB);
        assertThat(saved.get(0).getWarehouseId()).isNotEqualTo(saved.get(1).getWarehouseId());
    }

    @Test
    void create_singleWarehouse_repeatedSameIdAllRouteToThatWarehouse() {
        UUID wh = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        stubWarehouse("WH-ONLY", wh, WarehouseSnapshot.Status.ACTIVE);
        stubSupplier("SUP-0043", supplierId);
        stubSkuByCode("SKU-A", skuId);
        stubSkuById(skuId, "SKU-A");
        stubSaveEcho();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        when(asnNoSequence.nextAsnNo()).thenReturn("ASN-1", "ASN-2");

        // Two distinct POs to the same warehouse (degenerate single-warehouse deployment).
        sut.create(command("PO-1", UUID.randomUUID(), "WH-ONLY", "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 10));
        sut.create(command("PO-2", UUID.randomUUID(), "WH-ONLY", "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 20));

        ArgumentCaptor<Asn> captor = ArgumentCaptor.forClass(Asn.class);
        verify(asnPersistence, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Asn::getWarehouseId).containsOnly(wh);
    }

    // ------------------------------------------------------------- D6.2 dedup

    @Test
    void create_businessDuplicate_skipsCreation() {
        stubWarehouse("WH-A", UUID.randomUUID(), WarehouseSnapshot.Status.ACTIVE);
        when(asnPersistence.existsOpenByPoNumber("PO-DUP")).thenReturn(true);

        Optional<UUID> result = sut.create(command("PO-DUP", UUID.randomUUID(), "WH-A",
                "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 10));

        assertThat(result).isEmpty();
        verify(asnPersistence, never()).save(any());
        verify(eventPort, never()).publish(any());
    }

    // ------------------------------------------------ D3/D4 fail-closed rejects

    @Test
    void create_unknownWarehouse_rejectsToDlt() {
        when(masterReadModel.findWarehouseByCode("WH-GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.create(command("PO-1", UUID.randomUUID(), "WH-GHOST",
                "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 10)))
                .isInstanceOf(InboundExpectationRejectedException.class);
        verify(asnPersistence, never()).save(any());
    }

    @Test
    void create_inactiveWarehouse_rejectsToDlt() {
        stubWarehouse("WH-OFF", UUID.randomUUID(), WarehouseSnapshot.Status.INACTIVE);

        assertThatThrownBy(() -> sut.create(command("PO-1", UUID.randomUUID(), "WH-OFF",
                "WMS_WAREHOUSE", "SUP-0043", "SKU-A", 10)))
                .isInstanceOf(InboundExpectationRejectedException.class);
        verify(asnPersistence, never()).save(any());
    }

    @Test
    void create_thirdPartyNodeType_rejectsBeforeAnyResolution() {
        assertThatThrownBy(() -> sut.create(command("PO-1", UUID.randomUUID(), "WH-A",
                "THIRD_PARTY_LOGISTICS", "SUP-0043", "SKU-A", 10)))
                .isInstanceOf(InboundExpectationRejectedException.class);
        // Rejected on node type before touching the warehouse master.
        verify(masterReadModel, never()).findWarehouseByCode(any());
        verify(asnPersistence, never()).save(any());
    }

    @Test
    void create_unknownSupplier_rejectsToDlt() {
        stubWarehouse("WH-A", UUID.randomUUID(), WarehouseSnapshot.Status.ACTIVE);
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        when(masterReadModel.findPartnerByCode("SUP-GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.create(command("PO-1", UUID.randomUUID(), "WH-A",
                "WMS_WAREHOUSE", "SUP-GHOST", "SKU-A", 10)))
                .isInstanceOf(InboundExpectationRejectedException.class);
        verify(asnPersistence, never()).save(any());
    }

    @Test
    void create_unknownSku_rejectsToDlt() {
        stubWarehouse("WH-A", UUID.randomUUID(), WarehouseSnapshot.Status.ACTIVE);
        stubSupplier("SUP-0043", UUID.randomUUID());
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        when(masterReadModel.findSkuByCode("SKU-GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.create(command("PO-1", UUID.randomUUID(), "WH-A",
                "WMS_WAREHOUSE", "SUP-0043", "SKU-GHOST", 10)))
                .isInstanceOf(InboundExpectationRejectedException.class);
        verify(asnPersistence, never()).save(any());
    }

    // ----------------------------------------------------------------- helpers

    private Asn captureSaved() {
        ArgumentCaptor<Asn> captor = ArgumentCaptor.forClass(Asn.class);
        verify(asnPersistence).save(captor.capture());
        return captor.getValue();
    }

    private void stubWarehouse(String code, UUID id, WarehouseSnapshot.Status status) {
        when(masterReadModel.findWarehouseByCode(code)).thenReturn(Optional.of(
                new WarehouseSnapshot(id, code, status, FIXED_NOW, 1L)));
    }

    private void stubSupplier(String code, UUID id) {
        when(masterReadModel.findPartnerByCode(code)).thenReturn(Optional.of(
                new PartnerSnapshot(id, code, PartnerSnapshot.PartnerType.SUPPLIER,
                        PartnerSnapshot.Status.ACTIVE, FIXED_NOW, 1L)));
    }

    private void stubSkuByCode(String code, UUID id) {
        when(masterReadModel.findSkuByCode(code)).thenReturn(Optional.of(
                new SkuSnapshot(id, code, SkuSnapshot.TrackingType.NONE,
                        SkuSnapshot.Status.ACTIVE, FIXED_NOW, 1L)));
    }

    private void stubSkuById(UUID id, String code) {
        when(masterReadModel.findSku(id)).thenReturn(Optional.of(
                new SkuSnapshot(id, code, SkuSnapshot.TrackingType.NONE,
                        SkuSnapshot.Status.ACTIVE, FIXED_NOW, 1L)));
    }

    private CreateScmInboundExpectationCommand command(String poNumber, UUID poId, String whCode,
                                                       String nodeType, String supplierCode,
                                                       String skuCode, int qty) {
        return new CreateScmInboundExpectationCommand(poId, poNumber, supplierCode, whCode, nodeType,
                LocalDate.of(2026, 7, 24),
                List.of(new CreateScmInboundExpectationCommand.Line(skuCode, qty)));
    }
}
