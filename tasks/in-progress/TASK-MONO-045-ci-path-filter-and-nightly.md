# Task ID

TASK-MONO-045

# Title

CI 시간 단축 — path-based job filtering + frontend-e2e full-stack nightly cron 분리

# Status

in-progress

# Owner

devops

# Task Tags

- ci
- infra

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

5 프로젝트 동거 (wms / ecommerce / GAP / fan-platform / scm) + 50+ 서비스 + frontend full-stack e2e 가 누적되어 일반 PR CI 가 ~15분 단일 보틀넥 (`Frontend E2E full-stack (web-store)` Job) 으로 수렴. 변경 범위 대부분이 단일 프로젝트인데 모든 e2e/integration job 이 매번 돌고 있어 비효율.

본 task 가 fix 후 일반 project-internal PR CI **15분 → 3-5분**, frontend-e2e full-stack 보틀넥 PR 임계 경로에서 제거 (nightly main HEAD 검증으로 회귀 백업 catch).

기대 효과 (PR 종류별):

| PR 종류 | 현재 | 적용 후 |
|---|---|---|
| spec/chore (`tasks/**` 만) | 15분 | 1-2분 |
| `projects/scm-platform/**` 만 | 15분 | 2-3분 |
| `projects/wms-platform/**` 만 | 15분 | 5-7분 |
| `projects/global-account-platform/**` 만 | 15분 | 5-8분 |
| `projects/fan-platform/**` 만 | 15분 | 5-7분 (2순위 적용 시) |
| `projects/ecommerce-microservices-platform/**` 만 | 15분 | 3-5분 (2순위 적용 시) |
| `libs/**` / `platform/**` / `rules/**` / `.claude/**` / `.github/**` | 15분 | 15분 (전체 fallback, 정상) |

---

# Scope

## In Scope

### 1. Path-based job filtering (1순위)

`.github/workflows/ci.yml` 의 12 job 에 `dorny/paths-filter@v3` 또는 GitHub Actions native `paths` filter 적용. 변경된 경로에 따라 각 job 의 활성/비활성 결정.

Filter 규칙 표 (project ↔ job 매트릭스):

| Path | 활성화될 job |
|---|---|
| `libs/**` 또는 `platform/**` 또는 `rules/**` 또는 `.claude/**` 또는 `.github/workflows/**` 또는 `tasks/templates/**` 또는 root `build.gradle` / `settings.gradle` / `package.json` / `tsconfig.base.json` | **전체** (cross-project 영향 가능) |
| `projects/wms-platform/**` 만 | `build-and-test` (wms 모듈만) + `boot-jars` (wms) + `integration-tests` (master) + e2e gateway-master |
| `projects/ecommerce-microservices-platform/**` 만 | `build-and-test` (ecommerce 모듈) + `ecommerce-boot-jars` + `frontend-unit-tests` (ecommerce) + `frontend-checks` (ecommerce) + `frontend-e2e-smoke` (web-store 부분) + (2순위 적용 후) frontend-e2e-fullstack 미실행 |
| `projects/global-account-platform/**` 만 | `build-and-test` (gap 모듈) + `gap-integration-tests` |
| `projects/fan-platform/**` 만 | `build-and-test` (fan-platform 모듈) + `fan-platform-boot-jars` + `frontend-unit-tests` (fan-platform) + `frontend-checks` (fan-platform) + `frontend-e2e-smoke` (fan-platform-web) + e2e fan-platform live-trio |
| `projects/scm-platform/**` 만 | `build-and-test` (scm 모듈만) — 현재 scm 은 e2e 없음 |
| `tasks/**` 또는 `*.md` 만 | (대부분 skip — minimal lint/check 만) |

구현 옵션:
- **(i) `dorny/paths-filter@v3`**: 단일 step 으로 path → output 매핑 후 후속 job 의 `if:` 로 조건부 실행. 권장 — 단일 진실 표.
- **(ii) GitHub Actions native `paths:`** 트리거: 각 workflow 파일을 분리 (`.github/workflows/wms-ci.yml` 등). 더 명시적이지만 workflow 파일 다수 → 유지보수 부담.

권장: **(i)**. PR description 에 결정 근거 기록.

### 2. Frontend E2E full-stack 을 nightly cron 으로 분리 (2순위)

