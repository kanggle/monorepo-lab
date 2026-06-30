package com.example.settlement.infrastructure.event;

import com.example.settlement.application.service.RecordOrderSnapshotCommand;
import com.example.settlement.application.service.SettlementService;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.repository.ProcessedEventStore;
import com.example.settlement.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consumes {@code order.order.placed} → caches the line snapshot
 * ({@code order_id → [{seller_id, gross_minor}]} + envelope {@code tenant_id}),
 * idempotent on {@code order_id} (AC-2). No accrual yet — money not captured.
 *
 * <p>The envelope {@code tenant_id} is the only authoritative tenant source for
 * settlement (AC-7); it is persisted on the snapshot for the later accrual join.
 * Absent items / blank seller default to the {@code default} seller (D8 net-zero).
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class OrderPlacedSnapshotConsumer {

    private static final String DEFAULT_SELLER_ID = "default";

    private final SettlementService settlementService;
    private final ProcessedEventStore processedEventStore;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.placed", groupId = "settlement-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderPlacedEvent.class));
    }

    // @Transactional so handle()'s MANDATORY processed-event dedupe + snapshot write are
    // atomic when handle() is invoked directly through the proxy (the IT drives handle()
    // without the @KafkaListener onMessage tx boundary). A no-op in prod, where onMessage
    // self-invokes handle() so this proxy advice is bypassed and onMessage's tx remains the
    // single boundary — behaviour unchanged (TASK-BE-461).
    @Transactional
    public void handle(OrderPlacedEvent event) {
        if (processedEventStore.isDuplicate(event.eventId(), "OrderPlaced")) {
            return;
        }
        if (event.payload() == null || EventFieldParser.isBlank(event.payload().orderId())) {
            log.warn("OrderPlaced has no orderId — skipping. eventId={}", event.eventId());
            return;
        }

        String tenantId = EventFieldParser.isBlank(event.tenantId())
                ? TenantContext.DEFAULT_TENANT_ID
                : event.tenantId();

        List<OrderPlacedEvent.Item> items = event.payload().items() == null
                ? List.of() : event.payload().items();
        List<OrderSnapshotLine> lines = items.stream()
                .map(i -> new OrderSnapshotLine(
                        EventFieldParser.isBlank(i.sellerId()) ? DEFAULT_SELLER_ID : i.sellerId(),
                        i.unitPrice() * i.quantity()))
                .toList();

        settlementService.recordSnapshot(
                new RecordOrderSnapshotCommand(event.payload().orderId(), tenantId, lines));
    }
}
