# TASK-BE-554 — Reconcile ecommerce contract docs: review productName, admin order endpoints, events index

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (three contract-doc additions, no behavior change)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). The audit's original
> framing was partly wrong and corrected on re-measurement: claim-1 direction was reversed (gap is in the **request**, not
> the response); the "internal order-cancel undocumented" sub-claim is **REFUTED** (it is documented in prose at
> `order-api.md:337-369`). Only the items below are real.

---

## Goal

Three ecommerce contract docs under-document endpoints/fields that the code actually ships. Add the missing documentation so
the contracts match the running services. All doc-only.

## Scope

**In scope:**

1. **review-api — `POST /api/reviews` request missing `productName`.** `specs/contracts/http/review-api.md:21-29` documents only
   `productId`, `rating`, `title`, `content`. Code accepts and **persists** `productName`:
   `apps/review-service/.../interfaces/dto/CreateReviewRequest.java:14` → `ReviewController.java:50` → `CreateReviewCommand.java:8`
   → `ReviewCommandService.java:52` → `Review.java:36`. (The `GET /api/reviews/me` leg already documents it correctly at
   `review-api.md:115`.) Add `"productName": "string"` to the POST request body block (~line 27).
2. **order-api — two admin endpoints undocumented.** `apps/order-service/.../presentation/AdminOrderController.java:48-51`
   (`GET /api/admin/orders/summary`, returns `PeriodSummary`) and `:53-56` (`GET /api/admin/orders/insights`, returns
   `OrderInsights`) have no section in `order-api.md` (which under "Admin Endpoints" `:221-339` documents only list/detail/status).
   Add two `###` entries mirroring the existing admin-endpoint style.
3. **events README §6 index missing a row.** `specs/contracts/events/README.md:5` prose says "14 contract files" (matches the
   14 `*.md` on disk), but the § 6 Contract Index table (`:48-62`) lists only **13** rows — it omits `fulfillment-events.md`
   (a real, populated contract, `events/fulfillment-events.md`, topic `ecommerce.fulfillment.requested.v1`, ADR-MONO-022 D1;
   already referenced in README prose at `:16`/`:27`). Add its row alongside the other wms cross-project subscriptions.

**Out of scope:** the internal order-cancel endpoint (already documented in prose — optionally promote to its own `###` for
symmetry, but not required); any code change.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm each drift at current `main` (line numbers may shift): the DTO field,
  the two `@GetMapping`s, and the 13-vs-14 index gap. Drop any that already match.
- **AC-1** — `review-api.md` POST body documents `productName` as an accepted (persisted) field.
- **AC-2** — `order-api.md` documents `GET /api/admin/orders/summary` and `GET /api/admin/orders/insights` with their response
  shapes (`PeriodSummary`, `OrderInsights` — read the actual DTOs, do not invent fields).
- **AC-3** — events `README.md` § 6 table lists all 14 contract files including `fulfillment-events.md`; the prose count and the
  table row-count agree, and both match `ls specs/contracts/events/*.md` minus README.
- **AC-4** — No behavior change; only the three docs touched. Sanity-check the event count by listing the files (an empty diff
  between "table rows" and "files on disk" proves completeness).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/contracts/http/review-api.md`
- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md`
- `projects/ecommerce-microservices-platform/specs/contracts/events/README.md`

## Related Contracts
- `events/fulfillment-events.md` (exists; missing only from the index table)

## Edge Cases
- Response DTOs for the two admin endpoints must be read from code (`AdminOrderController` return types) — do not copy an
  adjacent endpoint's shape.
- `productName` is caller-supplied and persisted as-is; document it as accepted input, not a server-derived field.

## Failure Scenarios
- **F1 — documenting the summary/insights endpoints from guesswork.** Guarded by AC-2 (read the DTOs).
- **F2 — fixing the prose "14" instead of the table.** The prose is already correct; the *table* is short a row. AC-3's
  file-count cross-check pins the real gap.
