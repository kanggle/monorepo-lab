package com.example.product.infrastructure.event;

import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.infrastructure.metrics.ProductMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class KafkaProductEventPublisher implements ProductEventPublisher {

    static final String TOPIC_PRODUCT_CREATED = "product.product.created";
    static final String TOPIC_PRODUCT_UPDATED = "product.product.updated";
    static final String TOPIC_PRODUCT_DELETED = "product.product.deleted";
    static final String TOPIC_STOCK_CHANGED = "product.product.stock-changed";
    static final String TOPIC_PRODUCT_IMAGES_UPDATED = "product.product.images-updated";
    static final String TOPIC_RESERVATION_FAILED = "product.product.reservation-failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProductMetrics productMetrics;

    @Override
    public void publish(ProductEvent event) {
        String topic = switch (event.eventType()) {
            case "ProductCreated" -> TOPIC_PRODUCT_CREATED;
            case "ProductUpdated" -> TOPIC_PRODUCT_UPDATED;
            case "ProductDeleted" -> TOPIC_PRODUCT_DELETED;
            case "StockChanged" -> TOPIC_STOCK_CHANGED;
            case "ProductImagesUpdated" -> TOPIC_PRODUCT_IMAGES_UPDATED;
            case "OrderReservationFailed" -> TOPIC_RESERVATION_FAILED;
            default -> throw new IllegalArgumentException("Unknown event type: " + event.eventType());
        };

        String key = event.eventId().toString();
        try {
            kafkaTemplate.send(topic, key, event);
            log.debug("Published event to Kafka: topic={}, eventType={}, key={}", topic, event.eventType(), key);
        } catch (Exception e) {
            log.error("Event publishing failed: eventType={}, topic={}, key={}", event.eventType(), topic, key, e);
            productMetrics.incrementEventPublishFailure(event.eventType());
        }
    }
}
