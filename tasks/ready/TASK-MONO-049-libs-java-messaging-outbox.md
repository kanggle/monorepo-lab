# Task ID

TASK-MONO-049

# Title

Extract outbox + publisher + dedupe scaffolding into shared `libs/java-messaging` module

# Status

ready

# Owner

monorepo / shared-library

# Task Tags

- code
- adr
- onboarding

---

# Goal

Move the duplicated **transactional outbox transport scaffolding** (publisher loop, dedupe table contract, event envelope, MDC propagation) out of the per-service `adapter/out/event/` packages into the shared `libs/java-messaging` module. Each service then keeps only its **service-specific** outbox row writer + topic property, while the publishing engine, retry, and dedupe primitives are imported.

After this task is complete, adding a new outbox-using service does not require copy-pasting the publisher loop or rewriting the dedupe contract — it depends on `libs/java-messaging` and configures the topic + outbox row schema.

---

# Scope

## In Scope

- New (or extended) `libs/java-messaging` exports:
  - `OutboxRow` interface + reference JPA mapping (with `event_id`, `event_type`, `aggregate_id`, `payload`, `occurred_at`, `published_at`, `retries`, `last_error`)
  - `OutboxPublisher` (scheduled poll loop with exponential backoff + jitter; drives a `KafkaTemplate`/`KafkaProducer` injection)
  - `EventDedupePort` + reference JPA adapter (`event_dedupe(event_id PK, ...)` 30-day retention)
  - `EventEnvelope` (canonical record)
  - `EventEnvelopeParser` (`@Component`, throws `IllegalArgumentException` on malformed JSON)
  - MDC propagation helpers (`traceId`, `eventId`, `consumerLabel`)
  - Metrics adapter contract for `*.outbox.pending.count`, `*.outbox.lag.seconds`, `*.outbox.publish.failure.total`
- Migration plan for **5 wms backend services** (master/inventory/inbound/outbound/admin-projection-consumer) + **GAP** + **ecommerce** (where applicable):
  - Identify per-service outbox row table; determine whether table schema can converge or stays service-specific
  - Replace per-service publisher with shared one
  - Keep service-specific event payload classes (`*.event.*`) — domain events stay per service
- ADR documenting the boundary: `docs/adr/ADR-MONO-XXX-shared-messaging-scaffolding.md` per `platform/architecture-decision-rule.md`
- Update `platform/shared-library-policy.md` with the new boundary (transport scaffolding allowed; domain events forbidden)
- Update `.claude/skills/messaging/outbox-pattern/SKILL.md` to reference `libs/java-messaging` types

## Out of Scope

- Moving service-specific event payload classes into `libs/` (forbidden by `shared-library-policy.md`)
- Avro / Protobuf migration (separate v2 contract decision)
- Compaction-keyed topic policy (out of v1)
- Cross-service consumer dispatcher pattern beyond Java types (left to per-service consumer code)
- Switching outbox row PK strategy (UUID v7 stays — already standardised)

---

# Acceptance Criteria

- [ ] `libs/java-messaging` exposes `OutboxRow`, `OutboxPublisher`, `EventDedupePort`, `EventEnvelope`, `EventEnvelopeParser`, MDC helpers, metrics contract.
- [ ] All 5 wms backend services depend on the new module and pass `:projects:wms-platform:apps:<service>:test :integrationTest` unchanged.
- [ ] At least one non-wms service (GAP or ecommerce) is migrated to prove the abstraction is project-agnostic.
- [ ] Per-service publisher classes (`MasterOutboxPublisher`, `InventoryOutboxPublisher`, etc.) are deleted; the only per-service code left is the row writer + topic configuration.
- [ ] ADR-MONO-XXX accepted, listed in `docs/adr/INDEX.md`.
- [ ] `shared-library-policy.md` and `messaging/outbox-pattern/SKILL.md` updated.
- [ ] No service-specific content (entity names, topic names, error codes) in any new `libs/java-messaging` file (Hard Stop boundary check).
- [ ] CI Integration matrix all-green for every affected project (cross-project atomicity per `CLAUDE.md` § Cross-Project Changes).

---

# Affected Projects

