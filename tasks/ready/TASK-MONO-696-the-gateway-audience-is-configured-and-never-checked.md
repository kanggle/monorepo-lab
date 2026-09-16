# Task ID

TASK-MONO-696

# Title

🔴 게이트웨이의 `audiences:` 는 **설정돼 있고 한 번도 검사되지 않는다** — 계약서는 「`aud` 가 자기 플랫폼이 아니면 거절」, IdP 는 `aud` 에 **client id** 를 넣고, 6 게이트웨이 중 어디도 `aud` 를 안 본다 (보안 하드닝 · 설계 결정 대기)

# Status

ready

# Owner

monorepo

# Task Tags

- security
- gateway
- lib
- contract

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus (AC-0·AC-1 측정과 결정 전까지는 구현 없음. 결정 후 실행은 선택지에 따라 — B·C 는 Sonnet 가능, A 는 IdP 발급 경로 변경이라 Opus)
>
> 🔴 **이 티켓은 보안 하드닝이고, 조이면 지금 통과하던 토큰이 거절된다.** 영향 반경(blast radius)을 **먼저 재고** 그 다음에 결정한다. 설계는 이 티켓이 정하지 않는다 — § 결정 대기 는 **소유자 결정**이고, 거기 적힌 추천은 추천일 뿐이다.

# Goal

