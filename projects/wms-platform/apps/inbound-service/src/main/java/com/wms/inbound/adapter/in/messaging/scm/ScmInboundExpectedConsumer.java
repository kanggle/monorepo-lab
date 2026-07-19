package com.wms.inbound.adapter.in.messaging.scm;

import com.wms.inbound.application.port.in.CancelScmInboundExpectationUseCase;
import com.wms.inbound.application.port.in.CreateScmInboundExpectationUseCase;
import com.wms.inbound.application.port.out.EventDedupePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-MONO-050 — the first {@code scm → wms} consumer. Subscribes to scm procurement's
 * inbound-expected topic family and turns a confirmed PO into a wms inbound expectation (ASN),
 * plus a companion cancel handler.
 *
 * <p>Runs under the <strong>independent</strong> consumer group
 * {@code wms-inbound-scm-expected-v1} (separate offsets from the {@code wms.master.*}
 * projection consumers). {@code @Transactional} on each listener method is the proxy boundary
 * (matching {@code MasterEventConsumer}); {@link EventDedupePort#process} runs the work inside
 * that transaction so the dedupe row + domain writes + outbox row commit or roll back together.
 *
 * <p>Idempotency is two-layer (D6): envelope {@code eventId} dedup here, plus {@code (poNumber,
 * line)} open-expectation business dedup inside the use-case.
 */
@Component
@Profile("!standalone")
public class ScmInboundExpectedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScmInboundExpectedConsumer.class);

    private final ScmInboundExpectedEventParser parser;
    private final EventDedupePort dedupe;
    private final CreateScmInboundExpectationUseCase createUseCase;
    private final CancelScmInboundExpectationUseCase cancelUseCase;

    public ScmInboundExpectedConsumer(ScmInboundExpectedEventParser parser,
                                      EventDedupePort dedupe,
                                      CreateScmInboundExpectationUseCase createUseCase,
                                      CancelScmInboundExpectationUseCase cancelUseCase) {
        this.parser = parser;
        this.dedupe = dedupe;
        this.createUseCase = createUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @KafkaListener(
            topics = "${inbound.kafka.topics.scm-inbound-expected:scm.procurement.inbound-expected.v1}",
            groupId = "${inbound.kafka.scm-expected.group-id:wms-inbound-scm-expected-v1}"
    )
    @Transactional
    public void onInboundExpected(@Payload String rawJson) {
        ScmInboundExpectedEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "scm-inbound-expected");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> createUseCase.create(parser.toCreateCommand(envelope.body())));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("scm inbound-expected event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    @KafkaListener(
            topics = "${inbound.kafka.topics.scm-inbound-expected-cancelled:scm.procurement.inbound-expected.cancelled.v1}",
            groupId = "${inbound.kafka.scm-expected.group-id:wms-inbound-scm-expected-v1}"
    )
    @Transactional
    public void onInboundExpectedCancelled(@Payload String rawJson) {
        ScmInboundExpectedEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "scm-inbound-expected-cancelled");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> cancelUseCase.cancel(parser.toCancelCommand(envelope.body())));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("scm inbound-expected-cancelled event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }
}