| Project | Surface |
|---|---|
| `libs/java-messaging` | new module / extended exports |
| `projects/wms-platform/apps/master-service` | publisher migration |
| `projects/wms-platform/apps/inventory-service` | publisher migration |
| `projects/wms-platform/apps/inbound-service` | publisher migration |
| `projects/wms-platform/apps/outbound-service` | publisher migration (post #309–#313) |
| `projects/wms-platform/apps/admin-service` | projection consumer dedupe migration |
| `projects/global-account-platform/apps/auth-service` | publisher migration (verify before commit) |
| `projects/ecommerce-microservices-platform/apps/<service>` | publisher migration (one service to prove abstraction) |
| `docs/adr/` | ADR-MONO-XXX |
| `platform/shared-library-policy.md` | boundary update |
| `.claude/skills/messaging/outbox-pattern/SKILL.md` | references to new types |

---

# Related Specs

- `CLAUDE.md` § Cross-Project Changes (atomic PR rule)
- `platform/architecture-decision-rule.md` (ADR mandate)
- `platform/shared-library-policy.md` (boundary — transport-scaffolding-allowed)
- `rules/traits/transactional.md` (T3 outbox, T8 dedupe)
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`

---

# Architecture

Follow:

- `platform/shared-library-policy.md`
- `platform/architecture-decision-rule.md` (publish ADR before merge)

---

# Implementation Notes

- **Atomic PR**: per `CLAUDE.md` § Cross-Project Changes, the lib change + every affected project's adaptation must land in **one** PR. Do not stagger.
- **ADR first**: spec PR for the ADR may precede the implementation PR, but the implementation PR must reference the merged ADR.
- **Boundary policing**: a Hard Stop check during code review — `grep` the new `libs/java-messaging` files for any `wms` / `gap` / `ecommerce` / specific entity names. None should appear.
- **Schema convergence**: if per-service outbox tables differ structurally, the lib should expose `OutboxRow` as an interface with a default JPA reference impl, allowing services to keep their own table while implementing the contract.
- **D4 churn freeze**: this task touches `libs/`, `platform/`, `.claude/`, `docs/adr/` (4 of 5 freeze paths in ADR-MONO-003 D4). Coordinate with `project_monorepo_template_strategy.md` memory — the next reset will defer D2 again. Acceptable given the cross-project value, but flag in PR description.

---

# Edge Cases

- A service has a custom publisher metric (e.g. `master.outbox.pending.count`) — preserve metric name via per-service tag rather than renaming.
- A service has bulk publish optimisation (multiple rows per Kafka batch) — make the publisher's poll loop pluggable enough to support both single and batch modes.
- A service publishes to >1 topic from one outbox table — the lib must support topic resolution per row (column or strategy bean).
- A consumer's dedupe table has retention different from 30 days — the lib should expose retention as a config knob.

---

# Failure Scenarios

- Migration of one service breaks an IT → revert that service's adaptation, keep others, cut a separate fix-task. The atomic-PR rule in `CLAUDE.md` is for the **happy path**; partial revert during review is acceptable if isolated.
- ADR review surfaces a deeper boundary question (e.g. should domain events also move?) → split the task: keep this one as scaffolding-only; create a follow-up ADR for the deeper question.
- `libs/java-messaging` compile breaks because a service-specific Spring autoconfig leaks → add `@AutoConfiguration` + `@ConditionalOnClass` guards.

---

# Test Requirements

- Unit: each shared lib component (publisher loop, dedupe port, envelope parser) tested independently.
- Integration: every affected project re-runs its full IT suite via CI matrix.
- Cross-project smoke: at least one E2E (gateway-master live-pair, e.g.) confirms outbox emit + consume round-trip end-to-end.

---

# Definition of Done

- [ ] ADR-MONO-XXX accepted
- [ ] `libs/java-messaging` exports complete and tested
- [ ] All affected projects migrated and IT-green
- [ ] `platform/shared-library-policy.md` updated
- [ ] `.claude/skills/messaging/outbox-pattern/SKILL.md` updated
- [ ] CI matrix all-green
- [ ] No service-specific names in shared lib (`grep` clean)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-code wms outbound-service` dry-run (Manual Finding #1). The pattern was first observed across all 5 wms backend services + admin-projection consumer + ecommerce + GAP, but exceeded the per-service refactor scope. This is the cross-project consolidation task that completes the work.

Estimated complexity: HIGH (large blast radius — cross-project, libs change, ADR required). Recommend Opus implementation per `CLAUDE.md` § Recommending Tasks and Dispatching Agents.
