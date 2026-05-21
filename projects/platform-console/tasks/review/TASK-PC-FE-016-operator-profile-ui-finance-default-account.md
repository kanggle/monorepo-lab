# Task ID

TASK-PC-FE-016

# Title

platform-console operator-profile self-serve UI — `finance_default_account_id` setter (`PATCH /api/admin/operators/me/profile` proxy + form) — Phase 3 of `Operator Overview` finance card `MISSING_PREREQUISITE` resolution (option (a) self-provisioning UX)

# Status

review

# Owner

frontend

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

- **depends on**: TASK-BE-306 (GAP admin-service `PATCH /api/admin/operators/me/profile` self-serve mutation endpoint, **DONE** 2026-05-21 — spec PR #704 `bae27cff` / impl PR #705 `22952bfd` / close chore #706 `e958e4d4`). Without Phase 2 (this is **Phase 3**, actually Phase 2 by counting BE-306 as the producer; BE-306 was authored as "Phase 1 write-path sister of BE-304 read-path", so the chain is BE-304 read + BE-306 write + PC-FE-014 read consumer + PC-FE-016 write consumer = 4-leg vertical slice). Without BE-306 merged, this task's proxy would call a producer endpoint that returns 404.
- **depends on (indirectly)**: TASK-BE-304 (V0029 column) + TASK-PC-FE-014 (read consumer surfacing `operatorContext.defaultAccountId` on the registry surface). The read side is already activated end-to-end; this task adds the write side so the operator can self-provision through the console UI rather than DBA SQL.
- **origin**: TASK-BE-306 § Out of Scope explicit: *"Console-bff orchestrator endpoint (`PATCH /api/console/operators/me/profile`) — separate task `TASK-PC-BE-004`. Console-web UI input + save handler — separate task `TASK-PC-FE-016` after the console-bff orchestrator merges."* — but the console-bff orchestrator (PC-BE-004) is **NOT NEEDED**: ADR-MONO-017 D2 ("Server-side fan-out only") establishes that console-bff composes cross-domain reads; per-domain mutations follow the existing console-web → GAP gateway proxy pattern (the `me/password` precedent at `apps/console-web/src/app/api/operators/me/password/route.ts` is the established shape — same operator JWT bearer, same direct gateway forward, no console-bff hop). The originally-anticipated PC-BE-004 is therefore **architecturally redundant** and is **explicitly skipped**; this PC-FE-016 task ships the consumer adoption end-to-end (proxy route + UI + tests) using the GAP gateway directly.
- **prerequisite for**: nothing (this completes the BE-304 → BE-306 → PC-FE-016 vertical slice; the Operator Overview finance card now has both a read activation path (PC-FE-014) and a self-serve write activation path (PC-FE-016)).
- **spec-first**: spec PR (this file + `console-integration-contract.md § 2.4.3` operations table + per-endpoint header matrix + § 3 parity matrix attestation-marker count increment) → impl PR (proxy route + `_proxy.ts` schema + `operators-api.ts` function + `MyProfileForm.tsx` + `OperatorsScreen.tsx` integration + 4 unit / vitest tests + 1 Playwright e2e) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): the architectural decision is **already recorded** in ADR-MONO-017 D2 (Server-side fan-out only — mutations stay per-domain) + ADR-MONO-013 D5 (the `me/password` precedent establishes that self-serve `me/*` mutations on the GAP admin-api surface are console-web direct proxies). This task is the verbatim application of that established pattern to a new operator-profile attribute.

---

# Goal

Complete the operator self-provisioning UX for the `Operator Overview` finance card. The BE-304 column + BE-306 write endpoint + PC-FE-014 read consumer make the system *internally complete* — but an operator still cannot reach the write path: there is no UI input, no save handler, no console-side proxy. The finance card therefore renders `forbidden / MISSING_PREREQUISITE` for every operator whose `admin_operators.finance_default_account_id` was not seeded out-of-band by a DBA.

Activate the write path end-to-end:

