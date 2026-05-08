package com.wms.notification.adapter.inbound.messaging;

import com.wms.notification.application.port.inbound.ProcessInboundEventUseCase;
import com.wms.notification.domain.alert.AlertEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Shared template for the 6 source-topic consumers. Each subclass binds a
 * concrete {@code @KafkaListener} that delegates to {@link #handle}, so
 * they all share envelope parsing, MDC enrichment, and the call-into-the
 * use case shape.
 *
 * <p>Per-class subclasses (instead of one generic listener) keep the
 * Spring Kafka container per topic — that lets us tune per-topic concurrency
 * later without code changes.
 */
public abstract class AbstractAlertConsumer {

    protected static final Logger log = LoggerFactory.getLogger(AbstractAlertConsumer.class);

    protected final AlertEnvelopeParser parser;
    protected final ProcessInboundEventUseCase processUseCase;

    protected AbstractAlertConsumer(AlertEnvelopeParser parser,
                                    ProcessInboundEventUseCase processUseCase) {
        this.parser = parser;
        this.processUseCase = processUseCase;
    }

    /** Subclass hook — return the topic name for diagnostics. */
    protected abstract String sourceTopic();

    /**
     * Parse the raw envelope and forward to the application service.
     * Exceptions bubble up so Spring Kafka's {@code DefaultErrorHandler}
     * can either retry transiently or route to DLT.
     */
    protected void handle(String rawJson) {
        AlertEnvelope envelope = parser.parse(rawJson, sourceTopic());
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("sourceTopic", sourceTopic());
        try {
            ProcessInboundEventUseCase.Outcome outcome = processUseCase.process(envelope);
            log.debug("Processed eventId={} from topic={} outcome={}",
                    envelope.eventId(), sourceTopic(), outcome);
        } finally {
            MDC.remove("eventId");
            MDC.remove("sourceTopic");
        }
    }
}
