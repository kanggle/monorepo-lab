# Internal HTTP Contract: auth-service → account-service (Social Signup)

auth-service가 OAuth 소셜 로그인 처리 중 account-service를 호출하여 소셜 계정을 생성하거나 기존 계정을 조회한다.

**호출 방향**: auth-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2)
**인증** (TASK-BE-318c 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — auth-service 가 `auth-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 정적 `X-Internal-Token` 은 제거됨.

---

## POST /internal/accounts/social-signup

소셜 로그인 시 계정 자동 생성 또는 기존 계정 연결. **해당 tenant 안에서** 이메일이 이미 존재하면 기존 계정 정보를 반환하고, 존재하지 않으면 새 계정을 생성한다.

**Headers** (TASK-BE-507):

| Header | 필수 | 의미 |
|---|---|---|
| `X-Tenant-Id` | 선택 | **계정이 태어날 tenant** — auth-service 가 가입을 개시한 OIDC client 로부터 유도한 값(`SavedRequestTenantResolver`). 이 값은 auth-service 가 **이미** social-identity 행과 토큰 `tenant_id` claim 에 찍고 있던 값이며, BE-507 이전에는 이 hop 에서 버려져 **계정 행만 `fan-platform`** 이 되는 모순을 만들었다. 부재/공백/`*` → `fan-platform` (net-zero). |

**Request**:
```json
{
  "email": "string (required, provider로부터 획득한 이메일)",
  "provider": "string (required, 'GOOGLE' | 'KAKAO' | 'MICROSOFT')",
  "providerUserId": "string (required, provider의 고유 사용자 식별자)",
  "displayName": "string (optional, provider profile에서 획득한 표시 이름)"
}
```

**Response 201** (신규 계정 생성):
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE",
  "tenantId": "string (계정 행의 테넌트 — 풀 계정이면 consumer-pool)"
}
```

**Response 200** (기존 이메일 계정 존재 — 자동 연결):
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED",
  "tenantId": "string (계정 행의 테넌트 — 이 경로는 언제나 요청 tenant)"
}
```

- **`tenantId`** (`TASK-BE-617`, 추가 필드 — 기존 필드 무변경): 돌려준 계정의 `accounts.tenant_id`. auth-service 는 이 값이 `consumer-pool` 일 때만
  신원 행을 `consumer-pool` 에 쓰고 세션을 **풀 principal** 로 세운다. 그 밖의 값 · 필드 없음(이전 account-service) → 이전 그대로(요청 tenant 의 사이트 계정).
  토큰 값이 아니다 — 저장값이 토큰에 나오지 않는다는 규칙(`multi-tenancy.md` § 소비자 계정 풀 § 1)은 그대로다.

**동작 상세** — 위에서부터 처음 맞는 것:

1. tenant 검증(`ActiveTenantGuard`) — 없거나 `consumer-pool` 을 직접 지명 → 404 `TENANT_NOT_FOUND`, 정지 → 409 `TENANT_SUSPENDED`.
2. **그 tenant 안에** 같은 이메일 계정이 있다 → 그 계정 + 현재 status → **200** (자동 연결 — `TASK-BE-617` 이후에도 무변경. 사이트별 계정은 «묶이기 전까지 그대로 동작한다»).
3. **풀 가입** — `iam.consumer-pool.enabled` 가 켜져 있고 tenant 가 소비자 사이트(`B2C_CONSUMER`, 풀 제외)일 때 (`TASK-BE-617`, `multi-tenancy.md` § 소비자 계정 풀 § 2):
   1. 같은 이메일의 **풀 계정**이 있다 → **409 `ACCOUNT_ALREADY_EXISTS`**. 🔴 **이메일로 풀 계정에 연결하지 않는다**(ADR-MONO-078 D2 — IAM 은 이메일 인증을 로그인
      조건으로 쓰지 않고, 제공자가 이메일을 검증한다는 보장도 없다. 붙이면 남의 이메일을 쓰는 제공자 계정이 그 사람의 풀 계정에 비밀번호 없이 들어간다).
   2. 같은 이메일의 **사이트별 계정**이 다른 소비자 사이트에 있다 → **409 `ACCOUNT_ALREADY_EXISTS`** (§ 2 공존 금지 — 폼 가입과 같은 술어).
   3. 그 밖 → **풀 계정**(`tenant_id = consumer-pool`, 자격 없음) + 프로필 + 그 사이트 멤버십(`ACTIVE`, `consented_at` = 계정 생성 시각 — 가입 = 그 사이트 동의)
      → **201**, `tenantId = "consumer-pool"`. `account.created` 는 **그 사이트 테넌트로** 1회(§ 6). 동시 가입 경합(UNIQUE 위반)도 **409** 다 — 경합한 풀 계정을 돌려주지 않는다(같은 D2).
4. 그 밖(플래그 꺼짐 · B2B 등 소비자 사이트가 아닌 tenant) — 이전 그대로:
   1. tenant 가 소비자 사이트이고 같은 이메일의 **풀 계정**이 있다 → **409 `ACCOUNT_ALREADY_EXISTS`** (`TASK-BE-620`, 플래그와 무관).
   2. 그 밖 → 그 tenant 에 새 계정(status=ACTIVE, displayName 을 프로필에) → **201**, `account.created` 는 그 tenant 로. 경합(UNIQUE 위반) → 경합한 계정 → 200(이전 그대로).
- **credential(패스워드 해시)은 생성하지 않음** — 소셜 전용 계정은 패스워드 없이 존재 가능

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 422 | `VALIDATION_ERROR` | 필수 필드 누락 또는 형식 오류 |
| 409 | `ACCOUNT_ALREADY_EXISTS` | 위 3.1 · 3.2 · 4.1 — 그 이메일은 이미 다른 계정의 것이고, 이 경로는 **이메일로 연결하지 않는다**. (`TASK-BE-620` 은 4.1 만 있었다. `TASK-BE-617` 이 풀 가입의 3.1 · 3.2 를 더했다.) 같은 tenant 의 같은 이메일 계정은 위 «200 자동 연결» 이 먼저다(무변경) |
| 409 | `TENANT_SUSPENDED` | tenant 가 정지됨(`ActiveTenantGuard`) |

> 🔴 **caller 는 두 409 를 구별해야 한다**(`code` 로) — `ACCOUNT_ALREADY_EXISTS` 는 «그 이메일은 이미 다른 계정(비밀번호 풀 계정 등)의 것이니 그 방법으로 로그인하라», `TENANT_SUSPENDED` 는 «지금은 못 쓴다».
>
> 🔵 **caller 가 먼저 하는 것 (`TASK-BE-617`)** — auth-service 는 이 엔드포인트를 부르기 **전에** 신원 행을 찾는다: 소비자 사이트 client 면 `consumer-pool`
> 신원 행을 먼저, 없으면 그 client 테넌트의 신원 행([oauth-social-login.md § 계정 연결 전략](../../../features/oauth-social-login.md#계정-연결-전략)).
> 이 엔드포인트는 **둘 다 없을 때만** 불린다 — 그래서 위 3.1 의 «풀 계정이 이미 있다» 는 «그 풀 계정에 **이 제공자 신원이 없다**» 는 뜻이다.

**주의**: 반환된 `status`가 ACTIVE가 아닌 경우, auth-service가 로그인을 거부해야 한다 (caller 책임). account-service는 상태 검증 없이 조회 결과를 반환한다.

---

## Caller Constraints (auth-service 측)

- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프 + jitter). **4xx는 재시도 금지**
- Circuit breaker: 실패율 50% / 10초 → open → 30초 half-open
- account-service 장애 시 **소셜 로그인 불가** (fail-closed)
