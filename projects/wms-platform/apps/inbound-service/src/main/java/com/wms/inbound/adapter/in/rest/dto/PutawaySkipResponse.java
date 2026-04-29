package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.PutawaySkipResult;
import java.time.Instant;
import java.util.UUID;

public record PutawaySkipResponse(
        UUID putawayLineId,
        String status,
        String skippedReason,
        String skippedBy,
        Instant skippedAt,
        PutawayConfirmationResponse.Instruction instruction,
        PutawayConfirmationResponse.Asn asn
) {
    public static PutawaySkipResponse from(PutawaySkipResult r) {
        return new PutawaySkipResponse(
                r.putawayLineId(), r.status(), r.skippedReason(),
                r.skippedBy(), r.skippedAt(),
                new PutawayConfirmationResponse.Instruction(
                        r.instruction().status(),
                        r.instruction().confirmedLineCount(),
                        r.instruction().skippedLineCount(),
                        r.instruction().totalLineCount()),
                new PutawayConfirmationResponse.Asn(r.asn().status()));
    }
}
