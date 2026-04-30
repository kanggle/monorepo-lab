# Ownership Rule

This document defines ownership responsibilities for services, contracts, and shared assets.

---

# Service Ownership

- Each service owns its own domain rules.
- Each service owns its own persistence model.
- Other services must not redefine another service’s business rules.

---

# Contract Ownership

- HTTP contract ownership belongs to the service exposing the API.
- Event contract ownership belongs to the event producer.
- Consumers may request changes but do not own producer contracts.

---

# Shared Library Ownership

- Shared libraries own only reusable technical capabilities.
- Shared libraries do not own domain rules from any bounded context.

---

# Spec Ownership

- Service-specific rules must be documented under `specs/services/<service>/`.
- Cross-cutting rules must be documented under `platform/`.

---

# Change Rule

Before changing a service contract, shared library, or cross-service flow, confirm the owning source first.