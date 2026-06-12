package com.example.product.contract;

import com.example.product.domain.event.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.example.product.contract.ContractTestHelper.assertFieldsMatch;

/**
 * product-service 이벤트 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/events/product-events.md
 */
@DisplayName("Product Event 컨트랙트 테스트 — specs/contracts/events/product-events.md")
class ProductEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String SPEC_REF = "specs/contracts/events/product-events.md";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("event_id", "event_type", "occurred_at", "source", "tenant_id", "payload");

    // ─── ProductCreated ─────────────────────────────────────────────────

    @Test
    @DisplayName("ProductCreated envelope은 스펙 정의 필드만 포함한다")
    void productCreated_envelope_matchesSpec() throws Exception {
        ProductCreatedPayload payload = new ProductCreatedPayload(
                "prod-1", "노트북", "설명", 1000000L, "ON_SALE", "cat-1",
                "https://example.com/thumb.jpg",
                List.of(new ProductCreatedPayload.VariantPayload("var-1", "기본", 100, 0L)));
        ProductEvent event = ProductEvent.created(payload);

        assertFieldsMatch(objectMapper.writeValueAsString(event), ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("ProductCreated payload는 스펙 정의 필드만 포함한다")
    void productCreated_payload_matchesSpec() throws Exception {
        ProductCreatedPayload payload = new ProductCreatedPayload(
                "prod-1", "노트북", "설명", 1000000L, "ON_SALE", "cat-1",
                "https://example.com/thumb.jpg",
                List.of(new ProductCreatedPayload.VariantPayload("var-1", "기본", 100, 0L)));
        ProductEvent event = ProductEvent.created(payload);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(event));
        JsonNode payloadNode = root.get("payload");

        assertFieldsMatch(payloadNode, Set.of("productId", "name", "description", "price", "status", "categoryId", "thumbnailUrl", "variants"),
                SPEC_REF + " ProductCreated payload");

        JsonNode variant = payloadNode.get("variants").get(0);
        assertFieldsMatch(variant, Set.of("variantId", "optionName", "stock", "additionalPrice"),
                SPEC_REF + " ProductCreated payload variants[]");
    }

    // ─── ProductUpdated ─────────────────────────────────────────────────

    @Test
    @DisplayName("ProductUpdated payload는 {productId, name, description, price, status, categoryId, thumbnailUrl}만 포함한다")
    void productUpdated_payload_matchesSpec() throws Exception {
        ProductUpdatedPayload payload = new ProductUpdatedPayload("prod-1", "노트북 v2", "새 설명", 1200000L, "ON_SALE", "cat-1", "https://example.com/thumb.jpg");
        ProductEvent event = ProductEvent.updated(payload);

        JsonNode payloadNode = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payloadNode, Set.of("productId", "name", "description", "price", "status", "categoryId", "thumbnailUrl"),
                SPEC_REF + " ProductUpdated payload");
    }

    // ─── ProductDeleted ─────────────────────────────────────────────────

    @Test
    @DisplayName("ProductDeleted payload는 {productId}만 포함한다")
    void productDeleted_payload_matchesSpec() throws Exception {
        ProductDeletedPayload payload = new ProductDeletedPayload("prod-1");
        ProductEvent event = ProductEvent.deleted(payload);

        JsonNode payloadNode = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payloadNode, Set.of("productId"), SPEC_REF + " ProductDeleted payload");
    }

    // ─── StockChanged ───────────────────────────────────────────────────

    @Test
    @DisplayName("StockChanged payload는 {productId, variantId, previousStock, currentStock, delta, reason, orderId}만 포함한다")
    void stockChanged_payload_matchesSpec() throws Exception {
        StockChangedPayload payload = new StockChangedPayload("prod-1", "var-1", 100, 150, 50, "RESTOCK", null);
        ProductEvent event = ProductEvent.stockChanged(payload);

        JsonNode payloadNode = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payloadNode, Set.of("productId", "variantId", "previousStock", "currentStock", "delta", "reason", "orderId"),
                SPEC_REF + " StockChanged payload");
    }
}
