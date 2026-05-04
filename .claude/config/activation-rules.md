# Activation Rules

> **Role**: Tells agents "when trait/domain X is declared in [`PROJECT.md`](../../PROJECT.md), activate the following rule categories and skill bundles". This is a **short dispatch table**, not a rule definition.
>
> **Detailed rule content** lives in:
> - [`rules/traits/<trait>.md`](../../rules/traits/) — trait Mandatory Rules, Forbidden Patterns, Required Artifacts, Checklists
> - [`rules/domains/<domain>.md`](../../rules/domains/) — domain bounded contexts, ubiquitous language, mandatory rules, checklists
>
> Each trait and domain entry below MUST link to its detailed rule file if that file exists. On-demand principle: if a trait/domain is listed in the catalog but no rule file exists yet, show `(file to be created when a project declares this trait/domain)` instead of a broken link.

---

## Always Active

Apply all **common rules, common skills, and common agents** regardless of `PROJECT.md` classification:

- [`rules/common.md`](../../rules/common.md) — index of 14 canonical platform rule files
- [`platform/`](../../platform/) — technology baseline (architecture, coding, security, testing, observability, etc.)
- [`.claude/agents/common/`](../agents/common/) — domain-agnostic agents
- [`.claude/skills/`](../skills/) — technical skill tree (backend, cross-cutting, database, frontend, infra, messaging, service-types, testing, etc.)
- [`.claude/commands/`](../commands/) — slash commands (design-api, design-event, implement-task, refactor-code, etc.)

---

## Trait Activation Rules

### transactional
Activate rules for:
- transaction boundary
- idempotency
- concurrency control
- outbox / compensation (saga)
- duplicate prevention
- state machine modeling

→ Detailed rules: [`rules/traits/transactional.md`](../../rules/traits/transactional.md)

---

### regulated
Activate rules for:
- stricter authorization
- audit logging
- secret handling
- PII masking
- approval workflow
- immutable change history where needed

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### data-intensive
Activate rules for:
- large-volume query design
- partitioning / indexing strategy
- ETL / ELT validation
- batch / stream design
- data retention rules

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### real-time
Activate rules for:
- low-latency API design
- timeout / retry / circuit-breaker
- event fanout
- websocket / notification flow if needed

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### read-heavy
Activate rules for:
- cache strategy (multi-layer)
- query optimization
- read model design
- pagination / search optimization
- read replica routing

→ Detailed rules: [`rules/traits/read-heavy.md`](../../rules/traits/read-heavy.md)

---

### integration-heavy
Activate rules for:
- external API client abstraction (adapter layer)
- rate limiting
- retry / backoff with jitter
- circuit breaker
- failure isolation (bulkhead)
- webhook signature / replay protection
- DLQ and reprocessing

→ Detailed rules: [`rules/traits/integration-heavy.md`](../../rules/traits/integration-heavy.md)

---

### internal-system
Activate rules for:
- RBAC
- admin / operator workflow
- scheduled jobs
- operational traceability
- SSO integration

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### multi-tenant
Activate rules for:
- tenant isolation (DB / row / schema level)
- tenant-aware auth / query / schema design
- tenant config resolution
- tenant-safe caching
- per-tenant quota

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### audit-heavy
Activate rules for:
- immutable audit trail
- operator action logging
- state change history
- retention policy
- queryable audit API

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### batch-heavy
Activate rules for:
- chunking / streaming
- retry / restartability
- job scheduling
- idempotent reprocessing
- operational batch metrics
- batch-online contention management

→ Detailed rules: *(file to be created when a project declares this trait)*

---

### content-heavy
Activate rules for:
- structured content schema
- publish state machine
- media storage separation (object store + CDN)
- event-driven search indexing
- multi-layer content cache
- i18n at the model level
- moderation pipeline

→ Detailed rules: [`rules/traits/content-heavy.md`](../../rules/traits/content-heavy.md)

---

## Domain Activation Rules

### ecommerce
Activate:
- order lifecycle rules
- inventory consistency rules
- payment reconciliation rules
- promotion and coupon redemption rules
- review-purchase verification

