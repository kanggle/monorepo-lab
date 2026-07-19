# TASK-FIN-BE-056 — ledger-service: extract shared controller-slice test base (@WebMvcTest setup)

- **Type**: TASK-FIN-BE (test refactor — behaviour-preserving)
- **Status**: review
- **Service**: ledger-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-file behaviour-preserving refactor)

## Goal

Remove the duplicated `@BeforeEach setUp()` / `@MockitoBean` / SecurityContext boilerplate across the
ledger-service controller-slice tests by extracting a shared base. **No test method bodies,
assertions, coverage, or semantics change** — same slices run.

Consolidations (from the 2026-07-19 test audit):

1. **Auth `setUp()`** — the 4-line "build a `TestingAuthenticationToken`, `setAuthenticated(true)`,
   push into `SecurityContextHolder`" `@BeforeEach` is byte-identical in 5 controller-slice tests:
   `JournalControllerSliceTest`, `LedgerControllerSliceTest`, `PeriodControllerSliceTest`,
   `ReconciliationControllerSliceTest`, `SettlementControllerSliceTest`. Extract to a shared
   `AbstractLedgerControllerSliceTest` exposing a `protected ActorContext actor()` hook (each subclass
   overrides with its own actor shape). Add an `@AfterEach clearContext()` in the base for isolation
   ONLY IF the current suite already relies on cross-test cleanup — otherwise keep behaviour identical
   (do NOT introduce new teardown that changes observable behaviour; if in doubt, leave teardown as-is).
2. **FX controller slices** — `FxRateControllerSliceTest`, `FxRateHistoryControllerSliceTest`,
   `FxRatesRefreshControllerSliceTest` all `@WebMvcTest(FxRateController.class)` and share an identical
   `@MockitoBean` field list + `ACTOR` constant + `@BeforeEach`. Fold them onto the same base (they may
   need a variant base or the same one with the mock set as fields — pick the shape that keeps each
   test's `@MockitoBean` usage compiling and unchanged).
3. **Dead import** — `SettlementControllerSliceTest` imports `com.example.finance.ledger.domain.money.Currency`
   twice (lines ~26 and ~32); remove the redundant one (this file is already in scope via #1).

## Scope

- **In scope**: ledger-service `presentation/controller/*ControllerSliceTest.java` (8 files) + new
  `AbstractLedgerControllerSliceTest`.
- **Out of scope**: production code; unit/domain/integration tests (TASK-FIN-BE-057); account-service;
  the `*ControllerSliceTest` vs `*ControllerTest` naming question (repo-wide, separate).

## Acceptance Criteria

- **AC-1**: the identical auth `setUp()` lives once in `AbstractLedgerControllerSliceTest`; the 5
  non-FX slices use it via an overridable `actor()` hook.
- **AC-2**: the 3 FX controller slices share the base's `@MockitoBean`/setup where identical; each
  keeps its own test bodies and any slice-specific mocks.
- **AC-3**: the duplicate `Currency` import in `SettlementControllerSliceTest` is gone.
- **AC-4**: `./gradlew :ledger-service:test --tests "*ControllerSliceTest"` GREEN; `@Test` count
  across the slices is unchanged.
- **AC-5**: no production-code change; behaviour/coverage identical.

## Edge Cases / Failure Scenarios

- `@MockitoBean` fields differ between the non-FX slices (each mocks its own controller's ports) — only
  the SecurityContext setup is truly shared; do NOT force a single mock field list across controllers
  that mock different beans.
- Do NOT change the SecurityContext teardown semantics — if the suite currently leaks context between
  classes and relies on `@WebMvcTest` per-class context reset, adding `@AfterEach clearContext()` is a
  behaviour change; only add it if the audit confirms it is a pure no-op given the current setup.

## Related

- **TASK-FIN-BE-054/055** (the IT-base sibling of this slice-base extraction — same proven pattern).
- Memory: `feedback_repo_knows_what_it_does_not_say` (dedup only non-diverged — per-controller mock
  lists stay local), `project_console_web_godfile_split_series` (behaviour-preserving discipline).
