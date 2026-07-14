# TASK-MONO-406 — `libs/java-messaging` still ships the v1 `ProcessedEvent*` dedupe entity that ADR-MONO-004 forbids; its auto-config's `@EnableJpaRepositories` has broken two services' boot and ten now exclude it by name

**Status:** ready

**Type:** TASK-MONO (shared library — `libs/java-messaging`; atomic cross-project)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (shared-library contract change rippling into 6 projects / ~14 services; the deletion is compiler-guarded but the 4 caller migrations touch money-adjacent consume-path dedupe)

---

## Goal

`libs/java-messaging` ships a JPA dedupe entity, its repository, and an
`@AutoConfiguration` that installs `@EnableJpaRepositories` + `@EntityScan` into
**every consumer's application context**. Three things are true about this at once:

1. **The repo's own policy already forbids it.**
   [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) §
   *Messaging-specific guidance (per ADR-MONO-004)* puts **"Per-service dedupe table
   entities, retention-cleanup schedulers, tenant-scoping logic"** in the **Forbidden**
   column, and names the Allowed replacement: **`EventDedupePort` interface + `Outcome`
   enum**. That port **already exists** (`libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java`).

2. **It was only ever meant to be temporary.** ADR-MONO-004 (line 83, repeated at 159)
   retained it explicitly as a v1 back-compat shim:

   > "The existing classes (`OutboxPublisher` v1 …, `OutboxWriter`, **`ProcessedEventJpaEntity`**,
   > `BaseEventPublisher`) are **retained** so master-service and other **v1 callers** keep
   > working without migration."

   `TASK-MONO-312` then completed the v1 → v2 sweep and deleted `OutboxWriter` /
   `OutboxPublisher` — but **left the `ProcessedEvent*` trio and the `@EnableJpaRepositories`
   that carries it.** The lib's own javadoc records the leftover state:

   > `OutboxAutoConfiguration`: "The class itself is **retained because numerous services
   > exclude it by name** via `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)`."

   The auto-configuration now exists **in order to be excluded**. That is circular, and it
   is the whole finding.

3. **It has already broken production boots twice**, and both times was fixed
   *service-locally* rather than at the library:

   | | service | fix applied |
   |---|---|---|
   | `TASK-BE-432` | wms `inventory-service` | `exclude = OutboxAutoConfiguration.class` |
   | `TASK-BE-461` | ecommerce `settlement-service` | `exclude = OutboxAutoConfiguration.class` |

   ```
   org.springframework.beans.factory.support.BeanDefinitionOverrideException:
   Invalid bean definition with name 'processedEventJpaRepository' defined in
   com.example.messaging.outbox.ProcessedEventJpaRepository defined in @EnableJpaRepositories declared on OutboxJpaConfig:
   Cannot register bean definition [...] since there is already
   [... com.example.settlement.infrastructure.persistence.ProcessedEventJpaRepository
   defined in @EnableJpaRepositories declared on SettlementServiceApplication] bound.
   ```

   BE-461 also records **why it stayed invisible**: *"the Docker-free `:check` never boots
   the full context (ITs are `@Tag("integration")`-excluded), which is why it stayed latent
   until MONO-319 added a Testcontainers `@SpringBootTest` lane."* **No compiler and no unit
   test can see this defect.**

**Finish the retirement ADR-MONO-004 started and MONO-312 left half-done, and leave a guard
so a shared library cannot lay this trap again.**

> **This is not an open architecture decision.** ADR-MONO-004 is ACCEPTED and its Forbidden
> column already answers "where does the dedupe entity live" — *in the service*. Six services
> are already there. This task brings the four stragglers over and deletes the shim. No new
> ADR is required; the missing artefact is a **rule** (see AC-4) and a **guard** (AC-5).

---

## The two app-wide side effects (why this is a library defect, not a naming annoyance)

`OutboxAutoConfiguration` (`@AutoConfiguration`) `@Import`s `OutboxJpaConfig`, which is:

```java
@Configuration
@EntityScan(basePackageClasses = {ProcessedEventJpaEntity.class})
@EnableJpaRepositories(
        basePackageClasses = {ProcessedEventJpaRepository.class},
        enableDefaultTransactions = false
)
public class OutboxJpaConfig {}
```

**(1) Suppression.** Any explicit `@EnableJpaRepositories` anywhere in the context makes
Spring Boot's `JpaRepositoriesAutoConfiguration` back off **for the whole application**. So
every consumer of this library must hand-declare its own narrowing `@EnableJpaRepositories`
(+ `@EntityScan`) or **its own repositories silently stop being scanned**. The library's
javadoc *documents this obligation on the consumer* instead of removing it:

> "IMPORTANT: … the consuming service's own repositories are NO LONGER auto-scanned … Every
> service that depends on java-messaging **MUST declare its own `@EnableJpaRepositories`**…"

**(2) Collision.** The lib's `ProcessedEventJpaRepository` claims the default bean name
`processedEventJpaRepository` — the **most natural name any service would pick** for the same
concept. Two registrations → `BeanDefinitionOverrideException` → context never starts.

A shared library that must be *excluded* to be safe, and whose contract is *"remember to
declare an annotation or your app silently loses its repositories"*, is a library whose only
guard is human memory. That is the class of defect
[`project_guard_reachability_not_just_bite`] exists for.

---

## Measured population

> Measured by me on `origin/main` @ `606619695` (2026-07-14). **These numbers are a handed-over
> hypothesis, not a completed investigation — AC-0 re-measures them.** (This ticket's author
> has planted false counts in a ticket before: `TASK-MONO-394` → `TASK-MONO-400`.)

**~29 services declare `libs:java-messaging`.** They split three ways:

| group | n | services | what they do |
|---|---|---|---|
| **A. Use the lib's `ProcessedEvent*`** | **4** | iam `security-service`, iam `account-service`, ecommerce `order-service`, ecommerce `shipping-service` | import `com.example.messaging.outbox.ProcessedEvent*` in **main** source |
| **B. Shadow it with their own entity** | **6** | ecommerce `settlement-service`, erp `notification-service`, erp `read-model-service`, fan `notification-service`, finance `ledger-service`, scm `demand-planning-service` | own entity + `exclude = OutboxAutoConfiguration.class` |
| **C. Exclude it without shadowing** | **4** | wms `inbound-service`, wms `inventory-service`, wms `outbound-service`, finance `account-service` | `exclude = OutboxAutoConfiguration.class` only |

⇒ **10 services carry `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)`** (B + C).
⇒ **26 narrowing `@EnableJpaRepositories` declarations** exist across the repo, most of them
required by side effect (1) above.

**Group B is not a set of defectors — it is the set that already complies with ADR-MONO-004.**
Group A is the set that has not migrated yet.

### Group A already owns its table — the deletion is class-only, **zero schema change**

| service | its own Flyway migration for the dedupe table |
|---|---|
| iam `security-service` | `V0003__create_processed_events.sql` |
| iam `account-service` | `V0005__create_processed_events.sql` |
| ecommerce `order-service` | `V6__create_processed_events_table.sql` |
| ecommerce `shipping-service` | `V4__create_processed_events_table.sql` |

The lib's entity is a **shared JPA mapping over four tables the four services each already
declare themselves.** That is exactly the coupling ADR-MONO-004's Forbidden column names.

### Group B's schemas diverge — confirming the entity does not belong in a library

