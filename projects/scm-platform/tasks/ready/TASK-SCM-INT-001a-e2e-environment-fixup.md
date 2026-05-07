# Task ID

TASK-SCM-INT-001a

# Title

scm-platform e2e 환경 fixup — inventory-visibility-service 컨테이너 부팅 fail 진단·해소 + 로그 가시성 확보

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-SCM-INT-001](TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) 의 PR [#260](https://github.com/kanggle/monorepo-lab/pull/260) (현재 draft) 가 첫 e2e CI run 부터 6 scenario 모두 `initializationError` 로 fail. 1차 fix (PG admin DB, commit `8cabd016`) 후에도 잔존 fail. 본 task 에서 잔존 환경 fail 을 진단·해소하고 PR #260 을 ready-for-review 상태로 복귀.

본 task 의 핵심은 **PR #260 자체에 commit 추가** (force-rebase 하지 않음, history 보존). 머지 후 PR #260 이 자동으로 새 CI run 트리거 → green → ready 로 전환.

monorepo Phase 5 trigger 의 마지막 outstanding (INT-001) 해소가 본 task 머지에 직결.

---

# Scope

## In Scope

### 1. 로그 가시성 확보 (선제 조건)

`projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/testsupport/ScmPlatformE2ETestBase.java` 의 service container 들에 **`Slf4jLogConsumer`** attach. 현재 미부착 상태 — 부팅 fail 시 Spring Boot 로그가 JUnit XML `<system-out>` 에 안 찍혀 root cause 가시성 0. fan-platform e2e (`projects/fan-platform/tests/e2e`) 의 `withLogConsumer(new Slf4jLogConsumer(...))` 답습.

이거 먼저 적용하고 한 cycle 돌려야 진짜 root cause 가 노출됨. 그 다음 step 들은 그 로그를 보고 결정.

### 2. inventory-visibility-service 부팅 fail 진단·fix

증상: `Container exited with code 1`, `/actuator/health` 200 미반환 (180 s timeout). procurement-service 는 11.96 s 만에 boot success — 동일 base class · 동일 OIDC env. 차이점이 root cause.

#### 의심 가설 (우선순위 순)

1. **누락 env**:
   - `STALENESS_THRESHOLD_SECONDS` (docker-compose 에서 default `600`, e2e 미설정)
   - `WMS_KAFKA_BOOTSTRAP` (cross-project event source, dev 에선 같은 kafka, e2e 미설정 — `KAFKA_BOOTSTRAP` 으로 fallback 되는지 application.yml + KafkaConsumerConfig 확인)
   - inventory-visibility-service 만의 다른 env (예: ShedLock provider 설정, JdbcLockProvider 데이터소스)
2. **cross-project Kafka topic 미생성**:
   - `wms.inventory.{received,adjusted,transferred}.v1` 토픽 자동 생성 / eager creation 누락 시 consumer init 단계에서 부팅 hang. e2e fixture 가 base class `@BeforeAll` 에서 admin client 로 이 3 토픽을 미리 생성하는지 확인.
3. **ShedLock 분산 lock provider 초기화 실패**:
   - Postgres backed `JdbcTemplateLockProvider` 또는 Redis backed. e2e 환경에서 schema 미생성 (예: `shedlock` 테이블) 또는 Redis 접근 권한 이슈.
4. **`@Scheduled` staleness 배치 부팅 시 NPE**:
   - 5-min staleness 감지가 부팅 직후 즉시 trigger 되며 production code 의 dependency injection 실패. `application-e2e.yml` profile 로 `spring.task.scheduling.enabled=false` 또는 ShedLock disable 검토.
5. **Flyway migration fail**:
   - inventory-visibility-service V1 schema 가 `scm_inventory_visibility` DB 에 적용 시 typo / column type mismatch. `application.yml` 의 `spring.flyway.locations: classpath:db/migration/inventory-visibility` 가 boot jar 에 정상 패키지되는지 (build.gradle resources 처리).

#### 진단 procedure

a. 로그 가시성 fix 적용 → push → CI 1 cycle (~22 min).
b. `system-out` 에서 Spring Boot Banner 다음 줄들 분석. 다음 패턴 중 하나:
   - `APPLICATION FAILED TO START` + 명시적 cause → 직접 fix
   - Kafka consumer hang (`Subscribed to topic(s): ...` 에서 멈춤) → 토픽 미생성. base class 에 admin client 로 3 토픽 생성 추가
   - ShedLock `Cannot acquire lock` → provider 설정 fix
   - Flyway error → migration / build.gradle resources fix
c. fix 적용 → CI 1-2 cycle 더.

### 3. PR #260 description 갱신

머지 시 PR description 의 "Status: DRAFT — handed off to TASK-SCM-INT-001a" 헤더 제거 + 최종 6/6 PASS 결과 표 추가. CI run id 갱신.

### 4. (선택) 회귀 가드

부팅 fail 가 production config 결함이면 inventory-visibility-service IT (TASK-SCM-INT-001 외 영역) 추가 — 본 task scope 가 아니므로 별 task 발행 권장.

## Out of Scope

- 6 scenario 자체의 assertion 변경 — 부팅 환경만 fix.
- TASK-SCM-INT-001 의 spec deviation 재논의 (CB cooldown / cross-tenant 401 / supplier JDBC seeding) — 원본 PR description 에 javadoc 명시, 합의 완료로 본다.
- nightly e2e job 추가 — 현 PR-time job 으로 충분 (TASK-SCM-INT-001 결정).
- production code 의 새 기능 도입 — fix 만 허용. fix 가 production code 라면 별 commit + PR description 명시.

---

# Acceptance Criteria

1. PR #260 self-CI 의 `E2E (scm-platform v1 cross-service, Testcontainers)` job **PASS** (6/6 scenarios).
2. 다른 14 jobs 회귀 0 (master / GAP / fan e2e / Frontend / boot jars × 4 / Build & Test / Integration × 3).
3. base class 에 `Slf4jLogConsumer` attach 완료 — 향후 e2e 환경 회귀 시 즉시 로그 보임.
4. 본 task 의 fix commit 들이 PR #260 head 에 추가됨 (force-rebase 금지).
5. PR #260 description 의 draft hand-off 헤더 제거, 최종 CI 결과 표 추가.
6. PR #260 ready-for-review 전환 (`gh pr ready 260`).

---

# Related Specs

- [TASK-SCM-INT-001](TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) — 본 task 의 직접 선행 (impl PR #260 head 에 commit 추가)
- [TASK-SCM-BE-003](../done/TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md) — inventory-visibility-service production
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` — Service Type / cross-project event consumption / batch-heavy trait
- `projects/scm-platform/specs/services/inventory-visibility-service/staleness-monitoring.md` — `@Scheduled` + ShedLock 동작
- `projects/fan-platform/tests/e2e/src/test/java/.../testsupport/` — `Slf4jLogConsumer` attach 답습 reference

---

# Target Service / Component

- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/testsupport/ScmPlatformE2ETestBase.java` (1순위)
- `projects/scm-platform/apps/inventory-visibility-service/src/main/resources/application.yml` 또는 `application-e2e.yml` 추가 (필요 시)
- `projects/scm-platform/apps/inventory-visibility-service/src/main/java/.../config/` (production config fix 가 필요한 경우, 별 commit)

---

# Implementation Notes

- **답습 reference**: fan-platform e2e (`projects/fan-platform/tests/e2e/src/test/java/.../testsupport/`) — `withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("e2e.<alias>")))` 패턴 그대로.
- **CI run id 참조**: PR #260 의 첫 fail run `25479576813` (PG fail) 과 두 번째 run `25480163745` (inventory-visibility fail). artifact `scm-platform-e2e-test-reports` (id `6848863134`) 가 두 번째 run 의 XML / HTML 보고서.
- **본 task 시작 시점에 PR #260 의 head SHA**: `8cabd016` (PG fix 후). 그 위에 commit 누적.
- **production code 변경 정책**: 002b/002d 동일 — small fix 만 별 commit + PR description 정당화.
- **timeout 늘리기 금지** — 180 s 타임아웃은 충분. 진짜 fail 은 부팅 자체가 안 되는 거라 timeout 늘려도 무의미.

---

# Edge Cases

1. **Slf4jLogConsumer attach 후에도 로그가 비어 있음**: container 가 stdout 에 아무것도 안 찍으면 (예: image entrypoint 가 stderr only). 그 경우 `withLogConsumer` 가 stderr 도 받는지 확인 + `@Container` annotation 의 `withLogConsumer` 에 두 stream 다 attach.
2. **첫 cycle 의 fail 이 Slf4jLogConsumer 자체 NPE**: jar 미exist 상태로 base class 생성 시 NPE. fan-platform 의 `Slf4jLogConsumer` 사용 시 `LoggerFactory.getLogger(...)` 가 항상 non-null 이라 안전, 그러나 attach timing 이 `start()` 후라 race 가능. 권장: `withLogConsumer(...)` 를 `start()` 직전 builder chain 에 포함.
3. **inventory-visibility 가 Kafka topic 부재로 hang**: spring-kafka 의 default `auto-create-topics` = false 라면 admin client 로 e2e fixture 에서 생성. base class 의 `kafka.start()` 직후 `KafkaTestProducer` 또는 admin client 로 3 토픽 생성 + ack 대기.
4. **ShedLock JdbcLockProvider 가 `shedlock` 테이블 미생성**: production Flyway migration 에 이 테이블이 있어야. 없으면 별 V 추가 — 그러나 이건 production code 변경, 별 commit + 정당화.

---

# Failure Scenarios

## A. 로그 가시성 fix 만으로도 우연히 통과

base class 변경이 timing 영향을 줘서 통과할 수도. 그래도 본 task 의 가치 = 로그 가시성 자체 (회귀 가드). 6/6 PASS 면 그대로 머지.

## B. inventory-visibility 가 production 결함이면

cycle 1-2 후에도 부팅 fail 이 production code 의 결함 (예: `@PostConstruct` 의 NPE) 으로 진단되면, fix 가 production code 변경. 별 commit + PR description 정당화. 회귀 가드를 IT 에 추가하는 별 task 발행 권장 (예: TASK-SCM-BE-003a).

## C. 6 cycle 후에도 통과 안 됨

매 cycle ~22 min — 6 cycle ≈ 2 시간. 그 시점이면 더 깊은 분석 필요. 본 task 종결 → 별 deeper-investigation task 발행 (TASK-MONO-046-8 패턴).

---

# Test Requirements

- 로그 가시성 fix 적용 후 1 cycle 이상 CI run.
- 최종 CI run 에서 `E2E (scm-platform v1 cross-service, Testcontainers)` 6/6 PASS.
- 다른 14 jobs 회귀 0.

---

# Definition of Done

- [ ] `Slf4jLogConsumer` attach 완료 (4 service container: procurement / inventory-visibility / gateway / 가능하면 redis/kafka 도)
- [ ] inventory-visibility 부팅 fail root cause 진단 완료 (PR description 에 1-2 줄 명시)
- [ ] fix 적용 + PR #260 self-CI 6/6 PASS
- [ ] 회귀 0 검증 (다른 14 jobs)
- [ ] PR #260 description draft hand-off 헤더 제거 + 최종 결과 표
- [ ] `gh pr ready 260` ready-for-review 전환
- [ ] Ready for review (본 task 자체)

---

# Notes

- **Recommended impl model**: **Opus** — 부팅 fail 진단은 production config / Spring Boot bean lifecycle / Testcontainers timing 의 깊은 cross-cut 분석 필요.
- **분량 추정**: small-to-medium — 1차 fix 는 base class 에 1-2 줄, 그러나 진단 cycle 이 시간 비용. 최악 6 cycle (≈ 2 h CI 만).
- **dependency**:
  - `선행`: TASK-SCM-INT-001 (impl PR #260 draft 상태).
  - `후속`: 본 task 머지 → PR #260 ready → 머지 → review→done chore PR (TASK-SCM-INT-001 + 본 TASK-SCM-INT-001a 묶음).
- **monorepo Phase 5 trigger**: 본 task + INT-001 머지 시 verify-template-readiness exit 0 후보.
