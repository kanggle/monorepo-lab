# Feature: Wishlist Management

## Purpose

Lets an authenticated user save products for later by maintaining a
per-user wishlist (user ↔ product associations). Owned by `user-service`
as part of its declared Domain Scope (`user-service/architecture.md` —
"Wishlist: user-product associations, product info fetched from
product-service at query time"). Product display data (name, price,
status) is **not** stored — it is fetched from `product-service` at query
time so the wishlist never holds stale catalog data.

## Related Services

| Service | Role |
|---|---|
| user-service | Primary owner — wishlist CRUD, `wishlist_items` persistence, ownership enforcement, 100-item limit |
| product-service | Queried at wishlist-read time to enrich items with `productName` / `productPrice` / `productStatus` (read-only, `GET /api/products/{productId}`) |
| gateway-service | Token validation + `X-User-Id` header injection (all wishlist endpoints are authenticated via this header) |
| web-store | Customer-facing "add to wishlist" / wishlist page / "in wishlist?" toggle |

## User Flows

### Add to Wishlist

1. Authenticated user sends `POST /api/wishlists` with `productId`
2. user-service validates the user profile exists and the product is not already in the wishlist
3. On success: the item is persisted and `{ wishlistItemId, productId }` is returned (201)
4. If the product is already present, the request is rejected with `ALREADY_IN_WISHLIST` (409)
5. If the wishlist already holds 100 items, the request is rejected with `WISHLIST_LIMIT_EXCEEDED` (409)

### View Wishlist

1. Authenticated user sends `GET /api/wishlists/me` with optional `page` / `size`
2. user-service returns a paginated list; for each item it calls product-service to enrich `productName` / `productPrice` / `productStatus`
3. If product-service is unavailable or the product was deleted, the item is returned with `productStatus = "DELETED"` and `productName = null` (the wishlist read never fails because of product-service)

### Remove from Wishlist

1. Authenticated user sends `DELETE /api/wishlists/{wishlistItemId}`
2. user-service verifies the item belongs to the requesting user
3. On success: 204 No Content
4. If the item is not owned by the user → `ACCESS_DENIED` (403); if it does not exist → `WISHLIST_ITEM_NOT_FOUND` (404)

### Check Membership

1. Authenticated user sends `GET /api/wishlists/me/check?productId=...`
2. user-service returns `{ productId, inWishlist, wishlistItemId }` — `wishlistItemId` is populated only when `inWishlist` is `true` (used by the storefront to render the toggle state without fetching the full list)

## Business Rules

- All wishlist endpoints require authentication via the `X-User-Id` header injected by gateway-service; a missing header is `UNAUTHORIZED` (401)
- A user profile must exist (`USER_PROFILE_NOT_FOUND` 404 on add when it does not)
- Maximum 100 items per wishlist (`WISHLIST_LIMIT_EXCEEDED` on exceeding)
- A product can appear at most once per user wishlist (`ALREADY_IN_WISHLIST`; `DATA_INTEGRITY_VIOLATION` is the concurrent-duplicate-insert backstop)
- Wishlist items are owned by the creating user only — cross-user delete is `ACCESS_DENIED` (403), never a silent success
- Product display fields are derived at read time from product-service and are never persisted in `user_db`; product-service unavailability degrades gracefully (DELETED / null) rather than failing the read
- Error responses follow the platform envelope `{ code, message, timestamp }` and never leak stack traces

## Related Contracts

- HTTP: `specs/contracts/http/wishlist-api.md`
- Events: none — wishlist mutations publish no domain events in v1 (user-service's published events `UserProfileUpdated` / `UserWithdrawn` do not cover wishlist changes)

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| _(none)_ | — | Wishlist add/remove is not event-sourced in v1; no producer/consumer |
