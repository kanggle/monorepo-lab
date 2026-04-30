# Feature: Signup (회원가입)

## Purpose

새 사용자가 플랫폼에 계정을 등록하는 흐름. 계정 생성, 프로필 초기화, credential 저장, 이벤트 발행까지의 end-to-end.

## Related Services

| Service | Role |
|---|---|
| account-service | 계정·프로필 생성 소유. 이메일 중복 검사, 상태 ACTIVE 초기화 |
| auth-service | credential(패스워드 해시) 저장. 가입 후 즉시 로그인 가능 |
| gateway-service | `POST /api/accounts/signup` 라우팅, rate limit |

## User Flow

1. 사용자가 `POST /api/accounts/signup` 에 이메일·패스워드·(선택)프로필 정보 전송
2. gateway가 가입 rate limit 검사 (IP당 5회/분)
3. account-service가 이메일 중복 검사 (`accounts.email` unique index)
4. 중복 시 409 `ACCOUNT_ALREADY_EXISTS`
5. 패스워드 복잡도 검증 (PasswordPolicy: 최소 8자, 대소문자+숫자+특수문자 중 3종 이상)
6. `accounts` row 생성 (status=ACTIVE) + `profiles` row 생성
7. auth-service에 credential 생성 요청: 패스워드를 argon2id 해시 후 `credentials` 저장
8. `account.created` 이벤트 발행 (outbox)
9. 응답 201: `{ accountId, email, status, createdAt }`
10. (선택, 미래) 이메일 검증 코드 발송 → Redis `signup:email-verify:{token}` TTL 24h

**credential 생성의 서비스 간 조율**:
- 방법 A (동기): account-service가 내부 HTTP로 auth-service에 credential 생성 요청. 실패 시 계정 생성도 롤백.
- 방법 B (Saga): account.created 이벤트 발행 → auth-service가 소비하여 credential 생성. 실패 시 보상 트랜잭션.
- **초기 구현: 방법 A** (단순, 골든패스 우선). Saga는 백로그.

## Business Rules

- 이메일: RFC 5322 형식, 대소문자 무시 (저장 시 lowercase 정규화)
- 패스워드: 최소 8자, 3종 이상 조합. auth-service의 `PasswordPolicy` 도메인 객체가 검증
- 중복 가입 방어: `signup:dedup:{email_hash}` Redis 5분 TTL (리로드 공격) + DB unique constraint (최종 방어)
- 가입 직후 상태: `ACTIVE`
- 이메일 검증: 초기 스코프에서 선택사항 (검증 없이 가입 완료 가능). 검증 필수화는 백로그

## Edge Cases

- 동시 중복 가입 → 두 번째 요청이 DB unique constraint에 걸림 → 409
- 패스워드가 이메일과 동일 → 422 VALIDATION_ERROR (PasswordPolicy에서 거부)
- 한글 이메일 도메인 (IDN) → 초기 스코프 미지원. ASCII만

## Related Contracts

- HTTP: [account-api.md](../contracts/http/account-api.md) `POST /api/accounts/signup`
- Events: [account-events.md](../contracts/events/account-events.md) `account.created`
