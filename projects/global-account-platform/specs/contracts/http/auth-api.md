# HTTP Contract: auth-service (Public API)

모든 엔드포인트는 gateway 경유. base path: `/api/auth`

---

## POST /api/auth/login

사용자 로그인. 이메일·패스워드를 검증하고 JWT access/refresh token pair를 발급한다.

**Auth required**: No

**Request**:
```json
{
  "email": "string (required, email format)",
  "password": "string (required, min 8)",
  "tenantId": "string (optional, tenant slug e.g. 'fan-platform', 'wms')"
}
```

**Response 200**:
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (JWT)",
  "expiresIn": 1800,
  "tokenType": "Bearer"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `CREDENTIALS_INVALID` | 이메일 미존재 또는 패스워드 불일치. **구체 원인 노출 금지** ([rules/domains/saas.md](../../../rules/domains/saas.md)) |
| 400 | `LOGIN_TENANT_AMBIGUOUS` | 같은 이메일이 여러 테넌트에 등록되어 있고 `tenantId` 미지정. 사용자는 `tenantId`를 명시하여 재시도해야 한다 |
| 403 | `ACCOUNT_LOCKED` | 계정 잠김 상태 |
| 403 | `ACCOUNT_DORMANT` | 휴면 상태 (별도 복구 흐름 필요) |
| 403 | `ACCOUNT_DELETED` | 삭제된 계정 |
| 429 | `LOGIN_RATE_LIMITED` | 로그인 실패 횟수 초과 (Redis 카운터) |
| 422 | `VALIDATION_ERROR` | 이메일/패스워드 형식 오류 |

**Side Effects**:
- 성공: `auth.login.succeeded` 이벤트 발행 (outbox)
- 실패: `auth.login.failed` 이벤트 발행, Redis 실패 카운터 증가
- 모든 시도: `auth.login.attempted` 이벤트 발행

---

## POST /api/auth/logout

현재 세션 종료. refresh token을 블랙리스트에 등록한다.

**Auth required**: Yes (access token)

**Request**:
```json
{
  "refreshToken": "string (required, 현재 세션의 refresh token)"
}
```

**Response 204**: No Content

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |
| 400 | `VALIDATION_ERROR` | refreshToken 누락 |

**Side Effects**: `refresh:blacklist:{tenant_id}:{jti}` Redis SET

---

## POST /api/auth/refresh

Refresh token rotation. 기존 refresh token을 소비하고 새 access/refresh pair를 발급한다.

**Auth required**: No (refresh token을 body로 전달)

**Request**:
```json
{
  "refreshToken": "string (required)"
}
```

**Response 200**:
```json
{
  "accessToken": "string (JWT, new)",
  "refreshToken": "string (JWT, new — 기존 token은 즉시 무효)",
  "expiresIn": 1800,
  "tokenType": "Bearer"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_EXPIRED` | refresh token 만료 |
| 401 | `TOKEN_REUSE_DETECTED` | 이미 rotation된 refresh token 재사용. **해당 account의 모든 세션 즉시 무효화** |
| 401 | `TOKEN_TENANT_MISMATCH` | refresh token의 `tenant_id`와 계정의 현재 `tenant_id`가 불일치. 조작·버그 의심 → 보안 이벤트 발행 |
| 401 | `SESSION_REVOKED` | 명시적으로 revoke된 세션 |
| 403 | `ACCOUNT_LOCKED` | 계정 잠김 (refresh 차단) |

**Side Effects**:
- 성공: `auth.token.refreshed` 이벤트, DB에 새 refresh_token row + 기존 row의 `rotated_from` 체인 갱신
- 재사용 탐지: `auth.token.reuse.detected` 이벤트 + 해당 account의 모든 refresh_tokens `revoked=TRUE`

---

## Token Specification

### Access Token (JWT)

