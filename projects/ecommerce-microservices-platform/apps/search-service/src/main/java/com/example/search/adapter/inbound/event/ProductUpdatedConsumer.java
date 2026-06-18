package com.example.search.adapter.inbound.event;

import com.example.search.application.port.in.IndexSyncUseCase;
import com.example.search.domain.model.SearchDocument;
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
public class ProductUpdatedConsumer {

    private final IndexSyncUseCase indexSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.product.updated", groupId = "search-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, ProductUpdatedEvent.class));
    }

    public void handle(ProductUpdatedEvent event) {
        if (event.payload() == null || event.payload().productId() == null || event.payload().name() == null) {
            log.warn("ProductUpdatedEvent with missing required fields, skipping. eventId={}", event.eventId());
            return;
        }

        var p = event.payload();
        // M5 propagation: extract tenant_id from event envelope; default 'ecommerce' when absent
        String tenantId = (event.tenantId() == null || event.tenantId().isBlank()) ? "ecommerce" : event.tenantId();

        SearchDocument document = SearchDocument.of(
                p.productId(),
                p.name(),
                p.description(),
                p.price(),
                p.status(),
                p.categoryId(),
                0,
                p.thumbnailUrl(),
                tenantId
        );
        indexSyncService.upsertPreservingStock(document);
    }
}
