# Task ID

TASK-MONO-410

# Title

보안 스킬 3종이 계약과 **반대되는 것**을 가르친다 — 단수 `role` · 대칭키 HMAC · 인가 체크 없는 게이트웨이 필터

# Status

review

# Owner

monorepo

# Task Tags

- code
- security

---

# Goal

`/validate-rules`(2026-07-15)가 `.claude/skills/` 의 **보안 스킬이 정경 계약을 정면으로 거스르는** 것을 찾았다. 스킬은 에이전트가 코드를 쓸 때 **실제로 읽고 따라 쓰는** 표면이다 — 여기가 틀리면 결함이 **새 코드로 계속 재생산된다.**

**① `backend/jwt-auth/SKILL.md`** (실측 확인)
- `:50` — `.claim("role", user.getRole().name())` **단수 `role`**. 계약 `platform/contracts/jwt-standard-claims.md` 는 **`roles` 집합이 "the sole authorization axis"**(ADR-MONO-032, 한 계정이 `CUSTOMER` + `WMS_OPERATOR` 를 동시에 가진다). **단일 값 클레임으로는 표현 자체가 불가능하다.**
- `:32,135` — **대칭키 `SecretKey` / HMAC-SHA256**. 계약 `:36` — **RSA 비대칭 + JWKS 검증**.

**② `backend/gateway-search`… 아니라 `backend/gateway-security/SKILL.md`**
- `:51-56` — `claims.get("role", String.class)` 로 읽어 `X-User-Role` 로 포워딩. 계약은 **`roles` 배열**(`X-User-Role ← comma-separated roles array`).
- `:30-59` — 참조 `JwtAuthenticationFilter` 에 **역할 기반 admission 체크가 아예 없다.** 계약 `:128` — *"Admit iff the token carries ≥ 1 role valid for the requested surface; otherwise `403`."* **스킬대로 쓰면 인증만 하고 인가는 안 하는 게이트웨이가 나온다.**
- `:150-158` — rate-limit 키가 **모든 라우트에서 무조건 client IP**. `platform/api-gateway-policy.md` 는 인증 트래픽을 **`acct:<sub>`** 로 키잉하라고 한다(IP 키잉은 NAT 뒤 전원이 한 버킷을 공유하고, IP 를 도는 남용자는 영원히 안 걸린다 — `MONO-368/370` 이 실제로 치른 대가).

**③ `backend/rate-limiting/SKILL.md:85-99`** — `/api/auth/refresh` 를 IP+path 로 키잉. fleet 표는 refresh 를 **`acct:<sub>`** 로 규정한다.

**④ `service-types/identity-platform-setup/SKILL.md:21-23`** — 엔드포인트 경로 3개가 스펙과 다름(`/oauth/token` vs `/v1/oauth/token`, 별도 `/refresh` 경로 — 스펙은 하나의 `/v1/oauth/token` 이 두 grant 를 처리, revoke 는 `/v1/oauth/revoke`).

**⑤ `service-types/rest-api-setup/SKILL.md:40,68`** — 자기 예제가 `/orders`, `/v1/orders/**` — **mandatory 한 `/api/` 접두사 누락**.

---

# Scope

## In Scope

- `.claude/skills/backend/jwt-auth/SKILL.md` · `backend/gateway-security/SKILL.md` · `backend/rate-limiting/SKILL.md` · `service-types/identity-platform-setup/SKILL.md` · `service-types/rest-api-setup/SKILL.md` — **계약에 맞게 정정**.
- 각 스킬이 **자기가 인용하는 계약을 실제로 가리키게** 한다(`platform/contracts/jwt-standard-claims.md` · `platform/api-gateway-policy.md`).
- **jwt-auth 의 존재 이유를 결정한다(AC-2)** — 대칭키 HMAC 발급이 *잘못된 게* 아니라 **다른 문맥**(standalone 로컬 서비스)일 수 있다. 그렇다면 스킬 상단에 **적용 범위를 못박아라**(*"IdP 가 발급하는 플랫폼 토큰에는 쓰지 말 것 — 그건 identity-platform"*). 지금은 아무 경계가 없어 **플랫폼 토큰을 만들려는 사람이 그대로 따라 쓴다.**

## Out of Scope

