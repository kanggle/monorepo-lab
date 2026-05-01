package com.wms.outbound.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.webhook.erp.dto.ErpOrderLineRequest;
import com.wms.outbound.adapter.in.webhook.erp.dto.ErpOrderWebhookRequest;
import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookInbox;
import com.wms.outbound.adapter.out.persistence.repository.ErpOrderWebhookInboxRepository;
import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderLineCommand;
import com.wms.outbound.application.port.in.ProcessWebhookInboxUseCase;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.exception.PartnerInvalidTypeException;
import com.wms.outbound.domain.exception.SkuInactiveException;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background processor implementing {@link ProcessWebhookInboxUseCase}.
 *
 * <p>Walks the {@code erp_order_webhook_inbox} for {@code PENDING} rows,
 * deserializes each payload, calls {@link ReceiveOrderUseCase}, and marks
 * the row {@code APPLIED} on success or {@code FAILED} on error.
 *
 * <p>Per-row processing — {@code ReceiveOrderUseCase.receive()} is itself
 * {@code @Transactional} and opens its own TX; the inbox row's status flip
 * runs in a separate TX through the {@code @Lazy} self-proxy. This way one
 * row's failure does not block the rest of the batch.
 *
 * <p>{@link OrderNoDuplicateException} is treated as success — re-delivery
 * of the same ERP event is idempotent at the {@code orderNo} unique
 * constraint.
 */
@Service
public class WebhookInboxProcessorService implements ProcessWebhookInboxUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookInboxProcessorService.class);
    private static final String SYSTEM_ACTOR = "system:erp-webhook";
    private static final int MAX_REASON_LEN = 500;

    private final ErpOrderWebhookInboxRepository inboxRepo;
    private final ReceiveOrderUseCase receiveOrderUseCase;
    private final MasterReadModelPort masterReadModel;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final WebhookInboxProcessorService self;

    public WebhookInboxProcessorService(ErpOrderWebhookInboxRepository inboxRepo,
                                        ReceiveOrderUseCase receiveOrderUseCase,
                                        MasterReadModelPort masterReadModel,
                                        ObjectMapper objectMapper,
                                        Clock clock,
                                        @Value("${outbound.webhook.inbox.processor.batch-size:50}") int batchSize,
                                        @Lazy WebhookInboxProcessorService self) {
        this.inboxRepo = inboxRepo;
        this.receiveOrderUseCase = receiveOrderUseCase;
        this.masterReadModel = masterReadModel;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.self = self;
    }

    @Override
    public int processNextBatch() {
        List<ErpOrderWebhookInbox> pending = inboxRepo
                .findAllByStatusOrderByReceivedAtAsc("PENDING", Limit.of(batchSize));
        if (pending.isEmpty()) {
            return 0;
        }
        for (ErpOrderWebhookInbox row : pending) {
            processOneRow(row);
        }
        return pending.size();
    }

    void processOneRow(ErpOrderWebhookInbox row) {
        UUID rowId = row.getId();
        String eventId = row.getEventId();
        try {
            ErpOrderWebhookRequest req = objectMapper.readValue(row.getPayload(),
                    ErpOrderWebhookRequest.class);
            try {
                receiveOrderUseCase.receive(toCommand(req));
            } catch (OrderNoDuplicateException duplicate) {
                // Idempotent re-delivery — mark APPLIED and move on.
                log.info("webhook_inbox_duplicate eventId={} orderNo={} -> APPLIED",
                        eventId, req.orderNo());
            }
            self.markApplied(rowId);
            log.info("webhook_inbox_applied eventId={}", eventId);
        } catch (Exception e) {
            String reason = truncate(e.getMessage() != null
                    ? e.getMessage() : e.getClass().getSimpleName());
            self.markFailed(rowId, reason);
            log.warn("webhook_inbox_failed eventId={} reason={}", eventId, reason);
        }
    }

    @Transactional
    public void markApplied(UUID rowId) {
        inboxRepo.findById(rowId).ifPresent(row -> row.markApplied(clock.instant()));
    }

    @Transactional
    public void markFailed(UUID rowId, String reason) {
        inboxRepo.findById(rowId).ifPresent(row -> row.markFailed(clock.instant()));
    }

    private ReceiveOrderCommand toCommand(ErpOrderWebhookRequest req) {
        WarehouseSnapshot warehouse = masterReadModel.findWarehouseByCode(req.warehouseCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse not found in read model: code=" + req.warehouseCode()));

        PartnerSnapshot partner = masterReadModel.findPartnerByCode(req.customerPartnerCode())
                .orElseThrow(() -> new PartnerInvalidTypeException(
                        warehouse.id() /* placeholder — actual id unknown */,
                        "partner not found in read model: code=" + req.customerPartnerCode()));

        List<ReceiveOrderLineCommand> lines = req.lines().stream()
                .map(l -> toLineCommand(l))
                .toList();

        return new ReceiveOrderCommand(
                req.orderNo(),
                "WEBHOOK_ERP",
                partner.id(),
                warehouse.id(),
                req.requiredShipDate(),
                req.notes(),
                lines,
                SYSTEM_ACTOR,
                Set.of());
    }

    private ReceiveOrderLineCommand toLineCommand(ErpOrderLineRequest l) {
        SkuSnapshot sku = masterReadModel.findSkuByCode(l.skuCode())
                .orElseThrow(() -> new SkuInactiveException(null));
        UUID lotId = null;
        if (l.lotNo() != null && !l.lotNo().isBlank()) {
            LotSnapshot lot = masterReadModel.findLotBySkuAndLotNo(sku.id(), l.lotNo())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Lot not found in read model: sku=" + l.skuCode()
                                    + " lotNo=" + l.lotNo()));
            lotId = lot.id();
        }
        return new ReceiveOrderLineCommand(l.lineNo(), sku.id(), lotId, l.qtyOrdered());
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_REASON_LEN ? s : s.substring(0, MAX_REASON_LEN);
    }
}
