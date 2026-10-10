# HTTP Contract: auth-service (Public API)

모든 엔드포인트는 gateway 경유. base path: `/api/auth`

---

## OAuth2 / OIDC Endpoints (Standard, ADR-001)

> TASK-BE-251 Phase 2c 완료. 이하 엔드포인트는 Spring Authorization Server (SAS) 1.x가 처리한다.
> gateway는 JWT 검증 없이 auth-service로 forward하며 SAS가 인증 책임을 담당한다.

### GET /.well-known/openid-configuration

OIDC Discovery 문서. RFC 8414 준거.

**Auth required**: No

**Response 200** (example):
```json
{
  "issuer": "https://iam.example.com",
  "authorization_endpoint": "https://iam.example.com/oauth2/authorize",
  "token_endpoint": "https://iam.example.com/oauth2/token",
  "jwks_uri": "https://iam.example.com/oauth2/jwks",
  "userinfo_endpoint": "https://iam.example.com/oauth2/userinfo",
  "revocation_endpoint": "https://iam.example.com/oauth2/revoke",
  "introspection_endpoint": "https://iam.example.com/oauth2/introspect",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "client_credentials", "refresh_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "none"]
}
```

---

### GET /oauth2/jwks

RSA 공개키 JWK Set. SAS 발급 토큰 검증용. (TASK-BE-398 이전에는 레거시 `POST /api/auth/login` 발급 토큰도 같은 키로 검증됐다 — 그 발급 경로는 일몰되었다.)

**Auth required**: No

**Response 200**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "key-2026-04-01",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

---

### GET /oauth2/authorize

Authorization Code + PKCE 플로우 시작. PKCE (`code_challenge_method=S256`) 필수.

**Auth required**: No (사용자 세션 없으면 login 페이지로 redirect — `prompt=create` 면 signup 페이지)

**Query Parameters**:

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `response_type` | Y | `code` 고정 |
| `client_id` | Y | 등록된 client ID |
| `redirect_uri` | Y | 사전 등록된 redirect URI와 정확히 일치 |
| `scope` | Y | 공백 구분. 예: `openid profile email` |
| `code_challenge` | Y | S256 방식으로 계산된 PKCE challenge |
| `code_challenge_method` | Y | `S256` 고정 |
| `state` | 권장 | CSRF 방어용 opaque 값 |
| `prompt` | N | 공백 구분 OIDC prompt 목록. **`create` 를 포함하면 registration hint** (TASK-BE-578, 아래) |
| `acr_values` | N | 공백 구분. **`mfa` 토큰을 포함하면 2단계 상승 요청** (TASK-MONO-771, 아래 § IdP 브라우저 화면 — 2단계 인증 § 단계 상승). 그 밖의 값은 무시한다 |

**Response**: 302 redirect to `redirect_uri?code=...&state=...`

**Errors**:

| 조건 | 응답 |
|---|---|
| PKCE 미포함 | 400 `invalid_request` |
| 미등록 client | 400 `invalid_client` |
| 미등록 redirect_uri | 400 `invalid_request` |

#### Registration hint — `prompt=create` (TASK-BE-578)

가입 의도로 온 사용자를 **미인증 상태에서 로그인 폼이 아니라 가입 폼(`/signup`)으로** 보낸다. OIDC 표준 *Initiating User Registration via OpenID Connect 1.0* 의 `prompt=create` 를 그대로 쓴다.

아래는 전부 **라이브 실측**(auth-service 컨테이너, `ecommerce-web-store-client`)이다.

| 요청 | 미인증 | 인증됨 |
|---|---|---|
| `prompt` 없음 | 302 `/login` | 302 `redirect_uri?code=…` |
| `prompt=create` | **302 `/signup`** | 302 `redirect_uri?code=…` (hint 무효) |
| `prompt=login` 등 SAS 미구현 값 | 302 `/login` | 302 `redirect_uri?code=…` |
| `prompt=created` · `prompt=Create` | 302 `/login` (hint 아님) | 302 `redirect_uri?code=…` |
| `prompt=none` / `prompt=none create` | 302 `redirect_uri?**error=login_required**` (SAS 가 처리) | 302 `redirect_uri?code=…` |

규칙:

- **파싱은 공백 구분 토큰 단위**다(OIDC Core 3.1.2.1). `created` / `recreate` 처럼 `create` 를 *포함만* 하는 값은 hint 가 아니며, 값은 대소문자를 구분한다.
- **`none` 충돌은 SAS 가 해결한다.** `prompt=none` 은 "어떤 UI 도 띄우지 말라" 는 뜻이라 가입 폼 요청과 상충하는데, **SAS 의 authorization endpoint 필터가 entry point 보다 먼저** `login_required` 로 단락시킨다. 즉 UI 를 안 띄운다는 요구가 그대로 지켜진다. `RegistrationHintRequestMatcher` 에도 `none` 이 `create` 를 이기는 규칙이 있으나 **현재는 도달하지 않는 방어선**이며, 그 규칙 자체는 단위 테스트에서만 검증된다.
- **인증된 사용자에게는 아무 효과가 없다.** 분기는 미인증 요청에만 도는 entry point 에 있다. 그래서 가입을 마치고 authorize 를 재개할 때 저장된 요청이 hint 를 그대로 달고 있어도 정상적으로 code 가 발급된다.

🔵 **표준값을 고른 근거(실측)**: SAS 는 **자기가 구현하지 않은 `prompt` 값을 거부하지 않고 무시**한다 — `prompt=bogusvalue` 도 hint 없는 요청과 동일하게 동작한다. 그래서 표준 `create` 를 실어도 SAS 검증을 건드리지 않는다. 유일하게 SAS 가 **구현하는** 값이 `none` 이고, 그 동작은 위 표에 그대로 적혀 있다.

🔴 **hint 는 tenant 를 정하지 않는다.** 새 계정의 tenant 는 계속 저장된 `/oauth2/authorize` 요청의 `client_id` 에서만 나온다(`SavedRequestTenantResolver`). hint 를 tenant 출처로 승격시키면 임의 값으로 tenant 를 고르는 구멍이 열린다 — `TASK-FE-097` 이 IAM `/signup` 직링크를 기각한 것과 같은 이유다. 어떤 클라이언트가 hint 를 보내든 tenant 는 그 클라이언트에서 나온다.

🔵 **저장된 요청은 소비되지 않는다.** `ExceptionTranslationFilter` 가 entry point 호출 **전에** 요청을 저장하고 두 분기 모두 리다이렉트만 하므로, 가입 완료 → `/login?registered` → 로그인 → 원래 authorize 재개 경로가 그대로 유지된다.

**호출 측**: `signIn(provider, options, authorizationParams)` 세 번째 인자로 얹는다. 예 — web-store `/signup`:

```ts
signIn('iam', { callbackUrl: '/' }, { prompt: 'create' });
```

---

### POST /oauth2/token

토큰 발급. `authorization_code`, `client_credentials`, `refresh_token`, **`urn:ietf:params:oauth:grant-type:token-exchange`** (assume-tenant, RFC 8693) grant 지원.

**Auth required**: `client_secret_basic` (confidential client) 또는 `none` (public PKCE client)

**Request** (form-urlencoded):

| 파라미터 | 조건 | 설명 |
|---|---|---|
| `grant_type` | Y | `authorization_code` \| `client_credentials` \| `refresh_token` \| `urn:ietf:params:oauth:grant-type:token-exchange` |
| `code` | authorization_code 전용 | authorize 단계에서 발급된 code |
| `redirect_uri` | authorization_code 전용 | authorize 시와 동일 |
| `code_verifier` | authorization_code 전용 | PKCE verifier |
| `client_id` | public client | Basic auth 미사용 시 |
| `refresh_token` | refresh_token 전용 | 기존 refresh token 값 |
| `subject_token` | token-exchange 전용 | 운영자의 base IAM OIDC **access token** (auth-service 자신이 발급한 `platform-console-web` 토큰) |
| `subject_token_type` | token-exchange 전용 | `urn:ietf:params:oauth:token-type:access_token` |
| `audience` | token-exchange 전용 | 선택된(assume 대상) customer **tenant id** |
| `scope` | client_credentials 권장 | 요청 scope |

---

#### Assume-Tenant Exchange (RFC 8693 — TASK-BE-327 / ADR-MONO-020 § 3.3 step 2, D2+D3)

`grant_type=urn:ietf:params:oauth:grant-type:token-exchange` 로 운영자의 base IAM OIDC 세션을, **선택된 customer tenant** 로 scope 된 **단명(short-lived) domain-facing IAM OIDC access token** 으로 교환한다 (AWS STS AssumeRole 유사). 발급된 토큰은 login 토큰과 **동일한 `iss`/JWKS/kid** 를 가지므로 federated 도메인 게이트(ADR-019 D5)와 BFF(ADR-017 D6)가 변경 없이 수용한다.

- **subject_token**: auth-service 자신이 발급한 base IAM OIDC access token. auth-service 의 자기 `JwtDecoder`(자신이 서명한 동일 JWKS)로 검증한다. `sub`(account_id) + base `tenant_id` 추출. 검증 실패(만료/무효 서명/issuer 불일치 등) → `invalid_grant`.
- **선택된 tenant**: RFC 8693 **`audience`** 파라미터로 운반한다 (`resource` 는 사용하지 않음).
- **assignment 게이트 (fail-CLOSED)**: admin-service `GET /internal/operator-assignments/check?oidcSubject=<sub>&tenantId=<audience>` 가 `assigned=true` 를 반환할 때만 발급한다. 미할당 / 알 수 없는 subject / 비-ACTIVE 운영자 / **admin-service 장애·circuit-open·timeout** 모두 → **토큰 미발급**, `invalid_grant`. ([auth-to-admin.md](./internal/auth-to-admin.md) — fail-closed)
- **2단계 게이트 (fail-CLOSED, TASK-MONO-771 · ADR-MONO-080 D4)**: 같은 응답의 `mfaRequired` 가 `true`(또는 **필드 부재** — `true` 로 읽는다)이고 subject_token 의 `amr` 에 `mfa` 가 없으면(`amr` 부재 포함) **토큰 미발급** — `400 invalid_grant` + **`error_description=insufficient_user_authentication`**(고정 상수, 아래 Errors). 판정 술어는 `"mfa" ∈ amr` 하나다([jwt-standard-claims.md](../../../../../platform/contracts/jwt-standard-claims.md) `amr` 행). 요구 여부(`mfaRequired`)는 admin-service 가 계산하고, 비교는 발급자인 auth-service 가 한다 — 정책은 «들어가는 테넌트» 의 것이라 assignment · 플랫폼 `'*'` · 파트너십 host reach 어느 길이든 같다.
- **`entitled_domains` (fail-SOFT, least-privilege)**: 선택된 tenant 의 ACTIVE subscriptions **만** (다른 assignment 와의 union 없음 — D3). account-service 장애 시 claim 을 **생략**하고 토큰은 발급한다 (fail-soft; 도메인은 `tenant_id` 게이트로 fallback). keystone `populateEntitledDomains` 재사용.

