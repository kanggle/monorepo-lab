# TASK-FIN-BE-055 — ledger-service: consolidate integration-test HTTP/SQL helpers into AbstractLedgerIntegrationTest

- **Type**: TASK-FIN-BE (test refactor — behaviour-preserving)
- **Status**: done
- **Service**: ledger-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (large multi-file behaviour-preserving refactor)

## Goal

Remove copy-paste duplication across ~18 ledger-service integration tests by hoisting shared HTTP-verb
helpers and FX seed/query SQL helpers into the already-existing `AbstractLedgerIntegrationTest` base
(which owns Testcontainers wiring, JWT minting, and Kafka helpers, but currently NO HTTP or FX-seed
helpers). **No test method bodies, assertions, coverage, or semantics change.** The target helper
shapes ALREADY EXIST locally — several were independently reinvented by multiple authors — so this is
promotion of a proven shape, not a fresh design.

Consolidations (from the 2026-07-19 test audit):

1. **HTTP-verb helpers** — `get(path, token)` (byte-identical in 7 files),
   `post(path, token, body)` with null-body → `noBody()` branch (byte-identical in 4 files),
   `readJson(resp)` → `objectMapper.readTree(resp.body())` (3 files). Hoist to the base as
   `protected` methods (raw `java.net.http.HttpClient`, using the base's `port`).
2. **FX seed/query SQL helpers** — `positionFor(account, currency)` (identical SQL in 6 files — the
   generalized form already exists at `LedgerFxSettlementIntegrationTest`), `seedAssetAccount(code)`
   (5 files), `setFxCostFlow(method)` upsert (3 files), `openLots(account)` query (3 files). Hoist,
   parameterizing on the differing literal (account code / currency / method).

## Scope

- **In scope**: `AbstractLedgerIntegrationTest` + its IT subclasses under
  `ledger-service/src/test/java/com/example/finance/ledger/integration/`.
- **Out of scope**: production code (zero change); unit/slice tests; the `*UnitTest` /
  `*ControllerSliceTest` naming questions (finance/repo-wide, separate ticket); account-service
  (TASK-FIN-BE-054).

## Acceptance Criteria

- **AC-1**: `get` / `post` (null-body-aware) / `readJson` HTTP helpers live once in
  `AbstractLedgerIntegrationTest`; the truly-identical copies are removed from subclasses.
- **AC-2**: `positionFor` / `seedAssetAccount` / `setFxCostFlow` / `openLots` FX helpers live in the
  base, parameterized on the differing literal; the identical copies are removed.
- **AC-3**: **variants are LEFT ALONE** — `LedgerReconciliationIntegrationTest`'s simpler `post()`
  (no null-body branch, always JSON), `LedgerManualPostingIntegrationTest`'s purpose-named
  `postEntry()/postPeriod()/closePeriod()`, and the `establishUsdPosition/ensureWallet` +
  `seedJournalLine` helpers (scenario-specific amount/direction params) are NOT merged blindly.
- **AC-4**: `./gradlew :ledger-service:compileTestJava` is clean; the IT suite is unchanged in count.
- **AC-5**: no production-code change; behaviour/coverage identical (verified by CI's
  `Integration (finance-platform, Testcontainers)` lane — authoritative; local Windows Testcontainers
  is flaky and NOT authoritative).

## Edge Cases / Failure Scenarios

- **Divergence traps (audit-flagged — do NOT blind-merge)**: `post()` has a 2nd (no-null-body) variant
  and a split-into-purpose-named variant; keep those. `establishUsdPosition`/`ensureWallet` differ by
  scenario amounts; `seedJournalLine` differs by direction — parameterize, never flatten to one call.
- `AbstractLedgerIntegrationTest` starts containers in a `static {}` block (NOT `@BeforeAll`) per the
  bootstrap rule — the hoisted helpers must not disturb that ordering.
- Some FX ITs have load-bearing `@Test` ordering on a shared static container — do NOT reorder or split.

## Related

- **TASK-FIN-BE-054** (the account sibling of this consolidation).
- Memory: `feedback_repo_knows_what_it_does_not_say` (dedup only non-diverged; keep the variants),
  `project_testcontainers_docker_desktop_blocker` (CI Linux is the authoritative IT lane),
  `feedback_recount_population_dont_inherit_scope` (re-verify each "identical" claim against the actual
  file before deleting a copy — the audit line numbers are a hypothesis).
