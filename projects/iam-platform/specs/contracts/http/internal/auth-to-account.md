# Internal HTTP Contract: auth-service → account-service

auth-service가 로그인/refresh 플로우에서 계정의 현재 상태를 조회한다.

**호출 방향**: auth-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2)
**인증** (TASK-BE-318c 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — auth-service 가 `auth-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 정적 `X-Internal-Token` 은 제거됨. JWT 미제시/무효 시 모든 `/internal/**` 요청은 401 `UNAUTHORIZED` 로 fail-closed.

> **TASK-BE-063 (credential ownership)** — credential 데이터는 이제 auth-service 가 소유한다. 과거의 `GET /internal/accounts/credentials` 엔드포인트는 제거되었다. auth-service 는 로그인 시 로컬 `CredentialRepository` 로 credential 을 조회하고, 본 문서의 status 엔드포인트로 계정 활성 여부만 확인한다. credential 쓰기 경로는 [auth-internal.md](./auth-internal.md) 참조.

> **TASK-BE-229 (tenant-aware login)** — `GET /internal/accounts/tenant-info` 엔드포인트를 추가한다. auth-service 가 로그인 시 이메일·`tenant_id`(선택)로 계정의 `tenant_id`·`tenant_type`·`accountId`를 조회한다. 다중 매칭 가능 응답 형태: 0건 → credential 없음, 1건 → 정상, 2건 이상 → `LOGIN_TENANT_AMBIGUOUS` (presentation layer에서 변환).

> **TASK-BE-407 (authoritative tenant_type)** — auth-service 가 JWT `tenant_type` 클레임을 정확히 채우기 위해 account-service `GET /internal/tenants/{tenantId}` 를 호출해 권위 `tenant_type` 을 조회한다(과거의 `"fan-platform"→B2C_CONSUMER`, 그 외→`B2B_ENTERPRISE` 하드코딩 폴백 제거). hot-path(로그인/refresh) 성능을 위해 auth-service 는 결과를 캐시하며 `fan-platform` 은 프리시드된 기본값으로 네트워크 호출 0이다. 미존재 테넌트(404)는 `Optional.empty()`, 5xx/네트워크 장애는 `AccountServiceUnavailableException` 으로 매핑된다. 호출 대상 엔드포인트는 이미 존재하며(`TenantLifecycleController`), 본 task 는 auth-service 측 소비만 추가한다.

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

## GET /internal/tenants/{tenantId}

테넌트의 권위 `tenant_type` 조회. auth-service 가 토큰 발급 경로(로그인/refresh/social-callback)에서 JWT `tenant_type` 클레임을 정확히 채우기 위해 호출한다 (TASK-BE-407). 서버 측 엔드포인트는 account-service `TenantLifecycleController` 에 이미 존재한다.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string (slug) | 대상 테넌트 |

**Response 200**:
```json
{
  "tenantId": "ecommerce",
  "displayName": "E-commerce",
  "tenantType": "B2C_CONSUMER | B2B_ENTERPRISE",
  "status": "ACTIVE | SUSPENDED | ...",
  "createdAt": "2026-06-20T10:00:00Z",
  "updatedAt": "2026-06-20T10:00:00Z"
}
```

- auth-service 는 `tenantType` 필드만 소비한다. 나머지 필드는 무시한다.

**Response 404**: 해당 `tenantId` 의 테넌트 미존재 → auth-service 는 `Optional.empty()` 로 매핑.

**auth-service 매핑 규약**:
- 200 → `tenantType` 문자열을 `Optional` 로 반환.
- 404 → `Optional.empty()`.
- 5xx / 네트워크 장애 / circuit-open → `AccountServiceUnavailableException` (다른 internal 호출과 동일).

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
