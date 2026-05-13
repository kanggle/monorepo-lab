# Task ID

TASK-MONO-088

# Title

`gap-platform-e2e-smoke` PR-time job 신설 — ADR-MONO-011 § 6.2 (inherited ADR-MONO-010 § 6.2) outstanding closure / PR-time first-call validation 의무화

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- e2e
- gap
- governance
- adr-followup

---

# Goal

ADR-MONO-011 § 6.2 outstanding (inherited from ADR-MONO-010 § 6.2): **PR-time gap smoke job 신설**. gap `tests/e2e` 모듈의 `:e2eSmokeTest` 를 PR-time CI 에서 호출하여 ComposeFixture self-managed mode 의 PR-time first-call validation 의무화.

근거: ADR-MONO-011 acceptance 후 nightly gap-e2e-full 의 **7-cycle archaeological inspection** (TASK-MONO-080/081/082 + BE-278/279) 가 노출한 메타 학습 — gap tests/e2e 모듈이 settings.gradle 미등록 → PR-time CI 의 어떤 job 도 ComposeFixture 호출 안 함 → 7 layer 의 historical 누락이 "dev/local-only 작동" 가정으로 누적되어 nightly 첫 호출 시 노출. memory `project_e2e_3phase_strategy_complete.md` § Outstanding 학습: "ComposeFixture / Testcontainers-with-docker-compose / 외부 image build dependent fixture 도입 시 PR-time 의 smoke job 으로 first-call validation 의무화."

본 task = ADR-MONO-011 § 6.2 의 직접 closure. ci.yml 에 `gap-platform-e2e-smoke` job 신설 + nightly `gap-e2e-full` job (`.github/workflows/nightly-e2e.yml` L525-599) 의 구조 답습 + target = `:e2eSmokeTest` (`@Tag("smoke")` 만) + path-filter gate (`fan-platform-e2e` PR-time pattern 답습).

provenance:
- ADR-MONO-011 § 6.2 "PR-time gap smoke job (ADR-MONO-010 § 6.2 outstanding inherited) — separate file from this ADR; same docker-compose plumbing question."
- ADR-MONO-010 § D5 step 5 "Smoke job uncovered: gap currently has no PR-time smoke job... A dedicated `gap-platform-e2e-smoke` PR-time job is recorded as outstanding (ADR-MONO-010 § 6.2). Phase 3 does NOT add it..."
- 메모리 `project_e2e_3phase_strategy_complete.md` § "Phase 3 의 7 cycle archaeological inspection — 메타 학습 source" (root cause prevention).

---

# Scope

## In Scope

### A. `.github/workflows/ci.yml` 에 `gap-platform-e2e-smoke` job 신설

위치: `gap-integration-tests` job 인접 (job 정의 순서 자연성 — integration → e2e-smoke).

구조 (nightly `gap-e2e-full` L525-599 + ci.yml `fan-platform-e2e` L1014-1119 hybrid 답습):

- `needs: [changes, build-and-test]` (gap 은 fan-platform-boot-jars 같은 upstream boot-jars job 없음 — in-job bootJar build 패턴 = nightly gap-e2e-full 답습).
- `timeout-minutes: 20` (nightly gap-e2e-full 60min budget 의 1/3 — smoke @Tag 만이라 wall-clock 단축 + 5min cushion).
- `if:` path-filter gate (fan-platform-e2e L1028-1036 답습):
  ```yaml
  if: >-
    github.repository == 'kanggle/monorepo-lab' &&
    (
      github.event_name == 'push' ||
      needs.changes.outputs.libs == 'true' ||
      needs.changes.outputs.workflows == 'true' ||
      needs.changes.outputs.gap == 'true' ||
      needs.changes.outputs.contracts == 'true'
    )
  ```