- **프로덕션 코드 수정 없음.** 이 task 는 **가르치는 문서**를 고친다. 실제 서비스가 단수 `role` 을 쓰고 있다면 그건 **별건 티켓**이다(AC-4 가 그 여부를 *조사만* 한다).
- `libs/java-security`·`libs/java-gateway` 의 실제 구현(이미 `ADR-MONO-049` 로 정경화됨 — 스킬이 그 라이브러리를 가리키게 하는 것으로 충분할 수 있다. AC-3 참조).

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — **5건 전부 실재. phantom 0.** ②~⑤(PLAUSIBLE 등급)를 직접 열어 확인했고 전부 참이었다. **덤으로 6번째를 찾았다**: `backend/refactoring/SKILL.md` 의 예제도 `@PostMapping("/orders")` 로 `/api` 접두사가 빠져 있었다(티켓 목록에 없던 파일 — 같은 결함 클래스라 함께 고침).
- [x] **AC-1** 계약 모순 **0**. `roles` 배열 / RS256+JWKS / **역할 admission(규칙 6, 403)** / **인증=`acct:<sub>`**. 잔존 검사에서 걸린 4건은 읽어보니 **전부 정당**했다 — 위반을 *경고하는* 문장 2건, **웹훅 서명 검증용 HMAC**(JWT 서명과 무관) 1건, *"`JwtClaims.roles(...)` 는 없다"* 는 경고 1건. **개수가 아니라 읽어서 판정했다.**
- [x] **AC-2 (범위 선언)** `jwt-auth` 상단에 **Scope 표** 신설 — 발급 자격은 **선언된 `Service Type`** 이 정한다: `identity-platform` 만 발급, 게이트웨이는 `gateway-security`, **나머지 전부는 Resource Server(검증만)**. HMAC 자체 발급 금지 근거를 명시(폐기된 ecommerce `auth-service` — `settings.gradle` 이 빌드에서 제외).
- [x] **AC-3 (사본을 만들지 마라)** — **답은 "가리켜라"였다.** 스킬이 손으로 가르치던 것을 라이브러리가 **이미 제공**한다: `libs/java-security`(`Rs256JwtSigner`·`Rs256JwtVerifier`·`JwksProvider`·`AllowedIssuersValidator`) / `libs/java-gateway`(`IdentityHeaderStripFilter`·`JwtHeaderEnrichmentFilter`·`JwtClaims`·`SecurityConfig`·`FailOpenRateLimiter`·`ReactiveJwtAccess`). 두 스킬에 lib 매핑 표를 넣고 *"ADR-MONO-049 가 사본 49개를 지웠다 — 필터를 새로 쓰는 건 50번째를 쓰는 것"* 을 명시. **⚠️ 그리고 내가 lib API 를 지어냈다가 잡혔다**: 초안이 `JwtClaims.roles(jwt)` / `ReactiveJwtAccess.jwt(exchange)` 를 가르쳤는데 **둘 다 존재하지 않는다**(실제: `JwtClaims.role(jwt)` = 콤마 결합 **문자열**(배열 우선 + 단수 fallback + `""`), `ReactiveJwtAccess.currentJwt()`). 소스를 열어 확인하지 않았으면 **내가 고치려던 결함(없는 것을 가르치기)을 그대로 재생산**할 뻔했다 → 실제 API 로 정정하고, admission 은 `jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES)` 로 **배열을 직접** 읽게 했다(콤마 결합 문자열로 admit 하면 안 된다).
- [x] **AC-4 (청구서 조사 — 고치지 않음)** — **대리 지표 3개 중 2개가 오탐이었다.**
  - **단수 `role` 발급 = 살아있는 코드 0건.** grep 은 1건을 냈지만(`ecommerce/apps/auth-service/.../JwtTokenGenerator.java:37`) 그 모듈은 **`settings.gradle:51` 이 빌드에서 제외**했다(*"excluded from build by TASK-BE-132 (decommissioned)… Replaced by IAM OIDC"*). **죽은 코드다.** 라이브 IdP(iam)는 단수 role 을 내지 않는다.
  - **IP-only rate-limit = 0건.** `getRemoteAddress` 참조가 5개 게이트웨이에 있으나 읽어보니 **정경 준수**였다: erp(sub 6/ip 1)·fan(sub 4/ip 1)·finance 는 인증=`acct:<sub>`+익명=IP, ecommerce 는 `t:<tenant>:acct:<sub>`(MONO-368 수정본). **IP 참조 ≠ IP-only.**
  - **🔴 역할 admission = 진짜 갭. 게이트웨이 7개 중 5개(wms·fan·finance·erp·iam)에 없다.** 공유 `libs/java-gateway`의 `SecurityConfig` 는 `.anyExchange().authenticated()` 까지만 하고, **규칙 6(≥1 유효 역할 없으면 403)은 각 게이트웨이가 붙여야 하는데** ecommerce(`AccountTypeEnforcementFilter`)와 scm 만 붙였다. **과장하지 않는다** — `aud` 스코핑(규칙 5)이 타 플랫폼 토큰을 이미 막으므로 노출면은 *플랫폼 내부* 역할 분리(예: 일반 오퍼레이터가 admin 표면 도달)다. **⇒ 별건 티켓 제안**(프로덕션 보안 변경은 자기 티켓·자기 검증이 필요하다. 이 task 에서 고치지 않음).

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (**정경 — `roles` 단일 축, RSA/JWKS, admission 규칙**)
- `platform/api-gateway-policy.md` § Rate Limiting (**정경 — 인증=`acct:<sub>`, 익명=`ip`**)
- `platform/service-types/{identity-platform,rest-api}.md`
- `docs/adr/ADR-MONO-032-*`(roles 단일 축) · `ADR-MONO-049`(보안 사본 → lib)
- `tasks/done/TASK-MONO-368-*` / `TASK-MONO-370-*` (rate-limit 키 전략의 실제 인시던트)

