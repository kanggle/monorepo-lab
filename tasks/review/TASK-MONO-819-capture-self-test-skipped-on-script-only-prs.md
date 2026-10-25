# Task ID

TASK-MONO-819

# Title

`capture-portfolio.mjs --self-test` never runs on a PR that changes only that script — its host job's `if:` does not list the script's path filter

# Status

review

# Owner

devops

# Task Tags

- ci
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one `if:` line + a guard check that the wiring stays)
> Drafted 2026-10-25 UTC. Owner said «진행» to the recommendation on 2026-10-25 UTC. Facts read on origin/main `53a9227fb`.

# Dependency Markers

- 출처: 2026-10-24 session handoff («CI 배선 공백») — #4526 changed only the script and the self-test ran 0 times before merge; it ran on the `main` push (`**/*.mjs` → `code-changed`, run `40091794169` success).
- 관련: `TASK-MONO-711` (put the self-test in CI) · `TASK-MONO-648` AC-3 (`capture-route-staleness` filter).

# Goal

A PR that edits `scripts/capture-portfolio.mjs` runs its `--self-test` before merge, not only after it lands on `main`.

# Scope

## In Scope

- **AC-0 facts (measured while drafting):**
  - The self-test step lives in job `frontend-e2e-smoke` (`.github/workflows/ci.yml:3561-3562`) because that job installs chromium; the step's comment is `:3553-3560`.
  - That job's `if:` (`ci.yml:3511-3518`) is `push ∧ code-changed` **or** outputs `libs` · `workflows` · `ecommerce` · `fan` · `platform-console` · `contracts`. None of those filters lists `scripts/capture-portfolio.mjs`, so on a PR that touches only the script the job is `SKIPPED` — which GitHub counts as passing.
  - The script **is** in the `capture-route-staleness` filter (`ci.yml:963-967`), but the job keyed on that output (`Portfolio capture staleness`, `:2085-2100`) only runs `node --check` on it, not `--self-test`.
- **Fix:** add `needs.changes.outputs.capture-route-staleness == 'true'` to `frontend-e2e-smoke`'s `if:`. That output's other paths are the three apps' `src/app/**` (already covered by `ecommerce` / `fan` / `platform-console`) and `scripts/check-capture-route-staleness.mjs`, so the only new trigger is the two capture scripts.
- A check that keeps the wiring: the script that hosts the self-test must be reachable from a filter the host job's `if:` reads (a cell in an existing CI-wiring guard, or a small assertion next to it — whichever the repo already uses for path-filter wiring; AC-0 of the impl names it).

## Out of Scope

- Moving the self-test to a lighter job (it needs a browser; the host job already installs one).

# Acceptance Criteria

- [x] **AC-1** — `frontend-e2e-smoke`'s `if:` includes the output whose filter lists `scripts/capture-portfolio.mjs`.
- [ ] **AC-2** — evidence on the impl PR itself: it touches the script (a comment line is enough) and the job runs with the self-test step `success` — not `SKIPPED`.
- [x] **AC-3** — a guard fails if the script drops out of every filter the host job reads (red shown by mutation on the impl branch).

# Related Specs

- `.github/workflows/ci.yml` § `changes` filters · `frontend-e2e-smoke`
- `platform/git-workflow-policy.md` (path-filter rules: pure-positive filters)

# Related Contracts

- None.

# Edge Cases

- The job is heavy (three apps' Playwright smoke). Adding the trigger costs one full run on capture-script PRs — those are rare (a handful per month).

# Failure Scenarios

1. The `if:` gains the output, but the filter later loses the script line → silent again. AC-3.

---

# Implementation — 2026-10-25 UTC

- `.github/workflows/ci.yml`: `frontend-e2e-smoke` `if:` gains `needs.changes.outputs.capture-route-staleness == 'true'`; the `capture-route-staleness` filter gains `.github/workflows/ci.yml` (so an edit that removes the wiring still runs the judge).
- `scripts/check-capture-route-staleness.mjs`: `checkSelfTestWiring(ciText)` — three links read from the ci.yml text (filter lists `scripts/capture-portfolio.mjs` · the job's `if:` reads the filter output · the job has a `capture-portfolio.mjs --self-test` step); run in mode A. Self-test: `C1` control on the real ci.yml + `C2`–`C4` cut one link each.
- `scripts/capture-portfolio.mjs`: one comment line (so this PR itself is the AC-2 case).

# Verification

- **Red before:** with only the guard added (ci.yml unchanged) mode A rc=1 «잡 `frontend-e2e-smoke` 의 `if:` 가 `needs.changes.outputs.capture-route-staleness` 를 읽지 않습니다», self-test 23/24 (`C1` red). After the ci.yml fix: mode A rc=0, self-test 24/24 (bite 15 · 대조군 9). YAML parses.
- Every guard that reads ci.yml: `check-artifact-retention` · `check-controller-slice-naming` · `check-demo-resolver-copies` · `check-libs-ci-coverage` · `check-outside-module-input-filter-coverage` · `check-prerendered-demo-verdict` · `check-required-check-names` · `check-capture-route-staleness` — all rc=0 (staged tree).
- AC-2 is read on this PR's own run: `Frontend E2E smoke` must run (not `SKIPPED`) with the step «Capture verdict self-test» `success`.
