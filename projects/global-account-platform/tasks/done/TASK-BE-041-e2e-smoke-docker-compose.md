# Task ID

TASK-BE-041-e2e-smoke-docker-compose

# Title

플랫폼 — docker-compose 기반 E2E smoke 테스트 (auth → admin → security 한 사이클)

# Status

ready

# Owner

backend

# Task Tags

- test
- deploy

# depends_on

- TASK-BE-040-admin-refresh-logout

---

# Goal

서비스 경계에서 실제 Docker 컨테이너로 auth + admin + security + account-service + 인프라(MySQL, Redis, Kafka, schema-registry)를 기동해 로그인 → 2FA → 계정 강제 로그아웃 → 감사 이벤트 처리까지 한 사이클을 검증한다. 단위·슬라이스 테스트로 검증 불가능한 cross-service integration·Kafka event flow·Flyway prod vs non-prod 구분을 실제 실행으로 확인한다.

---

# Scope

## In Scope

### 1) 실행 인프라
- 루트 `docker-compose.e2e.yml` 신설 (기존 `docker-compose.yml`은 local dev, 충돌 없게 분리)
- 서비스: mysql(5.7 또는 8), redis, kafka(KRaft 또는 zookeeper), schema-registry(Avro 사용 시), account-service, auth-service, admin-service, security-service
- 포트 고정, `depends_on` + healthcheck로 기동 순서 보장
- profile `spring.profiles.active=e2e` 추가: Flyway `db/migration` + `db/migration-dev` 모두 포함 (prod 차단 대상 아님)

### 2) 테스트 모듈
- 루트 Gradle 서브프로젝트 `tests/e2e/` 신설 (Gradle Java test 프로젝트, JUnit5 + RestAssured + Testcontainers Compose 또는 Spring Boot `@ImportTestcontainers`)
- 권장: Testcontainers의 `DockerComposeContainer` + `waitingFor` 로 컴포즈 전체 기동 후 테스트 실행

### 3) 시나리오 — 골든 패스 (Primary)
1. admin-service `GET /.well-known/admin/jwks.json` → 200, 유효 JWKS
2. admin-service `POST /api/admin/auth/login` (dev seed operator, V0014 해시) → 401 `ENROLLMENT_REQUIRED` + bootstrapToken (SUPER_ADMIN은 require_2fa)
3. `POST /api/admin/auth/2fa/enroll` (bootstrap token) → otpauthUri + recoveryCodes 10
4. TOTP 코드를 secret에서 계산 → `POST /api/admin/auth/2fa/verify` → 200
5. 재로그인 `POST /api/admin/auth/login` with totpCode → 200 + accessToken + refreshToken
6. `POST /api/admin/auth/refresh` → 200 + 새 쌍, 기존 jti revoked
7. `POST /api/admin/auth/logout` → 204 + 이후 access token으로 보호 엔드포인트 접근 → 401 `TOKEN_REVOKED`

### 4) 시나리오 — 크로스 서비스 (Secondary)
1. 1번~5번 login 성공 후
2. admin-service `POST /api/admin/accounts/bulk-lock` (account.lock 권한) 대상 accountId 목록 전송
3. account-service가 실제 lock 반영 → admin-service가 `AccountLockedEvent` Kafka 발행 → security-service consumer가 수신해 `account_lock_history` 또는 동등 테이블에 기록
4. security-service `GET /internal/security/...` (또는 admin-service의 audit query) 로 기록 검증

### 5) 시나리오 — 재사용 탐지
1. login → refreshA → refreshB (refreshA revoked)
2. refreshA 재사용 → 401 REFRESH_TOKEN_REUSE_DETECTED
3. 해당 operator의 refreshB도 revoked 상태 확인 (다시 refresh 시 401)

### 6) 시나리오 — DLQ 검증
1. auth-events 토픽에 invalid JSON 메시지 주입
2. security-service consumer가 3회 retry 후 `.dlq` 토픽에 기록
3. admin-service `/actuator/health/circuitbreakers`, security-service `/actuator/metrics/kafka.consumer.lag` 정상 노출 확인

### 7) CI 통합 (선택)
- 루트 `build.gradle.kts` 또는 `Makefile`에 `./gradlew :tests:e2e:test` 타깃 추가
- GitHub Actions workflow는 본 태스크 범위 밖으로 명시 (운영 태스크)

### 8) 문서
- `docs/guides/e2e-testing.md` (human reference) 신설 — 실행 방법, 장애 대응. AI용 아님.
- `tests/e2e/README.md` — 실행 절차, 컴포즈 topology 다이어그램(텍스트), Known limitations

## Out of Scope

- GitHub Actions CI YAML 작성 (별도 DevOps 태스크)
- Kafka broker 클러스터 구성 (단일 인스턴스로 충분)
- 부하/성능 테스트 (별도)
- Grafana/Prometheus 구동 (별도 관측성 태스크)
- community/membership service 통합 (FROZEN)

---

# Acceptance Criteria

- [ ] `docker-compose.e2e.yml`로 전 서비스 기동 성공
- [ ] `tests/e2e/` 모듈 빌드 + 4개 시나리오 모두 PASS (golden path / cross-service / reuse detection / DLQ)
- [ ] `./gradlew :tests:e2e:test` 로 실행 가능 (Docker 전제 조건 명시)
- [ ] 테스트는 idempotent (중복 실행 가능)
- [ ] 기존 단위·슬라이스 테스트 회귀 없음
- [ ] README + docs/guides/e2e-testing.md 문서 완결

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/security.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/security-service/architecture.md`
- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/auth-events.md`
- `platform/service-types/event-consumer.md`
- `rules/traits/audit-heavy.md` A2

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/http/internal/admin-to-auth.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `tests/e2e/` (신규 Gradle 모듈)
- 루트 `docker-compose.e2e.yml`

---

# Edge Cases

- Docker 미설치 환경에서 `./gradlew :tests:e2e:test`가 명확한 에러로 실패 (skip 대신 fail하여 CI에서 명시적)
- 컴포즈 기동 시 Flyway가 `V0014__seed_dev_super_admin_password.sql`(non-prod)를 로드하므로 e2e profile = non-prod 또는 dev로 설정
- security-service consumer가 이벤트 수신 전에 테스트 어설션이 실행될 가능성 → Awaitility polling 사용

---

# Failure Scenarios

- 기동 순서 실패(예: admin-service가 MySQL 없이 시작) → healthcheck + depends_on condition 명시
- Kafka 초기 리더 선출 지연 → 10-20s waitStrategy
- 테스트 격리 실패(이전 실행 잔여 데이터) → `@BeforeAll`에서 DB truncate 또는 testcontainers recreate

---

# Test Requirements

- 위 4개 시나리오는 모두 실제 HTTP 호출 + Kafka 전파 + DB 변경 검증
- 테스트 시간 목표: 전체 5분 이내 (기동 포함)
- RestAssured 사용으로 JSON 구조 검증

---

# Definition of Done

- [ ] 컴포즈 + 테스트 모듈 동작
- [ ] 4 시나리오 PASS
- [ ] 문서 완결
- [ ] Ready for review
