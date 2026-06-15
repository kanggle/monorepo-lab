# TASK-BE-386 — Align ecommerce HTTP/event contracts to deployed code

**Status:** review
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet

---

## Goal

Align ecommerce HTTP and event contract specs to deployed code reality. Contracts are wrong; code is the source of truth. This is a SPEC-ONLY, NET-ZERO task — no apps/ code is changed. Unimplemented items are marked clearly (not deleted); implemented-but-undocumented items are added with exact signatures verified from controllers.

---

## Scope

**In (9 contract edits):**
1. `specs/contracts/http/product-api.md` — Fix `PATCH /api/admin/products/{productId}` response field `productId` → `id` (matches `RegisterProductResponse(String id)`)
2. `specs/contracts/http/product-api.md` — Mark `name` query filter on `GET /api/products` as NOT IMPLEMENTED (v1); `ProductController.list` accepts only `categoryId`, `status`, `page`, `size`
3. `specs/contracts/http/user-api.md` — Add NOT IMPLEMENTED note to `POST /api/users/me/withdrawal`; no `@PostMapping("/me/withdrawal")` exists in `UserController`
4. `specs/contracts/http/product-api.md` — Add undocumented operator endpoints: `DELETE /api/admin/products/{productId}`; `POST/PATCH/DELETE /api/admin/products/{productId}/variants/{variantId}`; `GET /api/admin/products/{productId}/images`; `GET /api/admin/sellers`; `GET /api/admin/sellers/{sellerId}`
5. `specs/contracts/http/shipping-api.md` — Add `POST /api/shippings/{shipmentId}/refresh-tracking` (verified in `ShippingController`)
6. `specs/contracts/http/search-api.md` — Add `POST /api/search/admin/reindex` (verified in `SearchAdminController`)
7. `specs/contracts/http/notification-api.md` — Add `GET /api/notifications/templates/{templateId}` (verified in `TemplateController`)
8. `specs/contracts/events/review-events.md` — Correct `ReviewCreated`/`ReviewUpdated`/`ReviewDeleted` consumers from "search-service, product-service" to "none in v1"; no `@KafkaListener` for `review.review.*` exists anywhere
9. `specs/contracts/events/promotion-events.md` — Correct `CouponUsed` consumers from "order-service, notification-service (future)" to "no consumer in v1"; no `@KafkaListener` for `promotion.coupon.used` exists in order-service

**Out:**
- All code under `apps/` — no code changes whatsoever
- Wiring the withdrawal HTTP endpoint (`UserController`) — separate code task
- A/B/C clusters (iam-platform, wms-platform, etc.)

---

## Acceptance Criteria

- [ ] Net-zero spec-only change: `git diff apps/` is empty
- [ ] `PATCH /api/admin/products/{productId}` response documents `{ "id": "..." }` matching `RegisterProductResponse(String id)`
- [ ] `POST /api/admin/products` response already documents `{ "id": "..." }` — confirmed consistent
- [ ] `GET /api/products` documents `name` filter with a clear NOT IMPLEMENTED (v1) note; parameter retained for intent
- [ ] `POST /api/users/me/withdrawal` has a NOT IMPLEMENTED (v1) note; section not removed
- [ ] All operator endpoints now in `product-api.md`: DELETE product, POST/PATCH/DELETE variants, GET image list, GET sellers list, GET seller detail
- [ ] `POST /api/shippings/{shipmentId}/refresh-tracking` documented in `shipping-api.md` with correct response shape (`shippingId`, `status`, `updatedAt`)
- [ ] `POST /api/search/admin/reindex` documented in `search-api.md` with correct response (`indexed`, `batchSize`) and internal-only note
- [ ] `GET /api/notifications/templates/{templateId}` documented in `notification-api.md` with correct `TemplateDetailResponse` shape
- [ ] `review-events.md` — all three events (Created/Updated/Deleted) state "no consumer in v1"
- [ ] `promotion-events.md` — `CouponUsed` states "no consumer in v1"

---

## Related Specs

- `specs/services/product-service/` — product domain architecture
- `specs/services/user-service/` — user domain architecture
- `specs/services/shipping-service/` — shipping domain architecture
- `specs/services/search-service/` — search domain architecture
- `specs/services/notification-service/` — notification domain architecture

---

## Related Contracts

- `specs/contracts/http/product-api.md`
- `specs/contracts/http/user-api.md`
- `specs/contracts/http/shipping-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/notification-api.md`
- `specs/contracts/events/review-events.md`
- `specs/contracts/events/promotion-events.md`

---

## Edge Cases

- **Mark, do not delete** — unimplemented items (`name` filter, withdrawal endpoint, review consumers, coupon-used consumer) are retained with explicit NOT IMPLEMENTED / "no consumer in v1" notes. Intent is preserved; a reader knows it is planned, not absent from the spec
- **Verify signatures before documenting** — every new endpoint was verified against the controller before being written into the contract; response shapes were verified against the concrete DTO records (`RegisterProductResponse`, `VariantDetail`, `SellerListResponse`, `SellerResponse`, `UpdateShippingStatusResponse`, `TemplateDetailResponse`)
- **Consumer verification via grep** — `@KafkaListener` grep across all `apps/` confirmed zero listeners for `review.review.*` and zero listeners for `promotion.coupon.used` in order-service; the outbox schedulers publish but have no paired consumers

---

## Failure Scenarios

- **Response field mismatch regression** — if `RegisterProductResponse` is renamed, the contract's `{ "id": "..." }` shape will diverge again; a future contract-alignment task must re-verify
- **Partial operator surface** — if new operator endpoints are added to `AdminProductController` or `AdminSellerController` without updating this contract, the drift recurs; contract updates must accompany each new controller method
- **Consumer added without contract update** — if a listener is wired for `review.review.*` or `promotion.coupon.used`, the "none in v1" note must be updated to reflect the actual consumer
- **Search admin internal-only assumption** — `SearchAdminController` is documented as internal-only (port 8085, not gateway-routed); if a gateway route is added, the contract note must be updated