`Frontend E2E full-stack (web-store, Playwright + docker compose)` Job 을 PR CI 에서 제거하고 별도 workflow `.github/workflows/nightly-e2e.yml` 로 이전:
- `schedule:` cron `0 18 * * *` (UTC 18:00 = 한국 03:00) — 트래픽 적은 시간대
- `workflow_dispatch:` 도 추가 (수동 실행 가능)
- `branches: [main]` 만 — PR 에서는 절대 안 돌림
- 실행 결과: 4 spec (`golden-flow`, `cart-management`, `auth-redirect`, `wishlist`) 모두 PASS 검증
- 실패 시 알림: v1 은 GitHub Actions UI / main badge 만 (포트폴리오 스코프). v2 는 issue auto-create 또는 Slack webhook (out of scope)

PR 이 ecommerce 영역을 만지면 자동 실행되는 옵션: 1순위 path-filter 의 룰에 따라 ecommerce-only PR 은 nightly 만으로 충분, libs/cross-project PR 은 PR CI 에 fullstack 다시 포함 (조건부 트리거 + manual `workflow_dispatch` 버튼).

### 3. INCIDENT 보고서 갱신

`knowledge/incidents/2026-05-05-ci-regression.md` 에 단락 추가 — 본 task 가 향후 CI 보틀넥 인식 + 해결 history 의 일부로 기록.

## Out of Scope

- Gradle build cache (`actions/cache` 로 `~/.gradle/caches`) — 1·2 적용 후에도 부족하면 별도 task
- Testcontainers reuse (`testcontainers.reuse.enable=true`) — 별도 task (test 인프라 영역)
- Docker Buildx layer cache (`actions/cache` 로 buildx) — 별도 task
- 유료 GitHub runner (4-core/16GB) 도입 — 비용/효과 별도 검토 후 task
- nightly 결과 알림 자동화 (issue / Slack) — v1 에서는 main badge 만, v2 검토
- 대형 e2e suite split (web-store full-stack 시나리오 4 spec → 병렬 4 job) — 별도 task

---

# Acceptance Criteria

## 1순위 path-filter

