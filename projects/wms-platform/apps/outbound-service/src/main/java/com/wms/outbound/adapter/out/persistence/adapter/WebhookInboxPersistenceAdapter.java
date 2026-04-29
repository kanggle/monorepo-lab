package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookDedupe;
import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookInbox;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookDedupeRepository;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookInboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically applies the dedupe + inbox writes for an ERP order webhook
 * delivery.
 *
 * <p>Per
 * {@code specs/contracts/webhooks/erp-order-webhook.md} § Replay Dedupe the
 * dedupe insert and inbox insert run inside a single {@code @Transactional}
 * boundary. Conflict on the dedupe row → no inbox write; the controller maps
 * this to a 200 {@code ignored_duplicate} response.
 */
@Component
public class WebhookInboxPersistenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookInboxPersistenceAdapter.class);

    private final ErpOrderWebhookInboxRepository inboxRepo;
    private final ErpOrderWebhookDedupeRepository dedupeRepo;
    private final Clock clock;

    public WebhookInboxPersistenceAdapter(ErpOrderWebhookInboxRepository inboxRepo,
                                          ErpOrderWebhookDedupeRepository dedupeRepo,
                                          Clock clock) {
        this.inboxRepo = inboxRepo;
        this.dedupeRepo = dedupeRepo;
        this.clock = clock;
    }

    /**
     * Ingest result. Exactly one of {@link Accepted} or {@link Duplicate} is
     * returned per call.
     */
    public sealed interface Result {

        record Accepted(Instant receivedAt) implements Result {
        }

        record Duplicate(Instant previouslyReceivedAt) implements Result {
        }

        static Result accepted(Instant receivedAt) {
            return new Accepted(receivedAt);
        }

        static Result duplicate(Instant previouslyReceivedAt) {
            return new Duplicate(previouslyReceivedAt);
        }
    }

    /**
     * Insert the dedupe row and (on first delivery) the inbox row in one TX.
     */
    @Transactional
    public Result ingest(String eventId, String rawPayload, String source) {
        Instant receivedAt = clock.instant();
        try {
            dedupeRepo.saveAndFlush(new ErpOrderWebhookDedupe(eventId, source, receivedAt));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("webhook_duplicate eventId={} source={}", eventId, source);
            Optional<ErpOrderWebhookDedupe> existing = dedupeRepo.findById(eventId);
            Instant previously = existing.map(ErpOrderWebhookDedupe::getReceivedAt).orElse(receivedAt);
            return Result.duplicate(previously);
        }
        ErpOrderWebhookInbox inboxRow = new ErpOrderWebhookInbox(
                UUID.randomUUID(), eventId, source, rawPayload, "PENDING", receivedAt);
        inboxRepo.save(inboxRow);
        log.info("webhook_accepted eventId={} source={}", eventId, source);
        return Result.accepted(receivedAt);
    }
}
