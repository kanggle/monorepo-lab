# Task ID

TASK-MONO-079

# Title

ADR-MONO-011 ACCEPTED transition + Phase 3 impl — `nightly-e2e.yml` 에 4 backend full e2e job 신설 (`wms-platform-e2e-full` / `fan-platform-e2e-full` / `scm-platform-e2e-full` / `gap-e2e-full`)

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- e2e
- workflow
- nightly
- impl

---

# Goal

ADR-MONO-011 (PROPOSED 2026-05-13, 본 task 와 동반 spec PR 산출물) 을 ACCEPTED 로 전환하면서 Phase 3 의 실제 코드 변경을 수행한다.

작업 = `.github/workflows/nightly-e2e.yml` 에 4 backend full e2e job 추가:

- `wms-platform-e2e-full` — `:projects:wms-platform:apps:gateway-service:e2eFullTest` (2 method)
- `fan-platform-e2e-full` — `:projects:fan-platform:tests:e2e:e2eFullTest` (1 class)
- `scm-platform-e2e-full` — `:projects:scm-platform:tests:e2e:e2eFullTest` (4 class)
- `gap-e2e-full` — `:projects:global-account-platform:tests:e2e:e2eFullTest` (3 class)

총 10 full unit (fan 1 + scm 4 + wms 2 + gap 3). Phase 2 에서 등록된 `:e2eFullTest` task 가 처음으로 CI 에서 호출되는 첫 cadence.

provenance: ADR-MONO-010 § 6.1 outstanding ("Phase 3 — nightly cadence + push-to-main gating") 의 이행. Phase 2 (TASK-MONO-076/077/078) 가 partition 을 authorise + Gradle 측 wiring 완료; Phase 3 = CI 측 cadence + wiring 의 첫 + 마지막 단계.

---

# Scope

## In Scope

### A. ADR-MONO-011 ACCEPTED 전환

- 사용자 explicit intent ("transition ADR-MONO-011 to ACCEPTED") 수신 후 impl 시작.
- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` 헤더 `Status: PROPOSED` → `Status: ACCEPTED`, History 라인 갱신, § Status transition history 표 row 2 채움.
- `docs/adr/INDEX.md` ADR-MONO-011 row Status `PROPOSED` → `ACCEPTED`.

### B. `wms-platform-e2e-full` job 신설 (`nightly-e2e.yml`)

`ci.yml` 의 `e2e-tests` job 패턴 답습 + 본 task § Implementation Notes 의 nightly 차이 4건:

```yaml
wms-platform-e2e-full:
  name: E2E full (wms gateway-master, Testcontainers)
  runs-on: ubuntu-latest
  timeout-minutes: 60
  if: github.repository == 'kanggle/monorepo-lab'

  steps:
    - Checkout
    - Set up JDK 21 (Temurin)
    - Set up Gradle
    - Build boot jars (in-job, no artifact reuse)
        run: ./gradlew :projects:wms-platform:apps:master-service:bootJar
                       :projects:wms-platform:apps:gateway-service:bootJar
                       --no-daemon --stacktrace
    - Verify Docker
    - Build service images (Docker CLI BuildKit)
    - Run gateway-master e2e full suite
        run: ./gradlew :projects:wms-platform:apps:gateway-service:e2eFullTest
                       -x :…:bootJar (양쪽)
                       -Dwms.e2e.masterImage=wms-master-service:e2e
                       -Dwms.e2e.gatewayImage=wms-gateway-service:e2e
                       --no-daemon --stacktrace
    - Upload e2e test reports on failure
        path: …/build/{reports,test-results}/tests/e2eFullTest/
```

### C. `fan-platform-e2e-full` job 신설 (`nightly-e2e.yml`)

`ci.yml` 의 `fan-platform-e2e` job 패턴 답습:

- boot jars build in-job: gateway + community + artist (3 service).
- Docker CLI image build: `fan-platform-gateway-service:e2e` + `fan-platform-community-service:e2e` + `fan-platform-artist-service:e2e`.
- `./gradlew :projects:fan-platform:tests:e2e:e2eFullTest -x :…:bootJar -Dfan.e2e.*Image=...`.
- timeout 60min. report path `e2eFullTest/`.

### D. `scm-platform-e2e-full` job 신설 (`nightly-e2e.yml`)

`ci.yml` 의 `scm-platform-e2e` job 패턴 답습:

- boot jars build in-job: gateway + procurement + inventory-visibility.
- Docker CLI image build: 3 image.
- `./gradlew :…:e2eFullTest -x :…:bootJar -Dscm.e2e.*Image=...`.
- timeout 60min. report path `e2eFullTest/`.

### E. `gap-e2e-full` job 신설 (`nightly-e2e.yml`)

gap 의 docker-compose 패턴 특수 처리 (ADR § D5):

```yaml
gap-e2e-full:
  name: E2E full (gap docker-compose)
  runs-on: ubuntu-latest
  timeout-minutes: 60
  if: github.repository == 'kanggle/monorepo-lab'

  steps:
    - Checkout
    - Set up JDK 21
    - Set up Gradle
    - Verify Docker
    - Run gap e2e full suite
        run: ./gradlew :projects:global-account-platform:tests:e2e:e2eFullTest
                       --no-daemon --stacktrace
    - Upload e2e test reports on failure
