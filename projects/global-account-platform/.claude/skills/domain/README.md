# Domain-Specific Skills

This directory holds skills that are bound to a specific `domain` declared in [`PROJECT.md`](../../../PROJECT.md).

Currently empty — first-project's domain (`ecommerce`) is served by the existing technology-axis skill tree ([`../backend/`](../backend/), [`../cross-cutting/`](../cross-cutting/), [`../database/`](../database/), [`../frontend/`](../frontend/), [`../infra/`](../infra/), [`../messaging/`](../messaging/), [`../review-checklist/`](../review-checklist/), [`../search/`](../search/), [`../service-types/`](../service-types/), [`../testing/`](../testing/)). This directory exists so domain-specific implementation guides can be added when they appear.

---

## Relationship to the existing skill tree

The existing subdirectories under [`../`](..) form the **technology axis** (backend framework, database, caching, testing, etc.) and apply to every project regardless of domain. They remain the default location for skills that are technology-specific but domain-agnostic (e.g. "Spring Boot transactional boundaries", "Kafka idempotent consumer").

This `domain/` directory is the **business axis**. Add a skill here only when the skill encodes domain knowledge that would not transfer to another domain.

| Axis | Location | Example |
|---|---|---|
| Technology | [`../backend/`](../backend/), [`../cross-cutting/`](../cross-cutting/), [`../database/`](../database/), ... | `backend/springboot-api/SKILL.md` — how to write a Spring Boot REST controller |
| Business | [`./`](./) | `domain/ecommerce/order-lifecycle/SKILL.md` — how to model order state transitions for an ecommerce system |

---

## When to add a skill here

Add a skill under `domain/<domain>/<skill-name>/SKILL.md` only when **all** of the following are true:

- The skill encodes **how to implement** (not **what to enforce**) a concrete, reusable pattern specific to the declared domain. Domain **rules** live in [`../../../rules/domains/<domain>.md`](../../../rules/domains/), not here.
- The skill would be irrelevant or misleading in a project with a different domain.
- The same guidance is not already available in a technology-axis skill under [`../`](..).

Example split:
- `rules/domains/ecommerce.md` → **Rule**: "Order state transitions must be modeled as an immutable state machine" (WHAT)
- `.claude/skills/domain/ecommerce/order-lifecycle/SKILL.md` → **Skill**: Step-by-step guide to implementing that state machine with Spring State Machine (HOW)

---

## Activation

Domain skills are activated when the declared `domain` in [`PROJECT.md`](../../../PROJECT.md) matches the subdirectory name. When bootstrapping a new project with a different domain, remove or replace the ecommerce subtree.

See [`../../config/activation-rules.md`](../../config/activation-rules.md) for the routing table.

---

## Naming convention

- Subdirectory: lowercase domain name matching the value in [`../../config/domains.md`](../../config/domains.md) (e.g. `ecommerce/`, `fintech/`, `logistics/`).
- Skill folder and `SKILL.md` file: follow the same folder-based convention used in the rest of [`.claude/skills/`](../).
