# Task ID

TASK-MONO-044

# Title

main 브랜치의 baseline CI 회귀 청소 (4 jobs FAIL 누적, 2026-05-04~05)

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test
- ci

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

[TASK-MONO-023](../done/TASK-MONO-023-main-baseline-integration-cleanup.md) 시리즈 (a~e, 2026-05-03 종결) 가 그 시점의 baseline 회귀를 청소했지만, **2026-05-04 이후 다시 4 CI job 이 main 에서 일관되게 FAIL** 하고 있음.

본 task 가 시도하는 것 (023 시리즈 답습):
- 4 회귀 job 의 root cause 진단 + 분류 ((a) 코드/테스트 회귀 / (b) 환경 한계 / (c) flaky)
- 분류별 sub-task 발행 (TASK-MONO-044a/b/c/...) — 단일 PR 으로 4개 모두 fix 시도하면 PR scope 폭발
- main baseline 안정화 — 신규 PR 가 변경 무관 잡 실패에 시달리지 않음

이 task 완료 후:
- main 의 모든 CI job (Build & Test 8 + Integration 3 + E2E 2 + Frontend E2E 2 = 15+) 이 안정적으로 PASS
- 신규 PR 의 false-positive grinding 비용 제거
- 회귀 재발 감지 메커니즘 (예: nightly main run + 알림) 검토 결과 문서화

본 task 는 **본인이 진단을 끝내는 게 아니라 진단 + 분류 + sub-task 발행** — 023 의 시리즈 패턴 답습.

---

# Scope

## In Scope

### 1. 회귀 진단 (1차 분석 결과 기록)

