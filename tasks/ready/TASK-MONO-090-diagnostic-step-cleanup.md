# Task ID

TASK-MONO-090

# Title

`Dump gap service logs on failure` diagnostic step 제거 (TASK-MONO-082 잔재 cleanup) — nightly-e2e.yml + ci.yml 양쪽 동시

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- cleanup
- e2e
- gap

---

# Goal

TASK-MONO-082 Phase 0 diagnostic step 의 cleanup PR 명시 의도 종결.

**provenance**:

- `.github/workflows/nightly-e2e.yml` L581 comment: "**진단 확정 후 본 step 은 제거 또는 verbose 줄임 (cleanup PR).**"
- TASK-MONO-082 Phase 1 fix 머지 (commit `bdc00b40`, 2026-05-13) 후 cycle 3-7 진단 완료, 본 step 의 진단 목적 종결.
- TASK-MONO-088 PR-time first-call validation 첫 trigger FAIL (run `25829462751`, 2026-05-14) 시 본 step 실행되었으나 **logs empty** — ComposeFixture JVM shutdown hook 이 test fail 후 자동 `docker compose down` cleanup → diagnostic step 실행 시점 container 모두 removed → useless 확정.
- 메모리 `project_e2e_3phase_strategy_complete.md` § "Cycle 8 의 design flaw 노출 — diagnostic step useless" 학습.

본 task = **diagnostic step 자체 제거 (Option A1 simple)**. nightly `gap-e2e-full` (nightly-e2e.yml L575-591) + PR-time `gap-platform-e2e-smoke` (ci.yml L877-891) 두 file 동시 적용. trade-off: cycle 9+ 같은 미래 fail surface 시 진단 도구 부재 — 대신 test report artifact (`Upload gap e2e ... test reports on failure`) 의존.

대안 (out of scope): **Option A2 redesign** = ComposeFixture cleanup 차단 + diagnostic step 가 logs capture 가능. substantial — 별 task 후보.

---

# Scope

## In Scope

### A. `.github/workflows/nightly-e2e.yml` L575-591 제거

`gap-e2e-full` job 의 `Dump gap service logs on failure` step block 제거. 직전 step (`Run gap docker-compose e2e full suite`) 와 직후 step (`Upload gap e2e full test reports on failure`) 사이에 inserted 된 step.

### B. `.github/workflows/ci.yml` L877-891 제거

`gap-platform-e2e-smoke` job 의 `Dump gap service logs on failure` step block 제거. 직전 step (`Run gap docker-compose e2e smoke suite`) 와 직후 step (`Upload gap e2e smoke test reports on failure`) 사이의 step.

### C. 검증

- 두 file step 제거 후 yaml syntax valid (GitHub Actions workflow validation 자연 검증).
- 첫 push to main 후 CI 자체 trigger 자연 검증 (markdown / cleanup 아닌 `.github/workflows/` 변경 = `workflows` flag 가 모든 downstream job activate, 회귀 가드).
- nightly cron 또는 push to main 시 gap-e2e-full / gap-platform-e2e-smoke job 의 step 정수 = 기존 - 1.

## Out of Scope

- **Option A2 redesign** (ComposeFixture cleanup 차단 + proper logs capture) — substantial, 별 task 후보. 본 task 후 별 ADR / TASK file.
- gap-e2e-full / gap-platform-e2e-smoke job 의 다른 step 변경.
- 다른 service (wms / fan / scm) 의 e2e job diagnostic step 검토 (각자 다른 mechanism 사용 — Testcontainers 의 standard log mechanism, ComposeFixture 가 아님).

---

# Acceptance Criteria

### Impl PR

- [ ] `.github/workflows/nightly-e2e.yml` 의 L575-591 step block 제거.
- [ ] `.github/workflows/ci.yml` 의 L877-891 step block 제거.
- [ ] 두 file 의 직전/직후 step 무변경 (`Run ... e2e ... suite` + `Upload ... test reports on failure` 그대로).
- [ ] yaml syntax valid (push 후 workflow validation 자연 검증).
- [ ] task lifecycle ready → review (in-progress 우회, mechanical cleanup single-PR closure 패턴 — TASK-MONO-084/085/086/087/088/089/BE-282 precedent).
- [ ] [`tasks/INDEX.md`](../INDEX.md) 동기.

### CI verification

- [ ] 첫 push to main 후 새 CI run 의 `gap-platform-e2e-smoke` job SUCCESS (GoldenPath 1 class smoke, step 제거 영향 0).
- [ ] 다른 jobs 회귀 0 (workflows flag 가 full pipeline trigger).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) § `gap-e2e-full` (TASK-MONO-082 잔재 source)
- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) § `gap-platform-e2e-smoke` (TASK-MONO-088 답습 추가 source)
- [`tasks/done/TASK-MONO-082-gap-e2e-nightly-health-timeout.md`](../done/TASK-MONO-082-gap-e2e-nightly-health-timeout.md) (Phase 0 diagnostic 추가 + cleanup 명시)
- [`tasks/done/TASK-MONO-088-gap-pr-time-smoke-job.md`](../done/TASK-MONO-088-gap-pr-time-smoke-job.md) (cycle 8 cleanup conflict 노출 source)
- 메모리 `project_e2e_3phase_strategy_complete.md` § "Cycle 8 의 design flaw 노출 — diagnostic step useless"

