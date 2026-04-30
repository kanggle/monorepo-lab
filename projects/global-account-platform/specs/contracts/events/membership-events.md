# Event Contract: membership-service

발행 방식: Outbox 패턴 ([libs/java-messaging](../../../libs/java-messaging))
파티션 키: `account_id`

---

## membership.subscription.activated

구독이 ACTIVE 상태로 활성화될 때 발행.

**Topic**: `membership.subscription.activated`

**Payload**:
```json
{
  "subscriptionId": "string (UUID)",
  "accountId": "string (UUID)",
  "planLevel": "FAN_CLUB",
  "startedAt": "2026-04-13T12:00:00Z",
  "expiresAt": "2026-05-13T12:00:00Z"
}
```

**Consumers**: notification-service (향후 — 구독 확정 알림)

---

## membership.subscription.expired

구독이 만료 스케줄러에 의해 EXPIRED 상태로 전이될 때 발행.

**Topic**: `membership.subscription.expired`

**Payload**:
```json
{
  "subscriptionId": "string (UUID)",
  "accountId": "string (UUID)",
  "planLevel": "FAN_CLUB",
  "expiredAt": "2026-05-13T12:00:00Z"
}
```

**Consumers**: notification-service (향후 — 만료 안내 알림)

---

## membership.subscription.cancelled

사용자가 구독을 해지할 때 발행.

**Topic**: `membership.subscription.cancelled`

**Payload**:
```json
{
  "subscriptionId": "string (UUID)",
  "accountId": "string (UUID)",
  "planLevel": "FAN_CLUB",
  "cancelledAt": "2026-04-20T09:00:00Z"
}
```

**Consumers**: notification-service (향후 — 해지 확인 알림)

---

## Consumer Rules

- 멱등 처리 (`eventId` dedup)
- 스키마 포워드 호환
- DLQ: `membership.subscription.activated.dlq`, `membership.subscription.expired.dlq`, `membership.subscription.cancelled.dlq` (3회 재시도)
- W3C Trace Context 헤더 전파
