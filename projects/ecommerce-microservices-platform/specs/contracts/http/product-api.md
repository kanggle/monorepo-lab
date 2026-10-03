# HTTP Contract: product-service

## Overview
Published HTTP API for product-service.
All endpoints are accessible through gateway-service only.
Public endpoints (`/api/products/**`) do not require authentication.
All `/api/admin/products/**` endpoints — the operator-plane **reads** `GET /api/admin/products` (TASK-MONO-243) and `GET /api/admin/products/{productId}` (TASK-MONO-703) and the **write** endpoints (POST/PATCH/DELETE) — are gated at the gateway by `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id` (entitlement-trust). The platform-console operator obtains the `ECOMMERCE_OPERATOR` domain role via the ADR-MONO-035 4a assume-tenant derivation (ecommerce-entitled tenant → `ECOMMERCE_OPERATOR`); the service applies no additional ecommerce-local RBAC — the gateway is the single admission point. (ADR-MONO-035 4b removed the legacy `account_type=OPERATOR` gateway leg; `roles`-only admission is uniform across `/api/admin/**`.)

---

## Base Path
`/api/products`

---

## Endpoints

### GET /api/products
List products with filtering and pagination.

**Query Parameters**
- `categoryId` (optional) — filter by category
- `status` (optional) — filter by status: `ON_SALE`, `SOLD_OUT`, `HIDDEN`
- `name` (optional) — case-insensitive partial match on product name (`LOWER(name) LIKE LOWER('%name%')`); absent = no name filter (TASK-BE-420)
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "id": "string (UUID)",
      "name": "string",
      "status": "ON_SALE",
      "price": 10000,
      "thumbnailUrl": "string",
      "categoryId": "string (UUID)",
      "sellerId": "string (read-only — owning seller within the tenant)",
      "collectionRef": "string (nullable — fan artist id, see § collectionRef)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

`sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2) is the owning
seller within the tenant. On the CONSUMER plane it is **read-only display** (the
shared catalog is never seller-narrowed — F5). When an OPERATOR request carries a
seller-scope claim (gateway header `X-Seller-Scope`), the list is narrowed to that
seller; an absent / `*` scope returns the full tenant catalog (net-zero, F1).

---

### GET /api/admin/products
Operator-plane tenant-scoped product list (snapshot). Mirrors the public
`GET /api/products` query path exactly (`content[]` / `page` / `size` /
`totalElements` / `totalPages`) but on the **operator plane** under `/api/admin/products`.
Added by **TASK-MONO-243** (ADR-MONO-030 Step 4 facet a-후속-2) as the producer
read behind the platform-console Operator Overview ecommerce snapshot leg
(§ 2.4.9.1 row 6) — the console's cross-domain composition (in the `console-web`
server) calls it with `?page=0&size=1` and surfaces
`totalElements` as the tenant's product count.

**Query Parameters** (all optional — mirror `GET /api/products`)
- `categoryId` (optional) — filter by category (UUID)
- `status` (optional) — filter by status: `ON_SALE`, `SOLD_OUT`, `HIDDEN`
- `name` (optional) — case-insensitive partial match on product name (mirrors public endpoint — TASK-BE-420)
- `page` (default: 0) — page number
- `size` (default: 20, capped at 100) — page size

**Response 200**
```json
{
  "content": [
    {
      "id": "string (UUID)",
      "name": "string",
      "status": "ON_SALE",
      "price": 10000,
      "thumbnailUrl": "string",
      "categoryId": "string (UUID)",
      "sellerId": "string (read-only — owning seller within the tenant)",
      "collectionRef": "string (nullable — fan artist id, see § collectionRef)"
    }
  ],
  "page": 0,
  "size": 1,
  "totalElements": 42,
  "totalPages": 42
}
```

**Authorization**: gateway **entitlement-trust + `roles ∋ ECOMMERCE_OPERATOR`**
(the gateway's `AccountTypeEnforcementFilter` requires `ECOMMERCE_OPERATOR` for
`/api/admin/**`, and `TenantClaimValidator` requires a non-blank `tenant_id`).
The platform-console operator carries the `ECOMMERCE_OPERATOR` domain role via the
ADR-MONO-035 4a assume-tenant derivation (ecommerce-entitled tenant → `ECOMMERCE_OPERATOR`);
the service applies **no additional ecommerce-local RBAC** — the gateway is the
single admission point for both this read and the write endpoints. (ADR-MONO-035 4b
removed the legacy `account_type=OPERATOR` gateway leg; `roles`-only admission is
uniform across `/api/admin/**`.) **Read-only** (no mutation).

**Tenant scoping**: the gateway injects the trusted `X-Tenant-Id`; the read is
scoped automatically by the repository `WHERE tenant_id` chokepoint (Step 2 /
M6) — tenant A's count never includes tenant B's products. Seller-scope
(`X-Seller-Scope`) narrows the list when present (net-zero / F1 when absent).

---

### GET /api/products/{productId}
Get product detail including variants.

**Response 200**
```json
{
  "productId": "string (UUID)",
  "name": "string",
  "description": "string",
  "status": "ON_SALE",
  "price": 10000,
  "categoryId": "string (UUID)",
  "thumbnailUrl": "string (nullable — resolved URL of primary image)",
  "sellerId": "string (read-only — owning seller within the tenant)",
  "collectionRef": "string (nullable — fan artist id, see § collectionRef)",
  "images": [
    {
      "imageId": "string (UUID)",
      "url": "string (resolved CDN/storage URL)",
      "sortOrder": 0,
      "isPrimary": true
    }
  ],
  "variants": [
    {
      "variantId": "string (UUID)",
      "optionName": "string",
      "stock": 100,
      "additionalPrice": 0
    }
  ]
}
```

`thumbnailUrl` is derived: it equals the resolved URL of the image with `isPrimary=true`.
If no images exist, the value from the manual `thumbnailUrl` field (set via PATCH) is used.
`images` is sorted by `sortOrder` ascending; empty array `[]` if no images.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

---

### GET /api/admin/products/{productId}
Operator-plane product detail. Added by **TASK-MONO-703**: the platform-console
product detail and edit screens read the detail of a product the operator just
opened from `GET /api/admin/products`, but the only detail read was the public
`GET /api/products/{productId}`, and the gateway admits `ECOMMERCE_OPERATOR` on
the public product tree **not at all** (the operator-on-public exception covers
only promotions / shippings / notifications) — so the operator got the list and
a 403 on its detail. This endpoint closes that gap on the operator plane
instead of widening the public tree's admission.

**Response 200** — byte-identical to `GET /api/products/{productId}` (same
`ProductDetailResponse`: product fields + `images[]` + `variants[]`, same
`thumbnailUrl` derivation, same `images` ordering). Same query path
(`QueryProductService#findById` + `ProductImageService#getImages`).

**Authorization**: identical to `GET /api/admin/products` — gateway
`roles ∋ ECOMMERCE_OPERATOR` on `/api/admin/**` (or the SUPER_ADMIN wildcard
read admission for GET/HEAD) + non-blank `tenant_id`; the service applies **no
additional ecommerce-local RBAC**. **Read-only** (no mutation).

**Tenant scoping**: identical to the sibling admin endpoints — the gateway
injects the trusted `X-Tenant-Id`; the read goes through the repository
`WHERE tenant_id` chokepoint (Step 2 / M6). A product belonging to another
tenant is **404 `PRODUCT_NOT_FOUND`**, never 200 and never 403 (existence is
hidden, M3).

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist, or exists in another tenant |

---

### POST /api/admin/products
Register a new product. Requires admin role.

**Idempotency (required on this endpoint, TASK-BE-536)**

A replayed registration must not create a second product with a second stock
ledger. A name-uniqueness natural key is deliberately NOT used — two genuinely
different products can legitimately share a name — so a missing or blank
`Idempotency-Key` is refused with **400 `IDEMPOTENCY_KEY_REQUIRED`** rather than
falling back to a non-idempotent write. The key is scoped per-tenant.

| Replay shape | Behaviour |
|---|---|
| Same key, same `name` | **Replay.** Returns **201** with the ALREADY-created product's `id`. No second product is created, no second `ProductCreated` is published. |
| Same key, different `name` | **409 `IDEMPOTENCY_KEY_CONFLICT`** — the key is bound to the first request's name. |
| Different key, same tenant | A **genuine second registration** (even with the same `name`). Proceeds normally. |

Records are retained indefinitely (no TTL). Under two simultaneous duplicates the
loser of the `UNIQUE (tenant_id, idempotency_key)` insert also receives **409
`IDEMPOTENCY_KEY_CONFLICT`**; retrying that request hits the replay row.

**Request Headers**
- `Idempotency-Key: string` (**required**) — client-supplied, scoped to the tenant

**Request Body**
```json
{
  "name": "string",
  "description": "string",
  "price": 10000,
  "categoryId": "string (UUID)",
  "sellerId": "string (optional — owning seller; OPERATOR surface)",
  "collectionRef": "string (optional, ≤ 64 chars — fan artist id, see § collectionRef)",
  "variants": [
    {
      "optionName": "string",
      "stock": 100,
      "additionalPrice": 0
    }
  ]
}
```

`sellerId` (optional, OPERATOR surface — ADR-MONO-030 Step 3 §3.2) sets the owning
seller. When omitted, ownership resolves from the request's seller-scope claim
(`X-Seller-Scope`), else the per-tenant default seller `default` (D8). The CONSUMER
plane has no seller authority — it cannot register products.

**Response 201**
```json
{ "id": "string (UUID)" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | INVALID_CATEGORY | Category with given ID does not exist |
| 400 | IDEMPOTENCY_KEY_REQUIRED | `Idempotency-Key` header missing or blank |
| 403 | ACCESS_DENIED | Admin role required |
| 409 | IDEMPOTENCY_KEY_CONFLICT | The same `Idempotency-Key` was replayed with a different `name`, or lost a concurrent same-key insert race |

---

### POST /api/admin/sellers
Onboard a marketplace seller within the current tenant (ADR-MONO-030 Step 3 §3.1 +
Step 4 facet f / ADR-MONO-042). Requires admin role. The owning `tenant_id` is derived
from the token (gateway `X-Tenant-Id`), not the body.

**Onboarding now mints a real IAM seller-operator account (ADR-042, fail-soft).** The
seller is created `PENDING_PROVISIONING`; product-service then calls account-service
(see [internal/product-to-account.md](internal/product-to-account.md)) to mint the
seller-operator account + born-unified identity → the seller transitions to `ACTIVE`. If
IAM is unavailable the seller STAYS `PENDING_PROVISIONING` and onboarding still returns
`201` (D3 fail-soft) — re-provision via `POST /api/admin/sellers/{sellerId}/provision`.

**Request Body**
```json
{
  "sellerId": "string",
  "displayName": "string"
}
```

**Response 201** (returned regardless of provisioning outcome — onboarding never blocks)
```json
{ "sellerId": "string" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |

### POST /api/admin/sellers/{sellerId}/provision
Re-provision a seller stuck in `PENDING_PROVISIONING` (ADR-042 D3 retry — e.g. IAM was
unavailable at onboarding). Requires admin role. Idempotent: an already-`ACTIVE` seller
is a no-op. **Response 204** (no body). `404 SELLER_NOT_FOUND` if the seller does not
exist in the tenant.

### POST /api/admin/sellers/{sellerId}/suspend
Operator SUSPEND (ADR-042 D4): seller `ACTIVE → SUSPENDED` and the backing IAM account
is locked. Requires admin role. Idempotent + null-safe (a seller with no backing account
transitions without an IAM call). **Response 204**. `404 SELLER_NOT_FOUND` if missing.

### POST /api/admin/sellers/{sellerId}/close
Operator CLOSE (ADR-042 D4): seller → `CLOSED` (terminal) and the backing IAM account is
deactivated. Requires admin role. Idempotent + null-safe. **Response 204**.
`404 SELLER_NOT_FOUND` if missing.

> **TASK-MONO-752 (ADR-MONO-079 D5) — SUSPEND and CLOSE also revoke the members' `SELLER` site role.**
> Every `ACTIVE` member of the seller loses the store site role `SELLER` (IAM
> `site-roles:revoke`, [internal/product-to-account.md § 6](internal/product-to-account.md)). The member's
> **person account is never locked** — only the seller's machine account is (D4 above). A member who is
> still an `ACTIVE` member of **another `ACTIVE` seller** keeps the role (the role is one per site, so
> taking it away would take it from the other seller too); their row here still becomes `REVOKED`.
> A revoke that IAM did not confirm leaves that member `ACTIVE` — **re-sending SUSPEND / CLOSE on an
> already-SUSPENDED / CLOSED seller retries exactly those revocations** (nothing else: no second account
> lock). The same revocation runs when the seller is suspended by the reverse projection (machine
> account `LOCKED`, `events/account-lifecycle-subscriptions.md`).

---

## Seller members (TASK-MONO-752 — ADR-MONO-079 D5 · rider R4)

A seller has **people**: pool accounts (ADR-MONO-078 A) linked as members. One role, `MEMBER` (R4).
A person is linked only by **accepting an invitation while logged in to the store** — the invitation
email alone never links anyone (ADR-MONO-034 § 1.3). On accept, IAM writes
`consumer_site_roles(account, <tenant>, SELLER)` so the person's next store token carries
`["CUSTOMER","SELLER"]`; the fan token is unaffected (roles are per site).

Data: `seller_members(tenant_id, seller_id, account_id, role, status, joined_at)` — `status`
`ACTIVE` | `REVOKED`; `seller_member_invitations(id, tenant_id, seller_id, email, token_hash, status,
expires_at, invited_by, created_at, accepted_at, accepted_account_id)` — `status` `PENDING` | `ACCEPTED`.
Only the SHA-256 of the invitation token is stored.

🔵 **One person may be a member of several sellers** (decided here). The `SELLER` site role stays while
at least one of those memberships is `ACTIVE` in an `ACTIVE` seller.

### GET /api/admin/sellers/{sellerId}/members
Operator read: the seller's members and invitations. Requires admin role (`X-User-Role` ∋ `ECOMMERCE_OPERATOR`).

**Response 200**
```json
{
  "members": [
    { "accountId": "string", "role": "MEMBER", "status": "ACTIVE", "joinedAt": "string (ISO 8601)" }
  ],
  "invitations": [
    { "invitationId": "string (UUID)", "email": "string", "status": "PENDING",
      "expired": false, "expiresAt": "string (ISO 8601)", "createdAt": "string (ISO 8601)",
      "acceptedAt": "string (ISO 8601) | null" }
  ]
}
```
Members are ordered by `joinedAt` ascending, invitations by `createdAt` descending. The token is never returned here.

**Error responses**: 403 `ACCESS_DENIED` · 404 `SELLER_NOT_FOUND`

### POST /api/admin/sellers/{sellerId}/invitations
Operator invites a person by email. Requires admin role. The seller must be `ACTIVE`.

**Request Body**
```json
{ "email": "string (email, ≤ 320)" }
```

**Response 201**
```json
{
  "invitationId": "string (UUID)",
  "email": "string (lower-cased)",
  "expiresAt": "string (ISO 8601)",
  "token": "string"
}
```
- `token` is returned **once**, here — there is no mail delivery path in this stack (ADR-MONO-078 R1);
  the operator hands it to the person. It is 32 random bytes, base64url; the server keeps only its SHA-256.
- Lifetime **7 days** (`seller.invitation.ttl`, ISO-8601 duration). Single use.
- Inviting the same email again issues another independent invitation.

**Error responses**: 400 `VALIDATION_ERROR` · 403 `ACCESS_DENIED` · 404 `SELLER_NOT_FOUND` · 409 `SELLER_NOT_ACTIVE`

### POST /api/seller-invitations/accept
**Consumer plane** — the invited person, logged in to the store (gateway: authenticated + `CUSTOMER`).
Identity comes only from the gateway headers (`X-User-Id` = token `sub` = the pool account id,
`X-Tenant-Id` = the store tenant); the body carries only the token.

**Request Body**
```json
{ "token": "string" }
```

**Response 200**
```json
{ "sellerId": "string", "role": "MEMBER", "status": "ACTIVE", "joinedAt": "string (ISO 8601)" }
```

**Order of checks** (the first that fails answers; nothing is written by a refusal):
1. `X-User-Id` present → else `401 UNAUTHORIZED`.
2. The token's invitation exists **in this tenant** → else `404 SELLER_INVITATION_NOT_FOUND`.
3. Not yet accepted → else `409 SELLER_INVITATION_ALREADY_USED` (an accept by the **same** account that
   already used it answers 200 with the membership — a double submit is not an error).
4. Not expired → else `410 SELLER_INVITATION_EXPIRED`.
5. The seller is `ACTIVE` → else `409 SELLER_NOT_ACTIVE`.
6. IAM grant with `expectedEmail` = the invitation's email
   ([internal/product-to-account.md § 5](internal/product-to-account.md)) — IAM compares it with the
   logged-in account's own email: mismatch → `403 SELLER_INVITATION_EMAIL_MISMATCH`; a site (non-pool)
   account or no store membership → `409 SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE`; IAM unreachable →
   `503 SERVICE_UNAVAILABLE` (**fail-closed** — unlike provisioning, no link is made without IAM).
7. The invitation becomes `ACCEPTED` (conditional update — a concurrent second accept loses) and the
   member row is written `ACTIVE` (a previously `REVOKED` row for the same person is re-activated).
   If this step fails after IAM granted the role, product-service revokes the role again (best-effort).

A refused attempt does **not** consume the invitation — the right person can still accept it.

**Error responses**: 400 `VALIDATION_ERROR` · 401 `UNAUTHORIZED` · 403 `SELLER_INVITATION_EMAIL_MISMATCH` ·
404 `SELLER_INVITATION_NOT_FOUND` · 409 `SELLER_INVITATION_ALREADY_USED` · 409 `SELLER_NOT_ACTIVE` ·
409 `SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE` · 410 `SELLER_INVITATION_EXPIRED` · 503 `SERVICE_UNAVAILABLE`

---

### DELETE /api/admin/products/{productId}
Delete a product (soft-delete). Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id` (entitlement-trust). Service applies no additional ecommerce-local RBAC.

**Response 204** (no body)

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

---

### POST /api/admin/products/{productId}/variants
Add a variant to an existing product. Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. Service applies no additional ecommerce-local RBAC.

**Idempotency (natural key, TASK-BE-536)**: a duplicate `optionName` on the same
product is never a legitimate second request (unlike stock/create/coupon, where two
identical requests can both be genuine), so this endpoint uses a DB-level
`UNIQUE (product_id, option_name)` constraint rather than a client
`Idempotency-Key` — no request header is required. The scope is per-product: the
same `optionName` on a DIFFERENT product is not a duplicate.

**Request Body**
```json
{
  "optionName": "string",
  "stock": 0,
  "additionalPrice": 0
}
```

**Response 201**
```json
{
  "id": "string (UUID)",
  "optionName": "string",
  "stock": 0,
  "additionalPrice": 0
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 409 | DUPLICATE_VARIANT_OPTION | A variant with this `optionName` already exists on this product |

---

### PATCH /api/admin/products/{productId}/variants/{variantId}
Update a variant's option name and additional price. Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. Service applies no additional ecommerce-local RBAC.

**Request Body**
```json
{
  "optionName": "string",
  "additionalPrice": 0
}
```

**Response 200**
```json
{
  "id": "string (UUID)",
  "optionName": "string",
  "stock": 0,
  "additionalPrice": 0
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | VARIANT_NOT_FOUND | Variant with given ID does not exist |

---

### DELETE /api/admin/products/{productId}/variants/{variantId}
Remove a variant from a product. Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. Service applies no additional ecommerce-local RBAC.

**Response 204** (no body)

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | VARIANT_NOT_FOUND | Variant with given ID does not exist |

---

### GET /api/admin/products/{productId}/images
List all images for a product on the operator plane, sorted by `sortOrder` ascending. Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. Service applies no additional ecommerce-local RBAC.

**Response 200**
```json
{
  "images": [
    {
      "imageId": "string (UUID)",
      "url": "string (resolved CDN/storage URL)",
      "objectKey": "string",
      "sortOrder": 0,
      "isPrimary": true,
      "uploadedAt": "string (ISO 8601)"
    }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

---

### GET /api/admin/sellers
Tenant-scoped paged list of sellers (ADR-MONO-030 Step 4 facet f). Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. The controller also validates `X-User-Role: ECOMMERCE_OPERATOR`. Service applies no additional ecommerce-local RBAC.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20, capped at 100) — page size

**Response 200**
```json
{
  "content": [
    {
      "sellerId": "string",
      "displayName": "string",
      "status": "ACTIVE",
      "createdAt": "string (ISO 8601)",
      "updatedAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |

---

### GET /api/admin/sellers/{sellerId}
Tenant-scoped seller detail (ADR-MONO-030 Step 4 facet f). Requires admin role.

**Authorization**: gateway `roles ∋ ECOMMERCE_OPERATOR` + non-blank `tenant_id`. The controller also validates `X-User-Role: ECOMMERCE_OPERATOR`. A cross-tenant or missing `sellerId` returns 404.

**Response 200**
```json
{
  "sellerId": "string",
  "displayName": "string",
  "status": "ACTIVE",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | SELLER_NOT_FOUND | Seller with given ID does not exist for this tenant |

---

### PATCH /api/admin/products/{productId}
Update product information. Requires admin role.

**Request Body** (partial update)
```json
{
  "name": "string",
  "description": "string",
  "price": 10000,
  "status": "ON_SALE",
  "collectionRef": "string (optional, ≤ 64 chars — see § collectionRef)"
}
```

`collectionRef` on PATCH: **absent / `null` = unchanged**; a **blank string
(`""`) clears it to `NULL`** (same convention as `description`); any other value
is trimmed and stored.

**Response 200**
```json
{ "id": "string (UUID)" }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 409 | CONFLICT | Optimistic locking conflict |

---

### PATCH /api/admin/products/{productId}/stock
Adjust inventory stock. Requires admin role.

**Idempotency (required on this endpoint, TASK-BE-536)**

Two identical stock deltas can both be genuine (a real second "+10" receipt), so
no natural key can separate a retry from a real second adjustment — only the
client knows which it is. A missing or blank `Idempotency-Key` is refused with
**400 `IDEMPOTENCY_KEY_REQUIRED`**. The key is scoped to `variantId` (the balance
that actually changes), not `productId`.

| Replay shape | Behaviour |
|---|---|
| Same key, same `quantity` | **Replay.** Returns **200** with the variant's current stock. `stock` is NOT adjusted a second time, and NO second `StockChanged` event is published. |
| Same key, different `quantity` | **409 `IDEMPOTENCY_KEY_CONFLICT`** — the key is bound to the first request's quantity. |
| Different key, same variant | A **genuine second adjustment** (even with the same `quantity` — e.g. "+10 received twice"). Proceeds normally. |

Records are retained indefinitely (no TTL). Under two simultaneous duplicates the
loser of the `UNIQUE (variant_id, idempotency_key)` insert also receives **409
`IDEMPOTENCY_KEY_CONFLICT`**; retrying that request hits the replay row.

**Request Headers**
- `Idempotency-Key: string` (**required**) — client-supplied, scoped to `variantId`

**Request Body**
```json
{
  "variantId": "string (UUID)",
  "quantity": 50,
  "reason": "RESTOCK | ORDER_RESERVED | ORDER_CANCELLED | ADMIN_ADJUSTMENT"
}
```

**Response 200**
```json
{ "variantId": "string (UUID)", "currentStock": 150 }
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | INSUFFICIENT_STOCK | Stock adjustment would result in negative stock |
| 400 | IDEMPOTENCY_KEY_REQUIRED | `Idempotency-Key` header missing or blank |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | VARIANT_NOT_FOUND | Variant with given ID does not exist |
| 409 | IDEMPOTENCY_KEY_CONFLICT | The same `Idempotency-Key` was replayed for this variant with a different `quantity`, or lost a concurrent same-key insert race |

---

### POST /api/admin/products/{productId}/images/upload-url
Request a presigned PUT URL for direct image upload to object storage.
Requires admin role.
See `platform/object-storage-policy.md` for the full upload flow.

**Request Body**
```json
{
  "contentType": "image/jpeg",
  "contentLength": 2048000
}
```

**Validation rules**
- `contentType` must be one of: `image/jpeg`, `image/png`, `image/webp`
- `contentLength` must be > 0 and ≤ 5,242,880 (5 MB)
- Product must exist and not be soft-deleted

**Response 200**
```json
{
  "uploadUrl": "string (presigned PUT URL)",
  "objectKey": "products/{productId}/{sortOrder}-{uuid}.jpg",
  "expiresAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid content type or content length |
| 400 | MEDIA_VALIDATION_FAILED | Content type not in allow-list or size exceeds limit |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 503 | STORAGE_UNAVAILABLE | Object storage backend unreachable |

---

### POST /api/admin/products/{productId}/images
Register a previously uploaded image. Requires admin role.
The service verifies the object exists in the bucket via HEAD before persisting.

**Request Body**
```json
{
  "objectKey": "products/{productId}/0-{uuid}.jpg",
  "sortOrder": 0,
  "isPrimary": true
}
```

**Validation rules**
- `objectKey` must be a valid key matching `products/{productId}/*`
- `sortOrder` must be ≥ 0
- `isPrimary`: if true, any existing primary image is demoted
- Maximum 10 images per product
- Object must exist in the bucket (verified via HEAD)

**Response 201**
```json
{
  "imageId": "string (UUID)",
  "url": "string (resolved CDN/storage URL)",
  "objectKey": "string",
  "sortOrder": 0,
  "isPrimary": true,
  "uploadedAt": "string (ISO 8601)"
}
```

**Side effects**
- Publishes `ProductImagesUpdated` event
- If `isPrimary` is true, updates product `thumbnailUrl` to the resolved URL of this image

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 400 | MEDIA_VALIDATION_FAILED | Object size/type mismatch with allow-list |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | MEDIA_NOT_FOUND | Object key does not exist in the bucket |
| 422 | IMAGE_LIMIT_EXCEEDED | Product already has 10 images |

---

### PATCH /api/admin/products/{productId}/images/{imageId}
Update image metadata (sort order, primary flag). Requires admin role.

**Request Body** (partial update)
```json
{
  "sortOrder": 1,
  "isPrimary": false
}
```

**Response 200**
```json
{
  "imageId": "string (UUID)",
  "url": "string",
  "objectKey": "string",
  "sortOrder": 1,
  "isPrimary": false,
  "uploadedAt": "string (ISO 8601)"
}
```

**Side effects**
- Publishes `ProductImagesUpdated` event
- If `isPrimary` changes, updates product `thumbnailUrl` accordingly

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid field value |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | IMAGE_NOT_FOUND | Image with given ID does not exist for this product |

---

### DELETE /api/admin/products/{productId}/images/{imageId}
Delete an image. Requires admin role.
Removes both the metadata row and the object from the bucket.

**Response 204** (no body)

**Side effects**
- Publishes `ProductImagesUpdated` event
- If the deleted image was primary, the image with the lowest `sortOrder` is promoted
- Deletes the object from the bucket (best-effort; orphans cleaned by lifecycle)

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 403 | ACCESS_DENIED | Admin role required |
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |
| 404 | IMAGE_NOT_FOUND | Image with given ID does not exist for this product |

---

### GET /api/products/{productId}/images
List all images for a product, sorted by `sortOrder` ascending.
Public endpoint — no authentication required.

**Response 200**
```json
{
  "images": [
    {
      "imageId": "string (UUID)",
      "url": "string (resolved CDN/storage URL)",
      "sortOrder": 0,
      "isPrimary": true
    }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 404 | PRODUCT_NOT_FOUND | Product with given ID does not exist |

---

## Internal seller read — workload identity (ADR-MONO-079 D2 · TASK-MONO-759)

The store side of the fan → store seller verification (`fan-platform`
`artist-api.md` § Store seller verification). It is **not** an operator or shopper
surface: it is product-service's **first JWT-validating surface**, and the only
caller is a `client_credentials` workload token re-issued for the store tenant
(ADR-MONO-076 assume-tenant). Every other product-service path keeps the
gateway-header-trust model unchanged.

### GET /internal/sellers/{sellerId}

Reached **through the ecommerce gateway** (route `product-service-internal`,
`Path=/internal/sellers/**`, no rewrite) — the same shape as the iam gateway carrying
`/internal/tenants/**` (owner decision R1, 2026-10-03).

**Authorization — two layers, both fail-closed**

| Layer | Admits iff | Otherwise |
|---|---|---|
| ecommerce gateway (`AccountTypeEnforcementFilter`) | method `GET`/`HEAD` **and** `scope ∋ store.seller.read` **and** `tenant_id == ecommerce`. The `CUSTOMER` / `ECOMMERCE_OPERATOR` role rules do **not** apply on this path — and a `CUSTOMER` token without the scope is refused here | 403 `FORBIDDEN` |
| product-service (`ProductSecurityConfig`, `/internal/**` chain) | signature + issuer + expiry verify **and** `scope ∋ store.seller.read` **and** `tenant_id == ecommerce` (all checked in the decoder) | 401 `UNAUTHORIZED` |
| product-service, method | only `GET /internal/sellers/{sellerId}` is admitted; any other method/path under `/internal/**` | 403 `FORBIDDEN` |

`store.seller.read` is a machine-only scope: IdP migration `V0042` grants it to
`artist-service-client` alone, and no end-user client carries it. The token's tenant is
the store tenant `ecommerce` (assume-tenant — `WorkloadTenantCatalog` enumerates
`artist-service-client → {ecommerce}` only). The lookup is scoped to the token's
`tenant_id` — a request header cannot change it.

**Response 200**
```json
{ "sellerId": "string", "status": "PENDING_PROVISIONING | ACTIVE | SUSPENDED | CLOSED" }
```
`status` is the `SellerStatus` name verbatim. No other field is exposed.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | no / invalid / expired token, wrong issuer, missing `store.seller.read`, `tenant_id ≠ ecommerce` |
| 403 | FORBIDDEN | gateway: not a seller-read workload token · service: a method/path other than `GET /internal/sellers/{sellerId}` |
| 404 | SELLER_NOT_FOUND | no seller with that id **in tenant `ecommerce`** — the definite «does not exist» the caller maps to `Optional.empty()` |

---

## collectionRef — fan artist collection (ADR-MONO-079 D3 · TASK-MONO-749)

`products.collection_ref VARCHAR(64) NULL`, indexed `(tenant_id, collection_ref)`.

- **Meaning**: the **fan-platform artist id** (`artists.id`, a UUID string) whose
  official goods this product is — 팬 아티스트 id. It is the only identifier the
  two projects share (rider R1: one artist per value; group / agency collections
  are out of scope).
- **No FK** — the value points into another project's database. product-service
  never validates it against fan-platform and never resolves it. An archived
  artist leaves the product orphaned **but still on sale in the store**; the fan
  site simply has no page that shows it (it does not show archived artists).
- **`NULL` = no collection** — the product belongs to no artist page. Every
  product that pre-dates this field is `NULL` and behaves exactly as before.
- **Write**: optional on `POST /api/admin/products` and `PATCH
  /api/admin/products/{productId}`; max 64 chars (`400 VALIDATION_ERROR` above
  that). Blank on register = `NULL`.
- **Read**: returned on the public list/detail (`GET /api/products`,
  `GET /api/products/{productId}`) and the admin list/detail. It is **public by
  design** — the public snapshot (`@demo/public-data`, `PublicProduct.collectionRef`)
  carries it so the fan artist page can select goods with **zero backend calls**
  (ADR-MONO-077 invariant): `collectionRef === <artistId>`.
- No query filter on `collectionRef` is offered (the fan selection runs inside the
  snapshot); add one only when a backend consumer needs it.

---

## Error Response Format
```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

## Notes
- Internal stack traces must not appear in error responses.
- Stock adjustment publishes a `StockChanged` event regardless of the direction (increase or decrease).
