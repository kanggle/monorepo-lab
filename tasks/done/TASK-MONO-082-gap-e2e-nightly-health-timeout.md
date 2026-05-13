# Task ID

TASK-MONO-082

# Title

`gap-e2e-full` nightly job 의 ComposeFixture HTTP health-probe timeout 진단 + fix (TASK-MONO-081 cycle 3 발견)

# Status

done

# Owner

monorepo

# Task Tags

- ci
- e2e
- gap
- fix
- impl
- triage

---

# Goal

TASK-MONO-081 (PR #439, 2026-05-13 cycle 2 fix) 머지 후 push-to-main trigger 자동 발동된 세 번째 nightly run `25774889748` (push `849e9649`, 2026-05-13 02:40 UTC) 에서 **gap-e2e-full 16m 48s fail (cycle 3, ComposeFixture HTTP health-probe timeout)**. cycle 2 의 boot-jars / docker-compose build context 는 작동 검증 완료 (5 service `Started` 까지 진행). 새 fail mode:

```
3 tests completed, 3 failed
CrossServiceBulkLockE2ETest > initializationError FAILED
    java.lang.IllegalStateException: Services did not become healthy within PT5M
        at com.example.e2e.ComposeFixture.waitForHealthy(ComposeFixture.java:117)
DlqHandlingE2ETest > initializationError FAILED
    (동일 5min timeout)
RefreshReuseDetectionE2ETest > initializationError FAILED
    (동일 5min timeout)
```

Timeline (test 1, CrossServiceBulkLock 기준):
- `02:42:20` mysql / redis / kafka **Created** + **Started**
- `02:42:26~39` mysql / redis / kafka **Healthy** (docker healthcheck, ~15s)
- `02:42:36~40` auth / account / admin / security / gateway **Started**
- `02:47:40` (정확히 5min 후) → `IllegalStateException: Services did not become healthy within PT5M`
- 5 GAP service `Healthy` 이벤트 부재 (docker-compose.e2e.yml 의 5 service block 에 `healthcheck:` 정의 자체가 없음)

각 test class 가 새로 ComposeFixture 호출 → 3 × 5min waste = 16m 48s 총 wall-clock.

## ComposeFixture 의 health 판단 메커니즘

`ComposeFixture.java:44-49` (`HEALTH_URLS`) + `ComposeFixture.java:127-139` (`isHealthy`):

```java
private static final String[] HEALTH_URLS = {
    "http://" + HOST + ":" + AUTH_PORT + "/actuator/health",      // 18081
    "http://" + HOST + ":" + ACCOUNT_PORT + "/actuator/health",   // 18082
    "http://" + HOST + ":" + SECURITY_PORT + "/actuator/health",  // 18084
    "http://" + HOST + ":" + ADMIN_PORT + "/actuator/health"      // 18085
};
private static final Duration HEALTH_TIMEOUT = Duration.ofMinutes(5);
```

= 4 GAP service (auth/account/security/admin) 의 host-port `/actuator/health` HTTP GET → 200 받으면 healthy. gateway 는 probe 대상 아님.

→ **docker healthcheck 부재는 ComposeFixture 의 health 판단과 무관** (가설 A 기각). 진정한 root cause = 4 service 의 `/actuator/health` 가 CI 환경에서 5min 안에 200 못 반환.

## 메타 발견 — gap e2e suite CI 검증 부재 history

TASK-MONO-080 의 cycle 1 진단에서 밝혀짐: **gap tests/e2e module 이 settings.gradle 등록 자체가 historical 누락** + **ci.yml 의 `Build and check GAP backend` step 도 `:tests:e2e:check` 호출 안 했음**. 즉 본 ComposeFixture self-managed mode 가 PR-time CI 에서 한 번도 호출된 적 없음. local 에서도 dev 가 직접 e2e suite 실행 안 한 한 검증 부재. **본 cycle 3 fail = CI 첫 정직한 호출에서 표면화한 history 영역**.

## fix

cycle 3 root cause 후보 (가설 B/C/D, ready 시점 우선순위):

- **가설 B (우선순위 1, 가장 가능성)**: CI runner cold-start 가 5 service 동시 booting (Spring AS + JPA + Liquibase migration + Kafka client + Redis pool) 5min 초과. `HEALTH_TIMEOUT` 상향 (5min → 10min) + service container `start_period` 명시.
- **가설 C (우선순위 2)**: service 자체 boot fail (port mapping / DB conn / Kafka conn / config 누락 등 — container 가 exit 또는 stuck). 진단 필요: `docker compose logs <service>` 출력 + 실패 시 자동 capture.
- **가설 D (우선순위 3)**: docker-compose.e2e.yml 의 service port mapping (18081/18082/18084/18085) 이 actual container port 와 일치 안 함 — 호스트 port 는 open 되었으나 응답 없음.

본 task scope = **진단 + 확정 root cause 별 minimal fix**. 가설별 진단 plan 은 § Implementation Notes.

provenance: TASK-MONO-081 의 partial close 후 cycle 3 잔존 fix. ADR-MONO-011 § D4 "triage within 1 business day" 정책 세 번째 발동.

---

# Scope

## In Scope

### A. 진단 — 5 GAP service 의 CI cold-start 실측 wall-clock

본 task in-progress 첫 step. 가설 B/C/D 중 확정 root cause 식별 위해 fail 시 service log capture step 추가:

```yaml
- name: Dump gap service logs on failure
  if: failure()
  working-directory: projects/global-account-platform/tests/e2e
  run: |
    if [ -f /tmp/gap-e2e-compose.yml ]; then
      docker compose -f /tmp/gap-e2e-compose.yml -p gap-e2e logs --tail=200 \
        auth-service account-service security-service admin-service gateway-service \
        || true
    fi
```

또는 ComposeFixture 의 `stopQuietly` 가 down 전에 `docker compose logs` 자동 dump 호출 추가 (Java-side 변경 — Phase 0 진단용, 확정 후 제거).

### B. fix — 가설 별 minimal change

진단 결과에 따라 다음 중 하나 (또는 조합):

**가설 B 채택 시** (CI cold-start 5min 초과):
1. `ComposeFixture.HEALTH_TIMEOUT` Duration.ofMinutes(5) → Duration.ofMinutes(10).
2. (옵션) `docker-compose.e2e.yml` 의 5 service block 에 `healthcheck:` 정의 추가 — actual fix 아니지만 docker 측 visibility 개선.

**가설 C 채택 시** (service boot fail):
1. 정확한 fail reason 별 fix (configs / dependencies / port mapping / etc).

**가설 D 채택 시** (port mapping):
1. docker-compose.e2e.yml 의 5 service block 의 `ports:` 정정.

### C. ADR-MONO-011 § D5 audit-trail 누적

TASK-MONO-080 (option C-1) + TASK-MONO-081 (option C-1) 와 동일 패턴. ADR 본문 직접 수정 안 함. 본 task body + INDEX outcome 에 audit-trail. § D5 정정 = "ComposeFixture self-managed mode 가 CI 환경에서 한 번도 검증 안 됐던 history" 명시.

### D. 재검증

본 PR 머지 후 push-to-main trigger → 네 번째 nightly run 의 gap-e2e-full GREEN within 60min 예상. cycle 4 가능성도 인지 (TASK-083 author 패턴 답습).

## Out of Scope

- gap e2e suite 의 production code 변경 (5 service application config / port / endpoint 등).
- docker-compose.e2e.yml 의 build service 추가/삭제.
- TASK-MONO-081 (gap boot-jars step) 변경.
- Phase 3 의 다른 backend job (wms / fan / scm, 모두 SUCCESS 유지) 영향.
- frontend-e2e-fullstack / ecommerce-boot-jars-nightly 영향.

---

# Acceptance Criteria

### Impl PR

- [ ] Phase 0 진단 — 첫 commit 에 service-log capture step 추가, 한 번 push-to-main trigger 후 정확한 root cause 식별.
- [ ] Phase 1 fix — 식별된 root cause 별 minimal change.
- [ ] inline comment / commit message 에 TASK-MONO-082 + ADR-MONO-011 § D5 audit 명시.
- [ ] 본 PR 머지 후 다음 nightly run 의 gap-e2e-full GREEN within 60min budget.
- [ ] 다른 3 backend full job (wms/fan/scm) + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 영향 0 (동일 GREEN 유지).
- [ ] Production code 0 (가설 C 일 경우만 예외 — service application-level fix 필요 시 별도 task 분리 가능).
- [ ] task lifecycle ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append 1-line outcome (impl PR # + commit + 다음 nightly run wall-clock + GREEN 검증).

---

# Related Specs

- `docs/adr/ADR-MONO-011-nightly-full-e2e-cadence.md` § D5 (정정 audit 누적 대상).
- `tasks/done/TASK-MONO-081-gap-e2e-nightly-boot-jars.md` (직접 선행, partial close).
- `tasks/done/TASK-MONO-080-gap-e2e-nightly-fix.md` (cycle 1, partial close).
- `tasks/done/TASK-MONO-079-nightly-full-e2e-impl.md` (Phase 3 impl 첫 PR).
- `.github/workflows/nightly-e2e.yml` § `gap-e2e-full`.
- `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/ComposeFixture.java` (HEALTH_URLS + HEALTH_TIMEOUT + waitForHealthy).
- `projects/global-account-platform/docker-compose.e2e.yml` (5 service block — healthcheck 부재).
- 세 번째 nightly run `25774889748` (실측 fail 데이터 source).

# Related Skills

N/A — yaml + Java minimal edit + 진단.

---

# Related Contracts

None.

---

# Target Service

`.github/workflows/nightly-e2e.yml` § `gap-e2e-full` (log capture step) + `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/ComposeFixture.java` (HEALTH_TIMEOUT 상향 가능) + `projects/global-account-platform/docker-compose.e2e.yml` (healthcheck 추가 가능).

---

# Architecture

Phase 3 의 세 번째 follow-up fix.

---

# Implementation Notes

## 가설별 진단 plan (Phase 0)

### 가설 B (CI cold-start 5min 초과) 검증

- service-log capture step 으로 `auth-service` / `account-service` / `security-service` / `admin-service` / `gateway-service` 의 마지막 200 line 확인.
- "Started ... in N.NNN seconds" 라인 grep → 어떤 service 가 가장 느린지, N 이 5min 초과하는지.
- 만약 모든 5 service 가 "Started" 까지 완료했으나 actuator endpoint 가 응답 안 함 → 가설 D 의심.
- 만약 일부 service 가 "Started" 자체 없이 끝남 → 가설 C 의심.

### 가설 C (service boot fail) 검증

- 실패한 container 의 exit code + stderr 확인.
- 흔한 boot fail 원인:
  - DB connection fail (mysql healthcheck 통과했어도 service-specific schema 부재 등).
  - Kafka topic 생성 fail (kafka-init 도 5min 안에 끝나는지).
  - Spring AS issuer config / OIDC discovery URL 불일치.
  - Liquibase migration 실패.

### 가설 D (port mapping) 검증

- `docker compose -f docker-compose.e2e.yml port <service> 8080` 로 host port mapping 확인.
- ComposeFixture HEALTH_URLS 의 18081/18082/18084/18085 가 5 service의 actual host-mapped port 와 일치하는지 검증.
- 만약 docker-compose.e2e.yml 의 `ports:` 에서 명시되지 않은 service 가 있으면 (예: gateway), HEALTH_URLS 에서 제외 또는 mapping 추가.

## 메타 패턴 — ComposeFixture-style fixture 의 PR-time validation 누락

TASK-MONO-080 cycle 1 + 081 cycle 2 + 082 cycle 3 = **모두 ComposeFixture self-managed mode 가 CI 에서 첫 정직 호출 시 표면화한 historical 누락**. ADR-MONO-011 § D5 (gap "ComposeFixture handles compose build" 가정) + ADR-MONO-010 § D5 step 4 (gap-integration-tests CI job 의 e2e module 호출 가정) 두 ADR 의 audit 가정이 모두 사실 아님. 본 PR scope 는 cycle 3 fix 만, ADR 본문 직접 수정 안 함 (option C-1).

장기적으로는: gap-integration-tests 와 별개로 gap PR-time smoke job 신설 필요 (ADR-MONO-011 § 6.2 outstanding, ADR-MONO-010 § D5 step 5 의 ProductReady 후속). 본 task 범위 밖.

---

# Edge Cases

- gateway-service 가 ComposeFixture probe 대상에 없음 — 의도적 (gateway 는 4 backend service 의 응답을 의존하므로 4 service healthy 후 gateway 도 자동으로 ready 가 됨). 단, 이 가정이 본 cycle 3 fail 에서 무너지면 HEALTH_URLS 에 gateway 추가 가능.
- account-service 외 admin/security 도 OIDC discovery 의존 → auth-service 의 issuer URL config 미스로 cascade fail 가능.
- 5min HEALTH_TIMEOUT 도 부족하면 10min, 15min 등 단계적 상향. CI runner 의 일반적 Spring Boot cold-start 는 ~30-60s 라 5 service 동시 booting 도 5min 충분이어야 정상. timeout 상향만으로 해결 안 되면 가설 C 의 더 깊은 진단 필요.
- cycle 4 발생: TASK-MONO-083 author.

---

# Failure Scenarios

- cycle 4 fail: 새 root cause → TASK-MONO-083.
- 진단 step 도 fail (예: docker compose logs 실행 안 됨): fail-safe `|| true`.
- 가설 C 가 application-level fix 요구: 본 task scope 밖 → 별도 BE task 분리 (gap production code 영역).
- HEALTH_TIMEOUT 10min 상향도 부족: 가설 C 확정 의심.

---

# Test Requirements

- gap-e2e-full GREEN within 60min budget.
- 3 full class PASS.
- 다른 3 backend full job + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 영향 0.
- Production code 0 (가설 C 확정 시 예외).

---

# Definition of Done

### Impl PR

- [ ] AC 완료, 재검증 GREEN.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- TASK-MONO-081 (PR #439) partial close 후 cycle 3 fix.
- 세 번째 nightly run `25774889748` (push `849e9649`, 2026-05-13 02:40 UTC) 의 gap-e2e-full 16m 48s fail 진단.
- ADR-MONO-011 § D4 "triage within 1 business day" 정책 세 번째 발동.
- ADR-MONO-011 § D5 audit-trail 누적 (option C-1, TASK-080 + 081 패턴 답습).
- 직접 선행 = TASK-MONO-081 (PR #439) + TASK-MONO-080 (PR #437) + TASK-MONO-079 (PR #435).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Phase 0 진단 + 가설별 fix, mechanical 아닌 깊은 archaeological inspection 필요).
