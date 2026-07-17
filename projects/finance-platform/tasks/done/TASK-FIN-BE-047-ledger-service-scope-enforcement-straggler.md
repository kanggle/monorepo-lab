# TASK-FIN-BE-047 — ledger-service `/api/finance/**` enforces only `.authenticated()`; the `finance.write` scope rule never reaches enforcement (FIN-BE-046 straggler)

- **Type**: TASK-FIN-BE
- **Status**: done
- **Service**: ledger-service (finance-platform)
- **Domain/traits**: fintech-ish saas / regulated, audit-heavy, transactional
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security authz, regulated money domain)

## Goal

Close the least-privilege gap on ledger-service: `iam-integration.md § Token 검증 규칙 #5` declares every
downstream finance service enforces `finance.read`/`finance.write` per method, but ledger's chain gated
the entire `/api/finance/**` surface with only `.authenticated()`. A `finance.read`-only token could drive
every ledger mutation. This is the sibling of the account-service fix **FIN-BE-046** — the fix was
single-service and never propagated to ledger.

## AC-0 — Finding (audit, verified 2026-07-17)

- **Config**: `ledger-service` `SecurityConfig` = `.requestMatchers("/api/finance/**").authenticated()` only —
  no method (POST/PUT/PATCH/DELETE) discrimination, no `hasAnyAuthority(SCOPE_finance.write)`. Stale javadoc
  claimed *"Read-only API (no mutating endpoints in the first increment). Mirrors account-service exactly."* —
  true at increment 1, but the service since grew ~12 mutating endpoints and the config was never updated.
- **Two layers deep**: even a corrected SecurityConfig would have no scope authority to match, because the
  ledger `ActorContextJwtAuthenticationConverter` lifted only `roles` and **never lifted the `scope` claim**
  into `SCOPE_*` authorities (account-service's converter does, per FIN-BE-046).
- **Blast radius (large, regulated)**: any authenticated `tenant_id=finance` token — including the
  `finance.read`-only platform-console operator read consumer (ADR-MONO-013) — can drive: post manual
  journal entries (`POST /ledger/entries`), book FX gain/loss (`POST /revaluations`, `/settlements`),
  **override the FX rate** (`PUT /settlements/fx-rate-override/{ccy}`), **close accounting periods**
  (`POST /period/{id}/close`), resolve reconciliation discrepancies. A read credential mutating the books
  violates `regulated + audit-heavy`.
- **Vacuous test (why it survived green CI)**: the IT base's happy-path token `financeReadToken()` =
  `tenant_id=finance` + `scope=finance.read`; every mutating IT drove writes with it and asserted success
  (e.g. `LedgerManualPostingIntegrationTest` POST entry → 201, period close → 200). The only negative-token
  test was `crossTenantToken()` (the **tenant** axis). No test exercised the **scope** axis — so the gap was
  invisible. Adding enforcement turns the mutating suite red (self-locking fixture) until it uses a write token.

## Scope

- **In**: ledger `ActorContextJwtAuthenticationConverter` lifts the `scope` claim into `SCOPE_*` authorities
  (mirror account-service); ledger `SecurityConfig` requires `finance.write` on POST/PUT/PATCH/DELETE and
  `finance.read`/`finance.write` on reads (role-OR-scope, mirroring account + the gateway's `roleOrScope`
  admission); regression tests (scope-axis HTTP IT + converter unit test, mutation-checked); migrate the
  mutating ITs to a `finance.write` token.
- **Out**: order/erp/console (separate tickets); the `ProcessedEventStore.isProcessed` global-vs-tenant
  asymmetry (a low, single-tenant-today correctness note, not this ticket); changing the contract (the
  contract already declares this rule — the code just didn't meet it).

## Acceptance Criteria

- **AC-1**: `ActorContextJwtAuthenticationConverter` lifts `scope` (JSON array / space-delimited string /
  `scp` fallback) into `SCOPE_*` authorities; roles unchanged.
- **AC-2**: `SecurityConfig` — POST/PUT/PATCH/DELETE `/api/finance/**` require `SCOPE_finance.write` (or an
  operator role); reads require `SCOPE_finance.read`/`SCOPE_finance.write` (or an operator role). Insufficient
  scope → 403 `PERMISSION_DENIED` via `SecurityErrorHandler`; no token → 401.
- **AC-3**: HTTP IT (`LedgerScopeEnforcementHttpIntegrationTest`, real chain + MockWebServer JWKS) asserts:
  read token → POST → 403 `PERMISSION_DENIED`; read token → GET → not 403; write token → POST/GET → not 403;
  operator role (no scope) → GET → not 403; bare token → POST + GET → 403.
- **AC-4**: Converter unit test (Docker-free) asserts scope lifting for list/string/`scp`; roles+scope both
  lifted; no-scope → no `SCOPE_*`. Mutation-checked (breaking the scope loop turns it RED).
- **AC-5**: The mutating ITs use `financeWriteToken()` (added to the base); pure behavior unchanged (write
  scope admits reads). Full ledger fast+integration lane green.

## Related Specs / Contracts

- `projects/finance-platform/specs/integration/iam-integration.md § Token 검증 규칙 #5` (the declared rule)
- Precedent (the reference sibling): account-service `SecurityConfig` + `ActorContextJwtAuthenticationConverter`
  + `ScopeEnforcementHttpIntegrationTest` (TASK-FIN-BE-046)

## Edge Cases / Failure Scenarios

- `scope` as list vs space-delimited string vs `scp`.
- Operator-role token with no finance scope must still read (console consumer) — role-OR-scope preserved.
- Write scope must admit reads (readAuthorities includes SCOPE_WRITE).
- Breaking a legitimate caller: the sole non-operator caller is the console read consumer (finance.read),
  which only reads — unaffected. Operators carry roles/finance.write.
