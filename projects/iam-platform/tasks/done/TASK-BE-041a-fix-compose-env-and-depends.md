# Task ID

TASK-BE-041a-fix-compose-env-and-depends

# Title

docker-compose.e2e.yml — auth-service ACCOUNT_SERVICE_URL 누락 + account-service kafka depends_on 누락 수정

# Status

ready

# Owner

backend

# Task Tags

- deploy
- fix

# depends_on

- TASK-BE-041a-service-dockerfiles (원본)

---

# Goal

TASK-BE-041a 리뷰에서 발견된 docker-compose.e2e.yml 의 두 가지 결함을 수정한다.

1. `auth-service` 환경변수 블록에 `ACCOUNT_SERVICE_URL` 누락 → 컨테이너 네트워크에서 account-service 호출 시 `localhost:8082`로 연결 시도해 실패
2. `account-service` depends_on에 `kafka: { condition: service_healthy }` 누락 → `java-messaging` 라이브러리가 Kafka producer를 자동 설정하므로 Kafka가 준비되기 전에 account-service가 기동되면 Kafka 연결 오류 발생 가능

---

# Scope

## In Scope

### 1) docker-compose.e2e.yml 수정

- `auth-service` environment 블록에 추가:
  ```yaml
  ACCOUNT_SERVICE_URL: http://account-service:8082
  ```
- `account-service` depends_on 블록에 추가:
  ```yaml
  kafka: { condition: service_healthy }
  ```
- `apps/account-service/src/main/resources/application-e2e.yml`에 `spring.kafka.bootstrap-servers` 바인딩 추가:
  ```yaml
  spring:
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
  ```
  (account-service application.yml에 spring.kafka 설정이 없으므로 e2e 프로파일에서 명시 필요)

## Out of Scope

- Dockerfile 변경 없음
- 단위 테스트 변경 없음

---

# Acceptance Criteria

- [ ] `docker compose -f docker-compose.e2e.yml up -d` 후 auth-service가 account-service의 `/internal/accounts/credentials` 호출 성공
- [ ] account-service가 Kafka healthy 상태 이후에 기동됨 (docker compose ps에서 순서 확인)
- [ ] 기존 단위 테스트 회귀 없음

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/account-service/architecture.md`

---

# Related Contracts

- `specs/contracts/http/internal/auth-to-account.md`

---

# Edge Cases

- account-service `application.yml`에는 `spring.kafka` 설정이 없으므로 e2e 프로파일에서 명시적으로 추가해야 Spring Boot가 올바른 broker를 사용함

---

# Failure Scenarios

- `ACCOUNT_SERVICE_URL` 미추가 시 auth-service가 로그인 처리 중 account-service 조회 실패 → 로그인 502 에러
- `kafka depends_on` 미추가 시 account-service 기동 시 Kafka broker에 연결 실패 → OutboxPollingScheduler 오류 (java-messaging 포함으로 Kafka autoconfigure 활성화)

---

# Test Requirements

- 로컬 compose up smoke 검증 (자동화는 041c에서)

---

# Definition of Done

- [ ] docker-compose.e2e.yml 수정 완료
- [ ] account-service application-e2e.yml kafka 설정 추가
- [ ] 로컬 compose up 후 전체 서비스 healthy 확인
- [ ] Ready for review
