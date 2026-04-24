package com.example.product.application.service;

import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublishingHelper {

    private final ProductEventPublisher productEventPublisher;

    public void publishSafely(ProductEvent event, String entityDescription, Object entityId) {
        try {
            productEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish {} event for {}: {}", event.eventType(), entityDescription, entityId, e);
        }
    }
}