---

# Related Contracts

본 task = CI workflow cleanup. HTTP API / event payload contract 변경 0.

---

# Target Service

`.github/workflows/` shared (monorepo-level CI surface). 2 file × ~16 line 제거.

---

# Architecture

본 task = 도구 fitness 의 retrospective enforcement. TASK-MONO-082 Phase 0 의 diagnostic step 가 cycle 3 진단 시점에 가치 있었지만, 이후 ComposeFixture JVM shutdown hook 의 자동 cleanup 으로 cycle 8 진단 시 logs empty 노출 → "fail 시 cleanup 전 logs capture" 가정 위배. cleanup 의 정상 design 패턴 = test framework 자체 (Gradle test report) 또는 ComposeFixture 내부 logs export.

A1 (제거) 의 trade-off:
- **Pro**: useless step 제거, workflow 가독성 향상, MONO-082 cleanup 명시 의도 종결.
- **Con**: 미래 fail 시 diagnostic 도구 부재. cycle 9+ 발생 시 logs 직접 capture 어려움 — test report artifact 의존 (Gradle 의 `tests/e2e/build/reports/tests/e2eSmokeTest/` HTML report) + ComposeFixture 가 가능하면 stdout 에 service logs forwarding (현재 standard JUnit testLogging stdout 으로 partial 가능).

trade-off acceptable — A2 (redesign) 가 substantial 이고, 진단 도구 부재 risk 가 cleanup 가치보다 가벼움 (test report 가 primary). A2 candidate 가 future cycle 9+ surface 시 prioritize 결정.

---

# Implementation Notes

## 제거 range

### nightly-e2e.yml L573 (직전 step blank line) → L591 (step block 끝)

```yaml
          --no-daemon --stacktrace                            # L573 직전 step 끝
                                                              # L574 blank
      - name: Dump gap service logs on failure (TASK-MONO-082 # L575 ← 제거 시작
        # cycle 3 진단 ... cleanup PR).                       # L576-581 comment
        if: failure()                                         # L582
        working-directory: projects/global-account-platform   # L583
        run: |                                                # L584
          echo "=== docker compose ps ..."                    # L585
          docker compose -f docker-compose.e2e.yml -p ...     # L586
          for svc in auth-service ...; do                     # L587
            echo ""                                           # L588
            echo "=== docker compose logs ..."                # L589
            docker compose -f ... logs --tail=200 ...         # L590
          done                                                # L591 ← 제거 끝
                                                              # L592 blank
      - name: Upload gap e2e full test reports on failure     # L593 직후 step
```

### ci.yml L875 (직전 step blank line) → L891 (step block 끝)

동일 패턴, comment 짧음 (TASK-MONO-088 답습 추가 시 abbreviated).

## D4 churn impact

- 2 file `.github/workflows/` touch.
- ~32 line 제거 (nightly 17 + ci 15).
- ADR-MONO-003a § D1.3 IN-scope (cross-cutting test policy 인접 — ADR-MONO-010/011 동일 분류). D4 OVERRIDE 적용.

---

# Edge Cases

- 두 file 의 인접 step (`Run` + `Upload`) 와의 spacing 보존 (blank line 의 적정 수). yaml indentation 위배 안 됨.
- 미래 cycle 9+ 발생 시 진단 도구 부재 — test report artifact 만 의존. ComposeFixture logs 캡처 부재.
- 다른 e2e job (wms / fan / scm) 의 diagnostic 도구 부재로 통일 (gap 만 추가 도구 보유했던 historical anomaly 종결).

---

# Failure Scenarios

- 두 file step 제거 시 yaml syntax error → push 후 workflow validation fail. spot-check + push 검증 필수.
- 미래 cycle 9+ 발생 시 진단 어려움 → A2 redesign task urgency 상승. 본 task 후 별 ADR / task 분리.

---

# Test Requirements

- yaml syntax valid (GitHub Actions workflow validation 자연).
- 첫 push to main 후 CI 자체 trigger SUCCESS (workflows flag 가 full pipeline 회귀 가드).
- gap-platform-e2e-smoke job step 정수 = 8 → 7 (Dump step 제거).
- gap-e2e-full nightly job step 정수 = 7 → 6.
- production code = 0 (CI workflow cleanup only).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] 첫 push to main 후 CI 자체 trigger SUCCESS.
- [ ] gap-platform-e2e-smoke job step 정수 변경 확인.

### Close chore PR

- [ ] review → done, [`tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- TASK-MONO-082 (Phase 0 diagnostic step 추가) 의 nightly-e2e.yml L581 cleanup 명시.
- TASK-MONO-088 (gap-platform-e2e-smoke 답습 추가) + cycle 8 PR-time first-call validation (run `25829462751`) 시 step useless 확정.
- 메모리 `project_e2e_3phase_strategy_complete.md` § "후속 candidate" 첫 항목.
- D4 OVERRIDE: ADR-MONO-003a § D1.3 (cross-cutting test policy 인접) 적용.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (2-file × ~16 line removal, mechanical cleanup).
