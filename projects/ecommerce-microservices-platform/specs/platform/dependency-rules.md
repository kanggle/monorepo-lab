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
- Frontend applications call backend services only through `gateway-service`.
- Frontend uses `packages/api-client` for HTTP communication.

## Forbidden
- Frontend calling service endpoints directly, bypassing the gateway.
- Frontend importing backend service internal types or modules.

---

# Build and Module Dependencies

## Allowed
- `apps/*` depends on `libs/*` and `packages/*`.
- `packages/*` depends on other `packages/*` if clearly scoped and non-circular.

## Forbidden
- `libs/*` or `packages/*` depends on `apps/*`.
- Circular module dependencies within the monorepo.

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
