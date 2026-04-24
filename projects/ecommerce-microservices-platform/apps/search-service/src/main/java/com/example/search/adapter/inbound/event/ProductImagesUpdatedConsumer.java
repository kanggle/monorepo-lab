package com.example.search.adapter.inbound.event;

import com.example.search.application.port.in.IndexSyncUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImagesUpdatedConsumer {

    private final IndexSyncUseCase indexSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.product.images-updated", groupId = "search-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        try {
            handle(objectMapper.readValue(payload, ProductImagesUpdatedEvent.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ProductImagesUpdatedEvent: {}", payload, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process ProductImagesUpdatedEvent: {}", payload, e);
            throw e;
        }
    }

    public void handle(ProductImagesUpdatedEvent event) {
        if (event.payload() == null || event.payload().productId() == null) {
            log.warn("ProductImagesUpdatedEvent with missing required fields, skipping. eventId={}", event.eventId());
            return;
        }

        var p = event.payload();
        indexSyncService.updateThumbnailUrl(p.productId(), p.thumbnailUrl());
    }
}
