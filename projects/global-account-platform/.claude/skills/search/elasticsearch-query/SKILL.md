---
name: elasticsearch-query
description: Elasticsearch query building
category: search
---

# Skill: Elasticsearch Query

Patterns for building search queries in the search service.

Prerequisite: read `platform/coding-rules.md` before using this skill.

---

## Query Adapter

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchQueryAdapter implements SearchQueryPort {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexProperties indexProperties;

    @Override
    public SearchProductResult search(SearchProductQuery query) {
        try {
            int from = query.page() * query.size();

            SearchRequest request = SearchRequest.of(s -> s
                .index(indexProperties.name())
                .query(buildQuery(query))
                .from(from)
                .size(query.size())
                .sort(buildSort(query.sort()))
                .aggregations("categories", a -> a
                    .terms(t -> t.field("categoryId").size(20))
                )
            );

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            return toResult(response);
        } catch (Exception e) {
            log.error("Search failed", e);
            throw new SearchException("Search failed", e);
        }
    }
}
```

---

## Building Queries

### Multi-Match (Keyword Search)

Search across multiple text fields:

```java
builder.must(m -> m.multiMatch(mm -> mm
    .query(filter.keyword())
    .fields("name", "description")
));
```

### Term Filter (Exact Match)

Filter by exact value (keyword fields):

```java
builder.filter(f -> f.term(t -> t
    .field("status")
    .value("ACTIVE")
));
```

### Optional Filters

Only add filter when the value is present:

```java
if (filter.categoryId() != null) {
    builder.filter(f -> f.term(t -> t
        .field("categoryId")
        .value(filter.categoryId())
    ));
}
```

### Price Range Filter

```java
if (filter.minPrice() != null || filter.maxPrice() != null) {
    builder.filter(f -> f.range(r -> {
        var range = r.field("price");
        if (filter.minPrice() != null) range.gte(JsonData.of(filter.minPrice()));
        if (filter.maxPrice() != null) range.lte(JsonData.of(filter.maxPrice()));
        return range;
    }));
}
```

---

## Sorting

```java
private List<SortOptions> buildSort(String sort) {
    return switch (sort != null ? sort : "relevance") {
        case "price_asc"  -> List.of(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))));
        case "price_desc" -> List.of(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))));
        case "newest"     -> List.of(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
        default           -> List.of(SortOptions.of(s -> s.score(v -> v.order(SortOrder.Desc))));
    };
}
```

---

## Pagination

Use `from` + `size` for offset-based pagination:

```java
.from(query.page() * query.size())
.size(query.size())
```

---

## Response Mapping

```java
private SearchProductResult toResult(SearchResponse<Map> response) {
    List<SearchProductHit> hits = response.hits().hits().stream()
        .map(hit -> {
            Map<String, Object> source = hit.source();
            return new SearchProductHit(
                (String) source.get("productId"),
                (String) source.get("name"),
                ((Number) source.get("price")).longValue(),
                (String) source.get("status")
            );
        })
        .toList();

    long totalHits = response.hits().total() != null
        ? response.hits().total().value() : 0;

    return new SearchProductResult(hits, totalHits);
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Using `match` on keyword fields | Use `term` for exact match on keyword fields |
| Not handling null in optional filters | Only add filter clause when value is non-null |
| Missing `from` calculation | `from = page * size` — not just `page` |
| No error wrapping | Catch all exceptions and wrap in `SearchException` |
