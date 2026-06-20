# TASK-BE-420 — Wire the product `name` partial-match filter

**Status:** done
**Type:** backend feature (product-service query)

## Goal

`product-api.md` documents a `name` filter on `GET /api/products` as "NOT IMPLEMENTED (v1) — documented for intent but not wired in the controller." Wire it end-to-end: a case-insensitive partial-match `name` query parameter that narrows the product list. Apply symmetrically to the operator-plane `GET /api/admin/products` (which the spec says mirrors the public query path exactly).

## Scope

Hexagonal threading of one optional `String name` parameter:

- `presentation/controller/ProductController.java` — add `@RequestParam(required = false) String name`, pass to `findAll`.
- `presentation/controller/AdminProductController.java` — same (keep operator/public read paths symmetric).
- `application/service/QueryProductService.java` — add `name` to `findAll`; extend the `@Cacheable` key with `name` (a name-filtered call must never serve an unfiltered cache entry); delegate to the port.
- `application/port/ProductQueryPort.java` — add `name` to `findSummaries`.
- `infrastructure/persistence/ProductRepositoryImpl.java` — thread `name` to `findByFilters`.
- `infrastructure/persistence/ProductJpaRepository.java` — add `@Param("name")` + null-guarded JPQL clause `(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))` (JPQL has no ILIKE; LOWER+LIKE is portable across Postgres prod and H2 PostgreSQL-mode tests).
- `specs/contracts/http/product-api.md` — mark `name` implemented on both endpoints (remove NOT IMPLEMENTED note).
- Tests: `QueryProductServiceTest` (port delegation), `ProductControllerTest` (param pass-through + null when absent), `ProductRegisterQueryIntegrationTest` (`@Tag("integration")` — partial match, case-insensitivity, no-match). Update all existing `findAll`/`findSummaries` stubs/call-sites to the new arity.

## Acceptance Criteria

- [ ] **AC-1** — `GET /api/products?name=X` returns only products whose name contains `X` (case-insensitive), within the existing tenant + seller-scope isolation.
- [ ] **AC-2** — Absent `name` = no name filter (full list, backward-compatible). null is threaded, not empty-string.
- [ ] **AC-3** — `GET /api/admin/products` accepts the same `name` param (symmetry).
- [ ] **AC-4** — `@Cacheable` key includes `name` (no cross-filter cache bleed).
- [ ] **AC-5** — Existing tenant/seller-scope predicates and pagination are unchanged; the `name` clause is nested inside the tenant filter.
- [ ] **AC-6** — Unit + slice + integration tests added; `product-api.md` updated. product-service build GREEN (CI Linux runs the `@Tag("integration")` IT).

## Related Specs / Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md` (GET /api/products + GET /api/admin/products)

## Edge Cases / Failure Scenarios

- `name` absent → null → full list (AC-2).
- Case-insensitive match (`blue` matches `Blue Shirt`) — `LOWER/LOWER`.
- No match → empty content, `totalElements=0`.
- Leading-wildcard `LIKE` cannot use a B-tree index → sequential scan within the tenant partition; acceptable for v1. A `pg_trgm` GIN index on `name` is a noted future follow-up (not in scope).
- Interaction with `categoryId`/`status`/seller-scope filters: all AND-composed, null-guarded independently.
