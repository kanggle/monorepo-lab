package com.wms.inbound.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for the ERP ASN webhook inbox table.
 *
 * <p>Two distinct flows share this port:
 * <ul>
 *   <li><b>Ingest</b> — atomic dedupe + inbox-row insert at webhook reception
 *       time, used by {@link com.wms.inbound.application.port.in.IngestWebhookEventUseCase}.</li>
 *   <li><b>Drain</b> — find pending rows and flip their status, used by the
 *       background {@code ErpWebhookInboxProcessor} loop.</li>
 * </ul>
 *
 * <p>Returns port-level records — JPA entities are an adapter-internal
 * concern.
 *
 * <p>The {@code event_id} (String) is the natural primary key of the inbound
 * webhook inbox table — distinct from outbound's per-row UUID + event-id
 * design — so the port surfaces {@code String} identifiers.
 */
public interface WebhookInboxStorePort {

    /**
     * Snapshot of a {@code PENDING} inbox row, exposed across the
     * application/adapter boundary. The {@code payload} is the raw JSON body
     * received from ERP; deserialization is handled in the application layer.
     */
    record PendingWebhookInbox(String eventId, String source, String payload) {
    }

    /**
     * Result of an {@link #ingest} call. Exactly one variant is returned per
     * call.
     */
    sealed interface IngestOutcome permits IngestOutcome.Accepted, IngestOutcome.Duplicate {

        record Accepted(Instant receivedAt) implements IngestOutcome {
        }

        record Duplicate(Instant previouslyReceivedAt) implements IngestOutcome {
        }
    }

    /**
     * Persist the dedupe row and (on first delivery) the inbox row in one
     * transaction. Conflict on the dedupe row → {@link IngestOutcome.Duplicate};
     * first delivery → {@link IngestOutcome.Accepted}.
     *
     * <p>The {@code signature} is stored on the inbox row for forensic
     * auditability — it is not used for further verification at this stage
     * (the controller already verified it before calling).
     */
    IngestOutcome ingest(String eventId, String rawPayload, String signature, String source);

    /**
     * Returns up to {@code limit} pending rows ordered by oldest first.
     */
    List<PendingWebhookInbox> findPending(int limit);

    /**
     * Transition the row to {@code APPLIED} with the given completion instant.
     */
    void markApplied(String eventId, Instant at);

    /**
     * Transition the row to {@code FAILED} with the given completion instant.
     * The {@code reason} is captured by the caller for logging and stored on
     * the inbox row's {@code failure_reason} column.
     */
    void markFailed(String eventId, Instant at, String reason);
}
