package com.wms.outbound.adapter.in.webhook.erp;

import com.wms.outbound.application.port.in.ProcessWebhookInboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled wrapper that ticks the
 * {@link ProcessWebhookInboxUseCase#processNextBatch()} method every 1 s.
 *
 * <p>Disabled in {@code test} profile so unit/integration tests trigger
 * processing directly via the use-case method.
 *
 * <p>Per AC-14: each tick is best-effort — exceptions are caught here so the
 * scheduler thread never dies.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "outbound.webhook.inbox.processor.enabled",
        havingValue = "true", matchIfMissing = true)
public class ErpOrderWebhookInboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(ErpOrderWebhookInboxProcessor.class);

    private final ProcessWebhookInboxUseCase useCase;

    public ErpOrderWebhookInboxProcessor(ProcessWebhookInboxUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${outbound.webhook.inbox.processor.fixed-delay-ms:1000}")
    public void tick() {
        try {
            int processed = useCase.processNextBatch();
            if (processed > 0 && log.isDebugEnabled()) {
                log.debug("webhook_inbox_processor batch_size={}", processed);
            }
        } catch (Exception e) {
            // Never let the scheduler thread die. The next tick re-scans.
            log.error("webhook_inbox_processor batch failed", e);
        }
    }
}
