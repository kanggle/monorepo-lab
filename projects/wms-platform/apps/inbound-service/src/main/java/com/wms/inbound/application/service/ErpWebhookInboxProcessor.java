package com.wms.inbound.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.webhook.erp.dto.ErpAsnWebhookRequest;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.webhook.ErpWebhookInboxJpaRepository;
import com.wms.inbound.application.command.ReceiveAsnCommand;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background processor that walks the {@code erp_webhook_inbox} for
 * {@code PENDING} rows, deserializes each payload, calls {@link ReceiveAsnUseCase},
 * and marks the row {@code APPLIED} on success or {@code FAILED} on error.
 *
 * <p>Each row is processed independently: {@link #processOneRow} is not
 * transactional, so {@code ReceiveAsnUseCase} (which is {@code @Transactional})
 * opens and commits its own TX. The inbox status update is done via
 * {@link #updateStatus} which uses {@code @Transactional} through the self-proxy
 * ({@code @Lazy} injection to break the circular reference).
 */
@Component
@ConditionalOnProperty(name = "inbound.webhook.inbox.processor.enabled",
        havingValue = "true", matchIfMissing = true)
public class ErpWebhookInboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(ErpWebhookInboxProcessor.class);
    private static final String SYSTEM_ACTOR = "system:erp-webhook";
    private static final int MAX_REASON_LEN = 500;

    private final ErpWebhookInboxJpaRepository inboxRepo;
    private final ReceiveAsnUseCase receiveAsnUseCase;
    private final MasterReadModelPort masterReadModel;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final ErpWebhookInboxProcessor self;

    public ErpWebhookInboxProcessor(ErpWebhookInboxJpaRepository inboxRepo,
                                    ReceiveAsnUseCase receiveAsnUseCase,
                                    MasterReadModelPort masterReadModel,
                                    ObjectMapper objectMapper,
                                    Clock clock,
                                    @Value("${inbound.webhook.inbox.processor.batch-size:50}") int batchSize,
                                    @Lazy ErpWebhookInboxProcessor self) {
        this.inboxRepo = inboxRepo;
        this.receiveAsnUseCase = receiveAsnUseCase;
        this.masterReadModel = masterReadModel;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${inbound.webhook.inbox.processor.fixed-delay-ms:1000}")
    public void tick() {
        try {
            int processed = processBatch();
            if (processed > 0 && log.isDebugEnabled()) {
                log.debug("webhook_inbox_processor batch_size={}", processed);
            }
        } catch (Exception e) {
            log.error("webhook_inbox_processor batch failed", e);
        }
    }

    public int processBatch() {
        List<ErpWebhookInboxJpaEntity> pending = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(batchSize));
        if (pending.isEmpty()) {
            return 0;
        }
        for (ErpWebhookInboxJpaEntity row : pending) {
            processOneRow(row);
        }
        return pending.size();
    }

    void processOneRow(ErpWebhookInboxJpaEntity row) {
        String eventId = row.getEventId();
        try {
            ErpAsnWebhookRequest req = objectMapper.readValue(row.getRawPayload(), ErpAsnWebhookRequest.class);
            ReceiveAsnCommand command = toCommand(req, row.getSource());
            receiveAsnUseCase.receive(command);
            self.updateStatus(eventId, true, null);
            log.info("webhook_inbox_applied eventId={}", eventId);
        } catch (Exception e) {
            String reason = truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            self.updateStatus(eventId, false, reason);
            log.warn("webhook_inbox_failed eventId={} reason={}", eventId, reason);
        }
    }

    @Transactional
    public void updateStatus(String eventId, boolean applied, String failureReason) {
        inboxRepo.findById(eventId).ifPresent(row -> {
            if (applied) {
                row.markApplied(clock.instant());
            } else {
                row.markFailed(clock.instant(), failureReason);
            }
        });
    }

    private ReceiveAsnCommand toCommand(ErpAsnWebhookRequest req, String source) {
        UUID warehouseId = masterReadModel.findWarehouseByCode(req.warehouseCode())
                .map(w -> w.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse not found in read model: code=" + req.warehouseCode()));

        UUID partnerId = masterReadModel.findPartnerByCode(req.supplierPartnerCode())
                .map(p -> p.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partner not found in read model: code=" + req.supplierPartnerCode()));

        List<ReceiveAsnCommand.Line> lines = req.lines().stream()
                .map(l -> {
                    UUID skuId = masterReadModel.findSkuByCode(l.skuCode())
                            .map(s -> s.id())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "SKU not found in read model: code=" + l.skuCode()));
                    UUID lotId = null;
                    if (l.lotNo() != null && !l.lotNo().isBlank()) {
                        lotId = masterReadModel.findLotBySkuAndLotNo(skuId, l.lotNo())
                                .map(lot -> lot.id())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Lot not found in read model: sku=" + l.skuCode()
                                        + " lotNo=" + l.lotNo()));
                    }
                    return new ReceiveAsnCommand.Line(skuId, lotId, l.expectedQty());
                })
                .toList();

        return new ReceiveAsnCommand(
                req.asnNo(),
                "WEBHOOK_ERP",
                partnerId,
                warehouseId,
                req.expectedArriveDate(),
                req.notes(),
                lines,
                SYSTEM_ACTOR,
                Set.of()
        );
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_REASON_LEN ? s : s.substring(0, MAX_REASON_LEN);
    }
}