| owner | table | columns |
|---|---|---|
| **lib (canonical)** | `processed_events` | `eventId:String`, `eventType`, `processedAt:LocalDateTime` |
| ecommerce settlement | `processed_event` *(singular)* | `eventId`, `eventType`, `processedAt:Instant` |
| fan notification | `processed_events` | `eventId`, `eventType`, `processedAt:Instant` |
| erp notification | `processed_events` | `eventId`, **`topic`**, **`aggregateId`**, `processedAt` |
| erp read-model | `processed_events` | `eventId`, **`topic`**, **`aggregateId`**, `processedAt` |
| finance ledger | `processed_events` | `eventId`, **`tenantId`**, **`topic`**, **`sourceTransactionId`**, `processedAt` |
| scm demand-planning | **`dp_processed_events`** | **`eventId:UUID`**, **`tenantId`**, `processedAt`, **`sourceTopic`** |

Four of the six genuinely need columns the lib's 3-field entity does not have. Two (fan
notification, settlement) are shape-identical to the lib and duplicate it for no structural
reason — but note **that is a consequence, not the disease**: they had to roll their own the
moment they wanted to *also* be a producer, because adding the lib triggered the collision.

### ⚠️ Explicitly NOT a defect — do not "fix" this

The lib uses `LocalDateTime` while **all six** shadowers use `Instant`. I checked whether this
is a timezone bug. **It is not.** The writer (`ProcessedEventJpaEntity.create()` →
`LocalDateTime.now()`) and the only reader (`ProcessedEventCleanupScheduler` →
`LocalDateTime.now().minusDays(30)`) run in the same JVM/host zone, so the 30-day retention
window is self-consistent in any timezone. It is **style drift, not a defect.** Recorded here
so the next person does not "correct" it and claim a fix that fixes nothing.

---

## Scope

### In scope

1. **Migrate group A (4 services) off the lib's `ProcessedEvent*`.** For each of iam
   `security-service`, iam `account-service`, ecommerce `order-service`, ecommerce
   `shipping-service`: move the entity + repository into the service's **own** persistence
   package (the group-B shape), keeping the **same table** (`processed_events` — no Flyway
   change) and the **same query semantics** (`existsByEventId`,
   `deleteByProcessedAtBefore`). Their existing `@EnableJpaRepositories` / `@EntityScan`
   already cover their own packages.
2. **Delete from `libs/java-messaging`:** `ProcessedEventJpaEntity`,
   `ProcessedEventJpaRepository`, `OutboxJpaConfig`, and `OutboxAutoConfiguration`
   (which then has zero payload), plus the `OutboxAutoConfiguration` line in
   `META-INF/spring/…AutoConfiguration.imports`. Check `OutboxMetricsAutoConfiguration`
   (`@AutoConfiguration(after = OutboxAutoConfiguration.class)`) — retarget or drop the
   `after`.
3. **Remove the 10 `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)`
   declarations** — **mandatory, not cleanup**: once the class is gone they are compile
   errors. This is what makes the PR atomic across 6 projects (wms, erp, fan, finance, scm,
   ecommerce) and is also the task's strongest safety property: **the compiler enumerates
   every site.** (Per `project_gateway_three_lineages_convergence`: in this repo the thing
   that has actually caught the stragglers has been the compiler, not grep.)
4. **Write the missing rule** into `platform/shared-library-policy.md` (see AC-4).
5. **Add the guard** (see AC-5).

### Out of scope

- **Consolidating group B's six entities.** They are policy-compliant and four of them have
  genuinely different columns. *Do not "de-duplicate" them* — duplication that has not
  diverged is propagation, and here it has diverged on purpose.
