package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.PutawayConfirmationResult;
import java.time.Instant;
import java.util.UUID;

public record PutawayConfirmationResponse(
        UUID confirmationId,
        UUID putawayLineId,
        UUID putawayInstructionId,
        UUID actualLocationId,
        String actualLocationCode,
        int qtyConfirmed,
        String confirmedBy,
        Instant confirmedAt,
        Instruction instruction,
        Asn asn
) {
    public record Instruction(
            String status,
            long confirmedLineCount,
            long skippedLineCount,
            int totalLineCount
    ) {}

    public record Asn(String status) {}

    public static PutawayConfirmationResponse from(PutawayConfirmationResult r) {
        return new PutawayConfirmationResponse(
                r.confirmationId(), r.putawayLineId(), r.putawayInstructionId(),
                r.actualLocationId(), r.actualLocationCode(), r.qtyConfirmed(),
                r.confirmedBy(), r.confirmedAt(),
                new Instruction(r.instruction().status(),
                        r.instruction().confirmedLineCount(),
                        r.instruction().skippedLineCount(),
                        r.instruction().totalLineCount()),
                new Asn(r.asn().status()));
    }
}
