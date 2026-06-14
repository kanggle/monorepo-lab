# web-store — Overview

> 1-pager: responsibilities, public surface (pages), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `web-store` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `frontend-app` |
| Architecture Style | **Feature-Sliced Design** — app/widgets/features/entities/shared, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Next.js (App Router), TypeScript, Tailwind, Toss Payments widget, `@repo/api-client` / `@repo/ui` / `@repo/types` / `@repo/utils` |
| Deployable unit | `web/web-store/` (or `apps/web-store/` per repo layout) |
| Bounded Context | `ecommerce-storefront` (customer-facing) |
| Persistent stores | none — auth session via cookie / next-auth; transient client state for cart + UI prefs |
| Backend dependencies | `gateway-service` (all API traffic), IAM IdP (auth, indirect via gateway) |

## Responsibilities

- **End-to-end customer journey** — product browsing → detail → search → cart → checkout → payment → order history → profile.
- **SSR/SSG for SEO** — product listing/detail pages server-rendered for crawler visibility; non-product pages client-rendered.
- **Search + filters** — query `search-service` via gateway; results page with filter + sort UX.
- **Cart state** — client-side (authenticated 사용자에게만 노출); server-side sync via `cart-service` (planned) or `order-service` cart endpoints.
- **Checkout + payment** — order creation via `order-service`, payment via Toss Payments widget redirect → `payment-service` confirmation callback.
- **Auth flows** — login / signup / refresh via `/api/auth/...` (currently `auth-service-deprecated`; IAM OIDC 전환 진행 중).
- **Profile + order history** — `user-service` 와 `order-service` read-only view.
- **Error / loading boundaries** — Next.js Suspense + `app/error.tsx` 으로 graceful fallback.

## Public surface (pages)

자세한 spec 은 [architecture.md § Key Features](architecture.md) 참조.

| Path | Pattern | Auth | Purpose |
|---|---|---|---|
| `/` | SSG | public | landing + featured products |
| `/products` | SSR | public | catalog listing (filter / sort) |
| `/products/[slug]` | SSG/ISR | public | product detail (PDP) |
| `/search` | SSR | public | search results |
| `/login`, `/signup` | CSR | public | auth flows |
| `/cart` | CSR | gated | cart management (authenticated only) |
| `/checkout` | CSR | gated | order + payment flow |
| `/checkout/confirm` | CSR | gated | Toss Payments redirect callback |
| `/orders` | CSR | gated | order history |
| `/orders/[id]` | CSR | gated | order detail + tracking |
| `/profile` | CSR | gated | profile + addresses + saved cards |

middleware-gated paths 외엔 `/login` 으로 redirect.

## Key invariants

1. **No tokens in localStorage / sessionStorage** — 모든 auth state 는 HttpOnly cookie 또는 next-auth session. XSS-resistant 원칙.
2. **All API traffic via gateway** — `web-store` 는 backend service 에 직접 호출 금지; `@repo/api-client` 가 `gateway-service` 만 호출.
3. **Cart visibility = authenticated only** — anonymous browsing 은 catalog/search/PDP 까지만; cart UI 는 인증된 사용자에게만 노출 (PRD 결정, anonymous cart 미지원 v1).
4. **PG widget 위임** — 카드 정보는 web-store 가 직접 처리 안 함; Toss Payments widget 이 PG 와 직접 통신 후 token 만 회수 (PCI-DSS scope 회피, `PROJECT.md` § Out of Scope).
5. **Feature isolation** — `features/<x>/` 는 자기 public API 만 노출; cross-feature data 는 `entities/` 또는 `widgets/` 합성 경유.
6. **No business logic** — 가격 계산 / 재고 검증 / 할인 적용 등은 모두 backend 가 단일 진실. frontend 는 표시 + 입력 + UX 전이만.

## Owned Data

- None (server-side). All persistent data fetched from backend via gateway.
- Client-side state: cart (UI state), authentication session, UI preferences (locale / theme).

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
