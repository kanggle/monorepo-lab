# Task ID

TASK-PC-FE-014

# Title

platform-console consumer adoption of GAP `operatorContext.defaultAccountId` — Phase 2 of `Operator Overview` finance card `MISSING_PREREQUISITE` resolution (option (a) activation)

# Status

done

# Owner

frontend + backend (joint — console-web parser + proxy + console-bff use-case + IT)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
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

---

# Dependency Markers

- **depends on**: TASK-BE-304 (GAP `operatorContext.defaultAccountId` registry-surface emission, **DONE** 2026-05-21 — spec PR #689 `7a531a7d` / impl PR #690 `7e172c50` / close chore PR #691 `816db7fa`). Without Phase 1 merged, the registry response would never carry `operatorContext.defaultAccountId` and Phase 2's parser would only ever observe the omitted case (untestable activation path). Phase 1 was merged sequentially per the established BE-296→FE-002a / FIN-BE-005→FE-009 / ERP-BE-002→FE-010 precedent.
- **origin**: completes the `TASK-PC-FE-011 § Honest gaps (a)` deferred follow-up. `OperatorOverviewCompositionUseCase.java:381-383` currently returns `Optional.empty()` from `resolveOperatorDefaultAccountId()` — wired to MVP option (b) since FE-011. This task activates option (a).
- **prerequisite for**: nothing (this completes the resolution chain).
- **spec-first**: spec PR (this file + `console-integration-contract.md § 2.2 Item shape` + § 2.4.9.1 Implementation guidance activation path subsection) → impl PR (console-web registry parser + dashboard proxy header forward + console-bff use-case + controller header accept + port extension + slice + IT) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): the architectural decision was pre-recorded in `console-integration-contract.md § 2.4.9.1 Implementation guidance` option (a); Phase 1 (TASK-BE-304) realized the GAP producer surface; this Phase 2 activates the consumer side. ADR-MONO-017 D4 HARD INVARIANT (per-domain credential rule, sealed switch) is **not** affected — the new `X-Finance-Default-Account-Id` header is operator profile data flowing alongside credential, never credential itself.

---

# Goal

Activate **option (a)** end-to-end. Currently the `Operator Overview` finance card always renders `forbidden / MISSING_PREREQUISITE` regardless of operator (per `OperatorOverviewCompositionUseCase.callFinance(...)` Javadoc + `resolveOperatorDefaultAccountId() → Optional.empty()`). With TASK-BE-304 merged, the GAP registry response now carries `productItem.operatorContext.defaultAccountId` on the finance product item when the operator's `admin_operators.finance_default_account_id` is set.

This task wires the consumer:

1. **console-web** parses `productItem.operatorContext?.defaultAccountId` from the GAP registry response when building the session catalog, surfaces it via a server-only session helper, and forwards it to the BFF dashboard composition route as a new `X-Finance-Default-Account-Id` request header.
2. **console-bff** `OperatorOverviewController` accepts the new header (optional), passes it through `OperatorOverviewCompositionUseCase.compose(tenantId, financeDefaultAccountId)`, and the use-case's `callFinance(...)` honors a non-blank id by routing through `FinanceBalanceReadPort.readBalances(tenantId, credential, accountId)` (port extension) → `FinanceBalanceReadAdapter.readBalances(...)` (already present, 3-arg overload exists since FE-011). When the header is absent/blank, the existing MVP option (b) path (`forbidden / MISSING_PREREQUISITE`, no outbound HTTP) is preserved verbatim — both paths are first-class behaviors.

The result: a finance card with `data.balances` payload for operators whose admin profile has a `finance_default_account_id` set, and the existing honest `MISSING_PREREQUISITE` rendering for those without.

# Decision authority (why a request header, not a session-stored or in-token claim; why port extension; why both paths first-class)

