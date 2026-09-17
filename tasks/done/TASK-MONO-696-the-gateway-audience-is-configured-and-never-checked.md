# Task ID

TASK-MONO-696

# Title

🔴 게이트웨이의 `audiences:` 는 **설정돼 있고 한 번도 검사되지 않는다** — 계약서는 「`aud` 가 자기 플랫폼이 아니면 거절」, IdP 는 `aud` 에 **client id** 를 넣고, 6 게이트웨이 중 어디도 `aud` 를 안 본다 (보안 하드닝 · 설계 결정 대기)

# Status

done

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

## 결정 (소유자, 2026-09-16 UTC) — AC-2

🔵 위 표·추천은 결정 전 기록으로 그대로 둔다. 아래가 소유자가 고른 것이고, **여기 적힌 것 외에는 결정되지 않았다.**

| 항목 | 결정 |
|---|---|
| 선택지 | **B** — 게이트웨이별 **client id allowlist** 로 `aud` 를 검사한다. IdP 발급은 **변경하지 않는다**. 계약서는 «`aud` = 발급 client id, 각 게이트웨이는 선언된 allowlist 를 받아들인다» 로 개정한다. |
| 롤아웃 | **2단계.** 1단계 = **섀도**(불일치를 거절하지 않고 로그 + 메트릭만) 를 **6 게이트웨이 전부**에 배포. 2단계 = 거절로 전환 — **별도 PR**, **실측 불일치 = 0 을 확인한 뒤에만**. |
| 섀도 기간 | **기간: 미정(불일치 0 실측이 전환 조건)** — 소유자가 기간을 정하지 않았다. |
| 거절 상태코드 | **403** (계약서 `:166`). 디코더 기본값 401 이 아니라, `TASK-BE-595` 의 `TENANT_FORBIDDEN` 처럼 **예외 원인 사슬을 훑어** 403 으로 매핑한다. |
| 오류 코드 이름 | **결정 아님.** 계약서 본문에 `AUDIENCE_FORBIDDEN` 을 **제안**으로만 적었다(`platform/contracts/jwt-standard-claims.md` § Error Handling) — 2단계 구현 PR 이 확정한다. |
| 이 PR 범위 | AC-2(기록) + AC-0 + AC-1 + AC-3(스펙). **AC-4/AC-5/AC-6 은 이 PR 이 아니다** — 검증기 구현·헬퍼 교정·죽은 속성 삭제 없음. 그래서 이 티켓은 `in-progress` 로 남는다. |

---

# Acceptance Criteria

- [x] **AC-0 재측정 (고치기 전에)** — ecommerce `SecurityConfigRealDecoderPathTest`(프로덕션 `OAuth2ResourceServerConfig#reactiveJwtDecoder()` 를 그대로 쓰는 하네스)에 두 칸을 추가해 **현 상태를 단언**한다: (i) `aud` 없음 + `tenant_id=ecommerce` → **통과**, (ii) `aud = ["wms"]`(다른 값) + `tenant_id=ecommerce` → **통과**. 🔴 두 칸 모두 **대조군**을 둔다 — 같은 토큰에서 `tenant_id` 만 빼면 403 이 되어야 한다(그래야 «통과» 가 디코더가 토큰을 실제로 받아들였다는 뜻이다; 하네스가 아무 토큰이나 통과시키는 게 아님). 6 게이트웨이 중 최소 한 곳(wms — `audiences: wms` 가 설정돼 있는 다른 한 곳)에서도 같은 (i) 칸을 잰다. **재현되지 않으면** 이 티켓의 전제가 틀린 것이다 — 구현하지 말고 사유를 기록한다. 이 칸들은 결정 집행 시 기대값이 뒤집히는 **bite 칸**이 된다.
      - **재현됨 (2026-09-16 UTC).** 전제가 맞다 — 두 게이트웨이 모두 `aud` 를 보지 않는다.
      - ecommerce: `SecurityConfigRealDecoderPathTest$AudienceNotCheckedToday` 4칸 — `noAudience_ecommerceTenant_passesToday_flipsInAc5`(200 `reached`) · `noAudience_control_withoutTenant_is403`(403 `TENANT_FORBIDDEN`) · `foreignAudience_ecommerceTenant_passesToday_flipsInAc5`(`aud=["wms"]` → 200 `reached`) · `foreignAudience_control_withoutTenant_is403`(403 `TENANT_FORBIDDEN`).
        명령 `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:test --tests "com.example.gateway.config.SecurityConfigRealDecoderPathTest*"` → **rc=0**, 이 nested 판 `tests="4" failures="0" errors="0"`.
      - wms: 이런 하네스가 **없었다** → ecommerce 판을 최소로 옮긴 새 `projects/wms-platform/apps/gateway-service/src/test/java/com/wms/gateway/config/SecurityConfigRealDecoderPathTest.java` — wms 가 실제로 등록하는 공유 `com.example.apigateway.config.SecurityConfig` 필터 체인 + 프로덕션 `OAuth2ResourceServerConfig#reactiveJwtDecoder()` + MockWebServer JWKS 실제 RS256 검증. 디코더 목 없음, Docker 없음. 칸 `noAudience_wmsTenant_passesToday_flipsInAc5`(200 `reached`) · `noAudience_control_withoutTenant_is403`(403 `TENANT_FORBIDDEN`).
        명령 `./gradlew :projects:wms-platform:apps:gateway-service:test --tests "com.wms.gateway.config.SecurityConfigRealDecoderPathTest"` → **rc=0**, `tests="2" failures="0" errors="0"`.
      - 🔵 칸 이름·주석이 «AC-5 phase 2 에서 403 으로 뒤집힌다» 를 말한다. 1단계(섀도)에서는 여전히 통과 + 불일치 로그/메트릭이어야 한다(AC-5 가 그 단언을 더한다).
      - 모듈 전체: ecommerce gateway `test` **rc=0** · 128칸 실패 0 · wms gateway `test` **rc=0** · 42칸 실패 0 (둘 다 `test` 태스크 실제 실행, 캐시 아님). 🔴 `test` 는 `@Tag("integration")` 을 제외한다 — Docker IT 는 포함되지 않는다(이 호스트는 Docker 꺼짐, CI 가 권위).
