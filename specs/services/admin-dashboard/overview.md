# Service Overview

## Service
`admin-dashboard`

## Responsibility
Provides an internal operations dashboard for platform administrators to manage products, orders, and users.

## In Scope
- product management (CRUD, variant management, inventory overview)
- order management (list, detail, status transitions, cancellation) — planned; requires admin order endpoints in order-api.md
- user management (list, detail view)
- dashboard summary statistics
- authentication guard (admin-only access)

## Out of Scope
- customer-facing storefront (owned by web-store)
- backend business logic (owned by backend services)
- SEO or public page rendering (all pages are client-rendered behind authentication)

## Owned Data
- None (all data is fetched from backend services via gateway-service)
- client-side state: authentication session, UI preferences

## Published Interfaces
- None (internal admin web application)

## Dependent Systems
- gateway-service (all API traffic routed through gateway)
- @repo/api-client (HTTP client for backend communication)
- @repo/types (shared TypeScript types)
- @repo/ui (shared UI component library)