→ Detailed rules: [`rules/domains/ecommerce.md`](../../rules/domains/ecommerce.md)

---

### marketplace
Activate:
- seller onboarding rules
- order routing rules
- settlement and commission rules
- dispute resolution rules

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### fintech
Activate:
- audit trail rules
- financial transaction rules
- approval workflow rules
- KYC / compliance rules
- stricter failure handling rules

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### data-platform
Activate:
- ETL validation rules
- batch and stream processing rules
- large volume persistence and query rules
- data lineage and catalog rules
- access control on data products

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### mes
Activate:
- factory workflow rules
- equipment integration rules
- operator traceability rules
- quality inspection rules

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### community
Activate:
- feed read-model rules
- moderation rules
- notification fanout rules
- graph relationship rules (if SNS)

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### fan-platform
Activate:
- artist-fan asymmetric publication rules (one-to-many vs community's peer-to-peer)
- post visibility tiers (public / members-only / premium)
- membership-based access control (fail-closed when membership service is unavailable)
- reaction upsert / dedup rules (one reaction per account per post)
- feed-by-following rules (subscribed artists only, status filter)
- artist verification / role separation rules
- moderation rules
- notification fanout rules

→ Detailed rules: [`rules/domains/fan-platform.md`](../../rules/domains/fan-platform.md)

---

### scm
Activate:
- multi-leg supply chain workflow rules (procurement → manufacturing → logistics → settlement)
- supplier integration rules (catalog sync, PO acknowledgment, ASN inbound)
- demand-supply matching rules
- inventory visibility across nodes (warehouses, suppliers, in-transit)
- batch reconciliation rules (settlement, demand planning runs)
- partner SLA / compliance tracking rules

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### logistics
Activate:
- shipment state machine rules
- partner integration rules
- warehouse flow rules
- tracking event rules

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### identity-platform
Activate:
- account type enforcement rules (CONSUMER vs OPERATOR)
- JWT issuance and validation rules
- JWKS key management rules
- SSO scope rules
- social login integration rules
- token lifecycle and rotation rules
- audit logging for all auth events

→ Detailed rules: *(file to be created when a project declares this domain)*

---

### saas
Activate:
- account / credential lifecycle rules (active / locked / dormant / deleted state machine)
- credential–profile physical separation rules
- internal vs public API boundary rules
- admin double-auth + audit trail rules (S5)
- account deletion grace-period + anonymization rules (S6)
- security analytics event rules (login attempt, suspicious activity)

→ Detailed rules: [`rules/domains/saas.md`](../../rules/domains/saas.md)

---

### wms
Activate:
- inbound workflow rules (ASN → inspection → putaway)
- inventory mutation rules (reserve → decrement / release atomically, W1)
- inventory history append-only rules (W2)
- location code uniqueness rules (W3)
- picking two-phase rules (reserve then confirm, W4–W5)
- master data referential integrity rules (W6)

→ Detailed rules: [`rules/domains/wms.md`](../../rules/domains/wms.md)

---

> **Other domains** (reservation, erp, groupware, accounting-system, analytics, bi, reporting, ad-platform, cdp, dmp, sns, forum, content-platform, ott, media-streaming, live-streaming, collaboration-tool, crm, developer-platform, pg, banking, securities, delivery-platform, fleet-management, edtech, lms, online-course, game-platform, game-backoffice) — listed in [`domains.md`](domains.md) catalog; activation mapping and detailed rules files will be added on-demand when a project declares them.

---

## Change Protocol

When adding a new trait or domain:

1. Add entry to [`domains.md`](domains.md) or [`traits.md`](traits.md)
2. Add section to this file with activated rule categories and detailed-rule link
3. Add narrative definition to [`rules/taxonomy.md`](../../rules/taxonomy.md)
4. Create the detailed rule file at [`rules/domains/<domain>.md`](../../rules/domains/) or [`rules/traits/<trait>.md`](../../rules/traits/) **if the project needs it** (otherwise leave as "file to be created when...")

All four steps must happen in the same PR to prevent drift.
