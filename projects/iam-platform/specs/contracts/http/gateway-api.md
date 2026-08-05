# HTTP Contract: gateway-service

게이트웨이는 자체 비즈니스 엔드포인트가 없다. 아래는 **라우트 매핑 + 게이트웨이 자체 응답** 정의.

---

## Route Map

| Public Path | Target Service | 인증 필요 | 비고 |
|---|---|---|---|
| `GET /.well-known/openid-configuration` | auth-service | No | OIDC Discovery (TASK-BE-251 Phase 2c) |
| `GET /oauth2/jwks` | auth-service | No | JWKS (TASK-BE-251 Phase 2c) |
| `GET /oauth2/authorize` | auth-service | No (SAS 처리) | Authorization Code PKCE (TASK-BE-251 Phase 2c) |
| `POST /oauth2/token` | auth-service | No (SAS 처리) | Token endpoint — authorization_code / client_credentials / refresh_token (TASK-BE-251 Phase 2c) |
| `GET /oauth2/userinfo` | auth-service | No (SAS 처리) | UserInfo — Bearer token SAS 내부 검증 (TASK-BE-251 Phase 2c) |
| `POST /oauth2/revoke` | auth-service | No (SAS 처리) | Token Revocation RFC 7009 (TASK-BE-251 Phase 2c) |
| `POST /oauth2/introspect` | auth-service | No (SAS 처리) | Token Introspection RFC 7662 (TASK-BE-251 Phase 2c) |
| ~~`POST /api/auth/login`~~ | ~~auth-service~~ | — | **REMOVED 2026-08-01 (TASK-BE-398)** — ADR-001 D2-b sunset. `public-paths` 에서도 제거되어 인증 없이 호출 시 엣지가 `401 TOKEN_INVALID`. 대체: `POST /oauth2/token` |
| ~~`GET /api/auth/oauth/authorize`~~ · ~~`POST /api/auth/oauth/callback`~~ | ~~auth-service~~ | — | **REMOVED 2026-08-01 (TASK-BE-398)** — 레거시 커스텀-JWT 소셜 플로우. 대체: 브라우저 `GET /login/oauth/{provider}` → SAS |
| `POST /api/auth/logout` | auth-service | Yes (access token) | — |
| `POST /api/auth/refresh` | auth-service | No (refresh token in body) | — |
| `POST /api/accounts/signup` | account-service | No | — |
| `GET /api/accounts/me` | account-service | Yes | — |
| `PATCH /api/accounts/me/profile` | account-service | Yes | — |
| `GET /api/accounts/me/status` | account-service | Yes | — |
| `GET /api/accounts/me/sessions` | auth-service | Yes | 세션 관리 — auth-service `AccountSessionController` (TASK-BE-508). `/api/accounts/**` 보다 **먼저** 선언된 전용 라우트. [auth-api.md](auth-api.md) |
| `GET /api/accounts/me/sessions/current` | auth-service | Yes | 현재 device session 단건 (TASK-BE-508). [auth-api.md](auth-api.md) |
| `DELETE /api/accounts/me/sessions/{deviceId}` | auth-service | Yes | 특정 device session revoke (TASK-BE-508). [auth-api.md](auth-api.md) |
| `DELETE /api/accounts/me/sessions` | auth-service | Yes | 현재 세션 제외 전체 revoke (TASK-BE-508). [auth-api.md](auth-api.md) |
| `POST /api/admin/auth/login` | admin-service | No (gateway layer) | admin-service self-serve auth, [admin-api.md §Authentication Exceptions](admin-api.md) |
| `POST /api/admin/auth/2fa/enroll` | admin-service | Bootstrap token (body) | admin-service 내부 검증 |
| `POST /api/admin/auth/2fa/verify` | admin-service | Bootstrap token (body) | admin-service 내부 검증 |
| `POST /api/admin/auth/refresh` | admin-service | No (refresh JWT in body) | admin-service 내부 검증 |
| `GET /.well-known/admin/jwks.json` | admin-service | No | public key 노출 |
| `POST /api/admin/accounts/{id}/lock` | admin-service | Yes (operator token, downstream) | 아래 §Admin Routes 참조 |
| `POST /api/admin/accounts/{id}/unlock` | admin-service | Yes (operator token, downstream) | 아래 §Admin Routes 참조 |
| `POST /api/admin/sessions/{id}/revoke` | admin-service | Yes (operator token, downstream) | 아래 §Admin Routes 참조 |
| `GET /api/admin/audit` | admin-service | Yes (operator token, downstream) | 아래 §Admin Routes 참조 |
| `GET /api/admin/console/registry` | admin-service | Yes (operator token, downstream) | platform-console product/tenant registry (TASK-BE-296). 아래 §Admin Routes 참조. contract: [console-registry-api.md](console-registry-api.md) |
| `GET /actuator/health` | gateway 자체 | No | 헬스체크 |
| `/internal/tenants/{tenantId}/**` | account-service | Yes (JWT) | path `{tenantId}` ↔ JWT `tenant_id` 검사. 불일치 시 403 `TENANT_SCOPE_DENIED`. 외부 노출 금지 |