- Steps (nightly gap-e2e-full L539-599 답습, smoke target 만 차이):
  1. Checkout
  2. Set up JDK 21 (Temurin)
  3. Set up Gradle
  4. Build gap boot jars (5 service: auth / account / security / admin / gateway — nightly L552-564 verbatim)
  5. Verify Docker (ComposeFixture prerequisite)
  6. Run gap docker-compose e2e smoke suite — target = `:projects:global-account-platform:tests:e2e:e2eSmokeTest`
  7. Dump gap service logs on failure (nightly L575-591 답습 — `if: failure()` 조건이라 PR-time 정상 시 무비용 + fail 시 diagnostic 가치)
  8. Upload gap e2e smoke test reports on failure (artifact name = `gap-platform-e2e-smoke-test-reports`, path = `tests/e2e/build/reports/tests/e2eSmokeTest/` + `test-results/e2eSmokeTest/`, retention-days 7)

### B. ci.yml header comment 갱신

ci.yml top header 의 job 목록에 `gap-platform-e2e-smoke` 추가 (fan/scm/wms 와 동위 표기).

### C. ADR audit-trail (option C-1 패턴)

ADR 본문 직접 수정 안 함 (acceptance 후 변경 = 신뢰성 손상, memory `project_e2e_3phase_strategy_complete.md` § "ADR 정정 패턴 — option C-1"). 대신:

- 본 task body 의 § Implementation Notes 에 ADR-MONO-011 § 6.2 / ADR-MONO-010 § 6.2 closure 명시.
- INDEX outcome line 에 "ADR-MONO-011 § 6.2 closure (ADR-MONO-010 § 6.2 inherited) — option C-1 audit-trail" 명시.

### D. 검증

- ci.yml lint = `actionlint` 또는 push-trigger self-CI.
- 첫 push to main 후 CI 의 `gap-platform-e2e-smoke` job trigger (push event 라 path-filter 무관 unconditional 실행).
- nightly gap-e2e-full 의 7-cycle archaeology 후 GREEN 상태 (commit `ec2bb73c` 이전) — 6 layer fix 모두 main 에 적재됨. smoke target 도 동일 base 위에서 작동 예상.
- smoke wall-clock 예상 ~ 5-10min (full 첫 GREEN = 3m 53s, smoke 는 @Tag("smoke") 만이라 더 짧음 — gap tests/e2e 의 smoke = 2 class 추정).

## Out of Scope

- nightly gap-e2e-full job 의 `Dump gap service logs on failure` step **cleanup** (nightly L575 의 "본 step 은 제거 또는 verbose 줄임 (cleanup PR)" 명시) — TASK-MONO-082 의 Phase 0 diagnostic 잔재. smoke job 에도 동일 diagnostic step 포함하므로 cleanup 은 별 task 후보.
- ADR-MONO-011 § 6.1 (auto-issue / Slack on nightly failure) — 별 outstanding, 별 task.
- ADR-MONO-011 § 6.3 (reusable workflow consolidation) — Phase 4.
- ADR-MONO-011 § 6.4 (matrix strategy) — Phase 4.
- ADR-MONO-011 § 6.5 (cost-budget telemetry) — observation 후 별 task.
- gap-integration-tests job 의 구조 변경 (현재 7 service `:integrationTest` 호출, smoke job 과 분리 유지 — integration vs e2e 다른 lifecycle).

---

# Acceptance Criteria

### Impl PR

- [ ] `.github/workflows/ci.yml` 에 `gap-platform-e2e-smoke` job 신설 (nightly gap-e2e-full L525-599 + ci.yml fan-platform-e2e L1014-1119 hybrid 답습).
- [ ] job target = `:projects:global-account-platform:tests:e2e:e2eSmokeTest`.
- [ ] path-filter gate (push / libs / workflows / gap / contracts).
- [ ] timeout-minutes 20.
- [ ] failure-time diagnostic step 포함 (Dump gap service logs).
- [ ] failure-time test reports upload 포함.
- [ ] ci.yml header comment 의 job 목록에 `gap-platform-e2e-smoke` 추가 (fan/scm/wms 와 동위 표기).
- [ ] task lifecycle ready → review (in-progress 우회, substantial 이지만 single-PR closure 패턴 — MONO-084/085/086/087 precedent).
- [ ] [`tasks/INDEX.md`](../INDEX.md) 동기.

