package com.wms.outbound.adapter.in.messaging.consumer;

import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.confirmed.v1} and advances the saga
 * {@code SHIPPED → COMPLETED}.
 *
 * <p>The {@code SHIPPED} state itself is set by
 * {@code ConfirmShippingUseCase} (TASK-BE-038); this consumer only handles
 * the final saga-completion step. Parse + dedupe + MDC scaffolding lives
 * in {@link InventoryConsumerSupport}.
 */
@Component
@Profile("!standalone")
public class InventoryConfirmedConsumer {

    private final InventoryConsumerSupport support;
    private final OutboundSagaCoordinator coordinator;

    public InventoryConfirmedConsumer(InventoryConsumerSupport support,
                                      OutboundSagaCoordinator coordinator) {
        this.support = support;
        this.coordinator = coordinator;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-confirmed:wms.inventory.confirmed.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        support.dispatch("inventory-confirmed", "inventory.confirmed", rawJson,
                coordinator::onInventoryConfirmed);
    }
}