- [x] **AC-1 토큰 모집단 실측** — § 측정 (c) 표의 «프레임워크 기본» 행을 **실제 발급 토큰 디코드**로 확인한다(auth-service IT 에서 각 grant 로 발급해 `aud` 단언, 또는 로컬 스택에서 발급한 토큰 디코드). 최소: 콘솔 base, assume-tenant(이미 단언 있음), web-store, 내부 `client_credentials` 1종. 그리고 **각 게이트웨이에 실제로 도달하는 client 목록**을 표로 남긴다(console-bff 팬아웃, 도메인 SPA, 서비스 간 호출 중 게이트웨이를 경유하는 것). 🔴 «이 client 는 그 게이트웨이에 안 온다» 는 부재 판정은 grep 이 아니라 호출 경로(설정된 base URL)로 댄다.
      - 증거와 표: 아래 **§ AC-1 실측**. 🔴 Testcontainers IT 에 넣은 단언은 **이 호스트에서 돌지 못했다**(Docker 꺼짐) — CI `iam` 통합 잡이 권위다. 로컬에서 실제로 돈 것은 Docker 없는 H2 슬라이스의 `client_credentials` 1칸뿐이다.
- [x] **AC-2 소유자 결정 기록** — § 결정 대기 에 소유자가 고른 선택지(A/B/C/D 또는 다른 것)와 부속 결정 2건(상태코드, 섀도 모드)을 **정확한 형태로** 기록한다. 결정 전에는 AC-3 이후를 시작하지 않는다.
      - § 결정 대기 › **결정 (소유자, 2026-09-16 UTC) — AC-2** 표.
- [x] **AC-3 스펙 먼저** — 결정이 계약서와 다르면 `platform/contracts/jwt-standard-claims.md`(`:46`, `:58`, `:130`, `:166`, 예제 `:219` 이하)와 `platform/service-types/identity-platform.md`(`:78`, `:225`, `:283`)를 **구현보다 먼저** 같은 PR 안에서 개정한다. 결정이 A 면 IdP 계약(`projects/iam-platform/specs/contracts/http/auth-api.md`)의 발급 `aud` 도.
      - **계약서는 이제 B 를 말한다.** 상세와 AC-4/5 가 맞춰야 할 목록: 아래 **§ AC-3 스펙 개정**. 결정이 A 가 아니므로 `auth-api.md` 는 건드리지 않았다.
- [x] **AC-4 집행 — 한 곳에서, 잊을 수 없게** — 검증은 `GatewayJwtDecoders.validatorChain`(또는 결정이 정한 공유 지점)에서 이루어지고, 게이트웨이가 audience 정책을 **생략할 수 없는** 시그니처여야 한다(선택지 C 제외). 6 게이트웨이 전부 적용. 죽은 `audiences:` 속성(ecommerce `application.yml:23`, wms `application.yml:24`, ecommerce `application-integration-test.yml:7`)은 삭제하거나 실제로 읽히게 한다 — **설정돼 있는데 안 읽히는 상태를 남기지 않는다**.
      - **1단계(SHADOW) 집행 (2026-09-17 UTC).** 상세: 아래 **§ AC-4 집행**. 요지 — `validatorChain(allowedIssuers, AllowedAudiencesValidator audienceGate, tenantGate)`: 구체 타입의 **필수** 인자(2-인자 판 삭제) · 6 게이트웨이 전부 적용 · allowlist 빈/부재 = 기동 실패 · 출하 모드 6/6 `SHADOW` · 죽은 `audiences:` 3곳 삭제(잔존 0 — `projects/**` 에서 `^\s*audiences:` 는 `done/` 티켓 본문 2곳뿐).
- [x] **AC-5 bite** — AC-0 의 칸이 결정에 맞게 뒤집힌다(C 면 뒤집히지 않고 이름·주석이 «검증하지 않는다» 를 말한다). 추가로 (iii) 결정이 허용하는 `aud` → 통과, (iv) 허용하지 않는 `aud` → 결정된 상태코드. 🔴 **테스트 헬퍼가 운영과 같은 `aud` 를 민팅**하도록 고친다 — 지금처럼 헬퍼가 `aud: ecommerce` 를 민팅하면 «플랫폼 이름을 요구» 하는 구현이 **테스트는 초록, 운영은 전량 거절**이 된다. 각 게이트웨이의 기존 스위트 초록.
      - 상세: 아래 **§ AC-5 실측**. 요지 — 1단계라 AC-0 칸은 **상태코드가 아니라 단언이** 뒤집혔다(200 유지 + `mismatch_shadowed +1`). (iii)/(iv) 는 ENFORCE 를 **테스트 한정**으로 켠 실제 디코더 경로 칸(ecommerce 자체 entry point · 공유 entry point via wms)에서 403 `AUDIENCE_FORBIDDEN`. 헬퍼 6개 모두 운영 client id 민팅. lib + 6 게이트웨이 `check` **rc=0**. bite: 검증기를 항상-통과로 → lib 98칸 중 9 · ecommerce 147칸 중 5 · wms 60칸 중 5 빨강.
- [x] **AC-6 후속 기안** — 서비스 레벨 디코더 14개 · console-bff · iam gateway 에 같은 원칙을 적용할지를 다루는 후속 티켓을 **이 티켓을 `done/` 으로 닫기 전에** 기안한다(또는 «적용하지 않는다» 는 결정을 사유와 함께 기록).
      - `TASK-MONO-697`(2단계 — 6 게이트웨이 ENFORCE 전환, AC-0 = 실측 불일치 0 **및 분모 match > 0**, 읽을 곳 명시, `AUDIENCE_FORBIDDEN` 이름 확정 포함) · `TASK-MONO-698`(서비스 레벨 14 · console-bff · iam gateway — 측정 먼저, 결정은 소유자). 둘 다 `tasks/ready/`.

---

# AC-1 실측 (2026-09-16 UTC)

## (a) grant 별 실제 발급 토큰의 `aud`

