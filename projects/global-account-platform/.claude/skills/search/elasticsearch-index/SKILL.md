---
name: elasticsearch-index
description: Elasticsearch index management
category: search
---

# Skill: Elasticsearch Index Management

Patterns for managing Elasticsearch indexes in the search service.

Prerequisite: read `platform/coding-rules.md` before using this skill.

---

## Index Adapter

The `ElasticsearchIndexAdapter` handles document upsert and delete operations.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexAdapter implements SearchIndexPort {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexProperties indexProperties;

    @Override
    public void upsert(SearchDocument document) {
        try {
            Map<String, Object> doc = Map.of(
                "productId", document.productId(),
                "name", document.name() != null ? document.name() : "",
                "price", document.price(),
                "status", document.status() != null ? document.status() : "",
                "categoryId", document.categoryId() != null ? document.categoryId() : "",
                "totalStock", document.totalStock()
            );
            elasticsearchClient.index(IndexRequest.of(i -> i
                .index(indexProperties.name())
                .id(document.productId())
                .document(doc)
            ));
        } catch (Exception e) {
            log.error("Failed to upsert document: productId={}", document.productId(), e);
            throw new SearchException("Failed to upsert search index", e);
        }
    }

    @Override
    public void delete(String productId) {
        try {
            elasticsearchClient.delete(DeleteRequest.of(d -> d
                .index(indexProperties.name())
                .id(productId)
            ));
        } catch (Exception e) {
            log.error("Failed to delete document: productId={}", productId, e);
            throw new SearchException("Failed to delete search index", e);
        }
    }
}
```

---

## Partial Update (Stock)

Update specific fields without replacing the entire document.

```java
@Override
public void updateStock(String productId, int totalStock, String status) {
    try {
        Map<String, Object> partial = Map.of(
            "totalStock", totalStock,
            "status", status
        );
        elasticsearchClient.update(UpdateRequest.of(u -> u
            .index(indexProperties.name())
            .id(productId)
            .docAsUpsert(true)
            .doc(partial)
        ), Map.class);
    } catch (Exception e) {
        log.error("Failed to update stock: productId={}", productId, e);
        throw new SearchException("Failed to update stock", e);
    }
}
```

---

## Document Schema

| Field | Type | Source |
|---|---|---|
| `productId` | keyword (doc ID) | ProductCreated event |
| `name` | text | ProductCreated/Updated event |
| `description` | text | ProductCreated/Updated event |
| `price` | long | ProductCreated/Updated event |
| `status` | keyword | ProductCreated/Updated/StockChanged event |
| `categoryId` | keyword | ProductCreated event |
| `totalStock` | integer | StockChanged event |

---

## Index Configuration

```yaml
# application.yml
elasticsearch:
  index:
    name: products
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Not using `docAsUpsert(true)` for partial updates | Document may not exist yet — upsert handles both cases |
| Null fields in document map | Default to empty string or 0 to avoid indexing errors |
| Missing error handling | Always catch and wrap in `SearchException` |
| Not using document ID as product ID | Use `productId` as the Elasticsearch document ID for idempotent upserts |
