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
public class ProductDeletedConsumer {

    private final IndexSyncUseCase indexSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.product.deleted", groupId = "search-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, ProductDeletedEvent.class));
    }

    public void handle(ProductDeletedEvent event) {
        if (event.payload() == null || event.payload().productId() == null) {
            log.warn("ProductDeletedEvent with missing productId, skipping. eventId={}", event.eventId());
            return;
        }

        indexSyncService.delete(event.payload().productId());
    }
}
