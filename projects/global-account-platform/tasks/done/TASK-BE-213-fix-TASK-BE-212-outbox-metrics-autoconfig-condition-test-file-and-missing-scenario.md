---
id: TASK-BE-213
title: "TASK-BE-212 fix — OutboxMetricsAutoConfigurationConditionTest 별도 파일 생성 및 @ConditionalOnClass 미싱 시나리오 테스트 추가"
type: fix
status: ready
service: libs/java-messaging
parent: TASK-BE-212
---

## Goal

TASK-BE-212 리뷰에서 발견된 두 가지 결함을 수정한다.

1. 태스크 Scope가 명시한 별도 파일 `OutboxMetricsAutoConfigurationConditionTest.java` 가 생성되지 않았다.
   `ApplicationContextRunner` 기반 조건부 테스트가 기존 `OutboxMetricsAutoConfigurationTest.java` 에 합쳐진 채로 남아 있어
   Edge Cases 항목 *"새 `OutboxMetricsAutoConfigurationConditionTest` 는 별도 파일로 추가"* 를 위반한다.

2. Acceptance Criteria 중 *"`MeterRegistry` 가 classpath에 없으면 auto-configuration이 건너뛰어진다 (`@ConditionalOnClass` 실패 시나리오)"* 에 해당하는 테스트가 누락되어 있다.

## Scope

### 추가
- `libs/java-messaging/src/test/java/com/example/messaging/outbox/OutboxMetricsAutoConfigurationConditionTest.java`
  - `ApplicationContextRunner` 기반 조건부 동작 테스트를 해당 파일에 이관/추가
  - 아래 세 시나리오를 모두 커버:
    1. 사용자가 자체 `OutboxFailureHandler` Bean을 등록 → auto-configuration back-off (bean 미등록)
    2. 자체 핸들러 없음 → default 핸들러 bean 등록
    3. `MeterRegistry` 가 classpath에 없음 → `@ConditionalOnClass` 실패, auto-configuration 전체 스킵

### 수정
- `libs/java-messaging/src/test/java/com/example/messaging/outbox/OutboxMetricsAutoConfigurationTest.java`
  - `ApplicationContextRunner` 기반 조건부 테스트 메서드(`userDefinedHandler_autoConfigurationBacksOff`, `noUserHandler_defaultHandlerRegistered`) 를 새 파일로 이동 후 삭제
  - `@DisplayName` 을 *"OutboxMetricsAutoConfiguration 단위 테스트"* 로 유지 (직접 호출 단위 테스트만 남음)
  - `UserHandlerConfig` inner class 가 새 파일에만 필요한 경우 이동

## Acceptance Criteria

- [ ] `OutboxMetricsAutoConfigurationConditionTest.java` 파일이 별도로 존재한다
- [ ] `OutboxMetricsAutoConfigurationConditionTest` 에서 사용자 정의 핸들러 back-off 시나리오 테스트 통과
- [ ] `OutboxMetricsAutoConfigurationConditionTest` 에서 default 핸들러 등록 시나리오 테스트 통과
- [ ] `OutboxMetricsAutoConfigurationConditionTest` 에서 `MeterRegistry` classpath 부재 시 auto-configuration 스킵 시나리오 테스트 통과
- [ ] `OutboxMetricsAutoConfigurationTest` 에 `ApplicationContextRunner` 기반 조건부 테스트가 남아 있지 않다
- [ ] `./gradlew :libs:java-messaging:test` 통과

## Related Specs

- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`

## Related Contracts

- 없음

## Edge Cases

- `@ConditionalOnClass` 실패 시나리오는 `FilteredClassLoader` 를 사용하여 `MeterRegistry` 를 classpath에서 제외한 후
  `ApplicationContextRunner` 로 컨텍스트를 구동해 `OutboxFailureHandler` bean 이 등록되지 않음을 검증한다.
- 기존 `OutboxMetricsAutoConfigurationTest` 의 직접 호출 단위 테스트(카운터 증가, metric prefix 도출 등)는 영향 없이 유지된다.

## Failure Scenarios

- `FilteredClassLoader` 를 잘못 설정하면 다른 클래스도 필터링될 수 있음 → `MeterRegistry.class` 만 명시적으로 제외하도록 한정
- `ApplicationContextRunner` 에서 `withClassLoader` 지원 여부 확인 → Spring Boot 2.3+ 에서 지원 (`FilteredClassLoader` 는 `spring-boot-test` 에 포함)
