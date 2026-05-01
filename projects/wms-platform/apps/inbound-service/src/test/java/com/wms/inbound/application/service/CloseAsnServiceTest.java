package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.CloseAsnCommand;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.CloseAsnResult;
import com.wms.inbound.domain.event.AsnClosedEvent;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionLine;
import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import com.wms.inbound.domain.model.PutawayInstructionStatus;
import com.wms.inbound.domain.model.PutawayLine;
import com.wms.inbound.domain.model.PutawayLineStatus;
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
class CloseAsnServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "user-1";
    private static final Set<String> WRITER = Set.of("ROLE_INBOUND_WRITE");

    @Mock AsnPersistencePort asnPersistence;
    @Mock InspectionPersistencePort inspectionPersistence;
    @Mock PutawayPersistencePort putawayPersistence;
    @Mock InboundEventPort eventPort;

    CloseAsnService sut;

    UUID asnId;
    UUID asnLineId;
    UUID warehouseId;

    @BeforeEach
    void setUp() {
        sut = new CloseAsnService(asnPersistence, inspectionPersistence, putawayPersistence,
                eventPort, Clock.fixed(NOW, ZoneOffset.UTC));
        asnId = UUID.randomUUID();
        asnLineId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
    }

    @Test
    void close_happyPath_emitsAsnClosedWithSummary() {
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asn(AsnStatus.PUTAWAY_DONE)));
        when(inspectionPersistence.findByAsnId(asnId)).thenReturn(Optional.of(inspection()));
        UUID confirmedLineId = UUID.randomUUID();
        when(putawayPersistence.findByAsnId(asnId)).thenReturn(Optional.of(instruction(confirmedLineId)));
        when(putawayPersistence.findConfirmationByLineId(confirmedLineId))
                .thenReturn(Optional.of(confirmation(confirmedLineId, 95)));
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CloseAsnResult result = sut.close(new CloseAsnCommand(asnId, 4L, ACTOR, WRITER));

        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.summary().expectedTotal()).isEqualTo(100);
        assertThat(result.summary().passedTotal()).isEqualTo(95);
        assertThat(result.summary().damagedTotal()).isEqualTo(3);
        assertThat(result.summary().shortTotal()).isEqualTo(2);
        assertThat(result.summary().putawayConfirmedTotal()).isEqualTo(95);
        verify(eventPort).publish(any(AsnClosedEvent.class));
    }

    @Test
    void close_asnNotFound_throws() {
        when(asnPersistence.findById(asnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.close(new CloseAsnCommand(asnId, 0L, ACTOR, WRITER)))
                .isInstanceOf(AsnNotFoundException.class);

        verify(eventPort, never()).publish(any());
    }

    @Test
    void close_asnNotInPutawayDone_throwsStateTransitionInvalid() {
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asn(AsnStatus.IN_PUTAWAY)));
        // CloseAsnService computes summary BEFORE state transition, so the inspection /
        // putaway lookups happen even on the failure path.
        when(inspectionPersistence.findByAsnId(asnId)).thenReturn(Optional.empty());
        when(putawayPersistence.findByAsnId(asnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.close(new CloseAsnCommand(asnId, 0L, ACTOR, WRITER)))
                .isInstanceOf(StateTransitionInvalidException.class);

        verify(eventPort, never()).publish(any());
    }

    @Test
    void close_missingRole_throwsAccessDenied() {
        assertThatThrownBy(() -> sut.close(new CloseAsnCommand(asnId, 0L, ACTOR, Set.of())))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Asn asn(AsnStatus status) {
        AsnLine line = new AsnLine(asnLineId, asnId, 1, UUID.randomUUID(), null, 100);
        return new Asn(asnId, "ASN-001", AsnSource.MANUAL, UUID.randomUUID(), warehouseId,
                LocalDate.of(2026, 5, 1), null, status, 4L,
                NOW, ACTOR, NOW, ACTOR, List.of(line));
    }

    private Inspection inspection() {
        UUID inspectionId = UUID.randomUUID();
        InspectionLine il = new InspectionLine(UUID.randomUUID(), inspectionId, asnLineId,
                UUID.randomUUID(), null, null, 95, 3, 2);
        return new Inspection(inspectionId, asnId, ACTOR, NOW, null, 1L,
                NOW, ACTOR, NOW, ACTOR, List.of(il), List.of());
    }

    private PutawayInstruction instruction(UUID confirmedLineId) {
        PutawayLine line = new PutawayLine(confirmedLineId, UUID.randomUUID(), asnLineId,
                UUID.randomUUID(), null, null, UUID.randomUUID(), 95, PutawayLineStatus.CONFIRMED);
        return new PutawayInstruction(UUID.randomUUID(), asnId, warehouseId, ACTOR,
                PutawayInstructionStatus.COMPLETED, 1L,
                NOW, ACTOR, NOW, ACTOR, List.of(line));
    }

    private PutawayConfirmation confirmation(UUID lineId, int qty) {
        return new PutawayConfirmation(UUID.randomUUID(), UUID.randomUUID(), lineId,
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                qty, ACTOR, NOW);
    }
}