# Related Skills

- 대상 그 자체(위 5개).

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

---

# Target Service

N/A — `.claude/skills/` 문서.

---

# Implementation Notes

- **이 결함은 CI 가 절대 못 잡는다** — 스킬은 컴파일되지 않고 테스트되지 않는다. **검증은 계약 문서와의 대조뿐**이고, 그래서 AC-0(재측정)과 AC-1(모순 0)이 이 티켓의 전부다.
- `.claude/skills/` 편집이 classifier 에 막히는지 **먼저 확인**하라(실측 지도상 `hooks/`·`agents/`·`commands/` 는 차단, `skills/` 는 불확실). 막히면 `MONO-409` 와 같은 절차(패치를 사용자에게).

---

# Edge Cases

- **단수 `role` 이 의도된 곳이 있는가** — 없다. 계약이 *"sole authorization axis"* 라고 못박았고 `account_type` 파티션은 ADR-032 로 제거됐다. 다만 **레거시 토큰 호환 서술**이 스킬에 필요한지는 계약의 § Migration Compatibility 를 읽고 판단.
- **rate-limit IP 키가 옳은 경우가 있다** — 익명 트래픽(login/signup, pre-auth)은 **IP 가 맞다**. 규칙은 "IP 금지" 가 아니라 **"인증 트래픽은 principal 로"** 다. 과잉 교정하면 로그인 폭주를 못 막는다.

---

# Failure Scenarios

- **스킬만 고치고 살아있는 코드를 안 본다** → 문서는 옳고 프로덕션은 틀린 상태. 완화 = AC-4(조사·별건 제안).
- **스킬에 보안 필터를 더 잘 써 준다** → 사본 50번째. 완화 = AC-3(lib 을 가리켜라).
- **감사 보고를 그대로 믿고 이미 옳은 스킬을 "고친다"** → phantom churn. 완화 = AC-0.

---

# Test Requirements

- N/A(문서). 대조 = 계약 문서.

---

# Definition of Done

- [ ] 5개 스킬 정정 + 계약 포인터.
- [ ] AC-3 lib 라우팅 판단 기록, AC-4 조사 결과(0건이면 0건) 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**(`jwt-auth/SKILL.md:50` 단수 `role`, `:32` `SecretKey`), ②~⑤는 서브에이전트 보고(**PLAUSIBLE — 착수 시 재확인 필수**).

분석=Opus 4.8 / 구현 권장=**Opus**(보안 계약 해석 + lib 라우팅 판단. 문장 교체가 아니라 *무엇을 가르칠 것인가* 결정이다).

---

# 착수 기록 (구현)

## 🔴 내가 고치려던 결함을 내가 저지를 뻔했다

이 티켓의 병은 *"스킬이 존재하지 않는/틀린 것을 가르친다"* 이다. 그런데 **내 초안이 정확히 그것을 했다** — `JwtClaims.roles(jwt)` 와 `ReactiveJwtAccess.jwt(exchange)` 를 가르쳤는데 **둘 다 lib 에 없다.**

실제 API:
- `JwtClaims.role(Jwt)` → **콤마로 결합된 문자열**(`roles` 배열 우선 → 단수 `role` fallback → `""`). 그 precedence 자체가 보안 계약이라 javadoc 이 못박아 뒀다. 이건 **`X-User-Role` 헤더용**이다.
- **admission 은 배열을 직접** 읽어야 한다: `jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES)`. **결합된 문자열로 admit 하면 안 된다.**
- `ReactiveJwtAccess.currentJwt()` (`Mono<Jwt>`), `currentToken()`.

