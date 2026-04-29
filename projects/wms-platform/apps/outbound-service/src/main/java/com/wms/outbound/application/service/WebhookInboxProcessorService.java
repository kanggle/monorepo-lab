package com.wms.outbound.application.service;

import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookInbox;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookInboxRepository;
import com.wms.outbound.application.port.in.ProcessWebhookInboxUseCase;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background processor implementing {@link ProcessWebhookInboxUseCase}.
 *
 * <p><b>TASK-BE-034 stub:</b> the processor only flips PENDING rows to
 * APPLIED. It does NOT yet invoke {@code ReceiveOrderUseCase} or write any
 * Order. The real domain handling lands in TASK-BE-035 alongside the
 * use-case.
 *
 * <p>Each batch is bounded by {@code outbound.webhook.inbox.processor.batch-size}
 * (default 50). One TX wraps the entire batch — if any row's mutation throws
 * the whole batch rolls back; the next tick re-scans the still-PENDING rows.
 */
@Service
public class WebhookInboxProcessorService implements ProcessWebhookInboxUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookInboxProcessorService.class);

    private final ErpOrderWebhookInboxRepository inboxRepo;
    private final Clock clock;
    private final int batchSize;

    public WebhookInboxProcessorService(ErpOrderWebhookInboxRepository inboxRepo,
                                        Clock clock,
                                        @Value("${outbound.webhook.inbox.processor.batch-size:50}") int batchSize) {
        this.inboxRepo = inboxRepo;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    @Transactional
    public int processNextBatch() {
        List<ErpOrderWebhookInbox> pending = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(batchSize));
        if (pending.isEmpty()) {
            return 0;
        }
        for (ErpOrderWebhookInbox row : pending) {
            row.markApplied(clock.instant());
            log.info("webhook_inbox_processed eventId={} status=APPLIED orderId=null failureReason=null",
                    row.getEventId());
        }
        // JPA dirty-check flushes status updates at TX commit.
        return pending.size();
    }
}
