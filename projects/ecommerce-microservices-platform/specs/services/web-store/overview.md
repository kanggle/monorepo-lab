# web-store — Overview

> 1-pager: responsibilities, public surface (pages), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `web-store` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `frontend-app` |
| Architecture Style | **Feature-Sliced Design** — app/widgets/features/entities/shared, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Next.js (App Router), TypeScript, CSS-variable design tokens + CSS Modules (**no Tailwind**), Toss Payments widget, `@repo/api-client` / `@repo/ui` / `@repo/types` / `@repo/utils` |
| Deployable unit | `apps/web-store/` |
| Bounded Context | `ecommerce-storefront` (customer-facing) |
| Persistent stores | none — auth session via cookie / next-auth; transient client state for cart + UI prefs |
| Backend dependencies | `gateway-service` (all backend API traffic); IAM IdP (auth **directly** via OIDC — discovery / `/oauth2/*`, not through gateway) |

## Responsibilities

- **End-to-end customer journey** — product browsing → detail → search → cart → checkout → payment → order history → profile.
- **SSG/ISR for SEO** — product listing/detail pages statically generated + revalidated (`revalidate=60`) for crawler visibility; account / checkout pages client-rendered.
- **Search + filters** — query `search-service` via gateway; results render on the catalog page `/products?query=…` (filter + sort via query params — there is no separate `/search` route).
- **Cart state** — client-side (authenticated 사용자에게만 노출); server-side sync via `cart-service` (planned) or `order-service` cart endpoints.
- **Checkout + payment** — order creation via `order-service`, payment via Toss Payments widget redirect → `payment-service` confirmation callback.
- **Auth flows** — login / signup / refresh via `/api/auth/...` (IAM OIDC via NextAuth v5; auth-service decommissioned 2026-05-04 — TASK-BE-132 + TASK-FE-067 done).
- **Profile + order history** — `user-service` 와 `order-service` read-only view.
- **Error / loading boundaries** — Next.js Suspense + `app/error.tsx` 으로 graceful fallback.

## Public surface (pages)

자세한 spec 은 [architecture.md § Feature Structure](architecture.md) 참조.

| Path | Pattern | Auth | Purpose |
|---|---|---|---|
| `/` | ISR (`revalidate=60`) | public | landing + featured products |
| `/products` | ISR (`revalidate=60`) | public | catalog listing + **search results** (`?query=…`, filter / sort) |
| `/products/[id]` | ISR (`revalidate=60`) | public | product detail (PDP) |
| `/login` | CSR | public | login (delegates to IAM `signIn('iam')`) |
| `/signup` | redirect | public | legacy alias → `/api/auth/signin/iam` |
| `/cart` | CSR | gated | cart management (authenticated only) |
| `/checkout` | CSR | gated | order + payment entry |
| `/checkout/payment` | CSR | gated | Toss Payments widget |
| `/checkout/payment/success`, `/checkout/payment/fail` | CSR | gated | PG redirect callbacks |
| `/checkout/complete` | CSR | gated | order completion |
| `/my/orders`, `/my/orders/[id]` | CSR | gated | order history + detail |
| `/my/profile` | CSR | gated | profile |
| `/my/addresses` | CSR | gated | address book |
| `/my/coupons` | CSR | gated | issued coupons |
| `/my/wishlist` | CSR | gated | wishlist |
| `/my/reviews` | CSR | gated | my product reviews |
| `/my/notifications`, `/my/notifications/settings` | CSR | gated | notification inbox + preferences (incl. Web Push opt-in) |

The customer account area lives under `/my/*`. `/orders` and `/orders/[id]` also
exist as legacy top-level aliases of `/my/orders*`.

Public paths (no auth): `/`, `/products/*`, `/login`, `/signup`, `/api/auth/*`
(architecture.md § Authentication). Everything else is middleware-gated →
redirect to `/login?from=<intended-path>`.

## Key invariants

1. **No tokens in localStorage / sessionStorage** — 모든 auth state 는 HttpOnly cookie 또는 next-auth session. XSS-resistant 원칙.
2. **All API traffic via gateway** — `web-store` 는 backend service 에 직접 호출 금지; `@repo/api-client` 가 `gateway-service` 만 호출.
3. **Cart visibility = authenticated only** — anonymous browsing 은 catalog/search/PDP 까지만; cart UI 는 인증된 사용자에게만 노출 (PRD 결정, anonymous cart 미지원 v1).
4. **PG widget 위임** — 카드 정보는 web-store 가 직접 처리 안 함; Toss Payments widget 이 PG 와 직접 통신 후 token 만 회수 (PCI-DSS scope 회피, `PROJECT.md` § Out of Scope).
5. **Feature isolation** — `features/<x>/` 는 자기 public API 만 노출; cross-feature data 는 `entities/` 또는 `widgets/` 합성 경유.
6. **No business logic** — 가격 계산 / 재고 검증 / 할인 적용 등은 모두 backend 가 단일 진실. frontend 는 표시 + 입력 + UX 전이만.

## Owned Data

- None (server-side). All persistent data fetched from backend via gateway.
- Client-side state: cart (UI state), authentication session, UI preferences — including the light/dark **theme** choice persisted in `localStorage` (`webstore-theme`); see [architecture.md § Styling & Theming](architecture.md).

## Published Interfaces

- None (customer-facing web application; no API surface to other services).

## Dependent Systems

- `gateway-service` — all backend API traffic
- `@repo/api-client` (shared HTTP client)
- `@repo/types` (shared TypeScript types)
- `@repo/ui` (shared UI component library)
- Toss Payments widget (PG, direct browser-to-PG channel)

## Out of scope (v1)

- Backend business logic — owned by backend services.
- Admin operations — owned by `platform-console` (hub).
- SEO for non-product pages — not critical for v1.
- Marketplace / seller UI — single-seller 구조, `PROJECT.md` § Out of Scope (marketplace).
- Native mobile app — web-only v1; React Native / Flutter v2+.
- Anonymous (logged-out) cart — v1 미지원, login 이후만 cart 진입.