| 토큰 모집단 | grant | 발급 client | 단언 위치 | 기대 `aud` | 이 호스트에서 돌았나 |
|---|---|---|---|---|---|
| 내부 `client_credentials` (Docker 없는 판) | `client_credentials` | `test-internal-client`(슬라이스가 심는 행) | `OAuth2AuthorizationServerSliceTest.java:278-291` — 실제 SAS `JwtGenerator` + `TenantClaimTokenCustomizer`, H2 | `["test-internal-client"]` | ✅ **돌았다** — `:auth-service:test --tests "…OAuth2AuthorizationServerSliceTest"` **rc=0**, `tests="14" failures="0"`. `aud` = 발급 client id **실측** |
| GAP 내부 워크로드 | `client_credentials` | `account-service-client`(V0019 시드) | `OAuth2AuthorizationServerIntegrationTest.java:310-314` (JWKS 서명 검증 후 `jwt.getAudience()`) | `["account-service-client"]` | ⚪ Testcontainers IT — **로컬 불가**(Docker 꺼짐, `docker info` rc=1). CI 통합 잡이 권위 |
| 도메인 워크로드 (fan) | `client_credentials` | `community-service-client`(V0009/V0032 시드) | `OAuth2AuthorizationServerIntegrationTest.java:453-456` | `["community-service-client"]` | ⚪ 같음 |
| 콘솔 base | `authorization_code` (form-login + PKCE) | `platform-console-web`(V0015 시드) | `FormLoginIntegrationTest.java:223-236` | `["platform-console-web"]` | ⚪ 같음 |
| 콘솔 assume-tenant | `token_exchange` | `platform-console-web` | `AssumeTenantExchangeIntegrationTest.java:472-474` (기존) | `aud` ∋ `platform-console-web` | ⚪ 같음(기존 단언, 이 PR 은 손대지 않음) |
| web-store 소비자 | `authorization_code` (소셜 로그인 세션 → 시드 client 로 재-authorize, `client_secret_basic` + PKCE) | `ecommerce-web-store-client`(V0012 시드, 콜백 `/api/auth/callback/iam` V0024) | `SocialLoginSasBrowserIntegrationTest.java:343-386` (+ 같은 흐름의 IT 공개 client 토큰 `:338-341`) | `["ecommerce-web-store-client"]` | ⚪ 같음 |

- 🔴 **위 ⚪ 다섯 줄은 «단언을 썼다» 이지 «초록을 봤다» 가 아니다.** 컴파일은 로컬에서 확인했다(`:auth-service:test` 가 같은 소스셋을 컴파일 — **rc=0**, 700칸 실패 0 · 28 skip; 🔴 `test` 는 `@Tag("integration")` 을 제외하므로 이 IT 들은 **실행되지 않았다**). 판정은 이 PR 의 CI `iam` 통합 잡이 한다.
- 🔵 web-store 칸은 IT 스탠드인 client 가 아니라 **시드된 실제 client** 로 발급한다 — 같은 세션(같은 계정·같은 테넌트 `ecommerce`)을 재사용하므로 추가 스텁 없이 web-store 발급 경로만 다르다. 이 칸이 CI 에서 빨개지면 원인 후보는 `aud` 가 아니라 발급 경로(시드 시크릿·콜백 등록)일 수 있다 — 먼저 어느 줄에서 실패했는지 본다.
- 🔵 legacy issuer `iam` 토큰(Edge Case): `TASK-MONO-367`(2026-08-01 일몰, LANDED)로 게이트웨이 allowlist 에서 빠졌다 — 6 게이트웨이에 도달해 통과할 모집단이 아니므로 `aud` 형태를 따로 재지 않았다.
- 🔵 Nimbus 는 원소 하나짜리 `aud` 를 **문자열**로 직렬화한다 — JSON 페이로드 단언은 문자열/배열 둘 다 받아 값 집합으로 비교했다. 구현(AC-4)도 같은 이유로 «교집합» 을 단일 문자열에도 적용해야 한다(계약서 `:58` 이 이제 그렇게 적는다).

## (b) 각 게이트웨이에 실제로 도달하는 client — **측정 입력이지 결정이 아니다** (1단계 섀도 allowlist 의 제안값)

호스트명 → 게이트웨이: `wms.local`=wms `gateway-service`(`projects/wms-platform/docker-compose.e2e.yml:103`), `scm.local`=`scm-platform-gateway`(`projects/scm-platform/docker-compose.yml:41,68`), `erp.local`=`erp-platform-gateway`(`projects/erp-platform/docker-compose.yml:51,78` — 백엔드 넷은 Traefik 라벨 없음 `:42-47`), `finance.local`=`finance-platform-gateway`(`projects/finance-platform/docker-compose.yml:52,77`), `fan-platform.local`=`fan-platform-gateway`(`projects/fan-platform/docker-compose.yml:29,58`), `ecommerce.local`=`ecommerce-gateway-service`(`projects/ecommerce-microservices-platform/docker-compose.yml:1135,1203`). 데모는 `.local` → `${DEMO_DOMAIN}`.

