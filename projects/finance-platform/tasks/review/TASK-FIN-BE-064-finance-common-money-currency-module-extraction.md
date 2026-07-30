# Task ID

TASK-FIN-BE-064

# Title

Extract `Money`/`Currency` into a project-scoped shared Gradle module (`projects:finance-platform:libs:finance-common`) — ADR-003 Option A execution

# Status

review

# Owner

backend

# Task Tags

- code
- test
- adr
- onboarding

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

`account-service` and `ledger-service` each declare their own near-byte-identical
`Money`/`Currency` value objects (package `<service>.domain.money`, plus the nested
`Money.CurrencyMismatchException` / `Currency.UnsupportedCurrencyException`).
`ledger-service`'s own `Currency` javadoc already self-acknowledges this as deferred
debt — *"Mirrors account-service's `Currency` … a single source of truth would be a
shared lib in a later increment; first-increment parity is intentional"*.

[`ADR-003`](../../docs/adr/ADR-003-money-currency-shared-location.md) is **ACCEPTED
(2026-07-30, owner exact-form intent)** and binds **Option A**: create a
**project-scoped** Gradle module holding `Money`/`Currency`, with both services
depending on it. `Money`/`Currency` shape, API and invariants transfer
**byte-unchanged** (ADR § 5 — this is a relocation, not a redesign), except that the
shared copy is the **UNION** of both services' method sets so no service loses a
method it already had.

After this task the finance-platform money vocabulary has exactly **one** definition,
and this repository has its **first** project-scoped shared subtree — which the layout
documentation must record so the next project does not have to re-derive it
(ADR § 6 step 4).

---

# Scope

## In Scope

### a. New module

- `settings.gradle` — `include 'projects:finance-platform:libs:finance-common'`.
- `projects/finance-platform/libs/finance-common/build.gradle` — pure-Java module,
  **no Spring / no JPA** (mirrors the value objects' existing "Pure Java" contract),
  JUnit 5 + AssertJ on the test source set only.
- `projects/finance-platform/libs/finance-common/src/main/java/com/example/finance/common/money/`
  — `Money.java`, `Currency.java` (each carrying its existing nested exception type).

### b. Both services converted

- `account-service` / `ledger-service` `build.gradle` — add
  `implementation project(':projects:finance-platform:libs:finance-common')`.
- Delete `apps/account-service/.../account/domain/money/{Money,Currency}.java` and
  `apps/ledger-service/.../ledger/domain/money/{Money,Currency}.java`.
- Rewrite every import (`com.example.finance.{account,ledger}.domain.money.{Money,Currency}`
  → `com.example.finance.common.money.{Money,Currency}`) and every non-import
  fully-qualified reference in **both** `src/main` and `src/test`.
- Move the two `MoneyTest` classes into the shared module as the union of their
  assertions, and add the missing direct coverage for `Currency.ofOrThrow`.

### c. Documentation kept in step with the structural change

- `CLAUDE.md § Repository Layout` — record the project-scoped shared-subtree pattern
  (ADR § 6 step 4). Short additive note, **not** a policy rewrite; ADR-003's ACCEPT
  scope explicitly includes it (ADR § 3 "Negative": *"settings.gradle 관례, CLAUDE.md
  의 shared/project 경계 서술 갱신 … 본 ADR의 ACCEPT 범위에는 포함하되, 문서 정합은
  구현 task에서"*).
- `platform/repository-structure.md` — its own **Change Rule** binds this task:
  *"Changes to the directory layout (… changing the monorepo `projects/<name>/`
  shape) must be updated here AND in `CLAUDE.md § Repository Layout` in the same
  PR."* Adding `projects/<name>/libs/` changes that shape, so updating only
  `CLAUDE.md` would violate the rule and leave a second stale enumeration.
- `platform/architecture.md` § Repository Structure — same enumeration of
  project-specific directories, one word added, for the same reason.
- `specs/services/{account,ledger}-service/architecture.md` — package tree +
  § Allowed dependencies.
- `.github/workflows/ci.yml` — add
  `:projects:finance-platform:libs:finance-common:check` to the existing
  *"Build and check finance-platform backend (Docker-free)"* step. Without this the
  new module's unit tests are **never executed by CI** (the finance job names its
  gradle targets explicitly; a module only reached as a compile dependency has its
  `test` task skipped) — a guard that does not run reports green.

