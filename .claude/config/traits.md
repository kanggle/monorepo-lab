# Trait Definitions

> **Role**: Routing catalog for agent runtime dispatch. This file lists every valid `trait` value used in [`PROJECT.md`](../../PROJECT.md) `traits:` declarations.
>
> **Detailed definitions** (each trait's meaning, when to select it, and cross-trait compatibility) live in [`rules/taxonomy.md`](../../rules/taxonomy.md). When adding a new trait, update **both** files in the same change.
>
> **Source of truth**:
> - Catalog membership ("is `X` a valid trait?") — this file
> - Narrative definition ("what does `X` mean?") — [`rules/taxonomy.md`](../../rules/taxonomy.md)
> - Mandatory rules/forbidden patterns ("what must I do if `X` is active?") — [`rules/traits/<trait>.md`](../../rules/traits/)
> - Activation mapping ("what rule categories activate?") — [`activation-rules.md`](activation-rules.md)

Multiple traits may be assigned to a project. Traits activate **additional** rules and skills on top of the common baseline. Traits do not replace domain rules.

---

## Available Traits

- transactional
- regulated
- data-intensive
- real-time
- read-heavy
- integration-heavy
- internal-system
- multi-tenant
- audit-heavy
- batch-heavy
- content-heavy

---

## Rule

Traits activate additional rules and skills on top of the common baseline.
Traits do not replace domain rules.

Values not listed above are a **Hard Stop** — see [`CLAUDE.md`](../../CLAUDE.md).

---

## Example (typical trait combinations per domain)

- `ecommerce` (order-handling service)
  - `transactional`
  - `integration-heavy`

- `fintech` (payment-handling service)
  - `transactional`
  - `regulated`
  - `audit-heavy`
  - `real-time`

- ad reporting worker
  - `data-intensive`
  - `batch-heavy`
  - `integration-heavy`

---

## Change Protocol

New trait → add here **and** to [`rules/taxonomy.md`](../../rules/taxonomy.md) **and** to [`activation-rules.md`](activation-rules.md) in the same change. If the new trait needs a detailed rules file, create [`rules/traits/<trait>.md`](../../rules/traits/) in the same change (on-demand principle).
