# Task ID

TASK-BE-030-fix-downstream-4xx-and-idempotency-race

# Title

admin-service — bulk-lock 4xx 분류의 타입 기반 전환 + idempotency race/저장 실패 처리

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-030

---

# Goal

TASK-BE-030 리뷰에서 발견된 Critical 2건과 Warning 2건을 해소한다. 다운스트림 4xx 분류를 예외 메시지 문자열 매칭이 아닌 타입/필드 기반으로 전환하고, idempotency 저장 실패 swallow 및 동시 첫 요청 race condition을 올바르게 처리한다.

---

# Scope

## In Scope

### Fix 1 — 4xx 분류 타입화 (Critical)
- `apps/admin-service/src/main/java/com/example/admin/application/BulkLockAccountUseCase.java:processOne`의 `ex.getMessage().contains(" 404" | " 400" | " 409")` 문자열 분기 제거.
- 옵션 A: `NonRetryableDownstreamException`에 `errorCode: String` 및/또는 `httpStatus: int` 필드 추가, caller가 필드로 분기.
- 옵션 B: 별도 예외 서브클래스 (`AccountNotFoundException`, `AccountAlreadyLockedException`, `StateTransitionInvalidException`) 도입하고 `AccountServiceClient`에서 HTTP 상태·응답 body의 `error.code`에 따라 적절한 서브클래스를 던짐.
- 선호: 옵션 B (도메인 예외) — error-handling.md의 코드 레지스트리에 맞춤.
- 테스트 픽스처의 `"account-service error 404"` 문자열 의존 제거.

### Fix 2 — idempotency 저장 race + 실패 처리 (Critical)
- `BulkLockAccountUseCase.execute`에서 `BulkLockIdempotencyJpaRepository.save()` 실패 경로를:
  1. `DataIntegrityViolationException` 은 catch하여 PK 중복으로 해석 → 저장된 row 재조회 → `request_hash` 비교 후 replay 응답 반환 (find-or-save 패턴).
  2. 해시 불일치 시 `IdempotencyKeyConflictException` throw (409).
  3. 그 외 저장 실패는 warn 로그 + `FAILURE` 응답 또는 500으로 명시 반환. 현재처럼 swallow 금지.
- 동시 첫 요청 2개가 동일 `(operator_id, idempotency_key)`로 들어오는 race를 위 처리로 결정적 단일-실행 보장.

### Fix 3 — application layer의 infra 직접 의존 제거 (Warning)
- `BulkLockAccountUseCase`가 `BulkLockIdempotencyJpaRepository`/`AdminOperatorJpaRepository`를 직접 import하는 경계 위반을 port 인터페이스 도입으로 해소.
- `BulkLockIdempotencyPort`, `OperatorLookupPort` 인터페이스를 `application/port/` 하위에 추가, 기존 JPA repository를 adapter로 연결 (`infrastructure/persistence/...Adapter`).
- `architecture.md`의 Allowed Dependencies 다이어그램과 일치.

### Fix 4 — 테스트 해시 로직 중복 제거 (Warning)
- `BulkLockAccountUseCaseTest.identical_retry_returns_stored_response`가 내부 해시 계산 로직을 복제. 테스트 helper 또는 `@VisibleForTesting` 패키지 메서드 노출.

### (선택) Suggestion
- `BulkLockAccountResult.outcome`을 enum(`Outcome`)으로 정의.
- `BulkLockRequest` 및 컨트롤러에 `Idempotency-Key` 길이(≤64자) 검증 추가.
- 빈 `accountIds` 리스트(`@NotEmpty`) 및 길이 초과 edge case 컨트롤러 슬라이스 테스트 추가.

## Out of Scope

- 비동기/큐 기반 대량 처리
- bulk-unlock 엔드포인트

---

# Acceptance Criteria

- [ ] `BulkLockAccountUseCase`에 예외 메시지 문자열 매칭 0건
- [ ] 다운스트림 404/400/409가 타입·필드로 분기되어 outcome에 매핑
- [ ] 동일 `(operator_id, idempotency_key)` 동시 요청 중 1건만 실제 실행, 나머지는 저장된 응답 반환
- [ ] idempotency 저장 실패 시 명확한 응답 (swallow 금지)
- [ ] application layer에서 `infrastructure/persistence/*Jpa*` 직접 import 0건
- [ ] 기존 테스트 회귀 없음 + 신규 race / `@Size(max=64)` 테스트 통과
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md` (application/infrastructure 경계)
- `specs/services/admin-service/rbac.md`
- `specs/contracts/http/error-handling.md` (에러 코드 레지스트리)

# Related Contracts

- `specs/contracts/http/admin-api.md` (bulk-lock 엔드포인트)

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- `DataIntegrityViolationException` 은 H2/MySQL/PostgreSQL 드라이버별 메시지가 다름 — 타입만 의존
- 저장된 응답의 역직렬화 실패(스키마 변경) — 500 + 명확한 로그

---

# Failure Scenarios

- port 도입 시 기존 통합 테스트가 Spring wiring에 영향 — adapter `@Component` + 동일 bean 이름 유지로 완화

---

# Test Requirements

- Unit: 4xx 타입 분기 3케이스, race 시뮬레이션 (DataIntegrityViolationException 강제 주입)
- Slice: 컨트롤러 `@Size(max=64)`/`@NotEmpty` 검증 추가
- 기존 7+6 케이스 유지

---

# Definition of Done

- [ ] 구현 완료
- [ ] 테스트 통과
- [ ] Ready for review