| 게이트웨이 | 도달하는 client (제안 allowlist) | 호출 경로 근거 (설정된 base URL) | 도달하지 않는 것 — 근거 |
|---|---|---|---|
| **ecommerce** | `platform-console-web` · `ecommerce-web-store-client` | 콘솔: console-bff `CONSOLE_BFF_OUTBOUND_ECOMMERCE_BASE_URL` 기본 `http://ecommerce.local`(`console-bff/src/main/resources/application.yml:88`, 데모 `infra/demo/demo.env:236`), console-web `ECOMMERCE_ADMIN_BASE_URL` 기본 `http://ecommerce.local/api/admin` · `ECOMMERCE_PUBLIC_BASE_URL` `http://ecommerce.local/api`(`console-web/src/shared/config/env.ts:278-293`), client id `OIDC_CLIENT_ID` 기본 `platform-console-web`(`env.ts:60`). web-store: `API_URL_INTERNAL=http://gateway-service:8080` · `NEXT_PUBLIC_API_URL` 기본 `http://ecommerce.local`, client `ECOMMERCE_WEB_STORE_CLIENT_ID` 기본 `ecommerce-web-store-client`(`ecommerce…/docker-compose.yml:1238,1244,1267`) | `ecommerce-internal-services-client`(batch-worker): 호출 대상이 서비스 직행 — `product-service:8081`·`search-service:8085`·`order-service:8082`·`promotion-service:8092`(`batch-worker/src/main/resources/application.yml:76-85`). ⚪ `ecommerce-admin-dashboard-client`(V0012 시드): 등록 콜백이 `localhost:3001`·`admin.ecommerce.local`(V0012 `:110`)인데 **그 앱이 이 저장소에 없다** — 도달 여부를 설정으로 댈 수 없다(부재 판정 아님, **미측정**). |
| **wms** | `platform-console-web` | console-bff `CONSOLE_BFF_OUTBOUND_WMS_BASE_URL` 기본 `http://wms.local`(`application.yml:78`, 데모 `demo.env:230`); console-web `WMS_ADMIN_BASE_URL` 기본 `http://wms.local/api/v1/admin` · `WMS_OUTBOUND_BASE_URL` `http://wms.local/api/v1/outbound`(`env.ts:174-193`) | ⚪ `wms-user-flow-client`(V0010 시드): 이 client 로 설정된 앱이 저장소에 없다 — **미측정**. ⚪ `wms-internal-services-client`: 설정된 호출자 없음. 수동 1회 호출 기록만 있다(`infra/demo/wms-devseed.override.yml:23-24`, `/api/v1/master/warehouses` 403) — 설정된 트래픽이 아니므로 표에 넣지 않았다. |
| **scm** | `platform-console-web` | console-bff `CONSOLE_BFF_OUTBOUND_SCM_BASE_URL` 기본 `http://scm.local`(`application.yml:80`, `demo.env:231`); console-web `SCM_GATEWAY_BASE_URL` 기본 `http://scm.local`(`env.ts:209`) | ⚪ `scm-platform-internal-services-client`: 게이트웨이 컨테이너 env 에 `OIDC_INTERNAL_CLIENT_ID` 로 들어가지만(`scm…/docker-compose.yml:54`) 게이트웨이 `src/main` 은 그 키를 읽지 않는다(릴라잉 파티라 발급하지 않음). 이 client 로 scm 게이트웨이를 부르는 호출자는 설정에서 찾지 못했다 — **미측정**. |
| **erp** | `platform-console-web` | console-bff `CONSOLE_BFF_OUTBOUND_ERP_BASE_URL` 기본 `http://erp.local`(`application.yml:84`, `demo.env:233`); console-web `ERP_BASE_URL` 기본 `http://erp.local`(`env.ts:261`) | ⚪ `erp-platform-internal-services-client`: `.env.example` 에만 있다 — 설정된 호출자 **미측정**. |
| **finance** | `platform-console-web` | console-bff `CONSOLE_BFF_OUTBOUND_FINANCE_BASE_URL` 기본 `http://finance.local`(`application.yml:82`, `demo.env:232`); console-web `FINANCE_BASE_URL` · `LEDGER_BASE_URL` 기본 `http://finance.local`(`env.ts:224,245`) | ⚪ `finance-platform-internal-services-client`: `.env.example` 에만 있다 — **미측정**. 🔵 e2e 오버레이는 console-bff FINANCE 를 게이트웨이가 아닌 `http://finance-account-service:8080` 으로 돌린다(`projects/platform-console/docker-compose.e2e.yml:390`) — e2e 에서는 이 경로가 finance 게이트웨이를 **타지 않는다**. |
| **fan** | `fan-platform-user-flow-client` | fan-platform-web `OIDC_CLIENT_ID` 기본 `fan-platform-user-flow-client` · `GATEWAY_URL_INTERNAL=http://gateway-service:8080`(`projects/fan-platform/docker-compose.yml:326,332`), `NEXT_PUBLIC_GATEWAY_URL`/`GATEWAY_URL_INTERNAL` 사슬(`web/fan-platform-web/src/shared/config/env.ts:100-106,121`) | `platform-console-web`: console-bff 의 팬아웃 대상 열거가 `IAM, WMS, SCM, FINANCE, ERP, ECOMMERCE` 로 **fan 이 없다**(`console-bff/…/domain/credential/DomainTarget.java:22-29`), console-bff outbound base URL 도 그 여섯뿐(`application.yml:76-88`). `community-service-client`: 호출 대상이 서비스 직행 `ARTIST_SERVICE_BASE_URL` 기본 `http://artist-service:8080`(`community-service/src/main/resources/application.yml:126`). |

- 🔴 **⚪ 칸은 «안 온다» 가 아니라 «설정으로 대지 못했다» 이다.** 1단계 섀도가 바로 이것을 재는 장치다 — 섀도 로그에 이 client 들이 나타나면 allowlist 에 넣는 것이 2단계 전환 조건(불일치 0)의 일부가 된다.
- 🔴 **데모/e2e 하네스가 직접 민팅하는 토큰**(Edge Case)은 이 표에 없다 — 게이트웨이 테스트 헬퍼가 `aud: ecommerce`/`wms` 를 민팅하는 것은 AC-5 가 고친다.

# AC-3 스펙 개정 (2026-09-16 UTC) — 계약서는 이제 B 를 말한다

