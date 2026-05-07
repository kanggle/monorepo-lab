# Task ID

TASK-MONO-048

# Title

scm-platform Integration CI job 추가 (procurement-service + inventory-visibility-service Testcontainers)

# Status

ready

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

scm-platform 의 `@Tag("integration")` Testcontainers IT 가 main CI 에서 실행되지 않는 결손을 메운다. 현재 [.github/workflows/ci.yml#L203-216](.github/workflows/ci.yml#L203-L216) `Build and check scm-platform backend (Docker-free)` step 의 주석이 명시: *"Docker-backed integrationTest is invoked separately on dev environments / **future scm-specific CI job**."* — 이 future job 을 본 task 에서 추가한다.

직접 트리거: [TASK-SCM-BE-002d](../../projects/scm-platform/tasks/review/TASK-SCM-BE-002d-procurement-testcontainers-it.md) PR #257 의 7 IT 가 local 만 검증되고 CI 미검증인 상태. 본 task 머지 후 PR #257 재 trigger → 7 IT 가 진짜 CI 에서 PASS 확인.

후속 영향:
- [TASK-SCM-INT-001](../../projects/scm-platform/tasks/ready/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) 의 cross-service E2E job (nightly) 추가가 본 service-internal IT job 위에 자연스럽게 쌓임.
- monorepo Phase 5 trigger 평가의 핵심 입력 — incident report L12 *"CI runner = only reproduction path"* 충족.

---

# Scope

## In Scope

### `.github/workflows/ci.yml` 변경

`gap-integration-tests` job (L566-620) 직답습으로 `scm-integration-tests` job 추가:

```yaml
scm-integration-tests:
  name: Integration (scm-platform, Testcontainers)
  runs-on: ubuntu-latest
  needs: [changes, build-and-test]
  timeout-minutes: 30
  # SCM integration suite boots MySQL + Postgres + Kafka + (per-service Redis/WireMock)
  # via Testcontainers. Scoped to monorepo-lab; standalone repo extraction
  # relies on its own Docker-free `check` signal.
  if: >-
    github.repository == 'kanggle/monorepo-lab' &&
    (
      github.event_name == 'push' ||
      needs.changes.outputs.libs == 'true' ||
      needs.changes.outputs.workflows == 'true' ||
      needs.changes.outputs.scm == 'true'
    )

  steps:
    - Checkout / setup-java 21 / setup-gradle / docker info  # gap-integration-tests 동일
    - name: Run SCM integration suites
      run: >-
        ./gradlew
        :projects:scm-platform:apps:procurement-service:integrationTest
        :projects:scm-platform:apps:inventory-visibility-service:integrationTest
        --no-daemon --stacktrace
    - upload reports on failure  # path: projects/scm-platform/apps/*/build/...
```

### `Build and check scm-platform backend` step 주석 정리

L204-207 의 *"future scm-specific CI job"* 주석을 *"runs in scm-integration-tests job below"* 로 갱신.

## Out of Scope

- `gateway-service` integrationTest 추가 — TASK-SCM-BE-001 시점 IT 없음. 추후 IT 도입 시 본 job step 에 추가 (별 task 불필요, ci.yml step 한 줄 추가).
- nightly e2e job (cross-service) — TASK-SCM-INT-001 scope.
- `scm-boot-jars` job — 본 task 범위 외 (procurement/inventory-visibility 가 boot jar 배포 단계 진입 시 별 task).

---

# Acceptance Criteria

1. `.github/workflows/ci.yml` 에 `scm-integration-tests` job 추가, `gap-integration-tests` job 의 구조 답습 (env / setup / docker info / upload reports 패턴 동일).
2. job 이 path-filter `scm` 변경 시 + push to main + libs/workflows 변경 시 활성화.
3. job 이 `procurement-service:integrationTest` + `inventory-visibility-service:integrationTest` 둘 다 실행.
4. PR open 후 self-CI 에서 본 job 실행 + PASS (procurement IT 7 + inventory-visibility IT 0 (현재) → 추후 IT 추가 시 자동 포함).
5. `Build and check scm-platform backend` step 주석에서 *"future"* 단어 제거, *"runs in scm-integration-tests job below"* 로 갱신.
6. 회귀 0: 다른 4 service 의 Integration / E2E job 영향 없음.

---

# Related Specs

- [TASK-SCM-BE-002d](../../projects/scm-platform/tasks/review/TASK-SCM-BE-002d-procurement-testcontainers-it.md) — 직접 후속 (본 task 머지 → 002d 재CI → 머지 권장)
- [TASK-SCM-INT-001](../../projects/scm-platform/tasks/ready/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) — 별 nightly e2e job 추가 (cross-service)
- [TASK-MONO-018](../done/TASK-MONO-018-gap-ci-and-outbox-scheduler.md) — `gap-integration-tests` job 도입 task (답습 reference)
- [TASK-MONO-045](../done/TASK-MONO-045-ci-path-filter-and-nightly.md) — `dorny/paths-filter@v3` + `scm` flag 도입
- [knowledge/incidents/2026-05-07-docker-cli-proxy-regression.md](../../knowledge/incidents/2026-05-07-docker-cli-proxy-regression.md) — CI Linux runner 가 유일한 reproduction path 인 배경

---

# Related Contracts

- 없음 (CI workflow 변경, 외부 contract 무관).

---

# Target Service / Component

- `.github/workflows/ci.yml`
- (no production code change)

---

# Implementation Notes

- 답습: `gap-integration-tests` job 직카피 + service 명만 SCM 으로 치환.
- `docker info` step 유지 — Testcontainers 사전 검증 표준.
- `--no-daemon --stacktrace` flag 유지 (다른 job 일치).
- upload artifact path glob: `projects/scm-platform/apps/*/build/{reports/tests/integrationTest,test-results/integrationTest}/`. retention 7 일.
- timeout-minutes: 30 (GAP 와 동일, procurement IT 7 + inventory-visibility 0 = 충분).
- 본 PR 자체는 `workflows` flag 트리거 → `Build & Test` + 모든 dedicated job 활성화 (전체 회귀 검증). 부수 효과로 첫 SCM Integration job 실 검증.
- Lifecycle: ready → in-progress (chore commit) → ci.yml 변경 commit → review (chore commit). 단일 PR.

---

# Edge Cases

1. **SCM 에 IT 가 0 인 service 포함 시 Gradle behavior**: `inventory-visibility-service:integrationTest` task 가 존재하지 않으면 Gradle FAIL. → 본 task 진행 전 task 가 정의돼 있는지 확인 (`./gradlew :projects:scm-platform:apps:inventory-visibility-service:tasks --all | grep integrationTest`). 없으면 그 service 는 step 에서 일시 제외 + 주석으로 명시 (추후 IT 도입 시 추가).
2. **path-filter race**: PR 이 ci.yml 만 변경 + scm code 변경 0 인 경우 → `workflows` flag 트리거 → 모든 dedicated job 활성화 (이미 045 정책). 본 PR 의 의도된 동작.
3. **Docker daemon 부재 (extracted standalone repo)**: `if: github.repository == 'kanggle/monorepo-lab'` 가드로 standalone repo 에서는 skip. GAP 패턴 동일.

---

# Failure Scenarios

## A. inventory-visibility-service 에 integrationTest task 가 없음

→ Gradle FAIL. 임시 조치: step 의 service 목록에서 일시 제외 + 주석 (`# inventory-visibility-service IT 추가 시 재포함`). 본 task PR description 에 명시.

## B. procurement IT 가 CI Linux runner 에서 local 과 다른 결과

→ local Rancher dockerd 29.1.3 vs CI ubuntu-latest dockerd 차이. 흔히 timing / port allocation. 진단: 본 task PR fail 시 002d 재CI 결과로 reproduce → 002d 후속 fix.

## C. ci.yml 문법 오류로 전체 workflow parse fail

→ `actionlint` 또는 `gh workflow view` 로 사전 검증. PR description 에 검증 결과 명시.

---

# Test Requirements

- self-PR 에서 `scm-integration-tests` job 활성화 + PASS.
- 다른 dedicated job (master / GAP / fan / Frontend) 회귀 0.
- ci.yml syntax valid.

---

# Definition of Done

- [ ] `scm-integration-tests` job 추가 + self-CI PASS
- [ ] L204-207 주석 갱신
- [ ] PR description 에 본 job 실행 결과 (job 시간 + tests=N skipped=0 failures=0) 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — 답습 카피 + 한 job 추가, 결정 무.
- **분량**: small — ci.yml 단일 파일 ~ 60줄 추가 + 주석 1줄 갱신.
- **dependency**:
  - `선행`: 없음 (gap-integration-tests 패턴 답습).
  - `후속`: [TASK-SCM-BE-002d](../../projects/scm-platform/tasks/review/TASK-SCM-BE-002d-procurement-testcontainers-it.md) 재CI → 머지 → Phase 5 trigger 평가 진행.
- **CI gating 영향**: 본 PR 의 ci.yml 변경 → `workflows` flag 트리거 → 모든 dedicated job 활성화 (의도된 회귀 검증).
