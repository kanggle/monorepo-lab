# Internal HTTP Contract: security-service → account-service

security-service가 비정상 로그인 탐지 결과에 따라 account-service에 자동 잠금 명령을 발행한다.

**호출 방향**: security-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*`
**인증** (TASK-BE-318 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — security-service 가 `security-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 정적 `X-Internal-Token` 은 제거됨. JWT 미제시/무효 시 account-service 가 401 `UNAUTHORIZED` 로 fail-closed.

---

## POST /internal/accounts/{accountId}/lock

계정 자동 잠금. security-service의 `IssueAutoLockCommandUseCase`가 suspicious 탐지 시 호출.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 잠금 대상 계정 |

**Headers**:
- `Idempotency-Key: {suspicious_event_id}` — 동일 탐지에 대한 중복 잠금 방지 ([rules/traits/transactional.md](../../../../../../rules/traits/transactional.md) T1)
- `X-Tenant-Id: {suspicious_event.tenant_id}` (TASK-MONO-735) — 탐지를 일으킨 이벤트의 테넌트. security-service 는 **항상** 싣는다(`SuspiciousEvent` 가 non-blank 를 강제). account-service 는 그 테넌트로만 찾는다 — 계정이 다른 테넌트에 있으면 `404 ACCOUNT_NOT_FOUND` 이고 **잠그지 않는다**(틀린 id 가 남의 계정을 잠그지 않게 하는 심층 방어). 헤더가 없거나 `*` 이면 account-service 는 계정 행의 테넌트로 찾는다([admin-to-account.md § Tenant Confinement](admin-to-account.md#tenant-confinement--x-tenant-id-task-be-467)). 🔴 MONO-735 이전 security-service 는 이 헤더를 싣지 않았고 account-service 는 `fan-platform` 으로 찾았다 ⇒ `fan-platform` 밖 계정의 자동 잠금은 404 였다(2026-09-26 16차 창 실측).

**Request**:
```json
{
  "reason": "AUTO_DETECT",
  "ruleCode": "GEO_ANOMALY | VELOCITY | DEVICE_CHANGE | TOKEN_REUSE",
  "riskScore": 85,
  "suspiciousEventId": "string (UUID)",
  "detectedAt": "2026-04-12T10:00:00Z"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "lockedAt": "2026-04-12T10:00:01Z"
}
```

**Response 200 (이미 LOCKED — 멱등 응답)**:
```json
{
  "accountId": "string",
  "previousStatus": "LOCKED",
  "currentStatus": "LOCKED",
  "lockedAt": "2026-04-10T08:00:00Z"
}
```

**Response 409 (이미 DELETED)**:
```json
{
  "code": "STATE_TRANSITION_INVALID",
  "message": "Cannot lock a deleted account",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**Response 404 `ACCOUNT_NOT_FOUND`**: `X-Tenant-Id` 의 테넌트에 그 id 의 계정이 없다(존재하지 않거나 다른 테넌트). 재시도 금지(4xx) — security-service 는 `Auto-lock non-retryable 4xx` 로 기록하고 FAILURE 로 끝낸다.

---

## Caller Constraints (security-service 측)

- 타임아웃: 연결 3s, 읽기 10s
- 재시도: 3회 (지수 백오프 + jitter). **409는 재시도 금지** (상태 전이 불가는 재시도해도 변하지 않음)
- 최종 실패 시: outbox에 `auto.lock.pending` 이벤트로 기록 → 운영자 수동 개입
- Idempotency-Key 필수: 같은 suspicious_event_id로 재호출 시 account-service는 동일 결과 반환 (T1)

---

## Server Constraints (account-service 측)

- `AccountStatusMachine.transition(current, LOCKED, AUTO_DETECT)` 경유 — 직접 UPDATE 금지
- `account_status_history`에 `actor_type=system`, `reason_code=AUTO_DETECT`, `details={ruleCode, riskScore, suspiciousEventId}` 기록
- `account.locked` 이벤트 발행 (outbox)
- Idempotency-Key 중복 체크: 24시간 TTL의 dedupe 테이블 또는 Redis
