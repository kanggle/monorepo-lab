package com.example.promotion.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxPollingScheduler extends com.example.messaging.outbox.OutboxPollingScheduler {

    static final String TOPIC_COUPON_USED = "promotion.coupon.used";
    static final String TOPIC_COUPON_EXPIRED = "promotion.coupon.expired";

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "CouponUsed" -> TOPIC_COUPON_USED;
            case "CouponExpired" -> TOPIC_COUPON_EXPIRED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
