package com.wms.outbound.adapter.in.webhook.erp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Webhook response envelope. Exactly one of the two factory methods is used
 * per request:
 *
 * <ul>
 *   <li>{@link #accepted(String, String, Instant)} — first delivery, inbox row created</li>
 *   <li>{@link #ignoredDuplicate(String, Instant)} — duplicate event-id</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookAckResponse(
        String status,
        String eventId,
        String orderNo,
        Instant receivedAt,
        Instant previouslyReceivedAt
) {

    public static WebhookAckResponse accepted(String eventId, String orderNo, Instant receivedAt) {
        return new WebhookAckResponse("accepted", eventId, orderNo, receivedAt, null);
    }

    public static WebhookAckResponse ignoredDuplicate(String eventId, Instant previouslyReceivedAt) {
        return new WebhookAckResponse("ignored_duplicate", eventId, null, null, previouslyReceivedAt);
    }
}
