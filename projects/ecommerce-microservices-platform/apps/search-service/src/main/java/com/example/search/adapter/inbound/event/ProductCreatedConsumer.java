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
public class ProductCreatedConsumer {

    private final IndexSyncUseCase indexSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.product.created", groupId = "search-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, ProductCreatedEvent.class));
    }

    public void handle(ProductCreatedEvent event) {
        if (event.payload() == null || event.payload().productId() == null || event.payload().name() == null) {
            log.warn("ProductCreatedEvent with missing required fields, skipping. eventId={}", event.eventId());
            return;
        }

        var p = event.payload();
        int totalStock = p.variants() == null ? 0
                : p.variants().stream().mapToInt(ProductCreatedEvent.VariantPayload::stock).sum();

        SearchDocument document = SearchDocument.of(
                p.productId(),
                p.name(),
                p.description(),
                p.price(),
                p.status(),
                p.categoryId(),
                totalStock,
                p.thumbnailUrl()
        );
        indexSyncService.upsert(document);
    }
}
