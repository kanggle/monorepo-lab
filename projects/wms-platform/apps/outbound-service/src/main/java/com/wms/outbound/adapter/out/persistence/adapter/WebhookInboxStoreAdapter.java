package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookDedupe;
import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookInbox;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookDedupeRepository;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookInboxRepository;
import com.wms.outbound.application.port.out.WebhookInboxStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter implementing {@link WebhookInboxStorePort}.
 *
 * <p>Translates between the JPA entity {@link ErpOrderWebhookInbox} and the
 * port-level {@link PendingWebhookInbox} record so the application layer never
 * sees adapter types.
 *
 * <p>{@code ingest} runs the dedupe-row + inbox-row insert in one
 * {@code @Transactional} boundary (per
 * {@code specs/contracts/webhooks/erp-order-webhook.md} § Replay Dedupe).
 *
 * <p>{@code markApplied} / {@code markFailed} run inside the standard TX scope
 * — propagation is decided by the caller bean
 * ({@link com.wms.outbound.application.service.WebhookInboxStatusUpdater}
 * uses {@code REQUIRES_NEW}).
 */
@Component
public class WebhookInboxStoreAdapter implements WebhookInboxStorePort {

    private static final Logger log = LoggerFactory.getLogger(WebhookInboxStoreAdapter.class);

    private final ErpOrderWebhookInboxRepository inboxRepo;
    private final ErpOrderWebhookDedupeRepository dedupeRepo;
    private final Clock clock;

    public WebhookInboxStoreAdapter(ErpOrderWebhookInboxRepository inboxRepo,
                                    ErpOrderWebhookDedupeRepository dedupeRepo,
                                    Clock clock) {
        this.inboxRepo = inboxRepo;
        this.dedupeRepo = dedupeRepo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IngestOutcome ingest(String eventId, String rawPayload, String source) {
        Instant receivedAt = clock.instant();
        try {
            dedupeRepo.saveAndFlush(new ErpOrderWebhookDedupe(eventId, source, receivedAt));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("webhook_duplicate eventId={} source={}", eventId, source);
            Optional<ErpOrderWebhookDedupe> existing = dedupeRepo.findById(eventId);
            Instant previously = existing.map(ErpOrderWebhookDedupe::getReceivedAt).orElse(receivedAt);
            return new IngestOutcome.Duplicate(previously);
        }
        ErpOrderWebhookInbox inboxRow = new ErpOrderWebhookInbox(
                UUID.randomUUID(), eventId, source, rawPayload, "PENDING", receivedAt);
        inboxRepo.save(inboxRow);
        log.info("webhook_accepted eventId={} source={}", eventId, source);
        return new IngestOutcome.Accepted(receivedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingWebhookInbox> findPending(int limit) {
        List<ErpOrderWebhookInbox> rows = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(limit));
        return rows.stream()
                .map(r -> new PendingWebhookInbox(
                        r.getId(), r.getEventId(), r.getSource(), r.getPayload()))
                .toList();
    }

    @Override
    @Transactional
    public void markApplied(UUID id, Instant at) {
        inboxRepo.findById(id).ifPresent(row -> row.markApplied(at));
    }

    @Override
    @Transactional
    public void markFailed(UUID id, Instant at, String reason) {
        // The current schema does not persist the failure reason; the caller
        // logs it separately. The signature includes it so future schema
        // additions are non-breaking at the port level.
        inboxRepo.findById(id).ifPresent(row -> row.markFailed(at));
    }
}
