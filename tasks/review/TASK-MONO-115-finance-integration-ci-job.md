# Task ID

TASK-MONO-115

# Title

finance-platform Integration CI job 추가 (account-service Testcontainers — scm TASK-MONO-048 동형)

# Status

review

# Owner

devops / backend

# Task Tags

- ci
- code

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

finance-platform 의 `@Tag("integration")` Testcontainers IT 가 main CI 에서 실행되지 않는 결손을 메운다. 현재 [.github/workflows/ci.yml#L343-L352](../../.github/workflows/ci.yml#L343-L352) `Build and check finance-platform backend (Docker-free)` step 은 `:projects:finance-platform:apps:account-service:check` (= unit/slice 117, Docker-free) 만 실행하고, account-service 의 4 Testcontainers IT 클래스는 **어떤 CI job 에서도 실행되지 않는다**. scm-platform 은 [TASK-MONO-048](../done/TASK-MONO-048-scm-integration-ci-job.md) 으로 동형 `scm-integration-tests` job 을 이미 확보했으나 finance 는 그 analog 이 부재 — 본 task 가 그 결손을 메운다.

직접 트리거: [TASK-FIN-BE-001](../../projects/finance-platform/tasks/done/TASK-FIN-BE-001-account-service-bootstrap.md) impl PR #598 (squash `ce2d16ce`) 의 4 IT (`AccountLifecycleIntegrationTest` / `AuditAndImmutabilityIntegrationTest` / `CrossTenantHttpIntegrationTest` / `IdempotencyConcurrencyIntegrationTest` + `AbstractAccountIntegrationTest` base) 가 **local + dispatcher 구조 검증만** 통과했고 실 CI 행위 증명은 미수행 상태 (green-wash 금지 차원에서 TASK-FIN-BE-001 close one-liner + memory 에 정직하게 surfaced backlog 로 적재됨). 본 task 머지 후 finance 코드 변경 PR (또는 본 PR 의 `workflows` 트리거) 에서 4 IT 가 진짜 CI MySQL+Redis+Kafka 위에서 PASS 확인.

후속 영향:
- TASK-FIN-BE-001 의 행위 증명 (idempotency exactly-once / cross-tenant 403 fail-closed / audit append-only / SETTLED immutable) 이 CI-authoritative 가 된다 — finance v1 종결의 마지막 monorepo-side 잔여 backlog 해소.
- 향후 finance 두 번째 서비스 (ledger-service v2 등) IT 추가 시 본 job step 에 `:integrationTest` 한 줄 추가만으로 자연 확장 (별 task 불필요).

---

# Scope

## In Scope

### `.github/workflows/ci.yml` 변경

`scm-integration-tests` job ([ci.yml#L904-L953](../../.github/workflows/ci.yml#L904-L953)) 직답습으로 `finance-integration-tests` job 추가:

```yaml
finance-integration-tests:
  name: Integration (finance-platform, Testcontainers)
  runs-on: ubuntu-latest
  needs: [changes, build-and-test]
  timeout-minutes: 30
  # finance integration suite boots MySQL 8 + Redis + Kafka (libs/java-messaging
  # outbox relay) via Testcontainers; JWKS stubbed per-class (OkHttp
  # MockWebServer). Scoped to monorepo-lab; standalone repo extraction relies on
  # its own Docker-free `check` signal. TASK-MONO-115.
  if: >-
    github.repository == 'kanggle/monorepo-lab' &&
    (
      github.event_name == 'push' ||
      needs.changes.outputs.libs == 'true' ||
      needs.changes.outputs.workflows == 'true' ||
      needs.changes.outputs.finance == 'true'
    )

  steps:
    - Checkout / setup-java 21 / setup-gradle / docker info   # scm-integration-tests 동일
    - name: Run finance integration suites
      run: >-
        ./gradlew
        :projects:finance-platform:apps:account-service:integrationTest
        --no-daemon --stacktrace
    - upload reports on failure  # path: projects/finance-platform/apps/*/build/{reports/tests,test-results}/integrationTest/
```

### `Build and check finance-platform backend (Docker-free)` step 주석 정리

[ci.yml#L343-L348](../../.github/workflows/ci.yml#L343-L348) 의 주석은 현재 *"v1 bootstrap (TASK-MONO-114) = account-service skeleton only (no business logic / no tests yet); TASK-FIN-BE-001 lands the domain + test pyramid + Testcontainers IT."* — TASK-FIN-BE-001 이 머지된 지금 **stale**. scm step 주석 ([ci.yml#L329-L332](../../.github/workflows/ci.yml#L329-L332)) 형태에 맞춰 *"Docker-backed integrationTest is invoked separately on dev environments / finance-integration-tests job below."* 로 갱신.

## Out of Scope

- `gateway-service` integrationTest — finance v1 은 gateway 미존재 (architecture.md § Identity = gateway v1-deferred → Traefik direct). 추후 도입 시 본 job step 에 한 줄 추가 (별 task 불필요).
- nightly cross-service E2E job — finance 는 v1 cross-service 흐름 없음 (이벤트 구독 = v2). 별 task scope.
- finance `boot-jars` job — account-service 가 boot jar 배포 단계 진입 시 별 task.
- finance ci.yml 의 `:check` 단계 변경 — 그대로 유지 (Docker-free fast feedback 보존; IT 는 별 job 으로 분리하는 것이 본 task 의 핵심).

---

# Acceptance Criteria

1. `.github/workflows/ci.yml` 에 `finance-integration-tests` job 추가, `scm-integration-tests` job 의 구조 답습 (env / setup-java 21 / setup-gradle / `docker info` / upload-artifact-on-failure 패턴 동일).
2. job `if:` 가 `github.repository == 'kanggle/monorepo-lab'` 가드 + (`push` to main 또는 `libs`/`workflows`/`finance` path-filter flag) 시 활성화. negation pattern 미사용 ([TASK-MONO-074/075](../done/TASK-MONO-075-ci-path-filter-negation-fix.md) pure-positive 규율 준수 — 기존 `finance` flag = [ci.yml#L136](../../.github/workflows/ci.yml#L136) `outputs.finance` 재사용, 신규 filter 불필요).
3. job 이 `:projects:finance-platform:apps:account-service:integrationTest` 실행 (`integrationTest` Gradle task 는 [account-service/build.gradle#L104](../../projects/finance-platform/apps/account-service/build.gradle#L104) 에 이미 등록됨 — scm MONO-048 Edge Case 1 [task 부재 → Gradle FAIL] 은 finance 에 **해당 없음**).
4. 본 PR self-CI (ci.yml 변경 → `workflows` flag 트리거) 에서 `finance-integration-tests` job 활성화 + 4 IT 클래스 PASS (`tests=N skipped=0 failures=0`).
5. `Build and check finance-platform backend (Docker-free)` step 주석에서 *"no tests yet"* / *"TASK-FIN-BE-001 lands"* stale 문구 제거, scm step 주석 형태 (*"... finance-integration-tests job below"*) 로 갱신.
6. 회귀 0: 다른 6 dedicated Integration / E2E / Frontend / boot-jars job (wms master+notification+admin / GAP / scm / fan e2e / frontend-checks) 영향 없음. ci.yml syntax valid.

---

# Related Specs

- [TASK-FIN-BE-001](../../projects/finance-platform/tasks/done/TASK-FIN-BE-001-account-service-bootstrap.md) — 직접 트리거 (본 task 머지 → finance IT 가 CI-authoritative; close one-liner 에 본 task 가 surfaced follow-up 으로 명기됨)
- [TASK-MONO-048](../done/TASK-MONO-048-scm-integration-ci-job.md) — scm 동형 선례 (exact 답습 reference)
- [TASK-MONO-018](../done/TASK-MONO-018-gap-ci-and-outbox-scheduler.md) — `gap-integration-tests` job 도입 (원형 패턴)
- [TASK-MONO-045](../done/TASK-MONO-045-ci-path-filter-and-nightly.md) — `dorny/paths-filter@v3` + project flag 도입
- [TASK-MONO-074](../done/TASK-MONO-074-ci-path-filter-cross-project.md) / [TASK-MONO-075](../done/TASK-MONO-075-ci-path-filter-negation-fix.md) — pure-positive path-filter 규율 (negation 금지)

---

# Related Contracts

- 없음 (CI workflow 변경, 외부 contract 무관).

---

# Target Service / Component

- `.github/workflows/ci.yml`
- (no production code change)

---

# Implementation Notes

- 답습: `scm-integration-tests` job ([ci.yml#L904-L953](../../.github/workflows/ci.yml#L904-L953)) 직카피 + (a) `name` finance 치환 (b) 주석 컨테이너 목록 `MySQL 8 + Redis + Kafka` (c) `if:` 의 `outputs.scm` → `outputs.finance` (d) run step service path → `:projects:finance-platform:apps:account-service:integrationTest` (e) upload artifact `name` + `path` glob finance 치환.
- 신규 path-filter **불필요**: `finance` flag 는 이미 [ci.yml#L136](../../.github/workflows/ci.yml#L136) + [ci.yml#L179-L182](../../.github/workflows/ci.yml#L179-L182) 에 존재 (`projects/finance-platform/**` minus `tasks/**` minus `**/*.md`). 재사용만.
- job 삽입 위치: `scm-integration-tests` 직후 (ci.yml#L953 `frontend-checks` 직전) — dedicated Integration job 들이 연속 배치되는 현 구조 보존.
- `docker info` step 유지 (Testcontainers 사전검증 표준 — `DockerAvailableCondition` 와 별개의 runner-level 가드).
- `--no-daemon --stacktrace` 유지 (다른 모든 job 일치).
- upload artifact: `name: finance-integration-test-reports`, `path: projects/finance-platform/apps/*/build/{reports/tests/integrationTest,test-results/integrationTest}/`, retention 7.
- timeout-minutes: 30 (GAP/scm 동일; account-service IT 4 클래스 = 충분, MySQL 3m + Kafka 3m startup 포함).
- 본 PR 자체 = `.github/workflows/` 변경 → `workflows` flag 트리거 → `Build & Test` + 모든 dedicated job 활성화 (전체 회귀 검증 + 부수적으로 첫 finance Integration job 실 검증).
- Lifecycle: ready → in-progress (impl PR) → ci.yml 변경 commit → review. spec PR (본 파일 + 루트 INDEX ready) 와 impl PR 분리 (루트 tasks/INDEX.md PR Separation Rule — spec ↔ impl 동일 PR 금지). close chore (review→done) 는 impl PR 머지 후.

---

# Edge Cases

1. **finance integrationTest task 부재 risk**: scm MONO-048 Edge Case 1 (IT 0 인 service 가 step 에 포함되면 `integrationTest` task 미존재 → Gradle FAIL) 은 finance 에 **해당 없음** — account-service `build.gradle` L104 가 `tasks.register('integrationTest', Test)` 로 명시 등록 (verified). 단일 service step 이라 다중-service Gradle FAIL risk 자체 부재.
2. **path-filter race**: 본 PR 이 ci.yml 만 변경 + finance code 변경 0 → `workflows` flag 트리거 → 모든 dedicated job 활성화 (MONO-045 정책, 의도된 동작 — 첫 finance IT job 실검증 기회).
3. **Docker daemon 부재 (extracted standalone repo)**: `if: github.repository == 'kanggle/monorepo-lab'` 가드로 standalone repo 에서 skip. GAP/scm 패턴 동일. (추가로 `DockerAvailableCondition` 가 JVM-level fallback.)
4. **markdown-only / tasks-only 변경**: `finance` flag 의 `!**/*.md` + `!tasks/**` exclusion + `code-changed` AND 합성 (MONO-074/075) 으로 본 job 이 doc/task PR 에서 skip — close chore PR 자체는 finance IT job 불활성 (의도된 동작, 회귀 아님).

---

# Failure Scenarios

## A. finance IT 가 CI Linux runner 에서 local 과 다른 결과 (실패)

→ 이것이 본 task 의 **존재 이유**: TASK-FIN-BE-001 의 IT 는 dispatcher 구조검증 + local 만 통과했고 CI 행위 증명 미수행. CI 에서 fail 하면 **진짜 행위 gap 이 surfaced 된 것** (green-wash 방지의 성과). 조치: 본 job 자체는 그대로 머지 (job 신설은 옳음), 실패는 별도 fix-task `TASK-FIN-BE-002` (또는 유사) 를 `projects/finance-platform/tasks/ready/` 에 신설, Goal 에 "Fix issue found in TASK-FIN-BE-001 (surfaced by TASK-MONO-115 CI job)" 명기 (project INDEX Review Rule). 흔한 원인: local Rancher dockerd vs CI ubuntu-latest dockerd timing / port allocation / Testcontainers startup timeout.

## B. ci.yml 문법 오류로 전체 workflow parse fail

→ `actionlint` 또는 `gh workflow view` 로 사전 검증. impl PR description 에 검증 결과 명시. (단순 답습 카피라 risk 낮음.)

## C. job 이 path-filter 로 skip 되어 self-CI 에서 미검증

→ 본 PR = `.github/workflows/` 변경 = `workflows` flag = 모든 dedicated job 강제 활성 (Edge Case 2). skip 되면 path-filter 합성 회귀 신호 → MONO-074/075 진단 재적용.

---

# Test Requirements

- self-PR (impl PR) 에서 `finance-integration-tests` job 활성화 + 4 IT 클래스 PASS (job 시간 + `tests=N skipped=0 failures=0` PR description 명시).
- 다른 dedicated job (wms master/notification/admin · GAP · scm · fan e2e · frontend) 회귀 0.
- ci.yml syntax valid (actionlint / gh workflow view).

---

# Definition of Done

- [ ] `finance-integration-tests` job 추가 + self-CI 활성화 + 4 IT PASS
- [ ] `Build and check finance-platform backend` step stale 주석 갱신
- [ ] impl PR description 에 본 job 실행 결과 (job 시간 + tests/skipped/failures) 명시
- [ ] 회귀 0 확인
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — `scm-integration-tests` 직답습 카피 + 단일 service step + 주석 1블록 갱신, 설계 결정 무.
- **분량**: small — ci.yml 단일 파일 ~50줄 추가 + 주석 1블록 갱신.
- **dependency**:
  - `선행`: TASK-FIN-BE-001 (머지 완료, impl PR #598 `ce2d16ce` = origin/main; 4 IT 클래스 main 에 존재 확인됨). spec PR 은 본 파일만; impl PR 은 ci.yml.
  - `후속`: 없음. 본 task 머지 = finance v1 의 monorepo-side surfaced backlog 2건 중 1건 (CI IT job) 해소. 잔여 1건 = 외부 `kanggle/finance-platform` Template-fork (user action, classifier-blocked, ADR-MONO-008 §6 PENDING — 본 task 와 독립).
- **CI gating 영향**: 본 PR 의 ci.yml 변경 → `workflows` flag 트리거 → 모든 dedicated job 활성화 (의도된 전체 회귀 검증 + 첫 finance Integration job 실검증).
- **green-wash 금지 연계**: 본 task 는 TASK-FIN-BE-001 close 시 정직하게 surfaced 한 gap 의 해소 task — silently drop 되지 않았음을 INDEX/memory 가 추적 중. Failure Scenario A 가 실현되면 그것은 본 job 의 성공 (숨은 행위 gap 노출) 이며, fix 는 별 finance fix-task 로 분리.
