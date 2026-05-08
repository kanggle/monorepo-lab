package com.wms.notification.adapter.inbound.messaging;

import com.wms.notification.application.port.inbound.ProcessInboundEventUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumes {@code wms.inbound.inspection.completed.v1}. */
@Component
@Profile("!standalone")
public class InboundInspectionCompletedConsumer extends AbstractAlertConsumer {

    private static final String TOPIC = "wms.inbound.inspection.completed.v1";

    public InboundInspectionCompletedConsumer(AlertEnvelopeParser parser,
                                              ProcessInboundEventUseCase processUseCase) {
        super(parser, processUseCase);
    }

    @Override
    protected String sourceTopic() {
        return TOPIC;
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.inbound-inspection-completed:" + TOPIC + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson) {
        handle(rawJson);
    }
}
