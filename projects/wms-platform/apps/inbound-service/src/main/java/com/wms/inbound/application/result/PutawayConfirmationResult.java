package com.wms.inbound.application.result;

import java.time.Instant;
import java.util.UUID;

public record PutawayConfirmationResult(
        UUID confirmationId,
        UUID putawayLineId,
        UUID putawayInstructionId,
        UUID actualLocationId,
        String actualLocationCode,
        int qtyConfirmed,
        String confirmedBy,
        Instant confirmedAt,
        InstructionState instruction,
        AsnState asn
) {
    public record InstructionState(
            String status,
            long confirmedLineCount,
            long skippedLineCount,
            int totalLineCount
    ) {}

    public record AsnState(
            String status
    ) {}
}