**Request 예** (form-urlencoded):
```
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token=<base IAM OIDC access token>
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=acme-corp
&client_id=platform-console-web
```

**Response 200**:
```json
{
  "access_token": "string (JWT, short-lived)",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "expires_in": 1800
}
```
> **`refresh_token` 없음** — assumed 토큰은 단명이며 selection 마다 재발급된다 (ADR-020 § 3.1). 이 grant 는 refresh token 을 발급/저장하지 않는다.

**Assumed Token Claims**:

| Claim | 설명 |
|---|---|
| `sub` | account_id (subject_token 의 `sub` 와 동일) |
| `iss` / kid | login 토큰과 동일 (`oidc.issuer-url` / `auth.jwt.kid`) |
| `tenant_id` | **선택된** customer tenant (`audience`) — `'*'` 이 아님 |
| `tenant_type` | 선택된 customer tenant 의 type (`B2B_ENTERPRISE`) |
| `entitled_domains` | 선택된 tenant 의 ACTIVE subscriptions **만** (fail-soft 시 생략) |
| `email` | **없음.** 아래 § Scope ↔ Claim 참조 — 이 grant 는 `email` 을 싣지 않는다 |
| `amr` | subject_token 의 `amr` 을 **그대로 복사**(TASK-MONO-771). subject 에 없으면 생략. 판정은 발급 **전에** 끝났다(위 2단계 게이트) — 이 복사는 하류 가시성용이다. **workload assume(`client_credentials` subject)에는 싣지 않는다** |

**Assume-Tenant Errors**:

| Status | 에러 코드 | `error_description` | 조건 |
|---|---|---|---|
| 400 | `invalid_grant` | (자유 문구) | subject_token 무효/만료, **assignment 미할당**, **admin-service 장애/circuit-open/timeout** |
| 400 | `invalid_grant` | **`insufficient_user_authentication`** (고정 상수) | **2단계 필요** — 위 2단계 게이트: `mfaRequired` 이고 subject `amr` 에 `mfa` 없음 (TASK-MONO-771) |
| 400 | `invalid_request` | (자유 문구) | `audience` 누락/malformed, `subject_token`/`subject_token_type` 누락 |

🔴 **판별자는 `error_description` 의 값 전체 일치다**(TASK-MONO-771 HS-C). RFC 6749 § 5.2 가 토큰 엔드포인트의 `error` 를 닫힌 목록으로 두므로 `error` 는 `invalid_grant` 그대로 두고, «2단계 필요» 는 고정 상수 `insufficient_user_authentication`(RFC 9470 의 어휘)으로 가른다 — 기존 `TOKEN_TENANT_MISMATCH` 와 같은 방식. **다른 어떤 `invalid_grant` 도 이 값을 `error_description` 으로 쓰지 않는다**(쓰면 콘솔이 미할당을 단계 상승으로 오독한다). 부분 일치 · 대소문자 무시 매칭 금지. 이 상수를 받은 클라이언트는 미할당이 아니라 **할당은 됐고 2단계가 모자란** 것으로 읽는다 — 운영자는 이미 «할당됨» 판정을 통과했으므로 이 구분이 운영자 존재를 새로 드러내지 않는다(subject_token 의 주인에게만 돌아간다).

**Response 200**:
```json
{
  "access_token": "string (JWT)",
  "token_type": "Bearer",
  "expires_in": 1800,
  "refresh_token": "string (authorization_code / refresh_token grant 전용)",
  "scope": "string",
  "id_token": "string (scope=openid 포함 시)"
}
```

> 🔴 **`id_token` 은 `refresh_token` 그랜트에도 실린다** (TASK-MONO-705, 2026-09-18).
> 조건은 grant 종류가 아니라 **authorization 의 scope 에 `openid` 가 있는가** 하나다. 위 줄은
> 원래 그렇게 읽히도록 쓴 것인데, 커스텀 `SasRefreshTokenAuthenticationProvider` 가
> **refresh 응답에서만 이 필드를 비우고 있었다** — 계약이 맞았고 구현이 갈라져 있었다. 그 결과
> 콘솔의 `console_id_token` 쿠키가 로그인 30분 뒤 사라졌고, 그 뒤의 로그아웃은 `id_token_hint`
> 없이 **로컬 폴백**으로 떨어져 **IdP 세션을 끝내지 못했다**(그 상태에서 «로그인» 을 누르면
> 비밀번호 없이 재입장한다 — 2026-09-18 데모 창 대조군 실측).
> 🔵 **회전된다**: refresh 마다 새 `id_token` 이 발급되고 authorization 에 저장되므로 **직전
> `id_token` 은 더 이상 유효한 `id_token_hint` 가 아니다.** RP 는 매 갱신 응답의 값으로 갈아 끼워라.
> 🔵 `nonce` 는 **우리가 넣지 않는다** — refresh 경로는 원래 인가 요청의 nonce 를 다시 싣지 않는다
> (OIDC Core 12.2 가 그렇게 말한다). 🔴 이 줄은 **명세와 우리 코드 기준**이고 응답 본문을 떠서 확인한
> 값이 아니다 — 필요하면 토큰 엔드포인트 응답을 직접 읽어 확인해라.

**Token Claims** (access token + id token 공통):

| Claim | 설명 |
|---|---|
| `sub` | account_id (UUID) |
| `iss` | OIDC issuer URL (`oidc.issuer-url`) |
| `iat` | 발급 시각 (epoch seconds) |
| `exp` | 만료 시각 |
| `tenant_id` | 테넌트 slug (필수 — 누락 시 발급 거부) |
| `tenant_type` | `B2C_CONSUMER` \| `B2B_ENTERPRISE` (필수) |
| `email` | 계정 이메일 — **`email` scope 가 승인된 경우에만** (§ Scope ↔ Claim) |
| `amr` | 로그인 수단 (RFC 8176, TASK-MONO-771). **모든 로그인에 싣는다**(소유자 결정 OD-7): 비밀번호 `["pwd"]` · 비밀번호+인증 앱 `["pwd","otp","mfa"]` · 비밀번호+복구 코드 `["pwd","mfa"]` · 소셜 `[]` · 소셜+인증 앱 `["otp","mfa"]`. `refresh_token` 그랜트는 **로그인 때 값을 그대로** 싣는다. 정본: [jwt-standard-claims.md](../../../../../platform/contracts/jwt-standard-claims.md) `amr` 행 |

#### Scope ↔ Claim (TASK-BE-577)

무엇을 요청하면 무엇이 실리는지는 지금까지 어디에도 적혀 있지 않았다. 그 결과
`email` scope 를 선언한 클라이언트 6개가 전부 그 scope 를 승인받고도 **클레임 없는
토큰**을 받아 왔다 — 게이트웨이의 `X-User-Email` 주입도, 소비자 프로필의 이메일도
그래서 영구히 비어 있었다(실측, TASK-BE-575 → BE-577).

| Scope | 실리는 claim | 어디에 |
|---|---|---|
| `openid` | `sub`, `iss`, `iat`, `exp` (표준) | access token + id token |
| `email` | **`email`** (계정 이메일) | access token + id token |
| `profile` | **없음** — 아래 참조 | — |
| (scope 무관) | `tenant_id`, `tenant_type`, `roles`, `entitled_domains`, `amr` (TASK-MONO-771) | access token + id token |

- **`email` 은 scope 로 게이트된다.** scope 없이 발급된 토큰에는 클레임이 없다. 동의가
  이 채널을 PII 의 정당한 경로로 만드는 근거이므로(ADR-MONO-037 P1), 무조건 실으면
  동의만 사라지고 PII 만 남는다.
- **값이 없으면 클레임을 생략한다 — 빈 문자열로 싣지 않는다.** 소비 측이
  `skipIfNull` 로 헤더를 매핑하므로, 빈 값은 "헤더 있음 + 값 없음" 으로 전달돼 빈
  이메일을 가진 프로필을 만든다(생략보다 나쁘다).
- **`profile` scope 는 아직 아무 클레임도 싣지 않는다.** auth-service 의 자격증명
  저장소에 표시 이름 컬럼이 **없고**(`credentials` 는 email 까지다), 이름을 읽는
  소비자도 없다(어느 게이트웨이도 `X-User-Name` 을 매핑하지 않는다). 소스도 소비자도
  없는 PII 클레임을 미리 싣지 않는다 — 필요해지면 그때 결정한다.
- **assume-tenant(token-exchange) 토큰에는 싣지 않는다.** 그 토큰이 답하는 질문은
  "이 운영자가 어느 테넌트로 행위하는가" 이고, base 토큰이 이미 이메일을 갖고 있으며,
  assumed 토큰의 이메일을 읽는 소비자는 없다.
- **`client_credentials` 에는 구조적으로 실리지 않는다** — 워크로드는 신원이 아니다.

**Errors**:

| Status | 에러 코드 | 조건 |
|---|---|---|
| 400 | `invalid_grant` | code 만료/재사용, refresh token 재사용(reuse detection), **refresh token 의 `refresh_tokens` 미러 행이 폐기·만료됨**(비밀번호 재설정 · 재사용 탐지의 계정 전체 폐기 · 강제 로그아웃), **`TOKEN_TENANT_MISMATCH`**(`error_description` — 미러 행 테넌트 ≠ 세션의 로그인 시점 테넌트, 아래 주석), assume-tenant subject_token 무효 / assignment 미할당 / admin-service 장애 / **2단계 필요(`error_description=insufficient_user_authentication`, TASK-MONO-771)** (위 Assume-Tenant Exchange 참조), **풀 principal 의 콘솔 토큰 거절(TASK-MONO-772)** — 운영자 측면 없음 = 기존 `consumer-pool` 문구 그대로 · 측면 판정 실패 = **`error_description=operator_eligibility_unavailable`**(고정 상수, 아래 § 풀 계정의 콘솔 토큰) |
| 400 | `invalid_request` | PKCE 미포함, assume-tenant `audience` 누락/malformed |
| 401 | `invalid_client` | client 인증 실패 |
| 401 | `unauthorized_client` | 해당 grant_type 미허용 client |

