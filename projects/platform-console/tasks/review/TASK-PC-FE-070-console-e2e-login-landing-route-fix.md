# Task ID

TASK-PC-FE-070

# Title

Fix the console-web Playwright `globalSetup` login that has silently aborted the **entire** Platform Console E2E nightly suite since 2026-06-02 — the OIDC login helper `waitForURL`s the EXACT URL `${consoleOrigin}/dashboards`, but TASK-PC-FE-034 (#1017) re-pointed the post-login landing `/` → `/dashboards/overview`, so the exact match never resolves → 30s timeout → globalSetup throws → 0 specs run. console-web e2e helper only; one-line predicate change (`/dashboards` prefix-match), no app/BFF/contract change.

# Status

review

# Owner

frontend-engineer (console-web — e2e login fixture only; no app/BFF/producer/contract change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

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

- **fixes a regression introduced by**: [TASK-PC-FE-034](../done/TASK-PC-FE-034-consolidate-overview-screens-home-5-domain.md) (#1017, squash `175b36ae8`, merged 2026-06-02 04:37 KST) — that commit changed `src/app/page.tsx` from `redirect('/dashboards')` to `redirect('/dashboards/overview')` (5-domain overview promoted to console home; IAM 3-leg overview demoted to a drill-down still at `/dashboards`). The app change is **correct and stays**; only the e2e login helper's landing assertion was left stale.
- **non-PR-gated latency**: the `Nightly E2E` workflow's `Platform Console E2E full-stack` job is **not a PR-required check**, so the route/assertion mismatch was not caught at #1017 merge time and main has been silently RED on this job for ~11 days (last GREEN nightly console-e2e = 2026-05-30; first consistent RED ≥ 2026-06-02). Matches the `project_adr023_plane_separation_fed_e2e` lesson: nightly e2e = post-merge regression surface, confirm after merges.
- **no dependency on**: any app/route/BFF/contract change. `page.tsx`, the `(console)` routing, and the BFF composition routes are byte-unchanged.

---

# Goal

The console-web Playwright suite's `globalSetup` (`tests/e2e/fixtures/global-setup.ts` → `loginAsSuperAdmin` → `driveOidcPkceLogin`) drives the real OIDC PKCE login (console-web → SAS `/oauth2/authorize` → auth-service `/login` → callback → operator-token exchange → `/`), then waits for the browser to settle on the post-login landing. It asserts the FINAL URL with:

```ts
page.waitForURL(`${DEFAULTS.consoleOrigin}/dashboards`, { timeout: 30_000 })
```

A bare string argument to Playwright `waitForURL` is an **exact** glob match. TASK-PC-FE-034 (#1017) re-pointed `/` → `/dashboards/overview`, so after login the browser settles on `/dashboards/overview`, which the exact `/dashboards` pattern never matches. `waitForURL` hangs the full 30s, throws `TimeoutError`, and because this is in `globalSetup` the **entire** suite aborts before a single spec runs — every Platform Console E2E nightly run since 2026-06-02 reports `failure` at `fixtures/login.ts:181`.

Root cause: a stale exact-string landing assertion in the e2e login fixture, not an app defect.

Fix: replace the exact string with a `/dashboards` **prefix predicate** —

```ts
page.waitForURL((url) => url.pathname.startsWith('/dashboards'), { timeout: 30_000 })
```

— which resolves whether the canonical home is `/dashboards`, `/dashboards/overview`, or any future `/dashboards/*` sub-route, never matches the intermediate `/`, and is robust to a further landing re-point. The stale comment block (lines 168-179) is refreshed to record the PC-FE-034 re-point and the prefix rationale.

# Scope

## In Scope

console-web e2e helper only — a single file:

1. **`apps/console-web/tests/e2e/fixtures/login.ts`** — line 181 `waitForURL` exact `${consoleOrigin}/dashboards` → `(url) => url.pathname.startsWith('/dashboards')` predicate; refresh the Step-4 comment block to document the PC-FE-034 landing re-point + prefix rationale + this task.
2. **Task md + `INDEX.md`** entry.

## Out of Scope

- **Any app / route / `page.tsx` change.** `/` → `/dashboards/overview` (PC-FE-034) is correct and stays; this task only realigns the test assertion to it.
- **The other e2e specs** (`overview-consolidation.spec.ts`, `operators-profile.spec.ts`) — already authored against the new routing (explicit `/dashboards/overview` + `**/dashboards/*` globs) by PC-FE-034; they never ran only because `globalSetup` aborted first, and pass once login resolves. No change needed.
- **Any producer / console-bff / composition-contract change.** Byte-unchanged.
- **Tightening the predicate to exact `/dashboards/overview`.** Deliberately a prefix match so a future landing re-point within `/dashboards/*` (or the IAM drill-down at bare `/dashboards`) does not re-break globalSetup; the helper only needs to confirm the authenticated `(console)` shell was reached, which the `/dashboards` prefix guarantees.
- **Making the nightly console-e2e job a PR-required check.** Separate scope (CI gating policy); noted in Failure Scenarios as the systemic gap.

# Acceptance Criteria

- [ ] **AC-1** `tests/e2e/fixtures/login.ts:181` no longer uses the exact string `${DEFAULTS.consoleOrigin}/dashboards`; it uses a URL predicate matching the `/dashboards` pathname prefix with the same 30s timeout.
- [ ] **AC-2** The predicate resolves for `/dashboards/overview` (the current PC-FE-034 landing) AND for bare `/dashboards` (the IAM drill-down), and does NOT resolve for the intermediate `/`.
- [ ] **AC-3** The `Platform Console E2E full-stack (Playwright + docker compose)` nightly job completes `globalSetup` (login succeeds) and runs its specs — the job conclusion returns to `success` (verify on the first nightly run after merge; non-PR-gated).
- [ ] **AC-4** The Step-4 comment block in `login.ts` documents the PC-FE-034 `/dashboards/overview` re-point and the prefix-match rationale (no stale "`/dashboards` is the canonical landing" claim).
- [ ] **AC-5** No app/route/`page.tsx`/BFF/contract/ADR change in the diff — scope is `login.ts` + task lifecycle only.

# Related Specs

- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) — Authentication (OIDC PKCE login) + the E2E testing requirement; the fixture realignment keeps the suite's login bootstrap honest to the current landing route.
- [TASK-PC-FE-034](../done/TASK-PC-FE-034-consolidate-overview-screens-home-5-domain.md) — the landing re-point this fixture must track (`/` → `/dashboards/overview`).

# Related Contracts

- None. `console-integration-contract.md` is byte-unchanged — the fix is a test-fixture URL assertion, no request/response/auth surface touched.

# Edge Cases

- **Intermediate `/` URL**: `redirect()` dispatches `/` → landing before any observable browser state; the prefix predicate (matching `/dashboards*`, never `/`) is correct exactly as the original `/dashboards` intent was — TASK-BE-311 iter 7's reason for not asserting bare `/` still holds.
- **IAM drill-down at bare `/dashboards`**: an operator deep-linked or redirected to `/dashboards` (no `/overview`) still satisfies the prefix — acceptable, the helper only needs to confirm the authenticated `(console)` shell loaded.
- **Future landing re-point** (e.g. `/dashboards/home`): prefix-tolerant → globalSetup keeps working without another fixture edit.
- **Trailing query/hash** (`/dashboards/overview?x=1`): `url.pathname` ignores query/hash → still matches.

# Failure Scenarios

- **Over-broad predicate masks a real login failure** — if login silently landed somewhere unexpected under `/dashboards`, the prefix would still pass. Mitigation: the only authenticated landing routes ARE under `/dashboards/*`; an actual auth failure lands on `/login` or an error page (NOT `/dashboards*`), so the predicate still times out and fails loudly. The downstream specs additionally assert concrete `/dashboards/overview` content.
- **Green-wash via local-only check** — console-web `:check` (vitest/tsc/lint) does NOT exercise the Playwright login (no docker stack), so a local green does not prove the nightly is fixed. Mitigation: AC-3 requires confirming the actual nightly `Platform Console E2E` job conclusion flips to `success` post-merge (`gh run list --workflow=nightly-e2e.yml`).
- **Systemic gap — non-PR-gated nightly let an 11-day regression persist** — out of scope to fix here, but recorded: route changes that the nightly-only console-e2e covers are not caught at PR time. Mitigation (noted, not actioned): treat post-merge nightly confirmation as part of any console routing/auth change's done-definition (consistent with the `federation-hardening-e2e` post-merge-check lesson).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (one-line e2e fixture predicate + comment refresh; mechanical, low-risk — the diagnosis/route archaeology is the hard part and is already done). 로컬 `:check`(tsc/lint) green 선검증 + 머지 후 첫 nightly console-e2e job conclusion 확인이 진짜 검증.
