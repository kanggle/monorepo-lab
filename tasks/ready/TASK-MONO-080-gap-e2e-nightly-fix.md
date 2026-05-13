# Task ID

TASK-MONO-080

# Title

`gap-e2e-full` nightly job — 진단 + fix (TASK-MONO-079 첫 nightly run 의 10초 fail, ADR-MONO-010 D5 step 4 의 잘못된 가정 발견)

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- e2e
- gap
- fix
- impl

---

# Goal

TASK-MONO-079 (PR #435) 머지 직후 첫 nightly run (`8768c730` push trigger, run `25771710919`, 2026-05-13 01:05 UTC) 에서 4 backend full e2e job 중 **gap-e2e-full 만 10초 만에 fail**. 다른 3 job (wms / fan / scm) 은 모두 success (wms 2m3s / fan 1m55s / scm 4m37s — ADR-MONO-011 § D1 의 60min budget 대비 5-8% 사용, 정상). 본 task = gap-e2e-full 의 fail 진단 + fix.

진단 가설 (impl 첫 단계에 검증):

- **가설 A (가장 유력)**: `gap-e2e-full` job 이 `:e2eFullTest` 만 호출 — `ComposeFixture` 의 `docker compose up -d --build` 가 service Dockerfile 의 `COPY apps/<service>/build/libs/*.jar app.jar` 패턴에 의존한다면 boot jars 사전 build 누락으로 fail. 다른 3 nightly job (wms/fan/scm) 은 본 job 내 boot jars build step 보유 (ADR § D3 명시).
- **가설 B**: docker-compose.e2e.yml 의 build context 가 working directory 의존성 — ComposeFixture 의 `pb.directory(composeFile.getParentFile())` 가 `projects/global-account-platform/` 으로 설정되어 정상이어야 하나, gradle e2eFullTest task 의 working dir 가 `projects/global-account-platform/tests/e2e/` 일 가능성 — file path resolution mismatch.
- **가설 C**: gap docker-compose 의 service image 가 `image: name:tag` 형태 (pre-pulled) — `up -d --build` 가 build 시도 시 build context 부재로 fail.

또한 **ADR-MONO-010 D5 step 4 의 잘못된 가정 발견**: ADR 는 "`gap-integration-tests` CI job 이 default `test` 호출" 이라 명시하나, 실제 ci.yml 의 `gap-integration-tests` job 은 7 service 의 `:integrationTest` 만 호출 — gap 의 `tests/e2e/` 모듈 (`tests/e2e/build.gradle` 의 default `test` 또는 e2eFullTest/e2eSmokeTest) 을 **호출하는 PR-time CI job 이 어디에도 없음**. 즉 TASK-MONO-078 (PR #432) 가 도입한 gap 의 e2e `@Tag` partition 은 CI 측면에서 **dead code** 였음 (`gap-e2e-full` nightly job 이 첫 호출 케이스).

provenance: TASK-MONO-079 (PR #435, ADR-MONO-011 ACCEPTED + Phase 3 impl) 첫 nightly run `25771710919` 의 결정적 실측. ADR-MONO-011 § D4 의 "triage within 1 business day → file fix task" 정책 그대로 이행.

---

# Scope

## In Scope

### A. 진단 (impl 의 Phase 0)

본격 fix 전 다음 진단 수행:

1. **gap-e2e-full job 의 실제 log 분석**: `gh run view 25771710919 --job 75696047899 --log` (run 종료 후) — `Run gap docker-compose e2e full suite` step 의 정확한 fail 출력 (stack trace, gradle error message, docker-compose stderr).
2. **로컬 재현**: `./gradlew :projects:global-account-platform:tests:e2e:e2eFullTest` 호출 → fail message 동일성 확인. Docker daemon 가용한 로컬에서.
3. **docker-compose.e2e.yml service 구조 검증**: 각 service 의 `build:` directive vs `image:` directive. boot jar 사전 build 의존성 여부.
4. **PR-time CI 의 gap-integration-tests** 가 호출한 적 없는 e2e module 인지 git log + `gh run list --workflow=ci.yml --json jobs` 로 확인.

진단 결과로 가설 A/B/C 중 하나 또는 조합 확정.

### B. Fix (가설별 대응 — impl 시 정확 진단 후 선택)

#### B-A (가설 A — boot jars 누락)

`gap-e2e-full` job 에 boot jars build step 추가. gap 의 docker-compose.e2e.yml 이 build 하는 4 service 모두:

```yaml
- name: Build gap boot jars
  run: >-
    ./gradlew
    :projects:global-account-platform:apps:auth-service:bootJar
    :projects:global-account-platform:apps:account-service:bootJar
    :projects:global-account-platform:apps:admin-service:bootJar
    :projects:global-account-platform:apps:security-service:bootJar
    --no-daemon --stacktrace
```

(verify: gap 의 service list — auth / account / admin / security 가 docker-compose.e2e.yml 의 build target.)

#### B-B (가설 B — working dir)

gradle `e2eFullTest` task 의 working dir 를 명시적으로 `projects/global-account-platform/` 로 설정 — `tests/e2e/build.gradle` 의 `baseE2eConfig` closure 에 `t.workingDir = file("${rootDir}/projects/global-account-platform")` 추가. 또는 ComposeFixture 의 `locateComposeFile()` 가 정상 작동하는지 verify.

#### B-C (가설 C — image: directive)

gap docker-compose.e2e.yml 의 service 정의를 `image: name:tag` 로 변경 + 사전 build & tag step 추가 — wms/fan/scm 패턴 답습 (Docker CLI BuildKit + `:e2e` tag).

### C. ADR-MONO-010 D5 step 4 의 잘못된 가정 정정

ADR-MONO-010 D5 step 4 의 본문 "`gap-integration-tests` CI job 이 default `test` 호출 유지, full 제외로 wall-clock 단축 효과만 받음" 문장은 사실과 다름 — 정정 필요.

옵션:
- **C-1 (최소 수정)**: ADR-MONO-010 본문 직접 수정 안 함 (ACCEPTED 상태 보존). 본 task body 의 진단 결과로 정정 사실을 audit-trail 에 기록.
- **C-2 (정정 ADR)**: ADR-MONO-010a 또는 ADR-MONO-012 로 정정 ADR 발행. 본 task scope 외 — 별도 spec PR 후보.

본 task scope = **C-1 채택** (audit-trail only). C-2 는 별도 follow-up task.

### D. 첫 nightly run wall-clock 측정값 기록

진단 부산물 — TASK-MONO-079 close outcome 에 4 job wall-clock 측정 적립 (wms 2m3s / fan 1m55s / scm 4m37s / gap 33s fail). 본 task 의 § Provenance 에 reference 만.

## Out of Scope

- **TASK-MONO-079 의 다른 측면 재검증** (wms / fan / scm 의 success 결과 보존, 본 task 는 gap 단독 fix).
- **gap PR-time smoke job 신설** — ADR-MONO-010 § 6.2 outstanding, 별도 task.
- **ADR-MONO-010 본문 수정 또는 정정 ADR 발행** — 별도 task (옵션 C-2).
- **Service production code 변경** — 본 task = workflow yaml + 가능한 build.gradle workingDir 변경만.
- **Phase 4 follow-up 들** (matrix / reusable / retry-on-flake / cost telemetry).

---

# Acceptance Criteria

### Impl PR

- [ ] **진단 Phase 0**: gap-e2e-full job log 분석 + 로컬 재현 + docker-compose.e2e.yml 구조 검증. 가설 A/B/C 중 확정.
- [ ] **Fix**: 가설별 대응 (B-A or B-B or B-C, 또는 조합). 정확한 yaml/gradle 변경.
- [ ] **재검증**: 본 PR 머지 후 push-to-main 트리거 또는 `gh workflow run nightly-e2e.yml --ref main` → gap-e2e-full GREEN within 60min.
- [ ] **다른 3 backend full job 영향 0**: wms-platform-e2e-full + fan-platform-e2e-full + scm-platform-e2e-full + ecommerce-boot-jars-nightly + frontend-e2e-fullstack 모두 본 PR 전후 동일 GREEN.
- [ ] **Production code 0**: `git diff --stat -- 'projects/*/apps/*/src/main/**' 'libs/**/src/main/**'` = 0.
- [ ] task lifecycle ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] task file Status `review` → `done`.
- [ ] `git mv tasks/review/TASK-MONO-080-*.md tasks/done/TASK-MONO-080-*.md`.
- [ ] `tasks/INDEX.md` ## review 제거, ## done append 1-line outcome (impl PR # + commit + gap-e2e-full 후속 nightly run wall-clock 측정 + 가설 확정 결과).

---

# Related Specs

- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` § D4 "triage within 1 business day → file fix task" 정책 (본 task 가 이행).
- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` § D5 step 4 — gap 가정 정정 필요 (옵션 C-1).
- `tasks/done/TASK-MONO-079-nightly-full-e2e-impl.md` (직접 선행, gap fail 의 발견 PR).
- `tasks/done/TASK-MONO-078-e2e-tag-impl-gap.md` (gap Phase 2 impl, 본 task 가 검증하는 partition 의 author).
- `.github/workflows/nightly-e2e.yml` § `gap-e2e-full` job (수정 대상).
- `projects/global-account-platform/docker-compose.e2e.yml` (build context 검증 대상).
- `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/ComposeFixture.java` (locateComposeFile + startCompose 검증 대상).
- `projects/global-account-platform/tests/e2e/build.gradle` (workingDir 가능한 변경 대상).

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md` — docker-compose 패턴.

---

# Related Contracts

None.

---

# Target Service

`projects/global-account-platform/tests/e2e/` + `.github/workflows/nightly-e2e.yml` § `gap-e2e-full` job.

---

# Architecture

Phase 3 후속 fix task. ADR-MONO-010/-011 본문 직접 수정 안 함 (옵션 C-1) — audit-trail only.

---

# Implementation Notes

## Run / Job ID

- Failing run: `25771710919` (push to main `8768c730`, 2026-05-13 01:05 UTC).
- gap-e2e-full job: `75696047899`.
- Log 명령: `gh run view --job 75696047899 --log`.

## Step 별 시간 (33초 분포)

`Run gap docker-compose e2e full suite` step: 01:06:07 → 01:06:17 = **10초** fail. 다른 step (Checkout / JDK / Gradle / Verify Docker) 모두 success.

## 가설 A 의 verify 방법

`projects/global-account-platform/apps/<service>/Dockerfile` 의 `COPY` 라인 확인:
- `COPY build/libs/*.jar app.jar` 또는 `COPY apps/<service>/build/libs/*.jar app.jar` 또는 `RUN gradle bootJar` (in-build).
- 첫 두 패턴 = boot jar 사전 build 필요.
- 세 번째 = Dockerfile 내부 build → 사전 build 불필요.

## 가설 B 의 verify 방법

`ComposeFixture.java` 의 `locateComposeFile()` 메서드 (file 67-159, 본 task 시 read 필요). working dir 가 어떻게 resolve 되는지.

## 다른 3 service 의 success 패턴 reference

`nightly-e2e.yml` 의 `wms-platform-e2e-full` / `fan-platform-e2e-full` / `scm-platform-e2e-full` 모두 다음 sequence:

1. Checkout
2. JDK 21
3. Gradle setup
4. Build boot jars (in-job, `:bootJar` task)
5. Verify Docker
6. Build service images (Docker CLI BuildKit, `:e2e` tag)
7. Run :e2eFullTest with `-x :…:bootJar` + `-D<service>.e2e.<X>Image=<X>:e2e`
8. Upload reports on failure

gap 의 success 패턴은 위 sequence 의 step 4 + 6 가 필요할 수도 있음 (가설 A). step 6 는 image: directive 가 docker-compose.e2e.yml 에 명시되어야 의미 — 현재 build: directive 일 가능성 (가설 C).

## PR-time CI 의 gap-integration-tests 가 호출 안 한 e2e 의 의미

gap 의 e2e module (`projects/global-account-platform/tests/e2e/`) 는 PR-time CI 의 어떤 job 도 호출하지 않음. 따라서:

- TASK-BE-041c (gap e2e suite 최초 author, 추정 2026-04-25 ~ PR #58 시점) 이후 본 module 의 코드가 main 에서 자동 실행된 적 없음.
- 로컬 dev 또는 수동 trigger 만 검증 통로.
- TASK-MONO-078 (PR #432) 의 `@Tag` partition + `excludeTags 'full'` 도 CI 측면에서 의미 없었음 (호출 자체가 없으니).
- TASK-MONO-079 의 gap-e2e-full 이 main 의 첫 자동 호출 — 첫 호출 fail = 그 동안 누적된 drift 가능성 (코드 vs docker-compose vs Dockerfile 의 mismatch).

이 발견은 **gap e2e module 의 CI 호출 활성화 + drift 정정** 이 더 큰 후속 작업 후보. 본 task = nightly 만 GREEN 으로 만들기 + 후속 PR-time job 신설은 ADR-MONO-010 § 6.2 outstanding.

---

# Edge Cases

- **가설 A/B/C 모두 해당**: 복합 원인 가능 — boot jars + workingDir + image directive 모두 손봐야. impl 시 진단 결과로 선택.
- **gap docker-compose.e2e.yml 의 service 가 4 외에 더 있음 (community / membership / gateway 등)**: boot jars 추가 + Dockerfile 확인.
- **First fix 후에도 fail**: cycle 2 진단. memory `project_046_7_11_cycle_burn.md` 의 6-cycle 임계값 적용.
- **Local 재현 불가** (Docker Desktop 의 npipe regression — memory `project_testcontainers_docker_desktop_blocker.md` 의 2026-05-08 회귀): Rancher Desktop 환경 또는 CI Linux runner 만 검증 가능.
- **드물게 옵션 C-2 가 채택**: 정정 ADR 발행 시 본 task scope 확대 — 별도 spec PR + 본 task scope 보존.

---

# Failure Scenarios

- **6 cycle 임계값 초과**: fix 시도 6회 후에도 fail — 본 task 를 partial close + 별도 deeper investigation task (TASK-MONO-080-1 등) 분리.
- **gap docker-compose.e2e.yml 자체의 drift**: 본 task scope 확대 — service code / docker-compose / Dockerfile 동시 정정. PR 면적이 너무 크면 chunk 별 PR 권장.
- **다른 3 backend full job 의 회귀**: 본 PR 의 의도 없는 부작용. self-CI 가 catch.

---

# Test Requirements

- **gap-e2e-full GREEN within 60min budget** — 본 PR 머지 후 첫 nightly run.
- **다른 3 backend full job 영향 0** — 동일 run 의 wms/fan/scm 도 GREEN.
- **로컬 재현 PASS** (가능 시).
- **Production code 0** 검증.

---

# Definition of Done

### Impl PR

- [ ] 진단 + fix 완료, push-to-main 또는 workflow_dispatch trigger 로 gap-e2e-full GREEN.
- [ ] task lifecycle: ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기. outcome 에 fix 측정값 (cycle 수, wall-clock, 가설 확정 결과).

---

# Provenance

- 2026-05-13 01:05 UTC 첫 nightly run (`25771710919`, push to main `8768c730`) 의 결정적 fail.
- ADR-MONO-011 § D4 "triage within 1 business day → file fix task" 정책 이행.
- 직접 선행 = TASK-MONO-079 (PR #435) + TASK-MONO-078 (PR #432).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (진단 우선, 가설 3건 분기 + ADR audit-trail 정정 + multi-cycle 가능성).