1. `.github/workflows/ci.yml` 에 path-filter 적용 — 단일 진실 표 (어떤 path 가 어떤 job 활성화하는지 yaml 또는 step 출력으로 명시)
2. 다음 PR 종류별 CI 시간 측정 + 기대 시간 충족:
   - spec/chore PR (tasks/** 만 변경): < 3분
   - 단일 project-internal PR (`projects/scm-platform/**` 등): < 8분
   - libs/platform/cross-project PR: 기존과 동일 (전체 fallback)
3. CI workflow 자체 변경 (`.github/workflows/**`) PR 은 항상 전체 job 활성화 (회귀 catch 보장)
4. path-filter 적용 후 머지된 PR 의 main 후속 push CI 결과가 PR CI 결과와 동일 (skipped job 으로 인한 main 회귀 0)

## 2순위 nightly cron

5. `.github/workflows/nightly-e2e.yml` 신설 — `schedule:` + `workflow_dispatch:` 트리거, `branches: [main]`
6. nightly 첫 run 결과: 4 web-store full-stack spec 모두 PASS
7. PR CI 의 `Frontend E2E full-stack (web-store)` Job 제거 (1순위 path-filter 와 충돌 없게 — ecommerce 변경 PR 도 PR 시점에 fullstack 안 돌림, nightly 가 catch)
8. (선택) libs/** 또는 cross-project 변경 PR 에서는 fullstack 도 PR CI 에 포함하도록 조건부 (선택사항, AC 5-7 충족 후 검토)

## 검증

9. 본 PR 이후 3개 후속 PR 의 평균 CI 시간 측정 (PR 종류별 분포 기록)
10. main HEAD 첫 nightly run 결과 PASS 확인 (workflow_dispatch 로 수동 trigger 가능)

## 문서

11. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 단락 추가 (CI 보틀넥 history)
12. `tasks/INDEX.md` done 항목에 outcome 라인 (적용 후 측정한 평균 CI 시간 포함)
13. `docs/guides/` 에 path-filter 룰 표 + nightly cron 정책 1쪽 (선택, AC 1-12 충족 후)

---

# Related Specs

- `.github/workflows/ci.yml` — 변경 대상
- [TASK-MONO-008 ecommerce subset CI 추가](../done/TASK-MONO-008-extend-ci-with-passing-ecommerce-services.md) — 초기 ecommerce CI 도입 history
- [TASK-MONO-013 frontend e2e smoke CI](../done/TASK-MONO-013-frontend-e2e-smoke-ci.md)
- [TASK-MONO-014 frontend e2e fullstack CI](../done/TASK-MONO-014-frontend-e2e-fullstack-ci.md) — 본 task 가 nightly 로 이전 대상
- [TASK-MONO-040 scm-platform bootstrap](../done/TASK-MONO-040-scm-platform-bootstrap.md) — 5번째 프로젝트 동거 시작 → CI scope 확장
- `knowledge/incidents/2026-05-05-ci-regression.md` — CI 보틀넥 분석 history

---

# Related Contracts

- 없음 (CI 인프라)

---

# Target Service / Component

- `.github/workflows/ci.yml` — path-filter 적용 + frontend-e2e-fullstack job 이전
- `.github/workflows/nightly-e2e.yml` (신설) — schedule cron + workflow_dispatch
- (선택) `docs/guides/ci-path-filter-rules.md` (신설) — 룰 표 + 정책

---

# Implementation Notes

## 1순위 path-filter — `dorny/paths-filter@v3` 채택 시 패턴

```yaml
jobs:
  changes:
    runs-on: ubuntu-latest
    outputs:
      libs: ${{ steps.filter.outputs.libs }}
      wms: ${{ steps.filter.outputs.wms }}
      ecommerce: ${{ steps.filter.outputs.ecommerce }}
      gap: ${{ steps.filter.outputs.gap }}
      fan-platform: ${{ steps.filter.outputs.fan-platform }}
      scm: ${{ steps.filter.outputs.scm }}
      workflows: ${{ steps.filter.outputs.workflows }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            libs:
              - 'libs/**'
              - 'platform/**'
              - 'rules/**'
              - '.claude/**'
              - 'build.gradle'
              - 'settings.gradle'
              - 'package.json'
              - 'tsconfig.base.json'
            workflows:
              - '.github/workflows/**'
            wms:
              - 'projects/wms-platform/**'
            ecommerce:
              - 'projects/ecommerce-microservices-platform/**'
            gap:
              - 'projects/global-account-platform/**'
            fan-platform:
              - 'projects/fan-platform/**'
            scm:
              - 'projects/scm-platform/**'

  gap-integration-tests:
    needs: [build-and-test, changes]
    if: ${{ needs.changes.outputs.libs == 'true' || needs.changes.outputs.workflows == 'true' || needs.changes.outputs.gap == 'true' }}
    # ... rest unchanged
```

핵심 패턴:
- `changes` job 이 단일 진실 — 모든 project flag + libs/workflows fallback flag 출력
- 각 후속 job 의 `if:` 에 `libs == 'true' || workflows == 'true' || <자기-project> == 'true'` 패턴
- `libs == 'true'` 또는 `workflows == 'true'` 면 모든 후속 job 활성 (cross-project 영향 fallback)

## 2순위 nightly cron — `nightly-e2e.yml` 신설

```yaml
name: Nightly E2E (full-stack)
on:
  schedule:
    - cron: '0 18 * * *'  # UTC 18:00 = KST 03:00
  workflow_dispatch:

permissions:
  contents: read
  checks: write

jobs:
  frontend-e2e-fullstack:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      # 기존 ci.yml 의 frontend-e2e-fullstack job 그대로 이동
      # ...
```

기존 `ci.yml` 의 `frontend-e2e-fullstack` job 은 **삭제 또는 제거** (path-filter 룰만으로는 ecommerce PR 에서도 안 돌게 하기 어려우므로 nightly 로 이동이 깔끔).

## 검증 명령

```bash
# 로컬 path-filter 룰 검증 (act 또는 dry-run)
act pull_request -W .github/workflows/ci.yml -j changes --dryrun

# nightly workflow_dispatch 수동 trigger
gh workflow run nightly-e2e.yml --ref main

# main HEAD 의 nightly 결과 확인
gh run list --workflow nightly-e2e.yml --branch main --limit 5
```

---

# Edge Cases

1. **PR 이 여러 project 동시 수정 (cross-project 의도된 변경)**: 각 project flag 가 모두 true → 해당 project 들의 job 모두 활성. libs flag 는 false. 정상.
2. **PR 이 `tasks/<project>/` 변경 (project 내부 task spec)**: project flag 활성화 — task 변경은 해당 project 의 spec 변경이므로 일관성 검증 의미. 단, root `tasks/**` 는 chore (전체 skip 가능).
3. **Force-push 또는 rebase 후 path-filter 결과 stale**: GitHub Actions 가 force-push 마다 재계산하므로 안전.
4. **nightly cron 이 GitHub 무료 limit 도달**: Actions 무료 분량 (월 2,000분) — 1회/일 × 30분 = 월 900분. 여유 있음.
5. **nightly 실패 시 main badge red**: 알림 자동화 v2 까지는 사람이 manually check. main badge 가 red 면 알아챔.
6. **path-filter step 자체 실패 (action 버그)**: `dorny/paths-filter@v3` 는 안정적이지만, 만약 step fail 시 모든 후속 job 의 `if:` 가 false 가 되어 *모두 skip* 되는 위험. 안전장치: `if: ${{ always() && (...) }}` 또는 `failure() == false` 추가.
7. **Workflow 자체 변경 PR 에서 path-filter 가 자기 자신 변경 인식 못 함**: `workflows` 패턴이 `.github/workflows/**` 로 잡으므로 정상. 단, 첫 적용 PR 은 자기 자신을 path-filter 로 분류 못 하므로 — 첫 PR 은 manual override 또는 workflow 직접 push 후 별도 PR.

---

# Failure Scenarios

## A. Path-filter 적용 후 회귀 missed

특정 cross-project 영향 변경이 path-filter 룰을 빠져나가 main 에서 회귀 발생. mitigation: AC #4 (PR CI 결과 ↔ main CI 결과 일치 검증) + libs/platform/rules/.github fallback 룰. 회귀 발견 시 룰에 path 추가.

## B. nightly fullstack 회귀가 24h 동안 발견 안 됨

main 에 회귀 머지 후 익일 nightly 실행 전까지 catch 못 함. v1 은 acceptable (포트폴리오 스코프). 회귀 추적 비용이 커지면 v2 에서 PR CI 에 다시 추가 또는 workflow_dispatch 트리거 빈도 증가.

## C. dorny/paths-filter@v3 가 PR base SHA 잘못 계산

force-push / rebase 후 base 추적 실패 가능성. mitigation: `base: main` 명시 + 의심 시 close+reopen.

## D. nightly cron 이 GitHub Actions 일시 장애로 누락

여러 일 연속 누락 시 회귀 catch 못함. mitigation: nightly 실행 history monitoring (수동 또는 `gh run list --workflow nightly-e2e.yml`).

---

# Test Requirements

- 본 PR 이후 첫 머지 PR (다양한 project) 의 CI 시간을 PR description 에 기록 (효과 검증)
- nightly workflow 첫 run (workflow_dispatch 수동 trigger) PASS 확인
- libs/** 변경 PR 시뮬레이션 (예: `libs/java-event/build.gradle` 1줄 추가) → 전체 job 활성 확인
- 단일 project PR 시뮬레이션 (예: `projects/scm-platform/apps/gateway-service/.../foo.java` 변경) → scm 관련 job 만 활성 확인

---

# Definition of Done

- [ ] path-filter changes job 추가 + 모든 후속 job 의 `if:` 조건부 적용
- [ ] frontend-e2e-fullstack job 을 nightly-e2e.yml 로 이전 + 기존 ci.yml 에서 제거
- [ ] nightly workflow_dispatch 수동 trigger PASS 확인
- [ ] PR description 에 1순위/2순위 옵션 결정 근거 기록
- [ ] tasks/INDEX.md done 항목에 outcome 라인 (평균 CI 시간 측정 결과)
- [ ] knowledge/incidents/2026-05-05-ci-regression.md 에 단락 추가
- [ ] 후속 PR 3건의 CI 시간 측정 (효과 정량화)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — yml 패턴 조합 + path 매칭 룰 작성. Opus 까지는 불필요. 분석은 Opus 4.7 수행 완료.
- **분량 추정**: spec PR (본 task) 1개 + impl PR 1개. impl PR = `.github/workflows/ci.yml` 수정 + `.github/workflows/nightly-e2e.yml` 신설 (~150 lines yaml). 작은 PR.
- **dependency**:
  - `선행`: 없음 (TASK-MONO-044 시리즈 종결 후 안정 main 시점 적용 권장 — 이미 그 시점)
  - `후속`: 효과 부족 시 Gradle build cache / Testcontainers reuse / Docker layer cache 별도 task
- **CI 자체 변경 시 주의**: 첫 적용 PR 은 path-filter 룰이 자기 자신을 인식 못 함 → 안전하게 전체 fallback (`.github/workflows/**` 패턴이 잡음).
- **포트폴리오 스코프**: nightly 실패 알림 자동화 v2 는 GitHub Actions 무료 한계 + 평가자 시간 모형 고려해 미루는 게 합리적.
- **분석=Opus 4.7 / 구현 권장=Sonnet**.
