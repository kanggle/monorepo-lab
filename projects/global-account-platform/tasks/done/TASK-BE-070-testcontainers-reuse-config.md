# Task ID

TASK-BE-070

# Title

infra(test): Testcontainers reuse + startup/healthcheck 튜닝 (auth-service, security-service 공용)

# Status

ready

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-066 (superseded, split)

---

# Goal

CI 에서 Kafka/MySQL testcontainer 가 테스트 진행 중 early shutdown, port 재할당, network 단절되는 불안정 이슈 해소. TASK-BE-062 residual 3건(OAuthLogin, DetectionE2E, DlqRouting) 의 실패 근본 원인이 testcontainer 인프라 레이어에 있음이 확인됨. 본 task 는 코드/테스트 로직은 건드리지 않고 **container 설정 자체만** 개선한다.

test 레벨 `@Disabled` 제거는 out-of-scope (TASK-BE-071).

---

# Scope

## In Scope

1. **Reuse 정책 정립**:
   - `~/.testcontainers.properties` 기반 `testcontainers.reuse.enable=true` 활성화 권장 사항 `platform/testing-strategy.md` 에 기록
   - 단, 활성화는 개발자 로컬 선택. CI runner 는 reuse 미활성 (세션 격리 우선).
2. **Kafka container (security-service, auth-service 공용)**:
   - `KafkaContainer` `waitingFor(Wait.forLogMessage("... started \\(kafka.server.KafkaServer\\).*", 1))` 등 강건한 헬스체크
   - startup timeout `withStartupTimeout(Duration.ofMinutes(3))` (기본 1분은 CI에서 부족 가능)
   - producer 테스트 설정(`spring.kafka.producer.properties.reconnect.backoff.ms=1000`, `spring.kafka.producer.properties.request.timeout.ms=60000`) — 테스트 프로파일에만
3. **MySQL container**:
   - `MySQLContainer` `withStartupTimeout(Duration.ofMinutes(3))`
   - `withConfigurationOverride` 또는 `withTmpFs` 로 I/O 개선 (CI 디스크 I/O 가 원인일 가능성 대비)
4. **`@Container` static 수명 정책**:
   - auth-service, security-service 두 서비스의 테스트 공용 base class (`AbstractIntegrationTest` 또는 동등) 가 있다면 거기에 static `@Container` 통일; 없다면 각 테스트 클래스의 static 필드 수명 convention 을 `platform/testing-strategy.md` 에 기록
5. **`platform/testing-strategy.md` 업데이트**:
   - Testcontainers convention 섹션 추가: static @Container 패턴, dynamic port 필수 사용, `@DynamicPropertySource` 지연 평가 주의, reuse 정책

## Out of Scope

- 실제 `@Disabled` 제거 (→ TASK-BE-071)
- Dockerfile / compose 변경
- CI runner OS/Docker 버전 변경
- Testcontainers 대체 실행기 도입 (dind, k3s 등)
- `OAuthLoginUseCase` 트랜잭션 재설계 (→ TASK-BE-069)

---

# Acceptance Criteria

- [ ] Kafka testcontainer 에 `waitingFor(Wait.forLogMessage(...))` 또는 동등한 강화 heartbeat 추가
- [ ] Kafka, MySQL testcontainer 에 `withStartupTimeout(Duration.ofMinutes(3))` 적용
- [ ] 테스트 전용 producer 재시도/타임아웃 설정이 `application-test.yml` 또는 `@DynamicPropertySource` 에 반영
- [ ] `platform/testing-strategy.md` 에 Testcontainers convention 섹션 추가
- [ ] `./gradlew :apps:auth-service:test :apps:security-service:test` green (통합 테스트는 @Disabled 유지)
- [ ] 기존 테스트 회귀 없음 (`LoginHistoryImmutabilityTest`, `ActivateSubscriptionIntegrationTest`, etc.)

---

# Related Specs

- `platform/testing-strategy.md`

---

# Related Contracts

없음 (test infra 레이어)

---

# Target Service

- `apps/auth-service`
- `apps/security-service`

공용 설정이 libs/ 안에 있을 경우 해당 모듈도 수정 가능. 새 모듈 생성은 금지.

---

# Architecture

test infrastructure. 레이어 영향 없음.

---

# Edge Cases

- reuse 활성화 시 테스트 간 상태 누수 가능성 — 본 task 에서는 권장 사항 기록만, CI 에서는 미활성이 default
- `withTmpFs` 는 Docker rootless mode 에서 거부될 수 있음 — rootless 여부에 따라 조건부로 적용하거나 단순 `withStartupTimeout` 만 적용

---

# Failure Scenarios

- Kafka 헬스체크 메시지가 버전별로 달라 `Wait.forLogMessage` 가 실패 → 여러 대안 메시지 패턴 `|` 조합 또는 `Wait.forListeningPort()` fallback
- MySQL 의 `withTmpFs` 가 CI 에서 실패 → `withConfigurationOverride` 로 buffer pool 튜닝만 적용

---

# Test Requirements

- 기존 green 테스트 회귀 없음
- container 설정 변경 후 `./gradlew :apps:auth-service:test :apps:security-service:test` pass

---

# Definition of Done

- [ ] 컨테이너 헬스체크/타임아웃 강화
- [ ] producer/consumer 테스트 전용 튜닝
- [ ] testing-strategy.md convention 기록
- [ ] Ready for review
