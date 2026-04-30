# Task ID

TASK-BE-062

# Title

CI에서 @Disabled 처리된 통합 테스트 5건(클래스 기준) 원인 조사 및 복원

> **진행 현황 (2026-04-19)**: 초기 @Disabled 8건 중 3건을 CI 이터레이션으로
> 복원 완료. `LoginHistoryImmutabilityTest`(event_id VARCHAR(36) 길이 수정),
> `ActivateSubscriptionIntegrationTest` + `SubscriptionExpirySchedulerTest`
> (membership JpaConfig @EntityScan 확장 + accountId UUID로 축소 +
> subscription_status_history TRUNCATE). 남은 5건 중 **3건을 추가 복원**:
>
> - `AuthIntegrationTest.refreshTokenReuseDetected` — `RefreshTokenUseCase`
>   의 reuse 체크를 blacklist 보다 앞으로 이동 (security-first). 단위 테스트
>   `RefreshTokenUseCaseTest` 도 새 순서에 맞춰 조정. 느슨한 assertion
>   `TOKEN_REUSE_DETECTED | SESSION_REVOKED` 을 `TOKEN_REUSE_DETECTED` 로 정밀화.
> - `OAuthLoginIntegrationTest` — WireMock 을 `dynamicPort()` 로 전환하고
>   `DynamicPropertySource` 가 `wireMock.baseUrl()` 을 사용하도록 변경. 정적
>   18082 포트로 인한 AuthIntegrationTest 와의 JVM 공유 충돌 해소. **단,
>   #18 CI 실측 결과 MySQL testcontainer 가 테스트 중 연결 종료되는
>   별개 container 안정성 문제가 드러나 4건 happy path 가 여전히 503 반환.
>   dynamic port 개선은 유지한 상태로 `@Disabled` 재부착 — residual 로 이동.**
> - `AccountSignupIntegrationTest.signup_duplicateEmail_returns409` —
>   `@MockitoBean KafkaTemplate` + `AccountOutboxPollingScheduler` 로 전환해
>   signup 이 Kafka 연결을 전혀 시도하지 않게. 첫 signup 50s metadata 타임아웃 원인 제거.
>
> **남은 3건 (residual)** — TASK-BE-066 으로 승계 (2026-04-20):
>
> - `DlqRoutingIntegrationTest` — Kafka testcontainer 불안정 (PR #21 CI 실측에서
>   agent 의 `ErrorHandlingDeserializer` 주입 수정 후에도 `awaitility
>   ConditionTimeoutException` 재현).
> - `DetectionE2EIntegrationTest` — Kafka broker `Node 1 disconnected` + dynamic port
>   유동 (32781→32775→32789). agent 의 UUID 길이 수정 후에도 `AssertionFailedError` 재현.
> - `OAuthLoginIntegrationTest` — MySQL testcontainer early shutdown + `OAuthLoginUseCase#callback`
>   의 `@Transactional` 범위가 외부 HTTP 호출까지 감싸 Hikari connection pinning 유발.
>
> **종결 상태 (2026-04-20)**: 초기 8건 → 4건 CI green 복원 + 4건(재분류 포함) infra-task 승계.
> 복원된 4건 = `LoginHistoryImmutability`, `ActivateSubscription`, `SubscriptionExpiryScheduler`,
> `AuthIntegrationTest.refreshTokenReuseDetected`, `AccountSignupIntegrationTest.signup_duplicateEmail_returns409`
> (총 5건이지만 `AuthIntegrationTest` 는 클래스 기준 1건).
> 승계 3건 → `TASK-BE-066` (Testcontainers 안정성 + OAuthLoginUseCase 트랜잭션 재설계).

# Status

done

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-057
- TASK-BE-058

---

# Goal

CI 파이프라인 첫 실측(run 24619397242) 시 재현된 실패를 임시로 `@Disabled` 처리한 통합 테스트 2건의 근본 원인을 조사하고 복원한다. 로컬에서는 Docker Desktop 4.69의 Testcontainers 호환성 이슈로 인해 동일 경로를 재현할 수 없어, TASK-BE-058 가이드의 대체 Docker 환경(Rancher Desktop / WSL docker-ce)을 확보한 뒤 조사 필요.

---

# Scope

## In Scope

### A. `OAuthLoginIntegrationTest` 전체 클래스 (`apps/auth-service`)

증상:
- happy path callback 테스트 전부 `503 Service Unavailable` 반환 (Google/Microsoft)
- Kakao/Microsoft happy path에서 outbox row의 `loginMethod` 필드가 빈 문자열로 기록됨

조사 가설 (상세한 순서로):
1. `AuthIntegrationTest`와 WireMock 포트 `18082` 중복 — `@BeforeAll/@AfterAll` 수명 사이클이 클래스 간에 깨끗하게 분리되지 않음 → 포트 충돌 또는 stub 잔존
2. `AccountServiceClient`의 resilience4j CircuitBreaker 상태가 test context 간에 공유됨 — AuthIntegrationTest의 `accountServiceDown` 시나리오가 CB를 OPEN으로 트립, OAuthLoginIntegrationTest 실행 시 CB 잔존 상태로 상시 fallback → 503
3. outbox `loginMethod` 빈 값: `AuthEventPublisher.publishLoginSucceeded(..., loginMethod)` 7-arg 오버로드 호출은 맞는데, outbox payload 직렬화 단계에서 필드가 누락될 가능성 — JSON envelope 구성 점검

복원 절차:
- WireMock 포트 충돌 제거 → `wireMockConfig().dynamicPort()` 사용 + DynamicPropertySource에 실제 port 주입
- CB 격리 → `@BeforeEach`에서 `circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset)`
- outbox `loginMethod` 값 실측 검증 — 스펙(auth-events.md)과 일치

