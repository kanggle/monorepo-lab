# Task ID

TASK-BE-567

# Title

ADR-MONO-058 D3 — adopt `libs/java-common.PageResult`/`PageQuery` in place of hand-rolled pagination shapes (5 services)

# Status

ready

# Owner

backend

# Task Tags

- code
- refactor

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` § 2 D3 found `libs/java-common.PageResult`/`PageQuery` frequently
already on the consuming service's classpath (used for `UuidV7`) and simply never
imported for paging, leading to hand-rolled shapes that drift from the shared one
(`content` vs `items` naming, missing `totalPages`). Grepping ecommerce's 12 backend
services confirms **partial adoption already exists** — `order-service`,
`shipping-service`, `promotion-service`, and `settlement-service` already import and
use `com.example.common.page.PageResult`/`PageQuery` at the application/domain layer
(e.g. `ShippingQueryService.listShippings` returns `PageResult<ShippingSummary>`
built directly from the repository's `PageResult<Shipping>`). Five services still
hand-roll an equivalent shape without importing the shared type, and every one of the
five is **missing `totalPages`** compared to the shared `PageResult<T>`'s
`(content, page, size, totalElements, totalPages)`:

| Service | Hand-rolled class | Missing field(s) |
|---|---|---|
| `user-service` | `application/result/WishlistPageResult.java`, `application/result/UserListPageResult.java` | `totalPages` |
| `review-service` | `application/result/ReviewListResult.java`, `application/result/MyReviewListResult.java` | `totalPages` |
| `product-service` | `application/dto/ProductListResult.java`, `application/dto/SellerListResult.java` | `totalPages` |
| `notification-service` | `application/result/ListNotificationsResult.java` | `totalPages` |
| `search-service` | `application/dto/SearchProductResult.java` | different shape entirely (`content, facets, totalElements` — no `page`/`size`/`totalPages`; see Edge Cases) |

After this task, these five services' application-layer pagination carriers are
replaced by (or built from) `com.example.common.page.PageResult`/`PageQuery`, closing
the `totalPages` gap as a side effect rather than patching each hand-rolled shape
individually.

---

# Scope

## In Scope

- `user-service`: replace `WishlistPageResult` and `UserListPageResult` with
  `PageResult<T>` (or have them wrap/delegate to it) at the
  `WishlistService`/`UserProfileService` application layer; update
  `WishlistPageResponse`/`AdminUserListResponse` presentation DTOs to source
  `totalPages` from the now-available field.
- `review-service`: same treatment for `ReviewListResult` and `MyReviewListResult`
  (note `ReviewListResult` carries two extra domain fields — `averageRating`,
  `totalReviews` — that are NOT part of `PageResult`'s shape and must be preserved
  alongside it, e.g. by having `ReviewListResult` compose a `PageResult<ReviewItem>`
  plus its own two domain fields, not by discarding them).
- `product-service`: same treatment for `ProductListResult` and `SellerListResult`.
- `notification-service`: same treatment for `ListNotificationsResult`.
- `search-service`: adopt `PageQuery` for the request side if `ProductCatalogHttpAdapter`
  /`ElasticsearchQueryAdapter` accept an offset/limit-shaped query today; for the
  response side, evaluate whether `SearchProductResult`'s `facets` field can coexist
  with `PageResult<SearchDocument>` (compose, per the `ReviewListResult` pattern above)
  — see Edge Cases for why a forced full swap may not fit Elasticsearch's
  cursor/facet-driven result shape.
- Corresponding presentation-layer `*ListResponse`/`*PageResponse` DTOs updated to
  expose `totalPages` where it was previously absent (this is the "fix the
  inconsistency, don't preserve it by wrapping" instruction in the ADR's D3 text) —
  confirm with each service's `specs/contracts/http/<service>-api.md` whether adding a
  `totalPages` field to a published response is additive (safe) per that contract's
  versioning policy before shipping it.

## Out of Scope

- `order-service`, `shipping-service`, `promotion-service`, `settlement-service` —
  already adopted, no changes needed.
- `gateway-service`, `payment-service`, `batch-worker`, `web-store` — no
  paginated-list endpoint of their own found in this task's grep (re-verify at
  implementation time; if one is found, it is a straightforward addition to this
  task's scope, not a separate task).
- Renaming `content` → `items` or vice versa on any already-adopted service (the ADR
  flags `content` vs `items` naming drift as a fleet-wide finding, but ecommerce's
  four existing adopters all already use `content` consistently — no drift found here).
- Any change to `libs/java-common.PageResult`/`PageQuery` themselves.

---

# Acceptance Criteria

- [ ] `user-service`, `review-service`, `product-service`, `notification-service`
      application-layer pagination carriers are backed by (compose or directly return)
      `com.example.common.page.PageResult`/`PageQuery`; hand-rolled equivalents are
      removed or reduced to a thin domain-specific wrapper around the shared type.
- [ ] `search-service`'s pagination/query surface is reconciled with `PageQuery`/
      `PageResult` to the extent Elasticsearch's result shape allows, with any
      deliberate divergence (e.g. keeping `facets` outside `PageResult`) documented in
      the code, not silently dropped.
- [ ] Every affected presentation-layer response DTO now exposes `totalPages`
      (previously absent in all five).
- [ ] Each affected service's `specs/contracts/http/<service>-api.md` is checked for
      whether the added `totalPages` field requires a contract-doc update — updated if
      the contract enumerates response fields explicitly (most likely) or noted as
      already-permissive if not.
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:<service>:test` GREEN
      for each of the 5 touched services.
