package com.wms.inbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PutawayInstructionTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "test-actor";

    @Test
    void confirmLine_lastLine_transitionsToCompleted() {
        UUID lineId = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(lineId)));

        PutawayInstruction.Transition t = instruction.confirmLine(lineId, NOW, ACTOR);

        assertThat(t.isLastLine()).isTrue();
        assertThat(instruction.getStatus()).isEqualTo(PutawayInstructionStatus.COMPLETED);
        assertThat(instruction.getLine(lineId).getStatus()).isEqualTo(PutawayLineStatus.CONFIRMED);
    }

    @Test
    void confirmLine_oneOfTwo_transitionsToInProgress_andNotLast() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(a), line(b)));

        PutawayInstruction.Transition t = instruction.confirmLine(a, NOW, ACTOR);

        assertThat(t.isLastLine()).isFalse();
        assertThat(instruction.getStatus()).isEqualTo(PutawayInstructionStatus.IN_PROGRESS);
    }

    @Test
    void skipLine_lastLine_anySkipped_transitionsToPartiallyCompleted() {
        UUID lineId = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(lineId)));

        PutawayInstruction.Transition t = instruction.skipLine(lineId, NOW, ACTOR);

        assertThat(t.isLastLine()).isTrue();
        assertThat(instruction.getStatus()).isEqualTo(PutawayInstructionStatus.PARTIALLY_COMPLETED);
        assertThat(instruction.getLine(lineId).getStatus()).isEqualTo(PutawayLineStatus.SKIPPED);
    }

    @Test
    void confirmAllLines_noneSkipped_transitionsToCompleted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(a), line(b)));

        instruction.confirmLine(a, NOW, ACTOR);
        PutawayInstruction.Transition t = instruction.confirmLine(b, NOW, ACTOR);

        assertThat(t.isLastLine()).isTrue();
        assertThat(instruction.getStatus()).isEqualTo(PutawayInstructionStatus.COMPLETED);
        assertThat(instruction.confirmedLineCount()).isEqualTo(2L);
        assertThat(instruction.skippedLineCount()).isZero();
    }

    @Test
    void mixedConfirmAndSkip_lastResolved_transitionsToPartiallyCompleted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(a), line(b)));

        instruction.confirmLine(a, NOW, ACTOR);
        PutawayInstruction.Transition t = instruction.skipLine(b, NOW, ACTOR);

        assertThat(t.isLastLine()).isTrue();
        assertThat(instruction.getStatus()).isEqualTo(PutawayInstructionStatus.PARTIALLY_COMPLETED);
        assertThat(instruction.confirmedLineCount()).isEqualTo(1L);
        assertThat(instruction.skippedLineCount()).isEqualTo(1L);
        assertThat(instruction.confirmedLines()).hasSize(1);
        assertThat(instruction.confirmedLines().get(0).getId()).isEqualTo(a);
    }

    @Test
    void confirmLine_alreadyConfirmed_throwsStateTransitionInvalid() {
        UUID lineId = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(lineId)));
        instruction.confirmLine(lineId, NOW, ACTOR);

        assertThatThrownBy(() -> instruction.confirmLine(lineId, NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void skipLine_alreadyConfirmed_throwsStateTransitionInvalid() {
        UUID lineId = UUID.randomUUID();
        PutawayInstruction instruction = instruction(List.of(line(lineId)));
        instruction.confirmLine(lineId, NOW, ACTOR);

        assertThatThrownBy(() -> instruction.skipLine(lineId, NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void confirmLine_unknownLineId_throwsPutawayLineNotFound() {
        PutawayInstruction instruction = instruction(List.of(line(UUID.randomUUID())));
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> instruction.confirmLine(unknown, NOW, ACTOR))
                .isInstanceOf(PutawayLineNotFoundException.class);
    }

    private static PutawayInstruction instruction(List<PutawayLine> lines) {
        return PutawayInstruction.createNew(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), ACTOR, NOW, lines);
    }

    private static PutawayLine line(UUID id) {
        return new PutawayLine(id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null,
                UUID.randomUUID(), 10, PutawayLineStatus.PENDING);
    }
}
