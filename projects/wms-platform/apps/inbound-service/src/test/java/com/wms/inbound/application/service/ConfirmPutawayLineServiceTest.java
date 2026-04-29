package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.ConfirmPutawayLineCommand;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.PutawayLineStatus;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
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
class ConfirmPutawayLineServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "user-1";
    private static final Set<String> WRITER = Set.of("ROLE_INBOUND_WRITE");

    @Mock PutawayPersistencePort putawayPersistence;
    @Mock AsnPersistencePort asnPersistence;
    @Mock InboundEventPort eventPort;
    @Mock MasterReadModelPort masterReadModel;

    ConfirmPutawayLineService sut;

    UUID asnId;
    UUID warehouseId;
    UUID instructionId;
    UUID lineId;
    UUID actualLocationId;

    @BeforeEach
    void setUp() {
        sut = new ConfirmPutawayLineService(putawayPersistence, asnPersistence, eventPort,
                masterReadModel, Clock.fixed(NOW, ZoneOffset.UTC));
        asnId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        instructionId = UUID.randomUUID();
        lineId = UUID.randomUUID();
        actualLocationId = UUID.randomUUID();
    }

    @Test
    void confirm_lastLine_completesPutawayAndPublishesEvent() {
        PutawayInstruction instruction = singleLineInstruction();
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId)).thenReturn(instruction);
        stubLocation(true, warehouseId);
        when(putawayPersistence.saveConfirmation(any())).thenAnswer(inv -> inv.getArgument(0));
        when(putawayPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asnInPutaway()));
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PutawayConfirmationResult result = sut.confirm(command(50));

        assertThat(result.instruction().status())
                .isEqualTo("COMPLETED");
        assertThat(result.asn().status()).isEqualTo("PUTAWAY_DONE");
        verify(eventPort).publish(any(PutawayCompletedEvent.class));
        verify(putawayPersistence).saveConfirmation(any(PutawayConfirmation.class));
    }

    @Test
    void confirm_notLastLine_doesNotPublishCompletedEvent() {
        PutawayInstruction instruction = twoLineInstructionFirstPending();
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId)).thenReturn(instruction);
        stubLocation(true, warehouseId);
        when(putawayPersistence.saveConfirmation(any())).thenAnswer(inv -> inv.getArgument(0));
        when(putawayPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asnInPutaway()));

        PutawayConfirmationResult result = sut.confirm(command(50));

        assertThat(result.instruction().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.asn().status()).isEqualTo("IN_PUTAWAY");
        verify(eventPort, never()).publish(any());
    }

    @Test
    void confirm_locationInactive_throws() {
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId))
                .thenReturn(singleLineInstruction());
        stubLocation(false, warehouseId);

        assertThatThrownBy(() -> sut.confirm(command(50)))
                .isInstanceOf(LocationInactiveException.class);
    }

    @Test
    void confirm_qtyMismatch_throwsValidationError() {
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId))
                .thenReturn(singleLineInstruction());

        assertThatThrownBy(() -> sut.confirm(command(40)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirm_missingRole_throwsAccessDenied() {
        ConfirmPutawayLineCommand cmd = new ConfirmPutawayLineCommand(
                instructionId, lineId, actualLocationId, 50, ACTOR, Set.of());
        assertThatThrownBy(() -> sut.confirm(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    private PutawayInstruction singleLineInstruction() {
        PutawayLine line = new PutawayLine(lineId, instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 50, PutawayLineStatus.PENDING);
        return new PutawayInstruction(instructionId, asnId, warehouseId, ACTOR,
                com.wms.inbound.domain.model.PutawayInstructionStatus.PENDING, 0L,
                NOW, ACTOR, NOW, ACTOR, List.of(line));
    }

    private PutawayInstruction twoLineInstructionFirstPending() {
        PutawayLine first = new PutawayLine(lineId, instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 50, PutawayLineStatus.PENDING);
        PutawayLine second = new PutawayLine(UUID.randomUUID(), instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 25, PutawayLineStatus.PENDING);
        return new PutawayInstruction(instructionId, asnId, warehouseId, ACTOR,
                com.wms.inbound.domain.model.PutawayInstructionStatus.PENDING, 0L,
                NOW, ACTOR, NOW, ACTOR, List.of(first, second));
    }

    private Asn asnInPutaway() {
        return new Asn(asnId, "ASN-001", AsnSource.MANUAL, UUID.randomUUID(), warehouseId,
                LocalDate.of(2026, 5, 1), null, AsnStatus.IN_PUTAWAY, 1L,
                NOW, ACTOR, NOW, ACTOR, List.of());
    }

    private void stubLocation(boolean active, UUID locWarehouseId) {
        LocationSnapshot loc = new LocationSnapshot(actualLocationId, "WH01-A-01-01-01",
                locWarehouseId, UUID.randomUUID(),
                LocationSnapshot.LocationType.STORAGE,
                active ? LocationSnapshot.Status.ACTIVE : LocationSnapshot.Status.INACTIVE,
                NOW, 1L);
        when(masterReadModel.findLocation(actualLocationId)).thenReturn(Optional.of(loc));
    }

    private ConfirmPutawayLineCommand command(int qty) {
        return new ConfirmPutawayLineCommand(instructionId, lineId, actualLocationId, qty,
                ACTOR, WRITER);
    }
}
