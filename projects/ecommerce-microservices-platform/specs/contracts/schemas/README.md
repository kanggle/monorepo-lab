# Contract Schemas

Shared schemas used by published HTTP and event contracts.

---

# Allowed

- reusable request/response schema fragments
- common error schema
- pagination schema
- metadata envelope schema
- shared event envelope schema

---

# Not Allowed

- service-internal DTO definitions
- unpublished internal payloads
- database-oriented models
- schemas not referenced by official contracts

---

# Rule

Anything in this directory must be referenced by at least one document in:
- `specs/contracts/http/`
- `specs/contracts/events/`