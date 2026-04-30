# Internal HTTP Contract: admin-service → auth-service

admin-service가 운영자 명령으로 auth-service에 강제 로그아웃 / refresh token revoke를 요청한다.

**호출 방향**: admin-service (client) → auth-service (server)
**노출 경로**: `/internal/auth/*`
**인증**: mTLS 또는 내부 서비스 토큰

---

## POST /internal/auth/accounts/{accountId}/force-logout

특정 계정의 모든 세션 강제 종료. 해당 account의 모든 refresh_tokens를 revoke하고 `refresh:invalidate-all:{account_id}` Redis 키를 설정한다.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 대상 계정 |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}` (필수)
- `X-Operator-ID: {operator_id}` (감사 추적용)

**Request**:
```json
{
  "reason": "string (운영자 사유)",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "revokedTokenCount": 5,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 200 (이미 revoke됨 — 멱등)**:
```json
{
  "accountId": "string",
  "revokedTokenCount": 0,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**:
```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "No refresh tokens found for this account",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**Side Effects**:
- `refresh_tokens` 테이블에서 해당 account_id의 모든 row `revoked=TRUE`
- `refresh:invalidate-all:{account_id}` Redis SET (TTL = refresh token 최대 수명)
- `auth.token.refreshed` 이벤트 발행하지 않음 (이것은 정상 rotation이 아닌 강제 revoke)

---

## POST /internal/auth/sessions/{jti}/revoke

특정 세션(refresh token) 하나만 revoke.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `jti` | string (UUID) | 대상 refresh token의 JWT ID |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}`
- `X-Operator-ID: {operator_id}`

**Request**:
```json
{
  "reason": "string",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "jti": "string",
  "accountId": "string",
  "revoked": true,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**: 해당 jti의 refresh token 미존재

---

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s
- 재시도: 2회 (지수 백오프). 404는 재시도 금지
- Circuit breaker: auth-service 장애 시 open → admin에게 502 반환
- Idempotency-Key 필수: 같은 request_id로 재시도 시 멱등 응답
