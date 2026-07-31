# Task ID

TASK-BE-571

# Title

ADR-MONO-058 D7 (wms-platform, `EventDedupePort` sub-pattern only) — adopt
`libs/java-messaging.dedupe.EventDedupePort` in `outbound-service`, `inventory-service`,
`inbound-service`, replacing their hand-rolled local interfaces of the same name and shape;
`admin-service`/`notification-service` investigated and scoped out with reasoning

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Dependency Markers

- **선행 없음** — standalone; does not depend on `TASK-BE-567`/`568`/`569`/`570` (the sibling
  ADR-MONO-058 wms adoption tasks filed alongside this one).
- `ADR-MONO-058 § D7` bundles two sub-patterns — `EventDedupePort` (adopted here) and
  `ResilienceClientFactory` (**explicitly NOT this task**, per this task's own scoping instruction — see
  Scope § Out of Scope; `ResilienceClientFactory` adoption for wms, if applicable, is separate future
  work, not bundled into this ticket).

---

# Goal

Close wms-platform's share of the `EventDedupePort` half of `ADR-MONO-058 § D7` (ACCEPTED 2026-07-30). No
new shared code — `libs/java-messaging.dedupe.EventDedupePort` (`Outcome process(UUID eventId, String
eventType, Runnable work)`, `enum Outcome {APPLIED, IGNORED_DUPLICATE, FAILED}`) already exists and is
already a declared `build.gradle` dependency (`implementation project(':libs:java-messaging')`) of
`outbound-service`, `inventory-service`, and `inbound-service` — the textbook adoption-gap shape
`ADR-MONO-058 § D7` describes: "the lib... is being ignored."

**Measured against the tree** (not the ADR's cross-project paraphrase, which lists wms among
erp/ecommerce/fan for "~9 services"):

