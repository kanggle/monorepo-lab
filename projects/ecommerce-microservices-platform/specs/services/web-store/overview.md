# Service Overview

## Application
`web-store`

## Responsibility
Provides the customer-facing storefront for product browsing, search, cart, checkout, and user account management.

## In Scope
- product browsing and detail view (SSR/SSG for SEO)
- product search with filters and sorting
- shopping cart management (client-side state, **인증된 사용자에게만 노출**)
- checkout flow and payment integration (Toss Payments widget)
- user authentication (login, signup, session management)
- user profile and address management
- order history view

## Out of Scope
- backend business logic (owned by backend services)
- admin operations (owned by admin-dashboard)
- SEO for non-product pages (not critical)

## Owned Data
- None (all data is fetched from backend services via gateway-service)
- client-side state: cart, authentication session, UI preferences

## Published Interfaces
- None (customer-facing web application)

## Dependent Systems
- gateway-service (all API traffic routed through gateway)
- @repo/api-client (HTTP client for backend communication)
- @repo/types (shared TypeScript types)
- @repo/ui (shared UI component library)
