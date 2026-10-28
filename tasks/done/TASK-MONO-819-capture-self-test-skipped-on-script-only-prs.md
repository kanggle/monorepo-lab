# Task ID

TASK-MONO-819

# Title

`capture-portfolio.mjs --self-test` never runs on a PR that changes only that script — its host job's `if:` does not list the script's path filter

# Status

done (2026-10-28 UTC — 4-dim verified · impl PR #4550 squash `dfd7ad75f` · AC-2 증거 PR #4602)

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
- [x] **AC-2** — evidence on the impl PR itself: it touches the script (a comment line is enough) and the job runs with the self-test step `success` — not `SKIPPED`.
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
- AC-2 — ⚪ **not isolated by this PR.** On #4550 `Frontend E2E smoke` ran and the step «Capture verdict self-test (denial + degraded + probe — no window)» was `success` — but this PR also edits `ci.yml`, so the `workflows` output alone would have started the job. The run therefore does not show the *new* trigger at work. What does: the guard's `C2` cell (removing the `if:` line turns mode A red) and the next PR that touches only `scripts/capture-portfolio.mjs` — read that PR's `Frontend E2E smoke` (must not be `SKIPPED`) when it comes.

---

## Close (2026-10-28 UTC)

- **AC-2 ✅ — 분리된 증거.** PR #4602(squash `28bdfd8a0`)는 `scripts/capture-portfolio.mjs` 한 파일(주석)만 바꿨다. 그 PR 의 `changes` 잡(run `40644217967`): `libs` · `workflows` · `ecommerce` · `fan` · `platform-console` · `contracts` 전부 **false**, `capture-route-staleness` **true** — 이 티켓이 `frontend-e2e-smoke` 의 `if:` 에 넣은 출력 하나만 켜졌다. 그 상태에서 `Frontend E2E smoke` 잡(job `122669243463`)이 돌았고 «Capture verdict self-test (denial + degraded + probe — no window)» 단계 `success`. (#4550 에서는 `ci.yml` 도 바뀌어 `workflows` 출력과 분리되지 않았다 — 위 Implementation 기록.)
- AC 문구는 «impl PR itself» 였다 — 구현 PR 이 `ci.yml` 을 함께 고쳐 그 PR 로는 분리할 수 없었으므로, 스크립트만 바꾼 후속 PR 로 같은 술어를 쟀다.
- 4차원: (a) #4550 MERGED · (b) `dfd7ad75f` 는 origin/main 조상 · (c) #4550 실패 0 (#4602 도 실패·취소 0) · (d) AC-1 · AC-2 · AC-3 전부 닫힘.
