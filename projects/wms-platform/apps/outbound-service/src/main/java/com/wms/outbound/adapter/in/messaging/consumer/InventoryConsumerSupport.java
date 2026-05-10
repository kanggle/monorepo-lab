package com.wms.outbound.adapter.in.messaging.consumer;

import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.saga.SagaIdResolver;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Shared dispatch shell for {@code wms.inventory.*} saga consumers.
 *
 * <p>Consolidates the parse → MDC → dedupe → state-resolution skeleton that
 * was previously duplicated across {@link InventoryReservedConsumer},
 * {@link InventoryReleasedConsumer}, and {@link InventoryConfirmedConsumer}.
 *
 * <h2>Transactional boundary</h2>
 *
 * <p>This class is <strong>not</strong> annotated {@code @Transactional}.
 * The outer TX is opened by the concrete consumer's {@code @KafkaListener}
 * method. This support bean runs entirely inside that TX so the dedupe row,
 * the saga mutation, and any outbox writes commit atomically.
 *
 * <p>Marking a method here {@code @Transactional} would re-introduce the
 * Spring AOP self-invocation hazard that broke the admin-service Unit C
 * refactor (cf. PR #304 revert). Do not add it.
 */
@Component
public class InventoryConsumerSupport {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumerSupport.class);

    private final EventEnvelopeParser parser;
    private final EventDedupePort eventDedupePort;
    private final SagaIdResolver sagaIdResolver;

    public InventoryConsumerSupport(EventEnvelopeParser parser,
                                    EventDedupePort eventDedupePort,
                                    SagaIdResolver sagaIdResolver) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.sagaIdResolver = sagaIdResolver;
    }

    /**
     * Parse the raw payload, set MDC, run the dedupe-protected handler.
     *
     * <p>The handler is invoked with the resolved {@code sagaId}; if no
     * correlation keys are present in the payload, the handler is skipped
     * and a warning is logged. The handler runs synchronously inside the
     * outer {@code @Transactional} boundary opened by the concrete consumer.
     *
     * @param consumerName     short name used in MDC and log lines (e.g.
     *                         {@code "inventory-reserved"})
     * @param eventTypeForLog  short event-type label used in skip log lines
     *                         (e.g. {@code "inventory.reserved"})
     * @param rawJson          the raw Kafka record value
     * @param onSagaId         the saga-coordinator delegate to invoke once
     *                         the {@code sagaId} is resolved
     */
    public void dispatch(String consumerName,
                         String eventTypeForLog,
                         String rawJson,
                         Consumer<UUID> onSagaId) {
        EventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", consumerName);
        try {
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> applyEvent(envelope, eventTypeForLog, onSagaId));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("{} eventId={} already applied; skipping",
                        eventTypeForLog, envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(EventEnvelope envelope,
                            String eventTypeForLog,
                            Consumer<UUID> onSagaId) {
        UUID sagaId = sagaIdResolver.resolve(envelope.payload());
        if (sagaId == null) {
            log.warn("{} without correlation keys; skipping payload={}",
                    eventTypeForLog, envelope.payload());
            return;
        }
        onSagaId.accept(sagaId);
    }
}
