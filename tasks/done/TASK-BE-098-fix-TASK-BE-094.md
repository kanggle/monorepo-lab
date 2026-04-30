---
id: TASK-BE-098
title: "fix: TASK-BE-094 — display_name 마스킹 값 오류(NULL → '탈퇴한 사용자') + preferences NULL 처리 누락 + 테스트 컨테이너 전략 미준수"
status: ready
area: backend
service: account-service
parent: TASK-BE-094
---

## Goal

TASK-BE-094 리뷰에서 발견된 세 가지 문제를 수정한다:

1. `Profile.maskPii()`가 `display_name`을 `NULL`로 설정하지만 `retention.md §2.5`는 `'탈퇴한 사용자'` 고정 문자열을 요구한다 — 스펙 위반.
2. `Profile.maskPii()`가 `preferences` 필드를 NULL 처리하지 않는다 — `retention.md §2.5` 스펙 위반.
3. `AccountAnonymizationSchedulerIntegrationTest`의 `display_name` 검증(`isNull()`)이 위 오류를 승인하는 방향으로 작성되어 있어 함께 수정이 필요하다.
4. 두 통합 테스트가 `AbstractIntegrationTest`를 확장하지 않고 독립 `@Container`를 선언하며 `withStartupTimeout()`을 누락하였다 — `platform/testing-strategy.md` 위반.
5. `application-test.yml`에 Hikari validation-timeout/keepalive 설정이 없다 — `platform/testing-strategy.md` "MySQL Hikari Validation" 위반.

## Scope

### In

1. **`Profile.maskPii()` 수정 (`apps/account-service/src/main/java/com/example/account/domain/profile/Profile.java`)**
   - `this.displayName = null;` → `this.displayName = "탈퇴한 사용자";`
   - `this.preferences = null;` 추가 (현재 누락)

2. **`AccountAnonymizationSchedulerIntegrationTest` 수정**
   - 시나리오 1 검증: `assertThat(profile.getDisplayName()).isNull()` →
     `assertThat(profile.getDisplayName()).isEqualTo("탈퇴한 사용자")`
   - 시나리오 2 검증(29일, grace period 내): `assertThat(profile.getDisplayName()).isEqualTo("John Doe")` — 이미 올바름, 유지

3. **두 통합 테스트를 `AbstractIntegrationTest` 확장으로 전환**
   - `AccountDormantSchedulerIntegrationTest` + `AccountAnonymizationSchedulerIntegrationTest`
   - 인라인 `@Container static MySQLContainer<?> mysql` + 전용 `@DynamicPropertySource` 제거
   - `testImplementation testFixtures(project(':libs:java-test-support'))` 의존성을
     `apps/account-service/build.gradle`에 추가 (TASK-BE-082 패턴 따름)
   - `withStartupTimeout(Duration.ofMinutes(3))`은 `AbstractIntegrationTest`에 이미 선언되어 있으므로
     별도 중복 선언 불필요

4. **`src/test/resources/application-test.yml` 생성** (`apps/account-service/`)
   ```yaml
   spring:
     datasource:
       hikari:
         validation-timeout: 3000
         connection-test-query: SELECT 1
         max-lifetime: 60000
         keepalive-time: 30000
         leak-detection-threshold: 10000
     kafka:
       consumer:
         properties:
           reconnect.backoff.ms: 500
           reconnect.backoff.max.ms: 10000
           request.timeout.ms: 60000
       producer:
         properties:
           reconnect.backoff.ms: 500
           reconnect.backoff.max.ms: 10000
           request.timeout.ms: 60000
   ```

### Out

- `AccountDormantScheduler` 구현 로직 변경 없음
- `AccountAnonymizationScheduler` 구현 로직 변경 없음 (`AnonymizationTransaction` 내 `reasonCode` 하드코딩은 TASK-BE-097 범위)
- `ProfileRepository` 또는 `ProfileJpaEntity` 변경 없음 (도메인 레이어에서 충분히 처리)

## Acceptance Criteria

- [ ] `Profile.maskPii()` 실행 후 `displayName`이 `"탈퇴한 사용자"`이다
- [ ] `Profile.maskPii()` 실행 후 `preferences`가 `null`이다
- [ ] `AccountAnonymizationSchedulerIntegrationTest` 시나리오 1 어서션이 `"탈퇴한 사용자"`를 검증한다
- [ ] 두 통합 테스트가 `AbstractIntegrationTest`를 확장하며 독립 `@Container`를 선언하지 않는다
- [ ] `apps/account-service/src/test/resources/application-test.yml`에 Hikari + Kafka 재연결 설정이 포함된다
- [ ] `./gradlew :apps:account-service:test` 전체 통과 (Docker 없는 환경에서는 통합 테스트 SKIPPED 허용)

## Related Specs

- `specs/services/account-service/retention.md` §2.5 — 마스킹 대상 필드 값 정의
- `platform/testing-strategy.md` — Testcontainers 컨테이너 라이프사이클, Hikari Validation, Producer/Consumer Retry

## Related Contracts

- `specs/contracts/events/account-events.md` — (직접 영향 없음, 이벤트 payload 변경 아님)

## Edge Cases

- `AbstractIntegrationTest`는 Kafka 컨테이너도 함께 시작한다. 두 스케줄러 테스트는 `KafkaTemplate`을 `@MockitoBean`으로 대체하고 있어 Kafka 브로커 연결이 불필요하지만, 컨테이너 시작은 `AbstractIntegrationTest` static block에서 발생하므로 오버헤드가 있다. 이는 기존 `AccountSignupIntegrationTest` 패턴과 동일하므로 허용.
- `application-test.yml` 활성화 프로파일 지정: `@SpringBootTest`에 `@ActiveProfiles("test")` 추가가 필요할 수 있음. 기존 테스트 패턴 확인 후 적용.

## Failure Scenarios

- `AbstractIntegrationTest` 의존성(`testFixtures`) 선언 누락 시 컴파일 오류 발생. `build.gradle` 수정 후 확인 필수.
- MySQL `max-lifetime`(60000) ≤ `keepalive-time`(30000) 불일치 시 Hikari 시작 실패. 현재 값(60000 > 30000)은 정상.