## Out of Scope

- **Any change to `Money`/`Currency` behaviour.** ADR § 2/§ 5 fix the shape, API and
  invariants as byte-unchanged. Union-of-methods is a superset move, not a redesign:
  no method is renamed, re-signed, removed, or given new semantics.
- **`LedgerReportingCurrency`** stays in `ledger-service`
  (`ledger.domain.money.LedgerReportingCurrency`). It is a **ledger-owned product
  decision** (which currency this service reports in), not shared finance
  vocabulary — `shared-library-policy.md § Ownership Rule`. It simply imports the
  shared `Currency`.
- **Promotion to repo-root `libs/`** — ADR § 2 Option B, explicitly **not chosen**:
  the v1 currency whitelist is a finance-platform product decision and must not be
  planted in an 8-project project-agnostic library.
- The wider technical-scaffolding duplication (`ApiEnvelope`/`ApiErrorBody`/
  `ClockPort`/`SecurityConfig`/`GlobalExceptionHandler`/`ActorContextResolver`) —
  ADR § 1.1 explicitly excludes it; it belongs to `ADR-MONO-058` (PROPOSED).
  In particular `GlobalExceptionHandler` dedup was **closed WONTFIX** by
  TASK-FIN-BE-058 and must not be reopened here.
- Any HTTP/event contract change. `Money`'s wire form (string-encoded minor units +
  currency code) is untouched.
- Applying the pattern to fan-platform or any other project (ADR § 4 — that
  duplication is technical scaffolding under ADR-MONO-058, not this axis).

---

# Structural decision — module path, name and package (first of its kind)

ADR § 5 leaves "이관 방식(모듈 이름, Gradle include 경로)" to the implementation task.
Recorded here because this is the repository's first project-scoped shared module.

| Axis | Choice | Reasoning |
|---|---|---|
| Gradle path | `projects:finance-platform:libs:finance-common` | Exactly the path ADR § 2 Option A names. It composes the two conventions already in `settings.gradle`: the project prefix `projects:<project>:…` used by every `apps:*` module, and the `libs:<name>` segment used by every repo-root shared library. A reader who knows either convention reads this one correctly. |
| Directory | `projects/finance-platform/libs/finance-common/` | Gradle's default path mapping for that include; keeps every finance-platform artifact under the project directory, so the project stays extractable to its standalone fork (`TEMPLATE.md`, ADR-MONO-008 Option C). |
| Module name | `finance-common` | Project-qualified. `common` alone would collide conceptually with repo-root `libs:java-common`; the `java-` prefix is reserved for the root shared layer, and this module is deliberately **not** in it. |
| Java package | `com.example.finance.common.money` | Sibling of `com.example.finance.account` / `com.example.finance.ledger` — same finance namespace, new peer segment. It reads as "finance-platform's shared vocabulary", not as "account-service code that ledger borrows". |
| Gradle `group` | `com.example.finance.common` | Matches each service module's `group = com.example.finance.<service>` convention. |
| Dependency direction | both services → module; module → nothing | `shared-library-policy.md § Dependency Rule` (one-way, no dependency on service implementation modules). |

**Guard reachability check performed before choosing** (a new path must not be one no
guard looks at):

- `scripts/check-service-map-drift.sh` matches `projects:<p>:apps:<svc>` only, and its
  header states `libs:` is deliberately not a service → new module correctly invisible,
  **no `docs/project-overview.md` row required**.