| Claim | 값 |
|---|---|
| `sub` | account_id (UUID) |
| `iss` | `global-account-platform` |
| `iat` | 발급 시각 (epoch seconds) |
| `exp` | `iat + 1800` (30분) |
| `jti` | UUID |
| `scope` | `user` (일반 사용자) 또는 `admin` (운영자) |
| `tenant_id` | 토큰을 발급한 테넌트의 slug (예: `fan-platform`, `wms`). **필수** — 누락 시 JwtSigner가 발급 거부 (fail-closed) |
| `tenant_type` | `B2C_CONSUMER` \| `B2B_ENTERPRISE`. **필수** |
| `device_id` | 이 access token이 속한 device session의 opaque UUID v7. 로그인 성공 시 `device_sessions.device_id`에서 채워지며, refresh rotation으로 새 access token이 발급될 때도 동일 값을 유지한다. `GET /api/accounts/me/sessions/current`, `DELETE /api/accounts/me/sessions` (bulk) 등 "현재 세션" 해석의 단일 소스 |

서명: RS256. 공개 키는 JWKS 엔드포인트로 배포.

**검증 필수 사항 (TASK-BE-143):** gateway-service 와 community-service 는 `iss == global-account-platform` 을 강제 검증한다. `iss` claim 이 누락되거나 다른 값이면 토큰을 거부 (`401 TOKEN_INVALID`). 검증 기댓값은 각 서비스의 `*.jwt.expected-issuer` 설정으로 관리되며 환경별 분리 시 환경 변수로 override 한다. admin-service 는 자체 `IssuerEnforcingJwtVerifier` 로 admin IdP issuer 를 별도 강제한다.

**`device_id` claim 설계 근거** ([specs/services/auth-service/device-session.md](../../services/auth-service/device-session.md) D1): `device_id`는 서버 발급 opaque UUID v7이며 fingerprint가 아니다. PII/식별 리스크가 낮아 access token claim으로 실어도 안전하며, stateless 경로에서 "현재 세션"을 즉시 해석할 수 있다.

### Refresh Token (JWT)

| Claim | 값 |
|---|---|
| `sub` | account_id |
| `jti` | UUID (DB의 `refresh_tokens.jti`와 일치) |
| `iat` | 발급 시각 |
| `exp` | `iat + 604800` (7일) |
| `type` | `refresh` |

### Refresh Token Rotation and `device_id` 지속성

Refresh rotation(`POST /api/auth/refresh`) 경로에서 새 access token이 발급될 때, `device_id` claim은 **기존 값을 그대로 상속**한다. `device_id`는 opaque device session ID로서 access/refresh token이 회전하더라도 device session 자체가 revoke·eviction되지 않는 한 불변이다. 즉 access token의 `jti`는 매 회전마다 바뀌지만 `device_id`는 같다. 이 불변성은 [device-session.md](../../services/auth-service/device-session.md) D5(refresh_tokens↔device_sessions 매핑)에서 보장된다.

---

## GET /api/accounts/me/sessions

현재 인증된 사용자의 활성 device session 목록을 조회한다.

**Auth required**: Yes (access token)

**Request**: (no body)

**Response 200**:
```json
{
  "items": [
    {
      "deviceId": "01936c2f-7d8a-7c3e-9b4a-1f2e3d4c5b6a",
      "userAgentFamily": "Chrome 120",
      "ipMasked": "192.168.*.*",
      "geoCountry": "KR",
      "issuedAt": "2026-04-01T10:00:00Z",
      "lastSeenAt": "2026-04-13T08:22:00Z",
      "current": false
    }
  ],
  "total": 1,
  "maxActiveSessions": 10
}
```

