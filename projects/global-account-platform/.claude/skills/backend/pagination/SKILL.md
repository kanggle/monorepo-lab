---
name: pagination
description: Shared PageQuery/PageResult pagination DTOs
category: backend
---

# Skill: Pagination

Patterns for the shared pagination DTOs used across all services.

Prerequisite: read `platform/coding-rules.md` before using this skill.

---

## PageQuery (Request)

Shared record in `libs/java-common`. Validates and clamps input values.

```java
public record PageQuery(int page, int size, String sortBy, String sortDirection) {

    public PageQuery {
        if (page < 0) throw new IllegalArgumentException("Page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Size must be 1-100");
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(Math.max(page, 0), Math.min(Math.max(size, 1), 100), null, null);
    }
}
```

---

## PageResult (Response)

Generic response with content list and metadata. Supports `map()` for DTO projection.

```java
public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public <R> PageResult<R> map(java.util.function.Function<T, R> mapper) {
        List<R> mapped = content.stream().map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages);
    }
}
```

---

## Usage in Controller

```java
@GetMapping
public ResponseEntity<PageResult<OrderSummaryResponse>> getOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status) {
    PageQuery pageQuery = PageQuery.of(page, size);
    PageResult<OrderSummary> result = orderQueryService.getOrders(userId, status, pageQuery);
    return ResponseEntity.ok(result.map(OrderSummaryResponse::from));
}
```

---

## Usage in Repository

```java
// Repository interface (domain layer)
PageResult<Order> findByUserId(String userId, PageQuery pageQuery);

// JPA implementation (infrastructure layer)
@Override
public PageResult<Order> findByUserId(String userId, PageQuery pageQuery) {
    Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
    Page<OrderJpaEntity> jpaPage = jpaRepository.findByUserId(userId, pageable);
    List<Order> orders = jpaPage.getContent().stream()
        .map(mapper::toDomain).toList();
    return new PageResult<>(orders, jpaPage.getNumber(), jpaPage.getSize(),
        jpaPage.getTotalElements(), jpaPage.getTotalPages());
}
```

---

## Rules

- Use `PageQuery.of()` to safely clamp values.
- `PageResult.map()` converts domain objects to response DTOs.
- `PageQuery` and `PageResult` live in `libs/java-common` — shared by all services.
- Do not create service-specific pagination DTOs — use the shared ones.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Page size > 100 | `PageQuery` constructor rejects sizes > 100 |
| Negative page number | `PageQuery.of()` clamps to 0 |
| Mapping in controller instead of `map()` | Use `result.map(Response::from)` for clean projection |
| Creating a custom PageResult per service | Use the shared `libs/java-common` record |
