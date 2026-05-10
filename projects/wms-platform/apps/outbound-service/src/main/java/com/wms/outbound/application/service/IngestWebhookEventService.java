package com.wms.outbound.application.service;

import com.wms.outbound.application.command.IngestWebhookEventCommand;
import com.wms.outbound.application.port.in.IngestWebhookEventUseCase;
import com.wms.outbound.application.port.out.WebhookInboxStorePort;
import com.wms.outbound.application.port.out.WebhookInboxStorePort.IngestOutcome;
import org.springframework.stereotype.Service;

/**
 * Implements {@link IngestWebhookEventUseCase}.
 *
 * <p>Pure delegation to {@link WebhookInboxStorePort#ingest} — the port itself
 * runs the dedupe + inbox writes inside a single transaction (decided at the
 * adapter implementation). This service exists so the controller depends on
 * an application port, not on the persistence adapter.
 *
 * <p>Translates the port-level {@link IngestOutcome} to the inbound port's
 * {@link IngestWebhookEventUseCase.IngestResult}. Both shapes are
 * intentionally similar so the translation is mechanical, but they are
 * separate types because they live on opposite sides of the application
 * layer.
 */
@Service
public class IngestWebhookEventService implements IngestWebhookEventUseCase {

    private final WebhookInboxStorePort inboxStore;

    public IngestWebhookEventService(WebhookInboxStorePort inboxStore) {
        this.inboxStore = inboxStore;
    }

    @Override
    public IngestResult ingest(IngestWebhookEventCommand command) {
        IngestOutcome outcome = inboxStore.ingest(
                command.eventId(), command.rawPayload(), command.source());
        return switch (outcome) {
            case IngestOutcome.Accepted accepted -> IngestResult.accepted(accepted.receivedAt());
            case IngestOutcome.Duplicate duplicate ->
                    IngestResult.duplicate(duplicate.previouslyReceivedAt());
        };
    }
}
