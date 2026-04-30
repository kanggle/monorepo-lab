# Event Contract: security-service

security-service가 발행하는 Kafka 이벤트. 비정상 탐지 결과 및 자동 잠금 트리거.

**발행 방식**: Outbox 패턴 (T3)
**파티션 키**: `account_id`

---

## Event Envelope

[auth-events.md](auth-events.md)와 동일한 표준 envelope.

---

## security.suspicious.detected

비정상 활동 탐지 시 발행. 자동 잠금 여부와 관계없이 모든 탐지에 대해 발행.

**Topic**: `security.suspicious.detected`

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID)",
  "accountId": "string",
  "ruleCode": "VELOCITY | GEO_ANOMALY | DEVICE_CHANGE | TOKEN_REUSE",
  "riskScore": 85,
  "actionTaken": "AUTO_LOCK | ALERT | NONE",
  "evidence": {
    "description": "string (rule-specific context)",
    "threshold": "10 failures/hour",
    "actual": "15 failures in last 45 minutes"
  },
  "triggerEventId": "string (원인 이벤트의 eventId)",
  "detectedAt": "2026-04-12T10:00:00Z"
}
```

**Consumers**: 관측성·알림 시스템 (Grafana alerting, SIEM). 현재 플랫폼 내 직접 소비자는 없으나 외부 연동 대비.

---

## security.auto.lock.triggered

자동 잠금 명령이 account-service로 발행되었음을 기록하는 이벤트. **실제 잠금은 HTTP로 수행** — 이 이벤트는 감사·관측성 용도.

**Topic**: `security.auto.lock.triggered`

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID, Idempotency-Key으로도 사용)",
  "accountId": "string",
  "ruleCode": "string",
  "riskScore": 90,
  "lockRequestResult": "SUCCESS | FAILURE | ALREADY_LOCKED",
  "lockRequestedAt": "2026-04-12T10:00:01Z"
}
```

**Consumers**: admin-service (감사 대시보드에서 자동 잠금 이력 표시), 관측성 시스템

---

## security.auto.lock.pending

account-service로의 HTTP 자동 잠금 요청이 재시도 예산 소진 후에도 모두 실패했을 때 발행.
**실제 잠금은 수행되지 않은 상태** — 운영자가 수동으로 개입해야 함을 알리는 이벤트.

**Topic**: `security.auto.lock.pending`

**발행 조건**: `IssueAutoLockCommandUseCase`에서 account-service 호출이 모든 재시도 후 실패한 경우

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID, 원인 SuspiciousEvent ID)",
  "accountId": "string",
  "ruleCode": "string",
  "riskScore": 90,
  "reason": "ACCOUNT_SERVICE_UNREACHABLE",
  "raisedAt": "2026-04-12T10:00:05Z"
}
```

**Consumers**: admin-service (운영자 수동 개입 큐 — 대시보드 알림 및 수동 잠금 처리)

---

## Consumer Rules

- 이 서비스의 이벤트는 현재 **내부 플랫폼에서는 관측성 소비만** (메트릭, 알림, 대시보드)
- 외부 SIEM이 Kafka 토픽을 직접 구독하는 경우를 고려하여 표준 envelope 유지
- `evidence` 필드에 PII 포함 금지 (IP는 마스킹, 이메일 미포함)
- forward-compatible: 새 `ruleCode` 추가 시 기존 consumer는 알 수 없는 코드를 무시
