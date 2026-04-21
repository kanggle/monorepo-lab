# Domain Definitions

> **Role**: Routing catalog for agent runtime dispatch. This file lists every valid `domain` value and provides short examples of which service maps to which domain.
>
> **Detailed definitions** (each domain's sub-systems, selection criteria, and narrative) live in [`specs/rules/taxonomy.md`](../../specs/rules/taxonomy.md). When adding a new domain, update **both** files in the same change.
>
> **Source of truth**:
> - Catalog membership ("is `X` a valid domain?") — this file
> - Narrative definition ("what does `X` mean?") — [`specs/rules/taxonomy.md`](../../specs/rules/taxonomy.md)
> - Activation mapping ("what rules does `X` activate?") — [`activation-rules.md`](activation-rules.md)

Choose one primary domain for each service.

---

## Available Domains

### Commerce & Transactions
- ecommerce
- marketplace
- reservation

### Enterprise & Internal Systems
- mes
- erp
- groupware
- accounting-system

### Data & Analytics
- data-platform
- analytics
- bi
- reporting
- ad-platform
- cdp
- dmp

### Content & Community
- community
- sns
- forum
- content-platform

### Media & Streaming
- ott
- media-streaming
- live-streaming

### SaaS & Collaboration
- saas
- collaboration-tool
- crm
- developer-platform

### Financial Services
- fintech
- pg
- banking
- securities

### Logistics & Mobility
- logistics
- wms
- delivery-platform
- fleet-management

### Education
- edtech
- lms
- online-course

### Gaming
- game-platform
- game-backoffice

---

## Rule

Exactly one primary domain must be chosen per project (declared in [`PROJECT.md`](../../PROJECT.md) `domain:` field).

Values not listed above are a **Hard Stop** — see [`CLAUDE.md`](../../CLAUDE.md) Hard Stop Rules.

---

## Example

- order-service → `ecommerce`
- settlement-service → `fintech`
- report-worker → `data-platform`
- factory-ops-service → `mes`

---

## Change Protocol

New domain → add here **and** to [`specs/rules/taxonomy.md`](../../specs/rules/taxonomy.md) **and** to [`activation-rules.md`](activation-rules.md) in the same change. If the new domain needs a detailed rules file, create [`specs/rules/domains/<domain>.md`](../../specs/rules/domains/) in the same change (on-demand principle).
