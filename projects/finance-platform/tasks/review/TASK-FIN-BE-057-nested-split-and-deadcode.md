# TASK-FIN-BE-057 — finance tests: @Nested split of long test classes (behaviour-preserving)

- **Type**: TASK-FIN-BE (test refactor — behaviour-preserving)
- **Status**: review
- **Service**: ledger-service + account-service (finance-platform)
- **Domain/traits**: saas / [transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical @Nested wrapping)

## Goal

Improve readability/report grouping of the longest finance test classes by converting their existing
hand-drawn `// ---- section ----` comment banners into JUnit5 `@Nested` inner classes. **No test
method bodies, assertions, coverage, or semantics change** — the same tests run, only grouped.

Targets (from the 2026-07-19 test audit — each already has section-banner comments that map directly to
`@Nested` boundaries):

1. **`ledger .../domain/reconciliation/ReconciliationMatcherTest`** (758 lines, 24 tests — the largest):
   banners at base-case / 11th-incr FX base-leg / 13th tolerance / 14th cross-currency-forward /
   19th reverse-cross-currency → `@Nested` classes `BasicMatching` / `BaseLegFx` / `Tolerance` /
   `CrossCurrencyForward` / `CrossCurrencyReverse`.
2. **`ledger .../application/SettleForeignPositionUseCaseTest`** (588 lines, 24 tests) — banners →
   `@Nested` groups (name each group after its banner).
3. **`ledger .../domain/journal/FxSettlementPolicyTest`** (410 lines) — banners → `@Nested`.
4. **`account .../integration/ScopeEnforcementHttpIntegrationTest`** (256 lines, 13 tests) — the
   `deny / allow / entitlement-trust / super-admin wildcard` comment sections → `@Nested`. (This is an
   integration test; only the class structure changes, not the Testcontainers wiring or the HTTP calls.)

## Scope

- **In scope**: the 4 named test files only.
- **Out of scope**: production code; controller-slice base (TASK-FIN-BE-056 — do NOT touch
  `SettlementControllerSliceTest`, that file is owned by 056); the IT-base helpers (already done in
  054/055); the long FX **integration** tests whose `@Test` ordering is load-bearing on a shared
  static container (do NOT @Nested-split those — audit-flagged as state-isolation risk).

## Acceptance Criteria

- **AC-1**: each of the 4 files uses `@Nested` groups; the `// ----` banners are gone (replaced by
  `@Nested` + `@DisplayName`).
- **AC-2**: `@Nested` inner classes are non-static (so they can reach the outer instance's fields/helpers).
- **AC-3**: `./gradlew :ledger-service:test :account-service:test` compiles and the affected classes are
  GREEN; `@Test` count per class is unchanged (the account IT's behaviour is verified by CI's
  Testcontainers lane — local Windows is not authoritative).
- **AC-4**: no production-code change; no assertion/coverage change.

## Edge Cases / Failure Scenarios

- If a class shares a `@BeforeEach`/helper across sections, keep it on the OUTER class (nested classes
  inherit access to the enclosing instance) — do NOT duplicate it into each `@Nested`.
- Do NOT reorder tests or change method bodies — only wrap existing `@Test`s in `@Nested` groups.
- `ScopeEnforcementHttpIntegrationTest` is Testcontainers-backed — the `@Nested` split must not disturb
  the `@SpringBootTest`/container lifecycle (nested classes share the outer context).

## Related

- **TASK-FIN-BE-053** (the converter-test @Nested refactor — same idiom, reference style).
- Memory: `project_console_web_godfile_split_series` (behaviour-preserving structural split discipline),
  `project_testcontainers_docker_desktop_blocker` (CI is the authoritative IT lane).