- **Why a request header (`X-Finance-Default-Account-Id`), not a server session cookie or in-token claim**:
  - **In-token claim**: would require ADR-MONO-014 (RFC 8693 token exchange) amendment — the exchanged operator token's claim set is a producer responsibility (`admin-service` token issuer), and changing it would land cross-cutting changes in GAP `OperatorAccessTokenIssuer` + downstream verifier compatibility. **Rejected** — the value is operator profile data, not authorization data; mixing them couples scopes.
  - **Server-only session cookie** (set at login from the registry response): would centralize the value in the console-web session helpers, but a session shape change has its own ripple (audit, refresh-flow re-issue, cookie size budget). **Rejected for now** — a per-request header sourced from the same registry-fetched data is the smallest blast radius: only the dashboard composition route forwards it, no shell-wide state.
  - **Per-request header**: console-web's dashboard proxy route (`(console)/api/console/dashboards/operator-overview`) already forwards 3 headers (`Authorization`, `X-Operator-Token`, `X-Tenant-Id`) per § 2.4.9.1 console-web obligations. Adding a 4th header from the same server-side context (session helper that reads the registry response) is a minimal symmetric extension. **Chosen.**
- **Why a port extension (`FinanceBalanceReadPort.readBalances(...)`) instead of a special-cased adapter call**: `FinanceBalanceReadAdapter` already has the 3-arg `readBalances(tenantId, credential, accountId)` since FE-011 (when the deferred path was anticipated), but it's not on the port interface — currently the use-case calls `financePort.read(tenantId, bearer)` which throws `UnsupportedOperationException` (an FE-011 anti-pattern marker that this task removes). Adding `readBalances` to the port interface restores cleanness: the use-case depends on the port, not the concrete adapter. The `read(...)` method stays on the port for `DomainReadPort` contract conformance but is no longer the active path on the finance leg.
- **Why both paths first-class (header-present + header-absent)**: a finance card that is `ok` when the operator has a default and `forbidden / MISSING_PREREQUISITE` when they don't is the honest UX. Treating MISSING_PREREQUISITE as "should never happen post-Phase-2" would hide the case that the column is `NULL` for un-provisioned operators (which is the default after V0029 migration). Both branches stay first-class; the slice + IT cover both.
- **Why no admin-api / operator-management mutation surface in scope**: setting `admin_operators.finance_default_account_id` is a future task (operator profile UI/API). For this task's IT, the value is seeded via Testcontainers `@Sql` or direct JDBC insert (same pattern as TASK-BE-304's IT). Operators using the console can only see the activated card when a SUPER_ADMIN seeds their row out-of-band; the console itself does not surface a setter.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.2 Item shape`:
  - Add `operatorContext` row to the consumer-side item shape table mirroring GAP `console-registry-api.md § Item shape` extension (cross-reference, not redefinition).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.9.1 Implementation guidance`:
  - Add a new "**Option (a) activation (Phase 2 — TASK-PC-FE-014)**" subsection after the existing `operatorDefaultAccountId resolution` bullet: describes (i) console-web side — registry parser reads `operatorContext.defaultAccountId`, session helper `getFinanceDefaultAccountId()` exposes it server-side; (ii) console-web side — dashboard proxy route forwards new `X-Finance-Default-Account-Id` header to console-bff; (iii) console-bff side — `OperatorOverviewController` accepts the optional header and threads it through `compose(tenantId, financeDefaultAccountId)`; (iv) `callFinance(...)` routes through `FinanceBalanceReadPort.readBalances(...)` when the id is non-blank, otherwise preserves the existing MISSING_PREREQUISITE path.
  - Note: the **two paths are both first-class** (header-present = finance card `ok`; header-absent = finance card `forbidden / MISSING_PREREQUISITE`).

**Code (impl PR)**:

- **console-bff** (`projects/platform-console/apps/console-bff/`):
  - `OperatorOverviewController.java`: add `@RequestHeader(value = "X-Finance-Default-Account-Id", required = false) String financeDefaultAccountId` to the GET handler signature; forward it to `compositionUseCase.compose(tenantId, financeDefaultAccountId)`.
  - `OperatorOverviewCompositionUseCase.java`:
    - New `compose(String tenantId, String financeDefaultAccountId)` overload; keep the existing 1-arg `compose(String tenantId)` as a thin pass-through (`compose(tenantId, null)`) for backward-compat with any direct in-process caller.
    - Replace `resolveOperatorDefaultAccountId()` stub: take the new parameter, return `Optional.of(trimmed)` when `hasText(financeDefaultAccountId)`, else `Optional.empty()`. Or: refactor to thread the value directly into `callFinance(tenantId, cred, financeDefaultAccountId)` and inline the gate (cleaner).
    - `callFinance(tenantId, cred, accountId)`: when `hasText(accountId)`, call `financePort.readBalances(tenantId, bearer, accountId)` (new port method); else emit `MISSING_PREREQUISITE` (existing path). Remove the comment block referencing the deferred state and update it to reflect both paths first-class.
  - `OperatorOverviewCompositionUseCase.FinanceBalanceReadPort`: add `Map<String, Object> readBalances(String tenantId, String credential, String accountId);` method to the interface. The existing `read(tenantId, credential)` stays for `DomainReadPort` contract conformance.
  - `FinanceBalanceReadAdapter.java`: no code change — the 3-arg `readBalances(...)` already exists since FE-011 (line 79). Javadoc update: remove the "Option (a) deferred" framing; replace with "When the use-case supplies a non-blank `accountId` (the operator's `finance_default_account_id` from `admin_operators` via the registry round-trip)" framing. The `read(...)` stays as `UnsupportedOperationException` (marker that the active path is `readBalances`, not direct port use).
- **console-web** (`projects/platform-console/apps/console-web/`):
  - `src/shared/lib/session.ts` (or equivalent): add `getFinanceDefaultAccountId()` server-only helper that reads the registry response stored in the session (the registry is already fetched and stored at login — find the path; if not stored, store it). Returns `string | null`. **Server-only** (`server-only` import or `import 'server-only'` at the top of the helper file).
  - `src/features/catalog/registry-client.ts` (or wherever the registry response is consumed): parse `operatorContext.defaultAccountId` from the finance product item (zod schema extension); the parsed value is stored in the session for `getFinanceDefaultAccountId()` to surface.
  - `src/app/(console)/api/console/dashboards/operator-overview/route.ts` (the dashboard proxy server route): forward `X-Finance-Default-Account-Id` if non-blank, derived from `getFinanceDefaultAccountId()`. Mirror the existing `getOperatorToken()` / `getActiveTenant()` / `getAccessToken()` server-only-helper pattern. **No browser-direct call** — the header is set on the server-side `fetch` only.
- **Tests**:
  - **console-bff unit**: extend `OperatorOverviewCompositionUseCaseTest` with 2 new cases — (a) `compose("tenant", null)` → finance leg `forbidden / MISSING_PREREQUISITE`, **no outbound HTTP** (verify via mock); (b) `compose("tenant", "acc-uuid-7")` → finance leg `ok` with mocked balance data, outbound HTTP fired exactly once with `accountId="acc-uuid-7"`. Existing tests preserved (some may need to be updated to call the 2-arg overload).
  - **console-bff slice**: extend `OperatorOverviewSliceTest` (or whatever the controller slice is named) — assert 200 response shape when `X-Finance-Default-Account-Id: acc-uuid-7` is set + finance leg returns ok; assert MISSING_PREREQUISITE when header omitted.
  - **console-bff IT**: extend `OperatorOverviewIntegrationTest` with 2 new cases — (a) header omitted → finance card `forbidden / MISSING_PREREQUISITE`, no outbound fired against the finance MockWebServer leg (use snapshot-and-diff request count pattern, mirroring `DomainHealthIntegrationTest`); (b) header set to a UUID → finance card `ok` with the MockWebServer's stubbed balance payload, exactly 1 request fired against the finance leg with path `/api/finance/accounts/<uuid>/balances`. **STRENGTHEN-ONLY**: existing tests covering the MISSING_PREREQUISITE path must continue to pass — the absent-header path is preserved.
  - **console-web vitest**: 4 new test cases —
    - `registry-client.test.ts`: registry response with `productItem[finance].operatorContext.defaultAccountId = "acc-uuid-7"` parsed into session; with no `operatorContext` parsed as null.
    - `session.test.ts` (or `getFinanceDefaultAccountId.test.ts`): server-only helper returns the stored value / null.
    - `overview-proxy.test.ts`: dashboard proxy route fetch invocation receives `X-Finance-Default-Account-Id` header when session has a value; absent when null.
    - `OperatorOverviewScreen.test.tsx` (or per-card test): finance card renders `data.balances`-derived display when leg is ok; renders MISSING_PREREQUISITE placeholder when leg is forbidden. The existing card UI does not need changes (the rendering switch is already in place from FE-011).