### CI verification (push to main self-CI 자연 trigger)

- [ ] `gap-platform-e2e-smoke` job 첫 trigger (push to main event 라 path-filter 무관).
- [ ] job 상태 = SUCCESS 또는 (실패 시) diagnostic step output 으로 root cause 진단.
- [ ] smoke job wall-clock 실측 = 자료화 (cost analysis 측면 ADR-MONO-011 § 4.4 보완).
- [ ] 기존 other gap-related jobs (`gap-integration-tests`, `gap-e2e-full` nightly) 회귀 0.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome (CI 결과 + wall-clock 실측 포함).

---

# Related Specs

- [`docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md`](../../docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md) § 6.2 (closure target)
- [`docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md`](../../docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md) § 6.2 (inherited outstanding) + § D5 step 5 (Smoke job uncovered statement)
- [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) § `gap-e2e-full` (L525-599 — answer-key structure)
- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) § `fan-platform-e2e` (L1014-1119 — PR-time path-filter pattern) + `scm-platform-e2e` (L1121+, parallel reference) + `gap-integration-tests` (L746-799, sibling gap CI surface)
- [`projects/global-account-platform/tests/e2e/build.gradle`](../../projects/global-account-platform/tests/e2e/build.gradle) § `e2eSmokeTest` task

---

# Related Contracts

본 task = CI workflow extension. HTTP API / event payload contract 변경 0. `:e2eSmokeTest` Gradle task 가 호출하는 e2e suite 가 검증하는 contract (auth / account / security / admin / gateway HTTP + Kafka domain events) 는 이미 기존 contract 모두 안에서 작동.

---

# Target Service

`.github/workflows/ci.yml` (monorepo-level CI surface). gap project 의 PR-time e2e validation 신설.

---

# Architecture

### CI surface 변경 도식

```
Before:
  PR / push to main
    ├─ build-and-test (all 5 projects compile + unit)
    ├─ ecommerce e2e (wms gateway, fan, scm) PR-time smoke
    ├─ gap-integration-tests (7 service :integrationTest)
    │     ↑ ComposeFixture 미호출 — tests/e2e module 의 first-call 없음
    ╰─ frontend-e2e-smoke

  Nightly (cron + push to main)
    ├─ frontend-e2e-fullstack
    ├─ wms-platform-e2e-full
    ├─ fan-platform-e2e-full
    ├─ scm-platform-e2e-full
    ╰─ gap-e2e-full
          ↑ 첫 ComposeFixture call — historical 누락 7 layer 부채 노출 source

After (본 task):
  PR / push to main
    ├─ build-and-test
    ├─ ecommerce e2e PR-time smoke
    ├─ gap-integration-tests
    ├─ **gap-platform-e2e-smoke (NEW)** — ComposeFixture first-call validation
    ╰─ frontend-e2e-smoke

  Nightly (변경 없음)
    └─ 5 backend full + frontend-e2e-fullstack
```

### ADR-MONO-011 § D5 의 gap structural 특수성 (재확인)

- **No boot-jars upstream job dependency**: gap 은 `fan-platform-boot-jars` 같은 별 PR-time boot-jars job 없음 — in-job bootJar build 패턴 (nightly L552-564 답습).
- **No Docker CLI image build step**: ComposeFixture 가 docker-compose 내부에서 build (`docker compose up -d --build` in @BeforeAll). fan/scm/wms 의 Docker CLI build step 불필요.
- **`-Pobservability=on` 미적용**: ADR-MONO-007 § D3 의 CI 명시 exclusion 답습 (nightly 도 OFF).
- **Smoke 의 wall-clock 예상**: nightly full 첫 GREEN = 3m 53s, smoke @Tag 의 2 class 추정 = 2-5min 정도. PR-time 20min timeout 충분 cushion.

