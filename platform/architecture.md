# Platform Architecture

This document defines the overall system structure.
All implementation tasks must read this document before proceeding.

---

# System Style

Microservices architecture deployed on Kubernetes.
Services communicate via HTTP (synchronous) and events (asynchronous).
All external HTTP traffic enters through a single API gateway.

---

# Services

| Service | Style | Responsibility |
|---|---|---|
| `gateway-service` | Layered | External traffic routing, auth delegation, rate limiting |
| `auth-service` | Layered | Authentication, token issuance and refresh |
| `user-service` | Layered | User profile management |
| `product-service` | DDD | Product catalog, variants, inventory, pricing |
| `order-service` | DDD | Order lifecycle, aggregates, domain events |
| `payment-service` | Hexagonal | Payment processing, external provider integration |
| `search-service` | Hexagonal | Product search index, search query API |
| `batch-worker` | Layered | Scheduled and batch processing |

## Frontend Applications

| Application | Style | Responsibility |
|---|---|---|
| `web-store` | Feature-Sliced Design | Customer-facing storefront (product browsing, cart, checkout) |
| `admin-dashboard` | Layered by Feature | Internal operations dashboard (product/order/user management) |

Frontend applications are built with Next.js (App Router) and follow the same rule:

Each application declares its own internal architecture in:
`specs/services/<service>/architecture.md`

Do not assume all services share the same internal structure.

Each service's architecture is declared in `specs/services/<service>/architecture.md`.

---

# Communication Rules

## Synchronous (HTTP)
- External clients communicate through `gateway-service` only.
- Services may call other services via HTTP only through published contracts.
- Direct service-to-service HTTP calls must be declared in `specs/contracts/http/`.

## Asynchronous (Events)
- Services publish and consume events through a shared messaging infrastructure.
- Event contracts must be declared in `specs/contracts/events/` before use.
- See `event-driven-policy.md` for event design rules.

For the full dependency direction diagram and forbidden dependency rules, see `dependency-rules.md`.

---

# Shared Libraries

## Java (`libs/`)
| Library | Purpose |
|---|---|
| `java-common` | Common utilities |
| `java-messaging` | Messaging abstractions |
| `java-observability` | Tracing, metrics, logging helpers |
| `java-security` | Security helpers |
| `java-test-support` | Test utilities |
| `java-web` | Web configuration helpers |

## TypeScript (`packages/`)
| Package | Purpose |
|---|---|
| `api-client` | HTTP client for frontend services |
| `types` | Shared TypeScript types |
| `ui` | Shared UI components |
| `utils` | Common utility functions |
| `eslint-config` | Shared ESLint rules |
| `tsconfig` | Shared TypeScript config |

Shared library placement rules: `shared-library-policy.md`

---

# Repository Structure

Monorepo managed with PNPM workspaces and Turborepo.

```
apps/        # Services (deployable units)
libs/        # Shared Java libraries
packages/    # Shared TypeScript packages
specs/       # Official specifications (source of truth)
tasks/       # Implementation task lifecycle
.claude/     # AI agent guidance
knowledge/   # Design references
docs/        # Human-oriented documentation
```

Full structure rules: `repository-structure.md`

---

# Deployment

- Each service is containerized with Docker.
- Deployed to Kubernetes.
- Infrastructure managed with Terraform.
- CI/CD via GitHub Actions.

Deployment rules: `deployment-policy.md`

---

# Key Constraints

- Services must not share databases.
- Services must not import each other's internal code.
- Shared logic belongs in `libs/` or `packages/` only if it passes the shared-library policy check.
- All API and event changes require contract updates before implementation.
- Each service's architecture is fixed by its own `architecture.md` and must not be changed without updating that document first.
