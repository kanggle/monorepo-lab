package com.wms.inbound.adapter.in.messaging.scm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Decoded envelope of an scm {@code inbound-expected} family event (ADR-MONO-050).
 *
 * <p>{@code eventId} is the idempotency key (T8 dedup); {@code body} carries the business fields
 * (the {@code payload} object under the standard wms envelope, or the root when a producer emits
 * a flat shape — the parser tolerates both).
 */
public record ScmInboundExpectedEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        JsonNode body
) {}
