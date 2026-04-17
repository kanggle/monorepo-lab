# Service Type: Frontend App

Normative requirements for any service whose `Service Type` is `frontend-app`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `frontend-app` service is a Next.js application that serves UI to end users (browser, mobile web). It is the primary surface that users interact with.

Services in this monorepo: `web-store` (customer-facing storefront), `admin-dashboard` (internal operator console).

---

# Mandatory Requirements

## Architecture Pattern
- Every `frontend-app` MUST declare its architecture style (`Feature-Sliced Design` or `Layered by Feature`) in `architecture.md`
- Implementation MUST follow the matching skill (`frontend/architecture/feature-sliced-design.md` or `frontend/architecture/layered-by-feature.md`)

## API Consumption
- All backend calls go through a typed API client under `shared/api/`
- Types are derived from `specs/contracts/http/<service>-api.md` (or generated from OpenAPI if available)
- Direct `fetch()` calls in components are forbidden — go through the client

## Authentication
- Tokens stored in HttpOnly cookies only — never localStorage / sessionStorage
- Refresh handled by a server route, not client JavaScript
- See `frontend/auth-client.md`

## Server vs Client Components
- Default to server components
- Push `'use client'` boundary as deep in the tree as possible
- Data fetching happens in server components when possible

## Performance Budget
- First-load JS budget per route declared in `architecture.md`
- Default budget: 180 KB gzipped (landing routes), 250 KB (app routes)
- CI fails on budget regression
- See `frontend/bundling-perf.md`

## Accessibility
- WCAG 2.1 AA compliance required for all production routes
- Component tests include axe-core check (`frontend/component-library.md`)
- Lighthouse a11y score >= 90 in CI

## Observability
- web-vitals reported to the observability backend (LCP, INP, CLS, TTFB)
- Client errors captured (Sentry or equivalent)
- Per-route latency dashboards under `infra/grafana/dashboards/<app>-overview.json`

## Environment Variables
- Public env vars prefixed with `NEXT_PUBLIC_`
- Server-only secrets injected at runtime, not build time
- Build artifacts MUST work across environments without rebuild

---

# Allowed Patterns

- Server components for data fetching
- Server actions for forms colocated with server components (`frontend/server-actions.md`)
- React Query for client-side mutations and shared cache
- HttpOnly cookies for auth
- shadcn/ui-style component primitives in `shared/ui/`
- Streaming with `<Suspense>` for slow data

---

# Forbidden Patterns

- JWT in localStorage or sessionStorage
- Direct `fetch()` to backend services bypassing the API client
- Client-side environment variables for secrets
- Mixing FSD and Layered styles in the same app
- Importing from another app's `features/` (apps share only via `shared/` packages or `@repo/`)

---

# Testing Requirements

- Component tests with Vitest + Testing Library for every reusable component
- Hook tests for every query/mutation hook
- a11y tests with axe-core for primitive components
- E2E happy-path tests for critical user journeys (login, checkout, key admin flow)

---

# Default Skill Set

When implementing a `frontend-app` feature:

`frontend/implementation-workflow`, matched architecture skill, `frontend/api-client`, `frontend/state-management`, `frontend/form-handling`, `frontend/loading-error-handling`, `frontend/component-library`, `frontend/testing-frontend`, `frontend/bundling-perf`, `frontend/auth-client`, `cross-cutting/observability-setup`, `service-types/frontend-app-setup`

---

# Acceptance for a New Frontend App

- [ ] `Service Type: frontend-app` declared in `architecture.md`
- [ ] Architecture pattern declared and followed
- [ ] Typed API client wired
- [ ] HttpOnly cookie auth flow
- [ ] Performance budget configured in CI
- [ ] a11y check enabled in CI
- [ ] web-vitals reporting wired
- [ ] At least one E2E test for the critical user journey
