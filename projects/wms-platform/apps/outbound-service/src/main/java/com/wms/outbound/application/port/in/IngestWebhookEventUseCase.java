package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.IngestWebhookEventCommand;
import java.time.Instant;

/**
 * Inbound port for the ERP webhook ingest stage.
 *
 * <p>Persists the dedupe row and the inbox row in a single transaction.
 * Conflict on the dedupe row → {@link IngestResult.Duplicate}; first
 * delivery → {@link IngestResult.Accepted}.
 *
 * <p>Pairs with {@link ProcessWebhookInboxUseCase} which drains the inbox
 * asynchronously.
 */
public interface IngestWebhookEventUseCase {

    /**
     * Ingest result. Exactly one of {@link Accepted} or {@link Duplicate} is
     * returned per call.
     */
    sealed interface IngestResult permits IngestResult.Accepted, IngestResult.Duplicate {

        record Accepted(Instant receivedAt) implements IngestResult {
        }

        record Duplicate(Instant previouslyReceivedAt) implements IngestResult {
        }

        static IngestResult accepted(Instant receivedAt) {
            return new Accepted(receivedAt);
        }

        static IngestResult duplicate(Instant previouslyReceivedAt) {
            return new Duplicate(previouslyReceivedAt);
        }
    }

    IngestResult ingest(IngestWebhookEventCommand command);
}
