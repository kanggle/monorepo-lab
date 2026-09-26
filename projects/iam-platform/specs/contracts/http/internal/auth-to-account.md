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

## Caller Constraints (auth-service 측)

- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프 + jitter). **404는 재시도 금지** (4xx)
- Circuit breaker: 실패율 50% / 10초 → open → 30초 half-open
- account-service 장애 시 **로그인 불가** (fail-closed) — TASK-BE-600 부터 폼 로그인 · 소셜 로그인 모두 실제로 그렇다(위 매핑 규약). 폼 로그인에서는 `AuthenticationServiceException` → 오답 비밀번호와 같은 `/login?error`
