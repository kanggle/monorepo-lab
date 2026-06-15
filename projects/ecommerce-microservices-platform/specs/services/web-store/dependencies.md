# Service Dependencies

## Service
`web-store`

## Allowed Direct Dependencies
- `@repo/ui` (shared UI component library)
- `@repo/api-client` (axios-based API client — all outbound HTTP)
- `@repo/types` (shared TypeScript types from backend contracts)
- `@repo/utils` (shared utilities)
- `next` (Next.js App Router framework)
- `next-auth` v5 (auth.js) — OIDC session management
- `react`, `react-dom`
- TypeScript strict

## Allowed Service Interactions
- **All API traffic goes through `gateway-service`** — no direct backend service calls.
- `gateway-service` forwards to the appropriate backend service (product-service, order-service, user-service, etc.) based on route.
- `iam-platform` (IAM) — OIDC authorization server for customer sign-in (`ecommerce-web-store-client`; confidential + PKCE).

## Consumes From

| Source | Method | Purpose |
|---|---|---|
| `gateway-service` | HTTP REST (via `@repo/api-client`) | All backend API calls (product catalog, search, cart, order, payment, user profile) |
| `iam-platform` | OIDC (next-auth v5) — `OIDC_ISSUER_URL` | Customer authentication: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/userinfo`, `/.well-known/openid-configuration` |

## Forbidden Dependencies
- direct HTTP calls to any backend service bypassing `gateway-service`
- importing backend service internal code or entity models
- server-side code importing `token-bridge.ts` (client-side only; server-side uses `getWebStoreSession()`)
- cross-feature imports between feature modules (see architecture.md § Forbidden Dependencies)

## Notes
All API calls must use `@repo/api-client`. Types shared with the backend must come from `@repo/types`.
