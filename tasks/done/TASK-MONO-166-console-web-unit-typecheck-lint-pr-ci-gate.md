# Task ID

TASK-MONO-166

# Title

Admit `console-web` vitest unit + `tsc --noEmit` typecheck + `next lint` into the PR CI gate (`.github/workflows/ci.yml` `frontend-unit-tests` job) — and fix the pre-existing console-web test debt that the missing gate let accumulate (logout / operator-overview-api stale mocks + domain-facing-credential tsc), landed atomically so main stays GREEN

# Status

done

# Owner

frontend-engineer (console-web test fixes) + CI wiring (`.github/workflows/ci.yml`) — single atomic PR

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- deploy

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

- **monorepo-level**: edits a shared path (`.github/workflows/ci.yml`) → root `tasks/`, per [`tasks/INDEX.md`](../INDEX.md) § "When to Use Root vs Project Tasks". The console-web test fixes (project-internal) are bundled in the SAME atomic PR because the CI gate cannot turn GREEN until they are fixed — staggering would land a transiently-RED main.
- **surfaced by**: [TASK-PC-FE-034](../../projects/platform-console/tasks/done/TASK-PC-FE-034-overview-consolidation-bff-home-and-gap-drilldown.md) close (2026-06-01) — its dispatch flagged that `console-web` unit/typecheck/lint have **no PR CI job** ("Frontend unit tests" is ecommerce+fan-platform only; console-web has only the nightly Playwright e2e smoke in PR CI), so pre-existing console-web unit failures sat outside the gate.
- **CI path-filter constraint (MUST honour)**: [`project_ci_path_filter_074_075_quirk`] — `dorny/paths-filter@v3` `predicate-quantifier: 'some'` negation quirk. NO negation patterns. The `platform-console` filter already exists (pure-positive, AND-combined with `code-changed`); this task only ADDS the `platform-console` trigger to the `frontend-unit-tests` job's `if:` and the console-web steps — no new filter, no negation.
- **root behaviour precedents**: the `frontend-unit-tests` job (ecommerce + fan-platform steps) is the template the console-web steps mirror; the `frontend-e2e-smoke` job already shows the console-web pnpm + `cache-dependency-path` wiring to copy.

---

# Goal

Close the CI-gate gap surfaced by TASK-PC-FE-034: `console-web`'s vitest unit suite, TypeScript typecheck, and lint do **not** run in PR CI, so regressions land silently (proven by the 5 failing unit tests + 4 tsc errors currently on `origin/main`, all introduced by prior console-web tasks whose test updates were missed).

Two halves, one atomic PR:
1. **Fix the pre-existing console-web test debt** so the suite is GREEN (the gate cannot be added over a RED suite without making main RED).
2. **Add console-web to the `frontend-unit-tests` PR CI job** (vitest + `tsc --noEmit` + `next lint`), triggered on `platform-console` changes, so future console-web regressions fail fast at PR time.

Net effect: a console-web code change that breaks a unit test, a type, or a lint rule now blocks its PR — the same protection ecommerce/fan-platform already have.

# Scope

## In Scope

**A. console-web test debt fixes (`projects/platform-console/apps/console-web/`):**

Diagnosis (verified by running `pnpm test` + `npx tsc --noEmit` on `origin/main` tip `2179c2d6`):

1. **`tests/unit/logout.test.ts`** (2 failing) — stale after TASK-PC-FE-033 (RP-initiated OIDC logout: operator-cookie clear + `/connect/logout` end_session). Update the test expectations to the current `POST /api/auth/logout` route behaviour. Confirm whether the route code is correct (it is, per FE-033 DONE) — fix the TEST, not the route.
2. **`tests/unit/features/operator-overview/operator-overview-api.test.ts`** (3 failing: 200 / 400 NO_ACTIVE_TENANT / 401) — stale after TASK-PC-FE-030: `getOperatorOverviewState()` now lazy-imports `next/headers` `cookies()` and forwards the cookie header; the test does not mock `cookies()`, so the call throws → the catch maps to `bffUnavailable: true`, failing the `noTenant`/`unauthorized`/`overview` assertions. Add the `next/headers` `cookies()` mock (mirror however the other server-route tests mock it) so the discriminated-state assertions pass.
3. **`tests/unit/domain-facing-credential.test.ts`** (4 tsc errors at lines 134/147) — `mockFetch.mock.calls[0][1]` indexing yields `RequestInit | undefined` over a `[url: string]` tuple; the `as RequestInit` conversion is rejected. Fix the test's typing (e.g. assert call shape / widen via `unknown` / type the mock's `calls`) so `tsc --noEmit` is clean. Test intent unchanged.

> All three are **test-only** fixes (stale tests / test-typing), not production-code changes — confirm per item; if any turns out to be a genuine production bug, STOP and report (it would be a separate fix-task, not silently patched).

**B. CI gate (`.github/workflows/ci.yml`):**

