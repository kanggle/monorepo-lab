package com.example.product.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductEvent 직렬화 테스트")
class ProductEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다 - ProductCreated")
    void serialize_productCreated_envelopeFieldsAreSnakeCase() throws Exception {
        ProductCreatedPayload payload = new ProductCreatedPayload(
                "prod-1", "테스트 상품", "설명", 10000L, "ON_SALE", "cat-1", null, "default", null
        );
        ProductEvent event = ProductEvent.created(payload);

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"event_id\"");
        assertThat(json).contains("\"event_type\"");
        assertThat(json).contains("\"occurred_at\"");
        assertThat(json).doesNotContain("\"eventId\"");
        assertThat(json).doesNotContain("\"eventType\"");
        assertThat(json).doesNotContain("\"occurredAt\"");
    }

    @Test
    @DisplayName("envelope 필드가 snake_case로 직렬화된다 - StockChanged")
    void serialize_stockChanged_envelopeFieldsAreSnakeCase() throws Exception {
        StockChangedPayload payload = new StockChangedPayload(
                "prod-1", "var-1", 100, 150, 50, "RESTOCK", null
        );
        ProductEvent event = ProductEvent.stockChanged(payload);

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"event_id\"");
        assertThat(json).contains("\"event_type\"");
        assertThat(json).contains("\"occurred_at\"");
        assertThat(json).doesNotContain("\"eventId\"");
        assertThat(json).doesNotContain("\"eventType\"");
        assertThat(json).doesNotContain("\"occurredAt\"");
    }
}