`TASK-BE-595`(PR #3861, 2026-09-16 UTC)의 구현자가 ecommerce `gateway-service` 의 실제 디코더 경로에서 **`aud` 없음 + `tenant_id=ecommerce` 토큰 → 200** 을 실측했다. 그 티켓은 `wrongAudience…` 테스트가 실은 테넌트 부재를 재고 있었음을 밝혀 이름만 `protectedRoute_noAudienceNoTenant_returns403TenantForbidden` 으로 바꿨고, audience 미검증은 **범위 밖이라 기안하지 않았다**(그 티켓 § 216–219행). 소유자가 후속 티켓을 요청했다 — 이것이 그것이다.

이 티켓의 목표는 **① 그 관측을 재측정해 굳히고, ② 누가 무엇을 들고 오는지(토큰 모집단)를 재고, ③ 계약서·IdP·게이트웨이 세 곳이 서로 다른 말을 하는 상태를 소유자 결정으로 하나로 만들고, ④ 그 결정을 집행하는 것**이다.

## 측정 (2026-09-16 UTC, 코드 읽기 — 실행 아님. `origin/main` `34bf34d7d`)

### (a) 왜 `audiences:` 가 효과가 없나

- Spring Boot 의 `spring.security.oauth2.resourceserver.jwt.audiences` 는 **Boot 가 자동 구성한 디코더에만** 적용된다. 자동 구성은 `ReactiveJwtDecoder` 빈이 이미 있으면 물러난다.
- 6 게이트웨이는 전부 자기 `OAuth2ResourceServerConfig#reactiveJwtDecoder()` 빈을 만든다(`GatewayJwtDecoders.nimbus(jwkSetUri, jwtTokenValidator())`). ⇒ 그 속성은 **읽히지 않는다**.
- 공유 검증기 사슬 `libs/java-gateway/src/main/java/com/example/apigateway/security/GatewayJwtDecoders.java:61-70` `validatorChain` 은 `JwtTimestampValidator` → `AllowedIssuersValidator` → tenant gate → `JwtValidators.createDefault()` 이다. **audience 검증기가 없고**, `createDefault()`(Spring Security 6.4 / Boot 3.4.1 — `gradle.properties:5`)도 `aud` 를 보지 않는다.

### (b) 게이트웨이별

| 게이트웨이 | 디코더 조립 | audience 검증기 | 설정된 audience 값 | 비고 |
|---|---|---|---|---|
| ecommerce | `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/config/OAuth2ResourceServerConfig.java:51-61` → `GatewayJwtDecoders.validatorChain` | **없음** | `application.yml:23` `audiences: ecommerce` — **죽은 설정** (테스트 `application-integration-test.yml:7` 도 같음) | BE-595 실측: aud 없음 → 200 |
| wms | `projects/wms-platform/apps/gateway-service/src/main/java/com/wms/gateway/config/OAuth2ResourceServerConfig.java:47-55` | **없음** | `application.yml:24` `audiences: wms` — **죽은 설정** | |
| scm | `projects/scm-platform/apps/gateway-service/src/main/java/com/example/scmplatform/gateway/config/OAuth2ResourceServerConfig.java:49-57` | **없음** | 없음 | |
| erp | `projects/erp-platform/apps/gateway-service/src/main/java/com/example/erp/gateway/config/OAuth2ResourceServerConfig.java:47-55` | **없음** | 없음 | |
| finance | `projects/finance-platform/apps/gateway-service/src/main/java/com/example/finance/gateway/config/OAuth2ResourceServerConfig.java:47-55` | **없음** | 없음 | |
| fan | `projects/fan-platform/apps/gateway-service/src/main/java/com/example/fanplatform/gateway/config/OAuth2ResourceServerConfig.java:49-57` | **없음** | 없음 | |

게이트웨이 뒤, 또는 게이트웨이 밖에서 JWT 를 직접 검증하는 곳:

| 표면 | 위치 | audience 검증 |
|---|---|---|
| finance `account-service` | `projects/finance-platform/apps/account-service/src/main/java/com/example/finance/account/infrastructure/security/ServiceLevelOAuth2Config.java:55-75` (자체 `JwtDecoder` 빈, 사슬은 게이트웨이와 같은 4단) | **없음** |
| 그 밖의 `ServiceLevelOAuth2Config` 13개 (erp 4 · fan 4 · finance ledger · scm 4) | 각 서비스 `…/ServiceLevelOAuth2Config.java` | **없음** — `src/main` 전체 grep 에서 audience 검증기를 가진 곳은 ecommerce `order-service`(`OrderSecurityConfig.java:88` `AudienceValidator`, 기본값 **빈 문자열 = 통과**, `application.yml:82`)와 iam `admin-service`(subject token 한정, 아래)뿐 |
| platform-console `console-bff` | `projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/infrastructure/security/SecurityConfig.java:93-94` `.jwt(jwt -> {})` — Boot 자동 구성 디코더, `application.yml:20-25` 에 `audiences` 없음 | **없음** (issuer·서명·시간만) |
| iam `gateway-service` (7번째 엣지, 참고) | `projects/iam-platform/apps/gateway-service/src/main/java/com/example/gateway/security/TokenValidator.java:128` `new Rs256JwtVerifier(publicKey)` — 1-인자 생성자, `expectedAudience=null` | **없음** (`libs/java-security/.../Rs256JwtVerifier.java:52,67-68` 에 3-인자 판은 있다) |
| iam `admin-service` subject-token 검증 | `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/infrastructure/security/IamOidcJwksSubjectTokenValidator.java:91` `.requireAudience(...)`, 값 `application.yml:132` `${OIDC_CONSOLE_CLIENT_ID:platform-console-web}` | **있음** — 값이 **client id** 다 |

### (c) IdP 가 실제로 넣는 `aud`

- `projects/iam-platform/apps/auth-service/src/main/java` 전체에 `aud` 를 설정하는 코드(`.audience(`, `claim("aud"`)가 **없다**. `AuthorizationServerConfig.java:460` 의 Spring Authorization Server `JwtGenerator` 기본값이 그대로 나간다 — access token 의 `aud` = **등록된 client 의 `client_id`** (프레임워크 기본 동작; 이 저장소 코드로는 덮어쓰지 않음).
- 실측 근거가 있는 것: **assume-tenant 토큰** — `AssumeTenantExchangeIntegrationTest.java:472-474` 가 `aud` 에 `platform-console-web` 이 들어 있음을 단언한다. `docs/adr/ADR-MONO-060-assumed-token-subject-identity.md:14` 실측 표도 `aud = "platform-console-web"`.
- 🔴 이름 함정: RFC 8693 교환의 **`audience` 요청 파라미터**는 선택된 **tenant id** 를 나른다(`projects/iam-platform/specs/contracts/http/auth-api.md:144,154`). 그 값은 `tenant_id` 클레임이 되고 **`aud` 클레임이 되지 않는다**.

| 토큰 모집단 | 발급 client (시드) | 예상 `aud` | 근거 수준 |
|---|---|---|---|
| 콘솔 base 토큰 (`authorization_code`) | `platform-console-web` (`V0015__seed_platform_console_oidc_client.sql:85`) | `platform-console-web` | 프레임워크 기본 + admin-service 가 그 값을 요구하며 동작 중 |
| 콘솔 assume-tenant 토큰 (`token_exchange`) | `platform-console-web` | `platform-console-web` | **IT 단언** |
| web-store 소비자 토큰 | `ecommerce-web-store-client` (`V0012:59`) | `ecommerce-web-store-client` | 프레임워크 기본 (디코드 실측 아님) |
| ecommerce 관리 대시보드 | `ecommerce-admin-dashboard-client` (`V0012:103`) | 그 client id | 프레임워크 기본 |
| fan 사용자 | `fan-platform-user-flow-client` (`V0011:54`) | 그 client id | 프레임워크 기본 |
| wms 사용자 | `wms-user-flow-client` (`V0010:51`) | 그 client id | 프레임워크 기본 |
| 내부 `client_credentials` | `wms-internal-services-client`(`V0010:91`) · `scm-platform-internal-services-client`(`V0013:61`) · `finance-platform-internal-services-client`(`V0017:64`) · `erp-platform-internal-services-client`(`V0018:66`) · `community-service-client`(`V0009:49`) · `admin/auth/security/account-service-client`(`V0019`) | 각 client id | 프레임워크 기본. scm 게이트웨이 테스트 헬퍼도 `aud = scm-platform-internal-services-client` 로 민팅(`JwtTestHelper.java:37,117`) |

⇒ **IdP 가 `aud` 에 플랫폼 이름(`ecommerce`, `wms` …)을 넣는 경로는 코드상 없다.** 그런데 ecommerce·wms 게이트웨이의 테스트 헬퍼는 `aud: ecommerce` / `aud: wms` 를 민팅한다(`ecommerce …/testsupport/JwtTestHelper.java:75,98,168,208`, `wms …/testsupport/JwtTestHelper.java:193`) — **테스트는 계약서를 모델링하고, 운영 토큰은 그렇지 않다.**

console-bff 는 **한 개의** IAM OIDC access token 을 WMS·SCM·FINANCE·ERP·ECOMMERCE 5 도메인에 팬아웃한다(`projects/platform-console/apps/console-bff/src/main/java/com/kanggle/platformconsole/bff/domain/credential/DomainTarget.java` Javadoc, ADR-MONO-017 D4.A). ⇒ 한 토큰이 5 게이트웨이를 통과해야 한다.

### (d) 적힌 요구사항 — **있다** (그리고 지금 IdP·게이트웨이 둘 다와 어긋난다)

- `platform/contracts/jwt-standard-claims.md:46` — *"**Audience Scoping:** Each access token carries a platform-specific `aud` claim; gateways reject tokens with mismatched `aud`"*
- 같은 파일 `:58` — `aud` Required, *"Target platform audience — must match gateway's own platform"*, 예 `ecommerce`, `fan`, `wms`, `erp`, `mes`, `scm`
- 같은 파일 `:130` — *"5. **Validate audience:** Reject if `aud` does not match the gateway's own platform identifier"*
- 같은 파일 `:166` — *"Wrong `aud`, or no role valid for the requested surface: respond with HTTP 403 Forbidden"* (🔵 401 이 아니라 **403**)
- `platform/service-types/identity-platform.md:78` — *"The `aud` claim MUST identify the target platform (e.g., `wms`, `ecommerce`). A token issued for one platform is invalid for another."* · `:225` — *"Validate every incoming bearer token: signature (via `kid`), `iss`, `aud` (matches its own platform), `exp`, `nbf` (if present)."* · `:283` 음성 테스트 목록에 *"mismatched `aud` rejected"*
- `platform/api-gateway-policy.md`, `platform/security-rules.md` 에는 audience 문장이 **없다**.

⇒ **HARDSTOP-06 급 불일치**(계약서 ↔ IdP 발급 ↔ 게이트웨이 검증). 그래서 이 티켓은 구현 전에 결정이 필요하다.

---

# Scope

## In Scope

- AC-0 재측정(실제 디코더 경로 테스트)과 AC-1 토큰 모집단 실측
- 소유자 결정(§ 결정 대기)의 기록
- 결정된 선택지의 집행: `libs/java-gateway` `GatewayJwtDecoders`(필요 시 필수 파라미터화), 6 게이트웨이 `OAuth2ResourceServerConfig`·`application.yml`, 테스트 헬퍼, 그리고 **선택지에 따라** `platform/contracts/jwt-standard-claims.md` · `platform/service-types/identity-platform.md` 개정 또는 IdP 발급 변경
- 죽은 `audiences:` 속성의 처분(ecommerce·wms 본 설정 + ecommerce IT 설정) — 어느 선택지든 «설정돼 있는데 안 읽힘» 상태는 남기지 않는다

## Out of Scope

- 서비스 레벨 디코더 14개(`ServiceLevelOAuth2Config`)와 console-bff 의 audience — **측정 표에는 넣었지만** 게이트웨이 결정이 난 뒤 같은 원칙을 따를지 별도 티켓으로 정한다(AC-6 이 그 후속을 기안한다). 한 PR 에 묶으면 영향 반경이 두 배가 된다.
- iam `gateway-service`(7번째 엣지) — 검증기 구현이 다르다(`Rs256JwtVerifier`). 같은 결정을 따를지는 AC-6 후속에서.
- tenant gate / role admission 동작 변경(`TASK-BE-595`, `TASK-MONO-388` 이 정한 것)
- 발급자 allowlist(legacy `iam` issuer deprecation window) 정리

---

# 결정 대기 (소유자)

**질문:** 각 게이트웨이는 `aud` 로 무엇을 요구해야 하는가 — 그리고 그 대답에 맞게 계약서와 IdP 중 **어느 쪽을 고치는가**.

| 선택지 | 내용 | 얻는 것 | 대가 / 깨지는 토큰 |
|---|---|---|---|
| **A. 계약서대로** — IdP 가 플랫폼 이름을 `aud` 에 넣고 게이트웨이가 자기 플랫폼을 고정 | IdP 토큰 커스터마이저가 client→platform 매핑(`RoleSeedPolicy` 가 이미 client-platform 을 키로 쓴다)으로 `aud` 를 채움. 게이트웨이는 `aud ∋ <own platform>` | 계약서 무개정. 한 도메인 토큰을 다른 도메인 게이트웨이에 재생하는 것을 엣지에서 막는다 | 🔴 **console-bff 한 토큰이 5 도메인으로 팬아웃**한다 ⇒ `aud` 를 다중값(진입 가능한 도메인 전부)으로 하거나 도메인별 교환으로 바꿔야 한다 — 다중값이면 콘솔 토큰에 대해 이득이 거의 사라진다. IdP 발급 경로 변경 + 모든 게이트웨이 동시 전환(원자 PR 또는 IdP 선행 이중 `aud`). admin-service 의 `requireAudience(platform-console-web)` 도 함께 바뀌어야 한다. 가장 큼. |
| **B. 현실대로** — 게이트웨이별 **client id allowlist** | `validatorChain` 에 `allowedAudiences` 를 **필수** 인자로 추가(빈 목록 = 기동 실패, `AllowedIssuersValidator` 와 같은 fail-closed). 예: ecommerce = `ecommerce-web-store-client`, `ecommerce-admin-dashboard-client`, `platform-console-web`, (내부 client …) | IdP 무변경. 등록되지 않은 client(예: 다른 도메인 사용자 SPA, 새로 등록된 client)의 토큰을 엣지에서 거절. 공유 사슬에 넣으면 «한 게이트웨이가 잊는» 실패가 구조적으로 불가 | 계약서 `:46,:58,:130` + `identity-platform.md:78,:225` 개정 필요(`aud` = client). `platform-console-web` 은 6 목록에 모두 들어가므로 콘솔 토큰의 교차 도메인 재생은 여전히 tenant gate·roles 가 막는다(엣지 `aud` 로는 못 막음). client 추가 시 게이트웨이 설정도 같이 바꿔야 하는 결합이 생긴다 — **빠뜨리면 새 client 가 전 게이트웨이에서 401/403**. |
| **C. 검증하지 않음을 계약으로** | 계약서·서비스타입 문서를 «게이트웨이 경계는 issuer + tenant gate + roles, `aud` 는 검증하지 않는다» 로 개정. 죽은 `audiences:` 삭제, 테스트 헬퍼가 현실적인 `aud` 를 민팅 | 런타임 위험 0. 문서가 참이 된다 | 보안 태세를 **올리지 않는다**. 같은 IdP 가 서명한 다른 client 의 토큰이 계속 엣지를 통과(tenant/role 이 맞으면). |
| **D. 존재만 요구** | `aud` 비어 있지 않음만 검사 | 싸다 | 보안 가치 거의 없음(IdP 는 늘 client id 를 넣는다). BE-595 의 «aud 없음 → 200» 만 닫는다. |

🔵 **추천(결정 아님):** **B, 단 2단계.** ① 먼저 `aud` 를 **거절하지 않고 기록만** 하는 섀도 모드(불일치 시 로그·메트릭)로 6 게이트웨이에 배포해 AC-1 모집단을 운영/데모 트래픽으로 확인하고, ② 불일치 0 을 확인한 뒤 거절로 전환. 이유: A 는 콘솔 팬아웃 구조와 정면으로 부딪혀 이득 대비 변경이 가장 크고, C 는 문서만 맞추고 태세는 그대로이며, B 는 IdP 를 건드리지 않고 공유 사슬 한 곳에서 6 게이트웨이를 동시에 닫는다. 🔴 단 B 를 택하면 계약서 개정이 **같은 PR 에서 먼저** 들어가야 한다(스펙이 이긴다 — 스펙과 반대로 구현하지 않는다).

부속 결정(어느 선택지든):

- **거절 상태코드** — 계약서 `:166` 은 wrong `aud` → **403**. 디코더 검증기 실패는 기본적으로 **401**(`invalid_token`) 이 된다. BE-595 가 tenant 거절을 403 `TENANT_FORBIDDEN` 으로 매핑한 방식을 따를지, 401 로 두고 계약서를 고칠지.
- **섀도 모드의 유무와 기간**

---

# Acceptance Criteria

- [ ] **AC-0 재측정 (고치기 전에)** — ecommerce `SecurityConfigRealDecoderPathTest`(프로덕션 `OAuth2ResourceServerConfig#reactiveJwtDecoder()` 를 그대로 쓰는 하네스)에 두 칸을 추가해 **현 상태를 단언**한다: (i) `aud` 없음 + `tenant_id=ecommerce` → **통과**, (ii) `aud = ["wms"]`(다른 값) + `tenant_id=ecommerce` → **통과**. 🔴 두 칸 모두 **대조군**을 둔다 — 같은 토큰에서 `tenant_id` 만 빼면 403 이 되어야 한다(그래야 «통과» 가 디코더가 토큰을 실제로 받아들였다는 뜻이다; 하네스가 아무 토큰이나 통과시키는 게 아님). 6 게이트웨이 중 최소 한 곳(wms — `audiences: wms` 가 설정돼 있는 다른 한 곳)에서도 같은 (i) 칸을 잰다. **재현되지 않으면** 이 티켓의 전제가 틀린 것이다 — 구현하지 말고 사유를 기록한다. 이 칸들은 결정 집행 시 기대값이 뒤집히는 **bite 칸**이 된다.
- [ ] **AC-1 토큰 모집단 실측** — § 측정 (c) 표의 «프레임워크 기본» 행을 **실제 발급 토큰 디코드**로 확인한다(auth-service IT 에서 각 grant 로 발급해 `aud` 단언, 또는 로컬 스택에서 발급한 토큰 디코드). 최소: 콘솔 base, assume-tenant(이미 단언 있음), web-store, 내부 `client_credentials` 1종. 그리고 **각 게이트웨이에 실제로 도달하는 client 목록**을 표로 남긴다(console-bff 팬아웃, 도메인 SPA, 서비스 간 호출 중 게이트웨이를 경유하는 것). 🔴 «이 client 는 그 게이트웨이에 안 온다» 는 부재 판정은 grep 이 아니라 호출 경로(설정된 base URL)로 댄다.
- [ ] **AC-2 소유자 결정 기록** — § 결정 대기 에 소유자가 고른 선택지(A/B/C/D 또는 다른 것)와 부속 결정 2건(상태코드, 섀도 모드)을 **정확한 형태로** 기록한다. 결정 전에는 AC-3 이후를 시작하지 않는다.
- [ ] **AC-3 스펙 먼저** — 결정이 계약서와 다르면 `platform/contracts/jwt-standard-claims.md`(`:46`, `:58`, `:130`, `:166`, 예제 `:219` 이하)와 `platform/service-types/identity-platform.md`(`:78`, `:225`, `:283`)를 **구현보다 먼저** 같은 PR 안에서 개정한다. 결정이 A 면 IdP 계약(`projects/iam-platform/specs/contracts/http/auth-api.md`)의 발급 `aud` 도.
- [ ] **AC-4 집행 — 한 곳에서, 잊을 수 없게** — 검증은 `GatewayJwtDecoders.validatorChain`(또는 결정이 정한 공유 지점)에서 이루어지고, 게이트웨이가 audience 정책을 **생략할 수 없는** 시그니처여야 한다(선택지 C 제외). 6 게이트웨이 전부 적용. 죽은 `audiences:` 속성(ecommerce `application.yml:23`, wms `application.yml:24`, ecommerce `application-integration-test.yml:7`)은 삭제하거나 실제로 읽히게 한다 — **설정돼 있는데 안 읽히는 상태를 남기지 않는다**.
- [ ] **AC-5 bite** — AC-0 의 칸이 결정에 맞게 뒤집힌다(C 면 뒤집히지 않고 이름·주석이 «검증하지 않는다» 를 말한다). 추가로 (iii) 결정이 허용하는 `aud` → 통과, (iv) 허용하지 않는 `aud` → 결정된 상태코드. 🔴 **테스트 헬퍼가 운영과 같은 `aud` 를 민팅**하도록 고친다 — 지금처럼 헬퍼가 `aud: ecommerce` 를 민팅하면 «플랫폼 이름을 요구» 하는 구현이 **테스트는 초록, 운영은 전량 거절**이 된다. 각 게이트웨이의 기존 스위트 초록.
- [ ] **AC-6 후속 기안** — 서비스 레벨 디코더 14개 · console-bff · iam gateway 에 같은 원칙을 적용할지를 다루는 후속 티켓을 **이 티켓을 `done/` 으로 닫기 전에** 기안한다(또는 «적용하지 않는다» 는 결정을 사유와 함께 기록).

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Signing Strategy(`:46` Audience Scoping) · § Standard Claims(`:58`) · § JWT Validation(`:130`) · § Error Handling(`:166`)
- `platform/service-types/identity-platform.md` `:78`, `:225`, `:283`
- `platform/api-gateway-policy.md` (audience 언급 없음 — 개정 대상이 될 수 있음)
- `platform/shared-library-policy.md` (`libs/java-gateway` 변경)
- `docs/adr/ADR-MONO-060-assumed-token-subject-identity.md` (assume 토큰 실측 표)
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` D2-A (aud-scoped roles — `aud` 가 플랫폼이라는 전제 위에 쓰였다)
- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` S3 (client-platform 키 시드)
- `projects/ecommerce-microservices-platform/tasks/review/TASK-BE-595-tenant-rejection-reports-itself-as-401-so-a-permission-problem-reads-as-an-expired-session.md` `:216-219` (최초 관측)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/iam-platform/specs/contracts/http/auth-api.md` § token exchange (`audience` 파라미터 = tenant id, `:144`, `:154`)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (도메인별 outbound credential — 한 토큰의 팬아웃)

---

# Target Service

- `libs/java-gateway` (`GatewayJwtDecoders`)
- `gateway-service` × 6 — ecommerce · wms · scm · erp · finance · fan
- (선택지 A 일 때) iam `auth-service`, iam `admin-service`

# Implementation Notes

- AC-0 하네스는 이미 있다: `projects/ecommerce-microservices-platform/apps/gateway-service/src/test/java/com/example/gateway/config/SecurityConfigRealDecoderPathTest.java` — 프로덕션 설정 클래스로 디코더를 만든다. `GatewayIntegrationTest` 는 `jwk-set-uri` 만 목 서버로 바꾸고 디코더 빈은 프로덕션 것을 쓴다.
- `libs/java-security` 의 `AllowedIssuersValidator` 가 «빈 목록 = 거절» 로 fail-closed 인 선례다. audience allowlist 도 같은 성질이어야 한다(빈 목록이 «전부 허용» 으로 퇴화하면 안 된다 — ecommerce `order-service` 의 `AudienceValidator` 는 빈 값이면 **통과**시키는 반대 설계다. 복사하지 말 것).
- 섀도 모드를 택하면 **끄는 조건을 숫자로** 적는다(예: «N일 동안 불일치 로그 0건») — 날짜만 적힌 섀도 모드는 영구 섀도가 된다.
- 경로 필터: 이 변경은 6 프로젝트 + `libs/` 를 건드린다. 각 프로젝트 게이트웨이 스위트가 CI 에서 실제로 도는지(`changes` 필터) PR 에서 확인.

# Edge Cases

- **콘솔 팬아웃** — 한 토큰(`aud=platform-console-web`)이 5 도메인 게이트웨이를 지난다. 어느 게이트웨이 목록에서라도 빠지면 콘솔의 그 도메인 화면 전체가 401/403.
- **`aud` 다중값** — JWT `aud` 는 배열일 수 있다. 판정은 «교집합 비어 있지 않음» 인지 «정확히 하나» 인지 결정에 포함.
- **legacy issuer `iam`** 로 발급된 토큰(deprecation window)의 `aud` 형태가 SAS 토큰과 같은지 — AC-1 에서 확인.
- **데모/e2e 하네스가 직접 민팅한 토큰** — 게이트웨이 테스트 헬퍼, nightly e2e 픽스처, 데모 시드 스크립트가 만든 토큰의 `aud` 가 결정과 맞는지.
- **새 client 등록** — B 를 택하면 client 추가 PR 이 게이트웨이 목록을 함께 바꾸지 않으면 조용히 거절된다. 그 결합을 막는 가드가 필요한지(예: 시드 client ↔ 게이트웨이 목록 대조)는 집행 시 판단.
- 내부 `client_credentials` 토큰이 게이트웨이를 경유하는 경로와 서비스로 직행하는 경로가 둘 다 있다 — 게이트웨이만 조이면 직행 경로는 그대로다(Out of Scope 의 서비스 레벨 디코더, AC-6).

# Failure Scenarios

- 🔴 **테스트 초록 · 운영 전량 거절** — 헬퍼가 `aud: ecommerce` 를 민팅하는 채로 «플랫폼 이름 요구» 를 구현하면 모든 스위트가 통과하고 실제 IdP 토큰은 전부 거절된다. AC-5 의 헬퍼 교정 + AC-1 의 실제 발급 토큰 디코드가 막는다.
- **한 게이트웨이만 누락** — 공유 사슬에 넣지 않고 게이트웨이별로 붙이면 한 곳이 빠진다(`GatewayJwtDecoders` Javadoc 이 경고하는 바로 그 메커니즘). AC-4 의 «생략할 수 없는 시그니처» 가 막는다.
- **빈 allowlist 가 전부 허용으로 퇴화** — 속성 누락 시 기동 실패여야 한다.
- **상태코드 불일치** — 검증기 거절이 401 로 나가 콘솔이 «세션 만료» 로 읽는다(`TASK-PC-FE-292`/`TASK-BE-595` 가 막 고친 바로 그 사슬). 부속 결정이 이것을 명시적으로 정한다.
- **AC-0 이 재현되지 않음** — 전제가 틀림. 구현하지 않고 기록 후 소유자에게 되돌린다.

# Test Requirements

- 실제 디코더 경로 테스트(프로덕션 설정 클래스로 만든 디코더)에서 AC-0 (i)(ii) + 대조군, AC-5 (iii)(iv)
- 6 게이트웨이 기존 스위트 초록
- auth-service IT 에서 grant 별 발급 토큰 `aud` 단언(AC-1)
- 섀도 모드를 택하면 «불일치 → 통과 + 로그/메트릭 1» 단언

# Definition of Done

- [ ] AC-0 ~ AC-6
- [ ] 계약서 · IdP 발급 · 게이트웨이 검증 세 곳이 **같은 말**을 한다(어느 쪽으로 맞췄는지는 AC-2 결정)
- [ ] «설정돼 있는데 안 읽히는» `audiences:` 속성 0건
