# Task ID

TASK-BE-300

# Title

admin-service projection-consumer dispatch dedup — 4 `*ProjectionConsumer.onMessage` byte-identical 16-line body → `ProjectionConsumerSupport.dispatch` static utility (Cohort C2)

# Status

done

# Owner

backend

# Task Tags

- code
- event

---

# Goal

`/refactor-code wms admin-service` (2026-05-26 dry-run) 의 **Cohort C2 = L6 4-Kafka-consumer dispatch dedup** finding closure. behavior-neutral, medium risk (Kafka consumer IT 의존).

직전 BE-297 (Cohort C1, L5+L6 cascade dedup) + BE-299 (Cohort C3, L1+L2+L3+L5 mixed hygiene) 직속 후속. dry-run 8 finding 중 마지막 1 finding (F-L6-2) closure 로 **wms admin-service sweep 8/8 완전 TRUE 0 도달**.

dry-run 결과 핵심 finding:

- **F-L6-2**: `InventoryProjectionConsumer.onMessage` (L46-62) + `InboundProjectionConsumer.onMessage` (L45-61) + `MasterProjectionConsumer.onMessage` (L52-68) + `OutboundProjectionConsumer.onMessage` (L41-57) 4 consumer 모두 `onMessage(ConsumerRecord<String, String> record)` body 가 **byte-identical** 16-line block:
  ```
  String topic = record.topic();
  try {
      ProjectionEnvelope envelope = parser.parse(record.value(), topic);
      MDC.put("eventId", envelope.eventId().toString());
      MDC.put("sourceTopic", topic);
      try {
          projectionService.project(envelope);
      } finally {
          MDC.remove("eventId");
          MDC.remove("sourceTopic");
      }
  } catch (RuntimeException ex) {
      metrics.recordError(topic);
      throw ex;
  }
  ```
  차이점은 `projectionService` 의 타입만 (InventoryProjectionService / InboundProjectionService / MasterProjectionService / OutboundProjectionService). 16 × 4 = **64 LOC byte-identical duplication**.

본 task 는 `ProjectionConsumerSupport` static utility class 신설 (functional interface + method reference 패턴) → 4 `onMessage` body 가 1-line delegation 으로 단축. `@KafkaListener` annotation + topics + groupId 각 concrete consumer class 에 유지 (인터페이스 상속 아닌 functional dispatch 패턴).

## SKILL.md AbstractProjectionService precedent 회피

`.claude/skills/backend/refactoring/SKILL.md` 의 경고:
- **AbstractProjectionService 패턴 (abstract superclass + `@Transactional` method 상속)** 는 Spring AOP self-invocation IT failures 유발 — `@Transactional` 의 proxy 가 self-invocation 시 무시됨.

본 task 의 채택 패턴 = **static utility method + functional interface (method reference)**:
- `ProjectionConsumerSupport` = static utility class (Spring bean 아님)
- `dispatch(...)` static method 가 `Consumer<ProjectionEnvelope> projectionFn` 함수형 인자 받음
- 4 consumer 의 `onMessage` 가 `ProjectionConsumerSupport.dispatch(record, parser, metrics, projectionService::project)` 1-line 호출
- `projectionService.project(envelope)` 호출 site = method reference (`projectionService::project`) → utility 가 reference 를 invoke 시 정상 Spring proxy 통해 호출 (utility class 자체는 Spring bean 아니라 AOP 우회 없음, projection service 만 Spring bean = `@Transactional` proxy 정상 적용)
- 따라서 Spring AOP self-invocation 함정 회피

---

# Scope

## In Scope

