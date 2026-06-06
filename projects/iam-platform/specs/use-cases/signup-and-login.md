# Use Case: Signup and Login

---

## UC-1: 이메일 회원가입

### Actor
- 미인증 사용자

### Precondition
- 해당 이메일로 가입된 계정이 없음

### Main Flow
1. 사용자가 이메일·패스워드·(선택)프로필 정보를 입력한다
2. gateway가 signup rate limit을 확인한다 (IP당 5회/분)
3. account-service가 이메일 중복을 검사한다
4. account-service가 패스워드 복잡도를 검증한다 (PasswordPolicy)
5. account-service가 `accounts` + `profiles` row를 생성한다 (status=ACTIVE)
6. account-service가 auth-service에 credential 생성을 요청한다 (내부 HTTP)
7. auth-service가 패스워드를 argon2id 해시하여 `credentials` 테이블에 저장한다
8. account-service가 `account.created` 이벤트를 발행한다 (outbox)
9. 사용자에게 201 응답: `{ accountId, email, status, createdAt }`

### Alternative Flow
- **AF-1**: 이메일 검증 활성화 시, 가입 후 검증 이메일 발송. 검증 완료까지 일부 기능 제한 (미래 스코프)

### Exception Flow
- **EF-1**: 이메일 중복 → 409 `ACCOUNT_ALREADY_EXISTS`
- **EF-2**: 패스워드 복잡도 미달 → 422 `VALIDATION_ERROR`
- **EF-3**: rate limit 초과 → 429 `RATE_LIMITED`
- **EF-4**: auth-service credential 생성 실패 → account 생성 롤백 → 500 `INTERNAL_ERROR`
- **EF-5**: 동시 중복 요청 → DB unique constraint → 409

---

## UC-2: 이메일·패스워드 로그인

### Actor
- 미인증 사용자 (가입 완료)

### Precondition
- 계정이 존재하고 status=ACTIVE

### Main Flow
1. 사용자가 이메일·패스워드를 입력한다
2. gateway가 login rate limit을 확인한다 (서브넷당 20회/분)
3. auth-service가 account-service에 credential lookup을 요청한다
4. auth-service가 계정 상태를 확인한다 (ACTIVE만 허용)
5. auth-service가 argon2id 해시를 비교한다
6. 성공: access token(30분) + refresh token(7일) 발급
7. auth-service가 Redis 실패 카운터를 삭제한다
8. auth-service가 `auth.login.succeeded` + `auth.login.attempted` 이벤트를 발행한다
9. 사용자에게 200: `{ accessToken, refreshToken, expiresIn, tokenType }`

### Alternative Flow
- **AF-1**: hash_algorithm이 구버전이면 로그인 성공 후 새 알고리즘으로 rehash (lazy migration)

### Exception Flow
- **EF-1**: 이메일 미존재 → 401 `CREDENTIALS_INVALID` (이메일 존재 여부 미노출)
- **EF-2**: 패스워드 불일치 → 401 `CREDENTIALS_INVALID` + 실패 카운터 증가
- **EF-3**: 실패 카운터 5회 초과 → 429 `LOGIN_RATE_LIMITED`
- **EF-4**: 계정 LOCKED → 403 `ACCOUNT_LOCKED`
- **EF-5**: 계정 DORMANT → 403 `ACCOUNT_DORMANT`
- **EF-6**: 계정 DELETED → 403 `ACCOUNT_DELETED`
- **EF-7**: account-service 장애 → 503 `SERVICE_UNAVAILABLE`

---

## Related Contracts
- HTTP: [auth-api.md](../contracts/http/auth-api.md), [account-api.md](../contracts/http/account-api.md)
- Internal: [auth-to-account.md](../contracts/http/internal/auth-to-account.md)
- Events: [auth-events.md](../contracts/events/auth-events.md), [account-events.md](../contracts/events/account-events.md)