🔴 **`refresh_token` grant 의 도메인 거부는 최종이다 (TASK-BE-604, 2026-09-26).** 위 `invalid_grant` 중 미러 행 폐기·만료 ·
`TOKEN_TENANT_MISMATCH` 는 `SasRefreshTokenAuthenticationProvider` 가 내리는데, TASK-BE-604 이전에는 SAS 기본
`OAuth2RefreshTokenAuthenticationProvider` 가 그 뒤에 남아 있어 **같은 요청을 다시 처리해 200 으로 발급**했다(SAS 인가만 봄 —
TASK-BE-603 CORRECTION). 기본 provider 는 제거됐다(`AuthorizationServerConfig#removeBuiltInRefreshTokenProvider` — 토큰
엔드포인트에 `refresh_token` provider 가 정확히 하나가 아니면 기동 실패).
테넌트 비교의 기준은 **client 의 테넌트가 아니라 세션의 로그인 시점 테넌트**(resource-owner principal details `tenant_id`
= 토큰의 `tenant_id` claim)다 — 교차 테넌트 로그인 세션(예: 소비자 테넌트 자격으로 콘솔에 들어온 ADR-MONO-044 D5 운영자)은
계속 갱신된다. 규칙 원문: [multi-tenancy.md § Refresh Token](../../features/multi-tenancy.md#refresh-token).

---

### GET /oauth2/userinfo

OIDC UserInfo 응답. `scope=openid` 포함 access token 필요.

**Auth required**: Yes (Bearer access token)

**Response 200**:
```json
{
  "sub": "account-uuid",
  "email": "user@example.com",
  "email_verified": true,
  "name": "홍길동",
  "preferred_username": "honggd",
  "locale": "ko-KR",
  "tenant_id": "fan-platform",
  "tenant_type": "B2C_CONSUMER"
}
```

**Errors**:

| Status | 조건 |
|---|---|
| 401 | Bearer token 없음 또는 만료 |
| 403 | scope=openid 미포함 |

---

### POST /oauth2/revoke

토큰 폐기 (RFC 7009). access_token, refresh_token 모두 revocation 가능.
revoke 시 `OAuth2AuthorizationService.remove()` → `DomainSyncOAuth2AuthorizationService`가 JPA `RefreshTokenRepository`도 동기화.

**Auth required**: client_secret_basic (confidential client) 또는 client_id (public client)

**Request** (form-urlencoded):

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `token` | Y | 폐기할 token 값 |
| `token_type_hint` | N | `access_token` \| `refresh_token` (선택적 힌트) |

**Response 200**: (RFC 7009 § 2.2 — 서버는 token 존재 여부와 무관하게 200 반환)

**Errors**:

| Status | 조건 |
|---|---|
| 401 | client 인증 실패 |

**Side Effect**:
- revoked token은 `/oauth2/introspect` 에서 `active=false` 반환

---

### POST /oauth2/introspect

토큰 검사 (RFC 7662). active 여부 + 표준 claim + 테넌트 extension claim 반환.

**Auth required**: client_secret_basic (confidential client만 허용 — public client는 introspect 불가)

**Request** (form-urlencoded):

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `token` | Y | 검사할 token 값 |
| `token_type_hint` | N | `access_token` \| `refresh_token` |

**Response 200**:
```json
{
  "active": true,
  "client_id": "string",
  "username": "string (sub)",
  "scope": "string",
  "exp": 1234567890,
  "iat": 1234566090,
  "nbf": 1234566090,
  "sub": "account-uuid",
  "aud": ["string"],
  "iss": "https://iam.example.com",
  "tenant_id": "fan-platform",
  "tenant_type": "B2C_CONSUMER"
}
```

비활성(revoked/expired/unknown) 토큰:
```json
{ "active": false }
```

| 필드 | 설명 |
|---|---|
| `active` | 토큰이 유효·활성 상태이면 `true` |
| `tenant_id` | RFC 7662 extension — multi-tenant 식별 (`TenantIntrospectionCustomizer`) |
| `tenant_type` | RFC 7662 extension — `B2C_CONSUMER` \| `B2B_ENTERPRISE` |

**Errors**:

| Status | 조건 |
|---|---|
| 401 | client 인증 실패 |

---

## OAuth2 Clients

Registered OAuth 2.0 clients. Seeded via Flyway migrations. Managed via admin-service OAuth client API (TASK-BE-258, pending).

| Client ID | Tenant | Grant Types | PKCE | Redirect URIs | Scopes | Flyway Version |
|---|---|---|---|---|---|---|
| `community-service-client` | `fan-platform` | `client_credentials` | No | — | `account.read`, `membership.read`, `artist.read` | V0009 (+V0032 `artist.read`) |
| `artist-service-client` | `fan-platform` | `client_credentials`, `urn:ietf:params:oauth:grant-type:token-exchange` | No | — | `store.seller.read` (machine-only; assume-tenant `ecommerce` only — `WorkloadTenantCatalog`; no roles) | V0042 (TASK-MONO-759) |
| `wms-user-flow-client` | `wms` | `authorization_code`, `refresh_token` | Yes | `http://localhost:9001/callback` | `openid`, `profile`, `email`, `offline_access`, `wms.*` | V0010 |
| `wms-internal-services-client` | `wms` | `client_credentials` | No | — | `wms.*` scopes | V0010 |
| `fan-platform-user-flow-client` | `fan-platform` | `authorization_code`, `refresh_token` | Yes (required) | `http://localhost:3000/api/auth/callback/iam`, `http://localhost:3002/api/auth/callback/iam`, `http://fan-platform.local/api/auth/callback/iam`, `http://web.fan-platform.local/api/auth/callback/iam` | `openid`, `profile`, `email`, `tenant.read`, `offline_access`, `fan-platform.community.read`, `fan-platform.community.write`, `fan-platform.artist.read` | V0011 (+V0028 `:3002`, +V0031 `web.`) |
| `ecommerce-web-store-client` | `ecommerce` | `authorization_code`, `refresh_token` | Yes (required) | `http://localhost:3000/api/auth/callback/iam`, `http://web.ecommerce.local/api/auth/callback/iam` | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.consumer` | V0012 |
| `ecommerce-admin-dashboard-client` | `ecommerce` | `authorization_code`, `refresh_token` | Yes (required) | `http://localhost:3001/api/auth/callback/iam`, `http://admin.ecommerce.local/api/auth/callback/iam` | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.operator` | V0012 |
| `scm-platform-internal-services-client` | `scm` | `client_credentials` | No | — | `scm.read`, `scm.write` | V0013 |
| `finance-platform-internal-services-client` | `finance` | `client_credentials` | No | — | `finance.read`, `finance.write` | V0017 |
| `erp-platform-internal-services-client` | `erp` | `client_credentials` | No | — | `erp.read`, `erp.write` | V0018 |

> `fan-platform-internal-services-client` (client_credentials) is deferred to v2 — see TASK-MONO-026 Out of Scope.
>
> `scm-platform-user-flow-client` (PKCE) is deferred to scm v2 when frontend is introduced — see TASK-MONO-042 Out of Scope. v1 is backend-only.
>
> `finance-platform-user-flow-client` (PKCE) is not issued — finance v1 is backend-only and its UI is rendered by the unified platform console (ADR-MONO-013 §3.3, ADR-MONO-008). See TASK-MONO-114.
>
> `erp-platform-user-flow-client` (PKCE) is not issued — erp v1 is backend-only (internal-system domain) and its UI is rendered by the unified platform console (ADR-MONO-013 §3.3, ADR-MONO-016). See TASK-MONO-119.

---

## ~~POST /api/auth/login~~ — REMOVED 2026-08-01 (TASK-BE-398)

> **제거됨.** ADR-001 D2-b 가 정한 90일 deprecation window(2026-05-01 deprecated →
> 2026-08-01 removal)가 만료되어 `LoginController` 와 함께 삭제되었다. 이 경로는 더 이상
> 라우팅되지 않으며 gateway `public-paths` 에서도 제거되었다 — 인증 없이 호출하면 엣지에서
> `401 TOKEN_INVALID` 로 거절된다.
>
> **대체**: 표준 OIDC. 브라우저는 `GET /oauth2/authorize` (Authorization Code + PKCE) →
> `/login` HTML 폼 → `POST /oauth2/token`, 서비스 간 호출은 `POST /oauth2/token`
> (`grant_type=client_credentials`).
>
> **함께 제거된 것**:
> - `OAuthLoginTransactionalStep` (커스텀 JWT / device-session / refresh 발급 꼬리)
> - `LoginRequest` / `LoginResponse` DTO
> - `DeprecatedApiHeaderFilter` — 이 경로에 RFC 8594 `Deprecation` / RFC 9745 `Sunset`
>   헤더를 붙이던 필터. 엔드포인트가 사라졌으므로 신호도 사라진다.
> - gateway `gateway.jwt.allowed-issuers` 기본값의 후행 `,iam` — 이 경로가 `iss=iam`
>   커스텀 JWT 를 발급하던 **유일한** 경로였다 (TASK-MONO-365 가 미리 allowlist 로 바꿔둔
>   덕분에 엣지가 죽지 않았다).
>
> **남은 것 (별도 판단, 이 task 범위 밖)**: `POST /api/auth/refresh` 와
> `POST /api/auth/logout` 은 유지된다 — deprecation 이 고지된 적이 없어 무고지 제거가 되고,
> [`specs/services/auth-service/architecture.md`](../../services/auth-service/architecture.md)
> 도 "유지(status 미정)" 로 선언한다. 다만 발급 경로가 사라져 신규 커스텀 refresh token 은
> 더 이상 생기지 않으므로, 두 엔드포인트의 일몰은 후속 task 로 분리한다.
>
> **`LoginUseCase` 는 코드에 남아 있다** — HTTP 진입점만 사라졌고, 자격증명 검증 / 로그인
> 실패 카운터 / `auth.login.*` 이벤트 / device-session 등록 로직은 그대로다. 이를
> 폼-로그인 경로(`CredentialAuthenticationProvider`)로 승격할지 폐기할지는 위 후속 판단과
> 함께 결정한다.
>
> **TASK-BE-599 갱신** — 그중 `auth.login.*` 이벤트만 폼-로그인 경로로 올라갔다
> (`LoginEventRecorder`, 발행 조건은 [auth-events.md](../events/auth-events.md) § «로그인 이벤트의
> 발행 경로»). **로그인 실패 카운터(rate-limit)는 올리지 않았다** — 소유자 결정(AC-0 ⓑ 기각):
> 방문자가 공유하는 데모 계정이 N회 실패 뒤 막히면 안 된다. 따라서 `POST /login` 은 실패 횟수와
> 무관하게 같은 `/login?error` 로 응답한다. **device-session 등록 · `auth.session.created` 도
> 올리지 않았다**(AC-0 ⓒ 철회 — 브라우저 폼에 기기 fingerprint 가 없다). `LoginUseCase` 자체는
> 여전히 호출자 없이 남아 있다.
>
> 원본 스펙 본문(요청/응답/에러 표)은 git history 에 보존된다.

---

## POST /login — HTML 폼 로그인의 계정 상태 규칙 (TASK-BE-600)

`POST /login`(`application/x-www-form-urlencoded`: `username` · `password` · CSRF)은 SAS 브라우저 플로우의
유일한 비밀번호 로그인이다(`CredentialAuthenticationProvider`, TASK-BE-309). 성공 → 저장된 `/oauth2/authorize`
로 302, 실패 → **항상** 302 `/login?error`(화면 문구 «Invalid email or password.»).

**계정 상태 규칙** — 비밀번호가 맞아도 계정 상태가 `ACTIVE` 가 아니면(`LOCKED` · `DORMANT` · `DELETED` · 그 밖의 값)
로그인은 거부된다. 규칙은 소셜 로그인과 **같은 하나**다(`application/AccountStatusRule` — 두 경로가 다른 규칙을 들면
지금 고친 결함이 그대로 되살아난다: BE-398 이후 폼 경로에는 상태 검사가 아예 없어서, 같은 잠긴 계정이 소셜로는 막히고
비밀번호로는 들어갔다).

| 경우 | 응답 | `auth.login.failed.failureReason` |
|---|---|---|
| 없는 이메일 | 302 `/login?error` | `CREDENTIALS_INVALID` (`accountId=null`) |
| 소비자 client 로 시작했고 그 client 테넌트에 자격이 없음 — 다른 테넌트에는 있어도 (TASK-BE-604) | 302 `/login?error` — **없는 이메일과 같다** | `CREDENTIALS_INVALID` (`accountId=null`, `tenantId` = client 테넌트) |
| 비밀번호 불일치 (상태 무관 — 잠긴 계정 포함) | 302 `/login?error` | `CREDENTIALS_INVALID` |
| 비밀번호 일치 + `LOCKED` / `DORMANT` / `DELETED` | 302 `/login?error` — **위 두 줄과 바이트 단위로 같다** | `ACCOUNT_LOCKED` / `ACCOUNT_DORMANT` / `ACCOUNT_DELETED` |
| 비밀번호 일치 + 계약 밖 상태 값 | 302 `/login?error` | 발행 안 함 (enum 에 없는 값을 지어내지 않는다 — `attempted` 만 남는다) |
| account-service 상태 조회 **실패** | 302 `/login?error` (**fail-closed**) | 발행 안 함 (`attempted` 만 — tenant_type 조회 장애와 같은 모양) |
| 상태 조회 404 (계정 레코드 없음 — 콘솔 운영자) | 규칙 미적용 → 비밀번호대로 | — |

**어느 자격을 찾나** — 시작 client(저장된 `/oauth2/authorize` 의 `client_id`)의 테넌트로 먼저 찾고, 없을 때 다른 테넌트로
넘어가는 것은 **콘솔 client(테넌트 `iam`)일 때만**이다. 규칙 원문과 근거: [multi-tenancy.md § 로그인 가능한 계정과 client](../../features/multi-tenancy.md#로그인-가능한-계정과-client-task-be-604).

🔴 **응답 모양 — 소유자 결정 (2026-09-25, AC-1 ⓐ)**: 상태로 거부된 로그인은 **오답 비밀번호와 정확히 같은 결과**다 —
같은 예외(`BadCredentialsException("Invalid credentials")`), 같은 `/login?error`, 같은 문구, 힌트 없음. «잠겼습니다» 를
비밀번호가 맞을 때만 보여 주면 잠긴 계정이 **비밀번호 정답 확인기**가 된다. 옛 JSON 경로의 423 `ACCOUNT_LOCKED` 는
가져오지 않는다(그건 API 였고 이건 폼이다).

🔴 **순서 — 타이밍도 새지 않게**: 자격을 찾으면 (1) 계정 상태를 **먼저** 조회하고 (2) 비밀번호를 상태와 **무관하게 항상**
검증한 뒤 (3) 비밀번호 → 상태 순으로 판정한다. 그래서 찾은 자격은 상태 · 비밀번호 정답 여부와 무관하게 같은 두 비용(상태
조회 1회 + 해시 검증 1회)을 치른다. 비밀번호 검증 **뒤에** 조회하면 조회 왕복이 «비밀번호가 맞았을 때만» 붙어 타이밍
오라클이 되고, 상태로 **먼저 끊으면** 비활성 계정만 해시를 건너뛰어 상태 오라클이 된다. (없는 이메일은 두 비용을 모두
치르지 않는다 — 계정 존재의 타이밍 차이는 BE-600 이전부터 있던 것으로 이 결정의 범위 밖이다.)

🔴 **조회 실패 — 소유자 결정 (2026-09-25, AC-2)**: **fail-closed**. account-service 가 5xx · 타임아웃 · circuit-open ·
404 가 아닌 4xx · 읽을 수 없는 200 을 주면 로그인은 거부된다(`AuthenticationServiceException` → 같은 `/login?error`).
조용한 fail-open 이면 결함이 장애 때마다 되살아난다. 조회는 **자격 행의 테넌트**로 한다(`X-Tenant-Id`,
[auth-to-account.md](internal/auth-to-account.md) § `GET /internal/accounts/{accountId}/status`).

🔵 **이미 받은 세션**: 이 규칙은 **새 로그인**만 막는다. 발급된 access token 은 만료까지 유효하고, `refresh_token` grant
(`SasRefreshTokenAuthenticationProvider`)는 아직 계정 상태를 보지 않는다 — TASK-BE-600 AC-0 ① 에서 후속으로 분리됐다.

---

## IdP 브라우저 화면 — 이메일 인증 (TASK-MONO-770 · ADR-MONO-080 D3)

메일을 **보내는** 화면과 메일의 링크가 **도착하는** 화면, 둘이다. 둘 다 `/login` · `/signup` · `/consent` 와 같은
`@Order(0)` 폼 체인에 있고(같은 세션 · CSRF 켜짐 · permitAll — 판정은 컨트롤러가 한다), account-service 의 JSON
엔드포인트를 **서버 측에서** 부른다(`/signup` 프록시와 같은 이유: IdP 화면과 `/api/accounts` 는 다른 오리진이고
account-service 는 CORS 를 두지 않는다). 데모 엣지(Traefik `iam-oidc` 라우터)는 두 경로를 PathPrefix 로 덮는다
(가드 (p) 가 템플릿 링크와 대조한다).

### GET · POST /email-verification — 인증 메일 보내기

| 상황 | 화면 |
|---|---|
| IdP 세션이 없다(로그인 안 함) | «로그인 세션이 없습니다» — 스토어·팬에 로그인한 브라우저로 다시 열라고 안내. account-service 를 부르지 않는다 |
| 세션 있음 · GET | 마스킹한 주소 + «인증 메일 보내기» 버튼 |
| POST → account-service `POST /api/accounts/signup/resend-verification-email` (`X-Account-Id` = 세션의 account id, `X-Tenant-Id` = 세션의 테넌트) | 아래 표 |

| account-service 응답 | 화면 문구(요지) | 재시도 버튼 |
|---|---|---|
| 204 | «인증 메일을 보냈습니다 — 메일함의 링크를 여세요(24시간 유효)» | — |
| 409 `EMAIL_ALREADY_VERIFIED` | «이미 인증된 이메일입니다» | — |
| 429 `RATE_LIMITED` | «방금 보냈습니다 — 5분 뒤 다시» | — |
| 503 `VERIFICATION_EMAIL_SEND_FAILED` | 🔴 **«메일을 보내지 못했습니다 — 잠시 뒤 다시 시도하세요»** (ADR-080 § 새로 생기는 위험: «권한을 못 받음» 이 아니라) | 있음 |
| 422 `VERIFICATION_EMAIL_UNDELIVERABLE` | «이 주소로는 메일을 보낼 수 없습니다» — 재시도 권하지 않음 | — |
| 404 `ACCOUNT_NOT_FOUND` | «이 계정은 이메일 인증 대상이 아닙니다»(예: 콘솔 운영자 세션) | — |
| 그 밖(5xx · 연결 실패 · 읽을 수 없는 본문) | «메일을 보내지 못했습니다 — 잠시 뒤 다시» (판정 불가 = 일시) | 있음 |

### GET · POST /verify-email — 메일의 링크가 도착하는 곳

- `GET /verify-email?token=…` 은 **아무것도 바꾸지 않는다** — «이메일 인증 완료» 버튼 하나를 그린다. 메일 보안 스캐너·
  미리보기가 링크를 먼저 GET 하므로, GET 이 인증을 끝내면 사람이 열기도 전에 토큰이 소비된다.
- `POST /verify-email`(`token` · CSRF) → account-service `POST /api/accounts/signup/verify-email`(공개 — 토큰이 인증).
  세션은 필요 없다(링크는 다른 브라우저·기기에서 열릴 수 있다).

| account-service 응답 | 화면 |
|---|---|
| 200 | «이메일이 인증되었습니다» |
| 400 `TOKEN_EXPIRED_OR_INVALID` · `VALIDATION_ERROR` | «링크가 만료되었거나 이미 사용되었습니다» + «인증 메일 다시 받기»(`/email-verification`) 링크 |
| 409 `EMAIL_ALREADY_VERIFIED` | «이미 인증된 이메일입니다» |
| 그 밖 | «지금은 확인할 수 없습니다 — 잠시 뒤 다시» (토큰은 소비되지 않았다 — 같은 링크로 다시 된다) |

🔴 R4: 화면·로그 어디에도 토큰을 쓰지 않는다(폼의 hidden 필드만 예외 — 그 페이지의 주인에게 돌려주는 것). 주소는 마스킹한다.

---

## IdP 브라우저 화면 — 2단계 인증 (TOTP) (TASK-MONO-771 · ADR-MONO-080 D4 · R3)

계정 평면의 2단계 인증. 수단은 **TOTP**(RFC 6238 · SHA1 · 6자리 · 30초) + **1회용 복구 코드 10개**. 저장은 auth-service
[`account_totp`](../../services/auth-service/data-model.md#account_totp) — **`account_id` 키**(풀 이동 뒤에도 등록이 산다). 화면은
`/login` · `/signup` · `/email-verification` 과 같은 `@Order(0)` 폼 체인(같은 세션 · CSRF 켜짐 · permitAll — 판정은 컨트롤러가 한다)이고,
데모 엣지(Traefik `iam-oidc` 라우터)는 `PathPrefix(\`/mfa\`)` 하나로 덮는다.

🔵 admin-service 의 break-glass TOTP(`admin_operator_totp`, `POST /api/admin/auth/login`)와 **다른 것**이다 — 주 경로 = 이 계정 TOTP,
비상 경로 = break-glass TOTP. 둘은 비밀 · 저장소 · 검증 위치가 분리되고 계산 코드만 공유한다(이유: break-glass 는 IdP 장애 때 쓰는 길이라
IdP 에 묶이면 함께 죽는다 — [admin-service/security.md § Operator Credential Convergence](../../services/admin-service/security.md#operator-credential-convergence-task-be-377--adr-mono-035--o2--step-4c)).

### 로그인 흐름의 2단계 — 폼 · 소셜 공통

1단계(폼 `POST /login` 성공 · 소셜 콜백 성공) 뒤, 계정에 **확정된** 등록(`account_totp.confirmed_at IS NOT NULL`)이 있으면 302 `/mfa/challenge`.

- 🔴 **판정 지점은 두 생산자 뒤 공통 한 곳이다.** 폼에만 두면 같은 계정이 소셜 로그인으로 2단계 없이 들어온다(옆문). 등록된 계정은 어느 1단계로
  들어와도 같은 `/mfa/challenge` 를 거친다.
- 2단계를 통과하기 전의 세션은 **«1단계만 통과»** 다 — `/oauth2/authorize` 는 code 를 내지 않고 `/mfa/challenge` 로 되돌린다. 저장된
  `/oauth2/authorize` 요청은 소비되지 않는다(통과하면 그대로 재개).
- **등록이 없는 계정은 지금과 같다**(티켓 AC-3) — 같은 화면, 같은 리다이렉트. 바뀌는 것은 토큰에 `amr`(`["pwd"]` 또는 `[]`)이 하나 붙는 것뿐이다.
- 계정 상태 규칙(§ POST /login — TASK-BE-600)은 1단계에서 이미 판정됐다. 2단계는 그 판정을 다시 하지 않는다.

### GET · POST /mfa/challenge — 두 번째 단계

| 상황 | 화면 / 결과 |
|---|---|
| 세션에 1단계 통과 기록이 없다 | 302 `/login` |
| GET | 6자리 코드 입력 폼 + «복구 코드로 하기» 입력(같은 폼의 다른 필드 — submit 버튼 1개) + «취소» |
| POST `code` 일치(±1 step) **이고** 그 time-step 이 계정의 `last_used_step` 보다 크다 | `last_used_step` 갱신 → 세션 `amr` = 1단계 수단 + `otp` + `mfa` → 저장된 authorize 로 302 |
| POST `recoveryCode` 가 남은 해시 중 하나와 일치 | 그 코드 소비(1회용) → 세션 `amr` = 1단계 수단 + `mfa` → 저장된 authorize 로 302. 남은 복구 코드가 2개 이하면 다음 화면에 재발급 안내 |
| 코드 불일치 · **이미 쓴 time-step 의 코드**(재생) · 형식 오류 | «코드가 맞지 않습니다» — 재입력. 재생과 오답을 화면에서 구별하지 않는다 |
| 같은 «1단계만 통과» 세션에서 **5회 실패** | 그 세션의 1단계 통과 기록을 지우고 302 `/login` — 비밀번호부터 다시. 계정은 잠그지 않는다(BE-599 소유자 결정과 같은 이유: 공유 데모 계정이 남의 오답으로 막히면 안 된다) |
| `account_totp` 읽기 실패(DB 장애 등) | «지금은 확인할 수 없습니다 — 잠시 뒤 다시» — **통과시키지 않는다**(fail-closed) |
| «취소» | 저장된 authorize 요청의 `redirect_uri` 로 `error=access_denied` · `error_description=mfa_cancelled` · `state` (OIDC Core 3.1.2.6). 저장된 요청이 없으면 302 `/login` |

🔴 **재생 방지**: 받아들인 코드의 time-step 을 `account_totp.last_used_step` 에 남기고, 그 이하 step 의 코드는 창(±1) 안이어도 거절한다.
admin-service break-glass 검증기에는 이 장치가 없다(TASK-MONO-771 AC-0 F5) — 그쪽은 이 티켓 밖이다.

### 단계 상승 — 이미 로그인한 세션에 2단계를 더한다

클라이언트(콘솔)가 2단계 증거가 필요할 때 `/oauth2/authorize` 를 **`acr_values=mfa`** 로 다시 시작한다(PKCE · `state` 는 평소 로그인과 같다).
RFC 9470 의 단계 상승 모양이다. 이 IdP 가 해석하는 `acr_values` 토큰은 `mfa` 하나이고, 토큰에 `acr` 클레임은 싣지 않는다 — 증거는 `amr` 이다.

| 세션 상태 | 결과 |
|---|---|
| IdP 세션 없음 | 평소 로그인(1단계 → 등록돼 있으면 `/mfa/challenge`) → 아래 행으로 이어진다 |
| 세션 `amr` 에 이미 `mfa` | 화면 없이 code 발급(평소 SSO) |
| `mfa` 없음 · 확정된 등록 있음 | `/mfa/challenge` → 통과 → code |
| `mfa` 없음 · 등록 없음 | `/mfa/setup` → 등록 확정(첫 코드 검증이 곧 두 번째 단계다) → code |

- 단계 상승으로 바뀐 세션 `amr` 은 그 **뒤에** 발급되는 code · 토큰에만 실린다. 이미 발급된 토큰은 그대로다.
- `acr_values` 에 `mfa` 가 **없는** 요청은 지금과 같다 — 등록 없는 계정에게 등록을 강요하지 않는다(소비자 로그인 불변).
- 🔵 「운영자 진입에 2단계가 필요하다」 를 판정하는 것은 이 화면이 아니다 — admin-service(토큰 교환 `403 MFA_REQUIRED`)와
  assume-tenant(`insufficient_user_authentication`)가 판정하고, 클라이언트는 그 거절을 받고 여기로 온다.

### GET · POST /mfa/setup — 등록

**전제 (소유자 결정 OD-4)**: ① IdP 세션에 1단계 통과 기록 ② **인증된 이메일** — `TASK-MONO-770` 의 공용 술어(account-service
`accounts.email_verified_at IS NOT NULL`)를 그대로 쓴다. 비밀번호만 가진 공격자가 피해자보다 먼저 등록하는 것(TOFU)을 메일함 접근까지 요구해 막는다.
시간 유예는 없다 — 정책이 켜진 진입은 거절되고, 거절이 곧 이 화면으로 가는 길이다.

| 상황 | 화면 |
|---|---|
| 1단계 통과 세션 없음 | 302 `/login` |
| 이메일 미인증 | «2단계 인증을 등록하려면 먼저 이메일을 인증해야 합니다» + `/email-verification` 링크 + «취소». **비밀을 만들지 않는다** |
| 인증 여부 조회 실패 | «지금은 확인할 수 없습니다 — 잠시 뒤 다시» (fail-closed — 등록하지 않는다) |
| 확정된 등록이 이미 있다 | «이미 등록되어 있습니다» + `/mfa` 링크. 다시 등록하려면 관리자 리셋이 먼저다(셀프 해제는 이 티켓 범위 밖) |
| GET (위 셋이 아님) | **대기(pending) 비밀**을 새로 만들어 저장(`confirmed_at = NULL`, 이전 대기 행은 교체) → `otpauth://totp/<issuer 표시명>:<마스킹한 이메일>?secret=…&issuer=<issuer 표시명>&algorithm=SHA1&digits=6&period=30` 의 QR + 수동 입력 키 + 6자리 확인 입력 |
| POST `code` 가 대기 비밀과 일치(±1 step) | 확정(`confirmed_at = now`, `last_used_step` = 그 step) + 복구 코드 10개 생성(Argon2id 해시만 저장) → **복구 코드를 이 응답에 한 번만** 표시 → 세션 `amr` 에 `otp` · `mfa` 추가 → «계속»(저장된 authorize 가 있으면 재개, 없으면 `/mfa`) |
| POST `code` 불일치 · 대기 행 없음/만료 | «코드가 맞지 않습니다» — 같은 QR 로 재입력(대기 행이 없으면 GET 으로 다시) |

- **등록 알림 메일 (OD-4)**: 확정 직후 인증된 주소로 «새 2단계 인증 수단이 등록되었습니다 — 본인이 아니라면 …» 을 보낸다. 발송 장치는
  `TASK-MONO-770` 의 `EmailSenderPort`(`iam.mail.enabled`) 그대로. 🔵 발송 실패는 등록을 되돌리지 않는다(WARN 로그, 주소·코드 미기록) — 등록은 이미
  인증된 메일함 소유를 전제로 통과했고, 알림은 그 위의 사후 감지다.
- 대기 비밀은 아무 판정도 통과시키지 않는다 — `/mfa/challenge` 와 단계 상승은 **확정된** 등록만 본다. 대기 행의 수명은 10분(넘으면 확인 시 «코드가 맞지 않습니다»).
- 🔴 R4: 비밀(평문 · Base32) · otpauth URI · 복구 코드 평문은 응답 본문에만 — 로그 · 이벤트 · 감사 어디에도 쓰지 않는다.

### GET /mfa · POST /mfa/recovery-codes — 상태와 복구 코드 재발급

- `GET /mfa`: 1단계 통과 세션이면 «등록됨 / 안 됨» + 남은 복구 코드 수 + 등록 · 재발급 링크. 세션 없으면 302 `/login`.
- `POST /mfa/recovery-codes`(CSRF): 세션 `amr` 에 `mfa` 가 있고 확정된 등록이 있을 때만 — 10개를 새로 만들어 **전부 교체**(이전 코드 즉시 무효) 하고 한 번만 표시.
  `mfa` 가 없는 세션은 `/mfa/challenge` 를 먼저 거친다(재발급은 2단계를 통과한 세션의 권리).

### 기기 분실 — 관리자 리셋

인증 앱과 복구 코드를 모두 잃으면 플랫폼 관리자가 리셋한다 — [admin-api.md § POST /api/admin/accounts/{accountId}/2fa/reset](./admin-api.md#post-apiadminaccountsaccountid2fareset)
(소유자 결정 OD-6: `SUPER_ADMIN` · `SECURITY_ANALYST` 만). 리셋은 그 계정의 `account_totp` 행을 지운다 — 다음 로그인은 등록 없는 계정의 흐름이고,
정책이 켜진 진입에서 거절되면 `/mfa/setup` 으로 간다(위 전제 그대로 — 인증된 이메일 + 등록 알림 메일). admin → auth 내부 계약은 S6 에서 쓴다.

---

## IdP 브라우저 화면 — 운영자 초대 수락 (TASK-MONO-772 · ADR-MONO-080 D6)

회사 운영자 초대([admin-api.md § Operator Invitation](./admin-api.md#operator-invitation-task-mono-772))의 메일 링크가 도착하는 곳. **수락은 IdP 에서 한다**(소유자 결정 OD-3 · 구현자 결정 D-4):
«로그인한 상태로» 를 IdP 브라우저 세션 principal 로 안다(`/consent` · `/email-verification` · `/mfa/setup` 과 같은 자리). 콘솔에 두지 않는 이유 — 운영자 측면이 **없는** 풀 계정은 콘솔
토큰을 받지 못한다(아래 § 풀 계정의 콘솔 토큰). 그걸 열면 셀프 온보딩(`/onboarding`, 인증 이메일 게이트 없음)까지 풀 계정 전부에 열린다 — 그것은 `TASK-MONO-773` 의 일이다(772 AC-0 F9).

화면은 `/login` · `/signup` · `/email-verification` · `/mfa/*` 와 같은 `@Order(0)` 폼 체인(같은 세션 · CSRF 켜짐 · permitAll — 판정은 컨트롤러가 한다)이고, admin-service ·
account-service 를 **서버 측에서** 부른다. 데모 엣지(Traefik `iam-oidc` 라우터)는 `PathPrefix(\`/operator-invitations\`)` 하나로 덮는다.

### GET · POST /operator-invitations/accept — 초대 수락

- `GET /operator-invitations/accept?token=…` 은 **아무것도 바꾸지 않는다** — 메일 보안 스캐너 · 미리보기가 링크를 먼저 GET 한다(`/verify-email` 과 같은 이유). 초대 미리보기
  ([auth-to-admin.md § preview](./internal/auth-to-admin.md#post-internaloperator-invitationspreview--초대-미리보기-task-mono-772))로 회사 · 역할 · 마스킹한 주소를 그리고, 세션 상태에 따라 아래 표의 화면을 보인다.
- `POST /operator-invitations/accept`(`token` · CSRF) → admin-service [`POST /internal/operator-invitations/accept`](./internal/auth-to-admin.md#post-internaloperator-invitationsaccept--초대-수락-task-mono-772) — 🔴 `accountId` 는 **세션 principal 에서** 꺼낸다(폼 · 파라미터에서 받지 않는다).

**GET — 세션 상태별 화면**:

| 세션 | 화면 |
|---|---|
| 미리보기 `404` | «초대를 찾을 수 없습니다 — 취소됐거나 새 링크로 다시 보내졌을 수 있습니다. 가장 최근 메일의 링크를 여세요» |
| 미리보기 `ACCEPTED` | «이미 수락된 초대입니다» + 콘솔 링크 |
| 미리보기 만료 | «초대가 만료되었습니다 — 초대한 분께 다시 보내 달라고 하세요» |
| 미리보기 실패(5xx · 연결) | «지금은 초대를 확인할 수 없습니다 — 잠시 뒤 다시» |
| IdP 세션 없음 | «{회사}의 운영자로 초대되었습니다 — {마스킹 주소}를 인증한 **개인(IAM) 계정**으로 로그인하세요» + **«로그인»**(→ `/login`, 이 화면이 저장된 요청이 되어 로그인 뒤 돌아온다) + **«IAM 계정 만들기»**(→ 아래 `/operator-invitations/signup`) |
| 세션 principal 이 **풀 계정이 아니다**(사이트 계정 · `iam` 자격 · B2B 계정) | «이 초대는 개인(IAM) 계정으로만 수락할 수 있습니다 — 지금 로그인한 계정으로는 수락할 수 없습니다» + 로그아웃 안내. admin-service 를 부르지 않는다(최종 판정은 어차피 account-service 다 — 아래 `403 …ACCOUNT_NOT_ELIGIBLE`) |
| 풀 principal | 회사 · 역할 · «{마스킹 주소}로 온 초대를 이 계정으로 수락합니다» + **«수락»**(POST) |

**로그인 — 수락 화면에서 시작한 로그인은 풀 자격만 고른다**: 저장된 요청이 이 화면(`/operator-invitations/accept`)인 폼 로그인은 **`consumer-pool` 자격을 고른다** — 없으면 로그인 실패(오답 비밀번호와 같은 `/login?error`).
[multi-tenancy.md § 로그인 가능한 계정과 client](../../features/multi-tenancy.md#로그인-가능한-계정과-client-task-be-604) 표의 «시작 client 없음 → 교차 테넌트 조회» 를 이 경로에서 쓰지 않는 이유:
같은 이메일의 `iam` 자격이 있는 사람은 교차 조회가 `LOGIN_TENANT_AMBIGUOUS` 로 막히고, `iam` 자격만 고르면 수락할 수 없는 principal 이 된다. 소셜 로그인은 이 화면에서 시작하지 않는다(소셜 로그인의 테넌트는 시작 client 에서 나오는데 이 화면에는 client 가 없다 — 소셜만 가진 풀 계정은 스토어 · 팬에서 로그인한 브라우저로 이 링크를 다시 연다).
2단계를 등록한 계정은 로그인 흐름의 2단계(`/mfa/challenge`)를 그대로 거친다.

**POST — admin-service 응답별 화면**:

| 응답 | 화면 (요지) | 재시도 |
|---|---|---|
| `200` | «{회사}의 운영자가 되었습니다» + **«콘솔로 가기»**(설정 `iam.operator-invitation.console-url`) — 이제 운영자 측면이 있으니 같은 세션으로 콘솔 토큰이 나온다(아래 § 풀 계정의 콘솔 토큰) | — |
| `200` · `alreadyAccepted` | «이미 수락했습니다» + 콘솔 링크 | — |
| `403 EMAIL_NOT_VERIFIED` | «이 초대를 받으려면 먼저 이메일을 인증해야 합니다» + **`/email-verification`** 링크 — «인증 메일의 링크를 연 뒤 이 초대 링크를 다시 여세요». 초대는 그대로 남는다 | 인증 뒤 같은 링크 |
| `403 OPERATOR_INVITATION_EMAIL_MISMATCH` | «초대받은 주소({마스킹})와 지금 계정의 주소가 다릅니다 — 그 주소의 계정으로 로그인하세요» + 로그아웃 | — |
| `403 OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE` | 위 GET 의 «풀 계정이 아니다» 화면과 같다 | — |
| `404 OPERATOR_INVITATION_NOT_FOUND` | GET 의 `404` 와 같다 | — |
| `409 OPERATOR_INVITATION_ALREADY_USED` | «이미 다른 계정으로 수락된 초대입니다» | — |
| `409 OPERATOR_ALREADY_PROVISIONED` | «이 계정은 이미 다른 회사의 운영자입니다 — 지금은 한 계정이 한 회사의 운영자만 될 수 있습니다» (소유자 결정 OD-1) | — |
| `409 OPERATOR_EMAIL_CONFLICT` | «이 회사에 같은 주소의 운영자가 이미 있습니다 — 초대한 분께 문의하세요» | — |
| `409 OPERATOR_INVITATION_INVALIDATED` | «이 초대는 더 이상 유효하지 않습니다 — 초대한 분께 다시 보내 달라고 하세요» | — |
| `410 OPERATOR_INVITATION_EXPIRED` | GET 의 만료 화면과 같다 | — |
| `503` · 그 밖 · 연결 실패 · 읽을 수 없는 본문 | «지금은 수락할 수 없습니다 — 잠시 뒤 다시» (아무것도 쓰이지 않았다 — 같은 링크로 다시 된다) | 있음 |

🔴 R4: 화면 · 로그 어디에도 토큰을 쓰지 않는다(폼의 hidden 필드만 예외 — 그 페이지의 주인에게 돌려주는 것). 주소는 마스킹한다.

### GET · POST /operator-invitations/signup — 사이트 없는 풀 가입 (소유자 결정 OD-3)

풀 계정이 없는 피초대자(772 Edge Case 2)를 위한 가입. 🔴 **풀 계정만 만든다 — 사이트 멤버십 · `account.created` 없음.** 직원이 회사 초대를 받으려고 스토어 · 팬 회원이 되지 않는다
(772 AC-0 F8 — 소비자 client 의 가입은 그 사이트 멤버십을 함께 만든다). 소비자 사이트는 그 사람이 나중에 처음 방문할 때 동의 화면으로 지금처럼 들어간다([multi-tenancy.md § 소비자 계정 풀 § 4](../../features/multi-tenancy.md#4-로그인--authorize--토큰)).

- `GET /operator-invitations/signup?token=…` — `/signup` 과 같은 입력(이메일 · 비밀번호 · 표시 이름). 토큰은 hidden 필드로만 들고 간다(가입 뒤 수락 화면으로 돌아가기 위해 — 가입 자체는 토큰을 판정하지 않는다).
- `POST` → account-service [`POST /internal/consumer-pool/signups`](./internal/auth-to-account.md#post-internalconsumer-poolsignups--사이트-없는-풀-가입-task-mono-772) (서버 측, `/signup` 프록시와 같은 이유).

| account-service 응답 | 화면 |
|---|---|
| `201` | `/login` 으로 — 저장된 요청 = `/operator-invitations/accept?token=…`(로그인 뒤 수락 화면으로 돌아온다). 수락 화면은 미인증 이메일에 `EMAIL_NOT_VERIFIED` 안내를 보인다 |
| `409 ACCOUNT_ALREADY_EXISTS` | «이미 IAM 계정이 있는 주소입니다 — 로그인하세요» + 로그인 링크(같은 저장 요청) |
| 그 이메일의 **소비자 사이트 계정**이 있어 풀 가입을 받지 않는 경우([multi-tenancy.md § 소비자 계정 풀 § 2](../../features/multi-tenancy.md#2-가입--소비자-client-의-새-가입은-풀로) 공존 금지 — 지금 소비자 가입이 내는 응답 그대로) | «이 주소는 스토어 · 팬 계정으로 이미 쓰이고 있습니다 — 그 계정으로 로그인한 뒤 다시 여세요» |
| `422 VALIDATION_ERROR` | 입력 오류 표시(`/signup` 과 같다) |
| `429` · 그 밖 · 연결 실패 | «지금은 가입할 수 없습니다 — 잠시 뒤 다시» |

- 🔵 이 경로가 만든 계정의 첫 세션은 **사이트 없는 풀 principal** 이다. `/email-verification`(인증 메일 보내기)은 이 세션에서도 동작해야 한다 — 그 화면은 `X-Tenant-Id` = 세션의 테넌트로 부른다. 🔴 사이트 없는 풀 principal 의 세션 테넌트에서 account-service 재발송이 계정을 찾는지는 **S3 의 확인 항목**이다(못 찾으면 S3 이 그 조회를 고친다 — 수락의 인증 전제가 이 화면에 달려 있다).

---

## 풀 계정의 콘솔 토큰 — 운영자 측면이 있을 때만 (TASK-MONO-772 · ADR-MONO-080 D6)

**`TASK-BE-615` D-5 개정 · 구현자 결정 D-5(P1).** 지금까지 풀 principal 은 콘솔 client(`platform-console-web`)의 토큰을 받지 못했다 — 콘솔 세션 테넌트가 `consumer-pool` 로 남아
발급자가 거절했다(`TASK-BE-614` 게이트, [multi-tenancy.md § 소비자 계정 풀 § 4](../../features/multi-tenancy.md#4-로그인--authorize--토큰)). 772 부터:

| 풀 principal | 콘솔 토큰 (`authorization_code` · `refresh_token`) |
|---|---|
| **살아 있는 운영자 측면 있음** — admin-service [`GET /internal/operators/console-eligibility`](./internal/auth-to-admin.md#get-internaloperatorsconsole-eligibility--풀-계정에-콘솔-토큰을-줄까-task-mono-772) `eligible=true`(= `admin_operators.oidc_subject = sub ∧ status = ACTIVE`, 토큰 교환과 같은 술어) | **발급** — `sub` = 풀 계정 id · `tenant_id = iam` · `tenant_type` · `email` · `amr` 은 다른 콘솔 토큰과 같은 규칙 · `roles` 없음(콘솔 client 는 역할을 주지 않는다 — 운영자 권한은 admin 토큰 교환에서 온다) · `entitled_domains` 없음 |
| 측면 없음 · 측면이 `ACTIVE` 아님(퇴사 · 정지) | **거절** — `400 invalid_grant`, `error_description` = 지금 문구 **그대로**(`tenant_id 'consumer-pool' is a reserved storage value and is never issued` — 바이트 불변. 콘솔 `TASK-PC-FE-324` 의 `sso_wrong_account` 판별이 이 문구의 `'consumer-pool'` 에 걸려 있다) |
| 판정을 못 받음(admin-service 4xx · 5xx · 타임아웃 · circuit-open · 본문 이상) | **거절(fail-closed)** — `400 invalid_grant`, **`error_description=operator_eligibility_unavailable`**(고정 상수 — 값 전체 일치로 가른다. `'consumer-pool'` 을 포함하지 않는다: 장애를 «다른 계정으로 로그인돼 있다» 로 보이게 하지 않는다, 772 AC-0 F5) |

- **매번 다시 묻는다** — 풀 principal 의 콘솔 `authorization_code` · `refresh_token` 발급마다(캐시 없음, 멤버십 읽기와 같은 모양). 퇴사(운영자 `SUSPENDED`) 뒤 **다음 refresh 부터** 콘솔 토큰이 없다(772 AC-3). 이미 발급된 콘솔 access token 은 만료까지 살지만, 운영자 토큰 교환은 그 즉시 `401` 이다(교환이 같은 술어를 매번 읽는다).
- **세션 테넌트 (772 AC-0 F4)** — 발급자 · authorize 게이트 · refresh 미러 행이 같은 함수(`AuthorizationSessionTenant`)로 세션 테넌트를 정한다. «풀 principal × 콘솔 client → `iam`» 갈래를 **풀-사이트 갈래와 따로** 둔다: 그 함수는 I/O 를 하지 않으므로 측면 판정은 발급자가 한다(위 표). refresh 미러 행의 테넌트는 `iam` 이다. `poolPrincipalMapsTo`(소비자 사이트 매핑)는 바꾸지 않는다 — 거기 `iam` 을 넣으면 게이트가 풀-사이트 분기로 가서 `iam` 의 소비자 사이트 멤버십을 묻고 «소비자 사이트 아님» → 재인증 무한 반복이 된다.
- 🔴 **authorize 게이트는 콘솔 client 에 대해 지금 판정을 그대로 한다** — 풀 principal 이어도 `TASK-BE-610` 조건부 재인증(같은 이메일의 `iam` 자격이 있으면 재인증 → 콘솔 폼이 그 `iam` 자격을 고른다)을 먼저 하고, 아니면 통과한다. 세션 테넌트가 이제 `iam` 으로 계산된다고 해서 «세션 테넌트 = client 테넌트 → 통과» 로 단락시키지 않는다 — 그러면 `iam` 자격과 풀 계정을 함께 가진 사람(데모 `demo@demo.com`)이 재인증 없이 풀 principal 로 콘솔에 들어와 «측면 없음» 거절을 받는다(BE-610 회귀). 이 줄은 772 AC-0 F4 의 «게이트는 `PASS`» 를 S1 에서 정밀화한 것이다.
- 2단계: 풀 운영자의 콘솔 로그인도 로그인 흐름의 2단계(§ IdP 브라우저 화면 — 2단계 인증)를 거친다. 교환 · assume 의 2단계 요구(TASK-MONO-771)는 그대로 문다 — TOTP 는 `account_id` 키라 풀 이동 뒤에도 산다.
- **열지 않는 것**: 측면 **없는** 풀 계정의 콘솔 토큰 · 그래서 셀프 온보딩(`/onboarding` — 콘솔 토큰 필요)도 풀 계정에 계속 닫혀 있다. 그 개방(비운영자 셸 · ADR-MONO-044 D4 인증 이메일 트러스트 게이트)은 `TASK-MONO-773` 이 한다(ADR-080 D9 = T1). 773 이 넓힐 때 바꾸는 곳은 위 표의 «측면 없음» 행 한 곳이다 — 두 거절 상수는 그대로 둔다.
- `consumer-pool` 은 여전히 어떤 토큰에도 나오지 않는다. 새 클레임 없음([jwt-standard-claims.md](../../../../../platform/contracts/jwt-standard-claims.md) 무변경).
- 🔵 **구현 = `TASK-MONO-772` S4.** 그 전까지는 위 § 소비자 계정 풀 § 4 의 615 동작(풀 principal 의 콘솔 토큰 없음)이다.

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
| 403 | `TOKEN_TENANT_MISMATCH` | refresh token의 `tenant_id`와 계정의 현재 `tenant_id`가 불일치. 토큰 자체는 유효(인증됨)하나 다른 tenant로의 rotation은 권한 없음(Forbidden) — `platform/error-handling.md` § Auth / Token (TASK-MONO-462). 조작·버그 의심 → 보안 이벤트 발행. **SAS OAuth2 token endpoint(refresh grant)는 이 REST 경로와 별개**로 RFC 6749 §5.2에 따라 동일 semantic 오류를 `400 invalid_grant`로 응답 — 의도된 divergence(OAuth2 스펙 제약), 통일 대상 아님 |
| 401 | `SESSION_REVOKED` | 명시적으로 revoke된 세션 |
| 423 | `ACCOUNT_LOCKED` | 계정 잠김 (refresh 차단) — `platform/error-handling.md` § Account (TASK-BE-462) |

**Side Effects**:
- 성공: `auth.token.refreshed` 이벤트, DB에 새 refresh_token row + 기존 row의 `rotated_from` 체인 갱신
- 재사용 탐지: `auth.token.reuse.detected` 이벤트 + 해당 account의 모든 refresh_tokens `revoked=TRUE`

---

## Token Specification

### Access Token (JWT)

| Claim | 값 |
|---|---|
| `sub` | account_id (UUID) |
| `iss` | OIDC issuer URL (`oidc.issuer-url`) |
| `iat` | 발급 시각 (epoch seconds) |
| `exp` | `iat + 1800` (30분) |
| `jti` | UUID |
| `scope` | `user` (일반 사용자) 또는 `admin` (운영자) |
| `tenant_id` | 토큰을 발급한 테넌트의 slug (예: `fan-platform`, `wms`). **필수** — 누락 시 JwtSigner가 발급 거부 (fail-closed) |
| `tenant_type` | `B2C_CONSUMER` \| `B2B_ENTERPRISE`. **필수** |
| `device_id` | 이 access token이 속한 device session의 opaque UUID v7. 로그인 성공 시 `device_sessions.device_id`에서 채워지며, refresh rotation으로 새 access token이 발급될 때도 동일 값을 유지한다. `GET /api/accounts/me/sessions/current`, `DELETE /api/accounts/me/sessions` (bulk) 등 "현재 세션" 해석의 단일 소스 |

서명: RS256. 공개 키는 JWKS 엔드포인트로 배포.

**검증 필수 사항 (TASK-BE-143):** gateway-service 는 `iss` claim 값이 `gateway.jwt.allowed-issuers` 허용목록(기본값: SAS OIDC issuer URL `oidc.issuer-url` + legacy `iam`)에 포함되는지 검증한다 (community-service 도 함께 강제했으나 TASK-MONO-394 로 RETIRED). `iss` claim 이 누락되거나 허용목록에 없는 값이면 토큰을 거부 (`401 TOKEN_INVALID`). 허용목록은 환경별 분리 시 환경 변수로 override 한다. admin-service 는 자체 `IssuerEnforcingJwtVerifier` 로 admin IdP issuer 를 별도 강제한다.

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

[platform/error-handling.md](../../../../../platform/error-handling.md) 표준.

---

## ~~GET /api/auth/oauth/authorize~~ · ~~POST /api/auth/oauth/callback~~ — REMOVED 2026-08-01 (TASK-BE-398)

> **제거됨.** 커스텀-JWT 로 종결되던 소셜 로그인 JSON 플로우다. ADR-006 이 이를 SAS
> 브라우저 세션 플로우로 대체했고(TASK-BE-396/397), 레거시 JSON 쌍은 `POST /api/auth/login`
> 과 같은 계열이므로 같은 일몰(2026-08-01)에 함께 삭제되었다. gateway `public-paths` 의
> 두 항목도 제거되었다.
>
> **대체**: `GET /login/oauth/{provider}` → provider → `GET /login/oauth/{provider}/callback`.
> 콜백은 커스텀 JWT 대신 **SAS 가 소비하는 인증 세션**을 확립하고 저장된
> `/oauth2/authorize` 요청을 재개하므로, 최종 토큰은 표준 OIDC 토큰이다.
> 상세: [oauth-social-login.md](../../features/oauth-social-login.md).
>
> **보존된 것 (브라우저 플로우가 그대로 재사용)**: `OAuthLoginUseCase.authorize()` /
> `resolveBrowserLogin()` / 공유 `resolveSocialLogin()`, `SocialIdentityPersistStep`,
> `OAuthClient` 구현 전부(Google/Kakao/Microsoft/Naver) + `OAuthClientFactory`,
> `OAuthStateStore`(Redis state), `social_identities` 테이블/리포지토리,
> ADR-036 born-unified 계정 연결.
>
> **함께 제거된 것**: `OAuthLoginUseCase.callback()`, `OAuthCallbackRequest` /
> `OAuthCallbackResponse` / `OAuthAuthorizeResponse` DTO, `OAuthCallbackTxnCommand`,
> `OAuthLoginResult`.
>
> **에러 코드 의미는 살아 있다** — `INVALID_STATE`(400) / `INVALID_CODE`(401, provider 가
> code 를 거절) / `PROVIDER_ERROR`(502, provider 실제 장애) 의 구분(TASK-MONO-350)은
> 어댑터(`OAuthClientSupport`)와 `AuthExceptionHandler` 에 그대로 남아 브라우저 플로우에도
> 적용된다. 브라우저 플로우는 이를 JSON 이 아니라 `redirect:/login?error=...` 로 표면화한다.
>
> 원본 스펙 본문은 git history 에 보존된다.

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
| CURRENT_PASSWORD_MISMATCH | 400 | 현재 패스워드 불일치. **400 은 의도적이다** — 호출자는 이미 인증된 상태이고 이건 요청 필드 검증 실패이지 로그인 실패가 아니다(401 을 주면 클라이언트가 세션 만료로 읽고 비밀번호 변경 도중 사용자를 로그아웃시킨다). **TASK-MONO-350 이전에는 코드가 `INVALID_CREDENTIALS`** 였다 — 로그인 실패가 401 로 쓰는 바로 그 코드라, `code` 로 분기하는 클라이언트가 둘을 구별할 수 없었다 |
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

**Delivery (TASK-MONO-770, 소유자 결정 2026-10-07 — 인증 메일과 같은 장치)**: `EmailSenderPort` 구현체는
`iam.mail.enabled` 하나로 고른다 — `true` 면 표준 `spring.mail.*` 로 보내는 SMTP 어댑터(공급자별 코드 없음),
`false`/미설정이면 `prod` 가 아닐 때만 로깅 스텁이 뜨고 `prod` 에서는 **아무것도 안 떠 기동이 실패한다**(TASK-BE-242
의 fail-fast 유지). 링크 = `{iam.mail.password-reset-link-base-url}?token=<token>`. 🔴 응답은 여전히 **언제나 204** 다 —
이 엔드포인트는 계정 존재 여부를 숨겨야 하므로 발송 실패를 응답으로 드러낼 수 없다(실패는 WARN 로그, 토큰·주소 미기록).
링크가 가리킬 재설정 화면 · 요청 화면은 TASK-BE-627 이 만들었다 — 아래 § IdP 브라우저 화면 — 비밀번호 재설정.

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

---

## IdP 브라우저 화면 — 비밀번호 재설정 (TASK-BE-627, TASK-MONO-770 의 남은 화면)

위 두 JSON 엔드포인트가 가리킬 화면 둘. 둘 다 `/login` · `/signup` · `/consent` · `/email-verification` ·
`/verify-email` 과 같은 `@Order(0)` 폼 체인에 있다(같은 세션 정책 · CSRF 켜짐 · permitAll — 판정은 컨트롤러가
한다). **이메일 인증 화면과 달리 account-service 를 부르지 않는다** — `RequestPasswordResetUseCase` ·
`ConfirmPasswordResetUseCase` 가 auth-service 로컬 애플리케이션 서비스이기 때문에(크리덴셜 소유권은 이미
auth-service local, TASK-BE-063 이후) `PasswordResetPageController` 는 이들을 HTTP 가 아니라 **직접** 부른다
— 기존 JSON `PasswordResetController` 와 같은 호출 방식. 데모 엣지(Traefik `iam-oidc` 라우터)는
`PathPrefix(\`/password-reset\`)` 하나로 두 경로를 다 덮는다(가드 (p) 가 템플릿 링크와 대조한다).

### GET · POST /password-reset/request — 재설정 메일 요청

| 상황 | 화면 |
|---|---|
| GET | 이메일 입력 폼 |
| POST, 이메일 비어 있음 | «이메일을 입력해 주세요» — `RequestPasswordResetUseCase` 를 부르지 않는다(계정 조회 전 형식 검사라 존재 비노출과 무관) |
| POST, 이메일 입력됨 | 🔴 **있는 이메일 · 없는 이메일 · 홍수 제한에 걸린 요청 모두 같은 화면**: «계정이 있다면 비밀번호 재설정 메일을 보냈습니다» — `RequestPasswordResetUseCase.execute()` 는 이 셋을 전부 조용히 흡수하고 정상 반환하므로(auth-api.md § POST /api/auth/password-reset/request), 컨트롤러에는 이 셋을 가를 수 있는 분기 자체가 없다 |
| POST, 그 밖의 예외(예: Redis 장애) | «잠시 후 다시 시도해 주세요» |

### GET · POST /password-reset — 메일의 링크가 도착하는 곳

- `GET /password-reset?token=…`(= `iam.mail.password-reset-link-base-url`): 토큰이 있으면 새 비밀번호 +
  확인 입력 폼. 토큰이 없으면(주소를 직접 친 경우 등) **오류가 아니라 안내** — «재설정 링크가 필요합니다» +
  요청 화면 링크(Edge Case).
- `POST /password-reset`(`token` hidden 필드 · `newPassword` · `confirmPassword` · CSRF) →
  `ConfirmPasswordResetUseCase`. 로그인 세션은 보지 않는다 — 로그인 상태에서 링크를 열어도 그대로 진행된다
  (토큰 자체가 자격이다, Edge Case).

| 화면/판정 | 결과 |
|---|---|
| `newPassword` 비어 있음 · `confirmPassword` 와 불일치 | «비밀번호가 일치하지 않습니다» — 유스케이스를 부르지 않는다. 토큰은 hidden 필드로 유지 |
| `PasswordResetTokenInvalidException`(토큰 없음·만료·이미 사용됨) | «링크가 만료되었거나 이미 사용되었습니다» + «재설정 메일 다시 요청» 링크. 같은 토큰으로 재시도해도 성공할 수 없으므로 토큰은 **버린다**(요청 화면으로 안내할 뿐) |
| `PasswordPolicyViolationException` | 🔴 정책 문구(8자 이상 · 대/소문자·숫자·특수문자 3종 이상 · 이메일 미포함) — **토큰은 유지**한다(Failure Scenario 3: 여기서 토큰을 잃으면 메일을 다시 받아야 한다), 단 `newPassword`/`confirmPassword` 입력값은 절대 다시 채우지 않는다 |
| 성공 | `/login?passwordReset` 로 redirect → «비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요» |
| 그 밖의 예외 | «지금은 처리할 수 없습니다 — 잠시 뒤 다시» (토큰은 소비되지 않았다 — 같은 링크로 다시 된다) |

🔴 R4: 화면·로그 어디에도 토큰을 텍스트로 쓰지 않는다(hidden 필드만 예외 — 그 페이지를 연 사람에게 돌려주는 것).

`login.html` 에 «비밀번호를 잊으셨나요?» 링크(`/password-reset/request`)를 추가했다 — ADR-007 invariant 3
(`#username`/`#password`, CSRF 필드, `POST /login`, 폼당 submit 버튼 정확히 1개)은 변경하지 않았다(이 링크는
`<a>` 이지 버튼이 아니다).