**Response 필드 노트**:
- `deviceId`: `device_sessions.device_id` (서버 생성 opaque UUID v7). 클라이언트는 이 값을 revoke path variable로 사용
- `current`: 이 응답을 받은 access token이 속한 session과 같으면 `true`
- `ipMasked`: IPv4는 마지막 두 옥텟을 `*`로, IPv6는 하위 80 bit을 `::*`로 마스킹. 정식 규칙은 [specs/services/auth-service/device-session.md](../../services/auth-service/device-session.md#ip-masking-format) "IP Masking Format" 절을 단일 참조로 사용
- fingerprint 원문, 원본 IP는 **절대 노출하지 않음**

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |

---

## GET /api/accounts/me/sessions/current

현재 access token이 속한 device session 단건 조회. 서버는 access token JWT의 `device_id` claim을 읽어 해당 `device_sessions` row를 반환한다.

**Auth required**: Yes (access token)

**Response 200**:
```json
{
  "deviceId": "01936c2f-7d8a-7c3e-9b4a-1f2e3d4c5b6a",
  "userAgentFamily": "Chrome 120",
  "ipMasked": "192.168.*.*",
  "geoCountry": "KR",
  "issuedAt": "2026-04-01T10:00:00Z",
  "lastSeenAt": "2026-04-13T08:22:00Z",
  "current": true
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |
| 404 | `SESSION_NOT_FOUND` | 토큰 claim의 `device_id`에 해당하는 활성 session 없음 (이미 revoke되었거나 DB 불일치) |

---

## DELETE /api/accounts/me/sessions/{deviceId}

특정 디바이스의 session을 revoke한다. 연결된 refresh token은 모두 `revoked = TRUE` 처리된다.

**Auth required**: Yes (access token)

**Path Parameters**:

| 이름 | 타입 | 설명 |
|---|---|---|
| `deviceId` | string (UUID) | revoke 대상 `device_sessions.device_id` |

**Response 204**: No Content

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |
| 403 | `SESSION_OWNERSHIP_MISMATCH` | 해당 `deviceId`가 현재 account 소유가 아님 |
| 404 | `SESSION_NOT_FOUND` | `deviceId` 미존재 또는 이미 revoke 상태 |

**Side Effects**:
- `device_sessions.revoked_at = NOW()`, `revoke_reason = 'USER_REQUESTED'`
- 연결된 `refresh_tokens.revoked = TRUE`
- outbox: `auth.session.revoked`

**Note**: 현재 자기 자신의 deviceId를 revoke하는 것은 허용된다 — 즉시 로그아웃과 동등. 클라이언트는 후속 호출에서 refresh token이 거부됨을 처리해야 한다.

---

## DELETE /api/accounts/me/sessions

현재 device session을 **제외**한 다른 모든 세션을 일괄 revoke한다 ("다른 기기에서 로그아웃"). "현재 device session"은 access token JWT의 `device_id` claim으로 식별한다.

**Auth required**: Yes (access token)

**Request**: (no body)

**Response 200**:
```json
{
  "revokedCount": 3
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |
| 404 | `SESSION_NOT_FOUND` | 현재 토큰의 `device_id`에 해당하는 active session이 없음 (제외 기준을 설정할 수 없음) |

**Side Effects**:
- 현재 device를 제외한 모든 active `device_sessions.revoked_at = NOW()`, `revoke_reason = 'LOGOUT_OTHERS'`
- 대응 `refresh_tokens.revoked = TRUE`
- 각 revoked session마다 outbox: `auth.session.revoked`

---

## Common Error Format

모든 에러 응답:

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable description (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

[platform/error-handling.md](../../../platform/error-handling.md) 표준.

---

## GET /api/auth/oauth/authorize

OAuth 소셜 로그인 시작. provider의 authorization URL과 CSRF 방어용 state를 생성하여 반환한다.

**Auth required**: No

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `provider` | string | Yes | OAuth provider 식별자. `google`, `kakao`, `microsoft` 중 하나 |
| `redirectUri` | string | Yes | 인증 완료 후 클라이언트가 callback을 받을 URI |

**Response 200**:
```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "string (cryptographic random, CSRF 방어)"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `UNSUPPORTED_PROVIDER` | 지원하지 않는 provider |
| 400 | `INVALID_REDIRECT_URI` | `redirectUri` 가 서버 화이트리스트와 정확히 일치하지 않음 (open-redirect 방지). 응답 메시지는 어떤 URI 가 거부됐는지 노출하지 않는다. |
| 422 | `VALIDATION_ERROR` | redirectUri 누락 또는 형식 오류 |

---

## POST /api/auth/oauth/callback

OAuth authorization code를 교환하여 로그인을 완료한다. 신규 사용자는 계정이 자동 생성된다.

**Auth required**: No

**Request**:
```json
{
  "provider": "string (required, 'google' | 'kakao' | 'microsoft')",
  "code": "string (required, provider가 발급한 authorization code)",
  "state": "string (required, authorize 응답에서 받은 state)",
  "redirectUri": "string (required, authorize 시 전달한 것과 동일해야 함)"
}
```

**Response 200**:
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (JWT)",
  "expiresIn": 1800,
  "tokenType": "Bearer",
  "isNewAccount": true
}
```

**Response 필드 노트**:
- `accessToken`, `refreshToken`, `expiresIn`, `tokenType`: `POST /api/auth/login` 응답과 동일 형식·스펙
- `isNewAccount`: 이번 소셜 로그인으로 계정이 **새로 생성**되었으면 `true`, 기존 계정이면 `false`

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `INVALID_STATE` | state 불일치, 만료, 또는 이미 사용됨 |
| 401 | `INVALID_CODE` | authorization code 교환 실패 (만료, 위조 등) |
| 400 | `UNSUPPORTED_PROVIDER` | 지원하지 않는 provider |
| 400 | `INVALID_REDIRECT_URI` | `redirectUri` 가 서버 화이트리스트와 정확히 일치하지 않음 (open-redirect 방지). 검증은 state 소비 직후·provider HTTP 호출 직전에 수행. |
| 403 | `ACCOUNT_LOCKED` | 연결된 계정이 잠김 상태 |
| 403 | `ACCOUNT_DORMANT` | 연결된 계정이 휴면 상태 |
| 403 | `ACCOUNT_DELETED` | 연결된 계정이 삭제 상태 |
| 422 | `EMAIL_REQUIRED` | provider가 이메일을 제공하지 않음 |
| 502 | `PROVIDER_ERROR` | provider token endpoint 또는 userinfo API 장애 |

**Side Effects**:
- 성공: `auth.login.succeeded` 이벤트 발행 (outbox, `loginMethod` 필드 포함)
- 성공: device session 생성 (기존 로그인과 동일 — [session-management.md](../../features/session-management.md))
- 신규 계정: account-service `/internal/accounts/social-signup` 호출 → 계정 자동 생성
- `social_identities` row 생성 또는 `last_used_at` 갱신

---

## PATCH /api/auth/password

인증된 사용자의 패스워드를 변경한다.

**인증**: Bearer Access Token 필수

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| currentPassword | string | Y | 현재 패스워드 (검증용) |
| newPassword | string | Y | 새 패스워드 (정책 검증) |

**Response**: 204 No Content

**Error**:
| Code | HTTP | Description |
|---|---|---|
| CREDENTIALS_INVALID | 400 | 현재 패스워드 불일치 |
| PASSWORD_POLICY_VIOLATION | 400 | 새 패스워드가 정책 미충족 |

---

## POST /api/auth/password-reset/request

패스워드 재설정 이메일을 요청한다. 계정 존재 여부와 무관하게 항상 204를 반환한다.

**인증**: 불필요

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| email | string | Y | 재설정 대상 이메일 |

**Response**: 204 No Content

---

## POST /api/auth/password-reset/confirm

재설정 토큰과 새 패스워드로 패스워드를 변경한다.

**인증**: 불필요

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| token | string | Y | 재설정 토큰 (Redis, TTL 1시간) |
| newPassword | string | Y | 새 패스워드 (정책 검증) |

**Response**: 204 No Content

**Error**:
| Code | HTTP | Description |
|---|---|---|
| PASSWORD_RESET_TOKEN_INVALID | 400 | 토큰 없음·만료·이미 사용됨 |
| PASSWORD_POLICY_VIOLATION | 400 | 새 패스워드가 정책 미충족 |