- **Migrating anyone to `EventDedupePort`.** The port exists and is the long-term target, but
  swapping the four services' dedupe *contract* is a behaviour change on a money-adjacent path
  (settlement's `replayed_payment_does_not_double_accrue` precedent). This task only relocates
  the class; a port migration is a separate ticket if wanted.
- Any change to `OutboxRow` / `AbstractOutboxPublisher` / `EventEnvelope` (the v2 core — those
  are the *Allowed* column and are fine).

---

## Acceptance Criteria

- **AC-0 (re-measure — blocking).** Before touching anything, independently re-derive the
  three group counts (A=4 / B=6 / C=4), the 10 `exclude` sites, and the four Flyway
  migrations above. **Self-validate every detector against a known-positive** before trusting
  a zero (`env_empty_detector_output_is_not_absence`). If a number differs from this ticket,
  **the ticket is wrong — correct it in place and keep the wrong claim visible next to the
  correction.**
- **AC-1.** `libs/java-messaging` contains **no** `@EntityScan` and **no**
  `@EnableJpaRepositories` in `src/main`. `ProcessedEventJpaEntity`,
  `ProcessedEventJpaRepository`, `OutboxJpaConfig`, `OutboxAutoConfiguration` are gone.
- **AC-2.** `git grep -n "OutboxAutoConfiguration"` returns **0** hits outside the task's own
  documentation. `./gradlew check` compiles — the compiler is the completeness proof for
  AC-2/AC-3.
- **AC-3 (behaviour preserved).** Every group-A service's consume-path dedupe still works
  against a **real DB**, not a mock: their existing dedupe ITs
  (`EventDeduplicationIntegrationTest`, `LoginSucceededConsumerIntegrationTest`, …) pass on
  their merits, and the retention-cleanup scheduler still deletes rows older than 30 days.
  **No Flyway migration is added or altered** (the tables already exist).
- **AC-4 (the missing rule).** `platform/shared-library-policy.md` § *Forbidden in Shared
  Libraries* gains an entry — the canonical home CLAUDE.md already points to; **do not create
  a new file**:
  > *A shared library's `@AutoConfiguration` (or anything it `@Import`s) must not declare
  > `@EnableJpaRepositories` or `@EntityScan`. Both are **application-wide**: an explicit
  > `@EnableJpaRepositories` makes Spring Boot's `JpaRepositoriesAutoConfiguration` back off
  > for the entire consuming app (silently un-scanning its own repositories), and any entity/
  > repository the library registers collides on bean name with the service's own. Ship the
  > port/interface; let the service own the `@Entity`, the table, and the scan. (TASK-BE-432,
  > TASK-BE-461, TASK-MONO-406.)*
- **AC-5 (the guard — must bite, must be reachable).** `scripts/check-shared-lib-jpa-scan.sh`
  fails if any file under `libs/*/src/main/**` contains `@EnableJpaRepositories` or
  `@EntityScan`, wired into `.github/workflows/ci.yml` as its own job following the
  `check-service-map-drift.sh` pattern (**pure-positive** paths-filter — `libs/**/*.java` +
  the script itself — no negation, MONO-074/075 quirk; plus a `bash -n` step).
  - **The guard must be shown to be RED on today's `origin/main` and GREEN after the fix.**
    That is a free self-validation: the defect it polices exists *right now*, so a guard that
    does not fail before the change is a broken guard (`env_guard_calibrated_on_laptop_fails_on_runner`
    — a guard that never bit is not a guard). Record both runs in the PR body.
  - False-positive check: confirm **0** other `libs/*` files match today, so the guard is not
    born red for an unrelated reason.
- **AC-6 (the failure mode is a boot failure — prove the contexts boot).** The four group-A
  services and the ten former-excluders must each **load their Spring context in CI**. Confirm
  each has a Testcontainers `integrationTest` lane already wired (BE-461's whole point: the
  Docker-free `:check` cannot see this class of defect). **If any of the 14 has no
  context-booting lane, say so explicitly in the PR body** — do not let a silent gap read as
  coverage.

---

## Related Specs

- [`docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md`](../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md) — **ACCEPTED**; lines 83 / 159 retain `ProcessedEventJpaEntity` as a v1 shim; the Allowed/Forbidden table is the authority for where a dedupe entity lives.
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § *Messaging-specific guidance* (line ~58) + § *Forbidden in Shared Libraries* — the rule's canonical home (AC-4).
- `tasks/done/TASK-MONO-312-…` — the v1 → v2 outbox sweep that removed `OutboxWriter`/`OutboxPublisher` and **left this behind**.
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-461-settlement-duplicate-processedevent-repo-boot-failure.md` — boot failure #2; enumerates the same options (a)/(b)/(c) and picks the local workaround.
- `TASK-BE-432` (wms `inventory-service`) — boot failure #1, same workaround.
- [`platform/testing-strategy.md`](../../platform/testing-strategy.md) — the integration lane is the only surface that can observe this defect.

## Related Contracts

- **None.** No HTTP or event contract changes. The dedupe tables are service-private and each
  group-A service already owns its Flyway migration for its own table. *(If AC-0's re-measure
  contradicts this, STOP — a schema change would make this a different task.)*

## Edge Cases

- **`enableDefaultTransactions = false`.** The lib's `@EnableJpaRepositories` sets it. If any
  group-A service relied on the lib's repository being non-default-transactional, moving the
  repo under the service's own `@EnableJpaRepositories` (which does **not** set it) changes
  transaction demarcation. Check each of the 4 — this is the one silent behaviour change hiding
  in an otherwise mechanical move.
- **`@EntityScan` widening.** Some services scan `com.example.messaging` explicitly (BE-461
  found settlement doing exactly that: *"`@EntityScan` scoped to `com.example.settlement` (was
  `+ com.example.messaging`, which pulled the lib `ProcessedEventJpaEntity` a second way)"*).
  Grep for `com.example.messaging` inside `@EntityScan` / `@EnableJpaRepositories` base
  packages across all ~29 consumers, not just the 14 in groups A–C.
- **Removing an `exclude` may un-suppress something else.** Deleting
  `exclude = OutboxAutoConfiguration.class` from the 10 restores whatever else that
  auto-config would have contributed — which, after this task, is *nothing*. Verify the
  imports file has no other entry pulling `OutboxJpaConfig` back in.
- `OutboxMetricsAutoConfiguration` declares `@AutoConfiguration(after = OutboxAutoConfiguration.class)`.
  Deleting the referenced class is a compile error; decide whether the ordering constraint is
  still meaningful or should simply drop.
- **Two `@Entity` classes on one table.** After the move, confirm no service ends up with both
  its own and a lib mapping over `processed_events` (the lib's is deleted, so this should be
  structurally impossible — assert it).

## Failure Scenarios

- **Silent dedupe loss (money).** ecommerce `order-service` / `shipping-service` dedupe the
  consume path. A relocated repository that quietly changes `existsByEventId` semantics or
  transaction demarcation could let a replayed event apply twice. AC-3 must be proven against
  a real-DB IT, not a mock — the `replayed_payment_does_not_double_accrue` precedent
  (BE-461 AC-3) is the shape.
- **A guard that never bites.** If `check-shared-lib-jpa-scan.sh` is written *after* the
  deletion and only ever observed GREEN, it proves nothing. AC-5 requires the RED-before /
  GREEN-after pair. A guard whose CI job is never triggered by the files it polices is the
  same failure (`TASK-MONO-405`'s subject; `TASK-MONO-389`'s reachability rule).
- **CI-RED at merge.** This deletion touches 6 projects' integration lanes at once. Per
  CLAUDE.md the merge-verification is three-dimensional and CI-RED-at-merge requires a
  separate fix-task — do not merge on a partially-green rollup.
- **Scope creep into group B.** The tempting next step ("now consolidate the six") is
  explicitly out of scope and would *reintroduce* the coupling this task removes. Four of the
  six have different columns; a one-size entity is nobody's shape.
- **"It's just a rename."** It is not: the `exclude` removals mean 10 services' boot
  configuration changes. Every one of them must boot in CI (AC-6).

---

## Definition of Done

- [ ] AC-0…AC-6 satisfied.
- [ ] `./gradlew check` GREEN; all affected integration lanes GREEN on CI.
- [ ] Guard demonstrated RED on pre-fix `origin/main`, GREEN after (both recorded in the PR body).
- [ ] `platform/shared-library-policy.md` carries the new Forbidden entry.
- [ ] Ready for review.
