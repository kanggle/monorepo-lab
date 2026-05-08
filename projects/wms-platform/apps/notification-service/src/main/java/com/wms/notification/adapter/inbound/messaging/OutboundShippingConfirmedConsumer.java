package com.wms.notification.adapter.inbound.messaging;

import com.wms.notification.application.port.inbound.ProcessInboundEventUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumes {@code wms.outbound.shipping.confirmed.v1}. */
@Component
@Profile("!standalone")
public class OutboundShippingConfirmedConsumer extends AbstractAlertConsumer {

    private static final String TOPIC = "wms.outbound.shipping.confirmed.v1";

    public OutboundShippingConfirmedConsumer(AlertEnvelopeParser parser,
                                             ProcessInboundEventUseCase processUseCase) {
        super(parser, processUseCase);
    }

    @Override
    protected String sourceTopic() {
        return TOPIC;
    }

    @KafkaListener(
            topics = "${wms.kafka.topics.outbound-shipping-confirmed:" + TOPIC + "}",
            groupId = "${spring.kafka.consumer.group-id:wms-notification-v1}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson) {
        handle(rawJson);
    }
}
