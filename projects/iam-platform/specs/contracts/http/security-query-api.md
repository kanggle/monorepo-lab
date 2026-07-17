# HTTP Contract: security-service (Read-Only Query API)

**내부 전용** — admin-service만 호출. 게이트웨이 퍼블릭 라우트에 노출되지 않음.

이 API는 security-service의 **좁은 read-only HTTP 표면**이다 ([architecture.md](../../services/security-service/architecture.md)). 상태 변경 엔드포인트는 절대 추가하지 않는다.

base path: `/internal/security`

---

## GET /internal/security/login-history

특정 계정의 로그인 이력 조회.

**Auth required**: internal — `Authorization: Bearer <IAM client_credentials JWT>` (TASK-BE-319a; 정적 `X-Internal-Token` 제거됨). 수신측 `InternalAuthFilter` 가 JWKS 서명 + issuer + **`internal.invoke` scope** 로 검증한다 (TASK-MONO-422). GAP `auth-service` SAS 는 시스템·유저 토큰을 모두 발급하는 **공유 issuer** 라 서명+issuer 만으로는 시스템 자격을 구별 못 하므로, 토큰은 `internal.invoke` 워크로드 scope(`V0019` seed)를 반드시 지녀야 한다. 자격증명 미제시/무효/scope 없음(유저 토큰 포함) → 403 `PERMISSION_DENIED` (fail-closed). 정당한 내부 caller(`admin-service-client`)는 이 scope 를 지닌다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (required) | 대상 계정 |
| `from` | ISO 8601 datetime (optional) | 시작 시각 |
| `to` | ISO 8601 datetime (optional) | 종료 시각 |
| `outcome` | string (optional) | SUCCESS / FAILURE / RATE_LIMITED / TOKEN_REUSE |
| `page` | int (default 0) | — |
| `size` | int (default 20, max 100) | — |

**Response 200**:
```json
{
  "content": [
    {
      "eventId": "string (UUID)",
      "accountId": "string",
      "outcome": "FAILURE",
      "ipMasked": "192.168.*.*",
      "userAgentFamily": "Chrome 120",
      "deviceFingerprint": "string (hashed, truncated)",
      "geoCountry": "KR",
      "occurredAt": "2026-04-12T09:58:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

**PII 마스킹 규칙** ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4):
- `ipMasked`: 마지막 두 옥텟을 `*`로 마스킹 (canonical: [auth-service device-session.md "IP Masking Format"](../../services/auth-service/device-session.md))
- `deviceFingerprint`: SHA256 해시의 앞 12자만
- 이메일: 응답에 포함하지 않음

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | accountId 누락, from > to 등 |
| 403 | `PERMISSION_DENIED` | 인증 실패 |

---

## GET /internal/security/suspicious-events

특정 계정의 비정상 로그인 탐지 이벤트 조회.

**Auth required**: internal — `Authorization: Bearer <IAM client_credentials JWT>` (TASK-BE-319a; 정적 `X-Internal-Token` 제거됨). 수신측 `InternalAuthFilter` 가 JWKS 서명 + issuer + **`internal.invoke` scope** 로 검증한다 (TASK-MONO-422). GAP `auth-service` SAS 는 시스템·유저 토큰을 모두 발급하는 **공유 issuer** 라 서명+issuer 만으로는 시스템 자격을 구별 못 하므로, 토큰은 `internal.invoke` 워크로드 scope(`V0019` seed)를 반드시 지녀야 한다. 자격증명 미제시/무효/scope 없음(유저 토큰 포함) → 403 `PERMISSION_DENIED` (fail-closed). 정당한 내부 caller(`admin-service-client`)는 이 scope 를 지닌다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (required) | — |
| `from` | ISO 8601 datetime (optional) | — |
| `to` | ISO 8601 datetime (optional) | — |
| `ruleCode` | string (optional) | VELOCITY / GEO_ANOMALY / DEVICE_CHANGE / TOKEN_REUSE |
| `page` | int (default 0) | — |
| `size` | int (default 20, max 100) | — |

**Response 200**:
```json
{
  "content": [
    {
      "id": "string",
      "accountId": "string",
      "ruleCode": "GEO_ANOMALY",
      "riskScore": 85,
      "actionTaken": "AUTO_LOCK",
      "evidence": {
        "previousCountry": "KR",
        "currentCountry": "US",
        "timeDeltaMinutes": 30
      },
      "detectedAt": "2026-04-12T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | accountId 누락 |
| 403 | `PERMISSION_DENIED` | — |

---

## Meta-Audit

이 엔드포인트의 모든 호출은 **조회 자체가 감사 기록됨** ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A5). 호출자(admin-service의 operator_id), 조회 대상(accountId), 시간이 security-service 내부에 `admin_query_audit` 로그로 기록.

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