| 파일 | 줄 | 바뀐 것 |
|---|---|---|
| `platform/contracts/jwt-standard-claims.md` | `:46` | Audience Scoping → «`aud` = 토큰을 얻은 등록 client 의 id, 게이트웨이는 client allowlist 를 선언하고 교집합 없으면 거절» |
| 같음 | `:58` | `aud` 행: 타입 `string or string[]`, 뜻 = 발급 client id(모든 grant, assume-tenant 는 acting client), RFC 8693 `audience` 파라미터는 `tenant_id` 로 가지 `aud` 로 가지 않음, 단일 문자열 = 원소 하나 집합, 예시는 자리표시자 |
| 같음 | `:130-134` | 규칙 5: **`aud` 집합 ∩ allowlist ≠ ∅ 이면 통과**, 아니면 403 · allowlist 필수 · **빈/부재 = 기동 실패** · `aud` 없음 = 빈 집합 = 불통과 · 공유 검증기 사슬에 둔다 · 새 client 등록 시 allowlist 동시 갱신 · **섀도 단계**(거절 없이 로그+메트릭, 전환 조건 = 실측 불일치 0, 날짜 아님) |
| 같음 | `:170` | Error Handling: allowlist 불일치(섀도 밖) → **403**, 디코더 기본 401 을 사슬 훑기로 403 매핑. 오류 코드 이름 **제안** `AUDIENCE_FORBIDDEN` (결정 아님) |
| 같음 | `:223-337` | 예제 1-6 의 `aud` 를 플랫폼 이름 → **client id 자리표시자**로, 게이트웨이 동작 줄을 «allowlist 교집합» 으로. 예제 5 는 «allowlist 에 없는 client» + 섀도 단계 동작 |
| 같음 | `:23`, `:83`, `:85`, `:102`, `:104`, `:153`, `:281`, `:284` | 🔵 **요청 목록 밖 정합 수정** — «`aud` = 플랫폼» 을 전제로 쓴 문장(«A token is always for exactly one platform (`aud`)», «`aud` platform's roles», SSO «any platform (`aud`)» 등)을 «발급 client 의 플랫폼» 으로. 안 고치면 같은 파일이 `aud` 를 두 가지로 말한다 |
| 같음 | `:358` | Change log 항목(2단계 롤아웃·전환 조건 포함) |
| `platform/service-types/identity-platform.md` | `:78` | `aud` MUST = 발급 client id(플랫폼 이름 아님), allowlist 에 없는 edge 에서 무효 |
| 같음 | `:225` | 릴라잉 파티 규칙 3: `aud` 교집합 규칙 · allowlist 필수(빈 = 기동 실패) · 403 · 섀도 단계 허용 |
| 같음 | `:283` | 음성 테스트: allowlist 밖 `aud` → 403(섀도 중엔 통과 + 로그/메트릭) · `aud` 없음 거절 · 빈 allowlist 기동 실패 |
| 같음 | `:13`, `:149-150`, `:156-157`, `:244`, `:253`, `:279`, `:307` | 🔵 **요청 목록 밖 정합 수정** — 같은 전제(`aud`-scoping = 플랫폼)의 문장들 |

- HARDSTOP-03: 두 파일에 서비스 이름·구체 client id(`platform-console-web` 등)를 **넣지 않았다** — 자리표시자만. (두 파일에 원래 있던 플랫폼 이름 예시 `ecommerce`/`wms` 는 역할·경로 설명에 남아 있다.) `scripts/check-jwt-claims-registry.sh` **rc=0**(«all 6 claims … registered»).

**AC-4/AC-5 가 맞춰야 할 것 (계약서 기준):**

1. 검사 = `aud` 값 집합(단일 문자열 포함) **∩** 게이트웨이 allowlist ≠ ∅. «정확히 하나» 가 아니다.
2. allowlist 는 **필수 인자** — 빈 목록/부재는 **기동 실패**(`AllowedIssuersValidator` 와 같은 fail-closed; ecommerce `order-service` `AudienceValidator` 의 «빈 값 = 통과» 를 복사하지 말 것). `aud` 없는 토큰은 불통과.
3. **공유 사슬**(`GatewayJwtDecoders.validatorChain`)에 둬서 6 게이트웨이가 생략할 수 없게.
4. **1단계 섀도**: 불일치 → 거절 없음 + 로그(`jti`, `aud` 값, 게이트웨이) + 메트릭 1. 6 게이트웨이 전부. 테스트는 «불일치 → 통과 + 메트릭 +1».
5. **2단계 거절**(별도 PR, 실측 불일치 0 이후): 불일치 → **403**, BE-595 식 원인 사슬 훑기로 매핑(401 아님), 만료·서명·발급자 거절은 여전히 401. AC-0 의 `…_passesToday_flipsInAc5` 칸이 403 으로 뒤집힌다.
6. allowlist 초기값 = 위 § AC-1 (b) 표(측정 입력) + 섀도가 드러내는 ⚪ client.
7. 테스트 헬퍼는 **운영과 같은 `aud`(client id)** 를 민팅한다(`aud: ecommerce`/`wms` 금지).
8. 죽은 `audiences:` 속성 처분(AC-4) — 계약서는 그 속성을 요구하지 않는다.

# AC-4 집행 (2026-09-17 UTC) — 1단계 SHADOW

## 공유 사슬 (`libs/java-gateway`, 프로젝트 이름·client id 없음 — HARDSTOP-03)

| 조각 | 무엇 |
|---|---|
| `security/AllowedAudiencesValidator` (신규) | `new AllowedAudiencesValidator(String gateway, List<String> allowedAudiences, AudienceMode mode, MeterRegistry)`. 판정 = `jwt.getAudience()`(문자열/배열 → 목록) ∩ allowlist ≠ ∅. `aud` 없음 = 빈 집합 = 불일치. 대소문자 구분. **빈/null/공백뿐인 allowlist · 빈 gateway 이름 = `IllegalArgumentException`**(생성은 디코더 빈 안 → 기동 실패). SHADOW: 불일치 → `success()` + WARN `JWT audience not on allowlist: gateway={} mode={} jti={} aud={}`(값은 개행 제거·128자 절단) + 카운터. ENFORCE: 불일치 → `OAuth2Error("audience_mismatch")`. |
| 메트릭 | `gateway.jwt.audience`(Prometheus `gateway_jwt_audience_total`) — 태그 **정확히** `gateway` · `outcome` ∈ {`match`, `mismatch_shadowed`, `mismatch_rejected`}. `aud` 값은 태그가 아니다(토큰에서 온 값 → 카디널리티를 client 등록자에게 넘김) — 로그에만. `match` 도 센다: 트래픽 0 에서의 «불일치 0» 은 측정이 아니다(`TASK-MONO-697` AC-0 의 분모). |
| `security/AudienceMode` (신규) | `SHADOW` / `ENFORCE`, `parse()` 대소문자 무관. **기본값 없음** — null/빈/모르는 값 = `IllegalArgumentException`. |
| `GatewayJwtDecoders.validatorChain(List<String> allowedIssuers, AllowedAudiencesValidator audienceGate, OAuth2TokenValidator<Jwt> tenantGate)` | 🔴 2-인자 판 **삭제**. `audienceGate` 는 구체 타입 필수(null = NPE) — 게이트웨이가 no-op 람다를 넘길 수 없다. 반환 `AudienceCheckedChain`: 기존 4단(timestamp → issuer → tenant → defaults) 을 먼저 돌리고 **오류가 없을 때만** audience 게이트. 이유 — `DelegatingOAuth2TokenValidator` 는 모든 위임자를 돌려 오류를 모은다: 나란히 두면 (a) 섀도에서 이미 거절된 토큰(만료·위조 issuer·교차 테넌트)이 불일치로 세어져 전환 조건 숫자를 부풀리고, (b) ENFORCE 에서 issuer+aud 둘 다 틀린 토큰이 403 매핑에 걸려 rule 4 의 401 을 덮는다. |
| `GatewayErrorCodes.AUDIENCE_MISMATCH` = `"audience_mismatch"` · `AUDIENCE_FORBIDDEN` = `"AUDIENCE_FORBIDDEN"` | 🔴 **`AUDIENCE_FORBIDDEN` 은 계약서의 제안 이름 그대로다 — 소유자 확정 전.** 6 게이트웨이 모두 SHADOW 출하라 이 값은 아직 어떤 클라이언트에도 관측되지 않는다. 확정은 `TASK-MONO-697` AC-1. |
| 공유 `config/SecurityConfig` entry point (wms/scm/erp/finance/fan) | 기존 사슬 훑기(`extractOAuth2Error`)가 찾은 오류 코드가 `audience_mismatch` 면 403 `AUDIENCE_FORBIDDEN`. |
| testFixtures `ShippedAudienceConfig` (신규) | `shippedValue(key)` — 클래스패스 `application.yml` 을 **환경변수 무시**로 해석(`${VAR:default}` → default, default 없는 placeholder = 실패). `enforceOverrides(projectDir)` — 프로젝트의 `docker-compose*.yml`·`.env*` 에서 audience mode 를 ENFORCE 로 두는 줄. compose 파일 0개 = 실패(공허 방지). |

## 게이트웨이별 출하 설정

속성 키는 형제 `allowed-issuers` 와 같은 접두사, env 는 형제 `OIDC_ALLOWED_ISSUERS` 와 같은 모양: `<prefix>.oauth2.allowed-audiences: ${OIDC_ALLOWED_AUDIENCES:<목록>}` · `<prefix>.oauth2.audience-mode: ${OIDC_AUDIENCE_MODE:SHADOW}`. `OAuth2ResourceServerConfig` 생성자에 `@Value` **기본값 없는** 두 인자 + `MeterRegistry` 추가, `audienceGate()` 가 검증기를 만든다.

| 게이트웨이 | prefix | `GATEWAY_NAME` (메트릭·로그) | 출하 allowlist | 출하 mode |
|---|---|---|---|---|
| ecommerce | `ecommerce` | `ecommerce` | `platform-console-web`, `ecommerce-web-store-client` | SHADOW |
| wms | `wms` | `wms` | `platform-console-web` | SHADOW |
| scm | `scmplatform` | `scm` | `platform-console-web` | SHADOW |
| erp | `erpplatform` | `erp` | `platform-console-web` | SHADOW |
| finance | `financeplatform` | `finance` | `platform-console-web` | SHADOW |
| fan | `fanplatform` | `fan` | `fan-platform-user-flow-client` | SHADOW |

🔴 **⚪ client 를 allowlist 에 넣지 않은 이유 (의도, 누락 아님).** § AC-1 (b) 의 ⚪ 칸(`ecommerce-admin-dashboard-client` · `wms-user-flow-client` · `wms-internal-services-client` · `scm-platform-internal-services-client` · `erp-`/`finance-platform-internal-services-client`)은 «안 온다» 가 아니라 «설정으로 대지 못했다» 였다. 1단계는 **거절하지 않으므로** 그 client 가 실제로 오면 로그·메트릭에 불일치로 나타난다 — 그것이 1단계가 존재하는 이유인 측정이다. 미리 넣으면 그 측정이 사라지고, 2단계에서 «실제로 오는가» 를 판정할 근거도 사라진다. 넣을지 말지는 `TASK-MONO-697` AC-0 이 섀도 로그로 판정한다.

## 기동 실패와 SHADOW 전용 가드

- **기동 실패** — (a) 키 부재: `@Value("${<prefix>.oauth2.allowed-audiences}")` 기본값 없음 → placeholder 해석 실패. (b) 빈 값(`OIDC_ALLOWED_AUDIENCES=` 포함): `AllowedAudiencesValidator` IAE. (c) mode 부재/모르는 값: placeholder 실패 / `AudienceMode.parse` IAE. 6 게이트웨이 `AudienceShippedConfigTest$StartupFailure` 가 `ApplicationContextRunner` 로 넷 다 실측(+ 대조군: 유효 설정이면 `ReactiveJwtDecoder` 단일 빈).
  - 🔵 실측 중 발견: 맨 `ApplicationContextRunner` 에는 `PropertySourcesPlaceholderConfigurer` 가 없어 해석 불가 `${…}` 가 **자기 문자열 그대로 주입**된다 — 「키 부재」 칸이 한 원소짜리 allowlist 로 **엉뚱한 이유로 초록**이었다(첫 실행 rc=1 로 드러남). `PropertyPlaceholderAutoConfiguration` 추가 + 실패 메시지가 키 이름을 담는지 단언으로 고쳤다. 실제 부팅되는 게이트웨이는 그 자동 구성을 갖는다.
- **SHADOW 전용 가드** — 게이트웨이마다 `AudienceShippedConfigTest$Shipped`: ① `shippedValue("<prefix>.oauth2.audience-mode") == "SHADOW"` ② allowlist 가 위 표와 정확히 같다 ③ 프로젝트 `docker-compose*.yml`·`.env*` 에 ENFORCE override 줄 0. 2단계 PR 은 이 기대값을 **의도적으로** 바꾼다. compose·.env 는 모듈 `src/` 밖이라 각 게이트웨이 `build.gradle` 에 `tasks.named('test') { inputs.files(…) }` 로 **Gradle 입력 선언**(최상위 파일만, 재귀 없음) — 안 하면 compose 만 바꾼 변경에 `test` 가 UP-TO-DATE 로 남는다(`TASK-MONO-683` 사각).
  - 🔴 **가드 밖(기록):** `infra/demo/*.override.yml` · `.github/workflows/*` 의 env override 는 읽지 않는다. 프로젝트 밖 파일을 읽는 테스트는 Gradle 캐시와 프로젝트 PR 경로필터 **둘 다**에 안 보인다(`TASK-MONO-695`) — 처음엔 읽게 짰다가 그 이유로 뺐다. `TASK-MONO-697` Edge Cases 에 옮겨 적었다. 현재 그 파일들에 `AUDIENCE_MODE` 는 **0건**(grep).

## 데모 / compose env

- **변경 없음.** 출하 기본값이 `application.yml` 에 있어 env 를 안 주면 측정된 allowlist + SHADOW 로 뜬다. 6 게이트웨이의 compose(`projects/*/docker-compose*.yml`) · `infra/demo/demo.env` · `infra/demo/*.override.yml` · `tests/**` compose · `nightly-e2e.yml` 어디에도 `OIDC_ALLOWED_AUDIENCES` / `OIDC_AUDIENCE_MODE` 가 없다(grep 0) — 그래서 빈 값으로 덮어 기동이 깨질 경로도 없다. 데모 client id 도 기본값과 같다(`infra/demo` 에 `*CLIENT_ID` 설정 0 — `verify-demo-wrapper.sh` 주석만).
- 🔵 일부러 env 이름을 generic(`OIDC_…`)으로 둔 대가: `demo.env` 가 그 이름을 **전역으로** 정의하고 compose 가 `${OIDC_ALLOWED_AUDIENCES:-…}` 로 전달하기 시작하면 6 게이트웨이가 한 목록을 공유하게 된다(`TASK-MONO-554` 의 `OIDC_ALLOWED_ISSUERS` 결함과 같은 모양). 지금은 전달하는 compose 가 없어 무해.

## 스펙·문서

- ecommerce `specs/integration/iam-integration.md` — 설정 블록의 `audiences: ecommerce` 삭제 + 새 키, 검증 규칙 4(섀도·기동 실패·메트릭), Error Responses 행(`aud` 불일치 → ENFORCE 에서만 403 `AUDIENCE_FORBIDDEN`, 이름 = 제안), Migration Path 행에 «설정만 되고 적용된 적 없음» 주석.
- ecommerce gateway `overview.md`(2) · `dependencies.md` · `architecture.md`(2), wms `overview.md`, scm `overview.md` — 거짓이던 `aud=ecommerce`/`aud=wms-platform`/`aud=scm` 을 «`aud` ∩ client-id allowlist (섀도)» 로.
- scm · fan · erp · finance gateway `architecture.md` § JWT Validation — audience 항목 추가.
- `platform/**` 는 AC-3 에서 이미 개정 — 이 PR 에서 손대지 않았다.

## CI

- `libs/java-gateway`: `build-and-test` 의 `GRADLE_TASKS_CORE` 에 `:libs:java-gateway:check` **있음** — 공유 검증기 테스트는 CI 에서 돈다.
- 🔴 **발견: erp · finance `gateway-service` 의 `:check`(= Docker 없는 `test`)가 어떤 CI 목록에도 없었다.** 두 모듈은 `integrationTest`(통합 잡)만 돌았다 — `TenantClaimValidatorTest` 등 기존 단위 테스트도, 이 PR 의 `AudienceShippedConfigTest`(SHADOW 가드)도 PR 에서 **한 번도 안 돈다**. `ci.yml` `GRADLE_TASKS_FINANCE` / `GRADLE_TASKS_ERP` 에 각 `:projects:<p>:apps:gateway-service:check` 추가. 로컬 두 `check` rc=0 확인 후 추가. (wms · ecommerce · scm · fan 은 `GRADLE_TASKS_CORE` 에 이미 있음.)

# AC-5 실측 (2026-09-17 UTC)

## 추가·변경한 칸

| 스위트 | 칸 | 기대 |
|---|---|---|
| lib `AllowedAudiencesValidatorTest` (13) | 기동 실패 4(빈 · null/공백뿐 · 빈 gateway/null mode · 트림) · ENFORCE 5(허용 단일 · 배열 교집합 · 낯선 → `audience_mismatch`+`mismatch_rejected` · 없음 · 대소문자) · SHADOW 3(낯선/없음 → 통과+`mismatch_shadowed` · 허용 → `match`) · 메트릭 태그 = {gateway, outcome} 뿐 | 전부 초록 |
| lib `AudienceModeTest` (2) · `GatewayErrorCodesTest` (+1 와이어 값 핀) | | 초록 |
| lib `GatewayJwtDecodersTest` (7, 기존 5 개정 + 신규) | 사슬 구조(`AudienceCheckedChain` = base 4단 + 게이트) · audience 게이트 null = 실패 · 순서 4칸: ENFORCE 낯선 aud 만 → 오류 하나 · ENFORCE issuer+aud 틀림 → issuer 오류만(rejected 카운터 0) · SHADOW 테넌트 거절 토큰 → 카운터 0 · SHADOW 유효+낯선 → 통과+1 | 초록 |
| ecommerce `SecurityConfigRealDecoderPathTest$AudienceShadowed` (AC-0 칸 뒤집기) | `noAudience_ecommerceTenant_passesInShadow_andIsCounted`(200 + `mismatch_shadowed +1`, match +0) · `foreignAudience_…_andIsCounted`(`aud=["wms"]` → 200 + 1) · 대조군 2(테넌트 빼면 403 `TENANT_FORBIDDEN`, audience 카운터 +0) · (iii) 허용 aud → 200 + `match +1` | 초록 |
| ecommerce `SecurityConfigAudienceEnforceRealDecoderPathTest` (11, ENFORCE 테스트 한정) | 허용(web-store) 200 · 허용(console) 200 · 배열 교집합 200 · 낯선 aud → 403 `AUDIENCE_FORBIDDEN`(본문에 `UNAUTHORIZED` 없음, `mismatch_rejected +1`, `gateway_jwt_validation_failure_total{reason=audience_mismatch} +1`, `invalid +0`) · aud 없음 → 403 · 회귀 6: 만료(허용 aud) 401 · 만료+낯선 aud 401 · 서명 불일치 401 · 발급자 불일치+낯선 aud 401 · 토큰 없음 401 · 테넌트 불일치+낯선 aud → 403 `TENANT_FORBIDDEN`(rejected +0) | 초록 |
| wms `SecurityConfigRealDecoderPathTest` (4, AC-0 칸 뒤집기) | aud 없음 → 200 + `mismatch_shadowed +1` · 대조군(테넌트 없음 → 403, +0) · 낯선 aud → 200 + 1 · 헬퍼 기본 aud → 200 + `match +1` | 초록 |
| wms `SecurityConfigAudienceEnforceRealDecoderPathTest` (9, **공유 entry point**) | 허용 200 · 배열 200 · 낯선 → 403 `AUDIENCE_FORBIDDEN` (+1) · 없음 → 403 · 회귀 5: 만료 401 · 위조 서명 401 · 발급자 불일치 401 · 토큰 없음 401 · 테넌트 불일치 → 403 `TENANT_FORBIDDEN` | 초록 |
| `AudienceShippedConfigTest` × 6 게이트웨이 (각 7) | 출하 3(SHADOW · allowlist 정확 · override 0) + 기동 실패 4(대조군 · 빈 · 키 부재 · mode 부재/모르는 값) | 초록 |
| scm · fan `OAuth2ResourceServerConfigTest` | 사슬 구조 단언을 `AudienceCheckedChain` 으로 개정 + `audienceGate().mode() == SHADOW` | 초록 |

## 헬퍼 — 운영과 같은 `aud`

| 게이트웨이 | 변경 |
|---|---|
| ecommerce `JwtTestHelper` | `aud: ecommerce` 4곳 → 소비자 토큰 `WEB_STORE_CLIENT_ID`(`ecommerce-web-store-client`), 운영자 토큰 `CONSOLE_CLIENT_ID`(`platform-console-web`). compact `signToken` 은 여전히 `aud` 없음(없음 칸이 씀). `GatewayIntegrationTest` 만료 칸 `aud` 도. |
| wms `JwtTestHelper` | `signToken` 기본 `aud = platform-console-web`(추가 클레임 `"aud"→null` 로 제거 가능), `signWmsOperatorToken` `aud: wms` → console. `JwtTestHelperTest` 의 `aud == "wms"` 핀 2곳 → `CONSOLE_CLIENT_ID`(첫 실행 rc=1 로 드러남 — 결함을 핀하던 셀). |
| scm · erp · finance | `signToken` 기본 `DEFAULT_AUDIENCE = platform-console-web`. 워크로드 토큰은 **운영대로 자기 client id**: scm `signClientCredentialsToken`(기존 `scm-platform-internal-services-client` 유지) · erp `signClientCredentialsToken(clientId)` → `aud=clientId` · finance `signScopeOnlyToken` → `aud=subject`. 🔴 이 셋은 allowlist **밖**이다 — SHADOW 에선 무해, ENFORCE 에선 해당 IT 가 403 이 된다. 픽스처를 allowlist 값으로 바꿔 초록을 만들지 않았다(운영 토큰은 그 `aud` 를 안 가진다) — `TASK-MONO-697` AC-4 로 넘김. |
| fan | `signToken` 기본 `DEFAULT_AUDIENCE = fan-platform-user-flow-client`. |

## 명령과 rc (파이프 없이, 파일로 리다이렉트 후 `$?`)

- 최종: `./gradlew --continue :libs:java-gateway:cleanTest :libs:java-gateway:check` + 6 게이트웨이 각 `:cleanTest :check` → **rc=0** (`cleanTest` 로 캐시 아님 — 7개 `:test` 태스크 실행 로그 확인). 집계(test-results XML): lib **98**/0 실패 · wms **60**/0 · scm **56**/0 · erp **33**/0 · finance **33**/0 · fan **48**/0 · ecommerce **147**/0 (skip 0 전부).
- 🔴 `test` 는 `@Tag("integration")` 을 제외한다 — **Testcontainers IT 는 이 호스트에서 돌지 않았다**(Docker 꺼짐). 6 게이트웨이 `integrationTest` 는 CI 통합 잡이 권위. IT 는 `application.yml` 기본값(SHADOW)을 물려받으므로 audience 로 거절될 경로는 없다 — 다만 **실행 초록은 미측정**.

## bite

| bite | 방법 | 결과 | 복원 |
|---|---|---|---|
| ① 공유 검증기 무력화 | `AllowedAudiencesValidator.validate` 첫 줄에 `return success()` | lib `:test` **rc=1**, 98칸 중 **9** 실패(검증기 단위 7 + 사슬 순서 2) · ecommerce **rc=1**, 147칸 중 **5**(섀도 3 · enforce 2) · wms **rc=1**, 60칸 중 **5**(섀도 3 · enforce 2). 🔵 회귀 칸(401·TENANT_FORBIDDEN)은 초록 유지 — 검증기와 무관하므로 옳다. 🔵 섀도 칸이 빨개진 것은 **카운터 단언** 때문이다 — «200» 만 단언했다면 무력화된 검증기도 통과했다. | 편집 되돌림, `BITE-696` 문자열 grep 0 |
| ② SHADOW 가드 — yml | fan `application.yml` 기본값 `SHADOW` → `ENFORCE` | fan `:test` **rc=1**, 48칸 중 1(`shipsShadow`) | sed 로 되돌림 |
| ③ SHADOW 가드 — compose override | fan `docker-compose.yml` 끝에 `OIDC_AUDIENCE_MODE: ENFORCE` | Gradle 입력 선언 **전**: rc=1(1칸, 소스 변경으로 어차피 재실행). 입력 선언 **후** 재측정: 직전 실행 `:test UP-TO-DATE` → compose 한 줄만 추가 → `:test` **실행됨**, rc=1, `noDeploymentFileOverridesToEnforce` 1칸 | `git checkout --` (이 파일은 이 PR 의 변경 대상 아님), `git diff --quiet` rc=0 |

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

- [x] AC-0 ~ AC-6
- [x] 계약서 · IdP 발급 · 게이트웨이 검증 세 곳이 **같은 말**을 한다(어느 쪽으로 맞췄는지는 AC-2 결정) — 🔵 **1단계 기준으로.** 계약서 rule 5 는 섀도 단계를 명시적으로 허용하고, 6 게이트웨이는 그 섀도로 검사한다. «불일치 → 403» 이 실제로 켜지는 것은 `TASK-MONO-697`.
- [x] «설정돼 있는데 안 읽히는» `audiences:` 속성 0건

## CORRECTION

**2026-09-17 UTC close chore — 4차원 검증.**

- (a) PR [#3879](https://github.com/kanggle/monorepo-lab/pull/3879)(결정 기록 · AC-0/1 · 스펙, `7cb1cab0f`)과 PR [#3885](https://github.com/kanggle/monorepo-lab/pull/3885)(1단계 SHADOW 구현, 헤드 `e99b4dd6a`) 모두 `state=MERGED`. #3885 머지 2026-09-17T03:42:31Z, 머지 커밋 `3c946e6c2`.
- (b) `3c946e6c2` 는 `origin/main` 의 조상(머지 직후 tip).
- (c) #3885 머지 전 롤업 SUCCESS 45 · SKIPPED 20 · **실패 0**. 게이트웨이 6곳을 담는 통합 잡(ecommerce A/B/C · inventory+inbound+gateway-service · scm · erp · fan · finance) 전부 SUCCESS, 그리고 이 PR 이 `ci.yml` 에 넣은 erp·finance `gateway-service:test`·`:check` 가 `Build & Test` 로그에서 **실제 실행**됐다. #3879 의 `aud` 발급 단언 4곳도 iam B 통합 잡에서 해당 메서드가 `PASSED`.
  🔴 **CI 가 잰 트리 ≠ 머지된 트리**: #3885 CI 이후 main 에 #3886·#3887·#3884·#3888(console-web 샘플·태스크 문서)이 먼저 들어갔고 GitHub 이 최신화를 요구하지 않아 재실행 없이 머지됐다. 두 변경 파일 집합은 겹치지 않는다(이 PR = `libs/java-gateway`·6 게이트웨이·`ci.yml`·스펙). 🔵 `ci.yml` 의 main push 런은 2026-09-07 이후 없어 머지 뒤 main CI 로 합친 트리를 재확인할 수단이 없었다.
- (d) AC 절을 열어 대조: AC-0~AC-6 · Definition of Done 3칸 전부 증거와 함께 닫혀 있다. «불일치 → 403» 의 **운영 적용**은 이 티켓의 AC 가 아니다 — 소유자 결정(2단계)에 따라 `TASK-MONO-697` 이 들고 있고, 그 착수 조건은 실측 불일치 0 이다.

🔴 **이 티켓이 넘긴 것**(done/ 은 다시 읽히지 않으므로 여기 명시):
- `TASK-MONO-697` — ENFORCE 전환 · 오류 코드 이름 `AUDIENCE_FORBIDDEN` 소유자 확정 · ⚪ 미측정 client 판정 · `infra/demo` override·워크플로를 SHADOW 가드가 안 보는 한계. 🔴 데모에서 섀도 불일치를 재려면 **AMI 재굽기가 먼저**다(현재 AMI `8cf474346`, 이 변경 없음).
- `TASK-MONO-698` — 서비스 레벨 디코더 14 · console-bff · iam gateway.