```

**boot-jars build 단계 없음** — gap 의 `ComposeFixture` 가 `@BeforeAll` 에서 `docker-compose build` 수행. **Docker CLI image build 단계 없음** — 동일 사유. ADR § D5 의 "no extra `docker compose down`" 도 적용 (ComposeFixture JVM shutdown hook).

### F. Header comment 갱신 (`nightly-e2e.yml`)

- 기존 header block (~L1~L20) 의 description 확장: "Runs the full-stack Playwright suite (ecommerce frontend-e2e-fullstack) + 4 backend full e2e suites (fan / scm / wms / gap) against main HEAD every night."
- TASK-MONO-079 + ADR-MONO-011 reference 추가.

### G. Spec / INDEX 갱신

- ADR-MONO-011 Status PROPOSED → ACCEPTED + transition history row 2.
- `docs/adr/INDEX.md` ADR-MONO-011 row Status 갱신.
- `tasks/INDEX.md` `## ready` → `## review` (impl PR) → `## done` (close chore).

## Out of Scope

- **Phase 3 § 6 outstanding 7건** 모두 본 task scope 밖:
  - Auto-issue / Slack on failure (v2)
  - Reusable workflow consolidation (Phase 4)
  - Matrix strategy (Phase 4)
  - Cost-budget telemetry (30-day observation 후)
  - PR-time gap smoke job (ADR-MONO-010 § 6.2 잔존)
  - Observability on nightly (ADR-MONO-007 § D3 explicit exclusion)
  - Retry-on-flake (2 weeks observation 후)
- `platform/testing-strategy.md` 갱신 — Phase 2 PR #428 에서 이미 4 insert 완료 (Pyramid block 의 "nightly + push-to-main" 표기 등 이미 반영).
- `ci.yml` 변경 — PR-time job 들은 본 task 미터치.
- Service production code 미터치.

---

# Acceptance Criteria

### Spec PR (본 PR — ADR PROPOSED + task ready)

- [ ] `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` 가 `Status: PROPOSED` 로 존재.
- [ ] `docs/adr/INDEX.md` 에 ADR-MONO-011 PROPOSED row 추가.
- [ ] `tasks/ready/TASK-MONO-079-nightly-full-e2e-impl.md` 가 본 파일 그대로 존재.
- [ ] `tasks/INDEX.md` `## ready` 에 본 task 1-line entry.
- [ ] markdown-only PR — Phase 1 markdown-skip 으로 e2e + build-and-test SKIP, `changes` job 만 활성 (TASK-MONO-074 자연 검증 5번째 사례).

### Impl PR (ACCEPTED 전환 후 단일 PR 권장 — 변경 면적 single workflow file)

- [ ] **B (wms-platform-e2e-full)**: `nightly-e2e.yml` 에 job 추가. boot-jars build in-job + Docker CLI image build + `:e2eFullTest` 호출 + report path `e2eFullTest/`.
- [ ] **C (fan-platform-e2e-full)**: 동일 패턴. 3 service boot jars + 3 image.
- [ ] **D (scm-platform-e2e-full)**: 동일. 3 service boot jars + 3 image.
- [ ] **E (gap-e2e-full)**: boot-jars 단계 없음, Docker CLI image build 단계 없음. `:e2eFullTest` 직접 호출.
- [ ] **F (header)**: description + ADR-011/TASK-079 reference 갱신.
- [ ] **G (status)**: ADR-MONO-011 PROPOSED → ACCEPTED + history row 2, `docs/adr/INDEX.md` row Status 갱신, `tasks/INDEX.md` ready → review.
- [ ] **First nightly run verification**: `gh workflow run nightly-e2e.yml --ref main` 또는 push to main 으로 trigger → 5 분 안 모든 job 시작 + 60 분 안 모두 종료. Test report row count = 10 (fan 1 + scm 4 + wms 2 + gap 3).
- [ ] **PR-time impact = 0**: 본 PR 머지 후 첫 번째 markdown-only or 일반 PR 의 ci.yml job 들이 변동 없음 (`workflows` flag 만 활성으로 self-CI 검증).
- [ ] **Repo gate**: 본 4 job 의 `if: github.repository == 'kanggle/monorepo-lab'` 명시.
- [ ] **Production code 0**: `git diff --stat -- 'projects/*/apps/*/src/main/**' 'libs/**/src/main/**'` = 0 rows. 본 task = workflow yaml 만 변경.
- [ ] task lifecycle: ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR (impl PR 머지 후)

