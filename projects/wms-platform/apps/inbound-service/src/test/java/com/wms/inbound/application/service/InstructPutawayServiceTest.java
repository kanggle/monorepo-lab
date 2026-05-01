package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.InstructPutawayCommand;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.domain.event.PutawayInstructedEvent;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.exception.LotRequiredException;
import com.wms.inbound.domain.exception.PutawayQuantityExceededException;
import com.wms.inbound.domain.exception.WarehouseMismatchException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionLine;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class InstructPutawayServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "user-1";
    private static final Set<String> WRITER = Set.of("ROLE_INBOUND_WRITE");

    @Mock AsnPersistencePort asnPersistence;
    @Mock InspectionPersistencePort inspectionPersistence;
    @Mock PutawayPersistencePort putawayPersistence;
    @Mock InboundEventPort eventPort;
    @Mock MasterReadModelPort masterReadModel;

    InstructPutawayService sut;

    UUID asnId;
    UUID asnLineId;
    UUID skuId;
    UUID warehouseId;
    UUID destinationLocationId;

    @BeforeEach
    void setUp() {
        sut = new InstructPutawayService(asnPersistence, inspectionPersistence, putawayPersistence,
                eventPort, masterReadModel, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        asnId = UUID.randomUUID();
        asnLineId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        destinationLocationId = UUID.randomUUID();
    }

    @Test
    void instruct_happyPath_savesAndPublishesEvent() {
        stubAsnAndInspection(AsnStatus.INSPECTED, 95);
        stubLocation(true, warehouseId);
        stubSku(SkuSnapshot.TrackingType.NONE);
        when(putawayPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PutawayInstructionResult result = sut.instruct(command(95));

        assertThat(result.asnId()).isEqualTo(asnId);
        assertThat(result.asnStatus()).isEqualTo(AsnStatus.IN_PUTAWAY.name());
        assertThat(result.lines()).hasSize(1);
        verify(putawayPersistence).save(any(PutawayInstruction.class));
        verify(asnPersistence).save(any(Asn.class));
        verify(eventPort).publish(any(PutawayInstructedEvent.class));
    }

    @Test
    void instruct_asnNotInspected_throwsStateTransitionInvalid() {
        stubAsnAndInspection(AsnStatus.CREATED, 95);
        stubLocation(true, warehouseId);
        stubSku(SkuSnapshot.TrackingType.NONE);

        assertThatThrownBy(() -> sut.instruct(command(95)))
                .isInstanceOf(com.wms.inbound.domain.exception.StateTransitionInvalidException.class);

        verify(eventPort, never()).publish(any());
    }

    @Test
    void instruct_qtyExceedsInspectionPassed_throwsPutawayQuantityExceeded() {
        stubAsnAndInspection(AsnStatus.INSPECTED, 50);
        // Note: the qty check runs before the location check, so location stub is unnecessary
        // and would be unused (Mockito strict-stubs warning). Skipping.

        assertThatThrownBy(() -> sut.instruct(command(60)))
                .isInstanceOf(PutawayQuantityExceededException.class);
    }

    @Test
    void instruct_locationInactive_throwsLocationInactive() {
        stubAsnAndInspection(AsnStatus.INSPECTED, 95);
        stubLocation(false, warehouseId);
        stubSku(SkuSnapshot.TrackingType.NONE);

        assertThatThrownBy(() -> sut.instruct(command(95)))
                .isInstanceOf(LocationInactiveException.class);
    }

    @Test
    void instruct_locationWrongWarehouse_throwsWarehouseMismatch() {
        UUID otherWarehouse = UUID.randomUUID();
        stubAsnAndInspection(AsnStatus.INSPECTED, 95);
        stubLocation(true, otherWarehouse);
        stubSku(SkuSnapshot.TrackingType.NONE);

        assertThatThrownBy(() -> sut.instruct(command(95)))
                .isInstanceOf(WarehouseMismatchException.class);
    }

    @Test
    void instruct_lotRequiredButMissing_throwsLotRequired() {
        stubAsnAndInspection(AsnStatus.INSPECTED, 95);
        stubSku(SkuSnapshot.TrackingType.LOT);

        assertThatThrownBy(() -> sut.instruct(command(95)))
                .isInstanceOf(LotRequiredException.class);
    }

    @Test
    void instruct_missingRole_throwsAccessDenied() {
        InstructPutawayCommand cmd = new InstructPutawayCommand(
                asnId,
                List.of(new InstructPutawayCommand.Line(asnLineId, destinationLocationId, 95)),
                0L, ACTOR, Set.of());
        assertThatThrownBy(() -> sut.instruct(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void stubAsnAndInspection(AsnStatus asnStatus, int qtyPassed) {
        AsnLine asnLine = new AsnLine(asnLineId, asnId, 1, skuId, null, 100);
        Asn asn = new Asn(asnId, "ASN-TEST-001", AsnSource.MANUAL,
                UUID.randomUUID(), warehouseId, LocalDate.of(2026, 5, 1), null,
                asnStatus, 0L, FIXED_NOW, ACTOR, FIXED_NOW, ACTOR, List.of(asnLine));
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asn));

        UUID inspectionId = UUID.randomUUID();
        InspectionLine il = new InspectionLine(UUID.randomUUID(), inspectionId, asnLineId,
                skuId, null, null, qtyPassed, 0, 0);
        Inspection inspection = new Inspection(inspectionId, asnId, ACTOR, FIXED_NOW, null, 1L,
                FIXED_NOW, ACTOR, FIXED_NOW, ACTOR, List.of(il), List.of());
        when(inspectionPersistence.findByAsnId(asnId)).thenReturn(Optional.of(inspection));
    }

    private void stubLocation(boolean active, UUID locWarehouseId) {
        LocationSnapshot loc = new LocationSnapshot(destinationLocationId,
                "WH01-A-01-01-01", locWarehouseId, UUID.randomUUID(),
                LocationSnapshot.LocationType.STORAGE,
                active ? LocationSnapshot.Status.ACTIVE : LocationSnapshot.Status.INACTIVE,
                FIXED_NOW, 1L);
        when(masterReadModel.findLocation(destinationLocationId)).thenReturn(Optional.of(loc));
    }

    private void stubSku(SkuSnapshot.TrackingType trackingType) {
        SkuSnapshot sku = new SkuSnapshot(skuId, "SKU-001", trackingType,
                SkuSnapshot.Status.ACTIVE, FIXED_NOW, 1L);
        when(masterReadModel.findSku(skuId)).thenReturn(Optional.of(sku));
    }

    private InstructPutawayCommand command(int qtyToPutaway) {
        return new InstructPutawayCommand(asnId,
                List.of(new InstructPutawayCommand.Line(asnLineId, destinationLocationId, qtyToPutaway)),
                0L, ACTOR, WRITER);
    }
}
