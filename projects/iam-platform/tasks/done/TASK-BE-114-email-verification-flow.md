---
id: TASK-BE-114
title: "feat(account): 이메일 인증 플로우 — 회원가입 후 비차단(non-blocking) 이메일 소유권 확인"
status: ready
priority: medium
target_service: account-service
tags: [code, api, event]
created_at: 2026-04-26
---

# TASK-BE-114: 이메일 인증 플로우 (비차단 방식)

## Goal

`specs/features/signup.md`에서 "optional in initial scope"로 미뤄진 **이메일 인증 플로우**를 구현한다.

설계 원칙:
- **비차단(non-blocking)**: 이메일 미인증 상태에서도 계정은 ACTIVE이며 서비스 이용 가능
- 인증 완료 시 `email_verified_at` 필드가 채워짐
- 인증 토큰은 Redis에 저장 (TTL 24시간), 1회 사용 후 삭제
- BE-108의 `PasswordResetTokenStore` 패턴을 그대로 재사용

## Scope

### In

**`specs/contracts/http/account-api.md` 계약 추가 (구현 전 먼저)**
- `POST /api/accounts/signup/verify-email`
  - Auth: None (토큰 자체가 인증 수단)
  - Request: `{ "token": "<uuid>" }`
  - Response 200: `{ "accountId": "...", "emailVerifiedAt": "..." }`
  - Errors:
    - 400 `TOKEN_EXPIRED_OR_INVALID`: 토큰 만료 또는 존재하지 않음
    - 409 `EMAIL_ALREADY_VERIFIED`: 이미 인증된 경우
- `POST /api/accounts/signup/resend-verification-email`
  - Auth: 필요 (`X-Account-Id` 헤더, gateway 주입)
  - Request: body 없음
  - Response 204
  - Errors:
    - 409 `EMAIL_ALREADY_VERIFIED`
    - 429 `RATE_LIMITED`: 재발송 레이트 리밋 (5분 내 1회)

**Flyway migration 추가**
- `V0008__add_email_verified_at.sql`
  - `ALTER TABLE accounts ADD COLUMN email_verified_at DATETIME(6) NULL;`
  - (인덱스 불필요 — 단순 nullable 컬럼)

**`Account` 도메인 수정**
- 필드 추가: `Instant emailVerifiedAt`
- 메서드 추가: `verifyEmail(Instant now)` — `emailVerifiedAt == null` 인 경우만 설정; 이미 인증된 경우 `IllegalStateException` (호출자가 409로 변환)

**`EmailVerificationTokenStore` (신규 — domain/repository)**
- interface: `save(token, accountId, Duration ttl)`, `findAccountId(token): Optional<String>`, `delete(token)`
- 구현: `RedisEmailVerificationTokenStore` (infrastructure/redis)
  - Key: `"email-verify:" + token`, UUID4 토큰

**`EmailVerificationNotifier` port (신규 — application/port)**
- `sendVerificationEmail(toEmail: String, token: String)`
- Stub 구현 `Slf4jEmailVerificationNotifier` — 로그만 출력 (R4 준수: 토큰 로그 금지, 이메일 마스킹)

**`VerifyEmailUseCase` (신규 — application/service)**
- `execute(token: String): Account`
- 순서: `tokenStore.findAccountId(token)` → account 조회 → `account.verifyEmail(Instant.now())` → save → `tokenStore.delete(token)` → return
- 토큰 미존재: `EmailVerificationTokenInvalidException` → 400
- 이미 인증: `IllegalStateException` catch → `EmailAlreadyVerifiedException` → 409

**`SendVerificationEmailUseCase` (신규 — application/service)**
- `execute(accountId: String)`
- 순서: account 조회 → 이미 인증 시 `EmailAlreadyVerifiedException` → 재발송 레이트 리밋 확인 (Redis `email-verify:rate:{accountId}`, TTL 5분) → 신규 토큰 생성(UUID) → `tokenStore.save(token, accountId, 24h)` → `notifier.sendVerificationEmail(email, token)`

**컨트롤러 엔드포인트 추가 (`AccountController` 또는 신규)**
- `POST /api/accounts/signup/verify-email` (auth 없음)
- `POST /api/accounts/signup/resend-verification-email` (`X-Account-Id` 헤더)

**예외 클래스**
- `EmailVerificationTokenInvalidException` (application/exception) → 400
- `EmailAlreadyVerifiedException` (application/exception) → 409

**`AccountJpaEntity` 수정**
- `email_verified_at` 컬럼 매핑 추가

### Out
- 이메일 미인증 계정에 대한 기능 제한 (비차단 설계)
- 실제 SMTP/SES 구현체 (Slf4j stub으로 대체)
- 관리자(admin-service)의 이메일 인증 상태 조회 API
- 인증 토큰을 이메일 URL에 포함하는 템플릿 렌더링

## Acceptance Criteria

