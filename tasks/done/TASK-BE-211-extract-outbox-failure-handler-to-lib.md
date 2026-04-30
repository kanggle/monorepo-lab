---
id: TASK-BE-211
title: "OutboxFailureHandlerConfig 4개 서비스 중복 제거 — libs/java-messaging 자동 설정 추출"
type: refactoring
status: ready
service: libs/java-messaging, auth-service, account-service, admin-service, membership-service
---

## Goal

4개 서비스(auth, account, admin, membership)에 거의 동일한 `OutboxFailureHandlerConfig`가 중복 정의되어 있다.
`libs/java-messaging`에 `OutboxMetricsAutoConfiguration`을 추가하여 `spring.application.name` 기반으로
자동으로 `{service}_outbox_publish_failures` 카운터를 등록하도록 하고, 서비스별 설정 클래스를 제거한다.

## Scope

### 추가
- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxMetricsAutoConfiguration.java`
  - `@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnMissingBean(OutboxFailureHandler.class)`
  - `spring.application.name`에서 metric prefix 도출 (`auth-service` → `auth`)
- `libs/java-messaging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 등록 추가
- `libs/java-messaging/build.gradle` — `compileOnly 'io.micrometer:micrometer-core'` 추가

### 제거
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/messaging/AuthOutboxFailureHandlerConfig.java`
- `apps/account-service/src/main/java/com/example/account/infrastructure/messaging/AccountOutboxFailureHandlerConfig.java`
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/messaging/AdminOutboxFailureHandlerConfig.java`
- `apps/membership-service/src/main/java/com/example/membership/infrastructure/messaging/MembershipOutboxFailureHandlerConfig.java`

## Acceptance Criteria

- [ ] `OutboxMetricsAutoConfiguration`이 `libs/java-messaging`에 존재하고 AutoConfiguration.imports에 등록된다
- [ ] 4개 서비스별 `XxxOutboxFailureHandlerConfig` 파일이 삭제된다
- [ ] metric 이름은 기존과 동일하게 유지된다 (`auth_outbox_publish_failures` 등)
- [ ] 서비스가 자체 `OutboxFailureHandler` Bean을 선언하면 auto-configuration이 양보한다 (`@ConditionalOnMissingBean`)
- [ ] `./gradlew :libs:java-messaging:test` 통과
- [ ] 4개 서비스 각각 `./gradlew :apps:{service}:test` 통과

## Related Specs

- `platform/shared-library-policy.md`

## Related Contracts

- 없음

## Edge Cases

- `spring.application.name`이 설정되지 않은 경우 기본값 `application`으로 폴백 → `application_outbox_publish_failures` 카운터 생성
- community-service, security-service처럼 기존에 `OutboxFailureHandler`가 없던 서비스도 auto-configuration이 동작하며 `community_outbox_publish_failures`, `security_outbox_publish_failures` 카운터가 새로 생긴다 (기존 없던 메트릭 추가 — 손상 없음)

## Failure Scenarios

- MeterRegistry가 없는 환경(`@ConditionalOnClass` 실패) → auto-configuration 건너뜀, OutboxFailureHandler 없는 기존 동작 유지
