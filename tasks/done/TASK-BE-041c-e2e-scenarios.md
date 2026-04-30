# Task ID

TASK-BE-041c-e2e-scenarios

# Title

플랫폼 — tests/e2e Gradle 모듈 + 4개 E2E 시나리오 (golden path / bulk-lock / reuse detection / DLQ)

# Status

ready

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-041a-service-dockerfiles
- TASK-BE-041b-security-account-locked-consumer

---

# Goal

041a로 컨테이너화된 4개 서비스가 기동 가능하고 041b로 `account.locked` consumer가 구현된 상태에서, 실제 HTTP + Kafka + DB를 사용한 E2E 시나리오 4개를 자동화한다.

---

# Scope

## In Scope

### 1) `tests/e2e/` Gradle 모듈
- 루트 `settings.gradle.kts`에 `include("tests:e2e")` 추가
- dependencies: JUnit5, RestAssured, Testcontainers (`testcontainers-compose`), Awaitility, Jackson, `kafka-clients` (DLQ 주입용)
- `@Testcontainers` + `ComposeContainer(new File("docker-compose.e2e.yml"))` + `.waitingFor("admin-service", Wait.forHttp("/actuator/health").forStatusCode(200))` 전 서비스 동일 적용

### 2) 시나리오 `GoldenPathE2ETest`
1. `GET /.well-known/admin/jwks.json` → 200, JWKS 파싱 성공
2. `POST /api/admin/auth/login` (dev seed operator) → 401 `ENROLLMENT_REQUIRED` + `bootstrapToken`
3. `POST /api/admin/auth/2fa/enroll` (bootstrap + scope=2fa_enroll) → otpauthUri + recoveryCodes
4. otpauthUri에서 secret 추출 → TotpGenerator 로직(또는 공개 RFC 6238 Java 구현)으로 TOTP 계산
5. `POST /api/admin/auth/2fa/verify` (bootstrap + scope=2fa_verify + totpCode) → 200
6. 재로그인 with totpCode → 200 + accessToken + refreshToken
7. `POST /api/admin/auth/refresh` → 200 + 새 쌍
8. `POST /api/admin/auth/logout` (access JWT) → 204
9. logout 후 access로 보호 엔드포인트(예: bulk-lock) → 401 `TOKEN_REVOKED`

### 3) 시나리오 `CrossServiceBulkLockE2ETest` (041b 의존)
1. login + 2FA 완료 후
2. account-service에 테스트 계정 2건 미리 seed (compose init SQL 또는 API)
3. `POST /api/admin/accounts/bulk-lock` with accountIds → 200 + per-row outcome=LOCKED
4. account-service DB SELECT → locked 상태 확인
5. Awaitility polling (10s) — security-service `account_lock_history` 테이블에 2 row 존재 확인

### 4) 시나리오 `RefreshReuseDetectionE2ETest`
1. login → refreshToken_0
2. refresh → refreshToken_1 (token_0 revoked)
3. refreshToken_0 재사용 → 401 `REFRESH_TOKEN_REUSE_DETECTED`
4. 해당 operator로 refreshToken_1 사용 → 401 (전체 revoke 확인)

### 5) 시나리오 `DlqHandlingE2ETest`
1. Kafka producer로 `auth.login.succeeded` 토픽에 invalid JSON 바이트 주입 (admin client 또는 KafkaProducer)
2. Awaitility로 `auth.login.succeeded.dlq` 토픽에 메시지 도착 검증 (KafkaConsumer subscribe)
3. security-service `/actuator/metrics/kafka.consumer.lag` 200 + 값 >= 0
4. admin-service `/actuator/health/circuitbreakers` 200

### 6) 테스트 유틸
- `tests/e2e/src/test/java/.../TotpTestUtil.java` — RFC 6238 6자리 30s step 재구현 (admin-service 내부 TotpGenerator 의존 금지, 독립 모듈)
- `tests/e2e/src/test/java/.../ComposeFixture.java` — 전 테스트 공유 컴포즈 (JUnit5 extension)
- `tests/e2e/src/test/java/.../E2EBase.java` — RestAssured baseURI 세팅

### 7) 실행
- `./gradlew :tests:e2e:test` — Docker 필수, 없으면 fail
- 실행 시간 목표: 5분 내
- 테스트 격리: compose recreate per class 또는 BeforeAll truncate (권장: BeforeAll cleanup)

### 8) 문서
- `tests/e2e/README.md` — 실행 방법, 시나리오 요약, Known limitations
- `docs/guides/e2e-testing.md` — human reference

## Out of Scope

- CI GitHub Actions (운영 태스크)
- 부하/성능
- `account.unlocked` 시나리오
- Grafana/Prometheus 구동

---

# Acceptance Criteria

- [ ] `./gradlew :tests:e2e:test` 로컬 실행 가능
- [ ] 4 시나리오 모두 PASS
- [ ] idempotent 실행 (재실행 가능)
- [ ] 기존 단위·슬라이스 테스트 회귀 없음
- [ ] README + docs/guides 완결

---

# Related Specs

- `specs/services/admin-service/architecture.md`, `security.md`
- `specs/services/security-service/architecture.md`
- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/auth-events.md`, `account-events.md` (041b)
- `rules/traits/audit-heavy.md` A2

---

# Target Service

- `tests/e2e/` (신규)

---

# Edge Cases

- 컴포즈 기동 지연으로 테스트 타임아웃 → waitStrategy 충분히 (60-120s)
- Kafka 리더 선출 지연 → Awaitility polling
- TOTP 시간 경계 ±1 window 케이스에서 실패 허용 재시도 (1 step 뒤 재계산)

---

# Failure Scenarios

- Docker 미설치 환경 → Testcontainers 기본 fail (skip 금지)
- compose 빌드 실패 시 041a 의존성 미해소 증거

---

# Test Requirements

- 시나리오 4개 + 테스트 유틸

---

# Definition of Done

- [ ] 구현 + 4 시나리오 PASS
- [ ] 문서 완결
- [ ] Ready for review