- [ ] task file Status `review` → `done`.
- [ ] `git mv tasks/review/TASK-MONO-079-*.md tasks/done/TASK-MONO-079-*.md`.
- [ ] `tasks/INDEX.md` ## review 제거, ## done append 1-line outcome (impl PR # + commit + 첫 nightly run wall-clock + 10 full unit 실측 PASS/FAIL 분포).
- [ ] (선택) 첫 nightly run wall-clock 측정값을 ADR-MONO-011 § Status transition history row 2 의 Note 에 추가.

---

# Related Specs

- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` (본 task 가 ACCEPTED 로 전환)
- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` (Phase 2 direct parent — `:e2eFullTest` Gradle task 정의)
- `tasks/done/TASK-MONO-076-e2e-tag-taxonomy-impl.md` (Phase 2 fan+scm impl)
- `tasks/done/TASK-MONO-077-e2e-tag-impl-wms.md` (Phase 2 wms impl, sourceSets-split + method-level)
- `tasks/done/TASK-MONO-078-e2e-tag-impl-gap.md` (Phase 2 gap impl, docker-compose 패턴)
- `tasks/done/TASK-MONO-014-frontend-e2e-fullstack-nightly.md` (ecommerce nightly reference, 본 task 가 답습할 패턴)
- `tasks/done/TASK-MONO-045-ci-path-filter-and-nightly-e2e.md` (nightly split prior art)
- `.github/workflows/nightly-e2e.yml` (확장 대상)
- `.github/workflows/ci.yml` §§ `e2e-tests` / `fan-platform-e2e` / `scm-platform-e2e` / `gap-integration-tests` (PR-time smoke job 패턴 reference)

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md` — Testcontainers + docker-compose 패턴.

---

# Related Contracts

None — CI workflow extension.

---

# Target Service

N/A — workflow yaml only.

---

# Architecture

ADR-MONO-011 (PROPOSED) → ACCEPTED 전환 + impl. Phase 3 의 단일 task. multi-PR 분리 불필요 — workflow yaml 변경이 단일 file (`nightly-e2e.yml`) 라 한 PR 에 묶이는 게 자연.

---

# Implementation Notes

## boot-jars build in-job vs artifact reuse

ADR § D3 에서 "Option B (in-job build)" 채택. 이유:
- GitHub Actions 가 cross-workflow artifact reuse 지원 안 함 (`actions/upload-artifact` + `actions/download-artifact` 는 same-workflow 만).
- Nightly 의 latency 가 중요하지 않음 (push-to-main 의 ~30–60s 비용은 무시).
- 단순성 우선.

## ecommerce-boot-jars-nightly + frontend-e2e-fullstack 와의 의존성

`ecommerce-boot-jars-nightly` → `frontend-e2e-fullstack` 의존 (`needs: ecommerce-boot-jars-nightly`). 본 PR 의 4 backend full job 은 이 의존성 없음 — 자기 boot jars 를 직접 빌드. 4 backend job + ecommerce 의 2 job 이 parallel 실행.

## Docker CLI BuildKit 패턴

`ci.yml` 의 PR-time smoke job 의 `Build service images for e2e` step 그대로 답습:

```yaml
- name: Build service images for nightly full e2e
  run: |
    docker build -t wms-master-service:e2e \
      -f projects/wms-platform/apps/master-service/Dockerfile \
      projects/wms-platform/apps/master-service/
    docker build -t wms-gateway-service:e2e \
      -f projects/wms-platform/apps/gateway-service/Dockerfile \
      projects/wms-platform/apps/gateway-service/
