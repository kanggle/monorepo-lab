package com.wms.inbound.application.command;

/**
 * Command for ingesting an ERP ASN webhook delivery into the inbox table.
 *
 * <p>The HMAC / timestamp / schema validation has already passed when this
 * command is built — this stage only touches the dedupe row and the inbox row.
 *
 * @param eventId    the ERP-supplied delivery id (unique per webhook event)
 * @param rawPayload the verbatim JSON body, stored as-is in the inbox table
 * @param signature  the {@code X-Erp-Signature} header value, stored on the
 *                   inbox row for forensic auditability
 * @param source     environment tag from {@code X-Erp-Source}
 */
public record IngestWebhookEventCommand(String eventId, String rawPayload, String signature, String source) {
}
