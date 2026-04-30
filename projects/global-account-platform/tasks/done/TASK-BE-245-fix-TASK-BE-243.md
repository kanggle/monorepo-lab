# TASK-BE-245: fix — TASK-BE-243 spec 정합 + idempotency guard 추가

## Goal

TASK-BE-243 리뷰에서 발견된 2개 이슈를 해소.

### 이슈 1 — 플랫폼 spec 충돌

`platform/testing-strategy.md` 211–248줄("Scheduler Thread Lifecycle (Test Context Rotation)")이 `OutboxPollingScheduler`를 **이름으로 명시**하며 `@PostConstruct` / `scheduleWithFixedDelay` / `@PreDestroy` / `ScheduledFuture.cancel()` 조합을 **canonical 패턴**으로 지정한다. CLAUDE.md "Source of Truth Priority"상 `platform/`(우선순위 5)이 `tasks/`(10)보다 위이므로, BE-243의 `@PostConstruct → @EventListener` 변경은 spec을 선행 갱신하지 않은 채 적용된 결함이다.

해소 방향: spec을 갱신해서 새 canonical 패턴을 반영. 기존 spec의 핵심 의도(context-scoped TaskScheduler + @PreDestroy + ScheduledFuture.cancel)는 유지하되, **start trigger만** `@PostConstruct` → `@EventListener(ApplicationReadyEvent.class)`로 교체. spec에 변경 이유(transactionManager race during cold-start)를 명시.

### 이슈 2 — Double-start 취약점

`OutboxPollingScheduler.start()`에 idempotency guard가 없다. `ApplicationReadyEvent`가 두 번 발행되는 경우(예: 부모/자식 컨텍스트 계층) `ScheduledFuture` 두 개가 생성되고 **두 번째만 추적**되어 첫 번째는 orphan으로 남는다. `stop()` 호출 시 첫 번째 future는 cancel되지 않는다.

해소 방향: `AtomicBoolean started`로 `compareAndSet(false, true)` 가드 추가. 이미 시작된 경우 즉시 반환.

## Scope

**In:**
- `platform/testing-strategy.md` 211–248줄 갱신:
  - "Drive the poll loop programmatically via `@PostConstruct`" → `@EventListener(ApplicationReadyEvent.class)`로 교체
  - 변경 이유(`transactionManager race` during simultaneous cold-start) 추가 설명
  - 컨텍스트 ready 이전에 polling 시작 금지 원칙 명시
- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxPollingScheduler.java`:
  - `private final AtomicBoolean started = new AtomicBoolean(false);` 필드 추가
  - `start()` 진입 시 `if (!started.compareAndSet(false, true)) { return; }` 가드
  - `stop()`은 그대로 (running flag로 idempotent하게 동작)
- 테스트 추가:
  - `start_calledTwice_secondCallIsNoop`: 두 번 호출 시 두 번째는 무시되어 단 1개의 ScheduledFuture만 생성됨을 검증
  - 기존 regression 테스트(`construction_withoutApplicationReadyEvent_doesNotStartPolling`) 유지

**Out:**
- 다른 서비스 코드 변경 없음
- outbox 비즈니스 로직(폴링 인터벌, 배치 사이즈 등) 변경 없음
- shutdown 로직 변경 없음

## Acceptance Criteria

- [ ] `platform/testing-strategy.md`이 새 canonical 패턴(`@EventListener(ApplicationReadyEvent.class)` + idempotency guard) 명시. spec과 구현이 정합
- [ ] `start()`가 두 번 호출되어도 `ScheduledFuture`는 1개만 생성됨 (단위 테스트로 검증)
- [ ] `stop()` 호출 후 `start()` 재호출 시 동작 정의 — 현재 정책: 재시작 불가(`started`는 한 번 true가 되면 false로 돌아오지 않음). spec과 코드 주석에 명시
- [ ] `./gradlew :libs:java-messaging:test` 통과
- [ ] 4개 서비스(auth/account/security/admin) 단위 테스트 회귀 없음
- [ ] BE-243 본 fix(`@EventListener` 전환) 그대로 유지

## Related Specs

- `platform/testing-strategy.md` (개정 대상)
- `platform/event-driven-policy.md`

## Related Contracts

- 없음

## Edge Cases

- `stop() → start()` 재호출 케이스: 새 정책상 불가. 기존에도 outbox는 한 번 시작 후 종료까지 계속 실행되는 패턴이라 호환 영향 없음
- `ApplicationReadyEvent`가 어떤 환경에서도 두 번 발행되지 않는다면 idempotency guard는 dead code? — 부모/자식 컨텍스트 가능성 + 향후 리팩터 안전망으로 보존하는 것이 적절. cost 무시 가능
- 테스트에서 `start()`를 직접 호출하는 기존 테스트: idempotency 동작에 영향 없음 (첫 호출만 작동)

## Failure Scenarios

- spec 갱신은 했으나 다른 spec 파일이 같은 canonical 패턴을 inline으로 인용하는 경우: grep으로 `@PostConstruct` + `scheduleWithFixedDelay` 인용 위치 확인 후 모두 일관 갱신
- idempotency guard 추가 후 기존 단위 테스트가 부수효과 의존성으로 깨질 가능성: `start()`가 idempotent하다는 가정이 추가됐을 뿐이므로 기존 동작과 호환

## Implementation Notes

- BE-243 본 PR(머지된 master)에 추가 commit으로 적용 권장 (revert 없이 보강)
- 주의: `started` 필드는 `AtomicBoolean` 사용 (volatile만으로는 compareAndSet 불가). 임포트 추가 필요
- spec 갱신은 contract-first 정신을 따르므로 코드 변경보다 먼저 commit (또는 같은 PR에 함께 포함)

## Risk Assessment

**낮은 위험도** — spec 갱신은 텍스트 변경이고, idempotency guard 추가는 비즈니스 로직과 독립. 회귀 가능성 낮음.
