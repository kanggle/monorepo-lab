# Feature: Login History

## Purpose

모든 로그인 시도(성공·실패·rate-limited·token-reuse)를 불변 이력으로 기록하여 감사·분석·사용자 자기 조회의 기반을 제공한다.

## Related Services

| Service | Role |
|---|---|
| auth-service | 로그인 이벤트 발행 (원본 데이터) |
| security-service | 이벤트 소비 → `login_history` 테이블 적재 소유 |
| admin-service | 감사 조회 프록시 (security-service query 호출) |

## Data Flow

```
auth-service → [Kafka: auth.login.*] → security-service → MySQL login_history
                                                              ↓
                                               admin-service ← GET /internal/security/login-history
```

## Recorded Fields

| 필드 | 소스 | 분류 등급 |
|---|---|---|
| event_id | auth 이벤트 eventId | internal |
| account_id | auth 이벤트 payload | internal |
| outcome | SUCCESS / FAILURE / RATE_LIMITED / TOKEN_REUSE / REFRESH | internal |
| ip_masked | 이벤트 payload (마지막 옥텟 마스킹) | confidential |
| user_agent_family | UA 파싱 (Chrome 120, Safari 17 등) | internal |
| device_fingerprint | 해시 (SHA256 truncated) | confidential |
| geo_country | GeoIP lookup (MaxMind) | internal |
| occurred_at | 이벤트 occurredAt (UTC) | internal |

## Business Rules

- `login_history` 테이블은 **append-only**. UPDATE/DELETE 금지 (A3)
- 보존 기간: **최소 1년** (A4). 1년 초과 데이터는 아카이브 또는 cold storage 이관 (미래)
- 이벤트 소비는 **멱등** (eventId dedup, T8)
- PII 마스킹: IP 마지막 옥텟 `***`, device fingerprint 해시 truncate. 이메일은 **기록하지 않음**
- 조회 API 응답에서도 동일 마스킹 적용 (R4)

## Related Contracts

- Events: [auth-events.md](../contracts/events/auth-events.md) (소비)
- HTTP: [security-query-api.md](../contracts/http/security-query-api.md) (조회)
