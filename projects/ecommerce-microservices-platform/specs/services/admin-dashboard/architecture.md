# Service Architecture

## Application
`admin-dashboard`

## Service Type
`frontend-app`

## Architecture Style
`Layered by Feature`

## Why This Architecture
This is an internal operations dashboard where most features follow a similar CRUD pattern: list, detail, create, edit.

The domain complexity is lower than web-store — the primary concern is consistency and developer productivity across many similar screens.

Layered by Feature provides per-feature isolation without the overhead of strict FSD layer rules. Each feature folder is self-contained, and common patterns (tables, forms, filters) are shared across features.

SEO is not required. All pages are client-rendered behind authentication.

## Tech Stack
- Next.js (App Router)
- TypeScript
- Shared packages: `@repo/ui`, `@repo/api-client`, `@repo/types`, `@repo/utils`

## Internal Structure Rule
This application uses a feature-based structure with shared layers.

Recommended internal areas:

- `app/` — Next.js App Router (routes, layouts, pages). Composes features.
- `features/` — One folder per management domain. Each feature owns its components, hooks, API, and types.
- `shared/` — Common components (DataTable, FormField, PageLayout), hooks, utilities, and configuration.

## Feature Structure
Each feature follows this internal layout:

```
features/{feature-name}/
├── components/   # Feature-specific components (list, detail, form, etc.)
├── hooks/        # Data fetching hooks, feature-specific logic
├── api/          # API calls (CRUD operations)
└── types/        # Feature-specific types (if not covered by @repo/types)
```

Key features:
- `product-management` — Product CRUD, variant management, inventory overview
- `order-management` — Order list, detail, status transitions
- `user-management` — User list, detail, role assignment
- `dashboard` — Summary statistics, charts, alerts

## Allowed Dependencies
- `app/` → `features/`, `shared/`
- `features/` → `shared/`
- `shared/` → external libraries only

Dependencies flow **downward only**.

## Forbidden Dependencies
- `features/` must not import from other `features/` directly
- `shared/` must not import from `features/` or `app/`
- `app/` route components must not contain business logic — delegate to features
- No cross-feature component sharing — extract to `shared/` if reuse is needed

## Boundary Rules
- `app/` pages compose feature components and handle routing/layouts
- `features/` own all UI and logic for a single management domain
- `shared/` provides reusable components, hooks, and utilities used by two or more features

## Shared Component Patterns
Admin dashboards share many UI patterns. These belong in `shared/`:

- `DataTable` — Sortable, filterable, paginated table
- `FormField` / `FormSection` — Consistent form layout
- `PageLayout` — Standard page header, breadcrumbs, actions area
- `StatusBadge` — Order/product status display
- `ConfirmDialog` — Destructive action confirmation
- `FilterBar` — Common filter/search controls

Features compose these shared components with feature-specific configuration and data.

## State Management Rules
- Feature-local state stays inside the feature's `hooks/`
- Server state managed via data fetching hooks (TanStack Query or SWR)
- Global state (auth session, sidebar collapse) lives in `shared/`
- Form state managed per-feature with React Hook Form or similar
- Do not duplicate server state in client stores

## Rendering Strategy
- All pages are client-rendered behind authentication
- No SSR/SSG required — admin pages are not indexed
- Use Next.js App Router `'use client'` for all feature components
- Layouts handle auth guard and navigation shell

## Integration Rules
- All API calls must use `@repo/api-client`
- Types shared with backend must come from `@repo/types`
- UI primitives must come from `@repo/ui` or `shared/`
- Do not call backend services directly — all traffic goes through gateway-service

## Testing Expectations
Required emphasis:
- Shared component tests (DataTable, FormField behavior)
- Feature-level component tests (CRUD flows per feature)
- Hook tests (data fetching, mutation logic)
- E2E tests for critical admin flows (product create, order status change)

## Change Rule
Any architectural change to this application must be documented here first before implementation.
