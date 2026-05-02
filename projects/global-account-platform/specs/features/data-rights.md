# Feature: Data Rights (GDPR/PIPA)

## Purpose

GDPR/PIPA 컴플라이언스를 위한 데이터 권리 기능. 삭제권(Right to Erasure)과 이식권(Right to Data Portability)을 구현한다.

---

## Right to Erasure (삭제권)

계정 소유자 또는 운영자가 계정 삭제를 요청하면 PII를 마스킹하여 개인 식별이 불가능하도록 처리한다.

### Flow

1. 운영자가 GDPR 삭제 요청 (POST /api/admin/accounts/{accountId}/gdpr-delete)
2. admin-service가 account-service 내부 엔드포인트 호출
3. account-service가 다음을 수행:
   - 계정 상태를 DELETED로 전이 (AccountStatusMachine 경유)
   - 이메일을 SHA-256 해시로 교체
   - 프로필 PII 필드(displayName, phoneNumber, birthDate) NULL 처리
   - deleted_at, masked_at 타임스탬프 기록
   - account.deleted 이벤트 발행 (outbox, anonymized=true)
4. admin-service가 admin_actions 감사 기록

### PII Masking Strategy

| Field | Before | After |
|---|---|---|
| email | user@example.com | gdpr_a1b2c3d4e5...@deleted.local |
| displayName | John Doe | NULL |
| phoneNumber | +82-10-1234-5678 | NULL |
| birthDate | 1990-01-15 | NULL |

- 이메일 해시는 SHA-256 full hex (64자)로 생성하여 `email_hash` 컬럼에 보관
- 해시된 이메일은 원복 불가능하되, 동일 이메일 재가입 방지 검사에 사용 가능
- 계정 row는 물리적으로 삭제하지 않음 (감사 추적 유지)

---

## Right to Data Portability (이식권)

계정 소유자 또는 운영자가 개인 데이터 전체를 JSON 형태로 내보낸다.

### Export Scope

| Data Category | Source | Fields |
|---|---|---|
| Account | accounts | accountId, email, status, createdAt |
| Profile | profiles | displayName, phoneNumber, birthDate, locale, timezone |

- 로그인 이력 등 추가 데이터는 auth-service/security-service 연동 시 확장 가능
- 현재 scope에서는 account-service가 소유한 데이터만 포함

### Export Format

```json
{
  "accountId": "string",
  "email": "string",
  "status": "string",
  "createdAt": "ISO 8601",
  "profile": {
    "displayName": "string",
    "phoneNumber": "string",
    "birthDate": "YYYY-MM-DD",
    "locale": "string",
    "timezone": "string"
  },
  "exportedAt": "ISO 8601"
}
```

---

## Data Retention Policy

- 삭제된 계정의 row는 감사 목적으로 영구 보존 (PII는 마스킹됨)
- email_hash는 재가입 방지 목적으로 보존
- 마스킹 처리는 GDPR 삭제 요청 시 즉시 수행 (유예 기간 없음 - 기존 deleteAccount의 유예 경로와 별도)

---

## Downstream PII Masking Obligations (TASK-BE-258)

account-service의 PII 마스킹은 계정 row에 한정된다. `account.deleted(anonymized=true)` 이벤트를 수신한 **다운스트림 서비스**도 각자 보유한 PII를 마스킹할 의무가 있다.

소비자별 의무와 SLA는 [account-events.md § Consumer Obligations](../contracts/events/account-events.md#consumer-obligations-task-be-258) 참조.

security-service의 reference 구현: `account.deleted(anonymized=true)` 수신 → `login_history`, `suspicious_events`, `account_lock_history` PII 마스킹 → `security.pii.masked` audit 이벤트 발행.
`security.pii.masked` 이벤트 정의: [security-events.md § security.pii.masked](../contracts/events/security-events.md#securitypiimasked-task-be-258).

## Related Specs

- [specs/services/account-service/architecture.md](../services/account-service/architecture.md)
- [specs/services/admin-service/architecture.md](../services/admin-service/architecture.md)
- [specs/contracts/http/admin-api.md](../contracts/http/admin-api.md)
- [specs/contracts/http/internal/admin-to-account.md](../contracts/http/internal/admin-to-account.md)
- [specs/contracts/events/account-events.md § Consumer Obligations](../contracts/events/account-events.md#consumer-obligations-task-be-258)
- [specs/contracts/events/security-events.md § security.pii.masked](../contracts/events/security-events.md#securitypiimasked-task-be-258)

## Related Rules

- rules/traits/regulated.md R7 (PII 익명화)
- rules/traits/audit-heavy.md A1/A10 (감사 기록 필수)