- `scripts/check-error-code-registry.sh` scans `projects/*/apps/*/src/main libs/*/src/main`,
  so the new path is unscanned — harmless **because no error-code literal moves**:
  both exception types call `super(message)` with a free-text message; the
  `CURRENCY_MISMATCH` string lives in each service's `GlobalExceptionHandler`, which
  is out of scope and stays put. Verified, not assumed.
- `scripts/check-shared-lib-jpa-scan.sh` targets repo-root `libs/` — not applicable;
  the module ships no `@AutoConfiguration`, no `@Entity`, no Spring at all.
- `.github/workflows/ci.yml` `changes` filter already matches
  `projects/finance-platform/**` → the finance jobs trigger on this module's changes.
  Its `check` target is added explicitly (see Scope c) because the finance job lists
  gradle tasks by name.

---

# Acceptance Criteria

- [ ] `projects:finance-platform:libs:finance-common` exists, is included in
      `settings.gradle`, and contains exactly `Money`, `Currency` (+ their nested
      exception types) under `com.example.finance.common.money`.
- [ ] The shared `Money` is the **union**: `of(long,Currency)`, `of(String,Currency)`,
      `zero`, `minorUnits`, `currency`, `toMinorString`, `isZero`, `isPositive`,
      `add`, `subtract`, **`absoluteDifference`** (was ledger-only), `isGreaterThan`,
      `isLessThan`, `isGreaterThanOrEqual`, `equals`/`hashCode`/`toString`,
      nested `CurrencyMismatchException`.
- [ ] The shared `Currency` is the **union**: `KRW(0) USD(2) EUR(2) JPY(0)`,
      `minorUnitScale`, `code`, `of(String)`, **`ofOrThrow(String, Function)`**
      (was ledger-only), nested `UnsupportedCurrencyException`.
- [ ] Neither service contains a local `Money.java` or `Currency.java` any more, and
      `grep -r "domain\.money\.\(Money\|Currency\)"` over both services returns zero
      hits outside `LedgerReportingCurrency`'s own package declaration.
- [ ] `LedgerReportingCurrency` still lives in `ledger-service` and still exposes
      `BASE == Currency.KRW`.
- [ ] `./gradlew :projects:finance-platform:libs:finance-common:check
      :projects:finance-platform:apps:account-service:check
      :projects:finance-platform:apps:ledger-service:check` is GREEN, with a
      **before/after** comparison of each service's test-count recorded in the PR
      (no test silently lost to the move).
- [ ] The shared module's own test suite covers the union, including
      `absoluteDifference` and `Currency.ofOrThrow` (remap on unsupported,
      pass-through on supported, `NullPointerException` still unremapped on `null`).
- [ ] `CLAUDE.md § Repository Layout`, `platform/repository-structure.md` and
      `platform/architecture.md` § Repository Structure all describe the
      `projects/<name>/libs/` subtree — none left stale.
- [ ] Both services' `architecture.md` package trees no longer show `Money.java` /
      `Currency.java` under their own `domain/money/`, and both § Allowed
      dependencies list the new module.
- [ ] `.github/workflows/ci.yml`'s finance Docker-free step runs the new module's
      `check`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (`fintech`; `transactional`, `regulated`, `audit-heavy`). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `projects/finance-platform/docs/adr/ADR-003-money-currency-shared-location.md` (**ACCEPTED** — the authoritative decision; § 2 Option A, § 5 binding scope, § 6 sequence)
