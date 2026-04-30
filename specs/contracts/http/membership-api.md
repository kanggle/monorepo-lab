# HTTP Contract: membership-service (Public API)

Base path: `/api/membership`

---

## POST /api/membership/subscriptions

구독 활성화. 현 단계에서 결제는 stub (항상 성공).

**Auth required**: Yes (JWT Bearer)

**Request**:
```json
{
  "planLevel": "FAN_CLUB",
  "idempotencyKey": "string (UUID, required)"
}
```

**Response 201**:
```json
{
  "subscriptionId": "string (UUID)",
  "accountId": "string",
  "planLevel": "FAN_CLUB",
  "status": "ACTIVE",
  "startedAt": "2026-04-13T12:00:00Z",
  "expiresAt": "2026-05-13T12:00:00Z"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 409 | SUBSCRIPTION_ALREADY_ACTIVE | 동일 플랜 ACTIVE 구독 중복 |
| 409 | ACCOUNT_NOT_ELIGIBLE | 계정 LOCKED/DELETED |
| 422 | VALIDATION_ERROR | planLevel 유효하지 않음 |
| 503 | ACCOUNT_STATUS_UNAVAILABLE | account-service 장애 시 |

**Side Effects**:
- `subscription_status_history`에 NONE→ACTIVE 기록
- `membership.subscription.activated` 이벤트 발행 (outbox)

**Idempotency**: `idempotencyKey` 기반 24시간 dedup. 동일 key 재요청 시 원래 응답 반환 (201이 아닌 200).

---

## DELETE /api/membership/subscriptions/{subscriptionId}

구독 해지. 즉시 CANCELLED 전이 (잔여 기간 환불 없음, 현 단계).

**Auth required**: Yes (JWT Bearer)

**Path Parameters**:
| 파라미터 | 타입 | 설명 |
|---|---|---|
| subscriptionId | string (UUID) | 구독 식별자 |

**Response 204**: (본문 없음)

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 403 | PERMISSION_DENIED | 타인의 구독 해지 시도 |
| 404 | SUBSCRIPTION_NOT_FOUND | 미존재 구독 |
| 409 | SUBSCRIPTION_NOT_ACTIVE | EXPIRED 또는 이미 CANCELLED |

**Side Effects**:
- `subscription_status_history`에 ACTIVE→CANCELLED 기록
- `membership.subscription.cancelled` 이벤트 발행 (outbox)

---

## GET /api/membership/subscriptions/me

내 구독 상태 조회.

**Auth required**: Yes (JWT Bearer)

**Response 200**:
```json
{
  "accountId": "string",
  "subscriptions": [
    {
      "subscriptionId": "string",
      "planLevel": "FAN_CLUB",
      "status": "ACTIVE",
      "startedAt": "2026-04-13T12:00:00Z",
      "expiresAt": "2026-05-13T12:00:00Z",
      "cancelledAt": null
    }
  ],
  "activePlanLevel": "FAN_CLUB"
}
```

**참고**: `activePlanLevel` — 현재 ACTIVE 구독의 최고 플랜 레벨. 없으면 `"FREE"`.
