---
id: TASK-BE-212
title: "TASK-BE-211 fix — OutboxMetricsAutoConfiguration 조건부 동작 테스트 추가 및 @ConditionalOnMissingBean 위치 수정"
type: fix
status: ready
service: libs/java-messaging
parent: TASK-BE-211
---

## Goal

TASK-BE-211 리뷰에서 발견된 두 가지 결함을 수정한다.

1. `@ConditionalOnMissingBean(OutboxFailureHandler.class)` 가 클래스 레벨에 선언되어 있어 Spring Boot 권장 패턴과 어긋난다.
   `@Bean` 메서드 레벨로 이동하여 클래스 파싱과 빈 등록 타이밍 문제를 방지한다.

2. Acceptance Criteria 중 "서비스가 자체 `OutboxFailureHandler` Bean을 선언하면 auto-configuration이 양보한다"에 대한
   `ApplicationContextRunner` 기반 테스트가 누락되어 있다.
   `@ConditionalOnMissingBean` 및 `@ConditionalOnClass` 조건부 동작을 검증하는 통합 수준 auto-configuration 테스트를 추가한다.

## Scope

### 수정
- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxMetricsAutoConfiguration.java`
  - 클래스 레벨의 `@ConditionalOnMissingBean(OutboxFailureHandler.class)` 제거
  - `@Bean` 메서드 `defaultOutboxFailureHandler` 에 `@ConditionalOnMissingBean(OutboxFailureHandler.class)` 이동

### 추가
- `libs/java-messaging/src/test/java/com/example/messaging/outbox/OutboxMetricsAutoConfigurationConditionTest.java`
  - `ApplicationContextRunner` 를 사용한 조건부 동작 테스트
  - 아래 Acceptance Criteria의 각 시나리오를 커버

## Acceptance Criteria

- [ ] `@ConditionalOnMissingBean(OutboxFailureHandler.class)` 가 `@Bean` 메서드 레벨에 위치한다
- [ ] `ApplicationContextRunner` 기반 테스트에서 서비스가 자체 `OutboxFailureHandler` Bean을 선언하면 auto-configuration bean이 등록되지 않는다 (back-off)
- [ ] `ApplicationContextRunner` 기반 테스트에서 서비스에 자체 `OutboxFailureHandler` Bean이 없으면 auto-configuration bean이 등록된다
- [ ] `ApplicationContextRunner` 기반 테스트에서 `MeterRegistry` 가 classpath에 없으면 auto-configuration이 건너뛰어진다 (`@ConditionalOnClass` 실패 시나리오)
- [ ] `./gradlew :libs:java-messaging:test` 통과

## Related Specs

- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`

## Related Contracts

- 없음

## Edge Cases

- `@ConditionalOnMissingBean` 을 `@Bean` 메서드 레벨로 이동해도 클래스 레벨 `@ConditionalOnClass(MeterRegistry.class)` 는 유지하므로 기존 동작에 영향 없음
- 기존 `OutboxMetricsAutoConfigurationTest` 단위 테스트는 그대로 유지 — 새 `OutboxMetricsAutoConfigurationConditionTest` 는 별도 파일로 추가

## Failure Scenarios

- `ApplicationContextRunner` 가 Spring Boot Autoconfigure 없이 동작하는 경우 → `spring-boot-starter-test` 에 이미 포함되어 있으므로 별도 의존성 추가 불필요
