# HTTP Contract: admin-service (Public API — Operator Only)

운영자 전용. 게이트웨이에서 `/api/admin/*` 경로에 **별도 인증 필터 체인** 적용. 일반 사용자 JWT로는 접근 불가.

base path: `/api/admin`

모든 요청에 필수: `X-Operator-Reason` 헤더 (감사 사유)

> **인코딩 (TASK-MONO-176)**: `X-Operator-Reason` 값은 **percent-encoded (UTF-8, `encodeURIComponent`)** 로 전송된다. HTTP 헤더 값은 ByteString(ISO-8859-1)이라 한글 등 비-Latin-1 사유를 RAW 로 실으면 클라이언트 `fetch()` 가 전송 전 throw 한다. 서비스는 수신 시 percent-decode(UTF-8) 하여 원문 사유를 `admin_actions.reason` 에 저장한다(percent-escape 없는 ASCII 값은 그대로 round-trip). 디코드는 모든 `/api/admin/**` 컨트롤러에 대해 단일 필터(`OperatorReasonDecodingFilter`)에서 수행된다.

---

## Authentication

모든 `/api/admin/*` 경로는 기본적으로 **operator JWT 필수**이다:

- `Authorization: Bearer <operator-token>` 필수 (`token_type = "admin"`, `iss = "admin-service"`)
- 토큰 발급·서명·검증은 admin-service 자체 IdP가 소유 ([specs/services/admin-service/architecture.md](../../services/admin-service/architecture.md) Admin IdP Boundary 참조)
- `X-Operator-Reason` 헤더 필수 (감사 사유)

### Exceptions (no operator JWT required)

아래 서브트리는 위 기본 규칙의 **예외**로, 정식 operator JWT 없이도 호출 가능하다. `OperatorAuthenticationFilter.shouldNotFilter`의 완화 범위는 **정확히 이 경로들로 한정**된다.

| Method | Path | 대체 인증 | X-Operator-Reason |
|---|---|---|---|
| `POST` | `/api/admin/auth/login` | 없음 (username + password + 선택적 TOTP 코드 body) | 요구 없음 |
| `POST` | `/api/admin/auth/token-exchange` | **IAM OIDC `platform-console-web` subject token 필수** (RFC 8693 body). 2단계 요구 운영자는 subject `amr ∋ mfa` 도 필수(TASK-MONO-771 — 아래 § token-exchange) | 요구 없음 |
| `POST` | `/api/admin/auth/2fa/enroll` | **bootstrap token 필수** | 요구 없음 |
| `POST` | `/api/admin/auth/2fa/verify` | **bootstrap token 필수** | 요구 없음 |
| `POST` | `/api/admin/auth/refresh` | 없음 (refresh JWT body) | 요구 없음 |
| `GET` | `/.well-known/admin/jwks.json` | 없음 (public key 노출) | 요구 없음 |

> **`/api/admin/auth/token-exchange` (TASK-BE-298 / ADR-MONO-014)**: 이 경로는
> 정식 operator JWT 없이 호출 가능한 **추가 operator-token 발급 경로**다. 정식
> operator JWT 대신 IAM OIDC `platform-console-web` access token(subject token)을
> body 로 제시한다 — 이는 `/api/admin/auth/login` 의 "요청자 본인 인증 플로우"
> 예외와 동일 성격(다른 대상에 대한 운영 명령이 아님). admin-service 는 이
> subject token 을 auth-service JWKS 로 검증한 뒤 OIDC subject 를
> `admin_operators` row 로 fail-closed 해석하고 **기존 login-success 발급기와
> 동일한 operator access token**(`token_type=admin`, `iss=admin-service`)을
> 민팅한다. `OperatorAuthenticationFilter` 는 **확장되지 않는다** — 2번째
> issuer 를 수용하지 않으며(ADR-MONO-014 D1 Option A 기각), exchange 는
> password+TOTP login mint 의 **형제 발급 경로**일 뿐이다 (자세한 검증 정책:
> [security.md §IAM OIDC Subject-Token Validation](../../services/admin-service/security.md)).

위 경로 외의 어떤 `/api/admin/*` 요청도 operator JWT + `X-Operator-Reason`이 없으면 401/400으로 거부된다.

#### Bootstrap Token (2FA sub-tree)

`/api/admin/auth/2fa/enroll` 및 `/api/admin/auth/2fa/verify`는 정식 operator JWT 대신 **bootstrap token**(`token_type = "admin_bootstrap"`, TTL 10분, scope = `["2fa_enroll", "2fa_verify"]`, `jti` 1회 소비)을 요구한다. bootstrap token은 `POST /api/admin/auth/login`이 password verify 성공 + 2FA 미완료 상태일 때 응답 body로 발급한다. 상세 규약은 [specs/services/admin-service/security.md](../../services/admin-service/security.md) "Bootstrap Token" 섹션 참조. 구현은 TASK-BE-029.

#### X-Operator-Reason in Exceptions sub-tree

본 서브트리의 요청은 "다른 대상에 대한 운영 명령"이 아니라 **요청자 본인의 인증 플로우**이므로 `X-Operator-Reason` 헤더를 요구하지 않는다. `admin_actions` 기록이 발생하는 경우(예: enroll/verify)는 reason 필드에 상수 `"<self_enrollment>"`로 기록한다.

**Self-serve `me/*` mutation 경로의 동일 면제 (TASK-BE-306)**: 위 sub-tree 외에도, 운영자가 **자기 자신의 프로파일/자격증명을 변경**하는 self-serve `me/*` mutation 경로는 동일한 self-flow 면제를 따른다 — operator JWT 는 필수이지만 `X-Operator-Reason` 은 요구하지 않으며, `admin_actions` row 의 reason 필드에 self-flow 상수를 기록한다. 현재 해당 경로:

| Method | Path | reason 상수 | 비고 |
|---|---|---|---|
| `PATCH` | `/api/admin/operators/me/password` | `<self_password_change>` | TASK-BE-013 |
| `PATCH` | `/api/admin/operators/me/profile` | `<self_profile_update>` | **신규 (TASK-BE-306)** — operator 프로파일 carrier (`operatorContext.defaultAccountId` 등) 자가 설정 |


---

## Authorization Model

모든 mutation 및 read endpoint는 필요한 **permission key**를 선언한다 ([specs/services/admin-service/rbac.md](../../services/admin-service/rbac.md)). 운영자의 role 집합이 보유한 permission 합집합에 요청 endpoint의 permission이 포함되어야 통과한다. 누락 시 `403 PERMISSION_DENIED`.

- Permission key catalog: `account.read`, `account.lock`, `account.unlock`, `account.force_logout`, `audit.read`, `security.event.read`
- Annotation이 선언되지 않은 endpoint는 **fail-closed로 deny**되며 `admin_actions`에 `outcome=DENIED, permission_used="<missing>"` 기록
- 권한 거부는 request 단위로 감사 row 1건 기록 (dedup 없음)
- Operator 식별자는 JWT `sub` 클레임에서 추출 (`token_type = "admin"` 필수)

### Tenant Confinement — `X-Tenant-Id` (TASK-BE-467)

계정/세션 **변이 엔드포인트** (`/accounts/{id}/lock`, `/unlock`, `/bulk-lock`, `/accounts/{id}/gdpr-delete`, `/accounts/{id}/export`, `/sessions/{id}/revoke`) 는 선택적 `X-Tenant-Id` 헤더로 **행위자의 활성 테넌트**를 선언한다. 이는 읽기 경로(`GET /api/admin/accounts` 의 `tenantId` 쿼리, TASK-BE-357)와 **동일한 effective-scope 게이트**(`QueryTenantScopeGate`: home ∪ 배정, TASK-BE-326)를 재사용해 변이 경로를 읽기 경로와 **테넌트 패리티**로 맞춘다.

