# Task ID

TASK-MONO-081

# Title

`gap-e2e-full` nightly job 에 boot jars build step 추가 (TASK-MONO-080 cycle 2 발견의 잔존 fix + ADR-MONO-011 § D5 결정 오류 정정)

# Status

in-progress

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

TASK-MONO-080 (PR #437, 2026-05-13 cycle 1) 의 fix 1+2+3 머지 후 push-to-main trigger 자동 발동된 두 번째 nightly run `25773183881` (`e8e53eb6`, 2026-05-13 01:48 UTC) 에서 **gap-e2e-full 여전히 fail (54s)**. cycle 1 의 task-not-found 는 해소, cycle 2 에서 다른 fail mode 발견:

```
#20 [admin-service build 3/4] COPY apps/admin-service/build/libs/ ./libs/
#20 ERROR: failed to calculate checksum of ref ...: "/apps/admin-service/build/libs": not found
java.lang.IllegalStateException: docker compose up failed with exit code 1
```

ComposeFixture 의 `docker compose up -d --build` 가 service Dockerfile 의 `COPY apps/<service>/build/libs/ ./libs/` 패턴에 의존 — boot jars 사전 build 가 필수.

**ADR-MONO-011 § D5 결정 오류**: "gap-e2e-full omits boot-jars build steps — ComposeFixture handles docker-compose build" 는 사실 아님. ComposeFixture 가 `--build` 호출하지만 build context 의 `apps/<service>/build/libs/` 디렉토리는 사전에 생성되어 있어야 한다. 다른 3 nightly job (wms/fan/scm) 은 모두 boot jars build step 보유.

fix: gap-e2e-full job 에 boot jars build step 추가 (docker-compose.e2e.yml 의 5 build service: auth / account / security / admin / gateway).

provenance: TASK-MONO-080 의 partial close 후 cycle 2 잔존 fix. ADR-MONO-011 § D4 "triage within 1 business day" 정책 두 번째 발동.

---

# Scope

## In Scope

### A. `nightly-e2e.yml` 의 `gap-e2e-full` job 에 boot jars build step 추가

step 위치: "Set up Gradle" 다음, "Verify Docker" 이전.

```yaml
- name: Build gap boot jars
  run: >-
    ./gradlew
    :projects:global-account-platform:apps:auth-service:bootJar
    :projects:global-account-platform:apps:account-service:bootJar
    :projects:global-account-platform:apps:security-service:bootJar
    :projects:global-account-platform:apps:admin-service:bootJar
    :projects:global-account-platform:apps:gateway-service:bootJar
    --no-daemon --stacktrace
```

5 service = `docker-compose.e2e.yml` 의 `build:` directive service. community/membership 는 e2e 환경에서 boot 안 함, 본 step 에서 제외.

### B. step 사이 inline comment

TASK-MONO-081 + ADR-MONO-011 § D5 audit 명시.

### C. ADR-MONO-011 § D5 audit-trail 정정 (옵션 C-1)

ADR 본문 직접 수정 안 함. 본 task body 의 § Implementation Notes + INDEX outcome 에 audit-trail.

### D. 재검증

본 PR 머지 후 push-to-main trigger → 다음 nightly run 의 gap-e2e-full GREEN within 60min 예상 (boot jars ~30-60s + docker-compose build ~3-5min + 3 full class ~2-3min ≈ 6-9min, 60min budget 의 10-15%).

## Out of Scope

- cross-workflow artifact reuse (ADR § D3 in-job build 결정 유지).
- ADR-MONO-011 본문 직접 수정 (옵션 C-2 별도 follow-up).
- service production code 변경.
- docker-compose.e2e.yml 의 build service 추가.
- TASK-MONO-080 의 fix 1/2/3 변경.

---

# Acceptance Criteria

### Impl PR

- [ ] `.github/workflows/nightly-e2e.yml` 의 `gap-e2e-full` job 에 `Build gap boot jars` step 추가 (5 service `:bootJar`).
- [ ] step inline comment 에 TASK-MONO-081 + ADR-MONO-011 § D5 audit 명시.
- [ ] 본 PR 머지 후 다음 nightly run 의 gap-e2e-full GREEN within 60min budget.
- [ ] 다른 3 backend full job (wms/fan/scm) + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 영향 0 (동일 GREEN 유지).
- [ ] Production code 0.
- [ ] task lifecycle ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append 1-line outcome (impl PR # + commit + 다음 nightly run wall-clock + GREEN 검증).

---

# Related Specs

- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` § D5 (정정 audit 대상).
- `tasks/done/TASK-MONO-080-gap-e2e-nightly-fix.md` (직접 선행, partial close).
- `tasks/done/TASK-MONO-079-nightly-full-e2e-impl.md` (Phase 3 impl 첫 PR).
- `.github/workflows/nightly-e2e.yml` § `gap-e2e-full`.
- `projects/global-account-platform/docker-compose.e2e.yml` (5 build service).
- `projects/global-account-platform/apps/<service>/Dockerfile` (COPY 패턴).
- ci.yml wms/fan/scm e2e-full job boot jars step (패턴 reference).

# Related Skills

N/A — workflow yaml edit.

---

# Related Contracts

None.

---

# Target Service

`.github/workflows/nightly-e2e.yml` § `gap-e2e-full` only.

---

# Architecture

Phase 3 의 두 번째 follow-up fix.

---

# Implementation Notes

## docker-compose.e2e.yml 의 5 build service

`grep "^  [a-z][a-z0-9-]*-service:" projects/global-account-platform/docker-compose.e2e.yml`:

- auth-service        (line 154)
- account-service     (line 171)
- security-service    (line 197)
- admin-service       (line 218)
- gateway-service     (line 274)

모두 `build:` directive 보유 (context: `.`, dockerfile: `apps/<service>/Dockerfile`).

## ADR-MONO-011 § D5 audit-trail (옵션 C-1)

D5 본문 발췌: "gap-e2e-full omits boot-jars + Docker CLI image build steps — its ComposeFixture handles docker-compose build in @BeforeAll."

사실: ComposeFixture 가 `docker compose up -d --build` 호출 (ComposeFixture.java:84-86). `--build` flag 가 service Dockerfile 의 build context (`apps/<service>/build/libs/`) 의 boot jars 사전 build 의존. ADR 작성 시점 (PR #434) 의 ComposeFixture 동작 audit 누락.

옵션 C-1 채택. ADR-MONO-010 D5 step 4 정정 (TASK-080) 와 동일 패턴.

---

# Edge Cases

- 5 service 외 새 build service 추가: docker-compose 와 step list 동기 필요.
- boot jars build timeout: 5 service `:bootJar` cold ~1-2min, warm ~30-60s. budget 충분.
- community/membership 의 e2e 향후 추가: 별도 task.
- cycle 3 발생: TASK-MONO-082 author.

---

# Failure Scenarios

- cycle 3 fail: 새 root cause → TASK-MONO-082.
- budget 초과: timeout 상향 또는 ADR § 3.7 재평가.
- 다른 backend job regression: self-CI 회귀 가드.

---

# Test Requirements

- gap-e2e-full GREEN within 60min budget.
- 3 full class PASS.
- 다른 3 backend job 영향 0.
- Production code 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료, 재검증 GREEN.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- TASK-MONO-080 (PR #437) partial close 후 cycle 2 fix.
- 두 번째 nightly run `25773183881` (push `e8e53eb6`, 2026-05-13 01:48 UTC) 의 gap-e2e-full 54s fail 진단.
- ADR-MONO-011 § D4 "triage" 정책 두 번째 발동.
- ADR-MONO-011 § D5 결정 오류 (gap-e2e-full omits boot-jars step) 정정.
- 직접 선행 = TASK-MONO-080 (PR #437) + TASK-MONO-079 (PR #435).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical yaml edit, wms/fan/scm 패턴 검증된 기반).
