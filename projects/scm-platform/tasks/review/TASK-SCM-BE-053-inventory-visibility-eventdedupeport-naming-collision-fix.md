# Task ID

TASK-SCM-BE-053

# Title

inventory-visibility-service: rename local `EventDedupePort` (and its "EventDedupe" vocabulary siblings) to `ProcessedEventPort` / "ProcessedEvent" — fix a name collision with the shared `libs/java-messaging` port

# Status

review

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

# Goal

`libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` is the shared, repo-wide
idempotent-consumer contract (`Outcome process(UUID eventId, String eventType, Runnable work)`) — documented as
"the interface every service shares." `inventory-visibility-service` independently declares a **local** port at
`application/port/outbound/EventDedupePort.java` with the exact same simple name but a completely different shape
(`boolean isDuplicate(UUID)` / `void markProcessed(...)`). Zero consumers in `inventory-visibility-service` import
the shared lib's `EventDedupePort` — the local declaration silently shadows the shared name, which is a genuine
naming collision (same simple name, different package, different contract, in a codebase where `EventDedupePort`
is documented elsewhere as a single canonical shared type).

Sibling services in the same project (`demand-planning-service`, `logistics-service`) already implement the
identical local concept (an `isDuplicate`/`markProcessed` consumer-idempotency port backed by a
`processed_events`-shaped table) under a **different, non-colliding name**: `ProcessedEventPort` /
`ProcessedEventAdapter` / `ProcessedEventJpaEntity` / `ProcessedEventJpaRepository`.

After this task: `inventory-visibility-service` no longer declares any type named `EventDedupePort`, and every
type in the service whose name carries the "EventDedupe" vocabulary for this same idempotency concept is renamed
to the "ProcessedEvent" vocabulary, matching the sibling services' naming. `libs/java-messaging`'s
`EventDedupePort` is completely untouched. Behaviour is unchanged — this is a pure identifier rename (Rename
category, `platform/refactoring-policy.md` § Allowed Refactoring Categories, Low risk).

---

# Scope

## In Scope

Renaming, in `inventory-visibility-service` only, every type/identifier carrying the "EventDedupe" vocabulary to
the "ProcessedEvent" vocabulary (package **names** stay `domain.dedupe` / `domain.dedupe.repository` —
see Implementation Notes for why only the simple class names move, not the packages):

- `application/port/outbound/EventDedupePort.java` → **`ProcessedEventPort`** (interface; same package,
  same method signatures `boolean isDuplicate(UUID)` / `void markProcessed(UUID, String, Instant, String)`).
- `domain/dedupe/EventDedupeRecord.java` → **`ProcessedEventRecord`** (domain class; same package
  `domain.dedupe`).
- `domain/dedupe/repository/EventDedupeRepository.java` → **`ProcessedEventRepository`** (domain port
  interface; same package `domain.dedupe.repository`).
- `adapter/outbound/persistence/adapter/EventDedupeRepositoryImpl.java` → **`ProcessedEventRepositoryImpl`**
  (implements both the renamed domain repository port and the renamed application port — the `RepositoryImpl`
  suffix is kept, per `platform/naming-conventions.md` "Repository (impl) → PascalCase + `RepositoryImpl`";
  it is not renamed to `...Adapter` because, unlike the siblings, this class also implements a domain
  `Repository` interface).
- `adapter/outbound/persistence/jpa/EventDedupeJpaEntity.java` → **`ProcessedEventJpaEntity`** (JPA entity
  class; `@Table(name = "event_dedupe")` value is **unchanged** — see Out of Scope).
- `adapter/outbound/persistence/jpa/EventDedupeJpaRepository.java` → **`ProcessedEventJpaRepository`**
  (Spring Data repository interface).
- Every production call site referencing the above types by name: `config/JpaConfig.java` (javadoc mention),
  `application/service/InventoryVisibilityApplicationService.java` (import, field type, field name
  `eventDedupePort` → `processedEventPort`, javadoc mention).
