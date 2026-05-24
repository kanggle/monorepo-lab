# Dependency Rules

Defines allowed and forbidden dependencies across services, libraries, and layers.

---

# Service-to-Service Dependencies

Cross-service interaction rules are defined in `service-boundaries.md`.

## Allowed
- HTTP calls via published contracts; event consumption via messaging.

## Forbidden
- Importing another service's internal code, accessing its database, circular sync dependencies.

---

# Service-to-Library Dependencies

## Allowed
- A service depends on shared libraries in `libs/` (Java) or `packages/` (TypeScript).
- A service uses only the interfaces and utilities provided by shared libraries.

## Forbidden
- A shared library depends on a service's internal modules.
- A shared library contains code that forces unrelated services to carry unnecessary dependencies.
- Dependencies between shared libraries unless clearly justified and documented.

Decision rules for shared library placement: `shared-library-policy.md`

---

# Frontend-to-Backend Dependencies

## Allowed
- Frontend applications call backend services only through the project's gateway service (declared in `PROJECT.md`).
- Frontend uses a shared HTTP client abstraction. If a root-level `packages/api-client` exists, it is the canonical client; otherwise each frontend app maintains its own client module under its own directory.

## Forbidden
- Frontend calling service endpoints directly, bypassing the gateway.
- Frontend importing backend service internal types or modules.

---

# Build and Module Dependencies

Path syntax depends on repository shape (single-project vs monorepo — see `repository-structure.md`):

| Shape | App location | Shared library location |
|---|---|---|
| Single-project | `apps/<service>` | `libs/<name>`, `packages/<name>` (if frontend present) |
| Monorepo | `projects/<project>/apps/<service>` | `libs/<name>` at repo root, `packages/<name>` at repo root (when present) |

## Allowed
- Apps depend on shared libraries (`libs/*`, `packages/*`).
- `packages/*` depends on other `packages/*` if clearly scoped and non-circular.

## Forbidden
- `libs/*` or `packages/*` depends on apps.
- Circular module dependencies within the repository.

---

# Dependency Direction Summary

```
External Clients
      ↓
gateway-service
      ↓
  [services]  ←→  (events via messaging)
      ↓
  libs / packages
```

Services are peers. They do not form a hierarchy except through the gateway for external traffic.

---

# Change Rule

Any new cross-service dependency must be declared in a contract before implementation.
Any new shared library dependency must pass the shared-library policy check.
