# admin-dashboard — Overview

> 1-pager: responsibilities, public surface (pages), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `admin-dashboard` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `frontend-app` |
| Architecture Style | **Layered by Feature** — `app/` → `features/` → `shared/`, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Next.js (App Router), TypeScript, Tailwind, `@repo/api-client` / `@repo/ui` / `@repo/types` / `@repo/utils` |
| Deployable unit | `web/admin-dashboard/` (or `apps/admin-dashboard/` per repo layout) |
| Bounded Context | `ecommerce-admin` (internal ops) |
| Persistent stores | none — auth session cookie only; transient client state for UI prefs |
| Backend dependencies | `gateway-service` (all admin API traffic; `/api/admin/**` JWT + `ROLE_ADMIN`), GAP IdP (auth, indirect) |

## Responsibilities

- **Operator CRUD UI** — product catalog management (variants + inventory overview), order management (list / detail / status transitions / cancellation, requires admin order endpoints — planned in `order-api.md`), user management (list + detail).
- **Dashboard summary** — KPIs, recent orders, alert surfaces (Grafana cross-links acceptable for deep dives).
- **Admin authentication guard** — every page behind `ROLE_ADMIN`-required session; unauthenticated redirect to `/login`.
- **CSR everywhere** — 모든 페이지 client-rendered behind auth (SEO 불필요). SSR/SSG 사용 안 함.
- **Backend-delegated data ownership** — admin 은 backend service 의 admin endpoints 만 호출, frontend 자체 비즈니스 로직 부재.
- **Per-feature isolation** — `features/<feature>/` 자가 격리, cross-feature import 금지 (architecture.md § Forbidden Dependencies).

## Public surface (pages)

자세한 spec 은 [architecture.md § Key Features](architecture.md) 참조.

| Path | Pattern | Auth | Purpose |
|---|---|---|---|
| `/login` | CSR | public | admin sign-in |
| `/` (dashboard) | CSR | gated + `ROLE_ADMIN` | KPI summary + recent activity |
| `/products` | CSR | gated + `ROLE_ADMIN` | product list (table + filters) |
| `/products/[id]` | CSR | gated + `ROLE_ADMIN` | product detail + edit form |
| `/products/new` | CSR | gated + `ROLE_ADMIN` | product create form |
| `/orders` | CSR | gated + `ROLE_ADMIN` | order list (status filter) |
| `/orders/[id]` | CSR | gated + `ROLE_ADMIN` | order detail + status transition / cancellation |
| `/users` | CSR | gated + `ROLE_ADMIN` | user list |
| `/users/[id]` | CSR | gated + `ROLE_ADMIN` | user detail (read-only v1) |

모든 page 가 `middleware.ts` (또는 layout `<AuthGuard>`) 로 `ROLE_ADMIN` enforce.

## Key invariants

1. **`ROLE_ADMIN` hard fail-close** — admin role 부재 시 hard redirect to `/login` (또는 `/403`). soft degraded UI 미허용.
2. **All API traffic via gateway** — `/api/admin/**` 경유; backend service 직접 호출 금지 (`@repo/api-client` 가 gateway 만 호출).
3. **No business logic in frontend** — 가격 / 재고 / 주문 상태 전이 / 환불 등은 backend 가 단일 진실. frontend 는 UI + 입력 + 전이 trigger 만.
4. **No public page (login 제외)** — 모든 URL 은 인증 require; SEO/public crawler 노출 없음.
5. **Feature isolation** — `features/order-management/` 가 `features/product-management/` 직접 import 금지 (architecture.md § Forbidden Dependencies).
6. **No tokens in localStorage / sessionStorage** — auth session cookie only (web-store 와 동일 원칙).

## Owned Data

- None (server-side). All persistent data fetched from backend admin endpoints via gateway.
- Client-side state: authentication session, UI preferences.

## Published Interfaces

- None (internal admin web application).

## Dependent Systems

- `gateway-service` — all `/api/admin/**` traffic
- `@repo/api-client` (shared HTTP client)
- `@repo/types` (shared TypeScript types)
- `@repo/ui` (shared UI component library)

## Out of scope (v1)

- Customer-facing storefront — owned by `web-store`.
- Backend business logic — owned by backend services.
- SEO or public page rendering — all pages behind auth.
- Multi-tenant admin (다른 tenant ROLE_ADMIN 으로 cross-tenant 운영) — single-tenant 구조 (`PROJECT.md` § Out of Scope, multi-tenant).
- Audit log UI — `audit-heavy` trait 미선언 (`PROJECT.md` § Out of Scope), v2+ 도입 시.
- Seller / marketplace admin — single-seller 구조.
