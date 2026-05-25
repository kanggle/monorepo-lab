# Task ID

TASK-MONO-140

# Title

TASK-MONO-139 AC-1 cycle-iteration follow-up — docker-compose `context:` path fix (cycle 1) + `workflow_dispatch` verification GREEN + further cycles as surfaced (sibling MONO-133 → PC-FE-027 honest scope adjustment pattern)

# Status

ready

# Owner

infra / qa

# Task Tags

- e2e
- phase-8
- federation-hardening
- ci
- cycle-iteration
- followup

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-139 (Phase 8 cross-product e2e cohort MVP, PR #838 squash `27238601` + close chore #839 squash `7ae78d85`) 의 **file scope + impl PR + close chore lifecycle 모두 main DONE** 이나 **functional AC-1 (workflow_dispatch GREEN 1회 이상) FAIL on first cycle**: 직접 trigger 한 `workflow_dispatch` run [`26410963737`](https://github.com/kanggle/monorepo-lab/actions/runs/26410963737) 가 Phase 1 (`Start docker compose Phase 1`) 에서 exit 1 — root cause = `unable to prepare context: path "/home/runner/work/monorepo-lab/projects/global-account-platform" not found`.

본 task = **AC-1 cycle-iteration follow-up** (sibling MONO-133 → PC-FE-027 honest scope adjustment pattern; MONO-133 § Failure Scenarios *"Option A fallback = Option B follow-up task"* 정확 답습 — MONO-139 § Failure Scenarios #4 *"CI fail / cycle 누적 > 7 cycles → STOP + 사용자 보고"* + § Edge Cases #1 *"wms / scm / erp 의 boot jar build 실패 시 sibling MONO-132 phase ordering 진단"* 의 cycle 1 surface).

## Cycle 1 root cause + fix

**Bug**: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` 의 9 `context:` 경로가 한 단계 더 깊이 위치 — `../../../../projects/...` 가 repo 상위 디렉토리 (`/home/runner/work/monorepo-lab/projects/...`) 로 resolve 됨; 실제 repo 는 `/home/runner/work/monorepo-lab/monorepo-lab/projects/...`. docker-compose `context:` 는 docker-compose 파일 위치 기준 상대 경로.

**Fix**: `context: ../../../../projects/` → `context: ../../../projects/` (9 occurrences).

**File path resolution 검증**:
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` 위치
- `..` → `tests/federation-hardening-e2e/`
- `../..` → `tests/`
- `../../..` → repo root
- `../../../projects/` → `projects/` at repo root ✓

## Why this is a separate task (not MONO-139 amend)

- MONO-139 lifecycle 가 이미 `tasks/done/` 에 main 머지됨 (`git mv` review→done + INDEX move + Status flip 완료, PR #839 squash `7ae78d85`).
- root `tasks/INDEX.md` § "review → done" rule: *"Tasks in `review/` must not be re-implemented directly. If review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task ID."*
- MONO-139 의 AC-1 functional verification gap 은 review 단계 외부에서 surface (close chore 가 self-CI 만 보고 premature merge); fix-task 가 lifecycle-correct.
- Sibling MONO-133 → PC-FE-027 패턴 정확 답습 (MONO-133 의 Option A AC-1 FAIL → PC-FE-027 follow-up).

---

# Scope

## In Scope (impl PR)

### 1. docker-compose context path fix (cycle 1 deliverable)

**`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** — 9 occurrences:

```diff
-      context: ../../../../projects/global-account-platform
+      context: ../../../projects/global-account-platform
```

`replace_all` semantics: `../../../../projects/` → `../../../projects/` (모든 occurrence; 9 services: auth-service / account-service / admin-service / wms-master-service / scm-procurement-service / finance-account-service / erp-masterdata-service / console-bff / console-web).

**검증**: `Get-Content docker-compose.federation-e2e.yml | Select-String "context:"` 가 9 매치 모두 `../../../projects/` prefix.

### 2. workflow_dispatch GREEN verification

Impl PR 머지 후 `gh workflow run federation-hardening-e2e.yml --ref main` trigger; `gh run watch --exit-status` 로 watch. Phase 1 success → 다음 layer (Flyway / health / Playwright spec / composition assertions) 가 surface 가능 (sibling MONO-132 5-layer chain 패턴).

### 3. Cycle 2-N (as surfaced)

Sibling MONO-132 chain (5-layer): PC-FE-023 (DNS gap) → PC-FE-024 (workflow `up <names>` integration seam) → MONO-132 (schema-readiness phase ordering) → PC-FE-025 (pnpm version drift cache-masked latent) → PC-FE-026 (missing public/ directory cache-masked latent). 본 task 의 cycle 2-N 도 surface 별 1-line / 1-file fix; **각 cycle 별 별 commit 단일 PR 안 머지 가능** (loop pattern 답습 아닌 burst pattern — same fix-task 안에서 iterate).

**Cycle stop condition**: workflow_dispatch GREEN OR cycle 누적 ≥ 7 (sibling MONO-046-7 11-cycle limit + MONO-046-7a 3-cycle limit 의 honest-stop 패턴).

## Out of Scope

- **MONO-139 의 file scope / impl 변경 0** — 7 specs / fixtures / docker-compose structure (services list / network / env vars) 본문 변경 0; 본 task = path resolution + cycle 별 minimal fix 만.
- **새 producer endpoint / spec / contract 변경 0** (HARDSTOP-04 + ADR-018 § 3.1 zero-retrofit invariant 보존).
- **ADR / `console-integration-contract.md` 변경 0**.
- **observability federation impl** (ADR-018 § 3.3 step 3) / **multi-tenant isolation regression IT cohort** (step 4) 변경 0 — 본 task = step 2 의 AC-1 cycle iteration 만.
- **Degrade path / additional Playwright spec 추가** = MVP 외, follow-up task 의 일.
- **agent memory 동기화** = repo task 외부.

---

# Acceptance Criteria

1. **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** 의 9 `context:` 경로가 `../../../projects/` prefix (cycle 1 fix).
2. **`gh workflow run federation-hardening-e2e.yml --ref main`** 후 workflow_dispatch run 가 **GREEN** (Phase 1 + Phase 2 + Playwright 7 specs PASS) OR **honest cycle iteration report**:
   - cycle 별 surfaced layer + fix + run ID 명시
   - cycle ≥ 7 도달 시 STOP + 사용자 보고 (sibling MONO-046-7 11-cycle honest stop 답습)
   - "all 7 spec PASS" claim 은 객관 dispatch run log 첨부 필수 (sibling MONO-132 close chore 의 functional dispatch verify 패턴)
3. **HARDSTOP-04 검증**: per-domain producer / `console-integration-contract.md` / 모든 ADR byte-unchanged (`git diff --stat docs/adr/ projects/` 0 변경).
4. **MONO-139 file scope (7 specs / fixtures / docker-compose services-list / network / env vars / workflow.yml steps order) 변경 0** — 본 task 는 path resolution + cycle 별 minimal layer fix 만.
5. **Lifecycle**: ready → review (impl PR 머지) → done (close chore PR). 루트 strict PR Separation Rule.
6. **CI 객관 검증** (BE-303 3-dim): impl PR 머지 시점 `gh pr view` `state=MERGED` + `gh pr checks` failing=0 + `git log origin/main` tip = squash commit. **CRITICAL**: impl PR push CI (compile + tests) GREEN 만으로 close 금지 — workflow_dispatch GREEN 객관 dispatch run log 가 추가 required (sibling MONO-139 close chore premature 의 학습).
7. **본 task 머지 후** = TASK-MONO-139 의 AC-1 functional verification (workflow_dispatch GREEN) 가 충족됨을 객관 기록 (본 task close chore 의 INDEX 항목에 dispatch run ID + squash commit 명시).

---

# Related Specs

> Target = root `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`. Governing: TASK-MONO-139 spec (parent task) + ADR-MONO-018 (federation hardening architecture) + sibling iteration patterns (MONO-132 / MONO-133 / MONO-046-7).

- [tasks/done/TASK-MONO-139-federation-hardening-cross-product-e2e-cohort.md](../../tasks/done/TASK-MONO-139-federation-hardening-cross-product-e2e-cohort.md) — parent task (file scope DONE; AC-1 functional verification gap 본 task 가 closure)
- [docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md](../../docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md) — governing ADR (D1-D3); cycle iteration 자체는 D-decisions 미접촉, surface별 mechanical fix
- [tasks/done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md](../../tasks/done/TASK-MONO-132-pc-e2e-seed-finance-phase-split.md) — 5-layer cycle chain 직답 (PC-FE-023 → PC-FE-024 → MONO-132 → PC-FE-025 → PC-FE-026); cycle iteration honest reporting 패턴 reference
- [tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md](../../tasks/done/TASK-MONO-133-pc-e2e-playwright-diagnostic-instrumentation.md) — AC-1 honest scope adjustment + Option B follow-up task 패턴 (PC-FE-027) 정확 sibling
- [tasks/done/TASK-MONO-046-7-...md](../../tasks/done/) (sibling 11-cycle burn) — cycle stop discipline reference
- federation-hardening-e2e workflow_dispatch run [`26410963737`](https://github.com/kanggle/monorepo-lab/actions/runs/26410963737) — cycle 1 root-cause evidence

# Related Skills

- `.claude/skills/` — N/A (mechanical 1-line fix + cycle iteration; dispatcher-direct).

---

# Related Contracts

- **None changed** (HARDSTOP-04 + ADR-018 § 3.1 zero-retrofit invariant; per-domain producer / `console-integration-contract.md` byte-unchanged).
- **Cross-referenced**: docker-compose.federation-e2e.yml `context:` paths resolve to existing `projects/global-account-platform` / `projects/wms-platform/apps/master-service` / `projects/scm-platform/apps/procurement-service` / `projects/finance-platform/apps/account-service` / `projects/erp-platform/apps/masterdata-service` / `projects/platform-console/apps/console-bff` / `projects/platform-console/apps/console-web`.

---

# Target Service

- N/A (root-level e2e cohort cycle iteration, monorepo-level).

---

# Architecture

- **Cycle iteration pattern** (sibling MONO-132 5-layer chain + MONO-046-7 11-cycle burn 답습): single fix-task 안에서 surface별 minimal commit; cycle 별 별 push + workflow_dispatch + watch + diagnose; cycle ≥ 7 또는 GREEN 까지.
- **dispatcher-direct authoring** — Sonnet agent re-dispatch 대신 dispatcher-direct 가 efficient (cycle iteration = push/watch/diagnose tight loop; agent re-invoke overhead > direct execution).
- **workflow_dispatch verify = functional AC** (push self-CI = metadata; sibling MONO-133 메타 ④ *"AC-1 dispatch verify 가 push CI 분리 됨의 가치"* 정확 답습).

---

# Implementation Notes

- **Recommended impl model = Opus 4.7** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — ADR-013 § D6 row 8 + ADR-018 D7 = federation e2e *core cohort* Sonnet 권장이나, **iteration fix-task = dispatcher-direct Opus 가 efficient** (cycle별 push/watch/diagnose tight loop + multi-layer surface 진단 precision). Sibling MONO-132 / MONO-133 모두 dispatcher-direct Opus.
- **CLAUDE.md "Branch name constraint"**: branch MUST NOT contain `master` substring. 본 task spec PR branch = `chore/mono-140-federation-e2e-cycle-fix-spec` ✓; impl PR branch 예 = `chore/mono-140-federation-e2e-cycle-fix-impl` ✓; close chore = `chore/mono-140-federation-e2e-cycle-fix-close` ✓.
- **BE-303 3-dim + BE-299 re-stage check** 각 단계 적용.
- **Cycle iteration discipline**:
  - cycle N 별 별 commit + push (force-with-lease 가능)
  - 각 cycle 후 `gh workflow run federation-hardening-e2e.yml --ref <impl branch>` (workflow_dispatch on branch)
  - watch → diagnose (`gh run view <id> --log-failed | grep -E "Error|fail|...."`)
  - fix → commit → repeat
  - cycle ≥ 7 도달 시 STOP + 사용자 보고
- **honest reporting**: 매 cycle 의 surfaced layer + run ID + 1-line root cause 명시 (sibling MONO-132 close chore 메타 ② + MONO-133 § Honest scope adjustment 답습).

---

# Edge Cases

1. **Cycle 1 fix 후 Phase 1 PASS, Phase 2 fail** — wms/scm/erp Flyway migration 누적 시간 또는 미존재 schema (예: `wms_db` CREATE 가 seed.sql 에 없음); cycle 2 surface (sibling MONO-132 finance_db CREATE 가 phase 1 에 있어야 finance Flyway pass 패턴).
2. **Cycle 2 fix 후 health gate fail** — 5 producer service 의 actuator/health 가 boot 안 끝남; cycle 3 = wait budget 늘리기 (sibling PC-FE-023 DNS gap 패턴).
3. **Cycle 3 fix 후 Playwright spec fail** — 도메인별 read endpoint 가 spec 가정과 mismatch (예: wms warehouse list URL `/console/wms/warehouses` 가 frontend route 미구현); cycle 4 = spec adjustment OR endpoint 가 contract 와 byte-unchanged 인지 검증 → contract drift 면 STOP + 사용자 보고 (HARDSTOP-04).
4. **Composition spec fail** — Operator Overview 5-card grid 가 wms/scm/erp 의 console-bff outbound URL placeholder (127.0.0.1:9 → 실 hostname) 변경 후 render 안 됨; cycle N = console-bff env var 또는 console-web route 확인.
5. **Cycle 누적 ≥ 7** — STOP + 사용자 보고. impl PR open 유지하되 close chore 미진행. sibling MONO-046-7 11-cycle saga 의 honest reporting 패턴 답습 — *"local PASS / CI FAIL split = surface fix 미달성 신호"*.
6. **HARDSTOP-04 violation** (producer spec / contract / ADR 변경 필요 surface) — STOP + 사용자 보고; fix-task 가 spec/contract 변경 영역으로 cross 시 별 task 분리.
7. **`docker compose down -v` 가 cycle 별 state leak** — 본 task 의 docker-compose down 가 모든 cycle 끝에 cleanup; cycle N+1 의 image cache 는 reuse OK (build context fix 후 image rebuild 트리거되니까).

---

# Failure Scenarios

1. **HARDSTOP-04 violation** (per-domain producer spec / `console-integration-contract.md` / 모든 ADR 변경 leak) → reject.
2. **MONO-139 file scope (7 specs / fixtures / docker-compose services-list / network / env vars / workflow.yml steps order) 변경** → reject; 본 task = path resolution + cycle 별 minimal layer fix 만.
3. **workflow_dispatch GREEN 객관 dispatch run log 없이 close chore 진행** → reject; sibling MONO-139 close chore premature 의 학습. workflow_dispatch GREEN 1회 dispatch run ID + log + 7/7 spec PASS 객관 첨부 필수.
4. **cycle 누적 7 초과 후에도 push through** → reject; STOP + 사용자 보고.
5. **새 producer endpoint 작성 leak** (예: wms warehouse list URL frontend route 새로 만들기) → STOP + 사용자 보고 (ADR-018 § 3.1 zero-retrofit + HARDSTOP-04 위반); contract drift 면 별 fix-task.

---

# Verification

- `git diff` confirms (impl PR): only `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` (9 `context:` line edit) + 추가 cycle별 minimal file edit + `tasks/INDEX.md` + task lifecycle file.
- ADR 모두 byte-identical (`git diff --stat docs/adr/`).
- `console-integration-contract.md` § 2.4.5/6/7/8 byte-identical.
- per-domain producer src/spec/contract 모두 byte-identical (`git diff --stat projects/`).
- **CI 객관 검증** (impl PR merge 후):
  - (a) `gh pr view <N> --json state,mergedAt,mergeCommit,statusCheckRollup` → `state=MERGED` + 0 failing
  - (b) `git log origin/main` tip = squash commit
  - (c) post-merge: workflow_dispatch GREEN 1회 객관 (run ID + 7 spec PASS log 첨부)
- **본 task 의 close chore INDEX 항목** 에 workflow_dispatch GREEN run ID + 7/7 spec PASS 객관 명시 (sibling MONO-133 close chore 의 AC-1 dispatch verify ID 첨부 패턴).

---

# Definition of Done

- [ ] `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` 9 `context:` paths fixed (cycle 1).
- [ ] workflow_dispatch GREEN 1회 객관 (run ID 첨부 + 7/7 spec PASS log).
- [ ] cycle 누적 < 7 (OR honest stop report at ≥ 7).
- [ ] HARDSTOP-04 검증: per-domain producer / `console-integration-contract.md` / 모든 ADR byte-unchanged.
- [ ] MONO-139 file scope (7 specs / fixtures / docker-compose services-list / network / env vars / workflow.yml steps order) 변경 0.
- [ ] Lifecycle ready → review → done.
- [ ] BE-303 3-dim merge verification + BE-299 re-stage check 각 단계 적용.
- [ ] 본 task 머지 후 = TASK-MONO-139 AC-1 functional verification 충족 객관 기록 (INDEX 항목 dispatch run ID + squash commit).
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus 4.7** (분석=Opus 4.7 / 구현 권장=Opus 4.7 — iteration fix-task = dispatcher-direct Opus, sibling MONO-132/133 답습).
- **분량**: small-to-medium — cycle 1 = 1-file 9-line edit; cycle 2-N (예상 2-4 추가) 도 각 1-file minimal edit. 총 누적 < 7 cycle 예상.
- **dependency**: 선행 = TASK-MONO-139 close chore #839 main `7ae78d85` (file scope main 도달); workflow_dispatch run [`26410963737`](https://github.com/kanggle/monorepo-lab/actions/runs/26410963737) Phase 1 fail (root-cause evidence). 후속 = ADR-018 § 3.3 step 3 (observability federation impl) + step 4 (isolation regression IT cohort) 의 dependency-correct base (본 task 후 AC-1 GREEN main 위에서 진행).
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR / impl PR / close chore distinct.
- **user-explicit intent provenance**: 사용자 AskUserQuestion 2026-05-26 option A 선택 *"TASK-MONO-140 fix-task 자동 안 (추천)"* (description: *"Spec PR 일 1-line fix 최소 (docker-compose context path 9 occurrences) + workflow_dispatch verify GREEN 첨부 + close chore. 추가 surface 입 cycle 2-N 이 나오면 동일 task 안 쓰고 수증하게 cycle iterate"*) = 명시 confirm form.
- **sibling MONO-133 → PC-FE-027 패턴 정확 답습**: MONO-133 Option A AC-1 FAIL + secondary value 보존 + Option B follow-up task. 본 task = MONO-139 Option A AC-1 FAIL + file scope 보존 (DONE) + cycle iteration follow-up.
