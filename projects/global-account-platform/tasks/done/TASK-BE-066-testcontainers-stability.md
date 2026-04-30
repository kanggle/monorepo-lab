# Task ID

TASK-BE-066

# Title

infra(testcontainers): CI 환경 Kafka/MySQL 안정성 재설계 — TASK-BE-062 residual 3건 승계

# Status

superseded

> **종결 (2026-04-20)**: 본 task 의 scope 가 과도함을 인지(OAuth txn 재설계 + Testcontainers infra +
> 3건 re-enable + CI 5회 연속 green) → backend-engineer agent 가 1회 rate-limited 로 중단 후
> WIP draft PR #28 close. 3개 소규모 task 로 분할:
>
> - **TASK-BE-069** — OAuthLoginUseCase#callback `@Transactional` 범위 축소 (application layer 만)
> - **TASK-BE-070** — Testcontainers reuse + startup/healthcheck 튜닝 (test infra 만)
> - **TASK-BE-071** — residual 3건 `@Disabled` 제거 + CI 3회 green 검증 (069+070 depends_on)
>
> 본 파일은 `tasks/done/` 으로 이동. 실제 진행은 069/070/071 에서 수행.

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-062

---

# Goal

TASK-BE-062 에서 4건 복원 후 남은 residual 3건 (`OAuthLoginIntegrationTest`, `DetectionE2EIntegrationTest`, `DlqRoutingIntegrationTest`) 은 모두 코드 레벨이 아닌 **Testcontainers 기반 CI 인프라 안정성 이슈** 로 수렴함. 본 task 는 해당 3건을 CI green 상태로 복원하기 위한 인프라 레이어 재설계를 수행한다.

**근거**: PR #21 (TASK-BE-062 residual-fix) 에서 agent 가 코드 레벨 수정 (UUID 길이, `ErrorHandlingDeserializer` 주입) 후 @Disabled 제거 → CI 실측(run 24644347803) 결과 2건 모두 다시 실패:

- `DetectionE2EIntegrationTest` — `AssertionFailedError` + Kafka broker `Node 1 disconnected` 반복 + dynamic port(32781→32775→32789) 유동
- `DlqRoutingIntegrationTest` — `awaitility ConditionTimeoutException` + Kafka 접속 불가
- `OAuthLoginIntegrationTest` — MySQL testcontainer 가 테스트 도중 종료 (TASK-BE-062 #18 실측)

공통 증상: testcontainer 가 테스트 진행 중 early shutdown, 포트 재할당, 컨테이너 network 단절.

---

# Scope

## In Scope

1. **Testcontainers 재사용/안정성 설정 재점검**:
   - `testcontainers.reuse.enable` 활성화 검토 (CI 에서는 기본 비활성이라 매 테스트 새 컨테이너 → resource 압박)
   - `@Container` static field 수명 정책 일원화 (서비스별 convention 확립)
   - MySQL/Kafka startup timeout / health check 튜닝
2. **Kafka testcontainer 한정 조치**:
   - `KafkaContainer` 의 `waitingFor(Wait.forLogMessage(...))` 강화
   - bootstrap server 재연결 후 topic metadata 전파 보장
   - producer `reconnect.backoff.ms`, `request.timeout.ms` 테스트 전용 값 검토
3. **`OAuthLoginUseCase#callback` @Transactional 범위 축소** (별도 선행 조치):
   - 외부 HTTP 호출 2건 (Google/Microsoft token + userinfo) 을 `@Transactional` 밖으로 분리
   - Hikari connection pinning 해소
4. **3건 복원**: 위 조치 후 `@Disabled` 제거 + CI 10회 연속 green 확인.

## Out of Scope

- Testcontainers 를 다른 실행기(예: dind, k3s 기반) 로 교체
- 테스트 프레임워크 자체 변경 (JUnit→Spock 등)
- CI runner OS/Docker 버전 변경
- production Kafka/MySQL 설정 변경 (테스트 전용 설정만)

---

# Acceptance Criteria

- [ ] `OAuthLoginUseCase#callback` 의 `@Transactional` 범위가 외부 HTTP 호출을 포함하지 않음
- [ ] `OAuthLoginIntegrationTest` 의 `@Disabled` 제거 + CI 연속 5회 green
- [ ] `DetectionE2EIntegrationTest` 의 `@Disabled` 제거 + CI 연속 5회 green
- [ ] `DlqRoutingIntegrationTest` 의 `@Disabled` 제거 + CI 연속 5회 green
- [ ] Testcontainers 사용 convention 이 `platform/testing-strategy.md` 에 반영
- [ ] `./gradlew build` CI green

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/services/auth-service/architecture.md` (OAuthLoginUseCase 트랜잭션 경계)
- `specs/services/security-service/architecture.md` (detection 파이프라인)

---

# Related Contracts

없음 (테스트 인프라 레이어)

---

# Target Service

- `apps/auth-service` (`OAuthLoginIntegrationTest`, `OAuthLoginUseCase`)
- `apps/security-service` (`DetectionE2EIntegrationTest`, `DlqRoutingIntegrationTest`)

---

# Architecture

layered 4-layer. 변경 범위는 application layer (OAuthLoginUseCase 트랜잭션 재설계) + test infrastructure (testcontainers 설정).

---

# Edge Cases

- OAuthLoginUseCase 의 트랜잭션을 축소하면 outbox write 와 external HTTP 호출의 순서/보상 로직 재검토 필요. outbox-first 패턴 유지하되, 실패 시 보상 이벤트 또는 DLQ 경로 명시.
- Testcontainers reuse 활성화 시 테스트 간 상태 누수 방지 — `@BeforeEach` cleanup 강화 필요.
- CI runner 의 docker rootless mode 여부가 reuse 동작에 영향.

---

# Failure Scenarios

- `@Transactional` 범위 축소로 인한 중복 outbox 이벤트 가능성 → idempotency key 로 중복 배제
- testcontainers reuse 설정 활성화 후 local/CI 환경 차이로 인한 flaky test → CI-only 프로파일 분리

---

# Test Requirements

- 3건 복원된 통합 테스트 + 기존 회귀 테스트 모두 CI 5회 연속 pass
- `OAuthLoginUseCaseTest` unit test 로 트랜잭션 경계 변화에 따른 시나리오 보강

---

# Definition of Done

- [ ] 3건 `@Disabled` 제거 + CI 연속 5회 green
- [ ] `platform/testing-strategy.md` 에 Testcontainers convention 반영
- [ ] Ready for review