- [ ] No existing endpoint's `content`/pagination field naming changes for any
      already-adopted service (out of scope, must remain untouched).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and
> `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a
> Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2
  D3
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)

---

# Related Contracts

- `specs/contracts/http/user-api.md`, `review-api.md`, `product-api.md`,
  `notification-api.md`, `search-api.md` — each must be checked for whether adding
  `totalPages` to a documented paginated response requires an explicit doc update
  (per this repo's contracts-before-implementation rule, `CLAUDE.md § Layer Rules`).

---

# Target Service

- `user-service`, `review-service`, `product-service`, `notification-service`,
  `search-service`

---

# Architecture

Follow, per touched service:

- `specs/services/user-service/architecture.md`
- `specs/services/review-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/notification-service/architecture.md`
- `specs/services/search-service/architecture.md`

---

# Implementation Notes

- `shipping-service`'s `ShippingQueryService.listShippings` (`apps/shipping-service/src/main/java/com/example/shipping/application/service/ShippingQueryService.java`)
  is the cleanest already-adopted worked example in this project: it takes a
  `PageQuery`, delegates to a repository method returning `PageResult<Shipping>`, and
  maps content via `PageResult<T>#map`-equivalent construction — use this as the
  reference shape for the four services being migrated in this task, rather than
  inventing a new pattern.
- `libs/java-common.PageResult<T>` already exposes a `.map(Function<T,R>)` method —
  prefer it over manually reconstructing a new `PageResult` field-by-field (as
  `ShippingQueryService` currently does manually; either is acceptable, `.map()` is
  less error-prone for new code).
- `review-service`'s `ReviewListResult` and `notification-service`'s
  `ListNotificationsResult` both nest a service-specific inner record (`ReviewItem`,
  `NotificationSummary`) as the `content` type parameter — this pattern is directly
  compatible with `PageResult<ReviewItem>`/`PageResult<NotificationSummary>`, no
  structural change needed beyond the field list.

---

# Edge Cases

- `search-service`'s `SearchProductResult` (`content, facets, totalElements`) has no
  `page`/`size`/`totalPages` at all — it may be intentionally shaped around
  Elasticsearch's result-window model rather than a page/size pagination model. Verify
  at implementation time whether `ProductCatalogHttpAdapter`'s actual request/response
  cycle uses offset/limit (in which case full `PageQuery`/`PageResult` adoption
  applies) or an Elasticsearch-native `from`/`size` + scroll/search-after model (in
  which case forcing the shared `PageResult` shape may not fit, and the correct
  outcome could be "adopt `PageQuery` for the request side only, keep `facets`
  co-located with a `PageResult`-shaped response via composition" — this is a design
  call for the implementer to make explicit, not silently default either way).
- `review-service`'s extra `averageRating`/`totalReviews` fields on `ReviewListResult`
  are aggregate summary data, not per-page content — composing `PageResult<ReviewItem>`
  plus these two fields (rather than trying to cram them into `PageResult<T>` itself)
  keeps `PageResult` project-agnostic per `platform/shared-library-policy.md`'s
  Ownership Rule (HARDSTOP-03 risk if a service-specific field were pushed into the
  shared type instead).
- Adding `totalPages` to a response a frontend (`web-store`) or `platform-console`
  already deserializes with a fixed shape is additive and should be non-breaking for
  any reasonable JSON deserializer, but verify no consumer does strict/closed schema
  validation on these specific response shapes before treating this as risk-free.

---

# Failure Scenarios

- Discarding `review-service`'s `averageRating`/`totalReviews` fields while migrating
  to `PageResult<T>` would silently drop response data a client currently depends on
  — a contract regression disguised as a refactor.
- Renaming `content` to match some other convention while migrating (not requested by
  this task) would be an unrequested breaking change to already-stable field naming;
  stay within "adopt the shared carrier, fix the `totalPages` gap," nothing more.

---

# Test Requirements

- Unit tests for each migrated service's query/list use case asserting `totalPages`
  is now correctly computed and present in the returned result.
- Existing list/pagination tests for the 5 services must remain GREEN (content shape
  and ordering unchanged) except where a test specifically needs a `totalPages`
  assertion added.

---

# Definition of Done

- [ ] All 5 services adopt `PageResult`/`PageQuery` (or a documented, deliberate
      partial adoption for `search-service`)
- [ ] `totalPages` present in all 5 previously-missing response shapes
- [ ] Contracts checked/updated where required
- [ ] Tests added/passing for all 5 services
- [ ] Ready for review