1. `POST /api/accounts/signup/verify-email`에 유효한 토큰 전달 시 200, `emailVerifiedAt` 반환.
2. 만료/존재하지 않는 토큰 사용 시 400 `TOKEN_EXPIRED_OR_INVALID`.
3. 이미 인증된 계정에 재인증 시도 시 409 `EMAIL_ALREADY_VERIFIED`.
4. `POST /api/accounts/signup/resend-verification-email` 호출 시 204 반환, 새 토큰이 Redis에 저장.
5. 5분 내 재발송 재시도 시 429 `RATE_LIMITED`.
6. `V0008__add_email_verified_at.sql` Flyway migration이 account-service에 추가된다.
7. `Account.emailVerifiedAt` 필드가 JPA 엔티티에 매핑된다.
8. `Slf4jEmailVerificationNotifier`가 R4 준수 로그만 출력한다 (토큰 미노출, 이메일 마스킹).
9. `./gradlew :apps:account-service:test` BUILD SUCCESSFUL.

## Related Specs

- `specs/features/signup.md` — "email verification optional in initial scope" (이 태스크에서 구현)
- `specs/services/account-service/architecture.md` — 4레이어, port/adapter, domain 순수성
- `rules/traits/regulated.md` — R4: 이메일(PII), 토큰(시크릿) 로그 금지

## Related Skills

- `.claude/skills/INDEX.md` 참조

## Related Contracts

- `specs/contracts/http/account-api.md` — 이 태스크에서 먼저 수정

## Target Service

account-service

## Architecture

`specs/services/account-service/architecture.md` 참조.  
`EmailVerificationTokenStore` interface는 `domain/repository/`, Redis 구현은 `infrastructure/redis/`.  
`EmailVerificationNotifier` port는 `application/port/`, Slf4j stub은 `infrastructure/email/`.

## Implementation Notes

- `EmailVerificationTokenStore` / `RedisEmailVerificationTokenStore` — BE-108의 `PasswordResetTokenStore` / `RedisPasswordResetTokenStore` 패턴을 그대로 따른다
- `Slf4jEmailVerificationNotifier` — BE-111에서 수정된 `Slf4jEmailSender.maskedEmail()` 패턴을 동일하게 적용: 토큰 출력 금지, 이메일 마스킹
- `Account.verifyEmail(now)` — 최초 인증만 허용 (`emailVerifiedAt == null` guard)
- 토큰 삭제는 `verifyEmail` 이후 마지막에 수행 (실패 시 재시도 가능)
- `SendVerificationEmailUseCase`의 레이트 리밋: Redis key `"email-verify:rate:{accountId}"`, TTL 300초 (5분), `setIfAbsent`로 구현
- 토큰 TTL 24시간 = `Duration.ofHours(24)` (상수로 선언)
- `Account.create()`는 기존 유지 — `emailVerifiedAt = null`로 생성됨

## Edge Cases

- 레이트 리밋 Redis 장애 시: fail-open (재발송 허용) — 서비스 가용성 우선
- `tokenStore.delete(token)` 실패 시: `verifyEmail`은 이미 commit → 로그 경고만 남기고 무시
- `account`가 DELETED 상태에서 인증 요청: account 조회 시 empty or 도메인 예외 → 400

## Failure Scenarios

- Redis 장애 (토큰 저장 불가): `SendVerificationEmailUseCase`에서 예외 전파 → 503
- DB 저장 실패 (`verifyEmail` 이후 save): 트랜잭션 롤백, 토큰 미삭제 → 재시도 가능

## Test Requirements

### 단위 테스트
- `VerifyEmailUseCaseTest`: 정상 인증, 토큰 미존재, 이미 인증
- `SendVerificationEmailUseCaseTest`: 정상 발송, 이미 인증, 레이트 리밋

### 컨트롤러 테스트 (`@WebMvcTest`)
- `EmailVerificationControllerTest`: 200, 400, 409, 204, 429 케이스

### 통합 테스트 (선택적)
- `EmailVerificationIntegrationTest` (Testcontainers): 토큰 저장 → 인증 → DB 반영 전체 플로우

## Definition of Done

- [ ] `specs/contracts/http/account-api.md` 계약 추가
- [ ] `V0008__add_email_verified_at.sql` migration 추가
- [ ] `Account.verifyEmail()` 및 `emailVerifiedAt` 필드 추가
- [ ] `EmailVerificationTokenStore` + Redis 구현 추가
- [ ] `EmailVerificationNotifier` port + Slf4j stub 추가 (R4 준수)
- [ ] `VerifyEmailUseCase`, `SendVerificationEmailUseCase` 구현
- [ ] 컨트롤러 엔드포인트 2개 동작
- [ ] 단위 + 컨트롤러 테스트 통과
- [ ] `./gradlew :apps:account-service:test` BUILD SUCCESSFUL
- [ ] 코드 리뷰 통과
- [ ] `tasks/review/`로 이동 완료
