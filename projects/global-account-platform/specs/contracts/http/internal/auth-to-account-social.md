# Internal HTTP Contract: auth-service → account-service (Social Signup)

auth-service가 OAuth 소셜 로그인 처리 중 account-service를 호출하여 소셜 계정을 생성하거나 기존 계정을 조회한다.

**호출 방향**: auth-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../rules/domains/saas.md) S2)
**인증**: mTLS 또는 내부 서비스 토큰 (기존 내부 API와 동일)

---

## POST /internal/accounts/social-signup

소셜 로그인 시 계정 자동 생성 또는 기존 계정 연결. 이메일이 이미 존재하면 기존 계정 정보를 반환하고, 존재하지 않으면 새 계정을 생성한다.

**Request**:
```json
{
  "email": "string (required, provider로부터 획득한 이메일)",
  "provider": "string (required, 'GOOGLE' | 'KAKAO' | 'MICROSOFT')",
  "providerUserId": "string (required, provider의 고유 사용자 식별자)",
  "displayName": "string (optional, provider profile에서 획득한 표시 이름)"
}
```

**Response 201** (신규 계정 생성):
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE"
}
```

**Response 200** (기존 이메일 계정 존재 — 자동 연결):
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED"
}
```

**동작 상세**:
- 요청 이메일로 `accounts` 테이블 조회
- 이메일 미존재 → 새 계정 생성 (status=ACTIVE, displayName을 프로필에 설정) → 201
- 이메일 존재 → 기존 accountId + 현재 status 반환 → 200
- 신규 계정 생성 시 `account.created` 이벤트 발행 (outbox)
- **credential(패스워드 해시)은 생성하지 않음** — 소셜 전용 계정은 패스워드 없이 존재 가능

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 422 | `VALIDATION_ERROR` | 필수 필드 누락 또는 형식 오류 |

**주의**: 반환된 `status`가 ACTIVE가 아닌 경우, auth-service가 로그인을 거부해야 한다 (caller 책임). account-service는 상태 검증 없이 조회 결과를 반환한다.

---

## Caller Constraints (auth-service 측)

- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프 + jitter). **4xx는 재시도 금지**
- Circuit breaker: 실패율 50% / 10초 → open → 30초 half-open
- account-service 장애 시 **소셜 로그인 불가** (fail-closed)
