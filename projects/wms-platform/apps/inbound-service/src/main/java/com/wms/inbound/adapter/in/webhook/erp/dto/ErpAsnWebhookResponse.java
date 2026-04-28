package com.wms.inbound.adapter.in.webhook.erp.dto;

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
public record ErpAsnWebhookResponse(
        String status,
        String eventId,
        String asnNo,
        Instant receivedAt,
        Instant previouslyReceivedAt
) {

    public static ErpAsnWebhookResponse accepted(String eventId, String asnNo, Instant receivedAt) {
        return new ErpAsnWebhookResponse("accepted", eventId, asnNo, receivedAt, null);
    }

    public static ErpAsnWebhookResponse ignoredDuplicate(String eventId, Instant previouslyReceivedAt) {
        return new ErpAsnWebhookResponse("ignored_duplicate", eventId, null, null, previouslyReceivedAt);
    }
}