- 생략 → 운영자 자신의 테넌트. 일반(비-플랫폼) 운영자가 effective scope 밖의 테넌트를 지정하면 → **`403 TENANT_SCOPE_DENIED`** (best-effort DENIED `admin_actions` row, BE-262 미러링).
- 해소된 테넌트는 `X-Tenant-Id` 로 account-service 에 스탬프된다. 대상 계정이 **다른 테넌트**면 tenant-scoped 조회가 → **`404 ACCOUNT_NOT_FOUND`** (enumeration-safe: 타 테넌트 존재를 확인해 주지 않는다).
- **SUPER_ADMIN 플랫폼 스코프**: SUPER_ADMIN(`tenant_id='*'`) 이 활성 테넌트 없이(헤더 부재/`'*'`) 호출하면 admin-service 는 `'*'` 를 그대로 스탬프한다. account-service 는 **`lock` · `unlock` 에서 계정 행의 테넌트**로 대상을 찾는다(TASK-MONO-735 — 이전엔 `fan-platform` 기본값이라 `fan-platform` 밖 계정이 404 였다) · **`gdpr-delete` 도 같다(TASK-BE-619)** — 그리고 `gdpr-delete` 는 SUPER_ADMIN 이 활성 테넌트를 골라 둔 채 불러도 **항상** `'*'` 를 찍는다(아래 § gdpr-delete) · **`lock` · `unlock` · `bulk-lock` 도 이제 같다(TASK-BE-621)** — 플랫폼 스코프 운영자에게는 활성 테넌트와 무관하게 **항상** `'*'`(계정 전체 잠금은 플랫폼 관리자만, 아래 § lock) · `export` 는 여전히 `fan-platform` 기본값(BE-467 net-zero). 상세: [admin-to-account.md § Tenant Confinement](internal/admin-to-account.md#tenant-confinement--x-tenant-id-task-be-467).
- **session-revoke** 는 admin-service 가 활성 테넌트를 동일하게 해소·스탬프하며(TASK-BE-467), auth-service 가 이를 **실제로 enforce** 한다(**TASK-BE-468**): 구체 테넌트가 계정을 소유하지 않으면 force-logout 은 **no-op**(`revokedTokenCount=0`, DB revoke·Redis 무효화 미수행 — enumeration-safe). 부재/`'*'` → net-zero. 상세: [admin-to-auth.md](internal/admin-to-auth.md#tenant-confinement--x-tenant-id-task-be-468).

---

## GET /api/admin/accounts

계정 검색. 이메일이 있으면 이메일로 단건 검색하고, 없으면 `account.read` 권한 보유 시 전체 계정 목록을 페이지네이션으로 반환한다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.read`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_READONLY`

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `email` | string (optional) | 이메일로 단건 검색. 없으면 전체 목록 반환 |
| `tenantId` | string (optional) | **TASK-BE-357** — 검색/목록 대상 테넌트. 생략 시 운영자의 활성/자기 테넌트. `*`는 SUPER_ADMIN 전용 (전 테넌트). 일반 운영자가 effective scope(home ∪ 배정, TASK-BE-326) 밖의 값을 지정하면 `403 TENANT_SCOPE_DENIED`. (`GET /api/admin/audit` 의 `tenantId` 와 동일 시맨틱.) |
| `status` | enum (optional) | **TASK-BE-475** — 계정 상태 필터. `ACTIVE` \| `LOCKED` \| `DORMANT` \| `DELETED` (대소문자 무관). **전체 목록 분기에만 적용** (`email` 단건 검색 시 무시 — 단건은 상태로 필터하지 않는다). 미지정/공백 → 전체 상태 반환(back-compat). 허용 목록 외 값 → `400 VALIDATION_ERROR`. 소비 예: `?status=LOCKED&page=0&size=1` 의 `totalElements` 로 잠금 계정 수 집계(platform-console IAM 개요, TASK-PC-FE-181). |
| `page` | int (default 0) | 전체 목록 조회 시 페이지 번호 |
| `size` | int (default 20, max 100) | 전체 목록 조회 시 페이지 크기 |

**동작 규칙**:
- 모든 분기는 먼저 `tenantId` 를 effective scope 게이트(TASK-BE-249/BE-326 audit 와 동일 경로 재사용)로 해석·검증한다. 생략 시 운영자 자신의 테넌트. out-of-scope → `403 TENANT_SCOPE_DENIED`. **TASK-BE-357** 이전에는 이메일 검색이 `fan-platform` 에 하드코딩돼 있고(다른 테넌트 계정 미검색) 전체 목록은 테넌트 무필터(cross-tenant 노출)였다 — 둘 다 본 task 에서 테넌트 스코프로 정정.
- `email` 파라미터 있음 → 해석된 테넌트 내 이메일 정확 일치 검색. `account.read` 권한 불필요 (기존 동작 유지) — 단 SUPPORT_LOCK 운영자도 테넌트 스코프를 벗어나 이메일 probe 할 수 없다.
- `email` 파라미터 없음 + `account.read` 권한 보유 → 해석된 테넌트의 계정 목록 페이지네이션 반환.
- `email` 파라미터 없음 + `account.read` 권한 미보유 → **403 `PERMISSION_DENIED`** (DENIED `admin_actions` 1행 기록). TASK-MONO-202 — 이전엔 빈 목록 200을 반환했으나, 이는 "권한 없음"과 "계정 0건"을 같은 응답으로 합쳐 소비자(콘솔)가 둘을 구분할 수 없게 만들었다. 이제 무권한은 403, 빈 목록 200은 **권한 보유 + 계정 0건**만 의미한다.
- `size` > 100 → 400 `VALIDATION_ERROR`.
- **TASK-BE-475** — `status` 지정 시 전체 목록 분기에서 해당 상태로만 필터한다 (`(:status IS NULL OR a.status = :status)`, `"*"` 전 테넌트 분기 포함). 허용 목록(`ACTIVE`/`LOCKED`/`DORMANT`/`DELETED`, 대소문자 무관) 외 값은 admin-service 경계에서 즉시 `400 VALIDATION_ERROR` (다운스트림 호출 전 — `AccountServiceClient` 가 downstream 400 을 503 으로 마스킹하는 것을 방지). 공백 `status=` 은 미지정으로 취급.

**Response 200** (email 없음, account.read 보유):
```json
{
  "content": [
    {
      "id": "string",
      "email": "string",
      "status": "ACTIVE",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 150,
  "page": 0,
  "size": 20,
  "totalPages": 8
}
```

**Response 200** (email 있음 → 단건 검색 결과, 또는 email 없음 + account.read 보유 + 계정 0건):
```json
{
  "content": [],
  "totalElements": 0,
  "page": 0,
  "size": 20,
  "totalPages": 0
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | email 없음 + `account.read` 권한 미보유 (전체 목록 조회). email 단건 검색은 무권한 허용 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-357** — 일반 운영자가 effective scope(home ∪ 배정) 밖의 `tenantId` 를 지정한 경우 (email/목록 양 분기 공통) |
| 400 | `VALIDATION_ERROR` | size > 100, 또는 `status` 가 허용 목록 외 값 (TASK-BE-475) |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

**Side Effects** (TASK-BE-357, BE-262 미러링): `403 TENANT_SCOPE_DENIED` 응답 시 `admin_actions` 에 `action_code=ACCOUNT_SEARCH`, `outcome=DENIED`, `tenant_id=operator's`, `target_tenant_id=operator's`, `downstream_detail` 에 시도한 대상 테넌트 기록 — best-effort 쓰기. 실패해도 403 자체는 정상 반환되며 `admin.audit.cross_tenant_deny_failure` 메트릭이 증가한다.

---

## POST /api/admin/accounts/{accountId}/lock

계정 강제 잠금.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `Idempotency-Key: string (required)`
- `X-Tenant-Id: string (optional, 활성 테넌트 — 생략 → 운영자 자신의 테넌트 · SUPER_ADMIN `*` → 계정 행의 테넌트(TASK-MONO-735); [Tenant Confinement](#tenant-confinement--x-tenant-id-task-be-467))`

**Request**:
```json
{
  "reason": "string (required, 상세 사유)",
  "ticketId": "string (optional, 내부 티켓 번호)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "operatorId": "string",
  "lockedAt": "2026-04-12T10:00:00Z",
  "auditId": "string (admin_actions.id)",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

**누가 무엇을 잠그나 (`TASK-BE-621`, 소유자 결정 2026-10-04 «사이트 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다. 계정 전체 잠금은 플랫폼 관리자만.» — [multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 잠금 vs 계정 잠금»](../../features/multi-tenancy.md))** — `unlock` · `bulk-lock`(행마다) 도 같다:

| 운영자 | 하류 `X-Tenant-Id` | 대상 | 결과 |
|---|---|---|---|
| **플랫폼 스코프**(SUPER_ADMIN — `QueryTenantScopeGate.Resolved.isPlatformScope`) | **항상 `'*'`** — 콘솔이 활성 테넌트를 보내도(619 의 gdpr-delete 와 같다) | 계정 행 자신의 테넌트로 찾은 계정 | **계정** 잠금(풀 계정이면 모든 소비자 사이트에서) · `scope = ACCOUNT` |
| 그 밖(사이트 운영자) | 활성 테넌트(BE-467 그대로) | 그 사이트의 **풀 멤버** | 그 사이트 **멤버십만** `LOCKED` — 계정 · 다른 사이트 무변경. `scope = SITE_MEMBERSHIP`, `previousStatus`·`currentStatus` = 멤버십 상태, `siteTenantId` = 그 사이트. 감사 행(`ACCOUNT_LOCK` · `SUCCESS`)의 `downstream_detail` = `SITE_MEMBERSHIP_LOCKED site=<사이트>`(해제는 `SITE_MEMBERSHIP_UNLOCKED`) |
| 〃 | 〃 | 그 사이트의 **자기 계정** | 계정 잠금 · `scope = ACCOUNT`(기존 그대로) |

🔴 사이트 운영자는 **계정 전체 잠금을 풀지 못한다** — 사이트 범위 해제는 그 사이트 멤버십만 본다(멤버십 `ACTIVE` 면 멱등 200, 계정은 `LOCKED` 그대로). `scope` 가 없는 응답(옛 account-service)은 `ACCOUNT` 로 읽는다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-467** — 일반 운영자가 effective scope 밖의 `X-Tenant-Id` 지정 |
| 400 | `STATE_TRANSITION_INVALID` | 이미 LOCKED 또는 DELETED 상태 |
| 400 | `REASON_REQUIRED` | X-Operator-Reason 또는 body reason 누락 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 **또는 cross-tenant** (`X-Tenant-Id` ≠ 계정 테넌트, BE-467) |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | **TASK-MONO-735** — 같은 운영자가 같은 `Idempotency-Key` 로 이 명령을 **이미 한 번 실행했다**(`admin_actions` 에 `(actor_id, action_code, idempotency_key)` 행이 있다). 두 번째 요청은 감사 행을 새로 쓰지 않고 account-service 를 부르지 않는다. 다시 시도하려면 새 키. 🔴 이전엔 두 번째 요청이 감사 INSERT 에서 유니크 키에 걸려 `500 AUDIT_FAILURE` 였다 |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN (호출 자체 거부) |

🔴 **account-service 4xx 매핑 (TASK-MONO-735)**: account-service 의 `404` → **`404 ACCOUNT_NOT_FOUND`**, `409 STATE_TRANSITION_INVALID` → **`400 STATE_TRANSITION_INVALID`**(위 표). 이전 구현은 모든 하위 4xx 를 `503 DOWNSTREAM_ERROR` 로 냈다 — 재시도해도 안 바뀌는 답을 «재시도하라» 로 보여 줬고, 콘솔 확인 대화상자가 같은 키로 다시 보내 위 `500` 에 닿았다(2026-09-26 16차 창 실측). 그 밖의 4xx 는 여전히 `503 DOWNSTREAM_ERROR`.

**Side Effects**: admin_actions 감사 기록 + `admin.action.performed` 이벤트 + account-service에 내부 HTTP lock 명령. `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` 시에도 `admin_actions`에 `outcome=FAILURE` 행이 기록된다 (A10 fail-closed). 하위 `404`/`409` 도 같다(`outcome=FAILURE`).

---

## POST /api/admin/accounts/bulk-lock

여러 계정을 한 번의 요청으로 순차 잠금. 보안 사고 대응 시 사용.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)` — 감사 헤더
- `Idempotency-Key: string (required, ≤64자, UUID 권장)`
- `X-Tenant-Id: string (optional, 활성 테넌트, TASK-BE-467)` — 배치 전체에 1회 해소되어 모든 per-row lock 이 상속. out-of-scope 지정 → `403 TENANT_SCOPE_DENIED` (배치 진입 전). **cross-tenant accountId 는 해당 row 만** `outcome=NOT_FOUND` (`ACCOUNT_NOT_FOUND`) 로 처리되고 배치는 200 유지. [Tenant Confinement](#tenant-confinement--x-tenant-id-task-be-467) 참조.

**Request**:
```json
{
  "accountIds": ["acc-1", "acc-2", "..."],
  "reason": "string (required, ≥8자)",
  "ticketId": "string (optional)"
}
```

**Constraints**:
- `accountIds.length` ≤ 100. 초과 시 `422 BATCH_SIZE_EXCEEDED`.
- 중복 `accountId`는 서버에서 1회만 처리 (입력 순서 보존 dedup).
- `reason`은 최소 8자.

**Response 200**:
```json
{
  "results": [
    { "accountId": "acc-1", "outcome": "LOCKED" },
    { "accountId": "acc-2", "outcome": "NOT_FOUND",      "error": { "code": "ACCOUNT_NOT_FOUND", "message": "..." } },
    { "accountId": "acc-3", "outcome": "ALREADY_LOCKED", "error": { "code": "STATE_TRANSITION_INVALID", "message": "..." } },
    { "accountId": "acc-4", "outcome": "FAILURE",        "error": { "code": "DOWNSTREAM_ERROR", "message": "..." } }
  ]
}
```

- `outcome` ∈ `{LOCKED, NOT_FOUND, ALREADY_LOCKED, FAILURE}`
- 부분 실패 허용: 일부 계정 실패 시에도 응답은 200. 전체 롤백 없음.
- 각 타겟 계정별로 `admin_actions` 1행 기록 (NOT_FOUND/FAILURE 포함).

**Idempotency**:
- `(operator_id, Idempotency-Key)`가 유일 키.
- 동일 key로 재요청 시 body(SHA-256 기준 canonical hash = sorted dedup accountIds + reason + ticketId) 일치하면 **이전 응답 본문을 그대로 반환**하며 추가 다운스트림 호출·감사 row 기록 없음.
- 동일 key + 다른 payload 시 `409 IDEMPOTENCY_KEY_CONFLICT`.
- 저장 TTL 정책은 운영 정책(out-of-scope). 별도 cleanup 작업이 책임.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `account.lock` 미보유 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 |
| 400 | `VALIDATION_ERROR` | 필수 헤더/필드 누락, reason<8자, accountIds 비어있음 |
| 422 | `BATCH_SIZE_EXCEEDED` | accountIds>100 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 동일 key 재사용·payload 불일치 |
| 503 | `CIRCUIT_OPEN` | 전체 요청이 circuit open으로 소화되지 않은 드문 경우 (개별 row 레벨 CB OPEN은 outcome=FAILURE로 기록) |

**Side Effects**: accountId별 `admin_actions` 1행, `admin.action.performed` outbox 이벤트 N건, `admin_bulk_lock_idempotency` 1행.

---

## POST /api/admin/accounts/{accountId}/unlock

계정 잠금 해제.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.unlock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**: Authorization + X-Operator-Reason + Idempotency-Key + `X-Tenant-Id` (optional, 활성 테넌트, BE-467)

**Request**:
```json
{
  "reason": "string (required)",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "LOCKED",
  "currentStatus": "ACTIVE",
  "operatorId": "string",
  "unlockedAt": "2026-04-12T10:00:00Z",
  "auditId": "string",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

`scope` · 하류 헤더 · 범위는 위 § lock 의 표와 같다(`TASK-BE-621`).

**Errors**: lock과 동일 구조 (503 `DOWNSTREAM_ERROR` + 503 `CIRCUIT_OPEN`, 403 `TENANT_SCOPE_DENIED`, cross-tenant → 404 `ACCOUNT_NOT_FOUND` 포함 — BE-467 · 409 `IDEMPOTENCY_KEY_CONFLICT` 와 하위 4xx 매핑 — TASK-MONO-735). `STATE_TRANSITION_INVALID`는 LOCKED가 아닌 상태에서 unlock 시도 시.

---

## POST /api/admin/sessions/{accountId}/revoke

특정 계정의 모든 세션 강제 종료 (refresh token 전체 revoke).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.force_logout`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`, `SECURITY_ANALYST`

**Headers**: Authorization + X-Operator-Reason + Idempotency-Key + `X-Tenant-Id` (optional, 활성 테넌트, BE-467)

**Request**:
```json
{
  "reason": "string (required)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "revokedSessionCount": 3,
  "operatorId": "string",
  "revokedAt": "2026-04-12T10:00:00Z",
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | — |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-467** — 일반 운영자가 effective scope 밖의 `X-Tenant-Id` 지정 |
| 404 | `ACCOUNT_NOT_FOUND` | — |
| 503 | `DOWNSTREAM_ERROR` | auth-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | auth-service circuit breaker OPEN (호출 자체 거부) |

**Tenant note (TASK-BE-467 propagation + TASK-BE-468 enforcement)**: admin-service 는 활성 테넌트를 해소해 `X-Tenant-Id` 로 auth-service force-logout 호출에 전파하고(BE-467), auth-service 가 이를 enforce 한다(BE-468). 구체 테넌트가 대상 계정을 소유하지 않으면 **no-op** — `revokedSessionCount=0`, DB revoke·Redis 무효화 미수행(enumeration-safe; 404 대신 200 count=0 — revoke 는 멱등·count 반환 연산). 부재/`'*'`(SUPER_ADMIN) → net-zero(계정 테넌트 전체 revoke). 상세: [admin-to-auth.md](internal/admin-to-auth.md#tenant-confinement--x-tenant-id-task-be-468).

**Side Effects**: auth-service에 내부 HTTP force-logout 명령 + admin_actions 기록. `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` 시에도 `admin_actions`에 `outcome=FAILURE` 행이 기록된다 (A10 fail-closed).

---

## GET /api/admin/audit

감사 로그 조회 (통합 뷰: admin_actions + login_history + suspicious_events).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `audit.read` (기본). `source=login_history` 또는 `source=suspicious` 필터 사용 시 `security.event.read`도 **추가로** 요구 (union이 아닌 intersection 검증 — 두 권한 모두 필요)
**Granted to roles**:
- `audit.read` only: `SUPPORT_LOCK` (admin_actions만 조회 가능, security source 필터 사용 시 403)
- `audit.read` + `security.event.read`: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST` (모든 source 조회 가능)

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (optional) | 특정 계정 필터 |
| `actionCode` | string (optional) | ACCOUNT_LOCK, SESSION_REVOKE 등 |
| `from` | ISO 8601 datetime | 시작 시각 |
| `to` | ISO 8601 datetime | 종료 시각 |
| `source` | string (optional) | `admin` / `login_history` / `suspicious` |
| `tenantId` | string (optional) | **TASK-BE-249** — 조회할 테넌트. 생략 시 운영자 자신의 테넌트. `*`는 SUPER_ADMIN 전용 (크로스 테넌트). 일반 운영자가 자신의 테넌트가 아닌 값을 지정하면 `403 TENANT_SCOPE_DENIED`. |
| `page` | int (default 0) | — |
| `size` | int (default 20, max 100) | — |

**Response 200**:
```json
{
  "content": [
    {
      "source": "admin",
      "auditId": "string",
      "actionCode": "ACCOUNT_LOCK",
      "operatorId": "string",
      "targetId": "string",
      "reason": "string",
      "outcome": "SUCCESS",
      "occurredAt": "2026-04-12T10:00:00Z"
    },
    {
      "source": "login_history",
      "eventId": "string",
      "accountId": "string",
      "outcome": "FAILURE",
      "ipMasked": "192.168.*.*",
      "geoCountry": "KR",
      "occurredAt": "2026-04-12T09:58:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `audit.read` 또는 source별 추가 권한(`security.event.read`) 부족 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-249** — 일반 운영자가 자신의 테넌트가 아닌 `tenantId`를 지정한 경우 |
| 422 | `VALIDATION_ERROR` | from > to, size > 100 등 |

**Side Effects** (TASK-BE-262): `403 TENANT_SCOPE_DENIED` 응답 시 `admin_actions` 테이블에 `outcome=DENIED`, `tenant_id=operator's`, `target_tenant_id=operator's`, `downstream_detail`에 시도한 대상 테넌트 기록 — best-effort 쓰기. 실패해도 403 자체는 정상 반환되며 `admin.audit.cross_tenant_deny_failure` 메트릭이 증가한다.

**Note**: 이 조회 자체가 **meta-audit**로 기록됨 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A5). PII는 마스킹됨 (IP 일부, 이메일 미포함).

---

## POST /api/admin/auth/login

운영자 자체 로그인. Argon2id 패스워드 검증 + (role 중 `require_2fa=TRUE`가 있으면) TOTP 또는 recovery code 검증 후 operator JWT를 발급한다.

**Auth required**: 없음 (body 기반 인증)
**Required permission**: 없음 (self-login)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_login>"` 상수 기록)

**Headers**: 없음

**Request**:
```json
{
  "operatorId": "string (UUID v7, required)",
  "password":   "string (required)",
  "totpCode":   "string (6 digits, optional)",
  "recoveryCode": "string (optional, XXXX-XXXX-XXXX 형식)"
}
```

- `totpCode`와 `recoveryCode`는 **택일**. 둘 다 제공하거나 둘 다 빠지면 400.
- `require_2fa=FALSE` 운영자(=2FA 비요구 role 집합)는 둘 다 생략하고 호출한다.
- `recoveryCode`는 서버가 대문자/하이픈 normalize 후 Argon2id `verify` 비교한다.

**Response 200**:
```json
{
  "accessToken": "eyJhbGciOi... (operator JWT)",
  "expiresIn": 3600,
  "refreshToken": "eyJhbGciOi... (operator refresh JWT)",
  "refreshExpiresIn": 2592000
}
```

- `accessToken`: `{sub: operator_uuid, iss: "admin-service", jti: uuidV7, iat, exp, token_type: "admin"}`.
- `expiresIn`: 초 단위 TTL (기본 3600, `admin.jwt.access-token-ttl-seconds`로 조정).
- `refreshToken`: `{sub: operator_uuid, iss: "admin-service", jti: uuid, iat, exp, token_type: "admin_refresh"}`. 발급 시 `admin_operator_refresh_tokens(jti)` row가 함께 INSERT 된다(같은 트랜잭션). TASK-BE-040.
- `refreshExpiresIn`: 초 단위 TTL (기본 2,592,000 = 30일, `admin.jwt.refresh-token-ttl-seconds`로 조정).

**Response 401 (ENROLLMENT_REQUIRED, 2FA 등록이 필요한 경우 body 확장)**:
```json
{
  "code": "ENROLLMENT_REQUIRED",
  "message": "Operator must complete 2FA enrollment before login",
  "bootstrapToken": "eyJhbGciOi...",
  "bootstrapExpiresIn": 600
}
```

`bootstrapToken`은 `POST /api/admin/auth/2fa/enroll` 및 `/2fa/verify`에만 사용 가능한 1회용 토큰이다 ([security.md §Bootstrap Token](../../services/admin-service/security.md)).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수 필드(`operatorId`/`password`) 누락 또는 형식 오류 |
| 400 | `BAD_REQUEST` | `totpCode`와 `recoveryCode`가 동시에 제공되었거나, 2FA가 필요한데 둘 다 미제공 |
| 401 | `INVALID_CREDENTIALS` | operator 조회 미스 또는 password_hash 불일치. **TASK-BE-377**: `password_hash = NULL`(OIDC-only 운영자, break-glass 비밀번호 없음)도 동일 fail-closed 분기 — 그런 운영자는 이 로컬 로그인 대신 OIDC token-exchange 로만 인증한다. 미스/null-hash 경로도 dummy Argon2id verify 수행 (타이밍 완화) |
| 401 | `ENROLLMENT_REQUIRED` | 2FA 필수이나 `admin_operator_totp` row 부재. body에 `bootstrapToken` 포함 |
| 401 | `INVALID_2FA_CODE` | TOTP 검증 실패 (±1 window 밖) |
| 401 | `INVALID_RECOVERY_CODE` | recovery code가 어떤 저장된 hash와도 일치하지 않음 (optimistic lock 1회 retry 후) |
| 500 | `AUDIT_FAILURE` | 성공 경로 감사 row 기록 실패 (fail-closed). 실패 경로의 secondary 감사 실패는 삼켜지고 원래 응답이 유지됨 |

감사: `action_code = OPERATOR_LOGIN`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.login`, `reason = "<self_login>"`, `twofa_used = TRUE|FALSE` (2FA 경로 여부), `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/token-exchange

**TASK-BE-298 / ADR-MONO-014 (ACCEPTED) § D2/D3 — RFC 8693 token exchange.**

platform-console 이 보유한 **IAM OIDC `platform-console-web` access token**
(subject token)을 단명 **operator access token**(`token_type=admin`,
`iss=admin-service`)으로 교환한다. console 이 `/api/admin/**` operator
엔드포인트(BE-296 registry 포함)를 operator trust boundary 확장 없이 호출할 수
있게 하는 IAM-side bridge다 (ADR-MONO-014 D1 Option A 기각 — `OperatorAuthenticationFilter`
는 2번째 issuer 를 수용하지 않으며, 본 엔드포인트는 password+TOTP login mint 의
**형제 발급 경로**일 뿐이다).

**Auth required**: 없음 (body 의 IAM OIDC subject token 이 인증 수단)
**Required permission**: 없음 (operator-token 발급 경로 — RBAC 평가 대상 아님)
**X-Operator-Reason**: 요구 없음 (`/login` 과 동일 — 요청자 본인 인증 플로우)

**Headers**: 없음 (subject token 은 Authorization 헤더가 아닌 body)

**Request** (RFC 8693 `application/json`):
```json
{
  "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
  "subject_token": "eyJhbGciOi... (IAM OIDC platform-console-web access token)",
  "subject_token_type": "urn:ietf:params:oauth:token-type:access_token"
}
```

- `grant_type` MUST be `urn:ietf:params:oauth:grant-type:token-exchange`.
- `subject_token_type` MUST be `urn:ietf:params:oauth:token-type:access_token`.
- `subject_token` 은 auth-service(SAS)가 `platform-console-web` client 에
  발급한 OIDC access token. 검증 정책(JWKS·iss·aud·exp·nbf·RS256·clock skew)은
  [security.md §IAM OIDC Subject-Token Validation](../../services/admin-service/security.md)
  가 canonical.
- 이 엔드포인트는 **재발급(re-exchange)** 모델이다 — 별도 operator-refresh
  state 를 두지 않는다 (ADR-MONO-014 D2). console 은 자신의 IAM refresh 로
  rotate 한 access token 으로 매번 재교환한다.

**Response 200**:
```json
{
  "accessToken": "eyJhbGciOi... (operator JWT)",
  "expiresIn": 3600,
  "tokenType": "admin"
}
```

- `accessToken`: `/api/admin/auth/login` 성공 응답의 `accessToken` 과
  **동일 구조·동일 발급기·동일 서명 키**: `{sub: operator_uuid,
  iss: "admin-service", jti: uuidV7, iat, exp, token_type: "admin"}`. tenant
  스코프는 **`admin_operators.tenant_id` 에서만** 결정된다 (ADR-002 `'*'`
  SUPER_ADMIN sentinel 포함) — subject token 의 어떤 claim(`tenant_id` 등)도
  스코프 결정에 사용하지 않는다.
- `expiresIn`: 초 단위 TTL. operator access TTL
  (`admin.jwt.access-token-ttl-seconds`, 기본 3600) 이하.
- refresh token 은 발급하지 않는다 (re-exchange 모델, ADR-MONO-014 D2).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `subject_token` 누락 |
| 400 | `BAD_REQUEST` | `grant_type` 또는 `subject_token_type` 가 RFC 8693 지정 값과 불일치 |
| 401 | `TOKEN_INVALID` | subject token 서명/`iss`/`aud`/`exp`/`nbf` 검증 실패, `platform-console-web` 가 아닌 client 에 발급된 토큰, IAM OIDC access token 이 아님(예: operator/bootstrap 토큰 제시), auth-service JWKS 도달 불가, **또는** OIDC subject 에 매핑되는 활성 `admin_operators` row 부재(미매핑/비활성/잠금 — fail-closed, 토큰 미발급) |
| 403 | `MFA_REQUIRED` | **TASK-MONO-771 (ADR-MONO-080 D4 · R2, 소유자 결정 OD-2 · OD-3)** — subject token 검증과 운영자 해석을 **모두 통과한 뒤**, 아래 «2단계 요구» 가 참이고 subject token 의 `amr` 에 `mfa` 가 없다(`amr` 부재 포함). operator token 미발급 |
| 500 | `INTERNAL_ERROR` | **TASK-MONO-771** — 2단계 요구 여부를 계산하는 로컬 읽기 실패(아래). operator token 미발급 |

> **Fail-closed invariant**: subject token 검증 또는 operator 해석의
> 어떠한 모호함도 `401 TOKEN_INVALID`(기존 `OperatorUnauthorizedException`)로
> 귀결되며 **operator token 은 절대 발급되지 않는다**. OIDC token 으로부터
> 스코프가 상승하는 경로는 존재하지 않는다.

**2단계 요구 (TASK-MONO-771)** — 아래 둘 중 하나라도 참이면 요구한다:

1. **역할 (R2 · OD-2)**: 운영자가 `admin_roles.require_2fa = TRUE` 인 역할을 하나라도 보유(`anyRoleRequires2fa` — 지금은 break-glass `/login` 만 보던 그 플래그를 주 경로에서도 문다).
2. **테넌트 진입 정책 (OD-3)**: 운영자의 **admin 범위** — 홈 테넌트(`admin_operators.tenant_id`) ∪ `operator_tenant_assignment` 행의 테넌트 — 중 하나라도 [진입 정책](#tenant-entry-policy-task-mono-771)이 켜져 있다(`tenant_entry_policy.require_mfa = TRUE`). **파트너십 host reach 는 넣지 않는다** — 파트너십은 admin 범위를 넓히지 않는다(ADR-MONO-045, 위 § Partnership Management). 그 테넌트로의 **assume** 은 assume-tenant 게이트가 따로 막는다([auth-to-admin.md](internal/auth-to-admin.md) `mfaRequired`). 홈이 `'*'`(플랫폼 범위)인 운영자에게 `'*'` 는 정책을 가질 수 없는 값이라(정책 행은 `'*'` 를 받지 않는다) 이 항은 assignment 행만 본다 — 플랫폼 운영자의 2단계는 1번(역할)과 assume 게이트가 문다.

판정 술어는 `"mfa" ∈ amr` 하나다([jwt-standard-claims.md](../../../../../platform/contracts/jwt-standard-claims.md) `amr` 행) — 수단 값(`otp` 등)을 읽지 않는다. 요구 여부를 계산하는 읽기(역할 · 정책 · assignment — 모두 admin_db 로컬)가 실패하면 **발급하지 않는다**(fail-closed) — 응답은 `500 INTERNAL_ERROR` 다. 🔴 `401` 로도 `403 MFA_REQUIRED` 로도 메우지 않는다: 판정을 끝내지 못한 것을 «운영자 아님» 이나 «2단계 필요» 로 보이게 하면 콘솔이 엉뚱한 화면으로 보낸다(콘솔은 5xx 를 «세션 불가» 로 읽는다 — § 2.6).

🔴 **`403 MFA_REQUIRED` 는 «운영자 아님» 이 아니다.** 콘솔은 **`401` 만** «운영자 아님 → `/onboarding`» 으로 읽는다. 이 403 을 받으면 IAM 2단계 단계 상승으로 보낸다([console-integration-contract.md § 2.6](../../../../platform-console/specs/contracts/console-integration-contract.md)) — 401 로 내면 `SUPER_ADMIN` 이 온보딩(테넌트 생성) 화면으로 오도된다(TASK-MONO-771 AC-0 F1). 이 403 은 subject token 의 주인에게만 돌아가고, 그 사람은 이미 운영자로 해석됐으므로 운영자 존재를 새로 드러내지 않는다. 응답 본문은 공통 에러 형식 그대로이며 **어느 항(역할 · 정책 · 어느 테넌트)이 요구했는지 싣지 않는다**.

정책 · 역할 변경은 **다음 교환부터** 적용된다(admin-service 로컬 읽기, 캐시 없음). 이미 발급된 operator token 은 만료(≤ `admin.jwt.access-token-ttl-seconds`)까지 유효하다 — 콘솔은 IAM refresh 마다 재교환하므로 그 시점에 걸린다.

**Side Effects**: 없음 (`admin_actions` 기록 없음 — `/login` 과 달리 이
엔드포인트는 자체 감사 row 를 남기지 않는다. 후속 operator 명령이 각자의 감사
row 를 남긴다. operator-token 발급 자체의 추적은 IAM OIDC subject token 의
auth-service 측 발급 이력으로 커버됨). OIDC↔operator 링크 키 결정·근거는
[data-model.md §OIDC Subject ↔ Operator Link Key](../../services/admin-service/data-model.md).

---

## POST /api/admin/auth/refresh

운영자 refresh JWT를 회전시켜 새 access + refresh 쌍을 발급한다. TASK-BE-040.

**Auth required**: 없음 (body의 refresh JWT가 인증 수단)
**Required permission**: 없음 (self-managed session)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_refresh>"` 상수 기록)

**Headers**: 없음

**Request**:
```json
{
  "refreshToken": "string (required, operator refresh JWT)"
}
```

**Behavior**:
1. JWT 서명/exp/iss 검증 + `token_type=admin_refresh` 확인
2. `admin_operator_refresh_tokens.findByJti(jti)` 조회 — 미존재 → 401 `INVALID_REFRESH_TOKEN`
3. row.`revoked_at != null` → **재사용 탐지**: 해당 operator의 모든 미revoked refresh token을 `revoke_reason=REUSE_DETECTED`로 일괄 revoke + 401 `REFRESH_TOKEN_REUSE_DETECTED`
4. 정상: 기존 jti를 `revoke_reason=ROTATED`로 revoke, 새 access + 새 refresh 발급, 새 row insert(`rotated_from`=기존 jti)

**Response 200**:
```json
{
  "accessToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "refreshToken": "eyJhbGciOi...",
  "refreshExpiresIn": 2592000
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken` 누락 |
| 401 | `INVALID_REFRESH_TOKEN` | 서명/exp/issuer 실패, `token_type` 불일치, jti 미등록, operator 불일치 |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | 이미 revoked된 jti의 재사용 — 체인 전체 invalidate |

감사: `action_code = OPERATOR_REFRESH`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.refresh`, `reason = "<self_refresh>"`, `outcome = SUCCESS|FAILURE`(REUSE_DETECTED 시 `downstream_detail = "REUSE_DETECTED"`).

---

## POST /api/admin/auth/logout

운영자 자체 logout. access JWT의 jti를 Redis 블랙리스트에 등록하고(잔여 TTL), 선택적으로 제공된 refresh token을 revoke한다. 204를 응답. TASK-BE-040.

**Auth required**: Yes (operator JWT, `token_type=admin`) — 본인 확인 위해 인증 필요. 029-1 bypass 목록에 포함되지 않는다.
**Required permission**: 없음 (self-managed session)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_logout>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <operator-token>`

**Request** (optional body):
```json
{
  "refreshToken": "string (optional)"
}
```

**Behavior**:
- access JWT의 jti를 Redis SETEX `admin:jti:blacklist:{jti}`, TTL = (access exp - now). `OperatorAuthenticationFilter`는 이 키를 매 요청마다 확인하여 hit 시 401 `TOKEN_REVOKED` 반환. Redis 다운 시 fail-closed (401).
- `refreshToken`이 제공되면 그 jti를 `revoke_reason=LOGOUT`으로 revoke. 미제공이거나 검증 실패 시 무시 (best-effort).

**Response 204**: 본문 없음

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator JWT 누락/만료/변조 |
| 401 | `TOKEN_REVOKED` | 이미 logout된 jti로 재호출 |
| 500 | `INTERNAL_ERROR` | Redis blacklist 쓰기 실패 — 클라이언트 재시도 |

감사: `action_code = OPERATOR_LOGOUT`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.logout`, `reason = "<self_logout>"`, `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/2fa/enroll

운영자 자신의 TOTP 2FA 최초(또는 재) 등록. 서버가 160-bit secret 생성·AES-GCM 암호화 저장, Argon2id hash된 10개 recovery codes를 동시 발급한다.

**Auth required**: Bootstrap token (operator JWT 불가). `Authorization: Bearer <bootstrap-token>`, `token_type = "admin_bootstrap"`, `jti` 1회 소비 (상세: [security.md §Bootstrap Token](../../services/admin-service/security.md)).
**Required permission**: 없음 (self-enrollment)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_enrollment>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <bootstrap-token>`

**Request**: body 없음

**Response 200**:
```json
{
  "otpauthUri": "otpauth://totp/admin-service:op@example.com?secret=...&issuer=admin-service&algorithm=SHA1&digits=6&period=30",
  "recoveryCodes": ["A1B2-C3D4-E5F6", "..."],
  "enrolledAt": "2026-04-14T10:00:00Z"
}
```

`recoveryCodes`는 **평문 1회 응답 후 서버 저장은 Argon2id 해시만**. 재호출 시 기존 secret + recovery codes는 전부 무효화되고 새 값으로 대체된다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `INVALID_BOOTSTRAP_TOKEN` | bootstrap token 부재/만료/재사용(jti 소비됨)/서명 실패/`token_type` 불일치 |
| 500 | `AUDIT_FAILURE` | 감사 row 기록 실패 (fail-closed) |

감사: `action_code = OPERATOR_2FA_ENROLL`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.2fa_enroll`, `twofa_used = FALSE`, `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/2fa/verify

Enroll 직후 또는 로그인 플로우(TASK-BE-029-3)에서 운영자가 제출한 TOTP 코드를 검증한다. 성공 시 `admin_operator_totp.last_used_at`이 갱신된다.

**Auth required**: Bootstrap token.
**Required permission**: 없음 (self-enrollment)
**X-Operator-Reason**: 요구 없음

**Headers**:
- `Authorization: Bearer <bootstrap-token>`

**Request**:
```json
{
  "totpCode": "string (6 digits, required)"
}
```

**Response 200**:
```json
{ "verified": true }
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `totpCode`이 6자리 숫자가 아님 |
| 401 | `INVALID_BOOTSTRAP_TOKEN` | bootstrap token 부재/만료/재사용/서명 실패 |
| 401 | `INVALID_2FA_CODE` | 코드가 ±1 window(30s step) 안에서 일치하지 않음 (enrollment 미존재 시 동일 코드 반환) |
| 500 | `AUDIT_FAILURE` | 감사 row 기록 실패 |

감사: `action_code = OPERATOR_2FA_VERIFY`, `target_type = OPERATOR`, `permission_used = auth.2fa_verify`, `twofa_used = FALSE` (본 엔드포인트 자체는 로그인 경로가 아님 — `twofa_used = TRUE`는 029-3의 `/login` 감사 row에서 기록).

---

## POST /api/admin/auth/2fa/recovery-codes/regenerate

운영자가 TOTP 백업 복구 코드를 모두 소진하거나 분실한 경우 새로운 10개 코드 세트를 발급받는다. 호출 시 기존 `recovery_codes_hashed`는 즉시 완전 교체되며 이전 코드는 무효화된다.

**Auth required**: Yes (operator JWT, `token_type=admin`) — 정식 operator 토큰 필요. bootstrap token 사용 불가.
**Required permission**: 없음 (self-service: 본인의 복구 코드만 재발급)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_recovery_regenerate>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <operator-token>`

**Request**: body 없음

**Response 200**:
```json
{
  "recoveryCodes": ["A1B2-C3D4-E5F6", "G7H8-I9J0-K1L2", "..."]
}
```

`recoveryCodes`는 **평문 1회 응답 후 서버 저장은 Argon2id 해시만**. 정확히 10개 반환된다. 응답 직후 클라이언트가 안전하게 저장해야 하며 서버는 이후 평문 코드를 보유하지 않는다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator JWT 누락/만료/변조 |
| 401 | `TOKEN_REVOKED` | 이미 logout된 jti |
| 404 | `TOTP_NOT_ENROLLED` | `admin_operator_totp` row 부재 (TOTP 미등록 상태에서는 재발급 불가 — 먼저 `/api/admin/auth/2fa/enroll` 필요) |

**Side Effects**: `admin_operator_totp.recovery_codes_hashed` 컬럼이 새 JSON 배열로 완전히 교체된다 (이전 코드 즉시 무효화). `last_used_at`도 함께 갱신된다.

**Note**: 본 엔드포인트는 평문 복구 코드를 응답으로만 반환하며 서버 로그에 출력하지 않는다 (R4 준수).

---

## Operator Roles

**Reference matrix**: Role × permission 매트릭스의 canonical source는 [specs/services/admin-service/rbac.md — Seed Matrix](../../services/admin-service/rbac.md#seed-matrix-role--permission) 한 곳이다. 본 계약에서는 중복 테이블을 유지하지 않는다 (drift 방지).

### 403 Response Shape

`PERMISSION_DENIED` 응답은 본 문서의 [Common Error Format](#common-error-format)을 따른다. permission 관련 추가 필드는 응답에 **노출하지 않는다** (attack surface 축소 — 클라이언트는 어떤 permission이 누락되었는지 알 수 없다). 거부 상세는 `admin_actions.detail`에만 기록된다.

```json
{
  "code": "PERMISSION_DENIED",
  "message": "Operator is not authorized to perform this action.",
  "timestamp": "2026-04-13T10:00:00Z"
}
```

---

## POST /api/admin/accounts/{accountId}/gdpr-delete

GDPR/PIPA 삭제권 이행. 계정 상태를 DELETED로 전이하고 PII를 즉시 마스킹한다 (이메일 SHA-256 해시 교체, 프로필 필드 NULL 처리).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock` (기존 권한 재사용)
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `Idempotency-Key: string (required)`
- `X-Tenant-Id: string (optional, 활성 테넌트, BE-467)`

**Request**:
```json
{
  "reason": "string (required, GDPR 삭제 사유)",
  "ticketId": "string (optional, 내부 티켓 번호)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "status": "DELETED",
  "maskedAt": "2026-04-18T10:00:00Z | null",
  "auditId": "string (admin_actions.id)",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

**누가 무엇을 지우나 (`TASK-BE-619`, 소유자 결정 2026-10-03 — [multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 탈퇴 vs 계정 삭제»](../../features/multi-tenancy.md))**:

| 운영자 | 하류 `X-Tenant-Id` | 대상 | 결과 |
|---|---|---|---|
| **플랫폼 스코프**(SUPER_ADMIN — `QueryTenantScopeGate.Resolved.isPlatformScope`) | **항상 `'*'`** — 콘솔이 활성 테넌트를 보내도. 플랫폼 관리자는 역할로 가른다(콘솔은 늘 활성 테넌트를 보낸다) | 계정 행 자신의 테넌트로 찾은 계정 | 계정 삭제 + PII 마스킹(풀 계정이면 모든 소비자 사이트에서) · `scope = ACCOUNT` |
| 그 밖(사이트 운영자) | 활성 테넌트(BE-467 그대로) | 그 사이트의 **풀 멤버** | **아무것도 지우지 않는다** — 그 사이트 멤버십만 `LEFT`. `scope = SITE_MEMBERSHIP`, `status` = 계정의 지금 상태, `maskedAt = null`, `siteTenantId` = 그 사이트. 감사 행(`admin_actions`, `GDPR_DELETE` · `SUCCESS`)의 `downstream_detail` = `SITE_MEMBERSHIP_LEFT site=<사이트>` |
| 〃 | 〃 | 그 사이트의 **자기 계정** | 계정 삭제 + 마스킹 · `scope = ACCOUNT` (기존 그대로) |

`scope` 가 없는 응답(옛 account-service)은 `ACCOUNT` 로 읽는다. 콘솔은 `SITE_MEMBERSHIP` 을 «이 사이트에서만 탈퇴 처리» 로 따로 보여 준다(«삭제됨» 으로 읽히지 않게).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-467** — 일반 운영자가 effective scope 밖의 `X-Tenant-Id` 지정 |
| 400 | `STATE_TRANSITION_INVALID` | 이미 DELETED 상태 |
| 400 | `REASON_REQUIRED` | X-Operator-Reason 또는 body reason 누락 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 **또는 cross-tenant** (BE-467) |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

**Side Effects**: admin_actions 감사 기록 + `admin.action.performed` 이벤트 + account-service에 내부 HTTP GDPR 삭제 명령. PII 마스킹은 account-service 내에서 동일 트랜잭션으로 수행.

---

## GET /api/admin/accounts/{accountId}/export

GDPR/PIPA 이식권 이행. 계정의 개인 데이터를 JSON으로 내보낸다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `audit.read`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `X-Tenant-Id: string (optional, 활성 테넌트, BE-467)`

**Response 200**:
```json
{
  "accountId": "string",
  "email": "string",
  "status": "string",
  "createdAt": "2026-01-01T00:00:00Z",
  "profile": {
    "displayName": "string",
    "phoneNumber": "string",
    "birthDate": "1990-01-15",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul"
  },
  "exportedAt": "2026-04-18T10:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-BE-467** — 일반 운영자가 effective scope 밖의 `X-Tenant-Id` 지정 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 **또는 cross-tenant** (BE-467) |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

**Side Effects**: 이 조회 자체가 **meta-audit**로 기록됨 (admin_actions에 action_code=DATA_EXPORT, outcome=SUCCESS). PII가 마스킹되지 않은 원본 데이터 접근이므로 감사 추적 필수.

---

## POST /api/admin/accounts/{accountId}/2fa/reset

**TASK-MONO-771 (ADR-MONO-080 D4, 티켓 Edge Case 2 · 소유자 결정 OD-6).** 계정 평면 2단계 인증(auth-service `account_totp` — [auth-api.md § IdP 브라우저 화면 — 2단계 인증](auth-api.md))을
지운다. 인증 앱과 복구 코드를 모두 잃은 사람을 위한 길이다. 운영자 break-glass TOTP(`admin_operator_totp`)와는 무관하다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.2fa_reset` (신규 — [rbac.md](../../services/admin-service/rbac.md#permission-keys))
**Granted to roles**: `SUPER_ADMIN`, `SECURITY_ANALYST` — **플랫폼 범위 grant(`tenant_id='*'`)만**. `TENANT_ADMIN` 은 이 키를 갖지 않는다.

🔴 **플랫폼 전용인 이유 (OD-6)**: 계정은 `ADR-MONO-080` 뒤 **개인 풀 계정**이다 — 리셋은 그 사람의 회사 진입뿐 아니라 쇼핑 · 팬 로그인 보안까지 바꾼다. 회사 관리자에게
그 사람의 개인 계정 보안을 내리는 권한을 주지 않는다(D5 «회수는 측면만» 과 같은 이유). 키를 가졌어도 grant 가 플랫폼 범위가 아니면 `403 TENANT_SCOPE_DENIED`(2차 방어 —
`tenant.manage` 의 inline platform-scope 검사와 같은 모양).

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `Idempotency-Key: string (required)` — § lock 과 같은 규칙(`(actor_id, action_code, idempotency_key)` 재사용 → `409 IDEMPOTENCY_KEY_CONFLICT`)

`X-Tenant-Id` 는 보내지 않는다(보내도 무시) — 대상은 계정 id 하나이고 테넌트로 좁히지 않는다(플랫폼 전용).

**Request**:
```json
{
  "reason": "string (required, 상세 사유 — 본인 확인 근거)",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "operatorId": "string",
  "resetAt": "2026-10-08T10:00:00Z",
  "auditId": "string (admin_actions.id)"
}
```

**사유 (S6 구현으로 정밀화)**: `X-Operator-Reason` 헤더 **와** body `reason` 이 **둘 다** 필요하다(아래 `400 REASON_REQUIRED` 행의 «또는 누락» = 어느 한쪽이라도 없으면) — § bulk-lock 과 같은 모양이다. 감사 행의 `reason` 은 body `reason`(상세 사유 — 본인 확인 근거)이고, `ticketId` 는 body 의 것이다. 헤더는 «사유 없는 명령은 들어오지 않는다» 의 관문이다.

**처리 순서**: 권한(`account.2fa_reset`, 없으면 `403 PERMISSION_DENIED` + DENIED 행) → 사유 → 플랫폼 범위(아니면 `403 TENANT_SCOPE_DENIED` + DENIED 행, 하류 호출 없음) → `Idempotency-Key` 재사용 검사(`409`) → `IN_PROGRESS` 감사 행 → auth-service 내부 명령 → 감사 완료 행(`SUCCESS` · `FAILURE`).

**효과**: 그 계정의 `account_totp` 행(비밀 · 복구 코드 · 대기 행 포함)을 지운다. **세션은 끊지 않는다**(이미 2단계를 거친 세션은 그대로 — 탈취 의심이면 `POST /api/admin/sessions/{accountId}/revoke` 를 따로 부른다). 다음 로그인은 등록 없는 계정의 흐름이고, 정책이 켜진 진입에서 거절되면 등록 화면으로 간다 — 그 등록은 인증된 이메일을 요구하고 등록 알림 메일을 보낸다(OD-4). 하류 호출은 admin-service → auth-service 내부 명령 [`POST /internal/auth/accounts/{accountId}/second-factor/reset`](internal/admin-to-auth.md#post-internalauthaccountsaccountidsecond-factorreset)(TASK-MONO-771 S6). 성공 감사 행의 `downstream_detail` = `wasConfirmed=true|false`(`false` = 미완료 등록의 대기 행만 지웠다).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `account.2fa_reset` 미보유 |
| 403 | `TENANT_SCOPE_DENIED` | 키를 가졌으나 grant 가 플랫폼 범위(`'*'`)가 아님 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 또는 body `reason` 누락 |
| 400 | `VALIDATION_ERROR` | `Idempotency-Key` 누락 |
| 404 | `ACCOUNT_NOT_FOUND` | 계정 미존재 — 🔵 S6: 지울 행이 **없을 때만** 판정된다(auth-service 가 그때 account-service 에 묻는다). 행이 있으면 계정 존재와 무관하게 지운다(삭제된 계정에 남은 비밀도 지워지는 편이 옳다) |
| 404 | `TOTP_NOT_ENROLLED` | 지울 등록이 없다(대기 행도 없음) — 리셋할 것이 없다는 사실을 운영자에게 보인다(플랫폼 전용 표면이라 열거 방어 대상 아님). 🔵 S6: 하류 재시도가 «이미 지운» 뒤에 닿은 경우도 이 코드다 — [admin-to-auth.md § 멱등](internal/admin-to-auth.md#post-internalauthaccountsaccountidsecond-factorreset) |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 운영자 · 같은 키로 이미 실행 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | auth-service 호출 실패(🔵 S6: 행이 없을 때 auth-service 의 account-service 읽기 실패 포함 — 아무것도 지워지지 않았다) |

**Side Effects**: `admin_actions` — `action_code=ACCOUNT_2FA_RESET`, `permission_used=account.2fa_reset`, `target_type=ACCOUNT`, `target_id=<accountId>`, `outcome=SUCCESS|FAILURE`(하류 실패도 행을 남긴다 — A10 fail-closed, § lock 과 같다) + `admin.action.performed` outbox.

---

## GET /api/admin/me

현재 로그인한 운영자의 정보를 반환한다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: 없음 (유효한 operator JWT만 필요)

**Response 200**:
```json
{
  "operatorId": "string (UUID v7)",
  "email": "string",
  "displayName": "string",
  "status": "ACTIVE",
  "roles": ["SUPER_ADMIN"],
  "totpEnrolled": true,
  "lastLoginAt": "2026-04-24T10:00:00Z",
  "createdAt": "2026-01-01T00:00:00Z",
  "homeTenantId": "*"
}
```

> **TASK-BE-626**: `homeTenantId` 는 `GET /api/admin/operators` 항목과 **같은 응답 DTO** 를 공유해서 이 응답에도 실린다 — 의미는 아래 § GET /api/admin/operators 의 `homeTenantId` 행과 같다(로그인한 운영자 자신의 HOME 테넌트).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |

---

## GET /api/admin/operators

운영자 목록 조회 (페이지네이션). **TASK-MONO-175 / ADR-MONO-020**: 결과는 `tenantId`(활성 테넌트)로 스코핑된다 — 해당 테넌트에 **속한** 운영자(HOME 테넌트 `admin_operators.tenant_id == tenantId` **또는** `operator_tenant_assignment` 에 `tenantId` 배정(D1 N:M, TASK-BE-326))만 반환.

> **TASK-BE-338 (ADR-MONO-020 D3 amendment)**: `operator_tenant_assignment.org_scope`(per-assignment 데이터-스코프, 부서 subtree-root id 배열; `NULL ⟺ ["*"]` net-zero)는 이 list 응답(`/api/admin/operators`)에는 노출되지 않으며, internal assignment-check edge([internal/auth-to-admin.md](./internal/auth-to-admin.md) `GET /internal/operator-assignments/check` 의 `orgScope` 응답 필드)로 반환되어 assume-tenant 토큰 `org_scope` claim 으로 전파된다.
>
> **TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up)**: org_scope **조회·설정 admin API 가 추가됨** — `GET /api/admin/operators/{operatorId}/assignments`(활성 테넌트 scope, org_scope 포함) + `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope`(set/clear, reason-gated). 콘솔 설정 UI(TASK-PC-FE-050)가 이 API 를 소비해 SQL 시드 없이 운영자별 데이터-스코프를 설정한다. (이전 "set/write API 는 follow-up" note 는 본 task 로 해소.)

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `status` | string (optional) | `ACTIVE` 또는 `SUSPENDED` 필터. 미지정 시 전체 반환 |
| `tenantId` | string (optional) | **TASK-MONO-175** — 조회할 테넌트(활성 테넌트). 생략 시 운영자 자신의 home 테넌트. `*` 는 SUPER_ADMIN(플랫폼 스코프) 전용 — 크로스 테넌트 전체 목록. 일반 운영자가 자신의 **effective scope**(home ∪ 배정, TASK-BE-326 dual-read) 밖의 `tenantId` 를 지정하면 `403 TENANT_SCOPE_DENIED`. console-web 은 활성 테넌트 쿠키 값을 기본 전달(audit `GET /api/admin/audit` 의 `tenantId` 와 동일 패턴). |
| `page` | int (default 0) | 페이지 번호 |
| `size` | int (default 20, max 100) | 페이지 크기 |

**Response 200**:
```json
{
  "content": [
    {
      "operatorId": "string (UUID v7)",
      "email": "string",
      "displayName": "string",
      "status": "ACTIVE",
      "roles": ["SUPPORT_LOCK"],
      "totpEnrolled": false,
      "lastLoginAt": "2026-04-24T10:00:00Z",
      "createdAt": "2026-01-01T00:00:00Z",
      "homeTenantId": "acme",
      "operatorContext": {
        "defaultAccountId": "acc-uuid-7"
      }
    }
  ],
  "totalElements": 10,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

**Response item shape (per-operator)**:

| 필드 | 타입 | 조건 |
|---|---|---|
| `operatorId` | string (UUID v7) | 항상 노출 |
| `email` | string | 항상 노출 |
| `displayName` | string | 항상 노출 |
| `status` | enum (`ACTIVE` / `SUSPENDED`) | 항상 노출 |
| `roles` | array<string> | 항상 노출 (빈 배열 허용) |
| `totpEnrolled` | boolean | 항상 노출 |
| `lastLoginAt` | string (ISO-8601) \| absent | 한 번도 로그인하지 않은 운영자는 키 자체 omit |
| `createdAt` | string (ISO-8601) | 항상 노출 |
| `homeTenantId` | string | 항상 노출. **TASK-BE-626** — 운영자의 HOME 테넌트(`admin_operators.tenant_id`), 플랫폼 운영자는 `*`. 이 목록은 HOME **또는** 배정(`operator_tenant_assignment`) 으로 그 테넌트에 속한 운영자를 함께 돌려주므로, 소비자가 둘을 가르려면 이 값을 `tenantId` 와 비교한다(`homeTenantId == tenantId` ⇒ HOME, 아니면 배정으로만 속함). 소비 예: 운영자 그룹 «멤버 추가» 선택기 — 그룹 멤버는 HOME 이 그룹 테넌트인 운영자만 받으므로(§ Operator Group Management · `422 GROUP_MEMBER_TENANT_MISMATCH`) 배정으로만 속한 운영자를 고르기 전에 막는다(platform-console TASK-PC-FE-319). 파트너십 참여자(ADR-MONO-045)는 이 목록에 나오지 않는다 — 배정 행이 아니라 별도 참여 테이블이다 |
| `operatorContext` | object \| **omit** | profile carrier (TASK-BE-308 신규). `admin_operators.finance_default_account_id` 가 NULL 이면 키 자체 omit (field-level `@JsonInclude(Include.NON_NULL)`); 값이 있으면 `{ "defaultAccountId": "<uuid>" }` 형태로 노출. v1 은 `defaultAccountId` 단일 키만 carrying — `me/profile` (TASK-BE-306) + `{operatorId}/profile` (TASK-BE-307) request body 의 carrier 와 동일 shape. console-web admin profile-edit dialog 가 dialog 초기값으로 사용 (TASK-PC-FE-018). v1 finance 단일 카드만 populating; 다른 carrier 키는 별 task 로 컬럼+노출 동시 확장 |

> **carrier shape 대칭성 (TASK-BE-308)**: 본 list 응답의 item-level `operatorContext` 은 다음과 byte-identical 한 shape 를 사용한다:
> - `GET /api/admin/console/registry` 응답의 finance product item 의 `operatorContext` ([console-registry-api.md § Item shape](./console-registry-api.md))
> - `PATCH /api/admin/operators/me/profile` (TASK-BE-306) request body 의 `operatorContext`
> - `PATCH /api/admin/operators/{operatorId}/profile` (TASK-BE-307) request body 의 `operatorContext`
>
> 결과적으로 console-web 은 동일 zod schema / TypeScript 타입으로 4개 surface 를 통일 처리한다 (read on list + read on registry + write on me/profile + write on admin/{id}/profile).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | **TASK-MONO-175** — 일반(비-플랫폼) 운영자가 effective scope(home ∪ 배정) 밖의 `tenantId` 를 지정한 경우 |
| 400 | `VALIDATION_ERROR` | size > 100 또는 status 값 오류 |

**Side Effects**: 없음 (read).

---

## GET /api/admin/operators/lookup

**TASK-MONO-777 (2026-10-08 UTC, 소유자 결정 «L2 운영자 이메일 조회»)** — 활성 테넌트의 **운영자**를 이메일로 정확히 찾아, 그 사람이 콘솔에 들고 오는 계정 id(`admin_operators.oidc_subject` = 콘솔 OIDC/assume 토큰의 `sub`, `ADR-MONO-060` A)를 돌려준다. 소비자: erp 직원 ↔ 계정 연결 제안(platform-console `console-integration-contract.md` § 2.4.8) — 인사 담당자가 계정 UUID 를 직접 치지 않게.

**왜 `GET /api/admin/accounts?email=` 가 아닌가**: 그 검색은 활성 테넌트의 `account_db` **계정 행**을 찾는다. 연결이 필요한 값은 **운영자 측면의 `oidc_subject`** 이고, 콘솔 자격이 `iam` 테넌트에 있는 운영자(데모 시드 운영자)·셀프 온보딩 운영자·`ADR-MONO-080` 풀 직원은 회사 테넌트에 계정 행이 없어 그 검색이 못 찾는다(실측 — 루트 `TASK-MONO-777` § AC-0 (b)).

**왜 이 경로인가**: 찾는 대상이 운영자 측면이므로 `operators` 계열에 둔다. 정적 세그먼트 형제 `GET /api/admin/operators/grantable-roles` 와 같은 모양이고, `GET /api/admin/operators/{operatorId}` 맨 경로는 없어 경로 변수와 충돌하지 않는다. `GET /api/admin/operators?email=` 로 목록에 분기를 얹지 않은 이유: 그 목록은 `@RequiresPermission(operator.manage)` 로 선언돼 있어, 권한 없는 분기를 넣으려면 그 선언을 걷고 수동 검사로 바꿔야 한다(권한 매핑 변경) — 소유자 결정은 «권한 표 변경 없음» 이다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: **없음** — `GET /api/admin/accounts` 의 `email` 분기와 같은 모양(권한 키 없음 · 테넌트 게이트만). GET 이므로 `@RequiresPermission` deny-default 가드(변이 전용) 대상이 아니다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `email` | string (**required**) | 정확 일치(앞뒤 공백 제거 · 소문자화 후 비교 — `admin_operators.email` 은 정규화 저장). 비었거나 없으면 `400 VALIDATION_ERROR` |
| `tenantId` | string (optional) | 찾을 테넌트(활성 테넌트). 생략 → 운영자 자신의 홈 테넌트. `QueryTenantScopeGate`(home ∪ 배정, TASK-BE-326 — `GET /api/admin/accounts` · `GET /api/admin/audit` 와 같은 판정기)로 해석한다. `*` 는 플랫폼 운영자 전용(전 테넌트) |

**동작 규칙**:
- 대상 = 해석된 테넌트에 **속한** 운영자(HOME `admin_operators.tenant_id == tenantId` **또는** `operator_tenant_assignment` 배정 — `GET /api/admin/operators` 와 같은 소속 술어) 중 `email` 이 일치하고, **`status = ACTIVE`** · **`oidc_subject` 있음**(콘솔 로그인이 이어진 운영자) · `confined_tenant_id` 가 없거나 그 테넌트(TASK-MONO-751 — 그 테넌트에 들어올 수 있는 사람)인 행. 이 셋이 아니면 그 테넌트에 콘솔로 들어와 연결을 **수락할 수 없는** 사람이므로 돌려주지 않는다.
- 같은 이메일이 HOME 운영자와 다른 테넌트에서 배정된 운영자 둘로 걸릴 수 있다 → `content` 는 배열이고 항목마다 `tenantId`(그 운영자의 HOME 테넌트)를 싣는다. 정렬 = `tenantId` 오름차순(결정적).
- 🔴 **«없음» 과 «범위 밖» 은 같은 응답이다** — 일치 0건, 그리고 일반 운영자가 effective scope 밖의 `tenantId` 를 지정한 경우 모두 **`200 {"content":[]}`**(바이트 동일). `GET /api/admin/accounts` 는 범위 밖을 `403 TENANT_SCOPE_DENIED` 로 답하지만, 이 조회는 소유자 결정으로 그 구별을 응답에서 지운다(존재 비노출 — 어떤 테넌트 이름이 범위 안인지도 응답으로 캐지 못한다).
- 감사: 성공 read 는 감사 행 없음(BE-486 read-path 규약). 범위 밖은 응답은 빈 200 이지만 게이트가 기존대로 best-effort DENIED `admin_actions` 1행을 남긴다(`action_code=ACCOUNT_SEARCH` 재사용 — 같은 «이메일로 계정 id 찾기» 행위이고 새 action code 는 권한 레지스트리 매핑을 요구하므로 만들지 않는다 · `downstream_detail` 에 시도한 테넌트). probe 는 응답으로는 안 보이고 감사에는 남는다(rbac.md D3).

**Response 200**:
```json
{
  "content": [
    {
      "accountId": "0199de70-0000-7000-8000-00000000ad03",
      "displayName": "Demo Operator",
      "tenantId": "demo-corp"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `accountId` | string | `admin_operators.oidc_subject` — 콘솔 토큰의 `sub`. erp `employees.account_id` 에 넣는 값 |
| `displayName` | string | 운영자 표시 이름 |
| `tenantId` | string | 그 운영자의 HOME 테넌트(`admin_operators.tenant_id`, 플랫폼 운영자는 `*`). 배정으로만 속한 운영자는 조회 테넌트와 다르다 |

`operatorId` · `email` · 역할은 싣지 않는다 — 연결에 필요한 것만.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 400 | `VALIDATION_ERROR` | `email` 없음/공백 |

`403` 은 없다(권한 키 없음 · 범위 밖은 빈 200).

**Side Effects**: 없음 (read) — 범위 밖일 때의 best-effort DENIED 감사 1행 외.

---

## GET /api/admin/operators/grantable-roles

**TASK-BE-388 (ADR-MONO-024 D3 read mirror)** — 호출 운영자가 **부여 가능한** seed role 이름 배열을 반환한다. 운영자 생성(`POST /api/admin/operators`) / 역할편집(`PATCH /api/admin/operators/{operatorId}/roles`) 폼이 부여 **불가**한 role 을 애초에 노출하지 않도록 하는 **read 힌트**다. 판정 규칙은 grant 강제 결정지점인 `RoleGrantGuard`(ADR-MONO-024 D3, `requireGrantable`)와 **동일 SoT** 이며, 본 엔드포인트는 그 규칙의 부작용 없는(감사 미기록) read 미러다. **최종 강제는 여전히 producer 측 `RoleGrantGuard` 의 `403 ROLE_GRANT_FORBIDDEN`** (본 엔드포인트는 힌트일 뿐, 우회 시 생성/편집에서 여전히 거부됨).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN` (+ `TENANT_ADMIN` 등 `operator.manage` 보유 delegated 역할)

**Headers**:
- `Authorization: Bearer <operator-token>`

**부여 가능 판정 규칙** (RoleGrantGuard D3 와 byte-identical):

- **플랫폼 스코프 caller** — effective admin-grant 스코프(`effectiveAdminScope(actor, operator.manage)`)에 `*` 포함(SUPER_ADMIN) → **전체 seed role** 반환(무제약, net-zero).
- **비-플랫폼 caller** — 다음을 **모두** 만족하는 role 만 반환:
  - role 이 `SUPER_ADMIN`(플랫폼/특권 role)이 **아님**, **그리고**
  - role 의 permission 집합이 caller 자신의 permission 집합의 **부분집합**(≤-own; empty-permission role 은 자명히 부여 가능).
- **정렬**: seed / `admin_roles.id` 오름차순 안정 순서.

**Response 200**:
```json
{
  "roles": ["SUPER_ADMIN", "TENANT_ADMIN", "SUPPORT_LOCK"]
}
```

| 필드 | 타입 | 조건 |
|---|---|---|
| `roles` | array<string> | caller 가 부여 가능한 seed role 이름 (role-id 안정 순서). 빈 배열 허용 |

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 (표준 `@RequiresPermission` 흐름) |

**Side Effects**: 없음 (read; 감사 미기록 — grant 강제 시에만 `RoleGrantGuard` 가 DENIED row 기록).

---

## GET /api/admin/roles

**TASK-BE-486** — RBAC **role 카탈로그**를 반환한다: 모든 `admin_roles` 행 + 각 role 이 보유한 permission 키 집합(role→permission 매핑). 콘솔 「권한 세트」 화면(TASK-PC-FE-228)이 소비한다 — role 을 "permission 의 집합"으로 프레이밍한 뷰가 곧 권한 세트다. **읽기 전용** — role/permission 정의(생성·수정·삭제)는 이 API 로 변경 불가하며 여전히 seed + Flyway 로만 바뀐다.

> **scope 는 전역(global)** — `admin_roles` / `admin_role_permissions` 에는 tenant 컬럼이 없다([data-model.md § admin_roles](../../services/admin-service/data-model.md)). 응답에 `scope: "global"` 을 명시하여 프런트가 tenant-scoped 로 오인하지 않도록 한다(신규 tenant-scoped role 모델 도입 아님).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN` (V0022) + `TENANT_ADMIN` (V0033)

> **권한 키 결정 (TASK-BE-486 Acceptance Criteria)**: 신규 `role.read` / `permission.read` 키를 도입하지 않고 기존 `operator.manage` 를 재사용한다. 가장 가까운 형제 엔드포인트 `GET /api/admin/operators/grantable-roles`(role 카탈로그 read)가 이미 `operator.manage` 로 게이트되며, role/permission 카탈로그는 운영자 관리(역할 부여)의 참조 데이터이므로 감사·권한 모델상 동일 독자층(운영자를 관리하는 role)이 소비한다. `operator.manage` 는 이미 seed 매트릭스에서 `SUPER_ADMIN`·`TENANT_ADMIN` 에 부여되어 있어 seed 변경 0([rbac.md § Permission Keys](../../services/admin-service/rbac.md)).

**Headers**:
- `Authorization: Bearer <operator-token>`

**정렬**: seed / `admin_roles.id` 오름차순 안정 순서(`grantable-roles` 와 동일). 각 role 의 `permissions` 배열은 오름차순(사전식) 정렬 — 결정적 출력.

**Response 200**:
```json
{
  "scope": "global",
  "roles": [
    {
      "id": 1,
      "name": "SUPER_ADMIN",
      "description": "Full platform administrator",
      "permissions": ["account.force_logout", "account.lock", "account.read", "account.unlock", "audit.read", "operator.manage", "security.event.read", "subscription.manage", "tenant.manage"]
    }
  ]
}
```

| 필드 | 타입 | 조건 |
|---|---|---|
| `scope` | string | 항상 `"global"` (tenant 무관 전역 카탈로그) |
| `roles` | array<object> | role 목록, `admin_roles.id` 오름차순. 빈 배열 허용 |
| `roles[].id` | int | `admin_roles.id` (내부 PK, 안정 정렬 키) |
| `roles[].name` | string | `admin_roles.name` (UPPER_SNAKE_CASE, UNIQUE) |
| `roles[].description` | string | `admin_roles.description` (관리 UI 설명) |
| `roles[].permissions` | array<string> | 해당 role 의 permission 키 집합(`admin_role_permissions.permission_key`), 오름차순 정렬. 빈 배열 허용 |

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조/부재 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 (표준 `@RequiresPermission` 흐름) |

**Side Effects**: 없음 (read; 감사 미기록. 권한 거부 시에만 `admin_actions` 에 DENIED row — rbac.md D3).

---

## GET /api/admin/permissions

**TASK-BE-486** — 전체 **permission 키 catalog** 를 반환한다([rbac.md § Permission Keys](../../services/admin-service/rbac.md) 기준). 콘솔 「권한」 화면(TASK-PC-FE-227)이 소비한다. catalog 는 코드 canonical 소스(`Permission.catalog()`)에서 나오며 seed 상태와 무관하게 정의된 모든 키를 포함한다 — seed 이후 추가되었으나 아직 아무 role 에도 부여되지 않은 키도 누락 없이 노출(task Edge Cases).

> **scope 전역** — permission 키는 tenant 무관 전역 정의. `scope: "global"` 명시.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage` (roles 엔드포인트와 동일 결정 — 위 § GET /api/admin/roles 권한 키 결정 참조)
**Granted to roles**: `SUPER_ADMIN` (V0022) + `TENANT_ADMIN` (V0033)

**Headers**:
- `Authorization: Bearer <operator-token>`

**정렬**: rbac.md § Permission Keys 의 canonical 순서.

**Response 200**:
```json
{
  "scope": "global",
  "permissions": [
    "account.read",
    "account.lock",
    "account.unlock",
    "account.force_logout",
    "audit.read",
    "security.event.read",
    "operator.manage",
    "tenant.manage",
    "subscription.manage",
    "tenant.admin.delegate",
    "partnership.manage"
  ]
}
```

| 필드 | 타입 | 조건 |
|---|---|---|
| `scope` | string | 항상 `"global"` |
| `permissions` | array<string> | 전체 permission 키 catalog (rbac.md 순서). `<missing>` 감사 sentinel 은 제외 |

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조/부재 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |

**Side Effects**: 없음 (read; 권한 거부 시에만 DENIED row).

> **`GET /api/admin/permission-sets` 미구현 (의도적)**: 선택 엔드포인트였으나 생략한다 — `GET /api/admin/roles` 가 이미 각 role 을 permission 키 집합과 함께 반환하므로 「권한 세트」 화면(TASK-PC-FE-228)이 요구하는 permission-set 뷰를 완전히 커버한다. 같은 테이블에 대한 두 번째 프레이밍 엔드포인트는 중복이다.

---

## GET /api/admin/operators/{operatorId}/assignments

**TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up)** — 운영자의 `operator_tenant_assignment` 행을 **활성 테넌트(`X-Tenant-Id`)로 scope** 하여 반환한다. 0 또는 1행(per-(operator, active-tenant)); 활성 테넌트에 명시 배정이 없으면 빈 배열(`home-tenant-only` 운영자 → org_scope 부적용 신호). 타 테넌트 assignment 는 노출하지 않는다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Tenant-Id: string (활성 테넌트 — 생략 시 빈 배열)`

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Response 200**:
```json
{
  "assignments": [
    {
      "tenantId": "acme-corp",
      "orgScope": ["dept-sales", "dept-eng"],
      "permissionSetId": 7
    }
  ]
}
```

**Response item shape (per-assignment)**:

| 필드 | 타입 | 조건 |
|---|---|---|
| `tenantId` | string | 항상 노출 (= 활성 테넌트) |
| `orgScope` | array<string> \| **omit** | 부서 subtree-root id 배열. 컬럼 `NULL`(미설정, `⟺ ["*"]` net-zero)이면 키 자체 omit (field-level `@JsonInclude(NON_NULL)` — `"orgScope": null` 로 렌더링하지 않음). 명시적 `[]`(zero-scope)는 비-null 이므로 노출 |
| `permissionSetId` | number \| **omit** | per-assignment permission set (`admin_roles.id`). `NULL`(operator-level role 상속)이면 omit |

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | `X-Tenant-Id` 가 호출자의 `operator.manage` 스코프 밖 (TASK-MONO-737 — 형제 `PUT …/org-scope` 와 같은 판정; 읽기이므로 DENIED 감사 행 없음) |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |

**Side Effects**: 없음 (read).

---

## PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope

**TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up)** — (operator, tenant) assignment 행의 `org_scope`(부서 subtree-root id 배열)를 **설정·해제**한다. 운영자-admin 은 자기 **활성 테넌트** 내 assignment 만 관리할 수 있으므로 `path tenantId` 는 `X-Tenant-Id` 와 일치해야 한다. org_scope 는 assignment 행에만 존재하며, 행 생성/삭제(운영자를 테넌트에 배정/해제)는 본 task 범위 밖이다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Tenant-Id: string (required, 활성 테넌트 — path tenantId 와 일치해야 함)`
- `X-Operator-Reason: string (required, 감사 사유)`

**Path Variables**:
- `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)
- `tenantId` — assignment 의 테넌트 (활성 테넌트와 일치해야 함)

**Request**:
```json
{
  "orgScope": ["dept-sales", "dept-eng"]
}
```

`orgScope` 값 의미 (end-to-end 보존):

| 값 | 의미 | 영속 |
|---|---|---|
| `null` (또는 키 생략) | clear (전체 테넌트, `⟺ ["*"]` net-zero) | 컬럼 `NULL` |
| `[]` | 명시적 zero-scope (`NULL` 과 구분, BE-338 fail-closed 의미) | 빈 JSON 배열 `[]` |
| `["<dept-id>", ...]` | 정규화(trim · blank 거부 · 중복 제거 order 보존 · 최대 256개) 후 영속. IAM 은 erp 부서 트리를 모르므로 id 형식·존재 검증 안 함(비-blank 문자열만; erp 가 소비 시점 검증, ERP-BE-008) | JSON 배열 |

**Response 200** (갱신된 assignment — `GET .../assignments` element 와 동일 shape):
```json
{
  "tenantId": "acme-corp",
  "orgScope": ["dept-sales", "dept-eng"],
  "permissionSetId": 7
}
```

`orgScope` 가 `null`(clear)이면 응답에서도 키 omit (`@JsonInclude(NON_NULL)`).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_MISMATCH` | `path tenantId != X-Tenant-Id` (자기 활성 테넌트 밖 assignment 편집 시도) |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 404 | `ASSIGNMENT_NOT_FOUND` | (operatorId, tenantId) assignment 행 부재 (org_scope 는 assignment 행 전용) |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |
| 400 | `INVALID_REQUEST` | `orgScope` 배열에 blank 원소 포함 / 256개 초과 |

**Side Effects**: 성공 시 행 `org_scope` 컬럼 `saveAndFlush`(BE-335 명시 flush — dirty UPDATE 영속 보장). `admin_actions` 에 `action_code=OPERATOR_ORG_SCOPE_UPDATE, permission_used=operator.manage, target_id=operatorId, target_tenant_id=path tenantId` 기록. 갱신된 `org_scope` 는 다음 assume-tenant 토큰 발급 시 `org_scope` claim 으로 전파(BE-338).

---

## POST /api/admin/operators/{operatorId}/assignments/{tenantId}

**TASK-BE-347 (ADR-MONO-024 D3-i)** — operator↔tenant `operator_tenant_assignment` row 생성 ("내 직원에게 내 테넌트 접근 부여"). 생성된 row 는 whole-tenant(`org_scope=null` ⟺ `["*"]`, `permission_set_id=null` = operator-level role 상속); 이후 `org_scope` 는 `PUT .../org-scope` 로 refine.

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Tenant confinement (ADR-024 D2, step-1)**: actor 는 path `tenantId` 에 대해 `operator.manage` 스코프를 보유해야 한다 — `TENANT_ADMIN @ acme` 는 acme 에만 배정 가능. SUPER_ADMIN(`'*'`) net-zero.

**`fan-platform` 은 배정 대상이 아니다 (`TASK-MONO-750`, `ADR-MONO-079` D4-A 라이더 R3)**: path `tenantId` 가 `fan-platform` 이면 **모든 actor**(SUPER_ADMIN 포함)에게 `403 TENANT_SCOPE_DENIED` — 그 테넌트는 플랫폼 운영자(`'*'`)만 assume 하고, 플랫폼 운영자는 assignment row 없이 platform-scope 로 닿는다([auth-to-admin.md](./internal/auth-to-admin.md) 판정 규칙 2·5). 고객사 운영자에게 row 를 만들어 줄 길이 없다. 배정 **해제**(`DELETE`)는 좁히는 방향이라 막지 않는다.

**Response 201** (`GET .../assignments` element 와 동일 shape — `tenantId`, `orgScope`(omitted when null), `permissionSetId`(omitted when null)).

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 path `tenantId` 에 대한 admin-grant 스코프 밖 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 409 | `ASSIGNMENT_ALREADY_EXISTS` | (operatorId, tenantId) assignment 행이 이미 존재 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |

**Side Effects**: 성공 시 `admin_actions` 에 `action_code=OPERATOR_ASSIGNMENT_CREATE, permission_used=operator.manage, target_id=operatorId, target_tenant_id=tenantId` 기록. `403 TENANT_SCOPE_DENIED` 시 best-effort DENIED row(`admin.audit.cross_tenant_deny_failure`).

---

## DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}

**TASK-BE-347 (ADR-MONO-024 D3-i)** — operator↔tenant assignment row 삭제.

**Auth required**: Yes · **Required permission**: `operator.manage` · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Tenant confinement**: 동일 (path `tenantId` ∈ actor admin-grant 스코프). 확인은 존재 검사 이전에 수행(cross-tenant 는 존재 여부 누설 없이 403).

**Response 204** (no content).

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 path `tenantId` 에 대한 admin-grant 스코프 밖 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 404 | `ASSIGNMENT_NOT_FOUND` | (operatorId, tenantId) assignment 행 부재 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |

**Side Effects**: 성공 시 `admin_actions` 에 `action_code=OPERATOR_ASSIGNMENT_DELETE` 기록.

---

## PATCH /api/admin/operators/{operatorId}/identity:link

**TASK-BE-373 (ADR-MONO-034 U3 / U6 step 3c)** — 기존 운영자(`admin_operators` 행)를 **중앙 identities 레지스트리**(account-service `account_db`, step 3a)의 identity 에 **opt-in 으로 링크**한다. `admin_operators.identity_id` 컬럼(V0036)을 설정하여, 오늘의 nullable `oidc_subject` 브릿지를 1급 링크로 정식화한다(U1). AIP-136 colon-verb (`identity:link`).

> **보안 불변식 (ADR-034 U3/U7 — 절대 변형 금지)**: 링크는 **explicit / authorized / audited / idempotent / reversible** 해야 하며 **email-match 는 necessary-but-NOT-sufficient**. 같은 이메일이라는 사실만으로는 절대 auto-link 하지 않는다(§ 1.3 cross-tenant email-collision privilege-escalation vector 차단) — 명시적 요청이 링크를 authorize 한다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage` (관리 대상 운영자의 home tenant 스코프 — 다른 operator-management mutation 과 동일 게이트)
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`

**Path Variables**:
- `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**:
```json
{
  "accountId": "acc-1111",
  "tenantId": "wms"
}
```
- `accountId` (required) — 중앙 identity 를 링크할 소비자 account
- `tenantId` (required) — account 가 속한 tenant (step-3b resolve EP `GET /internal/tenants/{tenantId}/accounts/{accountId}/identity` 의 scope)

**링크 결정 흐름** (cheapest / fail-closed 우선):
1. operator 조회 (없으면 404)
2. `operator.manage` tenant-scope authorize (관리 대상 home tenant; SUPER_ADMIN `'*'` net-zero)
3. account email(`GET /internal/accounts/{accountId}`) + 중앙 identity(`resolveIdentity`) **fail-CLOSED** 해결 — account-service 불가용/오류 시 링크 **실패**(503; issuance fail-soft 의 반대 — 링크는 링크-시점 authorization 결정)
4. resolve 성공이지만 `identityId == null` → 422 `ACCOUNT_IDENTITY_UNRESOLVABLE`
5. email-match **necessary** (case-insensitive); 불일치 → 422 `IDENTITY_LINK_EMAIL_MISMATCH` (링크 안 함)
6. idempotency: 이미 **같은** identity 에 링크됨 → no-op SUCCESS(여전히 감사); **다른** identity 에 링크됨 → 409 `OPERATOR_ALREADY_LINKED`(먼저 unlink 필요)
7. `admin_operators.identity_id` 설정 + 감사

**Response 200**:
```json
{
  "operatorId": "00000000-0000-7000-8000-0000000000aa",
  "identityId": "idy-7777",
  "alreadyLinked": false
}
```
`alreadyLinked=true` 는 같은 identity 재링크(idempotent no-op success).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 대상 운영자 home tenant 에 대한 admin-grant 스코프 밖 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 422 | `ACCOUNT_IDENTITY_UNRESOLVABLE` | account 가 중앙 identity 미보유(200 + null) — fail-closed |
| 422 | `IDENTITY_LINK_EMAIL_MISMATCH` | operator email ≠ account email (necessary 조건 위반) |
| 409 | `OPERATOR_ALREADY_LINKED` | 이미 **다른** identity 에 링크됨 (먼저 unlink 필요) |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 불가용/오류 — **fail-CLOSED**, 링크 안 함 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |

**Side Effects**: 성공 시 `admin_operators.identity_id` `saveAndFlush`(BE-335 명시 flush). `admin_actions` 에 `action_code=OPERATOR_IDENTITY_LINK, permission_used=operator.manage, target_id=operatorId, target_tenant_id=operator home tenant` 기록(idempotent no-op 도 감사). V0036 은 backfill 없음 — `oidc_subject`→identity backfill 은 오직 이 명시 surface 로만 수행(U3). 링크는 role namespace 를 병합하지 않는다(U5: identity ≠ authorization).

---

## PATCH /api/admin/operators/{operatorId}/identity:unlink

**TASK-BE-373 (ADR-MONO-034 U3 / U6 step 3c)** — 링크 **역전**: `admin_operators.identity_id` 를 `NULL` 로 클리어한다(U6 reversibility — ADR-032 step 4 credential consolidation 시작 전까지 가역). AIP-136 colon-verb (`identity:unlink`).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage` (link 와 동일 게이트)
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`

**Request**: body 없음.

**Response 200**:
```json
{
  "operatorId": "00000000-0000-7000-8000-0000000000aa",
  "previousIdentityId": "idy-7777",
  "alreadyUnlinked": false
}
```
이미 unlink 상태에서 호출 → `alreadyUnlinked=true` + `previousIdentityId=null` (idempotent no-op success).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 대상 운영자 home tenant 에 대한 admin-grant 스코프 밖 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |

**Side Effects**: 성공 시(이미 unlink 가 아니면) `admin_operators.identity_id` `saveAndFlush` 로 `NULL` 복원. `admin_actions` 에 `action_code=OPERATOR_IDENTITY_UNLINK, permission_used=operator.manage, target_id=operatorId, target_tenant_id=operator home tenant` 기록(idempotent no-op 도 감사). downstream 호출 없음(로컬 컬럼만 클리어).

---

## POST /api/admin/operators

신규 운영자 계정 생성. 🔴 **`TASK-MONO-772` 부터 플랫폼 범위(`tenantId='*'`) 운영자 전용이다** — 회사(비-`'*'`) 테넌트의 운영자는 [§ Operator Invitation](#operator-invitation-task-mono-772) 으로만 생긴다(아래 «TASK-MONO-772» 블록).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`
- `Idempotency-Key: string (required)`

**Request**:
```json
{
  "email": "string (required, valid email format)",
  "displayName": "string (required, 1–64자)",
  "password": "string (optional, 제공 시 ≥10자, 영문+숫자+특수문자 각 1자 이상)",
  "roles": ["SUPPORT_LOCK"],
  "tenantId": "string (required, 1–32자, TASK-BE-249)",
  "reuseExistingIdentity": false
}
```

> `tenantId`: 생성할 운영자가 속할 테넌트 ID. `*`는 SUPER_ADMIN 전용 플랫폼 스코프 센티넬이며, 플랫폼 스코프(`tenant_id='*'`) 운영자만 다른 플랫폼 스코프 운영자를 생성할 수 있다. 이를 위반하면 `403 TENANT_SCOPE_DENIED`.

> **TASK-BE-377 (ADR-MONO-035 O2 / step 4c)** — `password` (optional): 누락/공백이면 운영자는 **OIDC-only**(`admin_operators.password_hash = NULL`)로 생성된다 — PRIMARY 로그인은 통합 IAM OIDC credential(`token-exchange` 경유). 제공되면 break-glass 로컬 비밀번호로 hash 되어 잔존하며 정책(≥10자, 영문+숫자+특수문자)을 강제한다. OIDC-only 운영자는 `POST /api/admin/auth/login` 으로 로그인할 수 없고(`401 INVALID_CREDENTIALS`) OIDC 로만 인증한다 (security.md §Operator Credential Convergence).

> **TASK-MONO-772 (ADR-MONO-080 D6 · 구현자 결정 D-7) — `'*'` 전용으로 좁힌다 · 아래 `TASK-MONO-334` 블록을 대체한다.**
> - `tenantId` 가 `'*'` 가 **아니면** → **`422 OPERATOR_INVITATION_REQUIRED`**. 아무것도 만들지 않고 account-service 도 부르지 않는다. 회사 운영자의 입구는 초대 하나다([§ Operator Invitation](#operator-invitation-task-mono-772)).
> - 판정 순서: `operator.manage` → 사유 · 본문 검증 → `tenantId='*'` 이면 기존 플랫폼 범위 규칙(비-플랫폼 actor → `403 TENANT_SCOPE_DENIED`) → 비-`'*'` 이면 `422`(actor 와 무관). ADR-024 D2 · D3 검사(`TenantScopeGuard` · `RoleGrantGuard`)는 `'*'` 생성에 지금처럼 걸린다.
> - **왜 «선행 계정 조건만 지우기» 가 아닌가**: ADR-080 D6 은 334 규칙을 «초대 → 인증된 본인 수락» 으로 **대체**한다. 전제(가입 계정)만 지우고 생성을 남기면 334 가 닫은 «계정 없는 운영자» 가 다시 열린다. `'*'` 는 ADR-080 D1(플랫폼 관리자 분리 · 부트스트랩)이라 남긴다.
> - **잃는 것 (실측, 772 AC-0 F1)**: 회사 테넌트에 **break-glass 비밀번호 전용** 운영자를 새로 만들 수 없다. OIDC 쪽으로는 잃는 것이 없다 — 334 로 만든 운영자는 `oidc_subject` 를 받지 않아 원래 OIDC 주 경로로 로그인할 수 없었다(채우는 곳은 셀프 온보딩 · 일회성 백필뿐). 이미 있는 운영자 행은 바뀌지 않는다.
> - 334 의 확인(account-service `GET /internal/accounts?email&tenantId&excludePoolMembers=true` · `422 OPERATOR_ACCOUNT_NOT_FOUND` · 그 확인의 `503 DOWNSTREAM_ERROR`)은 이 엔드포인트에서 **사라진다**. `excludePoolMembers` 파라미터와 콘솔 사전 게이트도 호출자가 없어져 같은 슬라이스에서 걷는다([admin-to-account.md](internal/admin-to-account.md#get-internalaccountsemail) · 콘솔 § 2.4.3).
> - 🔵 **구현 = `TASK-MONO-772` S7.** 그 슬라이스가 머지되기 전까지는 아래 334 동작이 그대로다(콘솔이 비-`'*'` 생성을 더 부르지 않게 된 뒤 — S5 — 에 걷는다).
>
> **~~TASK-MONO-334~~ (위 `TASK-MONO-772` 블록이 대체 — 772 S7 머지 전까지의 동작으로만 남는다) (ADR-MONO-035 amendment) — 가입 계정 선행 조건**: `email` 은 대상 `tenantId` 에 **이미 가입된 계정**이어야 한다. producer 는 생성 전 account-service `GET /internal/accounts?email&tenantId` 로 존재를 확인하고, 계정이 없으면 `422 OPERATOR_ACCOUNT_NOT_FOUND` 로 거부한다 — 운영자의 PRIMARY 로그인이 그 계정의 통합 IAM credential 이기 때문. 이는 TASK-PC-FE-179 의 fail-soft advisory 를 **대체**한다: break-glass `password` 가 있어도 계정 없는 "허수 운영자"는 더 이상 생성되지 않는다(break-glass 는 이제 이미 계정이 존재하는 운영자의 secondary 로그인일 뿐). **`tenantId='*'`(플랫폼 스코프)는 면제** — account_db 에 `*` tenant 행이 없어 확인 대상이 없다(SUPER_ADMIN 부트스트랩; `FirstAdminProvisioner` self-service 온보딩 경로는 이 use-case 를 거치지 않으므로 무관). account-service 불가용 시 **fail-closed**: `503 DOWNSTREAM_ERROR` 로 거부하고 운영자를 생성하지 않는다.

> **TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d)** — `reuseExistingIdentity` (optional, nullable; 누락 시 `false`): 중앙 identities 레지스트리(account-service, step 3a)에서 동일 `(tenantId, email)` identity 가 **이미 존재할 때** 그 identity 를 **재사용**할지에 대한 명시적 opt-in. 신규 운영자 생성 직후 account-service `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` 를 호출해 identity 를 resolve-or-create 하고 그 `identity_id` 를 `admin_operators.identity_id` 에 링크한다 — step 3 이후 생성되는 모든 운영자가 중앙 identity 에 연결되어 identity divergence 가 멈춘다.
> - **no silent merge (U3)**: identity 가 이미 존재하지만 `reuseExistingIdentity` 가 `false`/누락이면, account-service 는 `EXISTS_NOT_REUSED`(identityId=null)를 반환하고 운영자는 **unlinked** 로 생성된다(나중에 `identity:link` step-3c surface 로 명시 링크). 이메일 일치만으로 자동 병합되지 않는다.
> - **fail-soft (U4)**: identity 인프라 불가용(account-service down/오류)이어도 운영자 생성은 **실패하지 않는다** — identity 호출 실패는 swallow + log.warn 되고 운영자는 unlinked 로 생성된다(3c 의 fail-closed 와 반대). 따라서 이 필드/링크 단계는 201 응답 자체를 막지 않는다.
> - **`tenantId='*'` (플랫폼 스코프) SKIP**: account_db 에 `*` tenant 행이 없어 identities FK 앵커가 없으므로 플랫폼 스코프 운영자는 identity resolve/link 를 **전면 생략**(unlinked; 필요 시 3c 로 수동 링크).
> - 링크는 role namespace 를 병합하지 않는다(U5: identity ≠ authorization). 응답 shape 은 변경 없음(identity_id 는 응답 본문에 노출하지 않음 — IdP 내부 상관키).

**Response 201**:
```json
{
  "operatorId": "string (UUID v7)",
  "email": "string",
  "displayName": "string",
  "status": "ACTIVE",
  "roles": ["SUPPORT_LOCK"],
  "totpEnrolled": false,
  "createdAt": "2026-04-24T10:00:00Z",
  "auditId": "string (admin_actions.id)",
  "tenantId": "string (TASK-BE-249)"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 비-플랫폼 스코프 운영자가 `tenantId='*'` 운영자 생성 시도 (TASK-BE-249); 또는 actor 가 `body.tenantId` 에 대한 admin-grant 스코프 밖 (ADR-024 D2, step-1) |
| 403 | `ROLE_GRANT_FORBIDDEN` | **TASK-BE-347 (ADR-024 D3)** — 비-플랫폼 actor 가 `SUPER_ADMIN` 또는 자신이 보유하지 않은 권한을 가진 role 을 부여 시도 (≤-own grant-menu) |
| 409 | `OPERATOR_EMAIL_CONFLICT` | 동일 (tenant_id, email) 운영자 이미 존재 |
| 422 | `OPERATOR_INVITATION_REQUIRED` | **TASK-MONO-772 (S7 부터)** — `tenantId` 가 `'*'` 가 아니다. 회사 운영자는 [§ Operator Invitation](#operator-invitation-task-mono-772) 으로만 생긴다. 미생성 · 하류 호출 없음 |
| 422 | ~~`OPERATOR_ACCOUNT_NOT_FOUND`~~ | **TASK-MONO-334 — 772 S7 머지 전까지만.** `email` 이 대상 `tenantId` 에 가입된 계정이 아님 (`*` 플랫폼 스코프는 면제). S7 뒤 이 엔드포인트는 이 코드를 내지 않는다(코드 자체는 `identity:link` 가 계속 쓴다) |
| 503 | ~~`DOWNSTREAM_ERROR`~~ | **TASK-MONO-334 — 772 S7 머지 전까지만.** account-service 불가용으로 가입 계정 여부를 확인 불가 (fail-closed, 미생성). S7 뒤 `'*'` 생성은 account-service 를 부르지 않는다(identity 링크는 `'*'` 에서 생략 — 아래 BE-374) |
| 400 | `ROLE_NOT_FOUND` | `roles` 배열에 존재하지 않는 role 이름 포함 |
| 400 | `VALIDATION_ERROR` | email 형식 오류 / password 정책 위반 / displayName 길이 초과 / tenantId 누락 또는 32자 초과 |

**Side Effects**: 성공 시 `admin_actions`에 `action_code=OPERATOR_CREATE, tenant_id=actor.tenantId, target_tenant_id=body.tenantId` 기록. **TASK-BE-374**: 비-`*` tenant 의 경우 identity resolve-or-create + `admin_operators.identity_id` 링크가 같은 트랜잭션에서 수행됨(fail-soft — identity 호출 실패 시 unlinked 로 생성, 별도 audit row 없음 — OPERATOR_CREATE 가 provisioning 행위를 이미 커버). `403 TENANT_SCOPE_DENIED` 시 `admin_actions`에 `outcome=DENIED, tenant_id=actor's, target_tenant_id=actor's, downstream_detail`에 시도한 `body.tenantId` 기록 — best-effort 쓰기 (TASK-BE-262). 실패해도 403 자체는 정상 반환되며 `admin.audit.cross_tenant_deny_failure` 메트릭이 증가한다.

---

## Operator Invitation (TASK-MONO-772)

**ADR-MONO-080 D6 · 라이더 R4 · 소유자 결정 OD-1 ~ OD-4 (2026-10-09 UTC).** 회사(비-`'*'`) 테넌트의 운영자는 **초대 → 인증된 본인 수락** 으로만 생긴다.
운영자 관리자가 이메일로 초대하고, 그 이메일을 **인증한 개인(풀) 계정**이 IdP 에 **로그인한 상태로** 수락하면 운영자 측면(`admin_operators` 행)이 생긴다.
이메일 일치만으로는 붙지 않는다(ADR-MONO-034 § 1.3 · ADR-MONO-080 D3). `TASK-MONO-334` 의 «대상 테넌트에 가입 계정이 있어야» 규칙을 **대체**한다(위 § POST /api/admin/operators).

| 무엇 | 어디 |
|---|---|
| 저장 | admin-service [`operator_invitation`](../../services/admin-service/data-model.md#operator_invitation) |
| 관리(발급 · 목록 · 취소 · 재발송) | 이 절 — 운영자 토큰, `operator.manage` |
| 수락 화면 | IdP — [auth-api.md § IdP 브라우저 화면 — 운영자 초대 수락](auth-api.md#idp-브라우저-화면--운영자-초대-수락-task-mono-772--adr-mono-080-d6) (OD-3: 콘솔이 아니다 — 측면 없는 풀 계정은 콘솔 토큰을 못 받는다) |
| 수락 판정 | auth → admin [`POST /internal/operator-invitations/accept`](internal/auth-to-admin.md#post-internaloperator-invitationsaccept--초대-수락-task-mono-772) — 계정 판정은 admin → account [`POST /internal/accounts/{accountId}/verified-email:match`](internal/admin-to-account.md#post-internalaccountsaccountidverified-emailmatch--인증된-이메일-일치-판정-task-mono-772) |
| 초대 메일 | admin → account [`POST /internal/notifications/operator-invitation`](internal/admin-to-account.md#post-internalnotificationsoperator-invitation--운영자-초대-메일-task-mono-772) (OD-4: `TASK-MONO-770` 발송기 재사용) |

**규칙**:

| 규칙 | 내용 |
|---|---|
| 1회용 · 만료 · 원문 미저장 (R4) | 토큰 = `SecureRandom` 32바이트 → base64url. 서버는 **SHA-256 hex 만** 저장한다(셀러 구성원 초대와 같은 계산). 만료 `expires_at = 발급(또는 재발송) 시각 + admin.operator-invitation.ttl`(ISO-8601, 기본 `P7D`). **만료는 읽을 때 판정**한다 — `EXPIRED` 는 상태값이 아니고 정리 잡도 없다. 응답의 `expired` 는 읽은 시각의 판정이다 |
| 상태 | `PENDING` → `ACCEPTED`(수락) · `PENDING` → `CANCELLED`(취소). 되돌림 없음. 수락은 조건부 갱신(`… WHERE id = ? AND status = 'PENDING'`)으로 **한 번만** 이긴다 |
| 대기 하나 | `(tenant_id, email)` 에 `PENDING` 은 **최대 한 행**(만료된 `PENDING` 포함 — 다시 보내려면 재발송). 이미 그 테넌트의 운영자인 이메일은 초대하지 않는다 |
| 전달 (OD-4) | **메일** — account-service 의 `TASK-MONO-770` 발송기를 내부 호출로 쓴다. 🔴 링크(토큰)는 초대자에게 **돌려주지 않는다** — 응답 · 목록 · 감사 · 로그 어디에도 없다. 발송이 실패해도 초대 행은 남고, 응답 · 목록의 `delivery` 가 그것을 보인다 → 재발송 |
| 재발송 (OD-4) | **같은 행**에 새 토큰 · 새 만료. 옛 해시를 덮어쓰므로 **옛 링크는 죽는다**(수락 → `404`). 새 행 + 옛 행 취소는 하지 않는다(«대기 하나» 를 깨기 쉽다) |
| 대상 테넌트 | `'*'` 거절(`400 VALIDATION_ERROR`) — 플랫폼 관리자는 초대로 생기지 않는다(ADR-080 D1). 등록된 테넌트여야 한다(발급 · 재발송 때 account-service 로 확인). **운영자가 0명인 테넌트도 받는다** — `SUPER_ADMIN` 이 새 테넌트의 첫 관리자를 초대하는 길(`TASK-MONO-773` 의 «관리자 대기» 가 이것을 쓴다) |
| 권한 | **새 키 없음** — `operator.manage`([rbac.md](../../services/admin-service/rbac.md#permission-keys)). 범위(ADR-024 D2 `TenantScopeGuard`, 대상 = 초대 테넌트)와 역할 무상승(D3 `RoleGrantGuard`)은 `POST /api/admin/operators` 와 같은 규칙이고, **수락 때 한 번 더** 판정한다(7일 사이 초대자의 권한이 줄었을 수 있다) |
| 🔴 한 사람 = 한 회사 (OD-1) | 772 에서 계정 하나는 운영자 행 **하나**까지다 — `admin_operators.oidc_subject` 가 플랫폼 전역 UNIQUE 이고 토큰 교환이 `sub` 로 한 행만 찾는다(772 AC-0 F2). 이미 운영자 측면이 있는 계정이 **두 번째 회사** 초대를 수락하면 `409 OPERATOR_ALREADY_PROVISIONED`(아무것도 쓰지 않는다 · 초대는 `PENDING` 그대로). 발급 단계에서는 막지 않는다 — 초대는 이메일에 가고, 누가(어느 계정이) 수락할지는 발급 때 모른다. 🔴 **다회사 운영자 모델은 `TASK-MONO-773` 착수 전 ADR 로 정한다**(ADR-080 개정 또는 새 ADR — 772 AC-0 OD-1 의 (b) 한 행 + 회사별 역할 · (c) 회사마다 행). 그 결정 전까지 772 Edge Case «같은 사람이 여러 회사에 초대» 는 **미충족 · 기록**이다 |

**공통 응답 항목 (invitation item)** — 발급 · 목록 · 취소 · 재발송이 같은 모양을 쓴다:

```json
{
  "invitationId": "string (UUID v7)",
  "tenantId": "acme-corp",
  "email": "person@example.com",
  "displayName": "홍길동",
  "roles": ["SUPPORT_LOCK"],
  "status": "PENDING",
  "expired": false,
  "expiresAt": "2026-10-17T10:00:00Z",
  "createdAt": "2026-10-10T10:00:00Z",
  "invitedBy": "operator UUID v7",
  "delivery": { "status": "SENT", "attemptedAt": "2026-10-10T10:00:01Z" },
  "acceptedAt": null,
  "acceptedOperatorId": null,
  "cancelledAt": null
}
```

| 필드 | 뜻 |
|---|---|
| `email` | 초대한 주소(앞뒤 공백 제거 · 소문자). 수락 판정의 «기대 이메일» |
| `displayName` | 수락 때 만들어질 운영자 행의 `display_name`(1–64자, 초대자가 정한다) |
| `roles` | 수락 때 부여될 역할(그 테넌트 범위 grant). 수락 때 초대자 기준으로 다시 판정한다 |
| `status` | `PENDING` · `ACCEPTED` · `CANCELLED` |
| `expired` | `status = PENDING` 이고 `expiresAt ≤ 읽은 시각` 이면 `true`. 그 밖엔 `false`(`ACCEPTED`/`CANCELLED` 는 만료를 따지지 않는다) |
| `invitedBy` | 지금 살아 있는 토큰을 낸 운영자(`admin_operators.operator_id`) — **재발송하면 재발송한 운영자로 바뀐다**(수락 때 그 사람 기준으로 재판정 — 아래 § 재발송) |
| `delivery.status` | 마지막 발송 시도의 결과: `SENT` · `FAILED_TRANSIENT`(메일 서버 연결 · 인증 · 시간 초과 · 판정 불가 — 다시 보내면 될 수 있다) · `FAILED_PERMANENT`(이 주소를 받지 않는다 — 다시 보내도 같다). 판별은 account-service 발송기가 한다(`TASK-MONO-770` 과 같은 분류). `ACCEPTED`/`CANCELLED` 행도 마지막 시도 값을 그대로 보인다 |
| `acceptedOperatorId` | 수락으로 생긴 운영자의 `operator_id`. `ACCEPTED` 일 때만 |

🔴 응답 어디에도 **토큰 · 토큰 해시 · 링크가 없다.** 수락한 계정 id 도 이 관리 표면에는 싣지 않는다(운영자 행의 `oidc_subject` 와 같은 값 — 감사 행에만 남는다, 아래 § 수락).

### POST /api/admin/operator-invitations

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN` · `TENANT_ADMIN`(자기 grant 테넌트 한정 — D2) · `operator.manage` 를 가진 그 밖의 위임 역할
**Headers**: `Authorization`, `X-Operator-Reason` (required), **`Idempotency-Key` (required)** — `POST /api/admin/operators` 와 같은 규칙(`(actor_id, action_code, idempotency_key)` 재사용 → `409 IDEMPOTENCY_KEY_CONFLICT`). `X-Tenant-Id` 는 판정에 쓰지 않는다(대상 = 본문 `tenantId`).

**Request**:
```json
{
  "email": "person@example.com",
  "displayName": "홍길동",
  "roles": ["SUPPORT_LOCK"],
  "tenantId": "acme-corp"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `email` | string | Y | 이메일 형식, ≤ 255. 저장 전 trim · 소문자 |
| `displayName` | string | Y | 1–64자 (`POST /api/admin/operators` 와 같다) |
| `roles` | array\<string\> | Y | `POST /api/admin/operators` 의 `roles` 와 같은 검증. 없는 역할 → `400 ROLE_NOT_FOUND` |
| `tenantId` | string | Y | 1–32자 테넌트 slug. **`'*'` → `400 VALIDATION_ERROR`** |

**처리 순서** (처음 실패한 것이 답한다 · 거절은 초대 행을 쓰지 않는다):

1. `operator.manage` — 없으면 `403 PERMISSION_DENIED`(DENIED 행, `RequiresPermissionAspect`).
2. `X-Operator-Reason` → `400 REASON_REQUIRED` · `Idempotency-Key` 누락 → `400 VALIDATION_ERROR` · 본문 검증 → `400 VALIDATION_ERROR`.
3. 범위(D2) — actor 의 admin-grant 범위가 `tenantId` 를 덮지 않으면 `403 TENANT_SCOPE_DENIED`(best-effort DENIED 행 — `POST /api/admin/operators` 의 BE-262 와 같다).
4. 역할 — 없는 이름 `400 ROLE_NOT_FOUND` → 무상승(D3) `403 ROLE_GRANT_FORBIDDEN`(DENIED 행).
5. `Idempotency-Key` 재사용 → `409 IDEMPOTENCY_KEY_CONFLICT`.
6. 테넌트 존재 — account-service 로 확인(비 hot-path). 없으면 `404 TENANT_NOT_FOUND`, 확인 실패면 `503 DOWNSTREAM_ERROR` · `CIRCUIT_OPEN` — **쓰지 않는다**(fail-closed: 없는 테넌트로 가는 초대를 남기지 않는다). 상태(`ACTIVE` 여부)는 여기서 보지 않는다 — 수락 때 본다.
7. 그 테넌트에 같은 이메일의 운영자 행이 이미 있다(`admin_operators (tenant_id, email)`) → `409 OPERATOR_EMAIL_CONFLICT`.
8. 같은 `(tenant_id, email)` 의 `PENDING` 초대가 있다(만료 포함) → `409 OPERATOR_INVITATION_ALREADY_PENDING` — 콘솔은 그 행의 재발송을 권한다.
9. 한 트랜잭션: 초대 행(`PENDING`, 해시 · 만료) + 감사 행 `OPERATOR_INVITATION_CREATE`(`SUCCESS`). 커밋.
10. **커밋 뒤** 메일 발송(admin → account). 결과를 초대 행의 `delivery` 에 적는다(별도 짧은 트랜잭션). 🔴 발송 실패는 **오류 응답이 아니다** — `201` + `delivery.status = FAILED_*`. 커밋 전에 보내지 않는 이유: 롤백된 초대의 링크가 메일함에 남지 않게.

**Response 201**: invitation item + `"auditId": "string (admin_actions.id)"`.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 `tenantId` 에 대한 admin-grant 범위 밖 (ADR-024 D2) |
| 403 | `ROLE_GRANT_FORBIDDEN` | 비-플랫폼 actor 가 `SUPER_ADMIN` 또는 자기가 갖지 않은 권한의 역할을 초대에 실음 (ADR-024 D3) |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 |
| 400 | `VALIDATION_ERROR` | `Idempotency-Key` 누락 · 이메일 형식 · `displayName` 길이 · `tenantId` 누락/형식/`'*'` |
| 400 | `ROLE_NOT_FOUND` | `roles` 에 없는 역할 |
| 404 | `TENANT_NOT_FOUND` | 대상 테넌트 미등록 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 운영자 · 같은 키로 이미 실행 |
| 409 | `OPERATOR_EMAIL_CONFLICT` | 그 테넌트에 같은 이메일의 운영자가 이미 있다 |
| 409 | `OPERATOR_INVITATION_ALREADY_PENDING` | 같은 테넌트 · 같은 이메일의 대기 초대가 있다(만료 포함) — 재발송으로 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | 테넌트 존재 확인 실패 — 쓰지 않음 |

**Side Effects**: `admin_actions` — `action_code=OPERATOR_INVITATION_CREATE`, `permission_used=operator.manage`, `target_type=OPERATOR_INVITATION`, `target_id=<invitationId>`, `target_tenant_id=<tenantId>`, `outcome=SUCCESS` + `admin.action.performed` outbox. 감사 행에 이메일 · 토큰을 싣지 않는다(이메일은 초대 행에 있다). 발송 결과는 감사 행이 아니라 초대 행의 `delivery` 와 구조화 로그(수신자 마스킹)에 남는다.

### GET /api/admin/operator-invitations

**Auth required**: Yes · **Required permission**: `operator.manage` · **Headers**: `Authorization`. 읽기 — 감사 행 없음.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string (optional) | 조회할 테넌트. 생략 시 운영자 자신의 home 테넌트. `*` 는 플랫폼 범위 운영자 전용(전체 테넌트). 범위 규칙은 `GET /api/admin/operators` 의 `tenantId` 와 **같다**(effective scope 밖 → `403 TENANT_SCOPE_DENIED`). 콘솔은 활성 테넌트를 보낸다 |
| `status` | string (optional) | `PENDING`(기본) · `ACCEPTED` · `CANCELLED`. 만료된 `PENDING` 은 `PENDING` 에 `expired=true` 로 나온다 |
| `page` · `size` | int | 기본 0 · 20, `size` ≤ 100 |

**Response 200**: `{ "content": [invitation item…], "totalElements", "page", "size", "totalPages" }` — `createdAt` 내림차순.

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 비-플랫폼 운영자가 effective scope 밖의 `tenantId` 지정 |
| 400 | `VALIDATION_ERROR` | `size` > 100 · `status` 값 오류 |

### POST /api/admin/operator-invitations/{invitationId}:cancel

**Auth required**: Yes · **Required permission**: `operator.manage`
**Headers**: `Authorization`, `X-Operator-Reason` (required). **`Idempotency-Key` 없음** — 같은 취소의 재전송은 아래 멱등 규칙이 답한다.

**처리 순서**: 권한 → 사유 → 초대 조회 — 없거나 **actor 의 admin-grant 범위 밖 테넌트**의 초대면 `404 OPERATOR_INVITATION_NOT_FOUND`(열거 방지 — 운영자 그룹의 `GROUP_NOT_FOUND` 와 같은 모양) → 상태:

| 지금 상태 | 결과 |
|---|---|
| `PENDING`(만료 포함) | `CANCELLED` 로 조건부 갱신 → `200` + item. 그 사이 수락이 이겼으면 `409 OPERATOR_INVITATION_NOT_PENDING` |
| `CANCELLED` | `200` + item — 멱등 no-op, 감사 행 없음 |
| `ACCEPTED` | `409 OPERATOR_INVITATION_NOT_PENDING` — 수락된 초대는 취소하지 않는다. 그 사람을 빼려면 운영자 상태 변경(`PATCH …/{operatorId}/status`) · 역할 · 배정 회수다(ADR-080 D5 — 회수는 측면만) |

취소된 초대의 링크로 수락하면 `404`(없는 초대와 구별하지 않는다 — 아래 § 수락).

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `operator.manage` 없음 |
| 400 | `REASON_REQUIRED` | 사유 누락 |
| 404 | `OPERATOR_INVITATION_NOT_FOUND` | 없거나 범위 밖 |
| 409 | `OPERATOR_INVITATION_NOT_PENDING` | `ACCEPTED`(또는 취소 중 수락이 이김) |

**Side Effects**: 전이했을 때만 `admin_actions` — `action_code=OPERATOR_INVITATION_CANCEL`, `target_type=OPERATOR_INVITATION`, `target_id=<invitationId>`, `target_tenant_id=<초대 테넌트>` + outbox.

### POST /api/admin/operator-invitations/{invitationId}:resend

**Auth required**: Yes · **Required permission**: `operator.manage`
**Headers**: `Authorization`, `X-Operator-Reason` (required). **`Idempotency-Key` 없음** — 재전송은 토큰을 한 번 더 돌릴 뿐이다(가장 나중 메일의 링크만 산다).

**처리 순서**: 권한 → 사유 → 초대 조회(없거나 범위 밖 → `404 OPERATOR_INVITATION_NOT_FOUND`) → `PENDING` 아님 → `409 OPERATOR_INVITATION_NOT_PENDING` →
**재발송하는 actor 기준** 무상승 재판정(D3 — 초대의 `roles` 를 actor 가 부여할 수 있어야 한다, 아니면 `403 ROLE_GRANT_FORBIDDEN`) → 테넌트 존재 확인(`404 TENANT_NOT_FOUND` · `503`, 쓰지 않음) →
한 트랜잭션: **같은 행**에 새 토큰 해시 · `expires_at = 지금 + ttl` · `invited_by = actor` · `version + 1`(동시 수락 · 동시 재발송과는 조건부 갱신으로 겨룬다 — 진 쪽은 `409 OPERATOR_INVITATION_NOT_PENDING`) + 감사 행 → 커밋 → 메일 → `delivery` 기록.

- 🔴 **옛 링크는 죽는다** — 해시를 덮어쓰므로 옛 토큰의 수락은 `404 OPERATOR_INVITATION_NOT_FOUND` 다.
- **만료된 초대를 살리는 길이 이것이다**(새 만료). 발송 실패를 다시 시도하는 길도 이것이다.
- `invited_by` 를 재발송한 actor 로 바꾸는 이유: 살아 있는 토큰을 낸 사람이 그 역할 부여의 책임자다 — 수락 때의 D3 재판정도 그 사람 기준이다.

**Response 200**: invitation item(새 `expiresAt` · `delivery`). 발송 실패도 `200`(`delivery.status = FAILED_*`).

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `operator.manage` 없음 |
| 403 | `ROLE_GRANT_FORBIDDEN` | 재발송 actor 가 초대 역할을 부여할 수 없다 |
| 400 | `REASON_REQUIRED` | 사유 누락 |
| 404 | `OPERATOR_INVITATION_NOT_FOUND` | 없거나 범위 밖 |
| 404 | `TENANT_NOT_FOUND` | 초대 테넌트가 더는 등록돼 있지 않다 |
| 409 | `OPERATOR_INVITATION_NOT_PENDING` | `ACCEPTED` · `CANCELLED` · 동시 갱신에 졌다 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | 테넌트 존재 확인 실패 — 토큰을 돌리지 않음 |

**Side Effects**: `admin_actions` — `action_code=OPERATOR_INVITATION_RESEND`, `target_type=OPERATOR_INVITATION`, `target_id=<invitationId>`, `target_tenant_id=<초대 테넌트>` + outbox.

### 수락 — 이 표면이 아니다

수락은 운영자 토큰을 가진 사람이 하는 일이 아니다(수락하는 사람은 **아직 운영자가 아니다**). IdP 화면이 받고, auth-service 가 admin-service 내부 엔드포인트
[`POST /internal/operator-invitations/accept`](internal/auth-to-admin.md#post-internaloperator-invitationsaccept--초대-수락-task-mono-772) 를 부른다. 판정 순서 · 거절 코드 · 수락이 만드는 행은 그 절이 정본이다. 요약:

- 호출자 = **IdP 세션의 풀 계정**(요청 본문은 계정을 말하지 않는다). 그 계정이 풀 계정이고 · 이메일이 초대 이메일과 같고 · 그 이메일이 **인증됐을 때만** 붙는다(판정은 account-service — `TASK-MONO-770` 의 공용 술어 `VerifiedEmailRequirement` 를 그 자리에서 부른다. 사본을 만들지 않는다).
- 초대 테넌트 `ACTIVE` · 초대자(`invitedBy`) 아직 `ACTIVE` · 초대 역할이 **지금도** 초대자의 부여 메뉴 안(D3) · 초대 테넌트가 **지금도** 초대자의 범위 안(D2).
- 만들어지는 것: 운영자 행(홈 = 초대 테넌트 · `password_hash` NULL · **`oidc_subject` = 수락한 계정 id**) · 역할 grant(테넌트 = 초대 테넌트) · 초대 `ACCEPTED`. `oidc_subject` 를 쓰는 것이 «로그인 문» 이다 — 이 행이 있어야 토큰 교환이 그 계정을 운영자로 해석하고, IdP 가 그 풀 계정에 콘솔 토큰을 준다([auth-api.md § 풀 계정의 콘솔 토큰](auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)).
- `amr ∋ mfa` 는 수락에서 요구하지 않는다 — 측면을 **만드는** 일이고, 2단계 진입 조건은 토큰 교환 · assume 이 문다(TASK-MONO-771).

---

## PATCH /api/admin/operators/{operatorId}/roles

운영자의 역할 목록 전체 교체.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**:
```json
{
  "roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"]
}
```

`roles` 빈 배열(`[]`) 허용 — 역할 전부 제거.

**Response 200**:
```json
{
  "operatorId": "string",
  "roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"],
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 대상 operator 의 home tenant 에 대한 admin-grant 스코프 밖 (ADR-024 D2, step-1) |
| 403 | `ROLE_GRANT_FORBIDDEN` | **TASK-BE-347 (ADR-024 D3)** — 비-플랫폼 actor 가 `SUPER_ADMIN` 또는 보유하지 않은 권한을 가진 role 을 부여 시도 (≤-own grant-menu; `TENANT_ADMIN` 은 `tenant.admin.delegate` 보유 시, `TENANT_BILLING_ADMIN` 은 `subscription.manage` 보유 시에만 부여 가능) |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 400 | `ROLE_NOT_FOUND` | roles 배열에 존재하지 않는 role 이름 포함 |

**Side Effects**: `admin_actions`에 `action_code=OPERATOR_ROLE_CHANGE` 기록. Redis 권한 캐시 즉시 invalidate (`admin:operator:perm:{operatorId}`). `403 ROLE_GRANT_FORBIDDEN`/`TENANT_SCOPE_DENIED` 시 best-effort DENIED row.

---

## PATCH /api/admin/operators/{operatorId}/status

운영자 계정 상태 변경.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**:
```json
{
  "status": "SUSPENDED"
}
```

허용 값: `ACTIVE`, `SUSPENDED`.

**Response 200**:
```json
{
  "operatorId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "SUSPENDED",
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 400 | `SELF_SUSPEND_FORBIDDEN` | 본인 계정 SUSPENDED 시도 |
| 400 | `STATE_TRANSITION_INVALID` | 현재 status와 요청 status가 동일 |
| 400 | `VALIDATION_ERROR` | status 값이 허용 목록 외 |

**Side Effects**: `SUSPENDED` 처리 시 해당 운영자의 모든 refresh token 즉시 무효화. `admin_actions`에 `action_code=OPERATOR_STATUS_CHANGE` 기록.

---

## PATCH /api/admin/operators/{operatorId}/profile

대상 운영자의 프로파일 carrier (`operatorContext`) 를 변경한다 — admin-on-behalf-of 경로. v1 은 **`finance_default_account_id` 단일 필드**만 수용한다 (TASK-BE-304 column / TASK-BE-306 self-serve sister; 본 task TASK-BE-307 admin sister). SUPER_ADMIN 이 다른 운영자의 platform-console `Operator Overview` finance 기본 계정을 provisioning 할 때 사용. **본인 (self) 변경은 본 endpoint 가 아닌 `PATCH /api/admin/operators/me/profile` (자가 변경, 별도 audit reason 포맷)** 사용.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)` — 자가 변경 면제 사항 아님 (다른 대상에 대한 운영 명령)

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**

```
PATCH /api/admin/operators/{operatorId}/profile
Content-Type: application/json
```

```json
{
  "operatorContext": {
    "defaultAccountId": "acc-uuid-7"
  }
}
```

값을 **clear** 하려면 `defaultAccountId` 에 `null` 을 명시 (BE-306 `me/profile` 와 동일 규약):

```json
{
  "operatorContext": {
    "defaultAccountId": null
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `operatorContext` | object | ✅ | 프로파일 attribute carrier. v1 은 `defaultAccountId` 단일 키만 수용 (`FAIL_ON_UNKNOWN_PROPERTIES`); 누락된 빈 body / 빈 객체 → 400 |
| `operatorContext.defaultAccountId` | string \| null | ✅ | finance-platform 계정 UUID (`VARCHAR(36)`, opaque — IAM 는 finance 에 verify 하지 않음, TASK-BE-304 § Decision authority). `null` = clear. 빈 문자열 / whitespace-only / 36자 초과 / 내부 공백·control char → 400 `INVALID_REQUEST` |

> **Body shape parity with `/me/profile`**: 본 endpoint 와 self-serve `/me/profile` (BE-306) 의 request body 는 **byte-identical**. UI 는 동일 form/parser 재사용 가능; producer 측은 caller-target 관계 (self vs cross-operator) 만 분기.

**Response**

`204 No Content` — 변경 성공, 본문 없음. 변경된 effective state 는 대상 운영자의 `GET /api/admin/console/registry` 응답에서 관측 (등록부가 read-side 권위; PATCH 는 fire-and-re-read).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `INVALID_REQUEST` | body shape mismatch / `defaultAccountId` 형식 위반 (`me/profile` 와 동일 validation) |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 또는 빈 문자열 |
| 400 | `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` | `{operatorId}` 가 caller 본인의 `operator_id` 와 일치. self-serve 는 반드시 `/me/profile` (BE-306) 사용 (audit reason 포맷이 다름: self-flow `<self_profile_update>` 상수 vs caller-typed reason) |
| 401 | `TOKEN_INVALID` | operator JWT 없음 / 만료 / 변조 / soft-delete 됨 |
| 403 | `PERMISSION_DENIED` | caller 가 `operator.manage` 권한 미보유 |
| 403 | `TENANT_SCOPE_DENIED` | 대상 operator 의 tenant 가 caller 의 scope 외 (caller `tenant='*'` 아니면 cross-tenant 차단) |
| 404 | `OPERATOR_NOT_FOUND` | `operatorId` 미존재 |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | `admin_operators.version` race |

**Side Effects**

- `admin_operators.finance_default_account_id` UPDATE — 단일 transaction (target operator 의 row)
- `admin_actions` row INSERT (동일 transaction) — `action_code = "OPERATOR_PROFILE_UPDATE"` (BE-306 와 **공통 enum 재사용**; actor differentiation = `(operator_id, target_id)` tuple), `operator_id = caller.internalId`, `target_type = "OPERATOR"`, `target_id = target.operator_id` (대상 operator 의 public UUID — caller 와 다름), `permission_used = "operator.manage"`, `outcome = "SUCCESS"`, `reason = <X-Operator-Reason header value>` (caller-typed; self-flow `<self_profile_update>` 상수와 형식 구분됨), `detail IS NULL` (new value 자체는 audit `detail` 에 기록하지 않음 — registry GET 가 audit evidence). 두 write 중 하나라도 실패 시 transaction 전체 rollback (audit-heavy A3 invariant)
- Refresh token 무효화 **없음** (status change 와 달리; profile mutation 은 인증 토큰 영향 없음)

---

## PATCH /api/admin/operators/me/password

운영자 자신의 비밀번호를 변경한다.

**Auth**: Operator JWT (유효한 토큰만 필요, 별도 permission 없음)

**Request**

```
PATCH /api/admin/operators/me/password
Content-Type: application/json
```

```json
{
  "currentPassword": "OldPass1!",
  "newPassword":     "NewPass2@"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `currentPassword` | string | ✅ | 현재 비밀번호 (평문) |
| `newPassword` | string | ✅ | 새 비밀번호 (평문). 8자 이상, 4종 문자(대·소·숫·특) 중 3종 이상 |

**Response**

`204 No Content` — 변경 성공, 본문 없음.

**Errors**

| HTTP | code | 설명 |
|---|---|---|
| 400 | `CURRENT_PASSWORD_MISMATCH` | 현재 비밀번호 불일치 |
| 400 | `PASSWORD_POLICY_VIOLATION` | 새 비밀번호가 정책 미충족 |
| 401 | `TOKEN_INVALID` | JWT 없음 또는 만료 |

---

## PATCH /api/admin/operators/me/profile

운영자 자신의 프로파일 carrier (`operatorContext`) 를 변경한다. v1 은 **`finance_default_account_id` 단일 필드**만 수용한다 — Platform Console `Operator Overview` 의 finance 카드 기본 조회 계정 자가 설정 (TASK-BE-304 § Goal). 자가-프로비저닝 self-serve 경로로, `me/password` 와 형제 (자기 자신만 변경 가능, 다른 operator 의 프로파일 수정은 v1 범위 외).

**Auth**: Operator JWT (유효한 토큰만 필요, 별도 permission 없음)
**X-Operator-Reason**: 요구하지 않음 (§ Authentication > X-Operator-Reason in Exceptions sub-tree 의 self-flow 면제 적용; audit row 는 `<self_profile_update>` 상수로 기록)

**Request**

```
PATCH /api/admin/operators/me/profile
Content-Type: application/json
```

```json
{
  "operatorContext": {
    "defaultAccountId": "acc-uuid-7"
  }
}
```

값을 **clear** 하려면 `defaultAccountId` 에 `null` 을 명시:

```json
{
  "operatorContext": {
    "defaultAccountId": null
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `operatorContext` | object | ✅ | 프로파일 attribute carrier. v1 은 `defaultAccountId` 단일 키만 수용 (그 외 키 → 400 `INVALID_REQUEST`, `FAIL_ON_UNKNOWN_PROPERTIES`). 본 키 자체가 누락된 빈 body (`{}`) 또는 빈 객체 (`{"operatorContext":{}}`) → 400 |
| `operatorContext.defaultAccountId` | string \| null | ✅ | finance-platform 계정 UUID (`VARCHAR(36)`, opaque — IAM 는 finance 에 verify 하지 않음, TASK-BE-304 § Decision authority). `null` = clear. 빈 문자열 / whitespace-only / 36자 초과 / 내부 공백·control char → 400 `INVALID_REQUEST` (clear 의도는 반드시 `null` 명시) |

> **Request 와 Response 의 carrier 대칭성**: 본 PATCH 의 request body 는 `GET /api/admin/console/registry` 응답의 finance product item 에 나오는 `operatorContext: { defaultAccountId?: string }` 와 **동일한 shape** 이다 ([console-registry-api.md § Item shape](./console-registry-api.md)). UI 는 동일 JSON path 로 read → mutate → re-read 한다.

**Response**

`204 No Content` — 변경 성공, 본문 없음. 변경된 effective state 는 `GET /api/admin/console/registry` 로 재조회한다 (등록부가 read-side 권위; PATCH 는 fire-and-re-read).

**Errors**

| HTTP | code | 설명 |
|---|---|---|
| 400 | `INVALID_REQUEST` | body shape mismatch (`operatorContext` 키 누락, 빈 객체, 알 수 없는 nested key) / `defaultAccountId` 가 빈 문자열, whitespace-only, 36 자 초과, 내부 control char 포함 |
| 401 | `TOKEN_INVALID` | JWT 없음 / 만료 / `OperatorAuthenticationFilter` 거부 / operator row soft-delete 됨 |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | `admin_operators.version` race (두 브라우저 탭 동시 PATCH). 재시도 권장 |

**Side effects**

- `admin_operators.finance_default_account_id` UPDATE — 단일 transaction
- `admin_actions` row INSERT (동일 transaction) — `action_code = "OPERATOR_PROFILE_UPDATE"`, `operator_id = self`, `target_type = "OPERATOR"`, `target_id = self.operator_id`, `permission_used = "<self_action>"`, `outcome = "SUCCESS"`, `detail IS NULL` (new value 자체는 audit `detail` 에 기록하지 않음 — registry GET 가 audit evidence). 두 write 중 하나라도 실패 시 transaction 전체 rollback (audit-heavy A3 invariant)

---

## Tenant Lifecycle (TASK-BE-256)

`POST /api/admin/tenants` 등 4개 엔드포인트는 SUPER_ADMIN(`tenant_id='*'`) 만 호출할 수 있다. 일반 운영자는 `403 TENANT_SCOPE_DENIED`. 모든 mutating 엔드포인트는 [admin-events.md](../events/admin-events.md) 의 `admin.action.performed` 이벤트와 함께 [tenant-events.md](../events/tenant-events.md) 의 lifecycle 이벤트를 outbox 패턴으로 발행한다.

### Tenant ID 규칙

- 정규식: `^[a-z][a-z0-9-]{1,31}$` ([multi-tenancy.md](../../features/multi-tenancy.md#tenantid)).
- **예약어** (생성 거부, `400 TENANT_ID_RESERVED`):
  `admin`, `internal`, `system`, `null`, `default`, `public`, `gap`, `iam`, `auth`, `oauth`, `me`.
- 한 번 발급된 `tenant_id` 는 변경·재할당 불가 (감사 트레일·외부 토큰 정합).

### Status 전이 매트릭스

| 현재 상태 | → ACTIVE (PATCH `status=ACTIVE`) | → SUSPENDED (PATCH `status=SUSPENDED`) |
|---|---|---|
| (none) | `POST` 로 생성 (status=ACTIVE 만 허용) | `POST` 로 직접 SUSPENDED 생성 불가 |
| ACTIVE | no-op (200, `tenant.updated` 미발행) | reactivate-able 전이; `tenant.suspended` 발행 |
| SUSPENDED | reactivate; `tenant.reactivated` 발행 | no-op (200, 이벤트 미발행) |

SUSPENDED 테넌트는 신규 로그인·신규 사용자 등록이 차단된다 (account-service consumer 가 `tenant.suspended` 이벤트 수신 시 차단 게이트 활성화 — TASK-BE-250 implementation 의 책임).

### POST /api/admin/tenants

신규 테넌트 등록.

**Auth required**: 운영자 JWT, role = `SUPER_ADMIN`

**Request**:
```json
{
  "tenantId": "wms",
  "displayName": "Warehouse Management System",
  "tenantType": "B2B_ENTERPRISE"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `tenantId` | string | Y | 정규식 + 예약어 미포함 |
| `displayName` | string | Y | 1~100자, trim 후 검증 |
| `tenantType` | enum | Y | `B2C_CONSUMER` \| `B2B_ENTERPRISE` |
| `orgNodeId` | string \| null | N | (`TASK-BE-625`, `ADR-MONO-047` § 개정 2026-10-07) 새 테넌트를 둘 org-node. **생략 또는 `null` = 무소속(지금과 동일)**. 값이 있으면 [`PUT /api/admin/tenants/{tenantId}/org-node`](#put-apiadmintenantstenantidorg-node) 와 **같은 쓰기 규칙**으로 본다 — 출발 = 무소속(생성자는 이미 `SUPER_ADMIN` 이므로 출발 쪽 통과), 도착 = `administers(actor, orgNodeId)` |

`Idempotency-Key` 헤더는 권장. 동일 키로 재요청 시 첫 요청과 동일한 응답 반환 (이벤트 중복 발행 방지).

> **`orgNodeId` 가 있을 때의 순서 (`TASK-BE-625`).** ① 도착 쪽 검사 `administers(actor, orgNodeId)` — 실패하면 **아무것도 만들지 않고** `404 ORG_NODE_NOT_FOUND` + best-effort DENIED 행. ② 테넌트 생성(아래 Side Effects 그대로). ③ 소속 쓰기 — `TENANT_ORG_NODE_ASSIGN` 감사 행(출발 `null` → 도착 `orgNodeId`). 🔴 ②와 ③ 은 account-service 의 서로 다른 두 내부 호출이라 한 트랜잭션이 아니다. ① 과 ③ 사이에 노드가 삭제되는 경합(또는 그 사이 account-service 장애)이면 테넌트는 **무소속으로 생성된 채** ③ 의 오류(`404 ORG_NODE_NOT_FOUND` / `503`)가 응답된다 — 재시도는 같은 POST 가 아니라(`409 TENANT_ALREADY_EXISTS`) `PUT /api/admin/tenants/{tenantId}/org-node` 로 한다. 생성 내부 호출(`POST /internal/tenants`)에 노드 칸을 싣지 않은 이유: 그 경로는 게이트웨이의 `/internal/tenants/**` 라우트로 테넌트 워크로드에도 열려 있다([gateway-api.md](gateway-api.md)) — 소속 쓰기를 거기에 얹으면 관리자 검사를 거치지 않는 소속 쓰기 길이 생긴다.

**Response 201**:
```json
{
  "tenantId": "wms",
  "displayName": "Warehouse Management System",
  "tenantType": "B2B_ENTERPRISE",
  "status": "ACTIVE",
  "createdAt": "2026-05-02T09:00:00Z",
  "updatedAt": "2026-05-02T09:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `tenantId` 정규식 위반, `displayName` 길이, `tenantType` enum 미일치 |
| 400 | `TENANT_ID_RESERVED` | `tenantId` 가 예약어 목록에 포함 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `tenant.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 비-SUPER_ADMIN 운영자 호출 |
| 404 | `ORG_NODE_NOT_FOUND` | (`orgNodeId` 가 있을 때만) 노드 미존재 또는 actor reach 밖 — 생성 전에 판정되어 아무것도 만들지 않는다. 위 순서 노트의 경합 경우만 예외 |
| 409 | `TENANT_ALREADY_EXISTS` | 동일 `tenantId` 가 이미 존재 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service 호출 CB open |

**Side Effects**:
- `admin_actions` 에 `action_code=TENANT_CREATE`, `tenant_id='*'` (actor), `target_tenant_id=<신규>`, `target_type='TENANT'`, `target_id=<신규 tenantId>`.
- Outbox 이벤트 `tenant.created` 발행 ([tenant-events.md](../events/tenant-events.md)).
- (`orgNodeId` 가 있을 때만) 이어서 `admin_actions` `action_code=TENANT_ORG_NODE_ASSIGN` 한 행 — 모양은 [소속 쓰기의 Side Effects](#put-apiadmintenantstenantidorg-node) 와 같다(`from_org_node_id=null`). `orgNodeId` 가 없으면 이 행은 없다(회귀 — 지금과 동일).

---

### GET /api/admin/tenants

테넌트 목록 조회.

**Auth required**: 운영자 JWT, role = `SUPER_ADMIN`

**Query parameters**:

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `status` | enum (optional) | — | `ACTIVE` \| `SUSPENDED` 필터 |
| `tenantType` | enum (optional) | — | `B2C_CONSUMER` \| `B2B_ENTERPRISE` 필터 |
| `page` | int | 0 | — |
| `size` | int | 20 (max 100) | — |

**Response 200**:
```json
{
  "items": [
    {
      "tenantId": "fan-platform",
      "displayName": "Fan Platform",
      "tenantType": "B2C_CONSUMER",
      "status": "ACTIVE",
      "createdAt": "2026-04-01T00:00:00Z",
      "updatedAt": "2026-04-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Errors**: `401`, `403 PERMISSION_DENIED`, `403 TENANT_SCOPE_DENIED`, `422 VALIDATION_ERROR` (size > 100 등).

---

### GET /api/admin/tenants/{tenantId}

단건 조회.

**Auth required**: 운영자 JWT, role = `SUPER_ADMIN` 또는 `tenant.read` 권한 + `tenantId` == operator 의 `tenant_id` (자기 테넌트는 본인이 조회 가능).

**Response 200**: POST 응답과 동일 schema.

**Errors**: `401`, `403 PERMISSION_DENIED`, `403 TENANT_SCOPE_DENIED`, `404 TENANT_NOT_FOUND`.

---

### PATCH /api/admin/tenants/{tenantId}

`displayName` 또는 `status` 변경. 두 필드 모두 optional 이며 최소 1개는 필요.

**Auth required**: 운영자 JWT, role = `SUPER_ADMIN`

**Request** (둘 중 하나 또는 둘 다):
```json
{
  "displayName": "Warehouse Management System v2",
  "status": "SUSPENDED"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `displayName` | string (optional) | — | 1~100자 |
| `status` | enum (optional) | — | `ACTIVE` \| `SUSPENDED` (status 전이 매트릭스 참조) |

**Response 200**: POST 응답과 동일 schema (변경 후 상태).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 두 필드 모두 누락, displayName 길이 위반, status enum 미일치 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `tenant.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 비-SUPER_ADMIN 호출 |
| 404 | `TENANT_NOT_FOUND` | `tenantId` 미존재 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects** (변경 종류별):
- `displayName` 변경 시 → `admin_actions: action_code=TENANT_UPDATE` + outbox `tenant.updated`.
- `status: ACTIVE → SUSPENDED` → `admin_actions: action_code=TENANT_SUSPEND` + outbox `tenant.suspended`.
- `status: SUSPENDED → ACTIVE` → `admin_actions: action_code=TENANT_REACTIVATE` + outbox `tenant.reactivated`.
- 동일 status 로의 PATCH 는 no-op (200 반환, audit/event 미발행).

---

## Tenant Entry Policy (TASK-MONO-771)

**ADR-MONO-080 D4 · 라이더 R2 · 소유자 결정 OD-1.** «이 테넌트에 **운영자로** 들어오려면 2단계 인증이 필요하다» 는 테넌트 단위 플래그. 저장은 admin-service
[`tenant_entry_policy`](../../services/admin-service/data-model.md#tenant_entry_policy) — **행 없음 = 꺼짐**. 소비자 사이트 로그인과는 **무관**하다(이 플래그는 운영자 평면의 진입 조건이지
테넌트의 소비자 로그인 규칙이 아니다).

이 플래그가 무는 곳은 둘이다:

| 진입 | 무엇을 보나 | 거절 |
|---|---|---|
| assume-tenant (`POST /oauth2/token`, token-exchange) | **선택 테넌트**의 정책 | `400 invalid_grant` + `error_description=insufficient_user_authentication` ([auth-api.md](auth-api.md) · [auth-to-admin.md](internal/auth-to-admin.md) `mfaRequired`) |
| 운영자 토큰 교환 (`POST /api/admin/auth/token-exchange`) | 운영자의 홈 ∪ assignment 테넌트 중 **하나라도** (OD-3) | `403 MFA_REQUIRED` (위 § token-exchange) |

**전이 (소유자 결정 OD-4)**: 시간 유예 없음 — 켜는 순간부터 다음 진입이 판정된다. 2단계를 등록하지 않은 운영자는 거절을 받고, 콘솔이 그 거절을 IAM 2단계
등록 · 검증 화면으로 보낸다([auth-api.md § IdP 브라우저 화면 — 2단계 인증](auth-api.md) § 단계 상승). 비밀번호로 로그인할 수 있는 사람은 아무도 잠기지 않는다.
이미 발급된 운영자 토큰 · assume 토큰은 만료까지 유효하다(다음 교환 · 다음 assume 에서 걸린다).

**권한 (OD-1)**: 새 권한 키 **`tenant.security.manage`** — `SUPER_ADMIN`(모든 테넌트) · `TENANT_ADMIN`(**그 grant 의 테넌트만** — D2 `TenantScopeGuard`, 대상 = path `tenantId`). 카탈로그 · seed 행렬: [rbac.md](../../services/admin-service/rbac.md#permission-keys). 읽기도 같은 키로 게이트한다(`tenant.manage` · `org.manage` 읽기 규약).

### GET /api/admin/tenants/{tenantId}/entry-policy

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `tenant.security.manage`
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)
**Headers**: `Authorization`, `X-Tenant-Id: <활성 테넌트>` (권장 — 대상은 path `tenantId` 이고 confinement 판정도 path 로 한다)

**Response 200**:
```json
{
  "tenantId": "acme-corp",
  "requireMfa": true,
  "updatedAt": "2026-10-08T10:00:00Z",
  "updatedBy": "operator UUID v7 | null"
}
```

- 행이 없으면 `{ "tenantId": "<path>", "requireMfa": false, "updatedAt": null, "updatedBy": null }` — 404 가 아니다(꺼짐은 정상 상태).
- `updatedBy` 는 `admin_operators.operator_id`(외부 UUID). 내부 BIGINT PK 는 노출하지 않는다.
- 성공 읽기는 감사 행을 남기지 않는다(BE-486 read-path 규약). `403 PERMISSION_DENIED`(키 없음)는 best-effort DENIED 행(`RequiresPermissionAspect`), `403 TENANT_SCOPE_DENIED`(범위 밖 테넌트)는 행을 남기지 않는다 — `X-Tenant-Id` 읽기의 MONO-737 규약(`TenantScopeGuard.requireTenantReadable`)과 같다. (S5 구현에서 이 문장을 정밀화: 두 403 의 감사 처리가 다르다.)

### PUT /api/admin/tenants/{tenantId}/entry-policy

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `tenant.security.manage`
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정)
**Headers**: `Authorization`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <활성 테넌트>`. **`Idempotency-Key` 없음** — 멱등 full-replace PUT(org-scope PUT 과 같은 규약).

**Request**:
```json
{ "requireMfa": true }
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `requireMfa` | boolean | Y | `true` = 켜기 · `false` = 끄기. 누락 · 비-boolean → `400 VALIDATION_ERROR` |

**Response 200**: GET 응답과 같은 모양(변경 후 상태).

- **끄기는 행을 지우지 않는다** — `require_mfa = FALSE` 로 남겨 마지막 변경자 · 시각을 보존한다(행 없음 = 한 번도 켠 적 없음).
- **테넌트 존재 확인**: 쓰기 전에 account-service 로 대상 테넌트가 있는지 확인한다(비 hot-path — 쓰기에서만). 없으면 `404 TENANT_NOT_FOUND`, account-service 장애면 `503` — **쓰지 않는다**(fail-closed: 없는 테넌트에 고아 행을 남기지 않는다). 읽기(GET · 진입 판정)는 이 확인을 하지 않는다.
- `tenantId = '*'` · 정규식 위반 → `400 VALIDATION_ERROR`(`'*'` 는 정책을 가질 수 없다 — 플랫폼 범위의 2단계는 역할 플래그 `require_2fa` 가 문다).

**Side Effects**: 성공한 PUT 마다(같은 값으로의 no-op 포함) `admin_actions` 한 행 — `action_code=TENANT_ENTRY_POLICY_SET`, `permission_used=tenant.security.manage`, `target_type=TENANT`, `target_id=<tenantId>`, `target_tenant_id=<tenantId>`, `downstream_detail = "requireMfa <이전>→<이후>"`(이전이 행 없음이면 `none→true`) + `admin.action.performed` outbox. 도메인 이벤트는 발행하지 않는다(소비자 없음 — 진입 판정은 같은 서비스의 로컬 읽기다).

**Errors** (GET/PUT 공통):

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `tenant.security.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | `TENANT_ADMIN` 이 자기 grant 테넌트가 아닌 `tenantId` 를 지정 (D2) |
| 400 | `REASON_REQUIRED` | (PUT) `X-Operator-Reason` 누락 |
| 400 | `VALIDATION_ERROR` | `requireMfa` 누락/형식 오류, `tenantId` 가 `'*'` 이거나 정규식 위반 |
| 404 | `TENANT_NOT_FOUND` | (PUT) 대상 테넌트 미등록 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | (PUT) 테넌트 존재 확인 실패 — 쓰지 않음 |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | (PUT) 같은 테넌트 정책을 동시에 쓴 다른 요청이 먼저 커밋됨(행의 `version`, 첫 쓰기 경합 포함) — 다시 읽고 재시도 |

### GET /api/admin/tenants/{tenantId}/entry-policy/enrolment-summary

**TASK-MONO-771 S5 — 켜기 전 사전 점검**(티켓 AC-0 § 4 · OD-4). 정책을 켜기 전에 «이 테넌트 운영자 중 2단계를 아직 등록하지 않은 사람이 몇 명인가» 를 보여 주기 위한 **조언용 읽기**다. 진입 판정(토큰 교환 · assume)은 이 읽기를 하지 않는다.

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `tenant.security.manage` (GET entry-policy 와 같은 키 · 같은 범위 규칙)
**Headers**: `Authorization`, `X-Tenant-Id: <활성 테넌트>`

**Response 200**:
```json
{ "tenantId": "acme-corp", "operators": 12, "enrolled": 7, "notEnrolled": 4, "unlinked": 1 }
```

| 필드 | 의미 |
|---|---|
| `operators` | 이 테넌트의 **ACTIVE** 운영자 수 — 홈 테넌트 = `tenantId` **또는** `operator_tenant_assignment` 행이 있는 운영자(`GET /api/admin/operators?tenantId=` 와 같은 소속 술어). 플랫폼(`'*'`) 운영자 · 파트너십 참여자는 이 테넌트의 소속이 아니라 세지 않는다 |
| `enrolled` | 계정 연결(`oidc_subject`)이 있고 auth-service 에 **확정된** 계정 2단계가 있는 운영자(등록 대기 중인 미완료 `/mfa/setup` 은 등록이 아니다) |
| `notEnrolled` | 계정 연결이 있고 확정된 2단계가 없는 운영자 — 정책을 켜면 **다음 진입 때 등록 화면으로 안내될** 사람 수 |
| `unlinked` | 계정 연결이 없는 운영자 — 토큰 교환이 이 운영자를 해석하지 못하므로(account_id 단독 해석) 정책이 바꾸는 것이 없다 |

`operators = enrolled + notEnrolled + unlinked`. 등록 여부는 admin-service → auth-service [`POST /internal/auth/second-factor/enrolment-status`](internal/admin-to-auth.md) 로 읽는다(500 개씩 나눠 호출).

- 감사 행 없음(읽기). `403` 의 감사 처리는 GET entry-policy 와 같다.
- **fail-closed**: auth-service 를 읽지 못하면 숫자를 지어내지 않고 `503` — 콘솔은 숫자 없이 확인 문구만 보인다(토글 자체는 막지 않는다).

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` | GET entry-policy 와 같다 |
| 400 | `VALIDATION_ERROR` | `tenantId` 가 `'*'` 이거나 정규식 위반 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | auth-service 등록 여부 읽기 실패 |

---

## Subscription Management (TASK-BE-343, ADR-MONO-023 step 2b)

테넌트↔도메인 구독(entitlement 평면)의 operator-facing 관리 표면. 실제 쓰기는 account-service(entitlement authority)로 위임된다 — admin-service 는 `subscription.manage` 게이트 + 운영자 감사만 담당하고 account-service `POST/PATCH /internal/tenant-domain-subscriptions` 로 위임한다(ADR-023 D2: account-service 는 IAM 미접근). `subscription.manage` 는 `operator.manage` 와 **분리된** 권한이다(D3 — 두 평면 독립 위임 가능).

### POST /api/admin/subscriptions

신규 구독 생성(subscribe, ACTIVE).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `subscription.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: string (required)`

**Request**:
```json
{ "tenantId": "acme-corp", "domainKey": "scm" }
```

**Response 201**:
```json
{
  "tenantId": "acme-corp",
  "domainKey": "scm",
  "previousStatus": null,
  "currentStatus": "ACTIVE",
  "occurredAt": "2026-06-10T10:00:00Z"
}
```

`admin_actions: action_code=SUBSCRIPTION_SUBSCRIBE`, `permission_used=subscription.manage`, `target_type=SUBSCRIPTION`, `target_id=<tenantId>:<domainKey>`. account-service 가 `tenant.subscription.changed` (previousStatus=null) 발행.

**`fan` 도메인은 `fan-platform` 테넌트만 구독한다 (`TASK-MONO-750`, `ADR-MONO-079` D4-A 라이더 R3)**: `domainKey ∈ {fan, fan-platform}` 이고 `tenantId ≠ fan-platform` 이면 **모든 actor 에게** `403 TENANT_SCOPE_DENIED`. `fan-platform` 의 `subscription.manage` 스코프는 platform-scope(`'*'`) grant 만 가지므로, 결과적으로 `fan` 구독은 **플랫폼 운영자만 선택할 수 있다**. 그 구독은 `fan-platform` 을 assume 한 토큰의 `FAN_OPERATOR` 파생 근거다 (auth-service `OperatorRoleDerivation`) — 고객사 테넌트가 `fan` 을 구독해도 팬 서비스는 엔타이틀먼트를 신뢰하지 않으므로 닿지 않지만, 닿지 않는 역할을 발급하지 않는 쪽으로 닫는다. `fan-platform` 의 `fan` 구독 자체는 account-service `V0031` 이 시드한다(`ecommerce` 의 `V0022` 와 같은 모양).

### PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status

기존 구독 상태 전이(suspend/resume/cancel). `SubscriptionStatus` 상태머신 가드(account-service) 통과 필요.

**Required permission**: `subscription.manage`
**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: string (required)`

**Request**:
```json
{ "status": "SUSPENDED" }
```

**Response 200**:
```json
{
  "tenantId": "acme-corp",
  "domainKey": "wms",
  "previousStatus": "ACTIVE",
  "currentStatus": "SUSPENDED",
  "occurredAt": "2026-06-10T10:00:00Z"
}
```

`admin_actions: action_code=SUBSCRIPTION_CHANGE_STATUS`. account-service 가 `tenant.subscription.changed` 발행.

> **평면 분리 (ADR-023 D2)**: SUSPENDED/CANCELLED 전이는 entitlement 평면만 바꾼다 — 그 테넌트의 도메인이 카탈로그 + 다음-발급 `entitled_domains` 에서 빠지지만 operator 할당·RBAC 는 보존된다. SUSPENDED→ACTIVE 재개는 재부여 없이 접근 복구(GCP billing↔IAM parity).

**Errors** (POST/PATCH 공통):

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `subscription.manage` 권한 없음 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |
| 404 | `TENANT_NOT_FOUND` | (POST) 대상 테넌트 미등록 (account-service 404) |
| 404 | `SUBSCRIPTION_NOT_FOUND` | (PATCH) 대상 구독 부재 (account-service 404) |
| 409 | `SUBSCRIPTION_ALREADY_EXISTS` | (POST) 동일 `(tenantId, domainKey)` 구독 존재 |
| 409 | `SUBSCRIPTION_TRANSITION_INVALID` | (PATCH) 상태머신 가드 위반 |
| 503 | `DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` | account-service 5xx/timeout / circuit open |

---

## Partnership Management (ADR-MONO-045)

**cross-org 파트너십**의 operator-facing 관리 표면 — 두 **독립 소유** 테넌트(host A, partner B) 사이의 bounded 위임 관계를 생성·수락·중단·종료하고, partner 가 자기 operator 를 participant 로 배정한다. 모든 엔드포인트는 `partnership.manage` + **D2 `TenantScopeGuard`**(대상 = acting-side 테넌트: invite/host-terminate → host, accept/participant/partner-terminate → partner)로 게이트되며, 두 테넌트 모두 `TENANT_ADMIN` 이 당사자다(D2 two-sided consent). 이 표면은 파트너십 **관계 상태**만 다루고, 그로부터 B-operator 가 A 에서 얻는 **파생 도메인-운영 권한**은 assume-tenant 발급 시 `delegated_scope ∩ participant ∩ host-holds` 로 캡된다([rbac.md](../../services/admin-service/rbac.md#cross-org-partner-delegation-confinement-adr-mono-045-d3d5)) — 파트너십은 admin 권한을 조직 경계 너머로 확장하지 **않는다**.

모든 mutating 엔드포인트는 [admin-events.md](../events/admin-events.md) 의 `admin.action.performed` 와 함께 [partnership-events.md](../events/partnership-events.md) 의 lifecycle 이벤트를 outbox 패턴으로 발행한다. colon-verb 전이는 AIP-136.

### Status 전이 매트릭스

| 현재 상태 | invite (`POST`) | `:accept` | `:suspend` | `:reactivate` | `:terminate` |
|---|---|---|---|---|---|
| (none) | → `PENDING` (host 발행) | — | — | — | — |
| PENDING | 중복 → 409 | → `ACTIVE` (partner 수락) | — | — | → `TERMINATED` (either, invite 철회/거절) |
| ACTIVE | 중복 → 409 | no-op (409 `PARTNERSHIP_TRANSITION_INVALID`) | → `SUSPENDED` (either) | — | → `TERMINATED` (either) |
| SUSPENDED | — | — | no-op (200, 이벤트 미발행) | → `ACTIVE` (either) | → `TERMINATED` (either) |
| TERMINATED | — | — | — | — | 종단(멱등 no-op 200) |

- **PENDING** = invite 발행·partner 미수락 → **파생 접근 0**(assume-tenant reach 없음).
- **ACTIVE** = 양방 합의 완료 → participant 파생 유효.
- **SUSPENDED**/**TERMINATED** = cascade-revoke(D6): 다음 요청에서 모든 participant 파생 즉시 0. SUSPENDED 는 가역(reactivate), TERMINATED 는 종단.
- `:accept` 는 **partner 측**만, invite/`:terminate`(host 사유)는 **host 측**만; `:suspend`/`:reactivate`/`:terminate` 는 **either party**(양측 모두 관계를 중단·종료할 수 있다, D2 상호성).

### POST /api/admin/partnerships

host 테넌트 A 가 partner 테넌트 B 에게 bounded `delegatedScope` 를 위임하는 파트너십을 **invite**(→ `PENDING`). host 측 `TENANT_ADMIN` 만.

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `partnership.manage`
**Granted to roles**: `TENANT_ADMIN`(host 테넌트 한정)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <host 활성 테넌트>`, `Idempotency-Key: <required>`

**Tenant confinement (D2)**: `host_tenant_id` = `X-Tenant-Id` 이며 actor 는 이에 대해 `partnership.manage` 스코프를 보유해야 한다 — `TENANT_ADMIN @ acme` 는 host=acme 로만 invite 가능.

**Request**:
```json
{
  "partnerTenantId": "globex-corp",
  "delegatedScope": { "domains": ["wms", "scm"], "roles": ["WMS_OUTBOUND_OPERATOR", "SCM_PLANNER"] }
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `partnerTenantId` | string | Y | tenantId 정규식; `!= host`(self 금지); `!= '*'`; 존재하는 ACTIVE 테넌트 |
| `delegatedScope.domains` | string[] | Y | 비어있지 않음; 각 도메인 키가 host 의 보유 도메인 이내(≤-own) |
| `delegatedScope.roles` | string[] | Y | 각 role 이 **비-admin**(`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`/`SUPER_ADMIN` 금지) + host 자신이 보유(≤-own across org) |

**Response 201**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "status": "PENDING",
  "delegatedScope": { "domains": ["wms", "scm"], "roles": ["WMS_OUTBOUND_OPERATOR", "SCM_PLANNER"] },
  "invitedAt": "2026-07-04T10:00:00Z"
}
```

**Side Effects**: `admin_actions: action_code=PARTNERSHIP_INVITE, permission_used=partnership.manage, target_type=PARTNERSHIP, target_id=<partnershipId>, target_tenant_id=<partnerTenantId>`. Outbox `partnership.invited`.

### POST /api/admin/partnerships/{partnershipId}:accept

partner 테넌트 B 가 invite 를 **수락**(→ `ACTIVE`). partner 측 `TENANT_ADMIN` 만.

**Required permission**: `partnership.manage` · **Granted to roles**: `TENANT_ADMIN`(partner 테넌트 한정)
**Headers**: `Authorization`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <partner 활성 테넌트>`

**Tenant confinement (D2)**: `X-Tenant-Id` == 파트너십의 `partner_tenant_id` 이며 actor 가 이에 대해 `partnership.manage` 스코프 보유. host 측은 accept 불가(상호성 — 수락은 위임받는 쪽의 권리).

**Response 200**: invite 응답 shape + `status=ACTIVE` + `acceptedAt`.

**Side Effects**: `admin_actions: action_code=PARTNERSHIP_ACCEPT`. Outbox `partnership.accepted`.

### POST /api/admin/partnerships/{partnershipId}:suspend · :reactivate · :terminate

파트너십 lifecycle 전이. **either party**(host 또는 partner 의 `TENANT_ADMIN`)가 호출 가능 — 관계 중단·종료는 상호적이다(D2). cascade-revoke(D6): SUSPENDED/TERMINATED 전이는 그 파트너십에서 파생한 모든 participant 의 host-reach 를 다음 요청에서 0 으로 만든다.

**Required permission**: `partnership.manage` · **Granted to roles**: `TENANT_ADMIN`(host 또는 partner 한정)
**Headers**: `Authorization`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <host 또는 partner 활성 테넌트>`

**Tenant confinement (D2)**: `X-Tenant-Id` ∈ {`host_tenant_id`, `partner_tenant_id`} 이며 actor 가 이에 대해 `partnership.manage` 스코프 보유(양 당사자 중 하나).

**Response 200**: invite 응답 shape + 전이된 `status` + 해당 시각 필드.

**Side Effects**: `admin_actions: action_code=PARTNERSHIP_SUSPEND|PARTNERSHIP_REACTIVATE|PARTNERSHIP_TERMINATE`. Outbox `partnership.suspended`/`partnership.reactivated`/`partnership.terminated`. `partnership.terminated` 는 **one-shot** cascade 이벤트 1건(operator 당 N 이벤트 아님, D6). 동일-status no-op 전이(예: SUSPENDED→suspend)는 200 + 이벤트 미발행.

### GET /api/admin/partnerships

actor 의 활성 테넌트가 **당사자(host 또는 partner)인** 파트너십 목록. host-side(내가 발행) + partner-side(나에게 위임됨) 양방 뷰.

**Required permission**: `partnership.manage` · **Granted to roles**: `TENANT_ADMIN`
**Headers**: `Authorization`, `X-Tenant-Id: <활성 테넌트>`

**Query parameters**: `role` (`host`|`partner`|둘 다=미지정), `status` (enum 필터), `page`, `size`(max 100).

**Tenant confinement (D2)**: 결과는 `X-Tenant-Id ∈ {host_tenant_id, partner_tenant_id}` 인 row 로 confine(목록 읽기도 스코프 confine — D2 read parity). 타 테넌트의 파트너십은 노출되지 않는다. `X-Tenant-Id` 자체가 actor 의 `partnership.manage` 스코프 밖이면 `403 PARTNERSHIP_SCOPE_DENIED`(읽기이므로 DENIED 감사 행 없음). 🔴 TASK-MONO-737 이전 구현은 이 판정이 **없었다** — 이 절이 요구한 D2 read parity 를 코드가 어기고 있었고, IAM 게이트웨이가 헤더를 벗기던 동안 가려져 있었다.

**Response 200**: `{ items: [ { partnershipId, hostTenantId, partnerTenantId, status, delegatedScope, myRole: "host"|"partner", invitedAt, acceptedAt, participantCount }, ... ], page, size, totalElements, totalPages }`.

### POST /api/admin/partnerships/{partnershipId}/participants/{operatorId}

partner 테넌트 B 가 **자기 소유 operator** 를 파트너십 participant 로 배정(D4). ACTIVE 파트너십에서만. partner 측 `TENANT_ADMIN` 만 — host 는 개별 B-사람을 지명하지 않는다(D4-B 거부).

**Required permission**: `partnership.manage` · **Granted to roles**: `TENANT_ADMIN`(partner 테넌트 한정)
**Headers**: `Authorization`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <partner 활성 테넌트>`

**Tenant confinement (D2)**: `X-Tenant-Id` == `partner_tenant_id`. 추가로 대상 `operatorId` 의 home `tenant_id` == `partner_tenant_id`(B 는 자기 사람만) — 위반 → `422 PARTICIPANT_NOT_OWN_OPERATOR`.

**Request** (optional participant 좁힘):
```json
{ "participantScope": { "domains": ["wms"], "roles": ["WMS_OUTBOUND_OPERATOR"] } }
```
`participantScope` 생략/`null` ⟺ `delegatedScope` 전체(net-zero 기본). 비-`null` 은 `⊆ delegatedScope` 여야 함(초과 원소 → `422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION`).

**Response 201**: `{ partnershipId, operatorId, participantScope, assignedAt }` (`participantScope` null 시 omit).

**Side Effects**: `admin_actions: action_code=PARTNERSHIP_PARTICIPANT_ADD, target_id=<operatorId>, target_tenant_id=<partner_tenant_id>`. Outbox `partnership.participant_added`. 배정 즉시 그 operator 의 assume-tenant reach 에 host 가 추가(다음 발급부터).

### DELETE /api/admin/partnerships/{partnershipId}/participants/{operatorId}

participant 해제(B 가 자기 직원 offboard 또는 참여 종료). partner 측 `TENANT_ADMIN` 만. 해제 즉시 그 operator 의 host-reach 파생 소멸(D6 individual offboarding — A-측 조치 불요).

**Required permission**: `partnership.manage` · **Granted to roles**: `TENANT_ADMIN`(partner 테넌트 한정)
**Headers**: `Authorization`, `X-Operator-Reason: <required>`, `X-Tenant-Id: <partner 활성 테넌트>`

**Response 204** (no content).

**Side Effects**: `admin_actions: action_code=PARTNERSHIP_PARTICIPANT_REMOVE`. Outbox `partnership.participant_removed`.

**Errors** (Partnership Management 공통):

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `partnership.manage` 권한 없음 |
| 403 | `PARTNERSHIP_SCOPE_DENIED` | actor 의 `X-Tenant-Id` 가 요구되는 acting-side 테넌트(host/partner) 스코프 밖 (D2 confinement) |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |
| 400 | `VALIDATION_ERROR` | `partnerTenantId` 정규식/self/`'*'` 위반, `delegatedScope` 형식 오류 |
| 422 | `PARTNERSHIP_SCOPE_INVALID` | (invite) `delegatedScope` 가 admin role 포함 또는 host 미보유(≤-own across org 위반) |
| 422 | `PARTICIPANT_NOT_OWN_OPERATOR` | (participant add) 대상 operator 의 home tenant ≠ `partner_tenant_id` |
| 422 | `PARTICIPANT_SCOPE_EXCEEDS_DELEGATION` | (participant add) `participantScope ⊄ delegatedScope` |
| 404 | `PARTNERSHIP_NOT_FOUND` | `partnershipId` 미존재 / actor 당사자 아님(enumeration-safe) |
| 404 | `OPERATOR_NOT_FOUND` | (participant) `operatorId` 미존재 |
| 404 | `PARTICIPANT_NOT_FOUND` | (participant delete) 배정 부재 |
| 409 | `PARTNERSHIP_ALREADY_EXISTS` | (invite) 동일 `(host, partner)` 파트너십이 이미 PENDING/ACTIVE |
| 409 | `PARTNERSHIP_TRANSITION_INVALID` | 상태머신 가드 위반(예: PENDING 아닌데 accept, TERMINATED 재개) |

> **파생 권한은 이 표면 밖.** 위 엔드포인트는 파트너십 **관계 상태**만 변경한다. B-operator 가 A 를 실제 운영하는 것은 별도 assume-tenant 발급 경로이며, 그 권한은 `delegated_scope ∩ participant ∩ host-holds` 로 캡되고 admin 권한은 절대 포함하지 않는다([rbac.md](../../services/admin-service/rbac.md#cross-org-partner-delegation-confinement-adr-mono-045-d3d5), M1 single-tenant 토큰 보존).

---

## Org Hierarchy (ADR-MONO-047)

**org-node 계층**의 operator-facing 관리 표면 — 한 회사가 여러 격리 service-tenant 를 소유하는 **grouping tree**(회사 → 서비스 → 도메인)와, 그 트리를 따라 **아래로만 좁혀 상속**되는 entitlement **ceiling**(deny-only guardrail)을 CRUD 한다. `org_node` 는 tenant 를 **group** 할 뿐 **nest 하지 않는다**(`ADR-MONO-047 § D1`) — M1 single isolation key 는 불변이며 토큰은 여전히 정확히 하나의 `tenant_id` 만 싣는다(`ADR-MONO-047 § D6`). 모든 엔드포인트는 `@RequiresPermission("org.manage")` 로 게이트된다(deny-default, `admin_actions` 감사 — `ADR-MONO-047 § 3.5`).

admin-service 는 이 트리를 **저장하지 않는다** — 권위는 account-service(`tenants` 소유자, 따라서 `org_node` 소유자)에 있고, admin-service 는 `org.manage` 게이트 + 운영자 감사만 담당하며 [account 내부 계약](internal/admin-to-account.md#get-internalorg-nodes)으로 위임한다. cycle/depth/subset 불변식은 account-service 서버측에서 강제되며 admin-service 는 그 422 를 통과 매핑한다.

**Reach 술어** (권한 판정 기준, `ADR-MONO-047 § D5`):

- `administers(actor, N)` = actor 가 `SUPER_ADMIN` 이거나 `ancestors(N) ∪ {N}` 중 한 노드에 `ORG_ADMIN` 을 보유.
- `strictlyAdministers(actor, N)` = actor 가 `SUPER_ADMIN` 이거나 N 의 **STRICT ancestor**(N 자신 제외)에 `ORG_ADMIN` 을 보유.
- `effectiveCeiling(N)` = root→N 체인 각 노드 ceiling 의 **교집합**(`ADR-MONO-047 § D2·D6`). `UNBOUNDED` 노드는 교집합 항등원(무제한)이다.
- **actor 의 reach 밖 대상은 항상 `404 ORG_NODE_NOT_FOUND`** (존재를 누설하는 403 을 절대 반환하지 않는다) + best-effort DENIED `admin_actions` row.

> **알려진 v1 제약.** `ORG_ADMIN`(perm 집합 `{org.manage, operator.manage, tenant.admin.delegate}`)은 `TENANT_ADMIN` 을 **mint 할 수 없다** — `RoleGrantGuard` 의 ≤-own 규칙(`actor.permissions ⊇ rolePerms`)이 요구되는데 `TENANT_ADMIN` 은 추가로 `partnership.manage` 를 보유하기 때문이다. 이는 `ADR-MONO-047 § D5` 가 `ADR-MONO-024 § D3` no-escalation 을 **그대로(unchanged) 재사용**한 직접 귀결이며 의도된 것이다: `ORG_ADMIN` 에 `partnership.manage` 를 부여하면 `ADR-MONO-045 § D2`(파트너십은 두 **고객 테넌트** 간 관계)를 결정 없이 확장하게 된다. **follow-up ADR 후보로 기록**하며 여기서 고치지 않는다.

### Ceiling wire shape

ceiling 은 두 값 `mode` + `domains[]` 로 표현된다(저장 시 `ceiling_mode` + `ceiling_domains` — `ADR-MONO-047 § D1` 의 단일 `entitlement_ceiling` 스케치가 이 쌍이다):

```json
{ "mode": "UNBOUNDED" }
{ "mode": "BOUNDED", "domains": ["wms", "erp"] }
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `mode` | enum | Y | `UNBOUNDED` \| `BOUNDED` |
| `domains` | string[] | `BOUNDED` 일 때만 | 도메인 키 집합(`wms`\|`scm`\|`erp`\|`finance`\|`iam`) |

- `UNBOUNDED` = ceiling 없음 = 교집합 항등원 (**"모든 알려진 도메인" 이 아니다**).
- `BOUNDED` + `domains: []` = 아무것도 허용하지 않음(fail-closed). 위 둘은 **정반대**이며, wire 에서 절대 혼동되지 않도록 `mode` 가 존재한다.
- `mode=UNBOUNDED` 일 때 `domains[]` 는 **없거나 무시**된다. `mode=BOUNDED` 인데 `domains` 누락 → `400 VALIDATION_ERROR`.

### Org node wire shape

```json
{
  "orgNodeId": "b3f1…",
  "parentId": null,
  "name": "Acme Corp",
  "depth": 1,
  "ceiling": { "mode": "UNBOUNDED" },
  "createdAt": "2026-07-10T09:00:00Z",
  "updatedAt": "2026-07-10T09:00:00Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `orgNodeId` | string (UUID) | 불투명 식별자 |
| `parentId` | string \| null | 부모 노드 id; `null` = ROOT |
| `name` | string | 1~100 자, **유니크 아님** |
| `depth` | int | root = 1, 최대 5 (`ADR-MONO-047 § D4`) |
| `ceiling` | object | ceiling wire shape (위) |
| `createdAt` / `updatedAt` | string (ISO-8601) | — |

list 엔드포인트는 `parentId` 를 포함한 **flat array** 를 반환한다(클라이언트가 트리를 조립). nested 응답은 만들지 않는다.

### POST /api/admin/org-nodes

신규 org-node 생성. `parentId=null` 이면 ROOT 노드 → **SUPER_ADMIN 만**; 자식 노드(`parentId=N`)면 `administers(actor, N)`.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")`. ROOT 생성은 SUPER_ADMIN 만; 자식 생성은 `administers(actor, parentId)`.

**Request**:
```json
{
  "name": "Acme Corp",
  "parentId": null,
  "ceiling": { "mode": "UNBOUNDED" }
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `name` | string | Y | 1~100 자, trim 후 검증, 유니크 아님 |
| `parentId` | string \| null | Y | UUID 또는 `null`(ROOT). 비-null 은 존재·reach 내여야 함 |
| `ceiling` | object | Y | ceiling wire shape. `effectiveCeiling(parent)` 의 부분집합이어야 함(child ⊆ parent) |

**Response 201**: [org node wire shape](#org-node-wire-shape).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` 길이, `ceiling` 형식(`BOUNDED` 인데 `domains` 누락 등) |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음, 또는 ROOT 생성을 비-SUPER_ADMIN 이 시도 |
| 404 | `ORG_NODE_NOT_FOUND` | `parentId` 미존재 또는 actor reach 밖(존재 미누설) |
| 422 | `ORG_NODE_DEPTH_EXCEEDED` | 생성 결과 depth > 5 |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | `ceiling ⊄ effectiveCeiling(parent)` |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service 호출 CB open |

**Side Effects**: `admin_actions` `action_code=ORG_NODE_CREATE`, `permission_used=org.manage`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. 도메인 이벤트 없음(v1).

---

### GET /api/admin/org-nodes

actor 의 reach 로 스코프된 노드 **flat array**. SUPER_ADMIN → 전체; `ORG_ADMIN @ N` → `subtree(N)`. 클라이언트가 `parentId` 로 트리를 조립한다.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")`.

**Response 200**:
```json
{
  "items": [
    { "orgNodeId": "b3f1…", "parentId": null, "name": "Acme Corp", "depth": 1, "ceiling": { "mode": "UNBOUNDED" }, "createdAt": "2026-07-10T09:00:00Z", "updatedAt": "2026-07-10T09:00:00Z" },
    { "orgNodeId": "c7a2…", "parentId": "b3f1…", "name": "Acme WMS 사업부", "depth": 2, "ceiling": { "mode": "BOUNDED", "domains": ["wms"] }, "createdAt": "2026-07-10T09:00:00Z", "updatedAt": "2026-07-10T09:00:00Z" }
  ]
}
```

`items[]` 는 각각 [org node wire shape](#org-node-wire-shape) 이며 **flat**(nested children 없음)이다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: **없음** — read-path 는 `admin_actions` row 를 쓰지 않는다(`grantable-roles`/BE-486 read-path 규약).

---

### GET /api/admin/org-nodes/{orgNodeId}

단건 노드 조회.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `administers(actor, orgNodeId)`(reach 밖 → 404).

**Response 200**: [org node wire shape](#org-node-wire-shape).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 404 | `ORG_NODE_NOT_FOUND` | 미존재 또는 actor reach 밖 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: 없음(read-path).

---

### PATCH /api/admin/org-nodes/{orgNodeId}

노드 rename 및/또는 re-parent. 두 필드 모두 optional 이며 최소 1개 필요.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")`.
- **rename N**: `administers(actor, N)`.
- **re-parent N**: `strictlyAdministers(actor, N)` **AND** `administers(actor, newParent)`(자기 subtree 를 자기가 관리하지 않는 곳으로 옮기는 것 방지).

**Request** (둘 중 하나 또는 둘 다):
```json
{ "name": "Acme 물류", "parentId": "d4e9…" }
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `name` | string (optional) | — | 1~100 자 |
| `parentId` | string (optional) | — | 새 부모 UUID; 존재·reach 내; cycle/depth/subset 서버 검증 |

**Response 200**: [org node wire shape](#org-node-wire-shape) (변경 후).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 두 필드 모두 누락, `name` 길이 위반 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 없음, 또는 re-parent 인데 `strictlyAdministers` 불충족 |
| 404 | `ORG_NODE_NOT_FOUND` | N 또는 `newParent` 미존재/reach 밖 |
| 422 | `ORG_NODE_CYCLE` | re-parent 가 사이클 유발(N 을 자기 후손 아래로) |
| 422 | `ORG_NODE_DEPTH_EXCEEDED` | re-parent 결과 subtree 중 하나라도 depth > 5 |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | re-parent 시 N(및 임의의 후손) 의 ceiling 이 새 조상 체인 ⊄ |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=ORG_NODE_UPDATE`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. 이벤트 없음.

---

### DELETE /api/admin/org-nodes/{orgNodeId}

노드 삭제. 자식 노드와 tenant 가 **모두 없어야** 삭제 가능.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `strictlyAdministers(actor, N)` **AND** N 에 자식 노드 없음 **AND** N 에 소속 tenant 없음.

**Response 204** (no content).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 없음, 또는 `strictlyAdministers` 불충족 |
| 404 | `ORG_NODE_NOT_FOUND` | 미존재 또는 actor reach 밖 |
| 422 | `ORG_NODE_NOT_EMPTY` | 자식 노드 또는 소속 tenant 존재 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=ORG_NODE_DELETE`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. 이벤트 없음.

---

### PUT /api/admin/org-nodes/{orgNodeId}/ceiling

노드의 entitlement ceiling 을 설정(전량 교체).

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + **`strictlyAdministers(actor, N)`**. `ORG_ADMIN @ N` 은 **자기 노드의 ceiling 을 편집할 수 없다** — 그 ceiling 은 자신의 상한이며 이를 편집하는 것은 self-escalation 이다(정확히 AWS 패리티: 자기 OU 에 붙은 SCP 를 그 안에서 떼어낼 수 없다) → `403 ORG_NODE_SELF_CEILING_DENIED`.

**Request**: [ceiling wire shape](#ceiling-wire-shape).
```json
{ "mode": "BOUNDED", "domains": ["wms", "erp"] }
```

**Response 200**: [org node wire shape](#org-node-wire-shape) (`ceiling` 반영).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `mode` 누락/미지정, `BOUNDED` 인데 `domains` 누락, 알 수 없는 도메인 키 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 403 | `ORG_NODE_SELF_CEILING_DENIED` | `ORG_ADMIN @ N` 이 자기 노드 ceiling 편집 시도(`strictlyAdministers` 불충족) |
| 404 | `ORG_NODE_NOT_FOUND` | 미존재 또는 actor reach 밖 |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | 새 ceiling ⊄ `effectiveCeiling(parent)`, 또는 임의 후손 노드의 ceiling ⊄ 새 ceiling |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=ORG_NODE_CEILING_SET`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. 이벤트 없음.

---

### GET /api/admin/org-nodes/{orgNodeId}/tenants

노드 자신 + 모든 후손에 소속된 tenant id 목록.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `administers(actor, orgNodeId)`(reach 밖 → 404).

**Response 200**:
```json
{ "tenantIds": ["acme-wms", "acme-erp"] }
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 404 | `ORG_NODE_NOT_FOUND` | 미존재 또는 actor reach 밖 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: 없음(read-path).

---

### GET /api/admin/org-nodes/{orgNodeId}/admins

노드에 부여된 `ORG_ADMIN` grant 목록.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `administers(actor, orgNodeId)`(reach 밖 → 404).

**Response 200**:
```json
{
  "items": [
    { "operatorId": "op-123", "displayName": "김운영", "roleName": "ORG_ADMIN",
      "grantedAt": "2026-07-10T09:00:00Z" }
  ]
}
```

`displayName` — 운영자 표시명 (`ADR-MONO-073` **ACCEPTED ⓐ**, `TASK-MONO-670`).

- 🔴 **nullable.** 운영자 레코드를 못 찾으면 `null` 이다. 🔵 형제
  `GET /api/admin/operator-groups/{id}/members` 의 `displayName` 과 **같은 규칙·같은 이름**이다
  — 열두 번째 이름을 만들지 않는다.
- 🔴 **빈 문자열로 채우지 않는다**: 「이름이 없다」와 「운영자를 못 찾았다」가 합쳐지면 다시는
  못 갈린다. 소비자는 `null` 을 **「이름 확인 불가」로 표현**하고 **`operatorId` 로 되돌아가지
  않는다**(`TASK-PC-FE-276`).
- 🔵 `operatorId` 는 **그대로 있다.** 이것은 더하는 변경이고, 운영자가 그 id 로 지원 요청을 받는다.
- 🔵 **왜 생산자가 싣는가**: 소비자가 따로 조회하면 feature 경계를 넘고 페이지 밖 참조를 잃는다
  (`ADR-MONO-073` § 갈래 ⓑ 가 기각된 이유). 그리고 **추가 조회가 없다** — 이 핸들러는
  `operatorId` 를 만들려고 **이미 그 행을 읽고 있었고** 표시명만 버리고 있었다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 404 | `ORG_NODE_NOT_FOUND` | 미존재 또는 actor reach 밖 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: 없음(read-path).

---

### POST /api/admin/org-nodes/{orgNodeId}/admins

노드에 `ORG_ADMIN`(또는 그 이하 노드-스코프 role) grant 를 부여.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `administers(actor, N)` **AND** 부여 role ⊆ actor 자신 보유(`ADR-MONO-024 § D3` `RoleGrantGuard`, 그대로 재사용) **AND** 부여 도메인 ⊆ `effectiveCeiling(N)` **AND** role ≠ `SUPER_ADMIN`.

**Request**:
```json
{ "operatorId": "op-123", "roleName": "ORG_ADMIN" }
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `operatorId` | string | Y | 존재하는 운영자; reach 밖 → 404 |
| `roleName` | string | Y | 비-`SUPER_ADMIN`; actor 보유 이내(≤-own); 파생 도메인 ⊆ `effectiveCeiling(N)` |

**Response 201**:
```json
{ "orgNodeId": "c7a2…", "operatorId": "op-123", "roleName": "ORG_ADMIN", "grantedAt": "2026-07-10T09:00:00Z" }
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `operatorId`/`roleName` 누락/형식 오류 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 없음, 또는 부여 role ⊄ actor 보유(no-escalation) 또는 role = `SUPER_ADMIN` |
| 404 | `ORG_NODE_NOT_FOUND` | 노드 또는 `operatorId` 미존재/reach 밖 |
| 422 | `ORG_ADMIN_GRANT_OUT_OF_CEILING` | 부여 role 의 파생 도메인 ⊄ `effectiveCeiling(N)` |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=ORG_ADMIN_GRANT`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`(부여 대상 operator 는 `detail`). 이벤트 없음.

---

### DELETE /api/admin/org-nodes/{orgNodeId}/admins/{operatorId}

노드의 `ORG_ADMIN` grant 회수.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + `administers(actor, orgNodeId)`(reach 밖 → 404).

**Response 204** (no content).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 404 | `ORG_NODE_NOT_FOUND` | 노드 미존재/reach 밖, 또는 해당 operator 가 N 에 `ORG_ADMIN` grant 없음(enumeration-safe) |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=ORG_ADMIN_REVOKE`, `target_type='ORG_NODE'`, `target_id=<orgNodeId>`. 이벤트 없음.

---

### 테넌트 소속 — 두기 · 옮기기 · 빼기 (`TASK-BE-625`, `ADR-MONO-047` § 개정 2026-10-07)

테넌트 T 의 소속을 «지금 위치»(노드 S 또는 무소속) → «목적지»(노드 D 또는 무소속)로 바꾸는 **쓰기 하나**. 넣기(무소속 → D) · 옮기기(S → D) · 빼기(S → 무소속)가 모두 이것이다. 소속은 T 의 **관리 범위**(S·D 와 그 조상의 `ORG_ADMIN` 이 T 를 관리하게 된다, D5)와 **유효 상한**(D 의 상한이 T 의 도메인을 좁힌다, D2·D6)을 함께 옮기는 쓰기라, **양쪽을 다 관리하는 actor 만** 할 수 있다.

**판정 — 양쪽 관리자** (단일 결정 지점, 판정 순서대로):

| 쪽 | 조건 | 실패 |
|---|---|---|
| 출발 (T 가 노드 S 아래) | `administers(actor, S)` — `SUPER_ADMIN` 또는 S 와 그 조상의 `ORG_ADMIN`. 🔴 T 의 `TENANT_ADMIN` 은 여기 들지 **않는다** — 혼자 S 에서 빼면 S 의 상한을 벗어난다 | `404 TENANT_NOT_FOUND` |
| 출발 (T 가 무소속) | T 가 actor 의 `operator.manage` 관리 범위 안 — `SUPER_ADMIN` 또는 T 의 `TENANT_ADMIN`(라이더 P1, **무소속 T 에만**). 무소속 T 는 어떤 `ORG_ADMIN` subtree 에도 들지 않는다 | `404 TENANT_NOT_FOUND` |
| 도착 (목적지 = 노드 D) | `administers(actor, D)` | `404 ORG_NODE_NOT_FOUND` |
| 도착 (목적지 = 무소속) | 조건 없음 — 출발 쪽만 | — |

- **출발 쪽을 먼저 판정한다.** 출발 쪽이 실패하면 목적지가 무엇이든 응답은 `404 TENANT_NOT_FOUND` 하나다 — 관리하지 않는 테넌트에 대해 «목적지 노드가 있는가» 를 캐 볼 수 없다. 출발 쪽이 실패한 테넌트와 없는 테넌트는 응답으로 구별되지 않는다.
- **범위 밖은 403 이 아니라 404** + best-effort DENIED `admin_actions` 행(org-node 규율과 같음 — 존재를 흘리지 않는다).
- 판정 **뒤에** 같은 목적지인지 본다 — 목적지 = 지금 위치여도 판정을 건너뛰지 않는다(건너뛰면 «200 이면 T 가 이미 D 아래» 라는 소속 정보가 관리하지 않는 actor 에게 샌다).
- `SUPER_ADMIN` 은 모든 노드·테넌트를 관리하므로 (존재하는 대상에 대해) 항상 통과한다(net-zero).
- 판정과 쓰기 사이에 다른 요청이 소속을 바꾸면 쓰기는 **판정한 출발 쪽**을 조건으로 하므로(내부 계약의 `expectedOrgNodeId`) `409 TENANT_ORG_NODE_CONFLICT` 로 거절된다 — 판정하지 않은 출발지에서 T 를 빼 오는 길이 없다.

#### Placement effect wire shape

소속 변경이 T 의 **유효 도메인**에 미치는 효과(라이더 P2 — 상한은 거절이 아니라 확인). 상한은 deny-only 라 **구독 행은 바뀌지 않는다** — 빼거나 넓은 노드로 옮기면 그대로 돌아온다.

```json
{
  "tenantId": "acme-wms",
  "fromOrgNodeId": "b3f1…",
  "toOrgNodeId": "c7a2…",
  "domainsBefore": ["finance", "wms"],
  "domainsAfter": ["wms"],
  "lostDomains": ["finance"],
  "gainedDomains": []
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string | 대상 테넌트 |
| `fromOrgNodeId` | string \| null | 지금 위치(`null` = 무소속) |
| `toOrgNodeId` | string \| null | 목적지(`null` = 무소속) |
| `domainsBefore` | string[] | `ACTIVE 구독 ∩ effectiveCeiling(from)` — 무소속이면 상한 없음(`UNBOUNDED`) |
| `domainsAfter` | string[] | `ACTIVE 구독 ∩ effectiveCeiling(to)` |
| `lostDomains` | string[] | `domainsBefore − domainsAfter` — 확인 화면의 «이동하면 꺼지는 도메인» |
| `gainedDomains` | string[] | `domainsAfter − domainsBefore` |

세 배열은 ACTIVE 구독 목록의 순서를 지킨다. 계산은 account-service(구독·상한의 권위)가 한 번에 한다 — 콘솔이 따로 계산하지 않는 이유는 `ORG_ADMIN` 이 T 의 구독을 읽을 권한(`subscription.manage`)이 없기 때문이다.

### GET /api/admin/tenants/{tenantId}/org-node/preview

소속 변경의 **미리보기**(쓰기 없음). 확인 화면(P2)이 «잃는 도메인» 을 쓰기 **전에** 보여 주려면 효과가 쓰기 전에 읽혀야 한다.

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + 위 **양쪽 관리자** 판정(쓰기와 같은 판정 — 미리보기로 남의 테넌트의 도메인을 읽을 수 없다).

**Query parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `orgNodeId` | string | N | 목적지 노드. **생략 = 무소속(빼기)** |

**Response 200**: [placement effect wire shape](#placement-effect-wire-shape).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음 |
| 404 | `TENANT_NOT_FOUND` | 테넌트 미존재 또는 출발 쪽 판정 실패 |
| 404 | `ORG_NODE_NOT_FOUND` | 목적지 노드 미존재 또는 도착 쪽 판정 실패 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: 성공은 감사 행 없음(read-path). 판정 실패는 best-effort DENIED 행(`GET /api/admin/org-nodes/{orgNodeId}` 와 같음).

### PUT /api/admin/tenants/{tenantId}/org-node

테넌트 T 의 소속을 목적지로 바꾼다(넣기 · 옮기기 · 빼기).

**Auth required**: 운영자 JWT, `@RequiresPermission("org.manage")` + 위 **양쪽 관리자** 판정. `X-Operator-Reason` 필수(org-node 쓰기와 같음). `Idempotency-Key` 는 받지 않는다(org-node 쓰기와 같음) — PUT 이 상태 멱등이다.

**Request**:
```json
{ "orgNodeId": "c7a2…" }
{ "orgNodeId": null }
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `orgNodeId` | string \| null | Y(키) | 목적지 노드 UUID, 또는 `null` = 무소속(빼기). **키 자체가 없으면(`{}`) `400`** — `null` 이 «빼기» 이므로 오타 난 키가 조용히 빼기가 되면 안 된다. 본문이 없거나 빈 문자열이어도 `400` |

**Response 200**: [placement effect wire shape](#placement-effect-wire-shape) + `changed`:

```json
{ "tenantId": "acme-wms", "fromOrgNodeId": "b3f1…", "toOrgNodeId": "c7a2…",
  "domainsBefore": ["finance", "wms"], "domainsAfter": ["wms"],
  "lostDomains": ["finance"], "gainedDomains": [], "changed": true }
```

- **멱등**: 목적지 = 지금 위치면 쓰기 없이 `200` · `changed=false` · `fromOrgNodeId == toOrgNodeId`. 같은 요청을 반복하면 상태는 같고(첫 요청 뒤 T 는 이미 목적지에 있다) 두 번째 응답은 `changed=false` 다.
- **상한은 거절 사유가 아니다**(P2) — `lostDomains` 가 비어 있지 않아도 쓴다. 확인은 미리보기로 쓰기 **전에** 한다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 본문 없음 · 형식 오류 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `org.manage` 권한 없음(예: `TENANT_ADMIN` 만 가진 actor) |
| 404 | `TENANT_NOT_FOUND` | 테넌트 미존재 또는 출발 쪽 판정 실패(존재 미누설) |
| 404 | `ORG_NODE_NOT_FOUND` | 목적지 노드 미존재 · 도착 쪽 판정 실패 · **판정 뒤 쓰기 전에 노드가 삭제됨**(account-service 의 존재 확인이 권위) |
| 409 | `TENANT_ORG_NODE_CONFLICT` | 판정과 쓰기 사이에 T 의 소속이 바뀜 — 소속 불변, 다시 시도 |
| 503 | `INTEGRATION_UNAVAILABLE` | account-service CB open |

**Side Effects**: `admin_actions` `action_code=TENANT_ORG_NODE_ASSIGN`, `permission_used=org.manage`, `target_type='TENANT'`, `target_id=<tenantId>`, `target_tenant_id=<tenantId>`, `reason=<X-Operator-Reason>`, `detail="from_org_node_id=<S|null> to_org_node_id=<D|null> changed=<true|false>"` — 성공한 요청마다 한 행(no-op 포함, org-node 쓰기와 같음). 판정 실패는 best-effort DENIED 행(`target_type='TENANT'`, `target_id=<tenantId>`, `detail` 에 실패한 쪽 `side=SOURCE|DESTINATION` 과 출발·목적지). 이벤트 없음. admin-service 의 subtree 캐시를 비운다(이 인스턴스) — 다른 인스턴스는 subtree 캐시 TTL(기본 5초) 안에 따라온다.

---

## Operator Group Management (ADR-MONO-046)

**운영자 그룹**의 operator-facing 관리 표면 — `admin_operators` 를 named unit(`operator_group`, [data-model.md](../../services/admin-service/data-model.md))으로 묶고, 역할/tenant-assignment 를 **여러 operator 에 한 번에** 부여한다(AWS IAM User Group / Google Group 의 workforce-grouping facet). **v1 은 fan-out**(`ADR-MONO-046 § D2-A`): group 에 grant 하면 각 현재 멤버의 flat `operator_tenant_assignment`/`admin_operator_roles` row 로 materialise 되고 `group_origin` 마커로 태깅되며([rbac.md](../../services/admin-service/rbac.md#operator-group-fan-out-adr-mono-046-d2-a)), 그룹 멤버십은 **평가-시점 edge 가 아니다** — `PermissionEvaluator`·perm-cache·모든 confinement 축은 byte-unchanged. 모든 엔드포인트(read 포함)는 `@RequiresPermission("group.manage")` 로 게이트되고(deny-default, `admin_actions` 감사 — D6), 모든 변이는 **`X-Operator-Reason` reason-gated** + `TenantScopeGuard`(대상 = `operator_group.tenant_id`, D3) confine 된다. grant 는 추가로 `RoleGrantGuard`(≤-own no-escalation, `ADR-MONO-024 § D3` 재사용, D4)를 grant-time + add-member-time 양쪽에서 거친다.

> **파생 권한 = 평범한 직접 grant.** 이 표면은 group aggregate 와 그 멤버십/grant 템플릿만 다룬다. 멤버가 실제로 얻는 권한은 fan-out 으로 materialise 된 **평범한 flat per-operator row** 이며(직접 grant 와 구별 불가, `group_origin` 마커만 상이), 그 마커는 lifecycle 부기 전용이다. inheritance 평가 경로(D2-B)는 후속 ADR — v1 은 group 을 새 평가/confinement 축으로 만들지 **않는다**.

### Group wire shape

```json
{
  "groupId": "00000000-0000-7000-8000-0000000000g1",
  "tenantId": "acme-corp",
  "name": "물류 지원팀",
  "description": "WMS 출고 지원 스쿼드",
  "memberCount": 5,
  "grantCount": 3,
  "createdAt": "2026-07-19T09:00:00Z",
  "updatedAt": "2026-07-19T09:00:00Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `groupId` | string (UUID v7) | 외부 식별자(`operator_group.group_id`) |
| `tenantId` | string | group 소유 테넌트(`TenantScopeGuard` 대상). `'*'` 아님 |
| `name` | string | 1~120 자, `(tenantId, name)` 테넌트 내 유니크 |
| `description` | string \| null | 선택 설명(≤255 자) |
| `memberCount` / `grantCount` | int | 현재 멤버 수 / 현재 grant 템플릿 수(조회 편의) |
| `createdAt` / `updatedAt` | string (ISO-8601) | — |

### Group grant wire shape

각 grant 템플릿(`operator_group_grant`)은 역할 **또는** tenant-assignment 다:

```json
{ "grantId": "…", "type": "ROLE", "roleName": "SUPPORT_LOCK", "grantedAt": "2026-07-19T09:00:00Z" }
{ "grantId": "…", "type": "TENANT_ASSIGNMENT", "tenantId": "acme-corp", "grantedAt": "2026-07-19T09:00:00Z" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `grantId` | string (UUID v7) | 외부 식별자(`operator_group_grant.grant_id`) — 개별 revoke path 에 사용 |
| `type` | enum | `ROLE` \| `TENANT_ASSIGNMENT` |
| `roleName` | string | `type=ROLE` 일 때 부여 역할명 |
| `tenantId` | string | `type=TENANT_ASSIGNMENT` 일 때 부여 대상(ASSIGNED) 테넌트 |
| `grantedAt` | string (ISO-8601) | — |

### POST /api/admin/groups

신규 운영자 그룹 생성. 생성자는 `group.manage` + 대상 `tenantId` 에 대한 스코프를 보유해야 한다.

**Auth required**: Yes (operator token, `token_type=admin`) · **Required permission**: `group.manage`
**Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트 한정), `ORG_ADMIN`(subtree 테넌트 한정)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`, `Idempotency-Key: <required>`

**Tenant confinement (D3)**: actor 는 `tenantId` 에 대해 `group.manage` 스코프(`effectiveAdminScope`)를 보유해야 한다 — `TENANT_ADMIN @ acme` 는 `tenantId=acme` 로만. SUPER_ADMIN(`'*'`) net-zero.

**Request**:
```json
{
  "tenantId": "acme-corp",
  "name": "물류 지원팀",
  "description": "WMS 출고 지원 스쿼드"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `tenantId` | string | Y | tenantId 정규식; `!= '*'`(플랫폼-전역 그룹 불가); actor 스코프 내 |
| `name` | string | Y | 1~120 자, trim 후 검증, `(tenantId, name)` 유니크 |
| `description` | string | N | ≤255 자 |

**Response 201**: [group wire shape](#group-wire-shape) (`memberCount=0`, `grantCount=0`).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` 길이, `tenantId` 정규식/`'*'` 위반 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 헤더 누락 |
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `group.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | actor 가 `tenantId` 스코프 밖 (best-effort DENIED row) |
| 409 | `GROUP_NAME_CONFLICT` | `(tenantId, name)` 그룹 이미 존재 |

**Side Effects**: `admin_actions` `action_code=GROUP_CREATE`, `permission_used=group.manage`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<tenantId>`. fan-out 없음(멤버·grant 0).

---

### GET /api/admin/groups

actor 스코프 내 그룹 **목록**(D3 read confine — 타 테넌트 그룹 미노출). `tenant.manage`/`org.manage` read-gating 규약대로 read 도 `group.manage` 게이트.

**Auth required**: Yes · **Required permission**: `group.manage` · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`, `ORG_ADMIN`

**Headers**: `Authorization: Bearer <operator-token>`

**Query parameters**: `tenantId`(optional 필터 — 생략 시 actor 스코프 전체), `page`, `size`(max 100).

**Tenant confinement (D3)**: 결과는 `operator_group.tenant_id ∈ actor effectiveAdminScope(group.manage)` 인 row 로 confine. SUPER_ADMIN(`'*'`) → 전체.

**Response 200**: `{ "items": [ <group wire shape>, ... ], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }`.

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`.

**Side Effects**: 없음(read-path — `admin_actions` row 미기록, `grantable-roles`/BE-486 규약).

---

### GET /api/admin/groups/{groupId}

단건 그룹 조회.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프; 밖 → 404) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`, `ORG_ADMIN`

**Path Variable**: `groupId` — `operator_group.group_id` (UUID v7)

**Response 200**: [group wire shape](#group-wire-shape).

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 404 `GROUP_NOT_FOUND`(미존재 또는 actor 스코프 밖 — enumeration-safe).

**Side Effects**: 없음(read-path).

---

### PATCH /api/admin/groups/{groupId}

그룹 rename / describe. 두 필드 optional, 최소 1개 필요. **fan-out 무관**(멤버 grant 불변).

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Request** (둘 중 하나 또는 둘 다):
```json
{ "name": "물류 지원팀 (개편)", "description": "…" }
```

**Response 200**: [group wire shape](#group-wire-shape) (변경 후).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 두 필드 모두 누락, `name` 길이 위반 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `group.manage` 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 그룹 `tenant_id` 가 actor 스코프 밖 |
| 404 | `GROUP_NOT_FOUND` | 미존재/스코프 밖 |
| 409 | `GROUP_NAME_CONFLICT` | 새 `name` 이 `(tenantId, name)` 충돌 |

**Side Effects**: `admin_actions` `action_code=GROUP_UPDATE`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`.

---

### DELETE /api/admin/groups/{groupId}

그룹 삭제 → **cascade-revoke**(D5): 이 그룹의 모든 `group_origin=<groupId>` fan-out row(멤버들의 assignment/role)를 revoke. 멤버의 **직접 grant(`group_origin IS NULL`)는 불변**. 멤버십·grant 템플릿은 FK CASCADE.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Response 204** (no content).

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 404 `GROUP_NOT_FOUND`, 400 `REASON_REQUIRED`.

**Side Effects**: `admin_actions` `action_code=GROUP_DELETE`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`. cascade-revoke 는 삭제와 **단일 트랜잭션**(감사·outbox 원자성, D6). 영향받은 멤버의 perm-cache 무효화. 이벤트는 v1 audit-only(소비자 존재 시 `admin_outbox` v2 새 `topicFor`, D6).

---

### GET /api/admin/groups/{groupId}/members

그룹 멤버 목록.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`, `ORG_ADMIN`

**Response 200**:
```json
{
  "items": [
    { "operatorId": "op-123", "displayName": "김운영", "addedAt": "2026-07-19T09:00:00Z" }
  ]
}
```

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 404 `GROUP_NOT_FOUND`.

**Side Effects**: 없음(read-path).

---

### POST /api/admin/groups/{groupId}/members

멤버 추가 → **fan-out**(D5): 그룹의 현행 grant 를 새 멤버로 materialise. 멤버는 그룹 테넌트 소속 operator 만.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`, `Idempotency-Key: <required>`

**Tenant confinement (D3)**: 대상 `operatorId` 의 home `tenant_id` == 그룹 `tenant_id`(그룹은 자기 테넌트 사람만) — 위반 → `422 GROUP_MEMBER_TENANT_MISMATCH`.

**No-escalation (D4, add-member 재검사)**: 그룹의 현행 grant 를 이 멤버로 fan-out 할 때 각 grant 가 actor 의 `effectiveAdminScope`/`RoleGrantGuard` 이내여야 한다 — 자기 미보유 role/tenant 를 새 멤버에게 우회 부여 불가. 위반 → `403 ROLE_GRANT_FORBIDDEN` 또는 `422 GROUP_GRANT_NO_ESCALATION`.

**Request**:
```json
{ "operatorId": "op-123" }
```

**Response 201**: `{ "operatorId": "op-123", "displayName": "김운영", "addedAt": "2026-07-19T09:00:00Z", "fannedOutGrants": 3 }` (`fannedOutGrants` = 이 멤버에 새로 materialise 된 grant 수; 이미 보유한 동등 직접 grant 는 idempotent skip 되어 미포함).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` / `REASON_REQUIRED` | `operatorId` 누락 / reason 누락 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `group.manage` 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 그룹 `tenant_id` 가 actor 스코프 밖 |
| 403 | `ROLE_GRANT_FORBIDDEN` | fan-out grant 가 actor 보유 초과(no-escalation) |
| 404 | `GROUP_NOT_FOUND` | 그룹 미존재/스코프 밖 |
| 404 | `OPERATOR_NOT_FOUND` | `operatorId` 미존재 |
| 409 | `GROUP_MEMBER_ALREADY_EXISTS` | (groupId, operatorId) 멤버십 이미 존재 |
| 422 | `GROUP_MEMBER_TENANT_MISMATCH` | 대상 operator home tenant ≠ 그룹 tenant |
| 422 | `GROUP_GRANT_NO_ESCALATION` | fan-out grant 가 tenant-scope no-escalation 위반 |

**Side Effects**: `admin_actions` `action_code=GROUP_MEMBER_ADD`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`(대상 operator 는 `detail`). fan-out row(`group_origin=<group.id>`) INSERT + 멤버 perm-cache 무효화 — 멤버십 add 와 **단일 트랜잭션**(D5/D6). idempotent: 멤버가 이미 보유한 동등 직접 grant 는 중복 생성 안 함.

---

### DELETE /api/admin/groups/{groupId}/members/{operatorId}

멤버 제거 → 그 멤버의 `group_origin=<groupId>` fan-out row **만** revoke(D5). 멤버의 **직접 grant(`group_origin IS NULL`)는 불변**.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Response 204** (no content).

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 404 `GROUP_NOT_FOUND`, 404 `GROUP_MEMBER_NOT_FOUND`(멤버십 부재), 400 `REASON_REQUIRED`.

**Side Effects**: `admin_actions` `action_code=GROUP_MEMBER_REMOVE`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`. cascade-revoke(`group_origin=<groupId>` row) + 멤버십 delete 단일 트랜잭션 + 멤버 perm-cache 무효화. **직접 grant·타 그룹발 row 미변경**.

---

### GET /api/admin/groups/{groupId}/grants

그룹의 현행 grant 템플릿 목록.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`, `ORG_ADMIN`

**Response 200**: `{ "items": [ <group grant wire shape>, ... ] }`.

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 404 `GROUP_NOT_FOUND`.

**Side Effects**: 없음(read-path).

---

### POST /api/admin/groups/{groupId}/grants

그룹에 역할 및/또는 tenant-assignment grant → **fan-out**(D5): 전 현재 멤버로 materialise. **no-escalation(D4, grant-time)**: actor 는 자기 보유 이내만 grant 가능.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) + `RoleGrantGuard`(≤-own) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`, `Idempotency-Key: <required>`

**No-escalation (D4)**: 각 `roles[]` 는 `RoleGrantGuard`(비-`SUPER_ADMIN` + actor 권한 ⊇ role 권한, `ADR-MONO-024 § D3` 재사용); 각 `tenantAssignments[].tenantId` 는 actor 의 `effectiveAdminScope`(operator.manage) 이내여야 한다. 위반 → `403 ROLE_GRANT_FORBIDDEN`(role) / `422 GROUP_GRANT_NO_ESCALATION`(tenant).

**Request** (roles / tenantAssignments 중 최소 하나):
```json
{
  "roles": ["SUPPORT_LOCK"],
  "tenantAssignments": [{ "tenantId": "acme-corp" }]
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `roles` | string[] | roles/tenantAssignments 중 최소 하나 | 존재하는 role; 비-`SUPER_ADMIN`; actor 보유 이내(≤-own) |
| `tenantAssignments` | object[] | 〃 | 각 `tenantId` 는 tenantId 정규식 + actor `operator.manage` 스코프 이내 |

**Response 201**: `{ "items": [ <group grant wire shape>, ... ], "fannedOutRows": 8 }` (생성된 grant 템플릿 + 전 멤버 fan-out 으로 materialise 된 row 수; 멤버별 동등 직접 grant 는 idempotent skip).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | roles·tenantAssignments 모두 비어있음, role 명/tenantId 형식 오류 |
| 400 | `REASON_REQUIRED` | reason 누락 |
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `group.manage` 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 그룹 `tenant_id` 가 actor 스코프 밖 |
| 403 | `ROLE_GRANT_FORBIDDEN` | grant role 이 `SUPER_ADMIN` 또는 actor 보유 초과(no-escalation) |
| 404 | `GROUP_NOT_FOUND` | 그룹 미존재/스코프 밖 |
| 400 | `ROLE_NOT_FOUND` | `roles[]` 에 미존재 role — 플랫폼 공통 `RoleNotFoundException` 매핑(400)과 일관(operator role-change 표면과 동일 status; TASK-BE-520 impl 정합화). role 은 요청 body 의 잘못된 참조이므로 400 |
| 409 | `GROUP_GRANT_ALREADY_EXISTS` | 동일 (그룹, type, role/tenant) grant 템플릿 이미 존재 |
| 422 | `GROUP_GRANT_NO_ESCALATION` | tenant-assignment grant 가 actor tenant-scope 초과 |

**Side Effects**: `admin_actions` `action_code=GROUP_GRANT_ADD`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`. grant 템플릿(`operator_group_grant`) INSERT + 전 멤버 fan-out row(`group_origin=<group.id>`) INSERT + 멤버 perm-cache 무효화 — **단일 트랜잭션**(D5/D6). idempotent: 멤버가 이미 보유한 동등 직접 grant 는 skip.

---

### DELETE /api/admin/groups/{groupId}/grants/{grantId}

grant 회수 → **cascade-revoke**(D5): 이 grant 로 materialise 된 전 멤버의 `group_origin=<groupId>` row(해당 role/tenant) revoke. 멤버의 **직접 grant 불변**.

**Auth required**: Yes · **Required permission**: `group.manage` + `TenantScopeGuard`(그룹 `tenant_id` ∈ actor 스코프) · **Granted to roles**: `SUPER_ADMIN`, `TENANT_ADMIN`(자기 테넌트), `ORG_ADMIN`(subtree)

**Headers**: `Authorization: Bearer <operator-token>`, `X-Operator-Reason: <required>`

**Path Variables**: `groupId` — `operator_group.group_id`; `grantId` — `operator_group_grant.grant_id`

**Response 204** (no content).

**Errors**: 401 `TOKEN_INVALID`, 403 `PERMISSION_DENIED`, 403 `TENANT_SCOPE_DENIED`, 404 `GROUP_NOT_FOUND`, 404 `GROUP_GRANT_NOT_FOUND`(grant 템플릿 부재/타 그룹), 400 `REASON_REQUIRED`.

**Side Effects**: `admin_actions` `action_code=GROUP_GRANT_REVOKE`, `target_type='GROUP'`, `target_id=<groupId>`, `target_tenant_id=<group tenant_id>`. grant 템플릿 delete + 그 grant 의 fan-out row(`group_origin=<groupId>` + 해당 role/tenant 자연키) cascade-revoke + 멤버 perm-cache 무효화 — **단일 트랜잭션**. **직접 grant·타 grant 발 row 미변경**.

> **Idempotence & 직접-grant 보존 (D5).** fan-out 은 멤버가 이미 보유한 동등 **직접** grant(`group_origin IS NULL`)를 절대 중복/덮어쓰기 하지 않으며(grant 당 최대 1 row — `(operator, tenant)`/`(operator, role)` PK), 모든 cascade-revoke 는 `group_origin = <groupId>` 로 엄격 필터하여 직접 grant 를 절대 파괴하지 않는다. 두 그룹이 같은 `(operator, grant)` 를 grant 하면 먼저 materialise 한 그룹이 마커를 소유하는 **단일-소유 v1 규약**이다([data-model.md](../../services/admin-service/data-model.md#group_origin-마커-fan-out-substrate)).

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
