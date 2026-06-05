# Task ID

TASK-BE-013

# Title

TASK-BE-004 리뷰 수정 — credential lookup 응답 필드 누락, 도메인 레이어 JPA 오염, 내부 delete 엔드포인트 누락, 기타

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-004

---

# Goal

TASK-BE-004(account-service 부트스트랩) 코드 리뷰에서 발견된 Critical 및 Warning 수준의 문제를 수정한다.

---

# Scope

## In Scope

### Critical 수정

1. **CredentialLookupResponse 필드 누락** (`specs/contracts/http/internal/auth-to-account.md`)
   - `GET /internal/accounts/credentials` 응답에 `credentialHash`·`hashAlgorithm` 필드 추가
   - 현재 구현은 `{ accountId, accountStatus }`만 반환하지만 계약은 `{ accountId, credentialHash, hashAlgorithm, accountStatus }` 요구
   - `CredentialLookupResult`, `CredentialLookupResponse` 레코드에 필드 추가
   - `AccountStatusUseCase.lookupByEmail()`이 auth-service에서 credential hash를 조회하거나 Stub 응답 제공

2. **도메인 레이어 JPA 어노테이션 제거** (`specs/services/account-service/architecture.md` — Allowed Dependencies)
   - `Account`, `Profile`, `AccountStatusHistoryEntry` 도메인 객체에서 `@Entity`, `@Table`, `@Column`, `@Id`, `@Version`, `@Enumerated`, `@GeneratedValue` 등 JPA 어노테이션 제거
   - `infrastructure/persistence/` 아래에 `AccountJpaEntity`, `ProfileJpaEntity`, `AccountStatusHistoryJpaEntity` JPA 엔터티 클래스 신설
   - 도메인 객체 ↔ JPA 엔터티 간 매핑 로직 추가
   - `AccountJpaRepository`, `ProfileJpaRepository`, `AccountStatusHistoryJpaRepository`가 JPA 엔터티 기반으로 동작하도록 수정

3. **`POST /internal/accounts/{accountId}/delete` 엔드포인트 누락** (`specs/contracts/http/internal/admin-to-account.md`)
   - `AccountLockController`(또는 신규 컨트롤러)에 admin delete 엔드포인트 추가
   - 요청: `{ reason: "ADMIN_DELETE|REGULATED_DELETION", operatorId, ticketId? }`
   - 응답 202: `{ accountId, previousStatus, currentStatus: "DELETED", gracePeriodEndsAt }`
   - 오류: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND`
   - `AccountStatusUseCase.deleteAccount()` 경유, outbox 이벤트 포함

### Warning 수정

4. **내부 API 상태 코드 불일치** (`specs/contracts/http/internal/security-to-account.md`)
   - DELETED→LOCKED 불허 전이는 **409** 반환이어야 하나 현재 400 반환
   - `GlobalExceptionHandler`에서 `StateTransitionException` 처리 시 전이 대상에 따라 409/400 구분하거나, security-to-account 컨트랙트에 맞게 409 응답

5. **내부 API 인증 없음** (`specs/services/account-service/architecture.md` — Boundary Rules)
   - `SecurityConfig`의 `/internal/**` 경로에 내부 서비스 토큰 검증 또는 IP 필터 추가
   - 최소: `X-Internal-Token` 헤더 검증 (mTLS는 인프라 레이어 — 초기 스코프는 헤더 기반도 허용)

6. **`AccountAlreadyExistsException` PII 누출** (`platform/coding-rules.md` — Logging, `platform/error-handling.md`)
   - 예외 메시지에서 이메일 주소 제거
   - 수정: `"Account already exists"` (이메일 제외)

7. **`@Value` 필드 주입** (`platform/coding-rules.md` — Dependencies)
   - `AccountStatusUseCase`의 `@Value("${account.deletion.grace-period-days:30}") private int gracePeriodDays` 를 생성자 주입으로 변경
   - `@ConfigurationProperties` 클래스 도입 또는 생성자에서 `@Value` 수신

## Out of Scope

- 패스워드 재인증 실제 검증 (auth-service 호출) — 백로그
- mTLS 인프라 구성 — 인프라 레이어 별도 태스크
- 이메일 검증 프로세스 — 백로그 (TASK-BE-009 이후)

---

# Acceptance Criteria

- [ ] `GET /internal/accounts/credentials` 응답에 `credentialHash`·`hashAlgorithm` 필드 포함
- [ ] 도메인 패키지(`domain/`) 내 Java 파일에 `import jakarta.persistence.*` 없음
- [ ] `POST /internal/accounts/{accountId}/delete` 엔드포인트 존재, 202 정상 응답
- [ ] DELETED→LOCKED 전이 시 409 `STATE_TRANSITION_INVALID` 반환
- [ ] `AccountAlreadyExistsException` 메시지에 이메일 미포함
- [ ] `AccountStatusUseCase`에서 `@Value` 필드 주입 없음 (생성자 주입 또는 `@ConfigurationProperties`)
- [ ] 내부 API 경로(`/internal/**`)에 최소한 토큰 기반 인증 검증 존재
- [ ] 모든 기존 테스트 통과, 신규 수정 항목에 대한 테스트 추가

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/account-service/data-model.md`

# Related Skills

- `.claude/skills/backend/architecture/layered/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/internal/auth-to-account.md`
- `specs/contracts/http/internal/security-to-account.md`
- `specs/contracts/http/internal/admin-to-account.md`

---

# Target Service

- `apps/account-service`

---

# Architecture

`specs/services/account-service/architecture.md` — Layered Architecture. JPA 엔터티는 `infrastructure/persistence/` 에 위치. 도메인 레이어는 프레임워크 의존 없음.

---

# Edge Cases

- credential hash 조회: 초기 스코프에서 account-service는 credential을 소유하지 않음 (auth-service 소유). 초기 구현은 `credentialHash: null`, `hashAlgorithm: "none"` stub 또는 TODO 마킹 허용, 단 응답 필드는 반드시 포함
- JPA 엔터티 분리 시 기존 Flyway 마이그레이션 영향 없어야 함 (테이블 이름 동일 유지)
- delete 엔드포인트: 이미 DELETED인 경우 409, 유예 만료 후 재삭제 시도도 409

---

# Failure Scenarios

- JPA 엔터티 매핑 오류 → `@SpringBootTest` 컨텍스트 로드 실패 → 즉시 발견 가능
- 도메인 분리 중 순환 의존 → 컴파일 오류로 즉시 발견

---

# Test Requirements

- Unit: `AccountStatusMachine` 기존 테스트 모두 통과
- Slice: `InternalControllerTest` 업데이트 — `/credentials` 응답 필드 검증, `/delete` 신규 케이스, 409 상태 코드 검증
- Integration: 기존 `AccountSignupIntegrationTest` 통과, `lockDeletedAccount_returns400` → `returns409`로 수정

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match (auth-to-account.md 필드, admin-to-account.md delete 엔드포인트)
- [ ] Domain layer has no framework imports
- [ ] Ready for review
