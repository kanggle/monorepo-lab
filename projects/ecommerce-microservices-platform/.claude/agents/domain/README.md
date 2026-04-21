# Domain-Specific Agents

This directory holds agents that are bound to a specific `domain` declared in [`PROJECT.md`](../../../PROJECT.md).

Currently empty — ecommerce-microservices-platform's domain (`ecommerce`) has no domain-specific agents yet; the 13 agents under [`../common/`](../common/) cover its needs. This directory exists so the layout is ready when specialized agents become necessary.

---

## When to add an agent here

Place a file under `domain/<domain>/<agent-name>.md` only when **all** of the following are true:

- The agent's logic is meaningful **only** within that domain.
  Examples: `domain/fintech/audit-architect.md`, `domain/ecommerce/order-lifecycle-architect.md`, `domain/logistics/fulfillment-architect.md`.
- The agent would not make sense for other domain projects (i.e. you would delete it when bootstrapping a non-matching project).
- The same capability is not already covered by a common agent ([`../common/`](../common/)).

Domain-agnostic agents (architect, backend-engineer, api-designer, database-designer, event-architect, code-reviewer, devops-engineer, qa-engineer, refactoring-engineer, coordinator, data-engineer, ml-engineer, frontend-engineer) belong in [`../common/`](../common/), not here.

---

## Structure

```
.claude/agents/
├── common/                 ← domain-agnostic agents (loaded always)
│   ├── architect.md
│   ├── backend-engineer.md
│   └── ...
└── domain/                 ← this directory
    ├── ecommerce/
    │   └── <agent>.md      ← only when needed
    ├── fintech/
    │   └── <agent>.md
    └── ...
```

---

## Activation

Domain-specific agents are activated when the declared `domain` in [`PROJECT.md`](../../../PROJECT.md) matches the subdirectory name. When bootstrapping a new project with `domain: fintech`, the `ecommerce/` subdirectory contents become dead weight and should be removed from the new project copy.

See [`../../config/activation-rules.md`](../../config/activation-rules.md) for the routing table and [`../../../specs/rules/README.md`](../../../specs/rules/README.md) for the overall rule resolution order.

---

## Naming convention

- Subdirectory: lowercase domain name matching the value in [`../../config/domains.md`](../../config/domains.md) (e.g. `ecommerce/`, `fintech/`, `data-platform/`).
- File: `<role>-<focus>.md` (e.g. `order-lifecycle-architect.md`, `audit-trail-reviewer.md`). Keep it descriptive but short.
