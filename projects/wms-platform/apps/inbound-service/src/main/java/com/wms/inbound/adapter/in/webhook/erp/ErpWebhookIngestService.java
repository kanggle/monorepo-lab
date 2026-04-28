package com.wms.inbound.adapter.in.webhook.erp;

import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookDedupeJpaEntity;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookDedupeJpaRepository;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically applies the dedupe + inbox writes for an ERP webhook delivery.
 *
 * <p>Per {@code specs/contracts/webhooks/erp-asn-webhook.md} § Replay Dedupe
 * the dedupe insert and inbox insert run inside a single {@code @Transactional}
 * boundary. Conflict on the dedupe row → no inbox write; the controller maps
 * this to a 200 {@code ignored_duplicate} response.
 *
 * <p>The duplicate is detected by primary-key violation on
 * {@code erp_webhook_dedupe.event_id} (the DB-level append-only trigger from V7
 * blocks any UPDATE/DELETE on the table — only INSERT is allowed).
 */
@Service
public class ErpWebhookIngestService {

    private static final Logger log = LoggerFactory.getLogger(ErpWebhookIngestService.class);

    private final ErpWebhookInboxJpaRepository inboxRepo;
    private final ErpWebhookDedupeJpaRepository dedupeRepo;
    private final Clock clock;

    public ErpWebhookIngestService(ErpWebhookInboxJpaRepository inboxRepo,
                                   ErpWebhookDedupeJpaRepository dedupeRepo,
                                   Clock clock) {
        this.inboxRepo = inboxRepo;
        this.dedupeRepo = dedupeRepo;
        this.clock = clock;
    }

    /**
     * Ingest result. Exactly one of {@link #accepted(Instant)} or
     * {@link #duplicate(Instant)} is returned per call.
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
    public Result ingest(String eventId, String rawPayload, String signature, String source) {
        Instant receivedAt = clock.instant();
        try {
            dedupeRepo.saveAndFlush(new ErpWebhookDedupeJpaEntity(eventId, receivedAt));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("webhook_duplicate eventId={} source={}", eventId, source);
            Optional<ErpWebhookDedupeJpaEntity> existing = dedupeRepo.findById(eventId);
            Instant previously = existing.map(ErpWebhookDedupeJpaEntity::getReceivedAt)
                    .orElse(receivedAt);
            return Result.duplicate(previously);
        }
        ErpWebhookInboxJpaEntity inboxRow = new ErpWebhookInboxJpaEntity(
                eventId, rawPayload, signature, source, receivedAt, "PENDING");
        inboxRepo.save(inboxRow);
        log.info("webhook_accepted eventId={} source={}", eventId, source);
        return Result.accepted(receivedAt);
    }
}
