---
id: TASK-BE-404
title: "search-service tenant_id isolation — Elasticsearch index filter (ADR-MONO-030 Step 4, facet c)"
status: done
service: search-service
tags: [code, test, multi-tenant, event-driven, elasticsearch]
analysis_model: "Opus 4.8"
impl_model: "Sonnet 4.6"
created: 2026-06-18
---

# TASK-BE-404 — search-service tenant_id isolation (Elasticsearch)

## Goal

Bring `search-service` into the multi-tenant marketplace SaaS model (ADR-MONO-030 Step 4, facet
c). search-service is **NOT a relational/Flyway pattern-repeat** of payment-service — it is an
**Elasticsearch read-index** fed by Kafka product events (Spring Data Elasticsearch, no relational
DB, no Flyway, no outbox). Tenant isolation here is an **index-document field + query filter**,
not a SQL column:

1. Each indexed `SearchDocument` gains a `tenantId` field, populated from the inbound
   product-sync event's `tenant_id` (product-service already publishes `tenant_id` on its event
   envelopes — TASK-BE-357 Step 2).
2. Every search/read query is scoped by `tenant_id` from the request `TenantContext`
   (a `term`/`filter` clause `tenant_id = <context>`).

This realises the M5 invariant (async propagation: the index consumer threads `tenant_id`) and
M1/M2/M3 in the Elasticsearch idiom (document-level tenant field + mandatory query filter +
cross-tenant results simply absent).

## Scope

1. **TenantContext + TenantContextFilter** — port the ThreadLocal `TenantContext` (default
   `'ecommerce'`, never null — D8 net-zero) and the `X-Tenant-Id` servlet filter from the
   payment-service / sibling pattern into search-service. (Same shape as the relational services;
   only the persistence side differs.)

2. **SearchDocument** — add a `tenantId` field to the `SearchDocument` record/document mapping
   (Elasticsearch `@Field`/keyword). Re-index mapping note: a new keyword field is additive to the
   index mapping; if the index is created from a mapping bean, ensure the field is declared so new
   documents carry it. Document the (demo-acceptable) reindex/backfill: existing documents without
   `tenantId` should be treated as the default tenant — either backfill on next product event or
   default-coalesce in the query (see Edge Cases).

3. **Index sync consumer** — the Kafka consumer that ingests product-service events
   (`product.product.*` create/update/stock-changed/etc.) must **extract `tenant_id` from the
   event envelope** and stamp it onto the `SearchDocument` it upserts. (Inbound events carry
   `tenant_id` at the envelope top level since TASK-BE-357.) This is the M5 propagation point.

4. **Query scoping** — every read path in `SearchService` (keyword search, filter, autocomplete,
   category browse — whatever exists) adds a mandatory `tenant_id = TenantContext.currentTenant()`
   filter clause to the Elasticsearch query. A document in tenant A must not appear in a tenant-B
   search (M2/M3 — cross-tenant docs are simply not matched, the search idiom of 404-over-403).

5. **Spec updates**:
   - ADD a `## Multi-Tenancy` section to `specs/services/search-service/architecture.md`
     describing the **index-field + query-filter** isolation shape (explicitly noting it differs
     from the relational `tenant_id`-column services; isolation = ES document field, not a Flyway
     column; default-tenant coalesce for D8).
   - UPDATE `specs/features/multi-tenancy-and-marketplace.md` — mark `search-service` done with a
     note that its shape is ES index-filter (e.g. `search-service | ES index-filter | TASK-BE-404`),
     NOT a Flyway version. (If TASK-BE-403 has already reclassified cart/auth/web-store in this
     file, only update the search row; do not duplicate. Coordinate via the file's current state at
     implementation time — re-read before editing.)

6. **Tests** — unit tests (no Testcontainers/ES container locally; ITs run in CI only):
   - `TenantContext` (set / currentTenant / clear / default `'ecommerce'`)
   - `TenantContextFilter` reads header and clears in `finally`
   - the index-sync consumer maps an inbound event's `tenant_id` onto the `SearchDocument`
     (assert the upserted document carries the event's `tenant_id`; default `'ecommerce'` when the
     event lacks one)
   - the query builder includes a `tenant_id` filter clause for the current context (assert at the
     query-construction level — no live ES needed)

## Acceptance Criteria

- AC-1: `SearchDocument` carries a `tenantId` field; the index-sync consumer populates it from the
  inbound event's `tenant_id` (default `'ecommerce'` when absent — D8 net-zero).
- AC-2: `./gradlew :projects:ecommerce-microservices-platform:apps:search-service:test` passes with
  all new tests GREEN (unit scope; ES/Testcontainers ITs are CI-only on this host).
- AC-3: every `SearchService` read query includes a mandatory `tenant_id` filter from
  `TenantContext`; a tenant-A document is not returned under a tenant-B context (verified at the
  query-construction unit level).
- AC-4: `TenantContext.currentTenant()` returns `'ecommerce'` with no header (standalone/D8); search
  behaves byte-identically to single-store when only default-tenant documents exist.
- AC-5: `specs/services/search-service/architecture.md` has a `## Multi-Tenancy` section describing
  the ES index-filter shape.
- AC-6: `search-service` marked done in `specs/features/multi-tenancy-and-marketplace.md` with the
  ES index-filter note.

## Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` — M1-M7, in-migration table
- `specs/services/search-service/architecture.md` — service architecture (no MT section yet)
- `specs/services/product-service/architecture.md` — product events that feed the index (carry
  `tenant_id` since TASK-BE-357)
- `rules/traits/multi-tenant.md` — M1-M7 (interpret M1/M2/M3 in the ES index idiom)

## Related Contracts

- the product → search event contract under `specs/contracts/events/` (product events already carry
  `tenant_id`; search-service is a consumer — confirm the envelope field name, no producer change).

## Edge Cases

- **Standalone / no IAM**: `X-Tenant-Id` absent → `TenantContext` = `'ecommerce'`; all docs are
  default-tenant; behaves as single-store search.
- **Pre-existing index documents without `tenantId`**: on a running demo index, documents indexed
  before this change lack the field. Acceptable demo handling: (a) they get the field on the next
  product event re-sync, and/or (b) the query coalesces a missing `tenantId` to the default tenant.
  Pick one, document it in the architecture MT section; do NOT require a destructive reindex.
- **Event without `tenant_id`** (older producer/standalone): consumer defaults the document's
  `tenantId` to `'ecommerce'` (additive-contract tolerance).

## Failure Scenarios

- **Index mapping conflict**: adding a `keyword` field is additive; if the index uses a strict
  mapping created at startup, ensure the field is declared in the mapping bean so upserts don't
  fail. Document the (non-destructive) mapping update.
- **Missing query filter regression**: a read path that forgets the `tenant_id` filter leaks
  cross-tenant results — the query-construction unit tests (AC-3) are the guard; ensure every read
  path is covered.
- **`tenant_id` null in context**: mitigated by the `TenantContext` invariant (never null; default
  `'ecommerce'`).
