package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.SkipPutawayLineCommand;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.PutawayPersistencePort;
import com.wms.inbound.application.result.PutawaySkipResult;
import com.wms.inbound.domain.event.PutawayCompletedEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
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
class SkipPutawayLineServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "user-1";
    private static final Set<String> WRITER = Set.of("ROLE_INBOUND_WRITE");

    @Mock PutawayPersistencePort putawayPersistence;
    @Mock AsnPersistencePort asnPersistence;
    @Mock InboundEventPort eventPort;

    SkipPutawayLineService sut;

    UUID asnId;
    UUID warehouseId;
    UUID instructionId;
    UUID lineId;

    @BeforeEach
    void setUp() {
        sut = new SkipPutawayLineService(putawayPersistence, asnPersistence, eventPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
        asnId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        instructionId = UUID.randomUUID();
        lineId = UUID.randomUUID();
    }

    @Test
    void skip_lastLine_allSkipped_publishesEventWithEmptyLines() {
        PutawayInstruction instruction = singleLineInstruction();
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId)).thenReturn(instruction);
        when(putawayPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asnInPutaway()));
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PutawaySkipResult result = sut.skip(command());

        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(result.instruction().status()).isEqualTo("PARTIALLY_COMPLETED");
        assertThat(result.asn().status()).isEqualTo("PUTAWAY_DONE");
        verify(eventPort).publish(any(PutawayCompletedEvent.class));
    }

    @Test
    void skip_notLastLine_noEvent() {
        PutawayInstruction instruction = twoLineInstruction();
        when(putawayPersistence.findByIdForUpdateOrThrow(instructionId)).thenReturn(instruction);
        when(putawayPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnPersistence.findById(asnId)).thenReturn(Optional.of(asnInPutaway()));

        PutawaySkipResult result = sut.skip(command());

        assertThat(result.instruction().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.asn().status()).isEqualTo("IN_PUTAWAY");
        verify(eventPort, never()).publish(any());
    }

    @Test
    void skip_missingRole_throwsAccessDenied() {
        SkipPutawayLineCommand cmd = new SkipPutawayLineCommand(
                instructionId, lineId, "broken lift", ACTOR, Set.of());
        assertThatThrownBy(() -> sut.skip(cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    private PutawayInstruction singleLineInstruction() {
        PutawayLine line = new PutawayLine(lineId, instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 50, PutawayLineStatus.PENDING);
        return new PutawayInstruction(instructionId, asnId, warehouseId, ACTOR,
                PutawayInstructionStatus.PENDING, 0L,
                NOW, ACTOR, NOW, ACTOR, List.of(line));
    }

    private PutawayInstruction twoLineInstruction() {
        PutawayLine first = new PutawayLine(lineId, instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 50, PutawayLineStatus.PENDING);
        PutawayLine second = new PutawayLine(UUID.randomUUID(), instructionId, UUID.randomUUID(),
                UUID.randomUUID(), null, null, UUID.randomUUID(), 25, PutawayLineStatus.PENDING);
        return new PutawayInstruction(instructionId, asnId, warehouseId, ACTOR,
                PutawayInstructionStatus.PENDING, 0L,
                NOW, ACTOR, NOW, ACTOR, List.of(first, second));
    }

    private Asn asnInPutaway() {
        return new Asn(asnId, "ASN-001", AsnSource.MANUAL, UUID.randomUUID(), warehouseId,
                LocalDate.of(2026, 5, 1), null, AsnStatus.IN_PUTAWAY, 1L,
                NOW, ACTOR, NOW, ACTOR, List.of());
    }

    private SkipPutawayLineCommand command() {
        return new SkipPutawayLineCommand(instructionId, lineId,
                "Location WH01-A-01-01-01 lift broken", ACTOR, WRITER);
    }
}