### 7-cycle archaeological inspection 답습 보장 (6 layer fix 의 main 적재 확인)

| cycle | fix | task | main 적재 |
|---:|---|---|---|
| 1 | settings.gradle 등록 | MONO-080 | ✓ |
| 2 | boot jars build step (5 service) | MONO-081 | ✓ (nightly L552-564) |
| 3 | auth-service JWT keys env + volume mount | MONO-082 Phase 1 | ✓ (docker-compose.e2e.yml) |
| 4-5 | Phase 0/1 진단 + 검증 | MONO-082 | ✓ |
| 6 | MySQL TEMPORARY TABLES privilege (init.sh) | BE-278 | ✓ |
| 7 | e2e seed tenant_id | BE-279 | ✓ |

본 task 시작 시점 = `ec2bb73c` 이후 의 base 위에서 6 layer 모두 적재. smoke job 추가 = 깨끗한 단순 추가, 회귀 risk 낮음.

---

# Implementation Notes

## Job 추가 위치 결정

`.github/workflows/ci.yml` 의 기존 job 순서:
- ... build-and-test ...
- ... wms gateway e2e-tests / fan-platform-e2e / scm-platform-e2e ...
- gap-integration-tests (L746)
- 그 외 ...

`gap-platform-e2e-smoke` 위치 = `gap-integration-tests` 직후가 자연 (gap 그룹). L799 이후 다음 job 시작 직전.

## 구조 답습 source

answer-key:
- **Steps** = nightly `gap-e2e-full` (L539-599) verbatim, target 만 `:e2eFullTest` → `:e2eSmokeTest`.
- **Trigger gate (`if:`)** = `fan-platform-e2e` PR-time pattern (L1028-1036), service flag 만 `fan` → `gap`.
- **Timeout** = 20min (nightly 60min budget 의 1/3, smoke @Tag 의 wall-clock 가벼움).
- **Artifact upload** = nightly 의 `gap-e2e-full-test-reports-nightly` 답습, artifact name 만 `gap-platform-e2e-smoke-test-reports`.

## Diagnostic step 유지 결정

nightly L575-591 의 `Dump gap service logs on failure` step 은 TASK-MONO-082 Phase 0 diagnostic 잔재 ("본 step 은 제거 또는 verbose 줄임 (cleanup PR)" 명시). 본 task 는 cleanup 안 함 — 이유:

1. PR-time job 의 failure 진단 가치가 nightly 와 동일하거나 더 높음 (dev 가 매번 보는 surface).
2. `if: failure()` 조건이라 정상 시 무비용.
3. cleanup 은 별 task 후보 (nightly + smoke 두 job 모두 cleanup 동시 적용 자연).

## Header comment 갱신

ci.yml top 의 job 목록 표 또는 comment block 에 `gap-platform-e2e-smoke` 추가 (실측 line 위치는 impl 시 확인).

## ADR audit-trail 패턴 (option C-1)

ADR-MONO-011 § 6.2 가 ADR-MONO-010 § 6.2 outstanding inherited 명시. 본 task closure 시:

- ADR 본문 § 6.2 자체는 수정 안 함 (acceptance 후 변경 = 신뢰성 손상, memory `project_e2e_3phase_strategy_complete.md` 학습).
- task body § Implementation Notes (본 section) 에 closure 명시.
- INDEX outcome line 에 "ADR-MONO-011 § 6.2 closure + ADR-MONO-010 § 6.2 inherited closure — option C-1 audit-trail 누적 6차" 명시.
- TASK-MONO-080/081/082 + BE-278/279 의 5 layer 누적 검증 패턴 답습.

## D4 churn impact

- 1 file `.github/workflows/ci.yml` touch.
- ~70 line addition (1 job def block).
- ADR-MONO-003a § D1.3 IN-scope (cross-cutting test policy 인접 — ADR-MONO-010 / 011 의 동일 OVERRIDE 분류 답습). D4 OVERRIDE 적용.
- 직전 ADR-MONO-011 impl (TASK-MONO-079) 와 동일 OVERRIDE precedent.

