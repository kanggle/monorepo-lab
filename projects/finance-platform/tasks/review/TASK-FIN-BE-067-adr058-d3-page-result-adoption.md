# Task ID

TASK-FIN-BE-067

# Title

ADR-MONO-058 D3 — adopt `libs/java-common.PageResult`/`PageQuery` in `account-service` + `ledger-service` (retire hand-rolled `PageResponse`/`TransactionPageView`/`DiscrepancyPageView`/`AccountLinePageView`)

# Status

review

# Owner

backend

# Task Tags

- code
- test
- adr

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

`docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 **D3**
(ACCEPTED 2026-07-30) found the pagination carrier hand-rolled ~15 times across 6 projects
(finance among them) instead of importing `libs/java-common.PageResult`/`PageQuery`, which is
frequently already on the consuming service's classpath for other reasons (finance uses it for
`UuidV7`) and simply never imported for paging. This is a pure adoption gap — no new shared type is
authorized or needed.

**This task is the finance-platform adoption.** `account-service` declares
`presentation/dto/PageResponse<T>(content, page, size, totalElements, totalPages)` and
`application/view/TransactionPageView(content, page, size, totalElements, totalPages)`;
`ledger-service` declares `application/view/DiscrepancyPageView(content, page, size, totalElements,
totalPages)` and `application/view/AccountLinePageView(content, page, size, totalElements,
totalPages)`. All four are, field-for-field, the exact same shape as
`libs/java-common.PageResult<T>(content, page, size, totalElements, totalPages)` — verified by
reading all four files and the library type directly. Unlike the ADR's general warning that "wire-shape
divergences already exist in the wild (`content` vs `items` field naming; some hand-rolled shapes omit
`totalPages`)", **finance-platform has no such divergence to fix** — this adoption is a pure
type-replacement with zero wire-shape change required.

---

# Scope

## In Scope

- `account-service` — replace `presentation/dto/PageResponse<T>` and
  `application/view/TransactionPageView` with `com.example.common.page.PageResult<T>` /
  `com.example.common.page.PageResult<TransactionView>` at every construction site and consumer
  (controller, use case, mapper). Delete both local record files once every reference is migrated.
- `ledger-service` — replace `application/view/DiscrepancyPageView` and
  `application/view/AccountLinePageView` with `PageResult<DiscrepancyView>` /
  `PageResult<AccountLineView>` at every construction/consumer site. Delete both local record files.
- Where a repository/query method currently accepts ad hoc `(page, size)` int parameters (verify
  actual signatures in `TransactionRepository`, `JournalRepository`, `ReconciliationRepository` before
  assuming), evaluate adopting `com.example.common.page.PageQuery` as the request-side carrier too —
  in scope only where it is a like-for-like replacement (same page/size semantics, same clamping
  behavior); if a repository's pagination request shape diverges materially (e.g. cursor-based or
  sort-key-based rather than page/size), leave the request side as-is and adopt only the
  `PageResult` response side — do not force a request-shape change the ADR did not ask for.
- Both `build.gradle`s — no dependency change required; both `account-service` and `ledger-service`
  already declare `implementation project(':libs:java-common')`.
- `account-api.md` / `ledger-api.md` / `reconciliation-api.md` — verify the documented pagination
  meta shape (`page`, `size`, `totalElements`, `totalPages` — confirmed already matching
  `PageResult`'s fields) needs no wording change; if any doc's field list differs from what the code
  now emits, reconcile it in this task rather than leaving it stale.

## Out of Scope

- `PageResult`/`PageQuery` themselves — no shape or behavior change to the shared library type; this
  is adoption only (ADR § 2 D3: "No new type").
- Any change to what data is paginated, sort semantics, or page-size limits beyond what a like-for-like
  `PageQuery` swap naturally carries (e.g. `PageQuery.MAX_SIZE = 100` — verify this does not silently
  tighten or loosen an existing per-endpoint size cap; if finance currently enforces a different max,
  keep enforcing it at the call site rather than relying on `PageQuery`'s constructor guard to be the
  same value).
- Any endpoint whose "page" is not actually a `content`/`page`/`size`/`totalElements`/`totalPages`
  shape today (verify none exists before assuming full coverage — this task's Scope only lists the
  four record types actually found by this task's investigation).
- D1 (actor/JWT cluster) and D2 (error envelope) — separate tasks (`TASK-FIN-BE-065`,
  `TASK-FIN-BE-066`).

---

# Acceptance Criteria

- [x] Neither service declares its own page-carrier record any more —
      `grep -r "record PageResponse\|record TransactionPageView\|record DiscrepancyPageView\|record AccountLinePageView"`
      over both services returns zero hits.
      **Evidence**: all four files deleted (`account-service/presentation/dto/PageResponse.java`,
      `account-service/application/view/TransactionPageView.java`,
      `ledger-service/application/view/AccountLinePageView.java`,
      `ledger-service/application/view/DiscrepancyPageView.java`); re-verified grep over
      `projects/finance-platform/apps` returns zero hits post-implementation.
- [x] Every former consumer of those four types now uses `com.example.common.page.PageResult<T>`
      with the same type parameter it paginated before (`TransactionView`, `DiscrepancyView`,
      `AccountLineView`, and whatever `PageResponse<T>`'s call sites used it for).
      **Evidence**: `AccountApplicationService.listTransactions` → `PageResult<TransactionView>`;
      `TransactionController.list` → `PageResult<TransactionResponse>` (uses `PageResult.map`);
      `QueryLedgerUseCase.getAccountLines` / `LedgerController.getAccountEntries` →
      `PageResult<AccountLineView>`; `QueryReconciliationUseCase.listDiscrepancies` /
      `ReconciliationController.listDiscrepancies` → `PageResult<DiscrepancyView>`. Two slice tests
      (`LedgerControllerSliceTest`, `ReconciliationControllerSliceTest`) updated to construct
      `PageResult` directly.
      **Investigation finding — NOT in scope, left untouched**: three *domain-port* page
      projections carrying domain entities (not the four named application/presentation view
      types) exist with the same shape but were not named in this task's Scope:
      `TransactionRepository.Page` (account-service), `JournalRepository.LinePage` +
      `ReconciliationRepository.DiscrepancyPage` (ledger-service). Left as-is per the task's
      explicit AC-grep (which only targets the four named record names) and the Out-of-Scope note
      restricting coverage to "the four record types actually found by this task's investigation" —
      flagged here as new information per Implementation Notes rather than silently expanded into.
- [x] The JSON wire shape emitted for every paginated endpoint is byte-identical before/after (field
      names, field order not required to match exactly since JSON object field order is not a wire
      contract, but field **set** and each field's JSON type must be unchanged) — verified by
      controller-slice or integration test assertions, not by code-reading alone.
      **Evidence**: `LedgerControllerSliceTest.getAccountEntries` and
      `ReconciliationControllerSliceTest.listQueue` (pre-existing, updated in place) both pass
      unchanged. `account-service` had **no existing controller-slice test** for
      `GET /{id}/transactions` — added `TransactionControllerSliceTest` (new file, 2 tests:
      populated page + empty page) asserting `data.content/page/size/totalElements/totalPages` and
      `meta.page/size/totalElements` — this closes a genuine pre-existing coverage gap the AC
      requires proof for (Test Requirements' "add coverage only if genuinely missing" clause).
- [x] `account-api.md` / `ledger-api.md` / `reconciliation-api.md` accurately describe the resulting
      pagination meta shape (should require no wording change, per this task's investigation — confirm
      at implementation time).
      **Finding — pre-existing doc/code divergence, reconciled (not a swap-introduced change)**:
      `ledger-api.md` § 2 and `reconciliation-api.md` § 4 already matched the code (`data` = bare
      array, `meta` carries pagination) — no change needed. `account-api.md`'s
      `GET /{id}/transactions` section documented `data` as a bare array, but the code's `data` has
      always been (both before and after this swap) a page-wrapper object
      (`{content, page, size, totalElements, totalPages}`) — a pre-existing drift unrelated to
      `PageResponse`→`PageResult`. Reconciled the doc to describe the actual object shape, per this
      task's Scope bullet authorizing doc reconciliation when "any doc's field list differs from
      what the code now emits." No code/behavior change made — doc-only fix.
- [x] `./gradlew :projects:finance-platform:apps:account-service:check :projects:finance-platform:apps:ledger-service:check`
      GREEN, before/after test counts recorded.
      **Evidence**: BUILD SUCCESSFUL. account-service: 154 → 156 tests (net +2, the new
      `TransactionControllerSliceTest`), 0 failures. ledger-service: 434 → 434 tests (net 0, two
      existing tests updated in place), 0 failures.
- [x] `./gradlew :projects:finance-platform:apps:account-service:integrationTest :projects:finance-platform:apps:ledger-service:integrationTest`
      GREEN (CI Testcontainers lane authoritative).
      **Evidence**: account-service integrationTest — 30/30 tests, 0 failures, GREEN. ledger-service
      integrationTest — 47 tests, 1 failure: `LedgerFxRatesReadIntegrationTest.
      twoQuotesSortedWithStalenessAndRateAsString` (`AC-3: EUR ageSeconds ~2 days`, actual 140400s
      vs expected ≥172795s — a ~9h gap consistent with an Asia/Seoul UTC+9 host-clock/Testcontainers
      artifact). **This test is unrelated to this task's diff** — it lives in the FX-rate-feed read
      endpoint (`git diff --name-only` contains zero `FxRate*` files) and is not one of the two
      services' pagination endpoints. Matches this repo's documented local-Windows
      Testcontainers-flakiness pattern (`platform`/session-memory: "Testcontainers×Docker(Windows) —
      로컬=FLAKY, CI Linux가 권위"); per this AC's own wording, the CI Testcontainers lane (Linux) is
      authoritative — deferring to CI on the PR rather than treating this local run as the gate.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/fintech.md` and `rules/traits/transactional.md` / `rules/traits/regulated.md` / `rules/traits/audit-heavy.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D3, § 6 item 4
  (the fleet decision this task adopts)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the root split origin)
- `libs/java-common/src/main/java/com/example/common/page/PageResult.java`,
  `libs/java-common/src/main/java/com/example/common/page/PageQuery.java` (the target types — read
  in full before implementing)
- `specs/services/account-service/architecture.md` § package tree, § Allowed dependencies
- `specs/services/ledger-service/architecture.md` § package tree, § Allowed dependencies

---

# Related Contracts

- `specs/contracts/http/account-api.md` — pagination meta section
  (`{ "content": [...], "meta": { "page", "size", "totalElements", "timestamp" } }`) — verify no
  wording drift needed; this task's own investigation found the field set already matches
  `PageResult` exactly.
- `specs/contracts/http/ledger-api.md` — same verification for its own paginated endpoints
  (`AccountLinePageView`-backed).
- `specs/contracts/http/reconciliation-api.md` — same verification for its `DiscrepancyPageView`-backed
  endpoint.
- No client-visible contract change is authorized by this task — it is a pure internal-type swap.

---

# Target Service

- `account-service`
- `ledger-service`

---

# Architecture

- `account-service`: Hexagonal. `PageResponse` lives in `presentation/dto/` (a presentation-layer
  shape today); `TransactionPageView` lives in `application/view/`. Adopting `PageResult<T>` from
  `libs/java-common` in both layers is consistent with the existing dependency direction — both layers
  already permit depending on `libs/java-common` (it is already a build dependency; verify the
  package-tree section of `architecture.md` does not need updating beyond removing the two deleted
  local types).
- `ledger-service`: Hexagonal + DDD. Both page-view types live in `application/view/`; same note.

---

# Implementation Notes

- **This is the most mechanical of the three ADR-058 finance adoptions** — unlike D1 (converter
  behavior must be preserved exactly) and D2 (a genuine wire-shape design decision), D3's four local
  types already match `PageResult`'s shape field-for-field, so the swap should be close to a
  find-and-replace of the type name plus deleting the now-redundant local record. Do not add scope
  beyond that; if implementation surfaces an actual divergence (e.g. a fifth hand-rolled page type
  this task's investigation missed, or a field-name mismatch), treat it as new information to
  re-scope against, not something to route around silently.
- **`PageResult<T>.map(Function<T,R>)`** is available and may simplify call sites that currently
  construct a `PageResponse<TransactionView>` from a `Page<Transaction>`-shaped intermediate — check
  whether existing mapping code (JPA `Page<T>` → domain page-view) can use `.map()` to reduce
  boilerplate as a natural side effect of the swap, but this is a style opportunity, not a requirement.
- **`PageQuery`'s `MAX_SIZE = 100` constant** — confirm this matches (or is compatible with) whatever
  size cap finance's controllers currently enforce on the request side before adopting `PageQuery` for
  request parsing; if finance has no current explicit cap (relying only on a `@Max` validation
  annotation or similar), adopting `PageQuery.of(...)`'s clamping behavior is a **behavior change**
  (silent clamp instead of a 400 rejection) — verify which finance currently does and preserve it,
  since the ADR's "no new type" framing does not authorize a silent behavior change in how
  out-of-range requests are handled.

---

# Edge Cases

- **JPA `Page<T>` / `Pageable` interop** — if any repository method returns Spring Data's
  `org.springframework.data.domain.Page<T>` today and a mapper converts it to the local page-view
  record, confirm the same conversion path produces an equivalent `PageResult<T>` (same
  `totalElements`/`totalPages` semantics — Spring Data's `Page.getTotalPages()` computation must match
  `PageResult`'s field, which is a plain carried value, not computed — verify nothing changes in how
  `totalPages` is calculated, only which type carries it).
- **Zero-result pages** — confirm `PageResult` with an empty `content` list and `totalElements = 0`
  serializes identically to today's empty-page response (no `null` vs `[]` divergence).

---

# Failure Scenarios

- Missing a consumer during the type swap (leaving a mix of `PageResponse<T>` and `PageResult<T>` in
  the same service) fails the build loudly (safe direction) rather than silently — the AC's
  zero-hits grep is the check that catches an incomplete migration before merge.
- Silently changing request-side out-of-range-size handling (rejection → clamping, or vice versa) by
  adopting `PageQuery.of(...)` without checking finance's current behavior would be an undocumented
  API behavior change riding along on what should be a type-only swap — see Implementation Notes.
- Leaving `account-api.md`/`ledger-api.md`/`reconciliation-api.md` unreconciled after a type swap
  that (contrary to this task's investigation) turns out to change a field would leave a contract doc
  silently wrong — verify at implementation time, don't rely solely on this task's pre-implementation
  read.

---

# Test Requirements

- Existing controller-slice / integration tests asserting the JSON shape of every paginated endpoint
  (`GET .../transactions`, ledger's account-line and reconciliation discrepancy list endpoints) must
  pass unchanged — this is the wire-shape-preservation proof.
- Any repository/mapper unit test constructing a `PageResponse`/`*PageView` directly must be updated
  to construct `PageResult` instead — mechanical test-code updates, not new test scenarios, unless the
  investigation surfaces an actual gap in coverage for the pagination boundary (empty page, last
  partial page, etc.) — add coverage only if genuinely missing, not as scope creep.

---

# Definition of Done

- [x] Implementation completed in both `account-service` and `ledger-service`
- [x] All four local page-carrier types deleted, zero remaining references
- [x] JSON wire shape unchanged for every paginated endpoint (test-verified)
- [x] Contract docs reconciled if needed (verified, not assumed unchanged) — `account-api.md`
      reconciled (pre-existing drift, see AC evidence); `ledger-api.md` / `reconciliation-api.md`
      already accurate.
- [x] Tests passing (unit + Testcontainers integration, CI-authoritative), before/after counts
      recorded. `check`: account-service 154→156, ledger-service 434→434, both 0 failures.
      `integrationTest`: account-service 30/30 GREEN; ledger-service 46/47 GREEN locally with one
      unrelated pre-existing local-clock-flake failure in an FX-rate-feed test this task's diff
      does not touch — CI Testcontainers lane is authoritative per this task's own AC wording.
- [x] Ready for review