- Test files that reference any renamed type (imports, field/mock types, field names, or the type name in the
  test class's own identity):
  - `src/test/java/.../domain/dedupe/EventDedupeTest.java` → **`ProcessedEventTest`** (class rename + all
    `EventDedupePort`/`eventDedupePort` references inside).
  - `src/test/java/.../integration/EventDedupeIntegrationTest.java` → **`ProcessedEventIntegrationTest`**
    (class rename + all `EventDedupeJpaRepository`/`dedupeJpa` references inside).
  - `src/test/java/.../integration/AbstractInventoryVisibilityIntegrationTest.java` (import + the shared
    `protected EventDedupeJpaRepository dedupeJpa` field → `protected ProcessedEventJpaRepository
    processedEventJpa`).
  - `src/test/java/.../integration/WmsInventoryReceivedConsumerIntegrationTest.java`,
    `WmsInventoryAdjustedConsumerIntegrationTest.java`, `InventoryNodeAutoCreateIntegrationTest.java` — update
    the `dedupeJpa` field references to `processedEventJpa`.
  - `src/test/java/.../application/ThirdPartyInboundExpectationSinkUseCaseTest.java`,
    `ApplyWarehouseCodeUseCaseTest.java`, `ApplyThirdPartyObservedStockUseCaseTest.java`,
    `ApplyInventoryTransferredUseCaseTest.java` — update the `@Mock EventDedupePort eventDedupePort` field
    (import + declaration) to `@Mock ProcessedEventPort processedEventPort` and every usage.
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — the two places that
  name the actual class `EventDedupeRecord` (Layer Structure diagram, § Domain-object inventory near the end)
  updated to `ProcessedEventRecord`. (See Out of Scope for what does **not** change in specs.)

## Out of Scope

- `libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` — **not touched at
  all**. Zero consumers in `inventory-visibility-service` import it today (confirmed by repo-wide grep before
  filing this task); this task removes the colliding local declaration, it does not wire the shared port in.
- `demand-planning-service` and `logistics-service` — already correct (`ProcessedEventPort` /
  `ProcessedEventAdapter` / `ProcessedEventJpaEntity`), not touched.
- **Database schema.** The Flyway-created table `event_dedupe` (and its `event_id`/`tenant_id`/`processed_at`/
  `source_topic` columns) is **not renamed**. `@Table(name = "event_dedupe")` on the renamed JPA entity keeps
  the literal string `"event_dedupe"`. Renaming a live table is a materially different, higher-risk change
  (new Flyway migration, `V1__init.sql` is the service's baseline migration and is not retroactively edited)
  that the naming-collision finding does not require — the collision is a Java-identifier collision, not a
  schema-naming collision. Every `event_dedupe` (snake_case, DB-table) reference across specs stays as-is.
- **Package names.** `domain.dedupe`, `domain.dedupe.repository`, `adapter.outbound.persistence.jpa`,
  `adapter.outbound.persistence.adapter` are unchanged — only the simple class names inside them move. Neither
  sibling service has an equivalent `domain.dedupe`-shaped package to mirror (they don't have a domain-layer
  repository port for this concept at all — their adapter implements the application port directly), so there
  is no sibling precedent for a package rename, and moving the package is materially larger/riskier than
  mechanically renaming the four-to-six identifiers (`platform/refactoring-policy.md` classifies "Restructure
  Package" as High risk vs. "Rename" as Low risk — mixing the two in one change is also prohibited by
  § Rules "One category at a time").
- General descriptive prose in specs that uses "EventDedupe" as a colloquial idempotency-concept label rather
  than a literal class-name reference (e.g. "EventDedupe idempotency", "EventDedupe 멱등") is left as-is — it
  does not name an actual Java identifier and the underlying `event_dedupe` DB-table term it maps to is
  unchanged. Only the two literal `` `EventDedupeRecord` `` class-name mentions in `architecture.md` are
  updated (see In Scope).
- `specs/contracts/events/inventory-visibility-subscriptions.md`, `data-model.md`, `overview.md` — reviewed;
  they reference the `event_dedupe` **table** (unchanged) or generic "EventDedupe" prose, never the Java type
  name `EventDedupeRecord`/`EventDedupePort` specifically, so no edit needed in those three files.
- Any behavioural change to idempotency semantics, dedup logic, or the T8 flow — none; this is a pure rename.

---

# Acceptance Criteria

- [ ] Baseline recorded before any edit: `inventory-visibility-service` `test` (and `integrationTest` if the
      module has that source set) GREEN, pre-change test count written into the PR body.
- [ ] `grep -r "EventDedupe" projects/scm-platform/apps/inventory-visibility-service/src` returns zero matches
      after the change (excluding the `event_dedupe` DB-table string literal in the JPA `@Table` annotation and
      any unrelated `event_dedupe` mentions carried over unchanged per Out of Scope).
- [ ] `libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` is byte-identical
      before and after (`git diff` shows no changes to this file or its test).
- [ ] `demand-planning-service` and `logistics-service` source trees show zero diff.
- [ ] `ProcessedEventPort`, `ProcessedEventRecord`, `ProcessedEventRepository`, `ProcessedEventRepositoryImpl`,
      `ProcessedEventJpaEntity`, `ProcessedEventJpaRepository` all exist in `inventory-visibility-service` with
      the same method signatures / field mappings as their `EventDedupe*` predecessors (verified by reading the
      post-change source, not inferred).
- [ ] `EventDedupeJpaEntity`'s `@Table(name = "event_dedupe")` value is preserved verbatim on
      `ProcessedEventJpaEntity` (schema untouched).
- [ ] All renamed test classes (`ProcessedEventTest`, `ProcessedEventIntegrationTest`) compile and pass; test
      **count** for `inventory-visibility-service` is identical to the recorded baseline (rename only, no test
      added/removed).
- [ ] `./gradlew :projects:scm-platform:apps:inventory-visibility-service:compileJava
      :projects:scm-platform:apps:inventory-visibility-service:compileTestJava
      :projects:scm-platform:apps:inventory-visibility-service:test` (or the project's equivalent Gradle
      invocation) is GREEN.
- [ ] `architecture.md`'s two literal `EventDedupeRecord` mentions read `ProcessedEventRecord`; no other spec
      file in `inventory-visibility-service`'s spec directory is touched (per Out of Scope).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/naming-conventions.md` (Java § Classes — `Repository (impl) → RepositoryImpl` row; the collision
  this task fixes)
- `platform/refactoring-policy.md` (Allowed Refactoring Categories — `Rename`, Low risk; § Rules "one category
  at a time"; § Prohibited note on production+test changes — see Implementation Notes for why a rename
  necessarily touches both)
- `platform/shared-library-policy.md` (why `libs/java-messaging`'s `EventDedupePort` must not be touched by a
  project-internal task)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (§ Layer Structure —
  Hexagonal `domain/` / `application/` / `adapter/` split; the two `EventDedupeRecord` mentions to update)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- None. No HTTP or event contract changes — `EventDedupePort`/`ProcessedEventPort` is an internal outbound
  port, never exposed on the wire. `specs/contracts/events/inventory-visibility-subscriptions.md` is unchanged
  (see Out of Scope).

---

# Target Service

- `inventory-visibility-service`

---

# Architecture

Follow:

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (Hexagonal; the renamed
  types keep their existing layer placement — `application/port/outbound`, `domain/dedupe`,
  `domain/dedupe/repository`, `adapter/outbound/persistence/jpa`, `adapter/outbound/persistence/adapter`)

---

# Implementation Notes

- This is a same-simple-name-different-package collision, not a duplicate-logic problem — do not attempt to
  make `inventory-visibility-service` consume the shared `libs/java-messaging` `EventDedupePort`; that is a
  separate, larger architectural decision (different method shape: `process(eventId, eventType, Runnable)` vs.
  `isDuplicate`/`markProcessed`) that this task does not make.
- `platform/refactoring-policy.md` § Prohibited lists "Refactoring production code and test code in the same
  change." For a pure identifier **Rename** this is unavoidable and expected: a renamed class/interface breaks
  compilation everywhere it's referenced, including test imports, `@Mock` field types, and field names — the
  prohibition targets smuggling behavioural test changes into a behavior-preserving structural refactor (e.g.
  Extract Method, Reduce Duplication — see `TASK-SCM-BE-051`), not the mechanical, search-and-replace-shaped
  updates a rename requires to keep the build compiling. Keep every test-file edit to identifier substitution
  only — no assertion, mock behaviour, or test-scenario changes.
- Do the rename in this order to keep the tree compiling at each intermediate step (or do it as one atomic
  commit if your tooling supports project-wide symbol rename): innermost first — `EventDedupeJpaEntity` →
  `EventDedupeJpaRepository` → `EventDedupeRecord` → `EventDedupeRepository` (domain port) →
  `EventDedupeRepositoryImpl` → `EventDedupePort` (application port) → call sites → tests.
- `EventDedupeRepositoryImpl` implements **two** interfaces (`EventDedupeRepository` domain port +
  `EventDedupePort` application port) — both must be renamed together since the class implements both.
- File names must match the (renamed) public class/interface name — e.g. `EventDedupeRecord.java` becomes
  `ProcessedEventRecord.java`, not left as `EventDedupeRecord.java` containing `class ProcessedEventRecord`.

---

# Edge Cases

- `EventDedupeRepositoryImpl` implementing two ports — verify both renamed interface names appear in the
  `implements` clause after the change, not just one.
- The `AbstractInventoryVisibilityIntegrationTest` base class's `protected EventDedupeJpaRepository dedupeJpa`
  field is inherited by several IT subclasses (`EventDedupeIntegrationTest`,
  `WmsInventoryReceivedConsumerIntegrationTest`, `WmsInventoryAdjustedConsumerIntegrationTest`,
  `InventoryNodeAutoCreateIntegrationTest`) — renaming the field in the base class requires updating every
  subclass usage in the same change, or the build fails to compile (not merely a test failure).
- `EventDedupeJpaEntity`'s `@Table(name = "event_dedupe")` string literal must survive the class rename
  unchanged — a careless global find/replace of "EventDedupe" → "ProcessedEvent" could accidentally also touch
  the `"event_dedupe"` string literal or the Flyway-created column comments; verify the annotation value by
  reading the post-change file.

---

# Failure Scenarios

- **Collision "fixed" by renaming the shared lib port instead** — renaming
  `libs/java-messaging/.../EventDedupePort.java` would be a monorepo-level, cross-project change requiring a
  root `tasks/ready/` task (per `CLAUDE.md` Task Rules) and would break every other service that legitimately
  depends on the shared contract. Guard: `libs/java-messaging` diff must be empty.
- **Table/column rename smuggled in** — a naive global rename of "EventDedupe" → "ProcessedEvent" (or
  "event_dedupe" → "processed_event") touches the `@Table`/`@Column` string literals, silently requiring a new
  Flyway migration and breaking the running schema against existing deployed state. Guard: `@Table(name =
  "event_dedupe")` value diff must be empty; grep the diff for any `event_dedupe` string-literal change.
- **Partial rename leaves the collision half-fixed** — e.g. renaming only `EventDedupePort` but leaving
  `EventDedupeRecord`/`EventDedupeRepository`/`EventDedupeJpaEntity` as-is reintroduces the inconsistency this
  task exists to remove and leaves "EventDedupe" vocabulary in the codebase. Guard: the zero-`EventDedupe`-match
  grep AC.
- **Test edits smuggled in beyond identifier substitution** — an assertion tweak or mock-behaviour change
  riding along with the rename converts a rename into an unreviewed behaviour change. Guard: test **count**
  must equal the recorded baseline, and a diff review of test files should show only identifier-shaped changes
  (type names, import lines, field names) — no changed literals, assertions, or control flow.
- **Verified only by the implementer's own completion note** — the two-interface `implements` clause, the
  `@Table` literal preservation, and the inherited-field cascade across 4 IT subclasses are structural facts;
  verify against the actual diff, not a report describing it.

---

# Test Requirements

- No new test scenarios required — this is a behaviour-preserving identifier rename. All existing
  `inventory-visibility-service` unit/slice/integration tests must pass with an identical test count to the
  recorded baseline, after updating only the identifiers (type names, imports, field names) the rename
  requires.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (unmodified in behaviour, identifiers updated only; same count as baseline)
- [ ] Contracts unchanged (verified — none exist for this port)
- [ ] `architecture.md`'s two `EventDedupeRecord` mentions updated to `ProcessedEventRecord`; no other spec
      touched
- [ ] Ready for review
