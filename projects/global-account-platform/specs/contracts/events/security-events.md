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

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID)",
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
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

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID, Idempotency-Key으로도 사용)",
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
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

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**발행 조건**: `IssueAutoLockCommandUseCase`에서 account-service 호출이 모든 재시도 후 실패한 경우

**Payload**:
```json
{
  "suspiciousEventId": "string (UUID, 원인 SuspiciousEvent ID)",
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
  "ruleCode": "string",
  "riskScore": 90,
  "reason": "ACCOUNT_SERVICE_UNREACHABLE",
  "raisedAt": "2026-04-12T10:00:05Z"
}
```

**Consumers**: admin-service (운영자 수동 개입 큐 — 대시보드 알림 및 수동 잠금 처리)

---

## security.pii.masked (TASK-BE-258)

GDPR/PIPA 컴플라이언스 audit trail 이벤트. security-service가 `account.deleted(anonymized=true)` 수신 후 자체 PII 마스킹을 완료한 시점에 발행한다. 규제 기관 또는 사용자가 "내 PII가 모두 삭제되었는가" 질문 시 이 이벤트를 집계하여 증명한다.

**Topic**: `security.pii.masked`

**Schema version**: 1 (TASK-BE-258: 최초 정의)

**발행 조건**: `AccountDeletedAnonymizedConsumer`가 PII 마스킹 트랜잭션을 커밋한 직후, outbox 패턴으로 발행.

**Payload**:
```json
{
  "accountId": "string (UUID)",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "maskedAt": "2026-05-02T12:00:00Z",
  "tableNames": ["login_history", "suspicious_events", "account_lock_history"]
}
```

**필드 설명**:
- `accountId` — 마스킹된 계정의 ID. 감사 무결성을 위해 보존.
- `tenantId` — 테넌트 ID. 마스킹은 `(tenant_id, account_id)` 쌍으로 격리.
- `maskedAt` — 마스킹이 완료된 시각 (UTC ISO 8601).
- `tableNames` — 이번 마스킹 작업에서 UPDATE를 수행한 테이블 목록. 대상 row가 없는 테이블도 포함 (UPDATE 0 rows = 정상).

**Consumers**: 컴플라이언스 감사 시스템 (SIEM, 규제 보고). 현재 플랫폼 내 직접 소비자는 없으나 외부 감사 연동 대비.

**멱등성**: 동일 `eventId` 로 중복 발행 안 됨 (outbox pattern의 polled-publish 보장). consumer 측에서 `eventId` dedupe 적용 권장.

---

## Consumer Rules

- 이 서비스의 이벤트는 현재 **내부 플랫폼에서는 관측성 소비만** (메트릭, 알림, 대시보드)
- 외부 SIEM이 Kafka 토픽을 직접 구독하는 경우를 고려하여 표준 envelope 유지
- `evidence` 필드에 PII 포함 금지 (IP는 마스킹, 이메일 미포함)
- forward-compatible: 새 `ruleCode` 추가 시 기존 consumer는 알 수 없는 코드를 무시
- `security.pii.masked` 이벤트는 GDPR 증명 용도 — PII 자체는 포함하지 않으며 `accountId`와 `tenantId`만 식별자로 사용