## Out of Scope

- **Admin-api / operator-management mutation surface** to write `admin_operators.finance_default_account_id`. The column lands NULL by default; IT seeds values via JDBC; production operators see the activated card only when SUPER_ADMIN seeds the value out-of-band. A separate future task may add a setter (admin-api `PATCH /api/admin/operators/{id}/finance-default-account-id` or similar).
- **In-token claim** — rejected per § Decision authority.
- **Server-side session cookie of the value** — rejected per § Decision authority (per-request header is the smallest blast radius).
- **Finance card UI change** — the existing FE-011 `<DomainCard>` already branches on `ok` / `degraded` / `forbidden`; the `ok` branch renders the leg's `data` payload. No new component needed.
- **ProductCatalog `available: false → true` for finance/erp** (BE-302 pattern reality alignment) — still out of scope (separate task).
- **`operatorContext.*` activation on other products** (wms `defaultWarehouseId`, scm `defaultNodeId`, erp `defaultDepartmentId`) — the spec PR reserves the shape per TASK-BE-304, but no other product populates it in v1; future tasks per product.
- **GAP-side validation of the account id against finance-platform** — out of scope (cross-service decoupling preserved; stale ids surface as finance `404 ACCOUNT_NOT_FOUND` honestly, console renders that leg as `degraded`).
- **ADR-MONO-013 / ADR-MONO-017 / ADR-MONO-014 / ADR-MONO-015 amendment** — none required (§ Decision authority "no ADR").

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands `console-integration-contract.md § 2.2 + § 2.4.9.1 activation subsection` + this task md only; no production code in the spec PR.
- **AC-2 (header-absent path preserved)**: omitting `X-Finance-Default-Account-Id` results in finance card `forbidden / MISSING_PREREQUISITE` exactly as it did pre-Phase-2 (regression guard: existing 10-IT happy-path / 401-cross-leg / per-leg-forbidden / per-leg-degraded IT continue to pass without modification; the new "happy-with-header" IT is additive).
- **AC-3 (header-present happy path)**: `X-Finance-Default-Account-Id: <uuid>` → finance card `ok` with `data` payload matching the finance leg's MockWebServer-stubbed balance JSON; exactly 1 outbound request fired against the finance leg with path `/api/finance/accounts/<uuid>/balances` and `Authorization: Bearer <gap-oidc-token>` header (verified via `MockWebServer.takeRequest()` + path/header assertions).
- **AC-4 (port extension clean)**: `FinanceBalanceReadPort` interface now declares `readBalances(...)`; the use-case calls the port method, not the concrete adapter. No direct `instanceof` / cast on the port in the use-case. Adapter's `read(...)` stays as `UnsupportedOperationException` (the marker that the active path is `readBalances`).
- **AC-5 (D4 HARD INVARIANT preserved)**: `CredentialSelectionAdapter` sealed switch unchanged (`git diff origin/main -- projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/adapter/outbound/http/CredentialSelectionAdapter.java` = empty). `bearerFromCred()` sealed switch (`OperatorOverviewCompositionUseCase` line ~368) unchanged. The new `X-Finance-Default-Account-Id` header is operator profile data flowing on the request, never on the credential dispatch.
- **AC-6 (5 producers + 4 other dashboards untouched)**: `git diff --stat origin/main -- 'projects/{wms,scm,finance,erp,fan,ecommerce}-platform/'` = empty. `git diff --stat origin/main -- projects/global-account-platform/` = empty (GAP work is Phase 1, already merged). Domain Health dashboard (TASK-PC-FE-013) controller / use-case / adapters / tests = byte-unchanged.
- **AC-7 (cross-leg 401 discipline preserved)**: when the finance leg is wired but another leg returns 401, the composition emits 401 `TOKEN_INVALID` per § 2.4.4 D3 (not "finance ok + others 401" partial-authed state). Existing AC-10 from FE-011 covers this; the new path must not break it.
- **AC-8 (no PII / no credential logging)**: the new header value (an opaque finance account UUID) is **internal**, not PII / not credential; nonetheless it must not be logged at INFO level (per finance F7 / `regulated.md` R7 transitive discipline). Verified by structured-log absence assertion in the relevant logger test, or grep `grep -r "financeDefaultAccountId\|X-Finance-Default-Account-Id" projects/platform-console/apps/console-bff/src/main/` showing no `log.info(...)` literal containing the value.
- **AC-9 (CI green)**: self-CI 20/20 GREEN at impl-PR merge time. `Integration (platform-console console-bff, Testcontainers + WireMock JWKS)` + `Frontend unit tests` PASS. **BE-303 3-dim verified at close chore** per [`CLAUDE.md § Task Rules`](../../../../CLAUDE.md): (a) `state=MERGED` + `statusCheckRollup` 0 failing; (b) `git log origin/main` tip matches; (c) pre-merge `gh pr checks` 0 failing.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.2 Item shape` + § 2.4.9.1 Implementation guidance (extended in this task).
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md § Per-operator profile attributes` (Phase 1 producer contract — read-reference, not modified here).
- `projects/global-account-platform/specs/services/admin-service/data-model.md § admin_operators` (`finance_default_account_id` column — Phase 1, read-reference).
- `projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/application/usecase/OperatorOverviewCompositionUseCase.java` (`callFinance` + `resolveOperatorDefaultAccountId` — modified in this task).
- `projects/platform-console/specs/services/console-bff/architecture.md` — Identity / Service Type Composition unchanged; no new layer.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — the consumer contract being extended.
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — the producer contract (read-reference; **byte-unchanged in this task** per AC-6).

