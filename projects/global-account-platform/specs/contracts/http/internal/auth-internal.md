# Internal HTTP Contract: account-service → auth-service

TASK-BE-063 Option A. 신규 계정이 저장된 직후, account-service 가 auth-service 에 자격증명(credential)을 생성하도록 요청한다.

**호출 방향**: account-service (client) → auth-service (server)
**노출 경로**: `/internal/auth/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../rules/domains/saas.md) S2). 내부 네트워크 외부로 나가선 안 된다.
**인증**: 내부 네트워크 경계 전제. 향후 X-Internal-Token 또는 mTLS 추가 예정 — 현 시점의 auth-service `SecurityConfig` 는 `/internal/**` 를 `permitAll()` 로 두고 있다 (TASK 별도).

---

## POST /internal/auth/credentials

계정 생성 시점에 호출되는 단방향 쓰기 엔드포인트. auth-service 가 argon2id 해시를 수행하고 `auth_db.credentials` 에 row 를 삽입한다. **plaintext password 는 절대 저장·로그되지 않는다.**

**Request Body**:

```json
{
  "accountId": "string (UUID v7, max 36)",
  "email": "string (RFC 5322)",
  "password": "string (min 8)"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `accountId` | string | Yes | 방금 생성된 계정의 UUID. `auth_db.credentials.account_id` 와 일치해야 함 |
| `email` | string | Yes | 로그인 lookup key. lower-case 정규화 후 저장 |
| `password` | string | Yes | 평문. auth-service 가 argon2id 해시 후 `credentials.credential_hash` 에 저장. **caller 는 반드시 HTTPS 내부망으로만 전송할 것** |

**Response 201 Created**:

```json
{
  "accountId": "string",
  "createdAt": "2026-04-19T04:00:00Z"
}
```

**Response 409 Conflict** — 동일 `account_id` 또는 `email` 이 이미 존재:

```json
{
  "code": "CREDENTIAL_ALREADY_EXISTS",
  "message": "Credential already exists for accountId=...",
  "timestamp": "2026-04-19T04:00:00Z"
}
```

동시성 대응: 선행 `existsByAccountId` 조회로 1차 차단하고, unique 제약 위반 시 2차로 409 로 변환한다 (DataIntegrityViolationException → 409).

**Response 400 Bad Request** — validation 실패 (email 형식, password 최소 길이 등). 게이트웨이/경계 validator 가 잡지 못한 케이스.

**Response 5xx / 타임아웃**: caller 는 **fail-closed** 로 처리한다. 즉 account-service 의 `SignupUseCase` 는 `@Transactional` 롤백으로 계정·프로필 row 를 폐기한다.

---

## Caller Constraints (account-service 측)

- 타임아웃: connect 3s, read 5s
- 재시도: 2회 (지수 백오프 + jitter). **4xx 는 재시도 금지**
- Circuit breaker: 실패율 50% / 10초 window → open → 10초 half-open
- 409 는 caller 가 `AccountAlreadyExistsException` 으로 변환 (동시 signup 경합 시 일관된 409 응답)
- 5xx / timeout / circuit-open 은 `AuthServiceUnavailable` 로 승격되어 signup 전체가 롤백되고 호출자에게 전파됨

## Server Constraints (auth-service 측)

- `credential_hash` 는 argon2id (m=65536, t=3, p=1)
- `email` 은 lower-case 정규화 후 유니크 제약 `credentials.email` 에 저장 (V0006 마이그레이션)
- `account_id` 컬럼도 유니크. 둘 중 어느 쪽이든 충돌하면 409
- 로그·감사에 `password`, `credential_hash` 기록 금지 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4). 분류 등급 **restricted**

---

## 단기 방어 (short-circuit)

auth-service `LoginUseCase` 는 credential lookup 이 null 을 반환하면 NPE 대신 `CredentialsInvalidException` 을 던진다. 이는 본 태스크가 끝난 이후에도 fail-safe 로 유지한다 — 방어 제거는 contract·DB 상태의 일관성이 충분히 검증된 후.