| 대상 | 변경 |
|---|---|
| 신규 `apps/admin-service/src/main/java/com/wms/admin/infra/messaging/ProjectionConsumerSupport.java` | static utility class (private constructor). 단일 static method: `public static void dispatch(ConsumerRecord<String, String> record, ProjectionEnvelopeParser parser, ProjectionMetrics metrics, java.util.function.Consumer<ProjectionEnvelope> projectionFn)` — body 가 byte-identical 16-line block 의 정확한 transcribe. javadoc 에 "Spring AOP self-invocation 회피 의도, projection service 의 `@Transactional` proxy 정상 적용 보존" 명시. |
| `apps/admin-service/src/main/java/com/wms/admin/infra/messaging/InventoryProjectionConsumer.java` | `onMessage` body 16-line → 1-line `ProjectionConsumerSupport.dispatch(record, parser, metrics, projectionService::project)`. `MDC` + `topic` 변수 + `try/finally` + `catch (RuntimeException ex)` 직접 사용 = 0. import 정리: `org.slf4j.MDC` 제거 (utility 가 쓰임), `org.apache.kafka.clients.consumer.ConsumerRecord` 유지 (method param). |
| `apps/admin-service/src/main/java/com/wms/admin/infra/messaging/InboundProjectionConsumer.java` | 동일 패턴 (`projectionService::project`). |
| `apps/admin-service/src/main/java/com/wms/admin/infra/messaging/MasterProjectionConsumer.java` | 동일 패턴. |
| `apps/admin-service/src/main/java/com/wms/admin/infra/messaging/OutboundProjectionConsumer.java` | 동일 패턴. |

## Out of Scope

- BE-297 + BE-299 회귀 verify only (cascade-revoke dedup + ProjectionStatusService 패턴 + magic constant + onAdjusted split 모두 보존).
- `@KafkaListener` annotation 변경 — 4 consumer 각각 `@KafkaListener(topics = {...}, groupId = "...")` 그대로 유지 (인터페이스 상속 아닌 functional dispatch 패턴).
- `*ProjectionService` 인터페이스 도입 — 4 service 가 `project(ProjectionEnvelope)` 시그너처 동일하지만 inheritance 도입 = 별 design decision, refactor 영역 밖.
- API / event contract / schema 변경.
- 다른 service (master / inventory / outbound / inbound / notification / gateway / scm / gap 등) 변경.
- libs/ 변경 (`ProjectionConsumerSupport` 가 admin-service-local — 다른 service 도 적용 시 별 task).

---

# Acceptance Criteria