- **`outbound-service`, `inventory-service`, `inbound-service` — clean, mechanical adoption case.** Each
  declares its own `application/port/out/EventDedupePort.java`, an interface **byte-identical in contract
  shape** to the shared type: `Outcome process(UUID eventId, String eventType, Runnable work)` with the
  same `APPLIED`/`IGNORED_DUPLICATE`/`FAILED` enum. Each has its own `EventDedupeRepositoryImpl`
  implementing it via `INSERT ... ON CONFLICT (event_id) DO NOTHING` against a service-owned dedupe table
  (`OutboundEventDedupe`/`EventDedupeJpaEntity` per service) — the persistence adapter genuinely is
  per-service (retention/tenant scoping, per the shared interface's own javadoc: "the persistence
  implementation lives in each service's adapter layer because the dedupe table's retention policy... is
  service-specific"), but the **interface itself** is pure duplication of the already-shared contract, and
  all three already have the shared module on their classpath unused for this purpose.
- **`admin-service` — different shape, scoped out.** `application/repository/AdminEventDedupeRepository`
  is **not** contract-compatible with `EventDedupePort` — it exposes `tryRecord(eventId, eventType) →
  DedupeOutcome`, a separate `markStale(eventId)` for late-arrival LWW (last-write-wins) correction, plus
  aggregate-counting methods (`countLifetime()`, `maxProcessedAtByEventType(...)`) that power the
  `/operations/projection-status` endpoint. This is a materially richer contract than the shared
  `EventDedupePort`'s single `process(...)` method — `admin-service`'s 4 projection consumers need the
  LWW-late-update step *between* the dedupe check and the mutation, which `EventDedupePort.process(...)`'s
  single `Runnable work` callback cannot express (it applies-once, it does not expose a
  "duplicate-but-stale, update anyway" third outcome). Forcing this onto `EventDedupePort` would mean
  either losing the LWW correction capability or wrapping/extending the shared interface for one
  consumer's shape — both against the Ownership Rule. **Out of scope.**
- **`notification-service` — explicitly opted out of `libs:java-messaging`, scoped out.** Its
  `build.gradle` states, verbatim: *"Shared libs — keep narrow. Do NOT pull libs:java-messaging because
  notification-service uses a service-local outbox table layout (`notification_outbox` with JSONB
  payload) that differs from the libs' base `outbox` table (TEXT payload)."* Its own
  `AlertDedupePort`/`AlertDedupeRepositoryImpl` is contract-compatible in spirit
  (`recordIfAbsent(eventId, sourceTopic, outcome) → Result{INSERTED, DUPLICATE}` + a separate
  `exists(eventId)` check) but not identical, and pulling `libs:java-messaging` in just for
  `EventDedupePort` while the service's own build-file comment explicitly documents *why* the whole
  module is kept off this service's classpath is a decision this task should not make unilaterally —
  **out of scope**, flagged as a separate finding for whoever next touches notification-service's
  dedupe/outbox layer to evaluate (does `EventDedupePort` alone, without the rest of `libs:java-messaging`,
  make sense to pull in? That is a module-boundary question, not a mechanical substitution).

This task closes the clean 3-service case; `admin-service` and `notification-service` are investigated
and explicitly deferred with the reasoning above, not silently skipped.

---

# Scope

## In Scope

- `outbound-service`: delete `application/port/out/EventDedupePort.java`; change
  `adapter/out/persistence/adapter/EventDedupeRepositoryImpl.java` to `implements
  com.example.messaging.dedupe.EventDedupePort` directly; update all consumer call sites (import change
  only, the method signature is identical) — `MasterWarehouseConsumer`, `MasterLotConsumer`,
  `ManualShipConfirmConsumer`, `InventoryReservedConsumer`, `InventoryReleasedConsumer`,
  `InventoryConfirmedConsumer`, `FulfillmentRequestedConsumer` (per this task's investigation grep of
  `outbound-service`'s consumer test files — re-verify the exact consumer list at implementation time).
- `inventory-service`: same conversion — delete `application/port/out/EventDedupePort.java`; change
  `adapter/out/persistence/dedupe/EventDedupeRepositoryImpl.java` to implement the shared interface;
  update consumer call sites (`ShippingConfirmedConsumer`, `PickingRequestedConsumer`, per investigation —
  re-verify).
- `inbound-service`: same conversion — delete `application/port/out/EventDedupePort.java`; change
  `adapter/out/persistence/dedupe/EventDedupeRepositoryImpl.java` to implement the shared interface;
  update consumer call sites.
- Each service's existing `EventDedupeRepositoryImplTest`/`EventDedupePersistenceIntegrationTest` pass
  unmodified in assertion content — only the imported interface changes, the implementation's runtime
  behavior (the `ON CONFLICT DO NOTHING` insert-or-skip logic, the `MANDATORY` transaction propagation,
  the TASK-BE-488 fix it embodies) is byte-unchanged.
- A brief note in each of the 3 services' `dependencies.md`/`architecture.md § Dependencies` (if such a
  section exists and currently omits `libs:java-messaging`'s `EventDedupePort` specifically) confirming
  the interface is now consumed, not merely declared as a build dependency.

## Out of Scope

- **`ResilienceClientFactory`** — the other sub-pattern `ADR-MONO-058 § D7` bundles. This task's own title
  and this repo's task-filing instruction both explicitly exclude it. If wms has `ResilienceClientFactory`
  adoption gaps, that is separate future work, not investigated by this task.
- **`admin-service`** — `AdminEventDedupeRepository`'s richer LWW-aware contract is not compatible with
  `EventDedupePort`'s single-outcome shape; see Goal. Untouched by this task.
- **`notification-service`** — explicit, documented, build-file-level opt-out of `libs:java-messaging`
  predates this task and is not reversed here; see Goal. Untouched.
- **`master-service`** — confirmed to have no dedupe-related classes at all (no Kafka consumers requiring
  event dedupe); not applicable, not touched.
- **`gateway-service`** — no Kafka consumers; not applicable.
- **The dedupe table schema, retention policy, or `INSERT ... ON CONFLICT` mechanism itself** — unchanged;
  this task swaps the interface each `EventDedupeRepositoryImpl` implements, not the implementation logic.
- **Every other project.** erp/ecommerce/fan (per the ADR's audit table) have their own D7 adoption gaps;
  separate tasks, not bundled here (`ADR-MONO-058 § 6`).
- ADR-MONO-058 D1 / D2 / D3 / D4 / D5 / D6 / D8 — separate tasks (`D2`/`D3`/`D4`/`D5` filed alongside this
  one as `TASK-BE-567`/`568`/`569`/`570`).

---

# Acceptance Criteria

- [ ] **AC-1 (local interface deleted, shared one adopted)** — `outbound-service`, `inventory-service`,
      `inbound-service` no longer declare their own `EventDedupePort` interface; repo-wide grep for
      `interface EventDedupePort` under `apps/{outbound,inventory,inbound}-service/src/main` returns zero
      hits; each service's `EventDedupeRepositoryImpl implements com.example.messaging.dedupe.EventDedupePort`.
- [ ] **AC-2 (behavior byte-preserved)** — the `INSERT ... ON CONFLICT (event_id) DO NOTHING` +
      `MANDATORY`-propagation + `Runnable.run()`-on-fresh-insert logic in each `EventDedupeRepositoryImpl`
      is unchanged; existing dedupe-behavior tests (including the TASK-BE-488 regression coverage —
      redelivered event does not re-apply side effects) pass unmodified.
- [ ] **AC-3 (all consumer call sites updated)** — every Kafka consumer in the 3 services that calls
      `EventDedupePort.process(...)` compiles against the shared interface's import
      (`com.example.messaging.dedupe.EventDedupePort`), confirmed by a full-service build, not just the
      dedupe adapter's own module compiling.
- [ ] **AC-4 (`admin-service`/`notification-service` confirmed untouched)** —
      `git diff --numstat -- apps/admin-service apps/notification-service` empty.
- [ ] **AC-5 (baseline parity)** — record each of the 3 converted services' test count before/after. No
      test may disappear. All 3 `:check`/`:test`/`:integrationTest` tasks green; wms CI `Integration`
      lanes (Testcontainers-backed — the dedupe-vs-Kafka-redelivery behavior is exactly the kind of thing
      that needs the real Postgres unique-constraint behavior, not an H2 approximation) green,
      authoritative over local Windows Docker.
- [ ] **AC-6 (findings recorded, not silently dropped)** — the `admin-service`/`notification-service`
      scope-out reasoning from this task's Goal section is carried into the PR body (or a short follow-up
      note) so a future reader does not conclude D7 is "fully closed for wms" when 2 of 5 dedupe-bearing
      services were deliberately left out with cause.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification (`domain: wms`, `traits: [transactional, integration-heavy]` — dedupe/idempotent-
> consumer correctness is exactly the `transactional` trait's territory, T8). Unknown tags are a Hard Stop
> per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D7, § 6 (ACCEPTED)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits from.
- `rules/traits/transactional.md` § T8 (idempotent-consumer pattern this interface implements)
- `.claude/skills/messaging/idempotent-consumer/SKILL.md` — "use the shared port — do not hand-roll,"
  the instruction this task closes the gap against for wms's 3 affected services.
- `specs/services/{outbound,inventory,inbound}-service/idempotency.md` — each service's own dedupe-table
  design documentation; confirm none of them assert the local interface's fully-qualified name in a way
  that would need updating.
- `tasks/done/TASK-BE-488-event-dedupe-save-merge-bug.md` — the fix this task's converted implementations
  must not regress (the `ON CONFLICT DO NOTHING` insert, replacing an earlier `save()`-based
  merge-upsert that silently re-applied redelivered events).
- `libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` — the shared
  type being adopted; read in full (already done during this task's investigation — contract is a 1:1
  match with all 3 services' local interfaces).

# Related Skills

- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

None — internal application-port interface, no HTTP/event wire-format change. `EventDedupePort` governs
consumer-side dedupe bookkeeping only; it does not appear in any `specs/contracts/` file.

---

# Target Service

- `outbound-service`, `inventory-service`, `inbound-service` (wms-platform)
- Investigated and explicitly scoped out: `admin-service`, `notification-service` (see Goal)

---

# Architecture

Follow each target service's own `architecture.md` (all three Hexagonal). `EventDedupePort` stays at its
current position in each service's port/adapter layout — `application/port/out/EventDedupePort.java` is
**deleted** (not moved; the shared type is imported directly from `libs/java-messaging` instead of
re-declared locally), and `adapter/out/persistence/{dedupe,persistence/adapter}/EventDedupeRepositoryImpl.java`
changes only its `implements` clause and import.

---

# Implementation Notes

- Order of work that keeps the diff reviewable: (1) one service end-to-end —
  `inventory-service` (already read in full during this task's investigation) — confirm the shared
  interface's method signature truly matches with zero adaptation needed, delete the local interface,
  swap the `implements` clause, fix imports at every consumer call site, run the full test suite; (2)
  replicate to `outbound-service` and `inbound-service` (both structurally identical per investigation).
- Because the three local interfaces are already byte-identical in contract shape to the shared type, this
  should be a pure delete-and-reimport with **zero** logic changes to `EventDedupeRepositoryImpl`'s method
  bodies — if the implementer finds any behavioral adaptation is needed to compile against the shared
  type, that is a signal the "byte-identical" claim from this task's investigation was wrong for that
  service and needs re-verification before proceeding, not a signal to quietly adapt the shared
  interface's usage in a way that diverges from the other two services.
- Re-verify the exact consumer-class list per service before editing (this task's investigation grepped
  test files, which is a reasonable proxy but not a substitute for grepping `src/main` directly at
  implementation time — a consumer added or removed since this task's filing would not show up in the
  investigation's test-file grep).

---

# Edge Cases

- A consumer that catches exceptions from `EventDedupePort.process(...)`'s `Runnable work` and expects a
  specific exception type to propagate must continue to see the same propagation behavior — the shared
  interface's contract (`work` throws → transaction rolls back, dedupe row included) matches all 3
  services' local interfaces' documented contract, so this should be a non-issue, but confirm no service
  has quietly added exception-translation logic inside its `process(...)` implementation that the shared
  interface's contract doesn't anticipate.
- `Outcome.FAILED` is documented (in both the shared and all 3 local interfaces) as "reserved for
  implementations that catch `work`'s exception (most do not)" — confirm none of the 3 wms
  implementations actually returns `FAILED` today (this task's investigation of the 3
  `EventDedupeRepositoryImpl` bodies found none do — they let `work.run()`'s exception propagate) before
  assuming the adoption is behavior-neutral on this axis.

---

# Failure Scenarios

- **Partial conversion (interface deleted, one consumer left on the old import).** A stale import would
  fail to compile, not silently misbehave — low risk, but AC-3's full-service-build requirement exists to
  make this a build failure caught immediately rather than assumed away by only compiling the dedupe
  module in isolation.
- **Silent adaptation of implementation logic while "just" swapping the interface.** If an implementer
  takes this as an opportunity to also touch the `ON CONFLICT DO NOTHING` logic itself, that reopens
  `TASK-BE-488`'s already-fixed defect class — AC-2 exists to keep this a pure interface swap.
- **Assuming D7 is "done" for wms after this task.** `admin-service` and `notification-service` still
  hand-roll their own dedupe contracts, for documented reasons — AC-6 exists so this is not misread as
  full closure in any future audit or the `TASK-MONO-495` split's own tracking.
- **Reaching for `libs:java-messaging` in `notification-service` anyway.** Out of scope per Goal — that
  service's build-file comment is a deliberate module-boundary decision predating this task; overriding it
  unilaterally here would violate the spirit of "do not silently change what a service already opted out
  of" even though `EventDedupePort` alone might seem harmless to add.
- **Scope leak into the other affected projects.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR;
  erp/ecommerce/fan D7 adoption are separate tasks.

---

# Test Requirements

- All 3 services' existing `EventDedupeRepositoryImplTest`/`EventDedupePersistenceIntegrationTest`
  (`outbound-service` additionally has a dedicated `EventDedupePersistenceIntegrationTest`; confirm
  whether `inbound-service`'s equally-named integration test exists and covers the same ground) pass
  unmodified in assertion content.
- Every consumer test (`MasterWarehouseConsumerTest`, `MasterLotConsumerTest`,
  `ManualShipConfirmConsumerTest`, `InventoryReservedConsumerTest`, `InventoryReleasedConsumerTest`,
  `InventoryConfirmedConsumerTest`, `FulfillmentRequestedConsumerTest`, `ShippingConfirmedConsumerTest`,
  `PickingRequestedConsumerTest`, per investigation — re-verify the exact list) compiles and passes against
  the new import.
- 3 services' `:check`/`:test`/`:integrationTest` green. wms CI `Integration` lanes (Testcontainers) green,
  authoritative — Postgres's real unique-constraint behavior is load-bearing for this pattern's
  correctness, not something an H2 substitute can be trusted to prove
  (`project_testcontainers_docker_desktop_blocker`).

---

# Definition of Done

- [ ] Implementation completed (`outbound`/`inventory`/`inbound`-service local `EventDedupePort` deleted,
      shared type adopted, all consumer call sites updated)
- [ ] Tests passing; per-service before/after counts recorded; no test lost
- [ ] `admin-service`/`notification-service` confirmed byte-unchanged, scope-out reasoning carried into
      the PR body
- [ ] `ResilienceClientFactory` confirmed untouched (separate, unbundled sub-pattern)
- [ ] Ready for review