**소스를 열지 않고 "라이브러리를 가리켜라" 만 했으면 사본 대신 *환각*을 가르쳤을 것이다.** AC-3 의 진짜 요구는 "lib 을 언급하라" 가 아니라 **"lib 이 실제로 무엇을 제공하는지 읽어라"** 였다.

## 훅이 편집을 막았고, 훅 자신이 결함이었다

`.claude/hooks/rule-consistency-check.ps1` 의 `RULE-CONSISTENCY-01` 은 *"스킬 본문에 `specs/` 경로가 없다"* 며 편집을 차단했다 — **내 새 본문에는 `specs/services/<service>/architecture.md` 가 분명히 있는데도.**

원인: 훅은 `Edit`(old_string/new_string)일 때 파일을 읽어 `.Replace(old, new)` 로 편집 후 내용을 재구성하는데, **워크트리 파일이 CRLF 라 LF 기준 `old_string` 이 매칭되지 않는다** ⇒ Replace 가 no-op → **훅이 "편집 전" 원본을 검사** → `specs/` 없음 → 차단. (`Write`(content) 경로는 새 본문을 직접 보므로 정상 동작한다.)

⇒ **가드가 검사한 것은 커밋될 산출물이 아니라 편집 전 파일이었다.** 같은 계열의 실패가 이 저장소에 이미 여러 번 있었다(`MONO-405` CRLF·인코딩, `MONO-376` perl↔CRLF). **범위 밖**(훅 = `.claude/hooks/`, 분류기 차단 구역) → **별건 티켓 후보로 기록**.

또 하나: 그 훅의 REMEDIATION ② (*"cross-cutting 이면 근거를 인라인으로 적어라"*)는 **술어를 통과시키지 못한다** — 술어는 오직 `content -match 'specs/'` 뿐이다. **스탠자가 제안하는 해법이 스탠자의 검사를 통과하지 못한다.**

## 무엇을 가르치도록 바꿨나

| 스킬 | before | after |
|---|---|---|
| `jwt-auth` | `SecretKey`/HMAC-SHA256 서명, `.claim("role", …)` 단수, 범위 선언 없음 | **Scope 표**(발급은 `identity-platform` 만; 나머지는 Resource Server) + **RS256/JWKS** + **`roles` 배열** + `jti`/`kid` + lib 매핑 |
| `gateway-security` | `claims.get("role", String.class)` → `X-User-Role`, **admission 0줄**, 전 라우트 IP 키 | **6개 enforcement 규칙**(계약 원문) + **`RoleAdmissionFilter`(403)** + **`acct:<sub>` 키(익명은 IP 유지)** + `libs/java-gateway` 라우팅 |
| `rate-limiting` | `/api/auth/refresh` 를 **IP+path** 로 키잉 | **키 결정표**: pre-auth(login/signup)=**IP 가 정답**, 인증(**refresh 포함**)=`acct:<sub>`. MONO-368/370 인시던트 인용 + **과잉교정 경고** |
| `identity-platform-setup` | `/oauth/token`, 별도 `/oauth/token/refresh`, `/oauth/token/revoke` | **`/v1/oauth/token` 하나가 두 grant**(`grant_type` 로 분기), **`/v1/oauth/revoke`**, `/v1/oauth/logout`·`/introspect` 명시 |
| `rest-api-setup` | `@PostMapping("/orders")`, `Path=/v1/orders/**` | **`/api/v1/orders`** (게이트웨이 뒤에서 `/orders` 는 도달 불가) |
| `refactoring` *(티켓 밖 — AC-0 재측정에서 발견)* | `@PostMapping("/orders")` ×2 | `/api/v1/orders` |

## 범위 밖으로 남긴 것 (별건 티켓 제안)

1. **🔴 게이트웨이 5개에 역할 admission 이 없다** (wms·fan·finance·erp·iam). 계약 규칙 6 은 *"≥1 유효 역할이 없으면 403"* 인데 공유 `SecurityConfig` 는 `.authenticated()` 까지만 하고, 나머지는 각 게이트웨이 몫이다 — ecommerce·scm 만 붙였다. **`aud` 스코핑이 타 플랫폼 토큰은 막으므로** 노출면은 *플랫폼 내부* 역할 분리다. **프로덕션 보안 변경이므로 자기 티켓·자기 검증이 필요하다.**
2. **`rule-consistency-check.ps1` 의 CRLF/술어 결함**(위 참조). `.claude/hooks/` = 분류기 차단 구역 → 패치를 사람이 적용해야 한다.