---

# Edge Cases

- gap tests/e2e 모듈의 `@Tag("smoke")` class 수 미확정 — ADR-MONO-010 § 1.2 表 가 "gap smoke 2" 라고 명시했지만, MONO-078 Phase 2 impl 시점 (PR #432) 의 distribution. 실측은 push 후 self-CI 의 test report 에서 확인.
- nightly gap-e2e-full 와 PR-time gap-platform-e2e-smoke 가 같은 docker-compose project name (`gap-e2e`) 사용 — 동시 실행 시 collision 가능성 0 (각 GitHub runner 가 별도 VM, 격리됨).
- gap-integration-tests (7 service `:integrationTest`) 와 gap-platform-e2e-smoke 는 다른 task family (`:integrationTest` vs `:e2eSmokeTest`) + 다른 lifecycle (Testcontainers per-class vs ComposeFixture shared) — 회귀 risk 0.
- markdown-only PR 의 path-filter behavior — `gap` flag 가 markdown 변경에 trigger 안 됨 (MONO-074/075 `code-changed` filter 자연 작동). PR-time smoke job SKIP 정상.

---

# Failure Scenarios

- **smoke target 의 wall-clock 가 20min 초과** → timeout 30-60min 으로 확장 follow-up commit. 실측 후 fine-tune.
- **smoke @Tag class 가 0개** (Phase 2 의 smoke/full 분류에서 smoke 가 empty) → `:e2eSmokeTest` task 가 "no tests found" 종료. green 으로 통과해도 first-call validation 의도 달성 안 됨. 이 경우 별 follow-up task 로 smoke 분류 보강.
- **ComposeFixture 의 새로운 7 cycle 노출** — historical 누락이 더 있는 경우. failure 시 diagnostic step output 으로 root cause 진단 후 layer 별 fix task 분리 (MONO-080/081/082 + BE-278/279 패턴 답습).
- **CI runner 자원 부족 fail** → retry-on-flake 후 안정 시 별 task 후보 (ADR-MONO-011 § 6.7).
- **gap-integration-tests 또는 다른 gap job 의 회귀** → smoke job 추가가 cross-impact 0 이어야 함. fail 시 즉시 revert.

---

# Test Requirements

- ci.yml lint = push 후 GitHub Actions 의 workflow validation 자연 검증 (syntax error 면 즉시 fail).
- `gap-platform-e2e-smoke` job 첫 trigger = push to main event → path-filter 무관 unconditional 실행 → 자연 검증.
- 기존 다른 jobs 회귀 0 (특히 gap-integration-tests, gap-e2e-full nightly).
- production code = 0 (CI workflow only).
- D4 OVERRIDE: ADR-MONO-003a § D1.3 (cross-cutting test policy 인접) 적용.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] `gap-platform-e2e-smoke` job 첫 trigger 시 SUCCESS (또는 follow-up fix).
- [ ] wall-clock 실측 자료화.
- [ ] 다른 jobs 회귀 0.

### Close chore PR

- [ ] review → done, [`tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- ADR-MONO-011 § 6.2 outstanding closure (ADR-MONO-010 § 6.2 inherited).
- 메모리 `project_e2e_3phase_strategy_complete.md` § "Phase 3 의 7 cycle archaeological inspection — 메타 학습 source" 의 root cause prevention.
- TASK-MONO-080 / 081 / 082 + BE-278 / 279 의 5 layer 누적 검증 패턴 (option C-1 audit-only).
- ci.yml `fan-platform-e2e` (L1014-1119) + nightly `gap-e2e-full` (L525-599) hybrid 답습.
- D4 OVERRIDE: ADR-MONO-003a § D1.3 (cross-cutting test policy 인접) 적용.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (substantial CI work — 7-cycle archaeology 의 root cause prevention 정책 wiring).
