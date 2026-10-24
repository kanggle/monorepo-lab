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

## GET /internal/security/metrics/daily

IAM 개요 실적 줄(admin-api.md [§ GET /api/admin/metrics](admin-api.md#get-apiadminmetrics), TASK-MONO-811)의 로그인 · 잠금 건수 — **KST 날짜별 건수만**. 행 · 계정 id · IP · 기기 지문은 응답에 없고, 쿼리도 `login_history` 의 `(outcome, occurred_at)` · `account_lock_history` 의 `(occurred_at)` 외의 컬럼을 읽지 않는다. 각 키의 규범 정의는 admin-api.md 의 표다 — 이 절은 그 숫자를 만드는 표면이다.

**Auth required**: internal — 위 두 엔드포인트와 같다(`InternalAuthFilter`: GAP client_credentials JWT + **`internal.invoke` scope**, 없거나 무효 · scope 없음(유저 토큰 포함) → 403 `PERMISSION_DENIED`, fail-closed).

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string (**required**) | 셀 테넌트(행의 `tenant_id` 와 정확히 일치). `*` → 전 테넌트(`consumer-pool` 포함). admin-service 가 `QueryTenantScopeGate` 로 이미 게이트한 값이다 — 이 서비스는 다시 판정하지 않는다(내부 신뢰 자세, `GET /internal/accounts` 와 같다) |
| `from` | ISO-8601 instant (**required**) | 창 시작(포함). `…Z` 또는 오프셋(`+09:00`) |
| `to` | ISO-8601 instant (**required**) | 창 끝(**제외**) — 창은 반열림 `[from, to)` |

`to < from` · 창 길이 62일 초과(한 달 창의 안전 상한 — 실수로 생기는 무제한 스캔 방지)는 400.

**Response 200**:
```json
{
  "tenantId": "fan-platform",
  "from": "2026-10-18T15:00:00Z",
  "to": "2026-10-23T05:05:00Z",
  "days": [
    { "date": "2026-10-19", "loginSuccess": 30, "loginFailure": 2, "accountLocked": 0 }
  ]
}
```

- `days` — 센 행이 하나라도 있는 **KST 날짜**만, 오름차순. 없는 날은 빠진다(0 채우기는 admin-service 가 공용 `PerformanceMetrics.of` 로 한다).
- `date` 는 `occurred_at` 을 Java 의 `KstPeriodBounds.dateOf` 로 자른 KST 날짜다 — SQL 에서 날을 자르지 않는다(performance-metrics-contract.md § 2).
- `loginSuccess` = `outcome = SUCCESS` 행 수 · `loginFailure` = `outcome = FAILURE` 행 수 · `accountLocked` = `account_lock_history` 행 수. `ATTEMPTED` · `RATE_LIMITED` · `TOKEN_REUSE` · `REFRESH` 는 읽지도 않는다.
- 🔴 테넌트는 **행에 기록된 `tenant_id`** 다. 소비자 풀 계정의 로그인 · 잠금은 `consumer-pool` 로 기록되므로 사이트 테넌트를 물으면 잡히지 않는다(admin-api.md 의 알려진 공백 · 후속 티켓).
- 인덱스: `V0012__metrics_indexes.sql` — `login_history (tenant_id, outcome, occurred_at)` · `account_lock_history (tenant_id, occurred_at)` · `account_lock_history (occurred_at)`. `*` 의 로그인 쿼리는 기존 `idx_login_history_occurred_at`.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `tenantId` · `from` · `to` 누락 · instant 가 아님 · `to < from` · 창 > 62일 |
| 403 | `PERMISSION_DENIED` | 인증 실패(위 Auth) |

---

## Meta-Audit

이 엔드포인트의 조회는 **호출자가 감사한다** ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A5 · TASK-BE-654 소유자 결정 2026-10-24 UTC «호출자 감사 + admin 보강»). security-service 는 감사 행을 쓰지 않는다. 요청에 운영자 신원이 실려 오지 않기 때문이다(서비스 토큰만 온다).

- **감사하는 곳**: 유일한 운영 호출자 admin-service 가 `GET /api/admin/audit` 마다 `admin_actions` 에 `action_code=AUDIT_QUERY` 행 하나를 쓴다([admin-api.md § GET /api/admin/audit «Side Effects»](admin-api.md)).
  - 그 행은 운영자, 조회 대상(`target_id` = accountId), 시간을 담는다.
  - `downstream_detail` 에는 실제로 읽은 소스(`sources=admin,login_history,suspicious`), 요청 source, 기간, actionCode 가 들어간다.
  - 보안 소스를 읽었으면 `permission_used = audit.read+security.event.read` 다.
  - 🔴 이 엔드포인트 호출이 **실패하거나 타임아웃이어도** `outcome=FAILURE` 행이 남는다. security-service 가 행을 이미 읽었을 수 있기 때문이다.
- 🔴 **감사되지 않는 경로 (알려진 한계)**: `/internal/**` 인증은 서명 · issuer · `internal.invoke` scope 만 본다(`InternalAuthFilter`). 그래서 그 scope 를 가진 다른 client 가 직접 부르면 어떤 감사 행도 남지 않는다. 2026-10-24 UTC 코드 기준으로 그런 호출부는 0 이다.
- (이전 문구 — 구현된 적 없음) «security-service 내부 `admin_query_audit` 로그» 는 TASK-BE-654 로 폐기됐다.

🔵 **`GET /internal/security/metrics/daily` 는 이 규칙의 대상이 아니다** (TASK-MONO-811). A5 의 meta-audit 는 «누가 **누구의** 감사 기록을 봤나» 를 남기는 것인데, 이 엔드포인트에는 조회 대상(`accountId`)이 없고 응답은 날짜별 건수뿐이라 어떤 사람의 기록도 드러나지 않는다. 운영자 측 기록은 admin-service 가 진다 — 거절은 `METRICS_QUERY` DENIED 행, 성공은 read-path 규약(BE-486)대로 행 없음(admin-api.md § GET /api/admin/metrics «Side Effects»). (두 행 조회 엔드포인트의 감사 공백은 TASK-BE-654 가 위 «호출자 감사» 로 닫았다.)

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