### 세션 라우팅 순서 정책 (TASK-BE-508)

`/api/accounts/me/sessions**` 4개 경로는 **auth-service** 가 담당하고, 나머지 `/api/accounts/**` 는 **account-service** 가 담당한다. Spring Cloud Gateway 는 라우트를 **선언 순서**로 매칭하므로, 더 구체적인 세션 라우트(`id: auth-service-sessions`)를 광범위한 `id: account-service`(`/api/accounts/**`) 라우트보다 **먼저** 선언해야 한다. 순서가 뒤바뀌면 세션 호출이 account-service 로 오배송되어 500 으로 변질된다.

- 세션 라우트 predicate 는 `Path=/api/accounts/me/sessions,/api/accounts/me/sessions/**` — 프로필/상태(`/api/accounts/me`, `/api/accounts/me/profile`, `/api/accounts/me/status`)는 매칭하지 않으므로 account-service 로 정상 라우팅된다.
- 세션 4경로는 public-paths 에 포함되지 않는다(인증 필수) — gateway JWT 필터가 `X-Account-ID` / `X-Device-Id` 를 주입한다.

### OIDC / OAuth2 라우팅 정책 (TASK-BE-251 Phase 2c)

`/oauth2/**` 및 `/.well-known/openid-configuration` 경로는 gateway JWT 검증 미수행(public-paths 선언).
auth-service 내부의 SAS 필터 체인이 해당 엔드포인트별 인증을 담당한다.

- `id: auth-service-oidc` 라우트가 `/oauth2/**`, `/.well-known/openid-configuration`를 `AUTH_SERVICE_URL`로 forward
- 기존 `id: auth-service` 라우트(`/api/auth/**`)와 분리 운영 — 상호 영향 없음
- `POST /oauth2/token` 경로는 rate-limit scope `refresh`에 매핑 (brute-force 보호)

---

## Admin Routes (second-layer auth)

admin-service 는 자체 IdP 를 소유하며 operator JWT 를 별도 RS256 키 쌍으로 서명한다 ([admin-service/security.md](../../services/admin-service/security.md)). 이 토큰은 auth-service 의 account JWKS 로 검증할 수 없으므로 **gateway 는 `/api/admin/**` 및 `/.well-known/admin/**` 서브트리 전체에서 자체 JWT 검증을 수행하지 않는다.**

- gateway 입장에선 위 서브트리가 **모두 public-paths** 에 포함된다. gateway 는 라우팅·rate-limit·request-id·CORS 만 담당하고 인증 책임을 지지 않는다.
- admin-service 의 `OperatorAuthenticationFilter` 가 **단일 검증 지점**으로서 operator JWT(`token_type="admin"`, `iss="admin-service"`) 또는 bootstrap token(`token_type="admin_bootstrap"`, 2FA 서브트리 전용)을 검증한다.
- 동일 요청에 대해 gateway 와 admin-service 양쪽에서 JWT 를 이중 검증하지 않는다.
- 권한(RBAC) 평가도 admin-service 에서 DB lookup 으로 매 요청 수행된다 ([admin-service/rbac.md](../../services/admin-service/rbac.md)).

이 위임 관계는 플랫폼 불변식이다. 향후 operator JWT 를 gateway 에서 검증하도록 변경하려면 ADR + 본 섹션 개정이 선행되어야 한다.

### 구현은 **서브트리 와일드카드**이며, 경로를 열거하지 않는다 (TASK-MONO-508)

`gateway.public-paths` 는 이 서브트리를 **메서드별 와일드카드 한 줄씩**으로 표현한다
(`GET:/api/admin/**`, `POST:`, `PUT:`, `PATCH:`, `DELETE:`, 그리고 `GET:/.well-known/admin/**`).
**개별 경로를 다시 열거하지 말 것.**

한때 열거로 되어 있었고, 그 목록은 admin-service 의 표면보다 뒤처졌다. 목록이 작성된 뒤 추가된
operator 엔드포인트는 전부 gateway 에서 401 로 죽었다 — 즉 **위 불변식이 금지한 "gateway 가
operator JWT 를 검증하는 상태" 가 ADR 없이 사실상 성립해 있었다.** 통합 데모에서 실제 operator
토큰으로 측정한 결과:

| | 결과 |
|---|---|
| 열거된 3경로 (`console/registry`, `audit`, `accounts`) | **200** |
| 열거되지 않은 8경로 (`org-nodes`, `me`, `operators`, `operators/grantable-roles`, `roles`, `permissions`, `groups`, `partnerships`) | **401 `TOKEN_INVALID`** |

콘솔의 IAM 관리 화면 7개가 `→ /login → /console` 로 튕겼다. 열거는 **드리프트가 조용한** 표현이다:
새 엔드포인트를 추가한 사람에게 아무 신호도 주지 않으면서 그 엔드포인트만 어둡게 만든다.

`RouteConfigTest` 가 이 불변식을 **실제 `application.yml` 을 읽어** 검증한다 — 픽스처를 손으로
복사하지 않는다. 이전 판은 복사본이 원본과 어긋난 채 초록이었고, 심지어
`GET /api/admin/accounts/{id}` 를 "public 아님" 이라고 단언해 이 섹션과 정면으로 모순됐다.
**산출물을 복제한 픽스처는 복제본만 검증한다.**

`/.well-known/admin/jwks.json` 은 admin-service 의 공개키를 외부 검증자에게 노출하기 위한 표준 디스커버리 엔드포인트다. gateway 는 `/.well-known/admin/**` 라우트를 admin-service 로 프록시한다.

---

## Gateway-Generated Responses

게이트웨이가 다운스트림 없이 직접 반환하는 응답들.

### 401 Unauthorized — JWT 검증 실패

```json
{
  "code": "TOKEN_INVALID",
  "message": "Access token is missing, expired, or has an invalid signature",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

발생 조건:
- `Authorization: Bearer <token>` 헤더 없음 (인증 필수 경로)
- JWT 서명 불일치 (JWKS kid mismatch 포함)
- JWT `exp` 만료
- JWT `nbf` 미도래
- JWT `tenant_id` claim 누락 (grace period fallback 비활성 시)

### 403 Forbidden — Tenant Scope 불일치

```json
{
  "code": "TENANT_SCOPE_DENIED",
  "message": "Tenant scope mismatch: path tenantId does not match token claim",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

발생 조건:
- `/internal/tenants/{tenantId}/...` 경로의 `{tenantId}` ↔ JWT `tenant_id` claim 불일치

### 429 Too Many Requests — Rate Limit 초과

```json
{
  "code": "RATE_LIMITED",
  "message": "Too many requests. Try again later.",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

헤더: `Retry-After: <seconds>`

발생 조건: 토큰 버킷 소진 (scope별 — [redis-keys.md](../../services/gateway-service/redis-keys.md) 참조)

### 503 Service Unavailable — 다운스트림 장애

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Downstream service is temporarily unavailable",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

발생 조건: circuit breaker open 또는 다운스트림 타임아웃

### 504 Gateway Timeout

```json
{
  "code": "GATEWAY_TIMEOUT",
  "message": "Downstream service did not respond in time",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

---

## Request Headers (gateway가 주입)

| 헤더 | 설명 |
|---|---|
| `X-Request-ID` | 없으면 UUID 생성, 있으면 전파 |
| `X-Account-ID` | JWT에서 추출한 account_id (인증 성공 시) |
| `X-Tenant-Id` | JWT `tenant_id` claim에서 추출한 테넌트 식별자 (인증 성공 시). 외부 입력 값은 반드시 gateway가 제거 후 JWT 기반으로 재설정. grace period fallback 활성 시 claim 누락 토큰은 `fan-platform`으로 간주 |
| `X-Forwarded-For` | 원본 client IP |

다운스트림은 이 헤더를 신뢰한다. 외부에서 `X-Account-ID` 또는 `X-Tenant-Id`를 직접 보내는 경우 **게이트웨이가 덮어씀** (spoofing 방지).

## Internal Provisioning Routes

| Path Pattern | 인증 | 검증 |
|---|---|---|
| `/internal/tenants/{tenantId}/**` | JWT 필수 | path `{tenantId}` ↔ JWT `tenant_id` 일치 확인. 불일치 시 403 `TENANT_SCOPE_DENIED` |

이 라우트는 외부 인터넷에 노출되지 않는다 (internal network only).

---

## CORS Policy

- Allowed origins: `CORS_ALLOWED_ORIGINS` 환경 변수 (콤마 구분)
- Allowed methods: `GET, POST, PATCH, DELETE, OPTIONS`
- Allowed headers: `Authorization, Content-Type, X-Request-ID, Idempotency-Key`
- Max age: 3600초
