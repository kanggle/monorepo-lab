# Service Architecture

## Application
`web-store`

## Service Type
`frontend-app`

## Architecture Style
`Feature-Sliced Design`

## Why This Architecture
This is a customer-facing storefront with diverse, complex features: product browsing, search, cart, checkout, payment, and user account.

Each feature has distinct state management, API calls, and UI components that must be independently developable and testable.

Feature-Sliced Design provides strict boundaries between features and clear dependency directions, preventing unintended coupling as the app grows.

SEO, performance (SSR/SSG), and user experience are critical — feature isolation helps optimize each area independently.

## Tech Stack
- Next.js (App Router)
- TypeScript
- Shared packages: `@repo/ui`, `@repo/api-client`, `@repo/types`, `@repo/utils`

## Internal Structure Rule
This application uses Feature-Sliced Design adapted for Next.js App Router.

Recommended internal layers (top = most composed, bottom = most shared):

- `app/` — Next.js App Router (routes, layouts, pages). Composes widgets and features.
- `widgets/` — Composite UI blocks that combine multiple features (e.g., ProductCardWithCart, HeaderWithAuth).
- `features/` — Self-contained feature modules. Each feature owns its own UI, model (state/types), API, and lib (logic).
- `entities/` — Business entities shared across features (e.g., Product, User, Order). Owns type definitions, base UI components, and API hooks.
- `shared/` — Framework-agnostic utilities, common UI primitives, configuration, and constants.

## Feature Structure
Each feature follows this internal layout:

```
features/{feature-name}/
├── ui/          # Feature-specific components
├── model/       # State management, types, stores
├── api/         # API calls and data fetching hooks
└── lib/         # Business logic, helpers, transformations
```

Key features:
- `product` — Product listing, detail, filtering
- `cart` — Cart state, add/remove/update items
- `checkout` — Order creation, payment flow
- `search` — Search query, filters, results
- `auth` — Login, signup, session management
- `user` — Profile, address, order history

## Allowed Dependencies
- `app/` → `widgets/`, `features/`, `entities/`, `shared/`
- `widgets/` → `features/`, `entities/`, `shared/`
- `features/` → `entities/`, `shared/`
- `entities/` → `shared/`
- `shared/` → external libraries only

Dependencies flow **downward only**. Upper layers may import from lower layers, never the reverse.

## Forbidden Dependencies
- `features/` must not import from other `features/` directly
- `entities/` must not import from `features/` or `widgets/`
- `shared/` must not import from any application layer
- `app/` route components must not contain business logic — delegate to features
- No cross-feature state sharing — use entities or shared event patterns instead

## Boundary Rules
- `app/` pages compose features and widgets, handle routing and layouts
- `widgets/` combine features into reusable page sections
- `features/` own all logic for a single user-facing capability
- `entities/` provide shared domain types, base components, and API primitives
- `shared/` provides only framework-agnostic utilities and UI primitives

## State Management Rules
- Feature-local state stays inside the feature's `model/`
- Cross-feature shared state (e.g., auth session, cart count) uses entities or a global store at `shared/` level
- Server state (API data) is managed via data fetching hooks in `api/` (e.g., TanStack Query, SWR, or Next.js Server Components)
- Do not duplicate server state in client stores

## Rendering Strategy
- Product listing and detail pages: SSR or SSG for SEO
- Cart and checkout: Client-side rendering (CSR) — user-specific, dynamic state
- Search results: SSR with client-side filter updates
- Follow Next.js App Router conventions: Server Components by default, `'use client'` only when needed

## Integration Rules
- All API calls must use `@repo/api-client`
- Types shared with backend must come from `@repo/types`
- UI primitives must come from `@repo/ui` or `shared/`
- Do not call backend services directly — all traffic goes through gateway-service

## Authentication: GAP OIDC (post TASK-FE-067)
- Auth library: `next-auth` v5 (auth.js) configured in `src/shared/auth/auth.ts`.
- Identity provider: `global-account-platform` (GAP) — `OIDC_ISSUER_URL` (default `http://gap.local`) hosts the discovery doc + `/oauth2/authorize` + `/oauth2/token` endpoints.
- Client: `ecommerce-web-store-client` (registered in GAP V0012 seed). Confidential client + PKCE.
- Scope: `openid profile email tenant.read ecommerce.consumer`. Backend (gateway-service) asserts `tenant_id=ecommerce` via the JWT claim.
- Account-type guard: only `account_type=CONSUMER` may sign in. An `OPERATOR` who completes the GAP flow is rejected by the `session()` callback (returned session has no `accountId`) and bounced to `/login?error=account_type_mismatch`.
- Bearer token wiring: NextAuth keeps the GAP-issued access token in an HttpOnly JWT cookie. The client-side `AuthProvider` reads `useSession().data.accessToken` and pushes it into `src/shared/auth/token-bridge.ts`, which `@repo/api-client`'s axios interceptor reads synchronously to attach `Authorization: Bearer ...` on every outbound request.
- Server-side (RSC, route handlers, server actions): use `getWebStoreSession()` from `src/shared/auth/session.ts`. Do not import the bridge from server code.
- 401 handling: NextAuth does not auto-refresh GAP tokens. On 401 the api-client's `onAuthError` clears the bridge and redirects to `/api/auth/signin/gap`, re-running the OIDC flow.
- Public paths (no auth required): `/`, `/products/*`, `/login`, `/signup` (redirects into GAP), `/api/auth/*`. Everything else is gated by `src/middleware.ts`.

## Testing Expectations
Required emphasis:
- Feature-level component tests (each feature's UI)
- Hook and state logic tests (each feature's model)
- Integration tests for critical user flows (cart → checkout)
- E2E tests for core paths (search → product → cart → checkout)

## Change Rule
Any architectural change to this application must be documented here first before implementation.
