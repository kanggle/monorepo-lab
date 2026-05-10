package com.wms.inbound.adapter.out.persistence.webhook;

import com.wms.inbound.application.port.out.WebhookInboxStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter implementing {@link WebhookInboxStorePort}.
 *
 * <p>Translates between the JPA entities ({@link ErpWebhookInboxJpaEntity},
 * {@link ErpWebhookDedupeJpaEntity}) and the port-level
 * {@link WebhookInboxStorePort.PendingWebhookInbox} record so the application
 * layer never sees adapter types.
 *
 * <p>{@code ingest} runs the dedupe-row + inbox-row insert in one
 * {@code @Transactional} boundary (per
 * {@code specs/contracts/webhooks/erp-asn-webhook.md} § Replay Dedupe). The
 * duplicate is detected by primary-key violation on
 * {@code erp_webhook_dedupe.event_id} (the DB-level append-only trigger from
 * V7 blocks any UPDATE/DELETE on the table — only INSERT is allowed).
 *
 * <p>{@code markApplied} / {@code markFailed} run inside the standard TX scope
 * — propagation is decided by the caller bean
 * ({@link com.wms.inbound.application.service.WebhookInboxStatusUpdater} uses
 * {@code REQUIRES_NEW}).
 */
@Component
public class WebhookInboxStoreAdapter implements WebhookInboxStorePort {

    private static final Logger log = LoggerFactory.getLogger(WebhookInboxStoreAdapter.class);

    private final ErpWebhookInboxJpaRepository inboxRepo;
    private final ErpWebhookDedupeJpaRepository dedupeRepo;
    private final Clock clock;

    public WebhookInboxStoreAdapter(ErpWebhookInboxJpaRepository inboxRepo,
                                    ErpWebhookDedupeJpaRepository dedupeRepo,
                                    Clock clock) {
        this.inboxRepo = inboxRepo;
        this.dedupeRepo = dedupeRepo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IngestOutcome ingest(String eventId, String rawPayload, String signature, String source) {
        Instant receivedAt = clock.instant();
        try {
            dedupeRepo.saveAndFlush(new ErpWebhookDedupeJpaEntity(eventId, receivedAt));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("webhook_duplicate eventId={} source={}", eventId, source);
            Optional<ErpWebhookDedupeJpaEntity> existing = dedupeRepo.findById(eventId);
            Instant previously = existing.map(ErpWebhookDedupeJpaEntity::getReceivedAt)
                    .orElse(receivedAt);
            return new IngestOutcome.Duplicate(previously);
        }
        ErpWebhookInboxJpaEntity inboxRow = new ErpWebhookInboxJpaEntity(
                eventId, rawPayload, signature, source, receivedAt, "PENDING");
        inboxRepo.save(inboxRow);
        log.info("webhook_accepted eventId={} source={}", eventId, source);
        return new IngestOutcome.Accepted(receivedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingWebhookInbox> findPending(int limit) {
        List<ErpWebhookInboxJpaEntity> rows = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(limit));
        return rows.stream()
                .map(r -> new PendingWebhookInbox(r.getEventId(), r.getSource(), r.getRawPayload()))
                .toList();
    }

    @Override
    @Transactional
    public void markApplied(String eventId, Instant at) {
        inboxRepo.findById(eventId).ifPresent(row -> row.markApplied(at));
    }

    @Override
    @Transactional
    public void markFailed(String eventId, Instant at, String reason) {
        inboxRepo.findById(eventId).ifPresent(row -> row.markFailed(at, reason));
    }
}
