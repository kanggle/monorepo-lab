# Task ID

TASK-BE-015

# Title

Fix TASK-BE-010 — audit-before-downstream 위반, Resilience4j 미적용, 에러 코드 불일치

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-010

---

# Goal

TASK-BE-010 리뷰에서 발견된 Critical/Warning 이슈들을 수정한다:
1. **Critical**: audit row가 downstream HTTP 호출 **이전에** 기록되지 않음 (현재는 호출 후 기록). 컨트랙트 및 A10 fail-closed 위반
2. **Critical**: `DOWNSTREAM_FAILURE` 에러 코드 — 컨트랙트는 `DOWNSTREAM_ERROR` 요구
3. **Critical**: Resilience4j 의존성만 선언됨, 재시도·circuit breaker 실제 미적용
4. **Warning**: outbox 테이블명 `outbox` → libs/java-messaging과 일치성 확인 필요
5. **Warning**: 인라인 JSON 에러 응답에 `timestamp` 필드 누락 (OperatorAuthenticationFilter, SecurityConfig)

---

# Scope

## In Scope

1. **audit-before-downstream 패턴**:
   - `AccountAdminUseCase`, `SessionAdminUseCase`에서 IN_PROGRESS 또는 PENDING audit row를 downstream 호출 **이전에** 삽입
   - 스펙의 append-only 제약 준수를 위해: completion row 별도 추가 방식 또는 outcome 컬럼 UPDATE 허용 DB 트리거 검토
   - 현재 `AdminActionAuditor.reserveAuditId()`는 UUID만 생성함 — 실제 row INSERT로 변경
   - 프로세스 크래시 시나리오에서도 audit 존재 보장

2. **DOWNSTREAM_ERROR로 변경**:
   - `AdminExceptionHandler`의 `DOWNSTREAM_FAILURE` → `DOWNSTREAM_ERROR`
   - `platform/error-handling.md`에 `DOWNSTREAM_ERROR` 등록

3. **Resilience4j 적용**:
   - `AccountServiceClient`, `AuthServiceClient`에 `@Retry` + `@CircuitBreaker` 어노테이션 적용
   - `application.yml`에 `resilience4j.retry.instances.<name>` 설정:
     - `maxAttempts: 3`, 4xx는 재시도 금지 (`ignoreExceptions`)
     - `waitDuration: 500ms`, 지수 백오프
   - `resilience4j.circuitbreaker.instances.<name>`: 50% failure rate / 10s window
   - `SecurityServiceClient`는 현재 silent fallback 유지 (스펙 요구)

4. **outbox 테이블명 일치**:
   - `V0002__create_outbox_events.sql`이 `outbox` 테이블 생성 중 — libs/java-messaging의 `OutboxJpaEntity` 테이블명 확인
   - admin-service에도 security-service처럼 `orm.xml` override로 `outbox_events` 강제하거나 일관된 `outbox` 유지

5. **timestamp 필드 추가**:
   - `OperatorAuthenticationFilter.unauthorized()`의 인라인 JSON에 `timestamp` 추가
   - `SecurityConfig`의 accessDenied/authenticationEntryPoint 예외 핸들러도 동일

6. **테스트 보강**:
   - @WebMvcTest 슬라이스 테스트: JWT 필터, role 검증, reason 누락
   - Integration: Testcontainers (MySQL + Kafka) + WireMock (auth/account/security) — lock → audit → 이벤트 E2E
   - `@EnabledIf("isDockerAvailable")` 적용

## Out of Scope

- 분산 트랜잭션 (Saga) 도입
- admin 대시보드 UI

---

# Acceptance Criteria

- [ ] lock/unlock/revoke 명령 실행 시 `admin_actions`에 IN_PROGRESS row가 downstream 호출 이전에 존재
- [ ] downstream 실패 시 이미 INSERT된 audit row의 outcome이 FAILURE로 최종 기록
- [ ] `AdminExceptionHandler`가 `DOWNSTREAM_ERROR` 반환
- [ ] `platform/error-handling.md`에 `DOWNSTREAM_ERROR` 등록
- [ ] downstream 호출에 Resilience4j @Retry + @CircuitBreaker 적용 (4xx 제외 재시도)
- [ ] 모든 인라인 에러 응답에 `timestamp` 포함
- [ ] @WebMvcTest 슬라이스 + 통합 테스트 추가
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/dependencies.md`
- `specs/features/admin-operations.md`
- `specs/features/audit-trail.md`
- `platform/error-handling.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/http/internal/admin-to-account.md`
- `specs/contracts/http/internal/admin-to-auth.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- IN_PROGRESS row 삽입 후 downstream 호출 전 JVM 크래시 → 재시작 시 orphan IN_PROGRESS row → 스크립트 또는 manual cleanup (문서화)
- Resilience4j 재시도 중복 요청 → Idempotency-Key로 방어

---

# Failure Scenarios

- DB 불가 상태에서 audit INSERT 실패 → 500 즉시 반환 (A10 fail-closed 재확인)
- Circuit breaker open 상태 → 503 즉시 반환 + audit row outcome=FAILURE

---

# Test Requirements

- Unit: Resilience4j 재시도 동작 (WireMock 2회 5xx → 성공)
- Slice: @WebMvcTest — JWT 필터, role 검증, reason 누락 400 응답
- Integration: lock → audit IN_PROGRESS → downstream → audit SUCCESS + outbox event

---

# Definition of Done

- [ ] audit-before-downstream 검증
- [ ] Resilience4j 동작 검증
- [ ] 에러 코드 DOWNSTREAM_ERROR 일치
- [ ] Tests passing
- [ ] Ready for review