# Edge Cases

- **`X-Finance-Default-Account-Id` set to an empty string** → treated as absent (`hasText` false). Finance card → `MISSING_PREREQUISITE`. No outbound HTTP fired.
- **`X-Finance-Default-Account-Id` set to whitespace** → same as empty (`hasText` false).
- **`X-Finance-Default-Account-Id` set to a malformed UUID** → BFF forwards it opaquely to finance via path template; finance returns `404 ACCOUNT_NOT_FOUND` (or `400` on its own validation). The leg surfaces as `degraded / DOWNSTREAM_ERROR` (404 → existing classification per `time()` catch block) — this is the **honest UX**: the operator profile has a stale id; the console shows the leg failed, not a fabricated `ok`. (Adding GAP-side validation is out of scope per § Decision authority.)
- **`X-Finance-Default-Account-Id` set, finance leg returns 5xx** → leg `degraded / DOWNSTREAM_ERROR`; existing degrade classification applies; counter `bff_aggregation_degrade_count_total{...,degraded_domain=finance}` increments.
- **`X-Finance-Default-Account-Id` set, finance leg returns 401** → cross-leg 401 discipline: composition emits 401 `TOKEN_INVALID` (AC-7 / § 2.4.4 D3); the header is irrelevant to the auth boundary.
- **console-web has no registry response in session** (e.g. registry fetch failed at login) → `getFinanceDefaultAccountId()` returns `null` → header omitted → MISSING_PREREQUISITE path. The shell continues to render normally per existing § 2.5 degraded catalog handling.
- **Multi-tenant operator switches tenant** (active tenant changes mid-session) → the registry response is per-operator (not per-tenant); the `finance_default_account_id` is on the operator row, not per-tenant. Switching tenants does **not** invalidate the value. The header continues to be sent.
- **Operator's `admin_operators.finance_default_account_id` updated mid-session** → console-web has the old value cached in session; the next registry refresh (at login / refresh / explicit re-fetch) picks up the new value. This is the same staleness window as `tenants` array changes — accepted (no live invalidation).
- **`@JsonInclude.NON_NULL` consumer-side parsing** (Phase 1 verified producer-side): the absent `operatorContext` field on a product item must parse to `undefined` on the console-web side, never throw. zod schema: `operatorContext: z.object({ defaultAccountId: z.string().optional() }).optional()`.

