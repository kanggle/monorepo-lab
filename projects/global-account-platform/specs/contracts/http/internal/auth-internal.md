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

**Response 201 Created** — 신규 credential 행 삽입 성공:

```json
{
  "accountId": "string",
  "createdAt": "2026-04-19T04:00:00Z"
}
```

**Response 200 OK** — 멱등 재시도: 동일 `(accountId, email)` 조합의 credential 이 이미 존재하고 요청 페이로드가 동일한 계정 컨텍스트임이 확인된 경우 success 로 응답한다. 응답 바디는 201 과 동일한 형태이며 기존 행의 `createdAt` 을 반환한다.

```json
{
  "accountId": "string",
  "createdAt": "2026-04-19T04:00:00Z"
}
```

> **TASK-BE-247 멱등성 보장** — account-service 의 `@Transactional` 롤백 + auth-service 커밋 race(half-commit)가 재현될 때, 동일 signup 페이로드의 재시도가 200 을 수신하여 signup 을 정상 완료시킨다. 멱등 조건: `existingRow.accountId == request.accountId AND existingRow.email == request.email`. **password 는 비교하지 않는다** — argon2id 해시는 매 호출마다 달라지며 평문 비교는 보안 결함이다.

**Response 409 Conflict** — 충돌: 동일 `accountId` 에 다른 `email` 이 등록되어 있거나, 동일 `email` 에 다른 `accountId` 가 등록된 경우 — 시그니처 불일치 → 진성 중복:

```json
{
  "code": "CREDENTIAL_ALREADY_EXISTS",
  "message": "Credential already exists for this account",
  "timestamp": "2026-04-19T04:00:00Z"
}
```

동시성 대응: 선행 `existsByAccountId` 조회로 1차 차단하고, unique 제약 위반 시 2차로 409 로 변환한다 (DataIntegrityViolationException → 409). 멱등 경로에서는 기존 행과 `(accountId, email)` 이 일치하면 409 대신 200 을 반환한다.

**Response 400 Bad Request** — validation 실패 (email 형식, password 최소 길이 등). 게이트웨이/경계 validator 가 잡지 못한 케이스.

**Response 5xx / 타임아웃**: caller 는 **fail-closed** 로 처리한다. 즉 account-service 의 `SignupUseCase` 는 `@Transactional` 롤백으로 계정·프로필 row 를 폐기한다.

---

## Caller Constraints (account-service 측)

- 타임아웃: connect 3s, read 15s (TASK-BE-247: cold-start race 마스킹을 위해 5s → 15s 상향)
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