```

Docker 28 BuildKit gRPC hang 회피 (incident `2026-05-05-ci-regression.md` § Root Cause #2 — TASK-MONO-044).

## gap 의 특수 처리

gap 의 `tests/e2e/build.gradle` 의 `e2eFullTest` task 는 `baseE2eConfig` closure 를 통해 `COMPOSE_PROJECT_NAME=gap-e2e` 환경변수 주입 + `timeout 5m` + observability 분기 보존. nightly job 에서 추가 환경변수 / network create 단계 불필요. `:e2eFullTest` 만 호출하면 됨.

## 헤더 comment 의 구체적 갱신 위치

기존 `nightly-e2e.yml` header (~L1~L20):
- 첫 줄 description: "Nightly E2E (full-stack web-store)" → "Nightly E2E (full-stack web-store + 4 backend full suites)" 또는 더 상세.
- comment block 의 "Runs the full-stack Playwright suite..." → 신규 backend job 4건 명시 + ADR-MONO-011 reference.

## TASK-MONO-014 의 SKIP_GAP_E2E 와 무관

기존 `frontend-e2e-fullstack` job 의 `SKIP_GAP_E2E=1` env 는 NextAuth GAP discovery 회피 목적 — 본 PR 의 4 backend full job 과 무관.

## Concurrency group

기존 `concurrency: group: nightly-e2e-${{ github.ref }} / cancel-in-progress: true` 가 workflow-level 정의 — 새 4 job 도 동일 group 에 속함. 즉 push to main 이 in-flight cron run 을 cancel 함. ADR § D3 에서 acceptable 명시.

---

# Edge Cases

- **`-x :…:bootJar` 누락**: e2eFullTest task 의 `dependsOn ':…:bootJar'` 가 in-job built jar 를 재 트리거 시도. 명시적 `-x :…:bootJar` 로 차단 (PR-time 패턴 답습).
- **wms 의 sourceSets-split 호환성**: `e2eFullTest` 는 Phase 2 PR #430 에서 `testClassesDirs = sourceSets.e2eTest.output.classesDirs` + `classpath = sourceSets.e2eTest.runtimeClasspath` 명시. Gradle 이 sourceSets compile 을 자동 수행하므로 추가 step 불필요.
- **First nightly run 에서 SupplierCircuitBreakerE2ETest flake**: ADR § 4.3 에서 ~95% green 예상. 첫 run 의 flake 는 Phase 4 retry-on-flake 후보로 기록.
- **gap default `test` task 와 e2eFullTest 의 분리**: 본 task 가 nightly 에서 `:e2eFullTest` 호출. default `test` (PR-time gap-integration-tests 가 호출) 는 변경 없음. 두 task 가 같은 5 class 를 분리해서 실행 (smoke 2 + full 3).
- **`workflow_dispatch` 의 ref 옵션**: `gh workflow run nightly-e2e.yml --ref main` 가 main 외 branch 에서도 trigger 가능. repo gate 가 catch 하지만, 본 task 의 검증에서 main HEAD 기준만 측정.
- **Docker daemon 부재 (silent skip)**: 모든 E2EBase 가 `@Testcontainers(disabledWithoutDocker = true)` 또는 `ComposeFixture` 의 docker 가용성 체크 보유. ubuntu-latest runner 에서 항상 docker 가용 → silent skip 예상 안 됨.

---

# Failure Scenarios

- **첫 nightly run 의 fail**: 즉시 triage. (a) genuine regression → fix task → 본 task close chore 와 별개; (b) flake → @Disabled 또는 retry-on-flake 후보 기록; (c) infrastructure → re-run + 한 cycle 관찰.
- **runner 시간 초과 (60 min)**: timeout 상향 검토. ADR § 4.4 의 cost 추정이 25 min 최대인데 60 min budget — 35 min 여유.
- **path-filter 의도치 않은 영향**: `nightly-e2e.yml` 은 path-filter 없음 (cron + push 이라 무조건 trigger). 영향 없음.
- **monorepo-lab 외 standalone repo 에서 job 누락 알람**: repo gate `if: github.repository == 'kanggle/monorepo-lab'` 으로 skip. 정상.

---

# Test Requirements

- **첫 nightly run wall-clock 측정**: 4 backend job 의 cold-start 포함 wall-clock 측정. ADR § 4.4 추정 (5–25 min/job) 과 비교. 50%+ 편차 시 Phase 4 cost-budget telemetry 트리거.
- **Test report row count**: 4 job 합쳐 10 full unit (fan 1 + scm 4 + wms 2 + gap 3) 실행 + report 확인.
- **PR-time impact 0**: 본 PR 머지 후 첫 PR 의 CI 16/16 변동 없음.
- **Production code 0** 검증.

---

# Definition of Done

### Impl PR

- [ ] AC B-G 모두 완료, 첫 nightly run 또는 workflow_dispatch trigger 1회 PASS.
- [ ] task lifecycle ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] review → done 이동, INDEX done 1-line outcome (PR # + commit + 첫 nightly run 측정값 + 10 full unit PASS/FAIL 분포).
- [ ] (선택) ADR § Status transition history row 2 Note 에 측정값 추가.

---

# Provenance

- ADR-MONO-010 § 6.1 outstanding ("Phase 3 — nightly cadence + push-to-main gating") 의 이행.
- 2026-05-13 사용자 세션 "Phase 3 ADR 시작" — Phase 2 완전 종결 (PR #427~#433) 직후 자연스러운 다음 분기.
- 직접 선행 = TASK-MONO-076 (Phase 2 1차 fan+scm, PR #428) + TASK-MONO-077 (wms, PR #430) + TASK-MONO-078 (gap, PR #432).
- TASK-MONO-014 (ecommerce frontend-e2e-fullstack nightly) 는 패턴 reference, 직접 dependency 아님.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical yaml edit; 패턴은 ci.yml PR-time job 4건과 nightly-e2e.yml ecommerce job 에서 검증된 기반).