- `platform/shared-library-policy.md` (§ Decision Rule, § Ownership Rule, § Dependency Rule — the ADR's reasoning rests on these)
- `platform/repository-structure.md` (§ Rules, § Change Rule — binds the doc updates in Scope c)
- `platform/architecture-decision-rule.md`, `platform/ownership-rule.md`
- `platform/naming-conventions.md` (package + module naming)
- `platform/testing-strategy.md`
- `specs/services/account-service/architecture.md` (§ package tree, § Allowed dependencies, § Boundary rules — `domain/` must not depend on Spring)
- `specs/services/ledger-service/architecture.md` (same sections)
- `rules/domains/fintech.md` (F5 — integer minor units, no float/double)

# Related Skills

- `.claude/skills/backend/testing-backend`

---

# Related Contracts

- `specs/contracts/http/account-api.md` — **unchanged**. `Money`'s wire form
  (string-encoded minor units + ISO-4217 code) and `CURRENCY_MISMATCH` mapping are
  untouched by a package move.
- `specs/contracts/http/ledger-api.md` — **unchanged**, same reason.
- `specs/contracts/events/finance-account-events.md`,
  `specs/contracts/events/finance-ledger-events.md` — **unchanged**; envelopes carry
  minor-unit strings + currency codes, never the Java type.

No contract change is required or permitted by this task.

---

# Target Service

- `account-service`
- `ledger-service`
- (new) `finance-common` — project-scoped shared module, not a deployable service

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md` (Hexagonal)
- `specs/services/ledger-service/architecture.md` (Hexagonal + DDD)
- The shared module has **no** internal layering: it is a leaf of pure domain value
  objects with zero dependencies. `domain/` in both services may depend on it
  precisely because it is framework-free — the § Boundary rule *"`domain/` MUST NOT
  depend on Spring"* stays satisfied.

---

# Implementation Notes

- **Promote from `account-service`'s copy, then merge ledger's additions**
  (ADR § 6 step 1). Measured differences, verified by reading both files rather than
  trusting the ADR's paraphrase:
  - `Money`: ledger has **`absoluteDifference(Money)`**; account has no method ledger
    lacks. Everything else is byte-identical apart from the javadoc's "Mirrors
    account-service's `Money`" sentence.
  - `Currency`: ledger has **`ofOrThrow(String, Function<String, ? extends RuntimeException>)`**;
    account has no method ledger lacks. Enum constants, scales, `BY_CODE`, `of` and
    the nested exception are byte-identical.
  - The ADR's "메서드 1개(`absoluteDifference`/`ofOrThrow`)" is therefore **two**
    methods across **two** types, not one — the union is the merge of both.
- Import rewriting is mechanical (`import com.example.finance.account.domain.money.X`
  → `import com.example.finance.common.money.X`) but must **not** touch
  `…ledger.domain.money.LedgerReportingCurrency`, which stays. Four
  **non-import** fully-qualified references also exist and are easy to miss:
  `AccountControllerSliceTest` (`new com.example.finance.account.domain.money.Currency.UnsupportedCurrencyException(...)`),
  `JournalRepositoryImpl` (an FQN parameter type), and two lines in
  `FxSettlementPolicyTest`.
- The javadoc lines that named the duplication (`"Mirrors account-service's …"`,
  `"first-increment parity is intentional"`) must be **removed**, not carried over:
  the debt they document is what this task closes. The retained javadoc is
  account-service's phrasing, which is the more complete of the two, plus
  `ofOrThrow`'s own doc from ledger.
- Keep the module Spring-free. Adding `spring-boot-starter-*` here would put the
  finance value objects on a framework the ADR's Option A analysis assumed they do
  not need, and would make `domain/ → Spring` reachable transitively in both
  services.

---

# Edge Cases

- **`ofOrThrow(null, …)`** must still raise `NullPointerException` (from
  `Objects.requireNonNull` inside `of`), **not** the caller's remapped exception —
  its javadoc states this explicitly and three ledger call sites
  (`GetFxRateOverrideUseCase`, `SetFxRateOverrideUseCase`, `SettlementController`)
  depend on the distinction between "absent" and "unsupported".
- **Exception identity across the module boundary**: `Money.CurrencyMismatchException`
  and `Currency.UnsupportedCurrencyException` are now single classes rather than two
  unrelated per-service classes. Both services' `GlobalExceptionHandler` catch them by
  type; the handlers keep their **deliberately asymmetric** mappings (TASK-FIN-BE-058
  — account maps `IllegalArgumentException` to 422 `AMOUNT_INVALID`, ledger to 400
  `VALIDATION_ERROR`) because each handler still declares its own `@ExceptionHandler`.
  The move must not merge or align the two handlers.
- **`absoluteDifference` becomes visible to `account-service`** and `ofOrThrow` to
  both. That is the intended superset; no account-service code is required to use
  them, and an unused public method on a shared value object is not dead code in the
  sense the repo prunes (it has a live caller in the sibling service).
- **`Math.addExact`/`subtractExact` overflow** semantics are unchanged — `long`
  arithmetic guards move verbatim.
- **Standalone fork extraction** (`kanggle/finance-platform`, ADR-MONO-008 Option C):
  the module lives under `projects/finance-platform/`, so it travels with the project.
  A module placed in repo-root `libs/` would not have — a further reason Option A
  beats Option B for this project.

---

# Failure Scenarios

- A currency added to the v1 whitelist (or a rounding/scale policy change) lands in
  one copy and not the other, so `account-service` accepts a currency
  `ledger-service` rejects and the derived journal entry fails at consume time —
  the exact `ADR-MONO-058` failure pattern ADR-003 § 2 Option C names. Closing the
  duplication removes the possibility structurally.
- A missed import rewrite compiles against a **deleted** class → the build fails
  loudly at compile time (safe direction). The dangerous variant is the reverse: a
  *surviving* local copy that still compiles, leaving the duplication half-closed.
  AC's "zero hits" grep is the check that catches it.
- The new module's tests never run because CI names its gradle targets explicitly →
  a regression in shared money arithmetic reports green. Prevented by the ci.yml
  target addition (Scope c).
- A future contributor reads `CLAUDE.md § Repository Layout`, sees only
  `apps/ specs/ tasks/ knowledge/ docs/ infra/` under `projects/<project>/`, and
  concludes a project-scoped shared module is not allowed — re-creating a third
  copy in a third service. Prevented by the layout-doc updates (Scope c).

---

# Test Requirements

- **Shared module unit tests** (`finance-common`, plain JUnit 5 + AssertJ, no Spring):
  - `MoneyTest` — the union of both services' existing `MoneyTest` assertions:
    minor-unit construction, string wire form, per-currency scale, negative
    rejection, non-integer string rejection (parameterized), `add`/`subtract`,
    negative-subtract rejection, mixed-currency `add` and compare →
    `CurrencyMismatchException`, comparisons, `Currency.of` unknown/wrong-length,
    case normalisation, zero + equality, **plus `absoluteDifference`** (both
    argument orders — the operation is symmetric and always non-negative).
  - `CurrencyTest` — `ofOrThrow` pass-through on a supported code, remap to the
    caller's exception on an unsupported code, and `NullPointerException`
    (unremapped) on `null`. This closes a real gap: `ofOrThrow` currently has
    **no direct unit test** in `ledger-service`, only indirect coverage through
    three use-case/controller call sites.
- **Both services**: existing suites must pass unchanged. No test may be deleted
  except the two `MoneyTest` classes whose assertions are absorbed by the shared
  module's `MoneyTest` — record before/after test counts for both services to prove
  the rest of the surface is intact.
- No new integration test. The move changes no runtime behaviour, no schema, no
  wire format; the existing Testcontainers suites in both services already exercise
  `Money`/`Currency` end-to-end and are the authoritative behaviour signal
  (local Windows Docker runs are flaky and non-authoritative — CI's
  *Integration (finance-platform, Testcontainers)* lane decides).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (shared module `MoneyTest` + `CurrencyTest`)
- [ ] Tests passing (baseline recorded before the change, re-run after)
- [ ] Contracts updated if needed (**not needed** — see Related Contracts)
- [ ] Specs updated (both services' `architecture.md`; layout docs per Scope c)
- [ ] Ready for review