본 spec 작성 시점 (2026-05-05) 에 가장 최근 main commit `248c08fb` (#193 머지, "chore: file TASK-SCM-BE-001 spec") 의 CI run `25327478714` 결과 분석:

**FAIL 4 job**:
1. **Integration (global-account-platform, Testcontainers)** — Gateway 통합 테스트 다수 (gateway-service의 `IntegrationTest`)
   - 로그: `MissingWebServerFactoryBeanException at ServletWebServerApplicationContext.java:216`
   - Spring Boot 가 servlet web environment 를 boot 하려는데 `WebServerFactory` bean 누락
   - 가설: GAP gateway-service 가 reactive (Spring Cloud Gateway) 인데 어떤 테스트가 servlet web context 를 강제, dependency 또는 `@SpringBootTest(webEnvironment=...)` 설정 충돌
   - 분류: **(a) 진짜 회귀 (코드/테스트 fix 필요)**
2. **E2E (gateway-master live-pair, Testcontainers)** — wms 의 e2e 잡
   - 메모리 (`project_ecommerce_import_plan.md` Remaining Follow-up #8): "GitHub-hosted runner 가 5-container 시나리오에 부족함이 확정. 후보: (a) self-hosted runner, (b) 시나리오 분할, (c) Testcontainers parallelism, (d) 컨테이너 헬스체크 timeout 단축"
   - 분류: **(b) 환경 한계 가능 — 진단 필요**
3. **E2E (fan-platform v1 live-trio, Testcontainers)** — fan-platform 의 e2e 잡
   - 메모리 (`project_fan_platform_v1_complete.md` v1 의 의도적 mock #2): "production AlwaysAllow 빈을 실 image e2e 에서 swap 못 함. follow-up: community-service 에 e2e-membership-deny 테스트 profile 추가 필요"
   - 분류: **(b) 환경 한계 가능 또는 (a) 회귀 — 진단 필요**
4. **Frontend E2E full-stack (web-store, Playwright + docker compose)** — ecommerce 의 frontend e2e 잡
   - 메모리 (`project_ecommerce_import_plan.md`): "5-container Testcontainers + 250 serial HTTP rate-limit 시나리오 자체가 30분 한도 초과"
   - 분류: **(b) 환경 한계 가능 — 진단 필요**

### 2. 분류별 sub-task 발행

진단 결과에 따라:
- **(a) 진짜 회귀**: 별도 fix sub-task 발행 (예: `TASK-MONO-044a-gap-gateway-context-loader-fix`)
- **(b) 환경 한계**: 시나리오 분할 / 헬스체크 튜닝 / self-hosted runner 결정 sub-task (예: `TASK-MONO-044b-e2e-runner-capacity`)
- **(c) flaky**: retry/quarantine 정책 sub-task

### 3. main baseline 안정화 검증

sub-task 들이 머지된 후:
- main 의 **3 연속 CI run** 이 모든 job PASS (또는 의도적 skip + 명시적 사유)
- 신규 PR 의 status check 가 변경 무관 잡 실패 0

### 4. 회귀 재발 방지

- nightly main CI run + 실패 시 알림 (GitHub Actions schedule trigger 도입 검토)
- 또는: 모든 PR 의 status check failure 가 main 의 동일 commit 의 같은 job 결과와 일치하면 "pre-existing" 라벨 자동 부여 (gh-action 검토)
- 결정 결과를 본 task 의 done 항목에 기록

## Out of Scope

- **진단된 회귀의 실제 코드 fix** — sub-task 영역. 본 task 는 1차 진단 + 분류 + sub-task 발행만.
- **CI workflow 재설계** (job 분할, runner type 변경 등) — 진단 결과로 필요해지면 sub-task 로 분리
- **E2E 시나리오 자체 변경** (예: 250 → 100 serial HTTP) — sub-task 영역
- **다른 환경 (self-hosted runner, GitHub Enterprise) 도입** — sub-task 또는 ADR 영역

---

# Acceptance Criteria

## 진단

1. 4 FAIL job 각각에 대해 root cause 분석 완료. 분류 (a/b/c) 결정.
2. 각 job 의 실패 로그 인용 + 핵심 stack trace 포함된 진단 보고서가 본 task 의 done 항목에 기록 (또는 별도 `knowledge/incidents/2026-05-05-ci-regression.md`).
3. (a) 회귀 분류된 job 은 첫 깨진 commit / PR 식별 (`git bisect` 또는 GitHub Actions history 시간 비교).

## sub-task 발행

4. 분류별 sub-task spec 이 `tasks/ready/` 에 등재 (분량은 진단 결과에 따라 1~3개).
5. 각 sub-task 는 본 task ID (`TASK-MONO-044`) 를 Goal 에 인용 (Move Rules § Review Rules).

## 안정화 검증

6. 모든 sub-task 머지 후 main 의 3 연속 CI run 이 모든 job PASS (또는 명시적 skip).
7. 신규 doc-only PR 1건 (sentinel) 이 status check 모두 PASS.

## 재발 방지

8. nightly main run / pre-existing 라벨 자동화 / 다른 메커니즘 중 1개 결정 + 적용 또는 별도 sub-task 로 분리.
9. 본 task 의 done 항목에 결정 결과 1줄 요약.

---

# Related Specs

- [TASK-MONO-023](../done/TASK-MONO-023-main-baseline-integration-cleanup.md) (done) — 본 task 의 reference 패턴 (2026-05-02~03 baseline 회귀 청소). a/b/c/d/e sub-task 분할 패턴 답습.
- [TASK-MONO-023b](../done/TASK-MONO-023b-oauth2-oidc-regression-family.md) (done) — OAuth2/OIDC 회귀 fix. 본 task 의 GAP gateway 회귀와 도메인 가까움.
- [`project_ecommerce_import_plan.md`](../../C:/Users/kangdow/.claude/projects/c--Users-kangdow-dev-project-ai-project-monorepo-lab/memory/project_ecommerce_import_plan.md) Remaining Follow-up #8 — e2e 30분 timeout 환경 fix candidate
- [`project_fan_platform_v1_complete.md`](../../C:/Users/kangdow/.claude/projects/c--Users-kangdow-dev-project-ai-project-monorepo-lab/memory/project_fan_platform_v1_complete.md) — fan-platform e2e 의 알려진 mock swap 한계
- [`project_gap_idp_promotion.md`](../../C:/Users/kangdow/.claude/projects/c--Users-kangdow-dev-project-ai-project-monorepo-lab/memory/project_gap_idp_promotion.md) — admin-override 머지 정당화 패턴 (회귀 청소 시리즈 의 history)
- `.github/workflows/ci.yml` — 본 task 가 분석할 CI workflow

---

# Edge Cases

1. **진단 중 새 회귀 발견**: 4 job 외 다른 job 의 sporadic failure 발견 시 본 task 의 sub-task 추가 또는 별도 task 발행. flaky 와 진짜 회귀 구분 위해 3+ run 관찰 필요.
2. **회귀 fix 자체가 다른 회귀 유발**: GAP gateway context fix 가 다른 service 의 어딘가를 깨뜨릴 가능성 — sub-task 의 PR 에서 모든 영향 service 의 `:check` 추가 검증 강제.
3. **환경 한계 sub-task 가 self-hosted runner 도입 결론**: 인프라 변화이므로 ADR 분리 (`docs/adr/ADR-MONO-XXX-ci-runner-strategy.md`). 본 task 범위 밖.
4. **`MissingWebServerFactoryBeanException` 의 root cause 가 의존성 충돌**: Spring Boot dependency-management BOM 의 servlet/reactive 동시 로딩. dependency tree 분석 필요 (`./gradlew :projects:global-account-platform:apps:gateway-service:dependencies`).
5. **sub-task 가 admin-override 로 머지된 후에도 main 회귀 지속**: 본 task 는 진단 → sub-task 발행 → sub-task 머지 → 검증 의 4 단계. 검증 실패 시 본 task 가 다시 ready 로 가지 않고, 검증 실패 자체가 새 sub-task (TASK-MONO-044f 등) 로 발행.

---

# Failure Scenarios

## A. 진단 결과 4 job 모두 환경 한계로 판정

코드 fix 불가능, 환경 변경 (self-hosted runner / 시나리오 분할) 만 가능. mitigation: ADR 작성 후 별도 long-running 인프라 task 로 이전. 본 task 는 단기 mitigation (admin-override 정책 명문화) 으로 close 가능.

## B. 진단 중 GAP gateway context loader 회귀가 dependency 변경 없이 재현 불가

로컬 (Docker / WSL2 또는 CI 환경) 에서 `:projects:global-account-platform:apps:gateway-service:integrationTest` 직접 실행 시 PASS. CI runner 와의 환경 차이가 root cause. 환경 의존 문제로 분류 후 sub-task.

## C. e2e job 들이 30분 timeout 만 발생 (실제 실패 stack trace 없음)

GitHub Actions 가 timeout 으로 cancel — log 에 actionable 진단 정보 적음. mitigation: workflow 의 step 별 timeout 단축 + diagnostic step 추가 (예: `docker logs gateway-service` 후속 step) sub-task.

## D. 본 task 자체의 진단이 길어져 다른 task 머지 정체

본 task 는 진단 + sub-task 발행만 — 진단 자체에 1~2일 이상 소요되면 admin-override 정책 (메모리 `project_gap_idp_promotion.md` 의 #107 패턴) 을 sub-task 들 머지 사이의 임시 mitigation 으로 적용. 본 task 와 별개 진행.

---

# Notes

- **Recommended impl model**: **Opus** — 4 job 의 root cause 진단은 stack trace + dependency tree + spring boot context 분석 + git bisect 의 동시 작업. 분석=Opus 4.7 / 구현 권장=Opus.
- **분량 추정**: 진단 자체 (보고서 작성) + sub-task spec 1~3개 발행 + INDEX 갱신 = 단일 PR. sub-task 들의 코드 fix 는 본 task 의 후속.
- **dependency 표현**:
  - `선행`: 없음 (본 task 가 즉시 시작 가능 — 이미 main 에 회귀 발생 중)
  - `후속`: TASK-MONO-044a/b/c... (진단 결과에 따라 발행)
  - 영향: 진행 중인 모든 PR (#194, #195, #196, #197 — 본 task spec PR 본인 포함) — 진단 후 admin-override 가능 여부 결정.
- **CI failure 와 본 PR 영향**:
  - 본 spec PR 자체도 같은 4 job 이 FAIL 할 것 (변경이 spec 1개 + INDEX 1줄 추가라 코드 영향 0)
  - 그러나 본 task spec 머지 자체가 진단 시작 신호 — admin-override 또는 일반 머지 결정.
- **Phase 4 evaluation 영향**: ADR-MONO-002 의 D3 churn 안정 평가 입력 — main baseline 회귀 발생 빈도 / 청소 비용은 monorepo 의 라이브러리 churn 비용 의 한 측면. 본 task 의 진단 결과 + sub-task 머지 후 회귀 재발 빈도를 추적.
- **메모리 갱신**: 본 task 머지 후 (또는 sub-task 시리즈 종결 후) 메모리에 "TASK-MONO-044 회귀 청소 시리즈" 추가 권장.
