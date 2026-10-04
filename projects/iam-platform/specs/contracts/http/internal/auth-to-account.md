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

> **TASK-BE-600 (2026-09-25)** — 소비처 현황: **폼 로그인**(`CredentialAuthenticationProvider`, `X-Tenant-Id` = 자격 행의 테넌트) · ~~소셜 콜백~~ (**TASK-BE-602 부터 소셜은 이 엔드포인트를 부르지 않는다** — 아래 [`status-with-tenant`](#get-internalaccountsaccountidstatus-with-tenant) 로 옮겼다). **refresh 경로는 아직 이 조회를 하지 않는다**(`SasRefreshTokenAuthenticationProvider` — 이 문서 첫 줄의 «refresh 시 확인» 은 구현되지 않은 약속이다; 후속으로 분리, TASK-BE-600 AC-0 ①).

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 대상 계정 |

**Headers** (TASK-BE-600):

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-Tenant-Id` | No | 계정이 속한 테넌트. **없거나 공백이면 `fan-platform` 고정**(🔴 TASK-MONO-735 부터 형제 변이 엔드포인트 `/lock` · `/unlock` · `/delete` 는 헤더가 없으면 **계정 행의 테넌트**로 찾는다 — 이 읽기는 그 변경을 따르지 **않는다**. BE-600 이전 동작 그대로 · membership-service · admin-service 는 헤더를 보내지 않아 net-zero. 🔴 이 고정은 그 호출자들을 위해 **바꾸지 않는다** — «헤더 없음 = 테넌트 모름» 이 필요한 호출자는 아래 `status-with-tenant` 를 쓴다, TASK-BE-602). 🔴 BE-600 이전 이 엔드포인트만 헤더를 받지 않아, `fan-platform` 밖의 계정은 `/lock` 으로 잠글 수는 있어도 여기서는 404 로 보였다 |

**Response 200**:
```json
{
  "accountId": "string",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED",
  "statusChangedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**: 계정 미존재 (헤더가 있으면 **그 테넌트에** 미존재)
```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "Account not found",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**auth-service 매핑 규약** (TASK-BE-600 — BE-063 의미를 바꾼다):

| 응답 | 포트 결과 | 로그인 경로의 처리 |
|---|---|---|
| 200 + `status` | `Optional.of(status)` | 공유 상태 규칙(`AccountStatusRule`) 적용 — `ACTIVE` 만 통과 |
| 404 | `Optional.empty()` | **규칙을 적용할 계정 레코드가 없다** → 통과. 콘솔 운영자 자격(테넌트 `iam`)은 설계상 `accounts` 행이 없다(`R__05_seed_demo_corp_tenant_and_consumer_accounts.sql` 헤더) |
| 그 밖의 4xx (401/403/422 …) | `AccountServiceUnavailableException` | **fail-closed** — 로그인 거부 |
| 200 인데 body/`status` 없음 | `AccountServiceUnavailableException` | **fail-closed** |
| 5xx / 타임아웃 / circuit-open / IO | `AccountServiceUnavailableException` | **fail-closed** (BE-600 이전에도 예외였다 — 불변) |

🔴 **바뀐 것**: BE-063 이후 «empty» 는 404 · 그 밖의 4xx · 읽을 수 없는 200 을 한데 묶었고, 소셜 경로는 empty 를 «조회 불가 → 상태 검사 생략» 으로 읽었다. 즉 **실패한 조회가 잠금 검사를 조용히 건너뛰었다(fail-open)**. 소유자 결정(2026-09-25, TASK-BE-600 AC-2): 조회 **실패**는 폼 · 소셜 **둘 다 fail-closed**. 404 는 실패가 아니라 답이므로 empty 로 남긴다.

~~🔴 소셜 경로는 헤더를 보내지 않는다(= `fan-platform` 고정 조회)~~ — **TASK-BE-602 로 해소.** BE-600 시점의 모양: BE-507 이전에 생긴 소셜 계정은 identity 행이 `ecommerce` 여도 계정 행은 `fan-platform` 에 있어 identity 의 테넌트를 보내면 그 계정들이 404 로 빠지고, 헤더 없이 부르면 BE-507 이후 `fan-platform` 밖에서 태어난 소셜 계정이 404 로 빠졌다(잠긴 스토어 소셜 계정이 소셜로 들어왔다). **어느 테넌트를 보내도 한쪽이 틀린다** — 그래서 소셜은 테넌트를 **보내지 않고 돌려받는** 아래 엔드포인트로 옮겼다.

---

## GET /internal/accounts/{accountId}/status-with-tenant

> **TASK-BE-602 (2026-09-25)** — 계정의 **실제 테넌트**와 상태를 함께 답한다. 테넌트는 **입력이 아니라 출력**이다.
> 소비처: **소셜 콜백**(`OAuthLoginUseCase`) — 위 `/status` 호출을 **대체**한다(소셜 로그인 1회당 account-service 상태 조회 1회, 호출 수 불변).
> 소유자 결정(TASK-BE-602 AC-0): 소셜 경로의 «계정의 실제 테넌트» 출처 = account-service `accounts` 행. 신원 행(`social_identities.tenant_id`) ·
> 시작 client 의 테넌트는 BE-507 이전 계정에서 **틀리므로** 쓰지 않는다.

🔴 **테넌트 격리 규칙의 문서화된 예외** — account-service 의 «`tenant_id` 없는 `findById(id)` 금지»
([multi-tenancy.md § 격리 회귀 방지](../../../features/multi-tenancy.md#격리-회귀-방지) · `AccountRepository` javadoc)에 대한 **유일한 예외**다. 성립 조건:
① **내부 전용**(`/internal/**`, 워크로드 JWT — 게이트웨이 퍼블릭 라우트 금지) ② 조회 키가 전역 유일 PK(`accounts.id`, UUID)라 결과가 최대 1행이고
다른 테넌트의 행을 «섞어» 돌려줄 수 없다 ③ 응답이 그 행의 `tenantId` 를 **함께** 돌려줘 호출자가 테넌트를 추측하지 않는다 ④ 응답에 PII 없음
(이메일 · 프로필 미포함). 이 조건 밖의 새 «테넌트 없는 조회» 는 이 예외를 근거로 삼을 수 없다.

🔵 **같은 finder 의 두 번째 사용 (TASK-MONO-735, 2026-09-26)** — `POST /internal/accounts/{id}/lock` · `/unlock` · `/delete` 가
`X-Tenant-Id` 가 **없거나 공백이거나 `*`** 일 때만 이 finder 로 대상 계정을 찾는다([admin-to-account.md § Tenant Confinement](admin-to-account.md#tenant-confinement--x-tenant-id-task-be-467)).
성립 조건은 ①② 그대로이고, ③ 은 «호출자가 테넌트를 **말하지 않았을 때만**» 으로 바뀐다 — 구체 테넌트를 말한 호출은 여전히 그 테넌트로
한정된다(교차 → 404). ④ 응답(`accountId` · 상태 · 시각)에 PII 없음. 새 예외가 아니라 **같은 예외의 등록된 두 번째 소비처**다 —
[multi-tenancy.md](../../../features/multi-tenancy.md#격리-회귀-방지) 에 한 줄로 적었다.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 대상 계정 |

**Headers**: `X-Tenant-Id` 를 **받지 않는다**(보내도 무시 — 응답의 `tenantId` 가 답이다).

**Response 200**:
```json
{
  "accountId": "string",
  "tenantId": "string (slug — 계정 행의 tenant_id)",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED",
  "statusChangedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**: 어느 테넌트에도 그 `accountId` 의 계정 행이 없다 — `ACCOUNT_NOT_FOUND` (위 `/status` 와 같은 본문).

**auth-service 매핑 규약** (`AccountServicePort.getAccountStatusAndTenant` — `/status` 의 BE-600 규약과 **같은 선**):

| 응답 | 포트 결과 | 소셜 로그인의 처리 |
|---|---|---|
| 200 + `status` + `tenantId` | `Optional.of(result)` | `AccountStatusRule` 적용(`ACTIVE` 만 통과) · 로그인 이벤트의 `tenantId` = 이 값 |
| 404 | `Optional.empty()` | **규칙 미적용 → 통과**(BE-600 규칙 유지 — 404 를 거부로 바꾸지 않는다). 로그인 이벤트는 내지 않는다(계정의 테넌트를 모른다) |
| 그 밖의 4xx | `AccountServiceUnavailableException` | **fail-closed** — 로그인 거부 |
| 200 인데 body / `status` / `tenantId` 없음 | `AccountServiceUnavailableException` | **fail-closed** |
| 5xx / 타임아웃 / circuit-open / IO | `AccountServiceUnavailableException` | **fail-closed** |

🔵 **배포 순서**: 새 auth-service + 옛 account-service(엔드포인트 없음) 조합이면 매핑 없는 경로 → `NoResourceFoundException` →
`CommonGlobalExceptionHandler`(libs/java-web-servlet, account-service `GlobalExceptionHandler` 의 부모)가 **404** 로 답한다(코드 읽기 —
라이브 미측정). 즉 «규칙 미적용 → 통과 · 이벤트 없음» 으로 떨어진다: 로그인이 막히는 쪽으로는 가지 않지만, 그 사이 **잠긴 계정도 소셜로
들어온다**(BE-507 이후 스토어 계정에 대해선 BE-602 이전과 같음, fan-platform 계정에 대해선 **퇴행**). 두 서비스는 같은 커밋이라
재굽기에서 함께 들어가야 한다 — **account-service 를 먼저 또는 함께** 올린다.

---

## POST /internal/accounts/{accountId}/unlock — 비밀번호 재설정에 의한 자기 복구 (TASK-BE-612)

> **새 caller 등록이지 새 엔드포인트가 아니다.** 엔드포인트와 요청 형태는 [admin-to-account.md § unlock](admin-to-account.md) 그대로다.
> 소유자 결정(`TASK-BE-608` § AC-3, 2026-09-26 UTC): 비밀번호 재설정 **확인**이 성공하면(이메일 소유 증명 + 전 세션 폐기가 끝난 지점)
> **`AUTO_DETECT` 로 잠긴 계정만** `USER_RECOVERY` 로 해제한다. `ADMIN_LOCK` 등 운영자·기타 사유의 잠금은 그대로 둔다.

**호출 시점**: `ConfirmPasswordResetUseCase` 의 트랜잭션이 **커밋된 뒤**(새 비밀번호 · 세션 폐기가 확정된 뒤). 재설정이 롤백되면 호출하지 않는다.

**호출 순서**: ① `GET /internal/accounts/{accountId}/status-with-tenant` 로 상태를 본다 → ② `status == LOCKED` 일 때만 unlock 을 호출한다
(`ACTIVE` 등이면 호출 없음 — 불필요한 호출 · 불필요한 이력 행 금지).

**Headers**: `X-Tenant-Id` 를 **보내지 않는다** — account-service 가 계정 행의 테넌트로 찾는다(위 § status-with-tenant 의 «같은 finder 의 두 번째 사용»).

**Request**:
```json
{ "reason": "USER_RECOVERY" }
```

**🔴 해제 가부의 권위는 account-service 다** — auth-service 는 잠금 사유를 판정하지 않는다. account-service 는 `USER_RECOVERY` 해제를
**그 계정을 실제로 잠근 전이**(`from != LOCKED`, `to == LOCKED` 인 가장 최근 이력 행)의 사유가 `AUTO_DETECT` 일 때만 허용하고, 그 밖에는
`409 STATE_TRANSITION_INVALID` 로 거부한다(상태 · 이력 불변). «가장 최근 이력 행» 이 아니라 «잠근 전이» 를 보는 이유: `ADMIN_LOCK` 으로
잠긴 뒤 자동 탐지가 한 번 더 발동하면 `LOCKED→LOCKED(AUTO_DETECT)` 멱등 행이 맨 위에 쌓인다 — 맨 위 행만 보면 운영자 잠금이 풀린다.

**응답 처리 (auth-service)** — 🔴 **fail-soft**(TASK-BE-612 AC-4): 해제는 최선 노력이고 재설정의 핵심 효과(새 비밀번호 · 세션 폐기)를 되돌리지 않는다.

| 응답 | auth-service |
|---|---|
| 200 | `info` 로그(accountId 만) |
| 409 `STATE_TRANSITION_INVALID` | 정상 — 자동 해제 대상이 아닌 잠금(`ADMIN_LOCK` 등). `info` 로그 |
| 상태 조회 404/실패 · unlock 의 그 밖의 4xx · 5xx · 타임아웃 | `warn` 로그 · 삼킴. 재설정 응답은 그대로 204 |

**이벤트**: 해제되면 account-service 가 `account.unlocked`(`reasonCode=USER_RECOVERY`, `actorType=user`, `actorId=<accountId>`)를 발행한다 —
[account-events.md](../../events/account-events.md) 에 이미 문서화된 값.

---

## GET /internal/tenants/{tenantId}/consumer-members/{accountId} — 소비자 계정 풀의 사이트 멤버십 (TASK-BE-615)

> [multi-tenancy.md § 소비자 계정 풀 § 4](../../../features/multi-tenancy.md#소비자-계정-풀--소비자-사이트끼리-계정-하나-adr-mono-078-a-task-mono-742) 의
> «멤버십 있음/없음» · «roles = 그 사이트 역할만» 을 auth-service 가 판정하는 읽기. account-service `ConsumerSiteMembershipController`.

**호출 시점 (auth-service)** — 세 곳, 모두 **풀 principal / 풀 자격이 있을 때만**(사이트별 계정의 로그인 · SSO · refresh 는 이 호출을 하지 않는다):

| 호출자 | 묻는 것 | 실패 시 |
|---|---|---|
| 폼 로그인(`CredentialAuthenticationProvider`) — 그 이메일의 풀 자격이 **있을 때만** | `consumerSite` — 이 client 테넌트에서 풀 자격을 먼저 볼까 | **fail-closed** (`AuthenticationServiceException` → `/login?error`) |
| authorize 게이트(`AuthorizeSessionTenantGate`) — 풀 세션 · 콘솔 아닌 client | `consumerSite` — 재인증 없이 통과시킬까 | 재인증(보수 쪽 — 폼이 같은 답 없이는 fail-closed 라 루프 없음) |
| 토큰 발급(`TenantClaimTokenCustomizer`, `authorization_code` · `refresh_token`) | `membershipStatus` · `siteTenantType` · `siteRoles` | **fail-closed** — 토큰 없음(`invalid_grant`). 저장 역할 조회의 fail-soft 와 다르다 |

**Path Parameters**: `tenantId` — 사이트 테넌트(slug, 범위의 첫 인자) · `accountId` — 풀 계정 id.

**Headers**: `X-Tenant-Id` 를 보내지 않는다. 보냈는데 path 와 다르면 `403 TENANT_SCOPE_DENIED`(`TenantScopeGuard`).

**Response 200 — 항상 200** («아니오» 도 본문의 답이다):
```json
{
  "accountId": "string (UUID)",
  "siteTenantId": "ecommerce",
  "consumerSite": true,
  "siteTenantType": "B2C_CONSUMER",
  "membershipStatus": "ACTIVE | LOCKED | LEFT | null",
  "siteRoles": ["SELLER"],
  "leftBy": "SELF | OPERATOR | null"
}
```

| 필드 | 뜻 |
|---|---|
| `consumerSite` | 그 테넌트가 소비자 사이트(`tenant_type = B2C_CONSUMER` ∧ ≠ `consumer-pool`, `Tenant.isConsumerSite()`)인가. 없는 테넌트 → `false` |
| `siteTenantType` | 그 테넌트의 권위 `tenant_type` — 풀 principal 토큰의 `tenant_type`. 없는 테넌트 → `null` |
| `membershipStatus` | `consumer_site_memberships` 행의 상태. 행 없음 · 풀 계정이 아님 · 소비자 사이트 아님 → `null`. **`LOCKED`** (`TASK-BE-621`) = 그 사이트 운영자가 이 사이트에서만 잠갔다 — auth-service 는 `ACTIVE` 가 아닌 다른 값과 같이 다룬다(토큰 없음 `invalid_grant`, 동의 화면 없음). 문자열 비교라 auth-service 코드 변경은 없다 |
| `siteRoles` | `consumer_site_roles(account, 그 사이트)` — **그 사이트 것만**, 이름 오름차순. `ACTIVE` 일 때만 채운다(그 밖엔 `[]`). 시드(`CUSTOMER`/`FAN`)는 저장하지 않으므로 여기 없다 — 발급이 합친다 |
| `leftBy` | **`TASK-BE-619`** — `LEFT` 멤버십을 누가 만들었나: `SELF`(본인 탈퇴) · `OPERATOR`(그 사이트 운영자가 내보냄). `LEFT` 가 아니거나 작성자 기록이 없으면 `null`. auth-service 는 **`SELF` 일 때만** authorize 에서 동의 화면을 다시 띄운다([multi-tenancy.md § 5 «사이트 탈퇴 vs 계정 삭제»](../../../features/multi-tenancy.md)) — 필드가 없는 옛 account-service 는 `null` 이라 지금처럼 토큰 거절(보수 쪽) |

- 계정 조회는 `consumer-pool` 테넌트로 한정(`findById(CONSUMER_POOL, id)`) — 테넌트 없는 조회를 새로 만들지 않는다(§ 격리 회귀 방지).
- **`iam.consumer-pool.enabled` 와 무관** — 플래그는 «새 가입이 풀로 가나» 만 정한다. 이미 있는 풀 계정은 플래그와 상관없이 로그인돼야 한다. 플래그가 꺼져 있으면 풀 계정을 만드는 경로가 없으므로 이 읽기에 도달하는 것도 없다.
- 감사 행 · 이벤트 · 변이 없음.

**auth-service 매핑 규약** (`AccountServicePort.getConsumerSiteMembership`):

| 응답 | 포트 결과 |
|---|---|
| 200 + `consumerSite`(boolean) | `ConsumerSiteMembershipLookupResult` |
| **404** | `AccountServiceUnavailableException` — 🔴 «멤버 아님» 은 200 이므로 404 는 **엔드포인트 없음**(옛 account-service)뿐이다. 그래서 실패로 읽는다 |
| 그 밖의 4xx · 5xx · 타임아웃 · circuit-open · `consumerSite` 없는 200 | `AccountServiceUnavailableException` |

🔵 **배포 순서**: 새 auth-service + 옛 account-service 이면 풀 principal 은 토큰을 못 받고(fail-closed) 풀 자격 폼 로그인은 `/login?error` 다.
사이트별 계정은 이 호출을 하지 않으므로 영향이 없다. **account-service 를 먼저 또는 함께** 올린다.

---

## PUT /internal/tenants/{tenantId}/consumer-members/{accountId} — 첫 방문 동의 (TASK-BE-616)

> [multi-tenancy.md § 소비자 계정 풀 § 4](../../../features/multi-tenancy.md#소비자-계정-풀--소비자-사이트끼리-계정-하나-adr-mono-078-a-task-mono-742) 의
> «멤버십 없음 → 그 사이트 동의 화면 한 번 → 멤버십 생성 → 토큰» 의 **쓰기**. 같은 자원(위 GET)의 PUT — account-service `ConsumerSiteMembershipController`.

**호출 시점 (auth-service)**: 동의 화면(`SiteConsentPageController`, `POST /consent`)에서 사람이 **동의**를 누른 때 한 번. 거절은 이 호출을 하지 않는다.
세션이 풀 principal 이고, authorize 게이트가 보관해 둔 그 사이트의 authorize 요청이 있을 때만.

**Path Parameters**: `tenantId` — 사이트 테넌트(범위의 첫 인자) · `accountId` — 풀 계정 id. **Request body 없음.**

**Headers**: `X-Tenant-Id` 를 보내지 않는다. 보냈는데 path 와 다르면 `403 TENANT_SCOPE_DENIED`(`TenantScopeGuard`) — 쓰기 없음.

**동작 (account-service `ConsentToConsumerSiteUseCase`)** — 한 트랜잭션:

| 상태 | 쓰기 | `account.created` |
|---|---|---|
| 소비자 사이트 ∧ ACTIVE 테넌트 ∧ 풀 계정 ∧ 그 사이트 멤버십 **행 없음** | `consumer_site_memberships(account, site, ACTIVE, consented_at = 지금)` | **1회**, `tenantId = 그 사이트`([account-events.md](../../events/account-events.md#accountcreated) «다른 사이트 첫 방문 동의» 행) |
| 이미 `ACTIVE`(재제출 · 뒤로가기 재전송) | 없음 | 없음 |
| `LEFT`, `left_by = SELF` (**`TASK-BE-619`** — 본인이 떠남) | 그 행을 **다시 `ACTIVE`** 로(`consented_at = 지금`, `left_*` 비움) — 소유자 결정 2026-10-03 «다시 동의하면 복귀» | **없음** — 그 사이트의 `account.created` 는 처음 들어갈 때 이미 한 번 나갔다(§ 6 «사이트마다 한 번») |
| `LEFT`, `left_by = OPERATOR` 또는 작성자 기록 없음 | 없음 — 운영자가 내보낸 멤버십은 동의로 **다시 열리지 않는다**(`TASK-BE-619`) | 없음 |
| `LOCKED` (**`TASK-BE-621`** — 그 사이트 운영자가 잠금) | 없음 — 잠긴 멤버십은 동의로 **열리지 않는다**(해제는 그 사이트 운영자의 unlock) | 없음 |
| 소비자 사이트 아님(B2B · 풀 테넌트 자신 · 없는 테넌트) · 정지된 사이트 · 풀 계정 아님 | 없음 | 없음 |

- **멱등 — `(accountId, site)` 당 이벤트 정확히 1회** (본인 탈퇴 후 복귀도 이벤트를 다시 내지 않는다 — `TASK-BE-619`). 동시에 두 첫 동의가 오면 PK 가 충돌하고, 진 쪽 트랜잭션(멤버십 행 + 이벤트)은 통째로 롤백된 뒤 **읽기의 답**으로 200 을 준다.
- **역할은 쓰지 않는다.** 사이트 시드 역할(`CUSTOMER`/`FAN`)은 발급 때 계산되고 저장되지 않는다 — 동의 전에도, 동의로도 역할 행이 생기지 않는다(`TASK-BE-616` Failure Scenario 1).
- **`iam.consumer-pool.enabled` 와 무관** — 위 GET 과 같은 이유(이미 있는 풀 계정은 플래그와 상관없이 다른 사이트에 들어갈 수 있어야 한다).
- 계정 조회는 `findById(CONSUMER_POOL, id)` 로만 — 테넌트 없는 조회 신설 없음.

**Response 200 — 항상 200**, 본문은 **위 GET 과 같은 문서**(쓰기 뒤의 읽기). `membershipStatus = "ACTIVE"` 이면 그 사이트를 쓸 수 있다;
그 밖의 값(`null` · `LEFT` · `LOCKED`, 또는 `consumerSite = false`)은 «쓰지 않았다» 는 답이다 — 오류가 아니다. 새 에러 코드 없음.

**auth-service 매핑 규약** (`AccountServicePort.consentToConsumerSite`) — GET 과 같다: 200 + `consumerSite` → `ConsumerSiteMembershipLookupResult`,
**404 포함** 그 밖의 모든 응답 · 타임아웃 · circuit-open · 읽을 수 없는 200 → `AccountServiceUnavailableException`. 멱등이므로 GET 과 같은 재시도 파이프라인을 탄다.

| 결과 | 동의 화면(auth-service) |
|---|---|
| `isActiveMember()` | 보관한 authorize 요청으로 302 → 게이트가 멤버로 통과 → 코드 → 그 사이트 토큰(`roles` = 그 사이트 시드 ∪ 사이트 역할) |
| 200 이지만 ACTIVE 아님 | 보관 요청 제거 → client 의 등록 redirect URI 로 `error=access_denied`(+`state`) — 토큰 없음 |
| `AccountServiceUnavailableException` | 동의 화면에 «잠시 후 다시» (503) — 보관 요청 **유지**(다시 누를 수 있다) |

🔵 **배포 순서**: 위 GET 과 같다 — 새 auth-service + 옛 account-service 이면 동의가 404 → 화면에 «잠시 후 다시», 토큰 없음(fail-closed). **account-service 를 먼저 또는 함께.**

---

## Caller Constraints (auth-service 측)

- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프 + jitter). **404는 재시도 금지** (4xx)
- Circuit breaker: 실패율 50% / 10초 → open → 30초 half-open
- account-service 장애 시 **로그인 불가** (fail-closed) — TASK-BE-600 부터 폼 로그인 · 소셜 로그인 모두 실제로 그렇다(위 매핑 규약). 폼 로그인에서는 `AuthenticationServiceException` → 오답 비밀번호와 같은 `/login?error`
