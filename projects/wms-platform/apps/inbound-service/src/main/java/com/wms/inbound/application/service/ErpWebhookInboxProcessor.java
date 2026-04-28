package com.wms.inbound.application.service;

import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background processor that walks the {@code erp_webhook_inbox} for
 * {@code PENDING} rows and marks each {@code APPLIED}.
 *
 * <p><b>TASK-BE-029 stub behaviour:</b> the processor only flips the row's
 * status — it does NOT yet invoke {@code ReceiveAsnUseCase} or write any ASN.
 * The real domain handling lands in TASK-BE-030 alongside the use-case.
 *
 * <p>Runs by default; tests disable via
 * {@code inbound.webhook.inbox.processor.enabled=false} and trigger via
 * {@link #processBatch()} directly.
 *
 * <p>Each batch is bounded by {@code inbound.webhook.inbox.processor.batch-size}
 * (default 50). One TX wraps the entire batch — if any row's mutation throws
 * the whole batch rolls back; the next tick re-scans the still-PENDING rows.
 */
@Component
@ConditionalOnProperty(name = "inbound.webhook.inbox.processor.enabled",
        havingValue = "true", matchIfMissing = true)
public class ErpWebhookInboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(ErpWebhookInboxProcessor.class);

    private final ErpWebhookInboxJpaRepository inboxRepo;
    private final Clock clock;
    private final int batchSize;

    public ErpWebhookInboxProcessor(ErpWebhookInboxJpaRepository inboxRepo,
                                    Clock clock,
                                    @Value("${inbound.webhook.inbox.processor.batch-size:50}") int batchSize) {
        this.inboxRepo = inboxRepo;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${inbound.webhook.inbox.processor.fixed-delay-ms:1000}")
    public void tick() {
        try {
            int processed = processBatch();
            if (processed > 0 && log.isDebugEnabled()) {
                log.debug("webhook_inbox_processor batch_size={}", processed);
            }
        } catch (Exception e) {
            // Never let the scheduler thread die. The next tick re-scans.
            log.error("webhook_inbox_processor batch failed", e);
        }
    }

    /**
     * Process up to {@link #batchSize} pending rows. Marked {@code APPLIED}
     * with no further side-effects in TASK-BE-029. Visible for tests.
     *
     * @return number of rows processed in this invocation
     */
    @Transactional
    public int processBatch() {
        List<ErpWebhookInboxJpaEntity> pending = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(batchSize));
        if (pending.isEmpty()) {
            return 0;
        }
        for (ErpWebhookInboxJpaEntity row : pending) {
            row.markApplied(clock.instant());
            log.info("webhook_inbox_processed eventId={} status=APPLIED asnId=null failureReason=null",
                    row.getEventId());
        }
        // JPA dirty-check flushes status updates at TX commit.
        return pending.size();
    }
}