### C. `AccountSignupIntegrationTest.signup_duplicateEmail_returns409` (`apps/account-service`)

증상:
- 첫 `POST /api/accounts/signup` 요청이 **50.3초** 후 500 반환 (기대값 201)
- 동 클래스의 `signup_thenLock_historyRecorded`는 0.85초에 성공

조사 가설:
1. Kafka producer 경로에서 토픽 `account.created` 메타데이터 조회 타임아웃 (60s 기본) — 첫 signup 시 outbox relay가 아직 ready 안 된 토픽에 sync publish 시도
2. 테스트 순서 의존 — `signup_thenLock_historyRecorded`가 먼저 돌면 이미 토픽이 생성돼 있어 이 테스트는 빠름
3. `allow.auto.create.topics` 설정 누락 또는 metadata 전파 지연

복원 절차:
- KafkaContainer에 `withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")` 또는 @BeforeAll에서 토픽 사전 생성 스크립트
- 또는 signup use case가 outbox-only 경로를 사용하도록 확인(이미 그러면 500 원인 재진단)

### B. `AuthIntegrationTest.refreshTokenReuseDetected` (`apps/auth-service`)

증상:
- 재사용된 refresh token 요청에 401 반환까진 OK, 그러나 Redis `refresh:invalidate-all:{accountId}` 마커가 설정되지 않음
- 예외 경로가 `TokenReuseDetectedException`이 아니라 `SessionRevokedException`으로 빠짐
  - 첫 rotate 시 원본 token이 `revoked=true`가 되면서, 두 번째 replay 시 `isRevoked()` 체크가 `isReuse()` 체크보다 앞서 평가되어 다른 분기를 탐

조사 절차:
1. `RefreshTokenUseCase` (또는 해당 use case) 내 reuse 탐지 순서 추적
2. `isRevoked()` vs `rotatedFrom != null` 체크 순서 — 의도가 "이미 회전된 토큰(revoked) 이라도 rotation chain 내라면 재사용으로 판정"이 맞는지 정책 확인
3. 수정 대상 결정:
   - 구현 수정: reuse 체크를 우선해 `refresh:invalidate-all:{accountId}` 마커 보장 (보안 우선)
   - 스펙/테스트 수정: 의도적으로 SessionRevokedException만 받는 경로가 정답이면 테스트를 그에 맞게 재작성

## Out of Scope

- 새 OAuth provider 추가
- Refresh token 보안 모델 전면 재설계
- 다른 @Disabled 테스트 (없음)

---

# Acceptance Criteria

- [ ] `OAuthLoginIntegrationTest`의 `@Disabled` 제거 + CI에서 7 tests all passed
- [ ] `AuthIntegrationTest.refreshTokenReuseDetected`의 `@Disabled` 제거 + CI에서 passed (필요 시 코드 수정 먼저)
- [ ] `AccountSignupIntegrationTest.signup_duplicateEmail_returns409`의 `@Disabled` 제거 + CI에서 passed
- [x] `ActivateSubscriptionIntegrationTest`의 `@Disabled` 제거 ✅ (2026-04-19)
- [x] `SubscriptionExpirySchedulerTest`의 `@Disabled` 제거 ✅ (2026-04-19)
- [ ] `DetectionE2EIntegrationTest`의 `@Disabled` 제거 (AssertionError 근본 원인 조사)
- [ ] `DlqRoutingIntegrationTest`의 `@Disabled` 제거 (Kafka DLQ timeout x3)
- [x] `LoginHistoryImmutabilityTest`의 `@Disabled` 제거 ✅ (2026-04-19: event_id VARCHAR(36) 준수)
- [ ] AuthIntegrationTest.java의 loose assertion(`TOKEN_REUSE_DETECTED | SESSION_REVOKED`)을 단일 값으로 정밀화 (정책 결정 반영)
- [ ] `./gradlew :apps:auth-service:test` 로컬(Docker 동작 환경)에서 통과
- [ ] `./gradlew build` CI에서 backend job 전체 green

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/redis-keys.md`
- `specs/contracts/events/auth-events.md` (loginMethod enum)
- `specs/features/authentication.md` (refresh rotation 정책)

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (auth.login.succeeded loginMethod)

---

# Target Service

- `apps/auth-service`

---

# Architecture

layered 4-layer. 변경 범위는 application layer (use case) 또는 infrastructure (event publisher) 수준.

---

# Edge Cases

- WireMock port 18082가 이미 열려 있는 경우 → dynamic port로 전환
- Multi-module gradle build의 `--parallel` 설정 영향 (현재 비활성이지만 향후 활성화 가능성)
- GDPR email masking과 loginMethod 필드의 직렬화 상호작용 (outbox payload 내)

---

# Failure Scenarios

- CB 리셋 후에도 503 지속 → WireMock request matching 정밀 추적 (request log → expected path 비교)
- Redis marker 로직 수정이 기존 SessionRevokedException 경로를 의도치 않게 깸 → 회귀 방지 위해 양방향 시나리오 테스트 추가

---

# Test Requirements

- 두 @Disabled 테스트 복원 후 로컬 전체 suite pass
- 기존 AuthIntegrationTest의 다른 13개 테스트 회귀 없음
- OAuthProviderTest(4) + OAuthClientFactoryTest(3) + MicrosoftOAuthClientTest(8) unit test 회귀 없음

---

# Definition of Done

- [ ] 두 @Disabled 제거 + CI green
- [ ] 조사 결과(특히 refresh reuse 순서)가 docstring/주석/specs에 기록
- [ ] Ready for review
