# Task ID

TASK-BE-041a-service-dockerfiles

# Title

플랫폼 — 4개 서비스 Dockerfile + multi-stage bootJar + compose.e2e 기동 foundation

# Status

ready

# Owner

backend

# Task Tags

- deploy
- code

# depends_on

- (없음)

---

# Goal

E2E smoke의 선행 foundation. admin/auth/account/security 네 서비스를 Docker 이미지로 빌드·실행 가능하게 만들고, `docker-compose.e2e.yml`로 기동+헬스체크 PASS까지만 검증한다. E2E 시나리오 테스트는 041c에서 분리.

---

# Scope

## In Scope

### 1) 각 서비스 Dockerfile
- `apps/admin-service/Dockerfile`, `apps/auth-service/Dockerfile`, `apps/account-service/Dockerfile`, `apps/security-service/Dockerfile`
- Multi-stage:
  - `FROM eclipse-temurin:21-jdk AS build` — Gradle bootJar 생성 (또는 외부 빌드 후 jar COPY)
  - `FROM eclipse-temurin:21-jre` — 최종 이미지, non-root user, HEALTHCHECK
- 기본 HEALTHCHECK: `wget --spider http://localhost:{port}/actuator/health || exit 1`
- JVM 옵션: `-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75`
- 포트: 각 서비스 application.yml의 `server.port` 값 준수
- 빌드 컨텍스트는 루트로 설정 (monorepo gradle shared config 때문)

### 2) docker-compose.e2e.yml (루트)
- infra: mysql:8, redis:7, kafka(bitnami/kafka KRaft 단일 노드)
- 4개 서비스: build context는 각 `apps/{svc}`, Dockerfile 경로 지정
- environment:
  - `SPRING_PROFILES_ACTIVE=e2e`
  - DB/Redis/Kafka 접속 정보
  - admin-service: `ADMIN_JWT_V1_PEM`, `ADMIN_TOTP_V1_KEY` 고정 테스트 값
- depends_on condition: infra healthcheck PASS 후 서비스 기동
- healthcheck 간격: 10s interval, 30-60s start_period

### 3) 서비스 application-e2e.yml
- `spring.flyway.locations: classpath:db/migration,classpath:db/migration-dev` (admin만 dev seed 필요)
- DB URL/Redis/Kafka 호스트를 service name으로 (compose DNS)
- logging minimal

### 4) Smoke 검증 (041a 수준)
- `./gradlew :apps:{svc}:bootJar` 4개 모두 성공
- `docker compose -f docker-compose.e2e.yml build` 성공
- `docker compose -f docker-compose.e2e.yml up -d` 후 30-60s 대기, 모든 서비스 `/actuator/health` 200 반환
- 이 smoke는 CI 아닌 로컬 수동 검증 허용; 자동화는 041c에서

### 5) 문서
- `docs/guides/docker-build.md` (human reference)

## Out of Scope

- Scenario tests (041c)
- CI GitHub Actions (별도 DevOps 태스크)
- Kafka Avro schema-registry (필요 시 041c에서 추가)
- 프로덕션 이미지 레지스트리 푸시

---

# Acceptance Criteria

- [ ] 4개 Dockerfile 존재, multi-stage, non-root user, HEALTHCHECK 포함
- [ ] `docker compose -f docker-compose.e2e.yml build` 성공
- [ ] `docker compose -f docker-compose.e2e.yml up -d` 후 4개 서비스 모두 헬스체크 healthy
- [ ] admin-service 기동 시 Flyway `db/migration + db/migration-dev` 모두 로드 (V0014 dev seed 적용)
- [ ] 각 서비스 `application-e2e.yml` 존재 및 compose 환경변수 바인딩
- [ ] 기존 단위 테스트 회귀 없음

---

# Related Specs

- `specs/services/{admin,auth,account,security}-service/architecture.md` (각 서비스 기본 구조)
- `platform/service-types/rest-api.md`, `platform/service-types/event-consumer.md`

---

# Target Service

- `apps/admin-service/`, `apps/auth-service/`, `apps/account-service/`, `apps/security-service/`
- 루트 `docker-compose.e2e.yml` (신규)

---

# Edge Cases

- bootJar 빌드 의존성(공유 libs/java-common, libs/java-security)이 빌드 단계에서 누락되지 않도록 multi-module Gradle 컨텍스트 주의
- ARM64 Mac과 x86 CI 모두에서 빌드 가능해야 함 (`platform: linux/amd64` 지정 여부 판단)

---

# Failure Scenarios

- admin-service application-e2e.yml에 AES-GCM key 누락 → 기동 실패. compose environment에서 전달 필수

---

# Test Requirements

- 로컬 smoke만 수행, 자동화는 041c

---

# Definition of Done

- [ ] 4 Dockerfile + compose + profile 완료
- [ ] 로컬 compose up 성공 기록 (보고서에 health 출력 첨부)
- [ ] Ready for review
