---
name: index-sync
description: Search index sync via events
category: search
---

# Skill: Index Sync

Patterns for synchronizing Elasticsearch indexes with source data via events.

Prerequisite: read `specs/platform/event-driven-policy.md` before using this skill.

---

## Event-Driven Sync Flow

```
Product Service                    Search Service
┌─────────────────┐               ┌──────────────────────┐
│ ProductCreated   │──── Kafka ──▶│ Upsert to ES index   │
│ ProductUpdated   │──── Kafka ──▶│ Upsert to ES index   │
│ ProductDeleted   │──── Kafka ──▶│ Delete from ES index  │
│ StockChanged     │──── Kafka ──▶│ Update stock in ES    │
└─────────────────┘               └──────────────────────┘
```

---

## Event Consumer → Index Update

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final SearchIndexPort searchIndexPort;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
        "product.product.created",
        "product.product.updated",
        "product.product.deleted",
        "product.product.stock-changed"
    }, groupId = "${spring.application.name}")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        ProductEvent event = objectMapper.readValue(payload, ProductEvent.class);

        switch (event.eventType()) {
            case "ProductCreated", "ProductUpdated" -> handleUpsert(event);
            case "ProductDeleted" -> handleDelete(event);
            case "StockChanged" -> handleStockChange(event);
            default -> log.warn("Unknown event type: {}", event.eventType());
        }
    }

    private void handleUpsert(ProductEvent event) {
        SearchDocument doc = SearchDocument.from(event.payload());
        searchIndexPort.upsert(doc);
    }

    private void handleDelete(ProductEvent event) {
        searchIndexPort.delete(event.payload().productId());
    }

    private void handleStockChange(ProductEvent event) {
        searchIndexPort.updateStock(
            event.payload().productId(),
            event.payload().totalStock(),
            event.payload().status()
        );
    }
}
```

---

## Consistency Model

- **Eventual consistency**: Search results may lag behind the source by seconds.
- Events are processed in order per partition (Kafka guarantees per-key ordering).
- Use `productId` as Kafka message key to ensure ordering per product.

---

## Stock Reset on Product Delete

When a product is deleted, the search index entry is removed entirely. If a `StockChanged` event arrives after deletion, `docAsUpsert(true)` will re-create the document. Guard against this by checking event ordering or skipping stock updates for deleted products.

---

## Testing Index Sync

```java
@Test
@DisplayName("ProductCreated 이벤트 수신 시 ES 인덱스에 문서가 생성된다")
void productCreated_indexesDocument() throws Exception {
    String eventJson = createProductCreatedEventJson("prod-1", "Test Product", 10000);

    consumer.onMessage(eventJson);

    verify(searchIndexPort).upsert(argThat(doc ->
        doc.productId().equals("prod-1") && doc.name().equals("Test Product")
    ));
}

@Test
@DisplayName("StockChanged 이벤트 수신 시 재고가 업데이트된다")
void stockChanged_updatesStock() throws Exception {
    String eventJson = createStockChangedEventJson("prod-1", 50, "ACTIVE");

    consumer.onMessage(eventJson);

    verify(searchIndexPort).updateStock("prod-1", 50, "ACTIVE");
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Stock update creates ghost document | Use `docAsUpsert(true)` carefully — may need to check if product exists |
| Events processed out of order | Use `productId` as Kafka key for per-product ordering |
| Consumer fails silently on unknown event type | Log a warning for unknown types |
| Missing consumer for new event type | Add handler when new product events are introduced |
