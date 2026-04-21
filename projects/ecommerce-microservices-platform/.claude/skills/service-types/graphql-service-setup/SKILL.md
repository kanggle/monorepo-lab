---
name: graphql-service-setup
description: Set up a `graphql-service` end-to-end
category: service-types
---

# Skill: GraphQL Service Setup

Implementation orchestration for a `graphql-service`. Use only when REST round-trip pain is documented.

Prerequisite: read `specs/platform/service-types/graphql-service.md` before using this skill. No services in this monorepo currently use GraphQL.

---

## Orchestration Order

1. **Justification** — document in `architecture.md` why GraphQL was chosen
2. **Schema** — author `specs/contracts/graphql/<service>/schema.graphqls`
3. **Architecture style** — pick from `backend/architecture/`; resolvers live in the application layer
4. **Server framework** — Netflix DGS, Spring GraphQL, or graphql-java directly
5. **DataLoaders** — one per downstream resource type
6. **Auth** — JWT bearer at gateway, field-level directives or resolver guards
7. **Complexity limits** — max depth, max complexity, query whitelisting for public clients
8. **Caching** — `cross-cutting/caching/SKILL.md` keyed by parent + args
9. **Observability** — per-resolver and per-operation metrics
10. **Tests** — schema validation, resolver unit tests, integration with Testcontainers downstream

---

## Schema Layout

```
specs/contracts/graphql/
  catalog-service/
    schema.graphqls
    directives.graphqls
```

```graphql
# schema.graphqls
type Query {
  product(id: ID!): Product @auth(role: USER)
  products(first: Int!, after: String, filter: ProductFilter): ProductConnection! @auth(role: USER)
}

type Product {
  id: ID!
  name: String!
  price: Money!
  reviews(first: Int!): ReviewConnection!   # MUST use DataLoader
}

directive @auth(role: Role!) on FIELD_DEFINITION
directive @owner(team: String!) on FIELD_DEFINITION | OBJECT
```

---

## DataLoader Pattern

Every list field that resolves to nested data needs a DataLoader:

```java
@DgsComponent
public class ProductDataLoader {

    @DgsDataLoader(name = "reviewsByProductId")
    public BatchLoader<String, List<Review>> reviewsByProductId() {
        return productIds -> CompletableFuture.supplyAsync(() ->
            reviewService.findByProductIds(productIds)
                .stream()
                .collect(Collectors.groupingBy(Review::productId))
                .values()
                .stream()
                .toList()
        );
    }
}

@DgsComponent
public class ProductDataFetcher {

    @DgsData(parentType = "Product", field = "reviews")
    public CompletableFuture<List<Review>> reviews(DgsDataFetchingEnvironment env) {
        Product product = env.getSource();
        DataLoader<String, List<Review>> loader = env.getDataLoader("reviewsByProductId");
        return loader.load(product.id());
    }
}
```

Adding a nested resolver without a DataLoader is forbidden.

---

## Complexity Limits

```java
@Bean
public Instrumentation queryComplexityInstrumentation() {
    return new MaxQueryComplexityInstrumentation(1000);
}

@Bean
public Instrumentation queryDepthInstrumentation() {
    return new MaxQueryDepthInstrumentation(7);
}
```

Reject introspection in production:

```yaml
spring:
  graphql:
    schema:
      introspection:
        enabled: false   # in production profile
```

---

## Persisted Queries (Public Clients)

For mobile / public web, register query hashes server-side and reject unknown hashes:

```java
@Bean
public PersistedQueryCacheLoader persistedQueryCacheLoader() {
    return new InMemoryPersistedQueryCacheLoader(loadFromConfigMap());
}
```

Build clients to send only the hash + variables, never the full query string.

---

## Observability Specifics

| Metric | Type | Labels |
|---|---|---|
| `graphql_resolver_duration_seconds` | histogram | parent_type, field |
| `graphql_resolver_errors_total` | counter | parent_type, field, error_code |
| `graphql_operation_duration_seconds` | histogram | operation_name |
| `graphql_operation_errors_total` | counter | operation_name, error_code |

Trace each resolver as a child span of the operation root span.

---

## Self-Review Checklist

Verify against `specs/platform/service-types/graphql-service.md` Acceptance section. Specifically:

- [ ] Schema reviewed and committed
- [ ] DataLoader covers every list field with nested resolvers
- [ ] Complexity and depth limits enforced
- [ ] Introspection disabled in production
- [ ] Persisted queries enabled for public clients
- [ ] Per-resolver and per-operation metrics emitted
- [ ] Auth wired at gateway + field directives
