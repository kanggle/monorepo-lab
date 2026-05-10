package com.wms.outbound.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the ERP webhook inbox table.
 *
 * <p>Two distinct flows share this port:
 * <ul>
 *   <li><b>Ingest</b> — atomic dedupe + inbox-row insert at webhook reception
 *       time, used by {@link com.wms.outbound.application.port.in.IngestWebhookEventUseCase}.</li>
 *   <li><b>Drain</b> — find pending rows and flip their status, used by the
 *       background {@link com.wms.outbound.application.port.in.ProcessWebhookInboxUseCase}
 *       loop.</li>
 * </ul>
 *
 * <p>Returns port-level records — JPA entities are an adapter-internal
 * concern.
 */
public interface WebhookInboxStorePort {

    /**
     * Snapshot of a {@code PENDING} inbox row, exposed across the
     * application/adapter boundary. The {@code payload} is the raw JSON body
     * received from ERP; deserialization is handled in the application layer.
     */
    record PendingWebhookInbox(UUID id, String eventId, String source, String payload) {
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
     */
    IngestOutcome ingest(String eventId, String rawPayload, String source);

    /**
     * Returns up to {@code limit} pending rows ordered by oldest first.
     */
    List<PendingWebhookInbox> findPending(int limit);

    /**
     * Transition the row to {@code APPLIED} with the given completion instant.
     */
    void markApplied(UUID id, Instant at);

    /**
     * Transition the row to {@code FAILED} with the given completion instant.
     * The {@code reason} is captured by the caller for logging; persistence of
     * the reason is at the adapter's discretion (current schema does not store
     * it).
     */
    void markFailed(UUID id, Instant at, String reason);
}