- [ ] (A1) 신규 `infra/messaging/ProjectionConsumerSupport.java` 신설; `public final class` + private constructor (utility class pattern). 단일 `public static void dispatch(...)` 메서드.
- [ ] (A2) `dispatch` 메서드 body 가 4 consumer 의 기존 `onMessage` body 16-line 의 정확한 transcribe (try-catch-finally + MDC put/remove + metrics.recordError + projectionFn.accept).
- [ ] (A3) 4 ProjectionConsumer (`InventoryProjectionConsumer` / `InboundProjectionConsumer` / `MasterProjectionConsumer` / `OutboundProjectionConsumer`) 의 `onMessage` body 가 각 1-line: `ProjectionConsumerSupport.dispatch(record, parser, metrics, projectionService::project);`. `MDC` / `try` / `finally` / `catch` 직접 사용 = 0 in 4 consumer.
- [ ] (A4) 4 consumer 의 `@KafkaListener` annotation (topics + groupId) byte-unchanged. `@Component` + `@Profile("!standalone")` + class javadoc 모두 보존.
- [ ] (A5) 4 consumer 의 `org.slf4j.MDC` import 제거 (utility 가 쓰임).
- [ ] (A6) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` BUILD SUCCESSFUL (baseline = main `a5d3a5d5`).
- [ ] (A7) CI authoritative verify — `Integration (master-service + notification-service, Testcontainers)` job GREEN (admin-service IT 가 wms IT 의 일부; real Kafka + Testcontainers verify).
- [ ] (A8) Kafka consumer behavior byte-identical:
  - 4 topic group (7 inventory / 3 inbound / 6 master / 2 outbound) 각 동일하게 dispatch
  - MDC `eventId` + `sourceTopic` put/remove invariant 보존
  - `RuntimeException` propagate 보존 (`metrics.recordError(topic); throw ex;`)
  - `ProjectionEnvelopeParser.parse` 호출 invariant 보존
  - `projectionService.project(envelope)` 의 `@Transactional` proxy 정상 적용 (Spring AOP self-invocation 함정 회피)
- [ ] (A9) BE-297 + BE-299 회귀 0 (cascade-revoke + ProjectionStatusService + magic constant + onAdjusted split 모두 보존).
- [ ] (A10) contract / event schema 변경 0, `admin.action.*` outbox event payload byte-identical.
- [ ] (A11) zero-retrofit invariant — `git diff --stat origin/main -- 'projects/{scm,global-account,erp,fan,ecommerce-microservices,finance,platform-console}-platform/' 'libs/'` = empty.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 (PROJECT.md = wms, domain=wms, traits=[transactional, integration-heavy]).

- `platform/refactoring-policy.md` — refactor 정의 (no behavior change).
- `platform/coding-rules.md` — duplication 제거 정책.
- `projects/wms-platform/specs/services/admin-service/architecture.md` § Package Structure (infra/messaging/) + § Event Consumption (eventId-based dedupe 패턴).
- `rules/traits/integration-heavy.md` — Kafka consumer idempotency + retry 패턴.
- `.claude/skills/backend/refactoring/SKILL.md` § AbstractProjectionService precedent (Spring AOP self-invocation IT failures 경고 — 본 task 의 static utility 패턴이 회피).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Method + Functional Interface + Static Utility patterns. § Baseline Check (compile + test GREEN before/after) + § Kafka Consumer IT 의 권위적 verify.

---

# Related Contracts

- `projects/wms-platform/specs/contracts/events/admin-events.md` § Consumed Events — 4 topic group (inventory 7 / inbound 3 / master 6 / outbound 2 = 18 topics).
- `admin.action.performed` outbox event payload (변경 0건, projection-side consumer 변경 무관).

---

# Target Service

- `admin-service` (단일 service scope)

---

# Architecture

admin-service = Layered (architecture.md override). 본 task 는 **infra layer 내부의 dedup** — `ProjectionConsumerSupport` 가 `infra/messaging/` 위치 (4 consumer 와 동일 package). inter-layer 변경 0.

Static utility class pattern:
- `ProjectionConsumerSupport` = Spring bean 아님 (단순 utility)
- `dispatch(...)` static method = pure function, side-effect = MDC put/remove + metrics.recordError + `projectionFn.accept` 호출
- `projectionFn` 인자 = `Consumer<ProjectionEnvelope>` — 4 consumer 가 `projectionService::project` method reference 전달
- Spring AOP proxy 가 `projectionService` 의 `@Transactional` 정상 처리 (method reference 호출도 proxy 경유)

이 패턴은 SKILL.md 의 AbstractProjectionService precedent (Spring AOP self-invocation 함정) 의 안전한 대안.

---

# Implementation Notes

1. **Pre-verify (BE-301 패턴 8회째)**: impl 단계에서 dispatcher main session 이 직접 (a) 4 consumer 의 onMessage 16-line body byte-identical verify / (b) `*ProjectionService` 4 service 의 `project(ProjectionEnvelope)` 시그너처 동일성 verify / (c) `ProjectionEnvelopeParser` + `ProjectionMetrics` 위치 grep 재실행.
2. **Utility class 위치**: `infra/messaging/ProjectionConsumerSupport.java` (4 consumer 와 동일 package, package-private 또는 public 선택. public 권장 — future 다른 admin-service consumer 의 잠재 사용).
3. **Functional interface 선택**: `java.util.function.Consumer<ProjectionEnvelope>` (java stdlib). 별도 `ProjectionEnvelopeHandler` interface 도입 불요 (over-engineering).
4. **Method reference syntax**: `projectionService::project` (`InventoryProjectionService.project` 등 4 service 의 `project(ProjectionEnvelope)` 메서드 reference).
5. **MDC pattern preservation**: utility 가 try-finally 안에서 MDC.put/remove 처리. finally block 의 MDC.remove 가 exception path 도 cover (기존 동작 동일).
6. **Exception path preservation**: outer `catch (RuntimeException ex)` 에서 `metrics.recordError(topic)` + `throw ex` 보존. utility 가 동일 exception 재throw.
7. **Branch**: `task/be-300-admin-projection-consumer-dispatch-dedup` (substring `master` 검증 ✓).
8. **Spec PR + impl PR + close-chore PR** 3 분리 (PR Separation Rule). impl PR 의 CI authoritative verify 필수 (Kafka IT 의존).
9. (분석=Opus 4.7 / 구현 권장=Opus 4.7 — medium risk Kafka consumer, AbstractProjectionService precedent 회피 패턴 신중 적용)

---

# Edge Cases

- `parser.parse` 가 `RuntimeException` throw 시 outer catch 가 `metrics.recordError` + 재throw → 기존 동작 동일.
- `projectionService.project` 가 `RuntimeException` throw 시 inner finally 가 MDC.remove → outer catch 가 `metrics.recordError` + 재throw → 기존 동작 동일.
- `projectionService.project` 가 checked exception throw — `Consumer<ProjectionEnvelope>.accept` 가 checked exception 허용 안 함. 실제 4 service 의 `project(ProjectionEnvelope)` 시그너처가 unchecked only (RuntimeException) 확인 필요. 만약 checked throw 가능하면 `BiConsumer` 또는 custom functional interface 필요.
- `@KafkaListener` annotation 의 method-level metadata (topics, groupId) → 4 consumer 의 method 에 그대로 유지. utility 호출과 무관.

---

# Failure Scenarios

- Spring AOP self-invocation 함정 재현 (SKILL.md 경고) — utility 패턴이 회피. 만약 발생 시 IT 가 `@Transactional` 부재 감지 (transaction rollback 안 됨, DB state 변경 persist). CI Kafka IT 가 권위적 catch.
- 4 consumer 의 import 정리 미흡 (`MDC` 사용 안 함에도 import 잔존) → compile warning. cleanup verify 의무.
- `projectionService::project` 의 method reference 가 ambiguous resolution → compile error. 4 service 의 `project(ProjectionEnvelope)` 메서드 단일 verify.
- utility class 가 Spring bean 으로 잘못 inject 시도 — `private constructor` + `final class` 가 instantiation 방지.

---

# Test Requirements

- baseline: main `a5d3a5d5` (post-BE-299 close-chore) `./gradlew :projects:wms-platform:apps:admin-service:check --rerun-tasks` GREEN.
- post-impl: 동일 명령어 GREEN.
- **CI authoritative verify 필수** — `Integration (master-service + notification-service, Testcontainers)` job 의 real Kafka + Testcontainers 검증 (admin-service consumer 가 같은 job 에서 실행). Spring AOP self-invocation 함정 검증.
- 추가 test 작성 불요 (extract method = caller signature 무관, 기존 ProjectionConsumer IT/unit test 가 cover).

---

# Definition of Done

- [ ] (A1-A11) 모두 PASS.
- [ ] Branch: `task/be-300-admin-projection-consumer-dispatch-dedup` (substring `master` 검증).
- [ ] PR: `refactor(wms-admin):` impl PR + close-chore PR (PR Separation Rule).
- [ ] Lifecycle: `ready/` → `review/` → `done/`.
- [ ] BE-303 3-dim verify ALL GREEN per stage. impl PR 의 CI = 19/20 GREEN authoritative (Kafka IT 포함) 필수.
- [ ] (분석=Opus 4.7 / 구현 권장=Opus 4.7)