4. In the **`frontend-unit-tests`** job:
   - add `needs.changes.outputs.platform-console == 'true'` to the `if:` condition (so console-web changes activate the job; markdown-only stays skipped via the existing `code-changed` AND in the `platform-console` output).
   - add `projects/platform-console/apps/console-web/pnpm-lock.yaml` to the `cache-dependency-path` list.
   - add console-web steps after the fan-platform steps: `pnpm install --frozen-lockfile` + `pnpm test` (vitest) + `npx tsc --noEmit` (typecheck — catches the domain-facing-credential class) + `pnpm lint` (`next lint`), all `working-directory: projects/platform-console/apps/console-web`.
   - update the job `name:` to reflect console-web inclusion (e.g. "Frontend unit tests (ecommerce + fan-platform + console-web, vitest)").

## Out of Scope

- **console-web production code changes** — the 3 failures are stale-test / test-typing only. No `src/**` behaviour change (if a real bug is found → STOP + separate task).
- **Other projects' CI** — ecommerce/fan-platform steps unchanged; no other job touched.
- **path-filter changes** — the `platform-console` filter already covers console-web; NO new filter, NO negation pattern (MONO-074/075 rule).
- **console-web e2e** — the nightly Playwright smoke stays as-is; this task is unit/typecheck/lint only.
- **A separate console-web typecheck npm script** — use `npx tsc --noEmit` directly in CI (adding a package.json script is optional, not required).

# Acceptance Criteria

- [ ] **AC-1** `projects/platform-console/apps/console-web` → `pnpm test` (vitest) is GREEN (0 failing; the 5 prior failures fixed).
- [ ] **AC-2** `npx tsc --noEmit` in console-web is clean (the 4 domain-facing-credential errors fixed).
- [ ] **AC-3** `pnpm lint` (`next lint`) in console-web passes.
- [ ] **AC-4** `.github/workflows/ci.yml` `frontend-unit-tests` job: `if:` includes `platform-console`; console-web install + `pnpm test` + `npx tsc --noEmit` + `pnpm lint` steps present; `cache-dependency-path` includes the console-web lockfile; job `name` updated.
- [ ] **AC-5** NO negation pattern added to `dorny/paths-filter` config; NO new filter; the existing `platform-console` pure-positive + `code-changed` AND is reused unchanged.
- [ ] **AC-6** A markdown-only PR under `projects/platform-console/**` still SKIPS the `frontend-unit-tests` job (the `code-changed` AND holds); a `.ts`/`.tsx` change under console-web ACTIVATES it.
- [ ] **AC-7** Only test files + `ci.yml` are in the diff (+ optionally a `typecheck` script in console-web package.json). NO console-web `src/**` production file; NO other project source.
- [ ] **AC-8** The PR's own CI run shows the (renamed) `frontend-unit-tests` job actually executing the console-web steps and passing — real-execution proof, not just config.

# Related Specs

- [`tasks/INDEX.md`](../INDEX.md) § Root vs Project decision (this is monorepo-level — shared `.github/workflows/`).
- [`docs/guides/monorepo-workflow.md`] — Conventional Commit scope (`ci:` / `test:` / `fix(platform-console):`) + atomic cross-cutting PR shape (human reference).

# Related Contracts

- None (CI infrastructure + test fixes; no API/event contract).

# Edge Cases

- **console-web has no `pnpm-lock.yaml`** — if absent, `pnpm install --frozen-lockfile` fails. Verify the lockfile exists (the `frontend-e2e-smoke` job already references it at `projects/platform-console/apps/console-web/pnpm-lock.yaml`, so it should); if missing, mirror the fan-platform `--no-frozen-lockfile` bootstrap and note it.
- **`next lint` interactive prompt** — `next lint` can prompt on first run if ESLint isn't configured; console-web has `eslint-config-next` + a config, so it should run non-interactively. If it prompts in CI, pin the lint invocation (`next lint --max-warnings=0` or eslint directly).
- **tsc picking up test vs app tsconfig** — ensure `tsc --noEmit` uses the console-web tsconfig that INCLUDES the test files (otherwise the domain-facing-credential errors won't be guarded). Verify the typecheck actually covers `tests/**`.
- **vitest setup heavy environment** — the suite takes ~30–60s (jsdom + setup); within the job's 15-min timeout, fine.

# Failure Scenarios

- **Gate added over a still-RED suite** — if the CI steps are added before all failures are fixed, the first PR (this one) goes RED and, if merged, main regresses. Mitigation: AC-1/2/3 (suite GREEN) are prerequisites for AC-8; verify locally before push; the PR's own run is the gate.
- **A "stale test" is actually a production regression** — fixing the test to match wrong behaviour would mask a bug. Mitigation: the In-Scope note + STOP-and-report rule; each fix must confirm the production code is correct (cite the originating task — FE-033/FE-030).
- **path-filter negation slip** — adding a negation to make console-web "exclude markdown" would re-introduce the MONO-075 quirk. Mitigation: AC-5 forbids it; reuse the existing AND-with-`code-changed` pattern only.
- **CI-RED-at-merge** — merging with the new job failing creates a main regression (CLAUDE.md merge-verify). Mitigation: 3-dim merge verify; the new console-web step must be GREEN in the pre-merge `gh pr checks` snapshot.

---

분석=Opus 4.8 / 구현 권장=Opus (CI gate wiring under the path-filter quirk discipline + 3-class test-debt diagnosis where "stale test vs real bug" must be judged per item). 테스트 갱신만이면 Sonnet 가능하나, CI shared-path + stale-vs-bug 판단 + atomic GREEN 보장이 얽혀 Opus 권장.
