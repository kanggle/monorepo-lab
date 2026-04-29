package com.wms.inbound.application.result;

import java.time.Instant;
import java.util.UUID;

public record PutawaySkipResult(
        UUID putawayLineId,
        String status,
        String skippedReason,
        String skippedBy,
        Instant skippedAt,
        PutawayConfirmationResult.InstructionState instruction,
        PutawayConfirmationResult.AsnState asn
) {}
