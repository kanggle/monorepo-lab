# Internal HTTP Contract: auth-service → account-service

auth-service가 로그인/refresh 플로우에서 계정의 현재 상태를 조회한다.

**호출 방향**: auth-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../rules/domains/saas.md) S2)
**인증**: `X-Internal-Token` 헤더. account-service `INTERNAL_API_TOKEN` 과 동일 값을 caller(auth-service `admin.downstream.internal-token` 또는 동등) 가 주입한다. 토큰 미설정 + bypass-when-unconfigured=false 조합에서 모든 `/internal/**` 요청은 401 `UNAUTHORIZED` 로 fail-closed (TASK-BE-142). 운영 환경은 반드시 secret store 에서 주입.

> **TASK-BE-063 (credential ownership)** — credential 데이터는 이제 auth-service 가 소유한다. 과거의 `GET /internal/accounts/credentials` 엔드포인트는 제거되었다. auth-service 는 로그인 시 로컬 `CredentialRepository` 로 credential 을 조회하고, 본 문서의 status 엔드포인트로 계정 활성 여부만 확인한다. credential 쓰기 경로는 [auth-internal.md](./auth-internal.md) 참조.

> **TASK-BE-229 (tenant-aware login)** — `GET /internal/accounts/tenant-info` 엔드포인트를 추가한다. auth-service 가 로그인 시 이메일·`tenant_id`(선택)로 계정의 `tenant_id`·`tenant_type`·`accountId`를 조회한다. 다중 매칭 가능 응답 형태: 0건 → credential 없음, 1건 → 정상, 2건 이상 → `LOGIN_TENANT_AMBIGUOUS` (presentation layer에서 변환).

---

## GET /internal/accounts/tenant-info

이메일과 선택적 `tenant_id` 파라미터로 계정의 tenant 정보를 조회한다. auth-service 가 로그인 시 `tenant_id`·`tenant_type`·`accountId`를 얻기 위해 호출한다.

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | Yes | 로그인 이메일 |
| `tenantId` | string | No | 특정 테넌트 한정 조회. 지정 시 단일 row 응답 강제. 미지정 시 다중 매칭 가능 |

**Response 200** (단일 또는 다중 매칭):
```json
[
  {
    "accountId": "string (UUID)",
    "tenantId": "string (slug)",
    "tenantType": "B2C_CONSUMER | B2B_ENTERPRISE"
  }
]
```

- 빈 배열 `[]` → 해당 이메일로 등록된 계정 없음 (또는 `tenantId` 지정 시 해당 테넌트에 없음)
- 배열 길이 1 → 단일 매칭 → 정상 로그인 흐름
- 배열 길이 2 이상 → 다중 테넌트 매칭 → auth-service presentation에서 `LOGIN_TENANT_AMBIGUOUS` 400으로 변환

**Response 404**: 요청 형식 오류 (email 누락 등) — `VALIDATION_ERROR`

---

## GET /internal/accounts/{accountId}/status

특정 계정의 현재 상태 조회. auth-service가 로그인/refresh 시 계정이 여전히 활성 상태인지 확인.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 대상 계정 |

**Response 200**:
```json
{
  "accountId": "string",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED",
  "statusChangedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**: 계정 미존재
```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account not found",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

---

## Caller Constraints (auth-service 측)

- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프 + jitter). **404는 재시도 금지** (4xx)
- Circuit breaker: 실패율 50% / 10초 → open → 30초 half-open
- account-service 장애 시 **로그인 불가** (fail-closed)
