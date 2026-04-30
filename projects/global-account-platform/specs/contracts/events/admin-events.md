# Event Contract: admin-service

admin-service가 발행하는 Kafka 이벤트. 모든 운영자 행위의 감사 이벤트.

**발행 방식**: Outbox 패턴 (T3). 감사 기록과 같은 DB 트랜잭션에서 커밋 (A7 fail-closed)
**파티션 키**: `target_id` (대상 계정 기준)

---

## Event Envelope

[auth-events.md](auth-events.md)와 동일한 표준 envelope.

---

## admin.action.performed

운영자가 수행한 **모든** 행위에 대해 발행. 성공·실패 불문.

**Topic**: `admin.action.performed`

**Payload**:
```json
{
  "auditId": "string (admin_actions.id)",
  "actionCode": "ACCOUNT_LOCK | ACCOUNT_UNLOCK | ACCOUNT_DELETE | SESSION_REVOKE | AUDIT_QUERY",
  "actor": {
    "type": "operator",
    "id": "string (operator_id)",
    "role": "SUPER_ADMIN | ACCOUNT_ADMIN | AUDITOR"
  },
  "target": {
    "type": "account | session",
    "id": "string (accountId or jti)"
  },
  "reason": "string (운영자 사유)",
  "ticketId": "string | null",
  "outcome": "SUCCESS | FAILURE | DENIED",
  "failureDetail": "string | null (실패 시 downstream 에러 요약)",
  "startedAt": "2026-04-12T10:00:00Z",
  "completedAt": "2026-04-12T10:00:01Z"
}
```

**Consumers**:
- 외부 SIEM (Splunk, Datadog, ELK) — Kafka 토픽 직접 구독
- 관측성 시스템 — 메트릭·알림 (admin command 실패 spike 등)
- 컴플라이언스 리포팅 — 운영자 행위 통계

---

## Consumer Rules

- 이 이벤트는 **감사 원장의 비동기 복제본** 역할. admin_actions 테이블이 정본, 이벤트는 외부 전파용
- PII 포함 금지: `reason`에 이메일/전화 등 기재 금지 (운영자 교육 + 입력 validation)
- forward-compatible: 새 `actionCode` 추가 시 기존 consumer는 알 수 없는 코드를 로그하고 계속 처리
- DLQ: `admin.action.performed.dlq` (외부 SIEM consumer가 실패 시)