# Failure Scenarios

- **The use-case calls `financePort.read(...)` instead of `readBalances(...)`** (regression from this task's intent) → `UnsupportedOperationException` thrown → `time()` catch arm classifies as `degraded / DOWNSTREAM_ERROR` → all happy-path IT FAIL (the `MISSING_PREREQUISITE` path is silently masked). **Reject in review** if `financePort.read(` appears anywhere in `callFinance` post-PR.
- **The header is forwarded from console-web's `OperatorOverviewScreen` (client component) instead of the server proxy route** → SSR/CSR boundary violation; the value leaks into the client bundle. **Reject in review** if `X-Finance-Default-Account-Id` appears in any non-server-route file under `apps/console-web/src/`.
- **The header is added to `getActiveTenant()` or other shared session helpers without the `getFinanceDefaultAccountId()` distinct boundary** → coupling violation. **Reject** — distinct helper, distinct concern.
- **The new path enables a `Number()` / `parseFloat()` on the finance balance amount** (F5 money discipline regression) → existing on-disk grep guard catches it (FE-009 finance-api.test.ts).
- **The use-case overload swallows the existing 1-arg `compose(tenantId)` signature** → existing in-process direct callers (if any) break. **Mitigation**: keep the 1-arg as a thin pass-through `compose(tenantId, null)` (Decision authority).
- **The `OperatorOverviewCompositionUseCaseTest`'s existing MISSING_PREREQUISITE assertion is weakened** (e.g. `assertThat(reason).isIn("MISSING_PREREQUISITE", "DOWNSTREAM_ERROR")`) to accommodate a mis-wired new path → **strengthen-only violation**, reject in review. The existing assertion stays narrow; the new test adds an independent assertion for the new path.
- **Phase 1 producer regression** (`operatorContext` field omitted from JSON when it should be set) → out of this task's diagnostic scope; would surface as `AC-3 IT FAIL` here, but the fix is on the GAP side, not Phase 2. Honest discovery → escalate to a GAP fix-task, do not paper over in Phase 2.

# Verification

1. Spec PR diff: exactly 2 files (`console-integration-contract.md` + task md). `git diff --stat origin/main -- projects/platform-console/apps/` is **empty**.
2. Impl PR diff: code + tests under `projects/platform-console/apps/console-bff/` + `projects/platform-console/apps/console-web/` only; AC-5 + AC-6 grep verify zero diff outside.
3. `./gradlew :console-bff:test :console-bff:integrationTest` green; new 2 unit + 1+ slice + 2 IT cases pass; existing 10 IT continue green (AC-2).
4. `pnpm vitest run` (console-web) green; new 4 cases pass; existing cases unaffected.
5. Self-CI 20/20 GREEN at impl-PR merge time; BE-303 3-dim verified at close chore start.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (joint frontend+backend dispatch — Next.js server-component-first registry parsing + zod schema extension + session helper boundary + server-route header propagation + Spring Boot RestController header parameter + sealed-switch-preserving use-case overload + port interface extension + MockWebServer IT happy/forbid path coverage; multiple integration seams across the FE↔BFF↔producer chain; deserves Opus judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-2/3/4/5/6/7/8 grep + IT happy-path AND missing-path 보존 검증 + BE-303 3-dim).