1. **console-web proxy route** (`/api/operators/me/profile/route.ts`) — same shape as `me/password`: PATCH-only, zod-validated body, calls `updateOwnProfile(input)` from `features/operators/api/operators-api.ts`, returns 204 No Content on success. **Direct forward to GAP `PATCH /api/admin/operators/me/profile`** via the existing `callGapOperators(...)` hardened call site (operator JWT bearer + active tenant header + no reason, no idempotency-key — the producer's self-serve self-flow exemption).
2. **`_proxy.ts` `UpdateProfileBodySchema`** — zod-validated request body: `{ operatorContext: { defaultAccountId: string | null } }`; defaultAccountId when string must be trimmed non-empty, length ≤ 36, no internal whitespace, no control chars. (The producer is the final authority — this zod is a UX pre-check; the producer's `400 INVALID_REQUEST` is always honored.)
3. **`features/operators/api/operators-api.ts` `updateOwnProfile(input)`** — mirror `changeOwnPassword`: calls `callGapOperators({ method: 'PATCH', path: '${OPERATORS_PREFIX}/me/profile', body: { operatorContext: { defaultAccountId: input.defaultAccountId } }, expectNoContent: true })`. Self path. NO `X-Operator-Reason`. NO `Idempotency-Key`.
4. **`MyProfileForm.tsx`** (new component, sibling of `ChangePasswordForm`) — single text input for "기본 finance 계정 ID" (UUID-like string, optional/nullable) + Save button + Clear button. State management mirrors `ChangePasswordForm`. Inline server error from last attempt. Server-rendered initial value from the registry response (`operatorContext.defaultAccountId` already present on the catalog from PC-FE-014).
5. **`OperatorsScreen.tsx` integration** — render `<MyProfileForm />` alongside `<ChangePasswordForm />` in the operators section's self-serve sub-section (line ~296 area; same `useMutation` wiring pattern).
6. **Tests** — unit (vitest): 4 cases on `MyProfileForm` (initial render with seeded value / save valid / save with whitespace / save with explicit null clear); 1 unit on the proxy `route.ts` (POST validates body, returns 204 on success, returns 400 on invalid body, returns mapError on producer error); 1 unit on `operators-api.ts.updateOwnProfile` (asserts the call shape — method, path, body, no extra headers); 1 Playwright e2e (`tests/e2e/operators-profile.spec.ts`) — operator login → navigate `/operators` → enter account UUID → save → 204 confirmed → navigate `/dashboards/overview` → finance card renders `ok` with balance data (the read-side activation observable, validating the full vertical slice).

After this task lands, the `Operator Overview` finance card behavior is fully self-serve: an operator can provision their own default account through the console UI without DBA intervention, and the value round-trips through GAP → registry → BFF → finance leg with no producer or BFF changes (zero-retrofit confirmation for the 8th time across Phase 2/4/5/6/7-skeleton/7-MVP/7-health/this Phase 7 write-path).

# Decision authority (why console-web direct proxy, why MyProfileForm not MyProfileScreen, why same OperatorsScreen integration)

- **Why console-web direct proxy (NOT console-bff orchestrator)**: ADR-MONO-017 § D2 chose **Option A — Server-side fan-out only** (CHOSEN PROPOSED direction): *"BFF is the sole cross-domain composer. `console-web` calls BFF endpoints for cross-domain views and continues to call per-domain endpoints directly for single-domain sections (existing FE-001..010 routes are NOT relocated)."* This task's mutation is **single-domain** (GAP admin-api only), not cross-domain, so console-bff is bypassed. Additionally, ADR-MONO-017 § 2.4.9.1 / § 2.4.9.2 hard invariant: *"The route is GET only — read-only. Adding a mutation surface requires a fresh ADR amendment to ADR-MONO-017."* The console-bff is explicitly read-only on its composition routes; mutation paths must stay on the per-domain proxies. The `me/password` precedent (FE-006 / PC-FE-004) is the established shape — same operator JWT bearer, same direct gateway forward, no console-bff hop. **PC-BE-004 (originally anticipated as a sequential follow-up in TASK-BE-306) is therefore NOT NEEDED and is explicitly skipped here.**
- **Why `MyProfileForm` (small composable component), not `MyProfileScreen` (new route)**: the self-serve `me/password` precedent already lives inline at the bottom of `OperatorsScreen` as `<ChangePasswordForm />` (line ~298). Same pattern applies: a single small form composable next to the password form. A separate `/operators/me/profile` route would force a new nav entry, new layout decision, and would split self-serve actions across pages — anti-pattern for the operator UX. A single screen containing all self-serve actions (password + future profile attributes) is the natural grouping.
- **Why the proxy is `POST` to the Next.js route (not `PATCH`)**: the `me/password` precedent uses `POST` on the Next.js proxy route even though the GAP producer expects `PATCH` — Next.js route handlers translate the inbound method, and `POST` is the established pattern across all `_proxy.ts`-using routes (avoids confusion with the producer's verb). This task follows the same convention: `POST /api/operators/me/profile` (client → console-web proxy) → `PATCH /api/admin/operators/me/profile` (console-web → GAP gateway). The body shape is the same on both legs; only the method-translation step at the proxy boundary.
- **Why the form's input is a free-text string, not a finance-account picker dropdown**: § Decision authority in TASK-BE-304 records: *"GAP carries the value as opaque (`VARCHAR(36)`, validated only as a non-null finite string when set); GAP does NOT verify the id exists in finance."* The opacity is intentional (cross-service decoupling). A picker dropdown would require either (a) a finance-side list endpoint (not in v1) or (b) cross-service call from console-web → finance, both of which violate the decoupling. Free-text input + honest UI guidance ("if this account id is invalid, the finance card will surface a `404 ACCOUNT_NOT_FOUND` honest degrade after save") is the right level of cross-coupling for v1.
- **Why no admin-on-behalf-of UI in scope**: BE-306 is self-serve only by producer design. An admin "set this operator's default account on their behalf" is a strictly larger surface (cross-tenant audit, target_id != self, `X-Operator-Reason` required, broader audit row policy) and a future task. v1 self-serve UX matches the v1 producer surface verbatim.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3 GAP operators surface`:
  - Operations table (currently 5 rows, lines 114-120): add row 6 — `PATCH /api/admin/operators/me/profile` (operator self profile mutation, kind = mutation (self), required permission = "(valid operator token)").
  - Per-endpoint header matrix table (currently 5 rows, lines 126-132): add row 6 — `PATCH .../me/profile | — | — | self path; valid operator token only (no X-Operator-Reason, no Idempotency-Key per producer; mirrors me/password)`.
  - Mutation audit / Password safety paragraphs (lines 136-137): byte-unchanged (the new endpoint is not a privileged/audited mutation in the elevated-confirm sense — it carries operator profile data, not credential data, and the self-flow exemption applies; the producer-side audit row is recorded but the UI does NOT require an X-Operator-Reason / elevated-confirm gate).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 3 parity matrix`:
  - Currently 16 attestation-marker rows (per § 2.4.10.0 attestation-marker count invariant). Add row 17 — `operators: change-profile | features/operators / updateOwnProfile | § 2.4.3 | PATCH /api/admin/operators/me/profile (§ admin-api L_NEW, self only — no admin-set-other) | M (self) | no reason, no idem (self path; mirrors me/password) | verified by TASK-PC-FE-016`. Update the `parity-verification.test.ts` no-drift guard expected count from **16 → 17** (one-line constant change in the test; the test stays a no-drift guard, just for the new count).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.10.0 attestation-marker count invariant`: byte-unchanged narrative paragraph; the *number* in the test source updates atomically with the row addition (spec PR holds both edits as atomic — the test source change is included in the spec PR if reasonable, or in the impl PR if the spec PR is held to "spec files only"; chosen at author).
- This task file itself.

**Code (impl PR)**:

- `projects/platform-console/apps/console-web/src/app/api/operators/_proxy.ts`: add `UpdateProfileBodySchema = z.object({ operatorContext: z.object({ defaultAccountId: z.string().nullable() }).strict() }).strict()`. The `.strict()` enforces no extra keys (the `FAIL_ON_UNKNOWN_PROPERTIES` mirror on the consumer). The `defaultAccountId` when a string is **not** further validated here — the producer is the final authority; the proxy passes it through. Empty-string is **rejected** at the proxy (return 422 VALIDATION_ERROR; the producer would also reject — fail fast). Whitespace-trimming happens at the producer.
- `projects/platform-console/apps/console-web/src/app/api/operators/me/profile/route.ts` (new — mirror `me/password/route.ts` line for line): `POST` handler, `runtime = 'nodejs'`, parse body via `UpdateProfileBodySchema`, call `updateOwnProfile(...)` from `features/operators/api/operators-api.ts`, return `new NextResponse(null, { status: 204 })` on success, `mapError(err, requestId)` on failure.
- `projects/platform-console/apps/console-web/src/features/operators/api/operators-api.ts`: add `updateOwnProfile(input: UpdateProfileInput): Promise<void>` mirroring `changeOwnPassword` line for line. Input type: `{ defaultAccountId: string | null }` (the outer carrier is the producer's shape; the function constructs the nested `operatorContext: { defaultAccountId }` body). Call shape: `callGapOperators({ method: 'PATCH', path: '${OPERATORS_PREFIX}/me/profile', body: { operatorContext: { defaultAccountId: input.defaultAccountId } }, expectNoContent: true })`. NO `X-Operator-Reason`. NO `Idempotency-Key`.
- `projects/platform-console/apps/console-web/src/features/operators/components/MyProfileForm.tsx` (new — sibling of `ChangePasswordForm.tsx`): client component, single text input ("기본 finance 계정 ID"), Save button, Clear button (sets the value to `null` explicitly). Server-rendered initial value passed as prop (from `getCatalog()` → `operatorContext.defaultAccountId`). Inline server error after a failed submit (mapped from producer code `INVALID_REQUEST` / `OPTIMISTIC_LOCK_CONFLICT` / `TOKEN_INVALID` per § 2.5 resilience). UX safety: do NOT auto-save on input change — explicit Save. Pending state during the call. Success state clears the form's "edited" flag (the underlying input stays populated with the new value).
- `projects/platform-console/apps/console-web/src/features/operators/api/types.ts` (or wherever): add `UpdateProfileInput` type + any error-mapping helper.
- `projects/platform-console/apps/console-web/src/features/operators/components/OperatorsScreen.tsx`: import `MyProfileForm`. Add a `useMutation` for `updateOwnProfile` (mirror `changePw.mutate`). Render `<MyProfileForm initial={initialDefaultAccountId} onSubmit={(v) => updateProfile.mutate({ defaultAccountId: v })} ... />` below `<ChangePasswordForm />`. Wire the initial value from a new prop `initialDefaultAccountId?: string | null` (parent route `app/(console)/operators/page.tsx` passes it from `getCatalog()` registry).
- `projects/platform-console/apps/console-web/src/app/(console)/operators/page.tsx`: extract `operatorContext.defaultAccountId` from the registry response (`getCatalog()` already returns the catalog; find the finance product item's `operatorContext.defaultAccountId`, default to `null`). Pass as new prop `initialDefaultAccountId` to `<OperatorsScreen />`.
- `projects/platform-console/apps/console-web/src/shared/api/registry-types.ts` (or wherever the catalog zod schema lives): already extended in TASK-PC-FE-014 to parse `operatorContext.defaultAccountId`; **byte-unchanged** in this task (the read side is already wired). The shape is `{ products: [{ key: "finance", ..., operatorContext?: { defaultAccountId?: string } }, ...] }`.
- **Tests** (impl PR — vitest + Playwright):
  - `apps/console-web/tests/unit/features/operators/MyProfileForm.test.tsx`: 4 cases — (a) initial render with `initial="acc-uuid-7"` → input value is `acc-uuid-7`; (b) edit value + Save → calls `onSubmit({ defaultAccountId: "<new-uuid>" })`; (c) Clear button → calls `onSubmit({ defaultAccountId: null })`; (d) whitespace-only Save → does NOT call `onSubmit`, surfaces a client-side error message.
  - `apps/console-web/tests/unit/api/operators/me-profile-route.test.ts`: 3 cases — (a) POST valid body → 204; (b) POST invalid body (extra key) → 422; (c) downstream throw → 500/503 via `mapError`.
  - `apps/console-web/tests/unit/features/operators/operators-api-update-profile.test.ts`: 2 cases — (a) call shape (method=PATCH, path=`/api/admin/operators/me/profile`, body shape matches producer expectation, NO `X-Operator-Reason`, NO `Idempotency-Key`); (b) operator JWT bearer present.
  - `apps/console-web/tests/e2e/operators-profile.spec.ts` (Playwright): operator login → `/operators` → input account UUID → Save → 204 confirmed (or success toast) → navigate `/dashboards/overview` → finance card renders `ok` with balance data. This e2e validates the full vertical slice (BE-304 + BE-306 + PC-FE-014 + this PC-FE-016).
  - `apps/console-web/tests/unit/specs/parity-verification.test.ts`: update the expected attestation-marker count from `16 → 17` (one-line constant).

## Out of Scope

- **Console-bff orchestrator (`PATCH /api/console/operators/me/profile`)** — NOT NEEDED per § Decision authority "Why console-web direct proxy". ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant ("GET only — read-only") explicitly rule this out for mutations; the originally-anticipated TASK-PC-BE-004 is **canceled**.
- **Admin-on-behalf-of UI (SUPER_ADMIN setting another operator's `finance_default_account_id`)** — future task. v1 matches BE-306's self-serve-only producer surface.
- **Picker dropdown that lists finance accounts** — would require either a finance-side list endpoint (not v1) or cross-service call from console-web → finance; both violate the BE-304 GAP↔finance decoupling. Free-text input + honest degrade UX is v1.
- **Other `operatorContext` fields (`wmsDefaultWarehouseId`, `scmDefaultNodeId`, etc.)** — the form accepts only `defaultAccountId` in v1, matching BE-306's `FAIL_ON_UNKNOWN_PROPERTIES = true` producer-side rejection of unknown keys under `operatorContext`. A future task adds a per-attribute form section as additional `operatorContext` keys are activated.
- **Validation against finance-platform from console-web** — BE-306 § Decision authority "Why `validation = opaque on producer`" applies symmetrically to the UI. The console does not call finance to verify the id; a stale value surfaces honestly as a finance card `404 ACCOUNT_NOT_FOUND` post-save when the user returns to `/dashboards/overview`. The Save button does NOT pre-flight a finance call.
- **Audit row visibility in the console** — `admin_actions` rows are written producer-side per BE-306 (`action_code = "OPERATOR_PROFILE_UPDATE"`, `reason = "<self_profile_update>"`), but exposing the operator's own audit log into the console is a separate future task (no parity-line requirement for self-action audit).
- **ADR amendment** — none. The architectural pattern is pre-recorded (ADR-MONO-017 D2 Option A + ADR-MONO-013 D5 me/password precedent). HARDSTOP-09 not triggered.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: a single spec PR lands `console-integration-contract.md § 2.4.3` + `§ 3` parity matrix edits + this task file with no production code (no `apps/console-web/src/**` changes). Atomic per the BE-296 / FE-002a / FIN-BE-005 / ERP-BE-002 / BE-304 / BE-306 spec-first pattern. The `parity-verification.test.ts` source-side constant update may go in either the spec PR (preferred — atomic with the parity matrix row addition) or the impl PR (acceptable if the spec PR is held to spec-only). Decision recorded at author.
- **AC-2 (proxy route shape)**: the new `/api/operators/me/profile/route.ts` is structurally identical to `/api/operators/me/password/route.ts` (line count within ±20%, same imports from `_proxy.ts`, same `runtime = 'nodejs'`, same `mapError` + `newRequestId` flow). Verified by a vitest source-shape grep test (mirrors the existing `parity-verification.test.ts` shape assertions).
- **AC-3 (no console-bff change)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant preserved. Verified by `git diff --stat origin/main -- projects/platform-console/apps/console-bff/src/` returning empty.
- **AC-4 (no GAP producer change)**: 0 byte diff across `projects/global-account-platform/**` in the impl PR. BE-306 is already merged; this consumer task does not touch the producer. Verified by `git diff --stat origin/main -- projects/global-account-platform/` returning empty.
- **AC-5 (zero-retrofit other producers)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/`. Eighth confirmation of ADR-MONO-013 § 3.3 zero-retrofit (Phase 2/4/5/6/7-skeleton/7-MVP/7-health/this 7-write).
- **AC-6 (D4 HARD INVARIANT preserved)**: 0 byte diff across the credential-related console-bff files and ADR-MONO-017 D1-D8. D4 governs per-domain *outbound credential* on console-bff fan-out legs; this task uses no console-bff path. Verified by ADR diff empty.
- **AC-7 (parity matrix count)**: `parity-verification.test.ts` expected attestation-marker count = **17** post-merge (was 16). Test PASSES.
- **AC-8 (full vertical slice working)**: the Playwright e2e `operators-profile.spec.ts` PASSES — operator can save a value, the registry round-trips it, the finance card on `/dashboards/overview` renders `ok` with balance data after the save. **This is the gate for "Operator Overview finance card MISSING_PREREQUISITE resolution chain COMPLETE"**.
- **AC-9 (self-CI 20/20 GREEN at impl PR merge)**: per CLAUDE.md BE-303 3-dim, the impl PR's pre-merge `gh pr checks` snapshot must show 0 failing required checks; the merged state must show `state=MERGED` + matching `mergeCommit` + main tip alignment. Close chore opens **only after** all 3 dims GREEN.
- **AC-10 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv review/ → done/` stages the review-state blob; close chore edits Status `review → done` AND `git add` AND verifies via `git show :<done-path>` that the staged blob reads `Status: done`. A done/ file with `Status: review` is a defect.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3` — extended in this task (row + matrix). Authoritative consumer-side binding to the GAP operators surface.
- `projects/platform-console/specs/contracts/console-integration-contract.md § 3` — parity matrix row added; attestation-marker count 16 → 17.
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.9.1 / § 2.4.9.2` — **byte-unchanged**. The "GET only — read-only" hard invariant on dashboard composition routes is preserved; this task does not touch console-bff.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/me/profile` — **byte-unchanged** (the producer contract was authored in TASK-BE-306 spec PR #704; this consumer task references it).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — **byte-unchanged**. The decision (D2 Option A Server-side fan-out only + per-domain mutations stay direct) directly supports this task's "no console-bff hop" pattern.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — the consumer contract being extended (§ 2.4.3 + § 3).
- `projects/global-account-platform/specs/contracts/http/admin-api.md` — the producer contract being consumed (already merged in BE-306 spec PR).

# Edge Cases

- **Initial value is `null` (operator never set a default)** → `MyProfileForm` initial state = empty input. Operator can enter a value and Save (transition NULL → set). Audit row written producer-side.
- **Initial value is set, operator clears it** → Clear button triggers Save with `defaultAccountId: null` body. Producer transitions value → NULL. Audit row written.
- **Operator submits the same value as the current** → producer accepts and writes an audit row (no-op at the column level, but the action is recorded as an intent — same as `me/password` setting the same password). The UI does NOT pre-check current-vs-new; the producer audit captures every intent.
- **Operator submits whitespace-only string** → client zod rejects (`UpdateProfileBodySchema` requires non-empty when string); UI surfaces inline "값은 공백만으로 구성될 수 없습니다". No producer call. If client validation is bypassed (developer tools), the producer returns `400 INVALID_REQUEST` and the proxy returns 400 via `mapError`.
- **Operator submits value over 36 characters** → client zod rejects (length ≤ 36). Same fallback as whitespace-only.
- **Operator submits value with internal whitespace (e.g. `"acc 1"`)** → client zod rejects (regex-like check). Same fallback.
- **Optimistic-lock conflict (two browser tabs racing)** → producer returns `409 OPTIMISTIC_LOCK_CONFLICT`; the proxy passes the code through; the UI surfaces "동시 변경 충돌 — 페이지를 새로고침한 뒤 다시 시도하세요" inline.
- **Operator JWT expired between page load and Save** → producer returns `401 TOKEN_INVALID`; the proxy maps to 401; the api-client refresh→login flow handles re-login (existing `mapError` path).
- **Operator has SUPER_ADMIN role and another operator's id in the URL by accident** → this endpoint is `me/profile` (self only); the producer ignores any `operatorId` query and uses the JWT `sub`. The UI does NOT expose an admin-on-behalf-of variant in v1.
- **Registry response missing `operatorContext` field for the operator** (e.g. value was never set) → `initialDefaultAccountId` defaults to `null` (empty input). The Save flow still works (NULL → set transition).
- **A future `operatorContext` carrier attribute is added (e.g. `wmsDefaultWarehouseId`)** → the v1 form ignores it (only `defaultAccountId` UI exists). Saving from the v1 UI does NOT touch the other attribute (producer-side `defaultAccountId` is the only key the v1 request body carries). When a v2 task adds a sibling form, the producer's `FAIL_ON_UNKNOWN_PROPERTIES` is still strict — both attributes must be wired together.
- **Save succeeds but registry response on subsequent navigation does NOT reflect the new value** → cache invalidation issue. The post-save handler should call `getCatalog()` re-fetch or invalidate the cached catalog; if cache is route-level only (Next.js `dynamic = 'force-dynamic'` should already bypass), navigation re-fetches naturally. The e2e verifies this round-trip.

# Failure Scenarios

- **The proxy route lands but the form is not wired into OperatorsScreen** → AC-8 e2e fails (the operator cannot reach the Save button); the proxy route is functionally dead. **Reject in review** if the form is added to a different page or not rendered at all.
- **`updateOwnProfile` sends `X-Operator-Reason` or `Idempotency-Key`** → producer accepts it (it ignores unrecognized headers) but the header-matrix-drift defect is the same as the FE-004 `roles/status` precedent — adding a header the producer omits is a contract deviation. Reject in review.
- **The form auto-saves on every input change** → privacy/audit-row pollution (every keystroke writes an audit row). Reject in review — Save must be explicit (button-triggered).
- **The Clear button sends `{ "operatorContext": { "defaultAccountId": "" } }` instead of `{ ... null }`** → producer rejects empty string as `400 INVALID_REQUEST` (BE-306 Edge Cases). Reject in review — Clear must send explicit JSON `null`.
- **The Save button sends `{ "defaultAccountId": "..." }` (missing the `operatorContext` carrier)** → producer rejects shape mismatch as `400 INVALID_REQUEST` (BE-306 Edge Cases). Reject in review — the body must be `{ operatorContext: { defaultAccountId: ... } }`.
- **The proxy or api-fn introduces a `getCatalog()` re-fetch on every Save without invalidating the cached registry response** → stale catalog returned to the operator. Reject in review — either invalidate cache OR rely on Next.js `force-dynamic` per-request fetch (existing pattern).
- **The Playwright e2e is flaky because of test-order dependence on `admin_operators.finance_default_account_id`** → seed isolation by per-test operator UUID (same pattern as BE-306 IT cycle 2 fix). Reject if the test uses a shared operator without explicit reset.
- **A reviewer suggests adding `X-Operator-Reason` "for audit completeness"** → reject. The producer's self-flow exemption is intentional (audit row is written automatically with `reason = "<self_profile_update>"`). Adding the header drift would also break the per-endpoint header matrix invariant for self-flow.
- **A reviewer suggests routing through console-bff for "consistency with overview composition"** → reject. ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant explicitly establish that mutations stay per-domain. Adding a mutation to console-bff requires a fresh ADR amendment.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows ≤ 3 modified files — this task file + `console-integration-contract.md` + (optionally) the parity test source (if included atomically).
2. Impl PR diff: code + tests under `projects/platform-console/apps/console-web/` only; AC-3 + AC-4 + AC-5 grep zero diff outside.
3. Unit (vitest): `pnpm --filter console-web test` (or equivalent project task) GREEN; new tests included.
4. Build + lint (vitest + tsc + eslint): `pnpm --filter console-web build` GREEN.
5. Playwright e2e: `pnpm --filter console-web test:e2e operators-profile.spec.ts` PASSES against the local docker-compose stack.
6. Self-CI 20/20 GREEN at impl-PR merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
7. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum: new proxy route + new client form + zod schema + tanstack-query useMutation wiring + initial-value plumbing from registry + 4 vitest tests + 1 Playwright e2e + parity matrix count update + console-integration-contract surgical edits; the proxy + api-fn parts are mechanical mirror of me/password but the form + integration + e2e are net new — deserves Opus judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-2 source-shape grep / AC-3 + AC-4 + AC-5 byte-diff grep / AC-7 parity count assertion / AC-8 e2e gate / AC-9 BE-303 3-dim at close chore / AC-10 BE-299 done re-stage).
