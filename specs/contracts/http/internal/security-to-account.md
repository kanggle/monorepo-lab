# Internal HTTP Contract: security-service → account-service

security-service가 비정상 로그인 탐지 결과에 따라 account-service에 자동 잠금 명령을 발행한다.

**호출 방향**: security-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*`
**인증**: `X-Internal-Token` 헤더 — security-service 의 `INTERNAL_SERVICE_TOKEN` (`security-service.internal-token`) 과 account-service 의 `INTERNAL_API_TOKEN` 이 동일 값으로 운영 secret store 에서 주입돼야 한다. 토큰 미설정 시 account-service 가 401 `UNAUTHORIZED` 로 fail-closed (TASK-BE-142).

---

## POST /internal/accounts/{accountId}/lock

계정 자동 잠금. security-service의 `IssueAutoLockCommandUseCase`가 suspicious 탐지 시 호출.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 잠금 대상 계정 |

**Headers**:
- `Idempotency-Key: {suspicious_event_id}` — 동일 탐지에 대한 중복 잠금 방지 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T1)

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
