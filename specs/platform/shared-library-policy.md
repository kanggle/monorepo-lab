# Shared Library Policy

This document defines what may and may not be placed in shared libraries.

---

# Purpose

Shared libraries exist to provide reusable technical building blocks across services.

They must not become a container for service-specific business logic.

---

# Allowed in Shared Libraries

Shared libraries may contain:

- common technical utilities
- shared web configuration helpers
- common exception primitives
- shared security helpers
- messaging abstractions used by multiple services
- observability helpers
- test support utilities
- common DTO primitives only if they are truly cross-service and stable

---

# Forbidden in Shared Libraries

Shared libraries must not contain:

- service-specific domain logic
- business rules owned by a single service
- direct references to a specific service entity
- repositories tied to one service database
- service-private policies
- service-specific orchestration logic
- code that forces unrelated services to depend on a domain they do not own

---

# Dependency Rule

- Shared libraries may be depended on by services.
- Shared libraries must not depend on service implementation modules.
- Dependencies must remain one-way.
- A shared library must remain reusable by multiple services.

---

# Decision Rule

Before adding code to `libs/`, confirm all of the following:

1. Is it used by more than one service?
2. Is it technical/common rather than domain-owned?
3. Can it remain stable without depending on one service's internal model?
4. Would moving it to `libs/` reduce duplication without increasing coupling?

If any answer is no, keep the code inside the owning service.

---

# Ownership Rule

If a rule or model belongs to one bounded context, it must stay in that service.

Domain ownership is more important than reuse convenience.

---

# Examples

## Good candidates

- common logging setup
- request tracing helpers
- retry utility
- Kafka envelope abstraction
- test fixture utilities
- standardized error response helpers

## Bad candidates

- order discount calculation
- payment approval policy
- user registration rule
- inventory allocation rule
- service-specific entity definitions

---

# Review Rule

Any new shared library or major shared-library expansion must be reviewed against this policy before implementation.