# Task ID

TASK-MONO-698

# Title

🟢 게이트웨이 **뒤**(서비스 레벨 디코더 ~~14개~~ **22개**)와 **다른 엣지**(console-bff · iam gateway)는 `aud` 를 여전히 안 본다 — 측정했고, 소유자가 **선택지 E** 로 정했다

# Status

done (2026-09-18 UTC — AC-0 ~ AC-4 닫힘)

# Owner

monorepo

# Task Tags

- security
- lib
- contract

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus (AC-0·AC-1 측정 + 결정 기안. 결정 후 집행이 서블릿 사슬 공유화를 포함하면 Opus, 설정만이면 Sonnet)
>
> 🔴 **이 티켓은 결정을 내리지 않는다.** `TASK-MONO-696` 이 게이트웨이 6개에 대해 한 것(측정 → 소유자 결정 → 섀도 → 거절)을 나머지 JWT 검증 지점에 대해 **측정과 선택지 기안까지만** 한다. «적용하지 않는다» 도 정당한 결론이다 — 사유와 함께 기록되기만 하면.

# Goal

`TASK-MONO-696` 은 범위를 6 게이트웨이로 잘랐다(그 티켓 § Out of Scope). 그 티켓 § 측정 (b) 두 번째 표가 남긴 지점들:

> 🔴 **정정 (2026-09-18 UTC — AC-0 (a)).** 아래 표의 둘째 행은 원래 *"그 밖의 `ServiceLevelOAuth2Config`
> **13개** (erp 4 · fan 4 · finance ledger · scm 4)"* 였고, 그래서 이 표의 합은 **14** 였다. 그 14 는
> **클래스 이름의 수**이지 사슬의 수가 아니다 — 같은 일을 하는 **wms 5개**는 이름이
> `OAuth2ResourceServerConfig` 라 696 의 인구조사에도 이 표에도 **들어온 적이 없다**.
> 실측값: 서블릿 엔드유저 사슬 **19** · 비-게이트웨이 디코드 지점 합계 **28**. 아래 행을 고쳐 적는다.

| 표면 | 위치 (2026-09-16 UTC 측정 · 2026-09-18 정정) | audience 검증 |
|---|---|---|
| finance `account-service` | `…/account/infrastructure/security/ServiceLevelOAuth2Config.java` (자체 `JwtDecoder`, 게이트웨이와 같은 4단 사슬) | 없음 |
| 🔴 **정정** — 그 밖의 서블릿 엔드유저 사슬 **18개** (`ServiceLevelOAuth2Config` 13 = erp 4 · fan 4 · finance ledger · scm 4, **+ wms `OAuth2ResourceServerConfig` 5** — 이름이 달라 누락돼 있었다) | erp 4 · fan 4 · finance ledger · scm 4 · **wms 5** | 없음 |
| ecommerce `order-service` | `OrderSecurityConfig.java` `AudienceValidator` — 기본값 **빈 문자열 = 통과** (`application.yml` `ORDER_INTERNAL_OAUTH2_AUDIENCE:`) | 사실상 없음 (fail-open) |
| platform-console `console-bff` | `SecurityConfig.java` `.jwt(jwt -> {})` — Boot 자동 구성, `audiences` 없음 | 없음 (issuer·서명·시간만) |
| iam `gateway-service` | `TokenValidator.java` `new Rs256JwtVerifier(publicKey)` 1-인자 생성자 (`expectedAudience=null`) | 없음 |
| iam `admin-service` subject token | `IamOidcJwksSubjectTokenValidator` `.requireAudience(platform-console-web)` | **있음** (참고 — 값이 client id) |

계약서는 이제 «게이트웨이» 에 대해 rule 5(`aud` ∩ client allowlist) 를 말한다(`platform/contracts/jwt-standard-claims.md` § JWT Validation). 게이트웨이 **뒤의 리소스 서버**와 **게이트웨이가 아닌 엣지**에 같은 규칙이 적용되는지는 계약서가 말하지 않는다 — 그 공백을 결정으로 메우는 것이 이 티켓이다.

🔵 왜 게이트웨이와 같은 답이 자동으로 나오지 않는가:

- **서비스 직행 호출** — 내부 `client_credentials` 토큰이 게이트웨이를 안 거치고 서비스로 간다(`TASK-MONO-696` § AC-1 (b) — batch-worker → order/product/search/promotion 직행, community → artist 직행). 게이트웨이 allowlist 는 이 경로를 보지 못한다. 서비스 레벨 allowlist 는 **게이트웨이 경유 토큰 + 직행 워크로드 토큰** 합집합이어야 한다.
- **console-bff** 는 소비자가 아니라 **콘솔 토큰을 받는 BFF** 다 — 들어오는 `aud` 는 사실상 `platform-console-web` 하나일 것으로 예상되나 **미측정**.
- **iam gateway** 는 `libs/java-gateway` 를 쓰지 않는다(ADR-MONO-048 § D2). 공유 사슬에 넣는 방식이 그대로 적용되지 않는다 — `Rs256JwtVerifier` 3-인자 판이 이미 있다.
- 서블릿 서비스 ~~14개~~ **19개**(AC-0 (a) 정정)는 `libs/java-gateway`(WebFlux)를 볼 수 없다(ADR-MONO-049 § D1). 같은 검증기를 공유하려면 프레임워크 중립인 `libs/java-security` 로 옮겨야 한다 — **공유 라이브러리 확장 = 정책 검토 대상**(`platform/shared-library-policy.md` § Review Rule).

# Scope

## In Scope

- AC-0 · AC-1 측정, 선택지 표 기안, 소유자 결정 기록
- (결정이 «적용» 이면) 집행 티켓 기안 — 이 티켓에서 구현하지 않는다
- (결정이 «적용 안 함» 이면) 계약서에 «rule 5 는 엣지(게이트웨이)의 규칙이며, 뒤의 리소스 서버는 … 로 대신한다» 를 사유와 함께 개정

## Out of Scope

- 6 게이트웨이의 섀도 → 거절 전환 — `TASK-MONO-697`
- 서비스 레벨 tenant gate / role 동작 변경
- IdP 발급 `aud` 변경(`TASK-MONO-696` 결정 B 가 «IdP 무변경» 으로 정했다)

# Acceptance Criteria

- [x] **AC-0 — 표 재측정 (착수 시점 `origin/main`).** 위 표를 grep 이 아니라 **디코더 빈이 실제로 쓰는 사슬**로 다시 댄다(각 서비스 `ServiceLevelOAuth2Config` 가 만드는 validator 목록, console-bff 의 자동 구성 디코더가 실제로 붙이는 validator — Boot 버전 확인). 14 라는 수도 다시 센다(`TASK-MONO-696` 이후 바뀌었을 수 있다). ecommerce `order-service` `AudienceValidator` 의 운영 값(env)이 어디서도 설정되지 않는지 확인.
- [x] **AC-1 — 도달 client 실측.** 각 리소스 서버에 대해 «게이트웨이 경유로 오는 client» 와 «직행으로 오는 client» 를 **설정된 base URL 로** 표로 댄다(`TASK-MONO-696` § AC-1 (b) 와 같은 규율 — grep 부재 판정 금지). console-bff 로 들어오는 토큰의 `aud` 와 iam gateway 로 들어오는 토큰의 `aud` 도. 측정할 수 없는 칸은 ⚪ 로 남기고 이유를 적는다. 가능하면 6 게이트웨이 섀도 메트릭(`gateway.jwt.audience`)과 같은 방식의 **섀도 측정**을 제안에 포함한다(측정 없이 거절로 가는 선택지는 표에 넣되 대가를 적는다).
- [x] **AC-2 — 선택지 기안.** 최소: (A) 서블릿 서비스도 client allowlist(검증기를 `libs/java-security` 로 승격 — 공유 라이브러리 정책 검토 + 필요 시 ADR) · (B) 서비스는 «게이트웨이 경유 + 서명·issuer·tenant» 로 충분, `aud` 는 엣지만(계약서 개정) · (C) 직행 워크로드 경로만 서비스에서 allowlist · (D) console-bff / iam gateway 는 별도 판정. 각 선택지의 **깨지는 토큰**(AC-1 표 기준)과 섀도 가능 여부를 적는다. ecommerce `order-service` 의 «빈 값 = 통과» `AudienceValidator` 는 어느 선택지에서든 처분을 명시한다(fail-open 을 남기지 않는다 — 삭제 또는 fail-closed).
- [x] **AC-3 — 소유자 결정 기록.** 선택지와 부속(섀도 유무, 거절 상태코드 401/403, iam gateway 포함 여부)을 **정확한 형태로** 기록. 내 추천은 추천일 뿐이다.
- [x] **AC-4 — 후속.** 결정이 «적용» 이면 집행 티켓(들)을 이 티켓을 `done/` 으로 닫기 **전에** 기안한다. «적용 안 함» 이면 계약서 개정(스펙 먼저)을 이 티켓 PR 에 포함한다.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 · § Change Rule
- `platform/service-types/identity-platform.md` `:225` (relying party 규칙 3)
- `platform/shared-library-policy.md` § Review Rule · § Change Rule
- `docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md` § D2 (iam gateway 제외) · `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` § D1 (서블릿/리액티브 분리)
- `tasks/review/TASK-MONO-696-the-gateway-audience-is-configured-and-never-checked.md` § 측정 (b) · § AC-1

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (콘솔 한 토큰의 도메인 팬아웃)
- `projects/iam-platform/specs/contracts/http/auth-api.md` § token exchange

# Target Service

> 🔴 **정정 (2026-09-18 UTC — AC-0 (a) 실측).** 이 절은 원래 `ServiceLevelOAuth2Config × 14
> (erp 4 · fan 4 · finance 2 · scm 4 — AC-0 에서 재집계)` 한 줄이었다. AC-0 이 실제로 재집계한 결과
> **모집단이 과소계상돼 있었다**: 14 는 클래스 이름의 수이고, 디코더 구축 지점으로 세면
> **게이트웨이 6 을 뺀 비-게이트웨이 디코드 지점 = 28**, 그중 서블릿 **엔드유저** 사슬 = **19**.
> 원문은 이 상자가 보존하고, 목록은 실측값으로 고쳐 적는다.

- 서블릿 **엔드유저** 사슬 **19** — 공유 `ResourceServerChainAssembler` **17**(wms 5 · erp 4 · fan 4 · scm 4)
  + 손조립 **2**(finance `account-service` · `ledger-service`)
- 서비스 안 워크로드 전용 `/internal/**` 디코더 **3** — fan `artist` · fan `membership` · ecommerce `order-service`
- iam 자체 서블릿 `/internal/**` 디코더 **4** — `account` · `admin` · `auth` · `security`
- Boot 자동 구성 디코더 **1** — platform-console `console-bff` (**엣지**)
- JJWT `Rs256JwtVerifier` **1** — iam `gateway-service` (**엣지**)
- (선택지 A — 채택되지 않음) `libs/java-security`

🔵 **결정(AC-3) 이후 실제로 손대는 것은 이 목록 중 셋뿐이다**: console-bff(`TASK-MONO-712`) ·
iam `gateway-service`(`TASK-MONO-713`) · ecommerce `order-service`(`TASK-MONO-714`, 삭제).
나머지 22 는 **의도적으로 무변경**이고, 그 사실이 계약서에 문장으로 들어갔다(AC-4).

# Edge Cases

- **직행 워크로드 토큰** — 서비스 레벨 allowlist 가 게이트웨이 allowlist 를 복사하면 직행 `client_credentials` 호출이 전부 거절된다.
- **같은 서비스가 게이트웨이 경유 + 직행 둘 다 받는다** — allowlist 는 합집합.
- **console-bff 자동 구성 디코더** — Boot `audiences` 속성은 자동 구성 디코더에는 **실제로 적용된다**(게이트웨이와 반대). 여기서는 «속성만 넣으면 된다» 가 참일 수 있으나, 그 속성은 «정확히 일치 / 교집합» 의미와 fail-closed 여부를 확인해야 한다(빈 목록일 때 동작).
- **iam gateway `Rs256JwtVerifier`** — 3-인자 판의 `expectedAudience` 가 단일 값인지 집합인지, `aud` 배열을 어떻게 다루는지 코드로 확인.
- **테스트 헬퍼** — 서비스 레벨 IT 의 헬퍼가 `aud` 를 플랫폼 이름으로 민팅하고 있으면 `TASK-MONO-696` Failure Scenario 1(테스트 초록 · 운영 거절)이 재발한다. AC-0 에서 같이 센다.

# Failure Scenarios

- **게이트웨이 결정을 그대로 복사** — 직행 워크로드 전량 거절(위 Edge Case 1).
- **«엣지가 막으니 뒤는 괜찮다» 를 측정 없이 채택** — 직행 경로가 있는 한 엣지 allowlist 는 그 경로를 보지 못한다. 선택지 B 를 고르려면 AC-1 의 직행 표가 근거여야 한다.
- **fail-open 검증기 방치** — `order-service` `AudienceValidator` 의 «빈 값 = 통과» 가 «audience 검증이 있다» 로 읽힌다.

# Test Requirements

- 이 티켓은 측정·결정 티켓이다 — 코드 테스트 없음. 측정 명령·rc·표를 파일에 남긴다(파이프 금지, rc 명시).

# Definition of Done

- [x] AC-0 ~ AC-4
- [x] 계약서가 게이트웨이 뒤의 리소스 서버와 비-게이트웨이 엣지에 대해 `aud` 를 어떻게 다루는지 **말한다**(적용이든 비적용이든) — 이 PR 의 `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 개정(«엣지» 정의 + *Behind the edge* 절 + *Implementation status* 절)

---

# 🟢 AC-0 재측정 (2026-09-18 UTC)

> 🔴 **이 절의 측정 수단과 그 한계를 먼저 적는다.** 전부 **저장소 파일을 열어서 읽은 것**이다 —
> 빌드·테스트·컨테이너를 **한 번도 실행하지 않았다**. 그러므로 이 절에는 **기록할 명령도 rc 도 없다**(0건).
> 그 자체가 한계다: 사슬의 «조립 순서» 는 소스에서 확정되지만 «런타임에 그 빈이 실제로 선택되는가»
> (`@ConditionalOnMissingBean` 경합, 프로파일)는 재지 않았다. 실행이 필요한 칸은 아래에서 ⚪ 로 표시한다.
> 측정 기준 트리 = 이 worktree(`task/mono-698-audience-behind-gateway`, base `origin/main` `5edf53592`).

## (a) 「14」를 다시 센다 — 14 는 **클래스 파일 이름의 수**였고, 사슬의 수가 아니다

`ServiceLevelOAuth2Config` 라는 **이름을 가진 파일**은 지금도 정확히 **14개**다(erp 4 · fan 4 · finance 2 · scm 4).
그 수 자체는 `TASK-MONO-696` 이후 변하지 않았다.

🔴 **그런데 그 14 는 «게이트웨이 뒤에서 JWT 를 검증하는 지점» 의 수가 아니다.** 같은 일을 하는
**wms 서비스 5개**는 클래스 이름이 `OAuth2ResourceServerConfig` 라서 그 집계에 **한 번도 들어온 적이 없다**.
696 § 측정 (b) 둘째 표도, 이 티켓의 Goal 표도 wms 서비스 레벨을 담고 있지 않다 — 클래스 이름을 모집단으로
삼았기 때문이다. (이것이 AC-0 이 «grep 이 아니라 사슬로» 를 요구한 이유이고, 요구가 실제로 물었다.)

**디코더 구축 지점으로 다시 센 결과**(`NimbusJwtDecoder.withJwkSetUri` · `ResourceServerChainAssembler.jwtDecoder`
· `GatewayJwtDecoders.nimbus` 호출 지점을 `projects/**/src/main/**/*.java` 에서 전수):

| 무리 | 수 | 비고 |
|---|---|---|
| 게이트웨이(WebFlux, `GatewayJwtDecoders.nimbus`) | **6** | `TASK-MONO-696` 이 이미 SHADOW 로 처리 |
| 서블릿 서비스 **엔드유저** 사슬 — 공유 `ResourceServerChainAssembler` 사용 | **17** | wms 5 · erp 4 · fan 4 · scm 4 |
| 서블릿 서비스 엔드유저 사슬 — **손조립**(assembler 미사용) | **2** | finance `account-service` · `ledger-service` |
| 서비스 안의 **두 번째** 워크로드 전용 `/internal/**` 디코더 | **3** | fan `artist-service` · fan `membership-service` · ecommerce `order-service` |
| iam 자체 서블릿 `/internal/**` 디코더 | **4** | `account-service` · `admin-service` · `auth-service` · `security-service` |
| Boot **자동 구성** 디코더(명시 빈 없음) | **1** | platform-console `console-bff` |
| Nimbus 가 아닌 엣지(JJWT `Rs256JwtVerifier`) | **1** | iam `gateway-service` |
| **게이트웨이 6 을 뺀 합계** | **28** | |

⇒ **«14» 를 «22~28» 로 고쳐야 한다.** 이 티켓의 Target Service 절(`ServiceLevelOAuth2Config × 14`)은
**모집단을 과소계상**하고 있었다. 어떤 선택지를 고르든 대상 수는 14 가 아니다.

🔵 `libs/java-security/build.gradle:24` 의 주석이 독립적으로 같은 수를 말한다 — *"the six reactive gateways
AND twenty servlet services"*. 그 「20」이 17+2(엔드유저 19) + 워크로드 판을 어떻게 세었는지는 그 파일이 말하지
않지만, **14 가 아니라는 점에서는 일치한다**.

## (b) 각 디코더 빈이 **실제로 조립하는 validator 목록** (파일을 열어서)

| # | 사슬 모양 | 대상 | 조립되는 validator (순서 그대로) | audience |
|---|---|---|---|---|
| S1 | 공유 assembler | wms `admin`·`inbound`·`inventory`·`master`·`outbound` / erp `approval`·`masterdata`·`notification`·`read-model` / fan `artist`·`community`·`membership`·`notification` / scm `demand-planning`·`inventory-visibility`·`logistics`·`procurement` — **17** | `JwtTimestampValidator` → `AllowedIssuersValidator(csv)` → `TenantClaimValidator.forTenant(...)`(서비스별 스위치) → `JwtValidators.createDefault()` | **없음** |
| S2 | 손조립(같은 4단) | finance `account-service` `:64-75` · finance `ledger-service` `:64-75` | 위와 **같은 4단을 손으로** 쌓는다(`new ArrayList<>()` + `DelegatingOAuth2TokenValidator`) | **없음** |
| S3 | 워크로드 전용, 테넌트 없음 | fan `artist-service#internalJwtDecoder` `:84-88` · fan `membership-service#internalJwtDecoder` `:102` | `JwtValidators.createDefaultWithIssuer(issuer)` **단 하나** | **없음** |
| S4 | 워크로드 전용, ecommerce | ecommerce `order-service#internalJwtDecoder` `:83-96` | `JwtTimestampValidator` → `createDefaultWithIssuer(issuer)` → **`AudienceValidator(audience)`** → `SystemClientSubjectValidator(allowedClientIds)` | **있으나 무효** (아래 (c)) |
| S5 | iam `/internal/**`, scope 판별자 | iam `account-service` `:101-106` · `auth-service` `:134-139` · `security-service` `:58-63` | `createDefaultWithIssuer(issuer)` → **`RequiredScopeValidator("internal.invoke")`** | 없음(대신 **scope** 로 판별) |
| S6 | iam `/internal/**`, 판별자 없음 | iam `admin-service#internalJwtDecoder` `:104-108` | `createDefaultWithIssuer(issuer)` **단 하나** | 없음 |
| S7 | Boot 자동 구성 | console-bff | 아래 (d) | **없음** |
| S8 | JJWT | iam `gateway-service` | 아래 (e) | **없음** |

🔴🔴 **S1·S2 는 «audience 검증기가 없다» 가 아니라 «검증기를 넣을 자리가 이미 한 곳으로 모여 있다»** 는 뜻이다.
17개가 전부 `libs/java-security-servlet` 의 `ResourceServerChainAssembler.JwtDecoderBuilder` 를 지난다
(`buildValidator()` `:244-257`: timestamp → issuers → 서비스가 넘긴 validator → defaults). 이 티켓 Goal 이
전제한 *"서블릿 14개는 `libs/java-gateway`(WebFlux)를 볼 수 없다 ⇒ 공유하려면 옮겨야 한다"* 는 **맞지만**,
**옮길 곳과 꽂을 자리는 이미 만들어져 있다**. finance 둘만 그 자리 밖에 있다(선택지 A 의 숨은 비용).

🔵 **S5 ↔ S6 비대칭(곁발견, 이 티켓의 대상 아님).** iam `/internal/**` 디코더 넷 중 **셋**은
`RequiredScopeValidator("internal.invoke")` 를 달았는데(`TASK-MONO-422`/`TASK-BE-514`), `admin-service` 만
**issuer+시간뿐**이다. 그 셋의 주석이 말하는 이유(*"같은 issuer 가 시스템 토큰과 사용자 토큰을 둘 다 민팅하므로
서명+issuer 로는 구별되지 않는다"*)는 admin-service 에도 그대로 적용된다. 🔴 **결함이라고 단정하지 않는다** —
admin-service 가 필터 층에서 보완하는지 확인하지 않았다(미측정). 별 티켓 후보.

## (c) `ORDER_INTERNAL_OAUTH2_AUDIENCE` 부재 판정 — **어디를 봤는지 이름을 댄다**

**판정: 이 저장소 어디에서도 설정되지 않는다.** 단, 그 판정은 아래 모집단 안에서만 참이다.

- 본 선언: `projects/ecommerce-microservices-platform/apps/order-service/src/main/resources/application.yml:82`
  `audience: ${ORDER_INTERNAL_OAUTH2_AUDIENCE:}` — **기본값이 빈 문자열**.
- 본 그 값의 유일한 다른 출현: 이 티켓 자신의 Goal 표 `:37`. 즉 **선언 1 + 문서 1 = 2건**, 설정은 0건.
- 본 곳(전수, worktree 루트 기준): `infra/**`(`AUDIENCE` **0건**) · `.github/**`(`audience`/`AUDIENCE` **0건**) ·
  `projects/**/*.yml`(전수 — 걸린 것은 6 게이트웨이의 `allowed-audiences`/`audience-mode`,
  `platform-console/docker-compose.e2e.yml:280` `ADMIN_OIDC_AUDIENCE`, ecommerce auth-service `JWT_AUDIENCE`,
  그리고 이 줄뿐) · compose/env 파일 **39개**(`**/docker-compose*.yml`, `**/*.env`, `**/.env*`) +
  `infra/demo/*.override.yml` **14개**.
- **k8s 도 봤다**: `projects/ecommerce-microservices-platform/k8s/services/order-service/configmap.yaml` 에
  이 키 **없음**. (같은 디렉터리의 `auth-service-deprecated/configmap.yaml:14` 에는 `JWT_AUDIENCE: "api"` 가
  있는데, 그건 **폐기된 ecommerce 자체 HS256 발급자**의 키이지 order-service 것이 아니다.)
- 🔵 **대조군**: 형제 키 `ORDER_INTERNAL_OAUTH2_ISSUER` · `ORDER_INTERNAL_OAUTH2_JWK_SET_URI` **도**
  어디에서도 설정되지 않는다(`application.yml:80,81` 의 기본값만). ⇒ *"audience 만 빠졌다"* 가 아니라
  **order-service 의 internal OAuth2 블록 전체가 출하 기본값으로 돈다**. 이 대조군이 없으면
  «누군가 audience 만 빠뜨렸다» 로 오독하기 쉽다.
- 🔴 **모집단의 구멍(정직하게)**: Grep 도구는 ripgrep 이라 `.gitignore` 된 파일을 보지 않는다. 커밋되지 않은
  로컬 `.env` 나 운영 환경의 배포 파이프라인 변수는 **이 판정의 밖**이다. 「저장소가 이 값을 설정하지 않는다」가
  잰 것이고, 「어떤 런타임에도 설정돼 있지 않다」는 **재지 않았다**.

⇒ **운영에서 `AudienceValidator` 는 `expectedAudience==""` → `validate()` 첫 줄에서 무조건 `success()`**
(`AudienceValidator.java:32-34`). fail-open 확인.

🔴🔴 **그런데 실제 모양은 «fail-open» 보다 나쁘다 — 테스트는 fail-closed 이고, 그 테스트가 핀한 값은 운영이
절대 만들 수 없는 값이다.**

- `order-service/src/test/java/com/example/order/support/InternalJwtTestHelper.java:46`
  `AUDIENCE = "order-service"` — **서비스 이름**이다.
- `OrderExistenceIT.java:82` · `ConfirmPaidStaleIT.java:103` 이
  `registry.add("order.internal.oauth2.audience", () -> InternalJwtTestHelper.AUDIENCE)` 로 **켠다**.
- 운영 호출자는 batch-worker 이고 그 토큰의 `aud` 는 발급 client id **`ecommerce-internal-services-client`**
  다(`batch-worker/application.yml:90`, IdP 프레임워크 기본 — 696 § 측정 (c)).
- ⇒ **지금 이 속성에 «테스트가 쓰는 값» 을 넣으면 운영 내부 호출이 전량 401 된다.** 이것이
  `TASK-MONO-696` Failure Scenario 1(테스트 초록·운영 거절)의 **완성형**이고, 696 AC-5 는 게이트웨이 헬퍼
  6개만 고쳤으므로 여기는 **그대로 남아 있다**.
- 🔵 다만 이 서피스가 무방비인 것은 아니다: 같은 사슬의 `SystemClientSubjectValidator` 가
  `sub ∈ {ecommerce-internal-services-client}` 를 **fail-closed 로 핀한다**(`OrderSecurityConfig.java:72,93`).
  `client_credentials` 토큰에서는 `sub` 도 `aud` 도 **같은 client id** 이므로, 이 서피스에서 audience allowlist 가
  하려는 판정은 **이미 sub 축에서 이루어지고 있다**. (선택지 표의 order-service 처분은 이 사실 위에 선다.)

## (d) console-bff 자동 구성 디코더 — Boot 3.4.1 이 실제로 붙이는 것

- 배선: `SecurityConfig.java:93-94` `.oauth2ResourceServer(rs -> rs.jwt(jwt -> {}))` — **디코더 빈 없음** ⇒
  Boot 자동 구성이 물러나지 않는다(게이트웨이와 반대). `application.yml:24-25` 에 `issuer-uri` + `jwk-set-uri`
  **둘 다** 있고 `audiences` 는 **없다**. Boot 버전 `gradle.properties:5` = **3.4.1**.
- Boot 3.4.1 `OAuth2ResourceServerJwtConfiguration.JwtDecoderConfiguration` 의 동작(🟠 **증거 등급: 프레임워크
  지식이지 이 트리의 jar 을 읽은 것이 아니다 — 집행 티켓은 테스트로 핀해야 한다**):
  1. `jwk-set-uri` 가 있으면 `NimbusJwtDecoder.withJwkSetUri(...)`, `issuer-uri` 가 있으면 기본 validator 는
     `JwtValidators.createDefaultWithIssuer(issuerUri)`.
  2. `audiences` 가 **비었거나 없으면 audience validator 를 아예 추가하지 않는다** —
     ⇒ 지금 console-bff 는 **서명 + `iss` + `exp`/`nbf` 뿐**. Goal 표의 판정 그대로.
  3. `audiences` 가 **있으면** `JwtClaimValidator<List<String>>(AUD, aud -> aud != null && !disjoint(aud, audiences))`
     를 더한다 ⇒ 의미는 **교집합**(«정확히 일치» 아님), 그리고 `aud == null` 은 **거절**.
- ⇒ Edge Case 가 물은 두 질문의 답: **교집합이 맞고, `aud` 없는 토큰에 대해 fail-closed 다.**
  🔴 **단 «빈 목록 = 전부 허용»** 이다 — 속성을 지우면 조용히 검사 자체가 사라진다(기동 실패가 **아니다**).
  이것은 `AllowedAudiencesValidator`(빈 목록 = **기동 실패**)와 **반대 posture** 이고, 계약서 rule 5 의
  *"an absent or empty allowlist is a startup failure"* 를 **Boot 속성만으로는 만족시킬 수 없다**.
  ⇒ *"console-bff 는 속성 한 줄이면 된다"* 는 **절반만 참**이다. 한 줄이면 검사는 켜지지만, 「빈 값이면
  기동 실패」를 원하면 별도 가드(`AudienceShippedConfigTest` 같은 출하값 핀)가 **또** 필요하다.

🔴🔴 **그리고 console-bff 의 IT 7개가 `aud = "console-bff"` 를 민팅한다** — **서비스 이름**이다:
`ConsoleBffSmokeIntegrationTest:69` · `DomainHealthIntegrationTest:91` · `OperatorOverviewIntegrationTest:91` ·
`NotificationAggregatorIntegrationTest:79` · `CircuitBreakerIntegrationTest:101` ·
`CrossTenantDenyIntegrationTest:98` · `EntitlementPassThroughIntegrationTest:109`.
운영 토큰의 `aud` 는 `platform-console-web`(console-web `OIDC_CLIENT_ID` 기본값, `env.ts:60`)이다.
⇒ 여기서도 order-service 와 **같은 덫**이다: 속성에 `console-bff` 를 넣으면 IT 는 초록이고 운영은 전량 401,
`platform-console-web` 을 넣으면 IT 7개가 빨개진다. **어느 선택지를 고르든 이 7곳을 같이 고쳐야 한다.**

## (e) iam gateway `Rs256JwtVerifier` — 코드로 확인

- `TokenValidator.java:128` `new Rs256JwtVerifier(publicKey)` — **1-인자 생성자** ⇒
  `expectedIssuer=null`, `expectedAudience=null`(`Rs256JwtVerifier.java:33-35`). `iss` 는 이 파일이 **직접**
  allowlist 로 본다(`:130-135`) — 3-인자 판의 `requireIssuer` 가 **단일 값**이라 allowlist 를 표현할 수 없기
  때문이라고 주석이 밝힌다(`:119-125`).
- 🔴 **`expectedAudience` 도 `String` 단일 값이다**(`:28,:52-56`, JJWT `parser.requireAudience(expectedAudience)`).
  ⇒ **3-인자 판으로는 client id allowlist(집합 교집합)를 표현할 수 없다.** iam gateway 를 계약서 rule 5 에
  맞추려면 `Rs256JwtVerifier` 에 **다중값 판을 새로 내거나**, `TokenValidator` 가 `iss` 를 직접 보듯
  **`aud` 도 직접 보는** 코드를 쓰는 수밖에 없다. 「이미 3-인자 판이 있으니 값만 주면 된다」는 **거짓**이다.
- ⚪ `aud` 가 **배열**일 때 JJWT `requireAudience` 가 「포함」인지 「동등」인지는 **재지 않았다**(JJWT 0.12.6,
  `libs/java-security/build.gradle:7`). 어느 쪽이든 위 결론(단일값이라 allowlist 불가)은 바뀌지 않는다.

## (f) 테스트 헬퍼 — `aud` 를 민팅하는 곳 (Edge Case 「테스트 헬퍼」 집계)

`.audience(` 또는 `claim("aud"` 를 `projects/**/src/test*/**/*.java` 전수: **24 파일**.

| 무리 | 수 | `aud` 값의 현실성 |
|---|---|---|
| 게이트웨이 헬퍼 6(wms·scm·erp·finance·fan·ecommerce `JwtTestHelper`) | 6 | ✅ **운영과 같은 client id** — `TASK-MONO-696` AC-5 가 이미 고쳤다 |
| 서비스 레벨 헬퍼 — 현실적 | 2 | fan `artist-service/testsupport/JwtTestHelper.java:116` `.audience(clientId)` · fan `membership-service/…:106` `.audience(clientId)` |
| 서비스 레벨 헬퍼 — 🔴 **서비스 이름** | 1 | ecommerce `order-service/support/InternalJwtTestHelper.java:46` `AUDIENCE = "order-service"` |
| 🔴 **비-게이트웨이 엣지 — 서비스 이름** | 7 | console-bff IT 7개, `aud = "console-bff"` (위 (d)) |
| iam 자체 테스트(발급·subject token 단언) | 8 | 대상 밖(발급측 단언) |

🔴🔴 **그리고 나머지 19 서비스 레벨 사슬(S1 17 + S2 2)의 픽스처는 `aud` 를 «틀리게» 민팅하는 게 아니라
아예 민팅하지 않는다.** 표본 실측: `projects/erp-platform/apps/masterdata-service/src/test` 전체에서
`audience` 문자열 **0건**. 위 24-파일 목록에 wms·erp·scm 서비스 레벨과 finance 서비스 레벨이
**한 파일도 없다**는 것이 같은 사실의 다른 면이다.
⇒ **교집합 검사를 켜면 이 19개 스위트는 전부 빨개진다**(픽스처 토큰의 `aud` = 빈 집합 = 불통과).
🔵 이것은 **안전한 방향의 실패**다(초록인 채 운영이 죽는 것의 반대). 하지만 선택지 A 의 작업량 추정에
**19 스위트의 픽스처 교정**이 들어가야 한다는 뜻이고, 그 수는 Target Service 절의 「14」로는 나오지 않는다.

## (g) 곁발견 — 아직 살아 있는 죽은 속성 1건

`TASK-MONO-696` AC-4 는 죽은 `audiences:` 잔존을 **`projects/**` 안에서** 0 으로 만들었다. 그 밖에 하나 남아 있다:
`.claude/skills/service-types/identity-platform-setup/SKILL.md:79` `audiences: ${GATEWAY_AUDIENCE}   # e.g., wms`.
🔴 **스킬이 새 게이트웨이를 만들 때 «한 번도 읽히지 않는 속성 + 플랫폼 이름» 을 계속 가르친다.**
범위 밖이지만 어느 선택지에서든 같이 고쳐야 하는 한 줄이다(공유 경로 ⇒ root task).

---

# 🟢 AC-1 도달 client 실측 (2026-09-18 UTC)

## (a) 방법 — `TASK-MONO-696` § AC-1 (b) 와 같은 규율

호출자의 **설정된 base URL** 로 간선을 댄다(`application.yml` / compose env / `env.ts`).
«이 client 는 안 온다» 를 grep 부재로 말하지 않는다 — 설정에서 못 대면 **⚪(미측정)** 이고 그 이유를 적는다.
🔵 696 이 이미 «게이트웨이 경유» 열을 실측해 뒀으므로 이 티켓은 그 표를 **인용**하고 **직행 열**을 새로 잰다.

## (b) 리소스 서버별 — (a) 게이트웨이 경유 / (b) 직행

| 리소스 서버 | (a) 게이트웨이 경유로 오는 client | (b) **직행**으로 오는 client | 직행 근거 (설정된 base URL) |
|---|---|---|---|
| wms `master`·`inbound`·`inventory`·`outbound`·`admin` (5) | `platform-console-web` (696 § AC-1(b) wms 행) | ⚪ **설정된 직행 호출자를 찾지 못했다** | wms 게이트웨이 라우트(`gateway/application.yml`)만이 이 다섯을 가리킨다. console-bff 는 `http://wms.local`(게이트웨이)로 간다. ⇒ 미측정이지 부재 아님 |
| erp `masterdata` | `platform-console-web` | 🔴 **`platform-console-web` (운영자 토큰 그대로)** | erp `approval-service` → masterdata: `approval/application.yml:113` `ERP_MASTERDATA_BASE_URL=http://masterdata-service:8080`, 토큰은 `MasterDataRestAdapter.java:125` `"Bearer " + caller.getTokenValue()` — **호출자 토큰 전달** |
| erp `approval`·`notification`·`read-model` | `platform-console-web` | ⚪ 설정된 직행 호출자 없음(위와 같은 이유) | |
| scm `procurement` | `platform-console-web` | 🔴 **`platform-console-web` (운영자 토큰 그대로)** | scm `demand-planning` → procurement: `demand-planning/application.yml:104` `PROCUREMENT_BASE_URL=http://procurement-service:8080`; `ProcurementDraftPoClient.java:23-25,117` — *"the operator's bearer token is propagated unchanged"* |
| scm `inventory-visibility` | `platform-console-web` | **토큰 없음** | 같은 파일 `:107-109` — 야간 배치가 `/internal/inventory-visibility/snapshot` 을 **토큰 없이**(network trust) 읽는다 |
| scm `logistics`·`demand-planning` | `platform-console-web` | ⚪ 없음 | |
| finance `account`·`ledger` | `platform-console-web` | 🔴 **`platform-console-web`** | e2e 오버레이가 console-bff FINANCE 레그를 게이트웨이가 아니라 `http://finance-account-service:8080` 으로 돌린다(`platform-console/docker-compose.e2e.yml:390`, 696 실측). ⇒ **운영 경로는 게이트웨이, e2e 경로는 직행** |
| fan `artist` (엔드유저 사슬) | `fan-platform-user-flow-client` | ⚪ 없음 | |
| fan `artist` (**`/internal/**` 워크로드 사슬**) | — (게이트웨이가 `/internal` 을 라우팅하지 않음) | 🔴 **`community-service-client`** | fan `community/application.yml:126,138-140` `ARTIST_SERVICE_BASE_URL=http://artist-service:8080` + `IamClientCredentialsTokenProvider(client-id=community-service-client, scope=artist.read)` |
| fan `membership` (`/internal/**`) | — | 🔴 **`community-service-client`**(추정) | `community/application.yml:110` `MEMBERSHIP_SERVICE_BASE_URL=http://membership-service:8080`. ⚪ 이 레그가 같은 토큰을 다는지 **파일로 확정하지 못했다**(`MembershipCheckerAutoConfig` 를 열지 않았다) — 미측정 |
| fan `community`·`notification` | `fan-platform-user-flow-client` | ⚪ 없음 | |
| ecommerce `order-service` `/api/internal/**` | — (게이트웨이 **제외** 경로, `OrderSecurityConfig` Javadoc `:36-38`) | 🔴 **`ecommerce-internal-services-client`** | `batch-worker/application.yml:82` `ORDER_SERVICE_BASE_URL=http://order-service:8082` + `:89-90` `IAM_CLIENT_ID=ecommerce-internal-services-client` |
| ecommerce `product`·`search`·`promotion` | — | `ecommerce-internal-services-client` (batch-worker `:76,79,85`) | 🔵 **그런데 이 셋에는 JWT 디코더가 없다** — `oauth2ResourceServer` 를 쓰는 ecommerce 모듈은 `gateway-service` 와 `order-service` **둘뿐**(전수 grep). ⇒ 여기서 allowlist 를 말하는 것은 **공허**하다 |
| iam `account`·`auth`·`security`·`admin` `/internal/**` | — | iam 형제 워크로드 — `admin-service-client` · `account-service-client` · `security-service-client` · `auth-service-client` | 각 `application.yml:191/115/102/164` 의 `client-id` 기본값. ⚪ **어느 client 가 어느 서비스에 도달하는지의 전체 행렬은 재지 않았다** (이 티켓 범위 밖) |
| iam `account-service` `/internal/**` | — | 🔴 **`product-service-client`** (교차 프로젝트!) | ecommerce `product-service/application.yml:75` `IAM_CLIENT_ID=product-service-client` + `AccountServiceSellerProvisioner.java:132,164,192,214` `setBearerAuth(tokenProvider.currentBearer())` |

🔴🔴 **이 표의 가장 중요한 줄은 «직행 = 운영자 토큰 그대로» 셋이다** (erp approval→masterdata,
scm demand-planning→procurement, e2e finance). 티켓 Goal 이 걱정한
*"게이트웨이 allowlist 를 복사하면 직행 `client_credentials` 호출이 전부 거절된다"* 는 **이 세 줄에는 해당하지 않는다** —
직행 토큰의 `aud` 가 게이트웨이 경유 토큰과 **같은 `platform-console-web`** 이기 때문이다.
⇒ **Edge Case 1 이 참인 곳은 워크로드 직행 넷뿐이다**: fan artist(`community-service-client`),
ecommerce order-service(`ecommerce-internal-services-client`), iam account-service(`product-service-client`),
iam 형제들. 「합집합」의 크기는 리소스 서버당 **0~1개 client 추가**이지, 전면 재설계가 아니다.

## (c) console-bff 로 오는 `aud` / iam gateway 로 오는 `aud`

| 엣지 | 도달하는 것 | 근거 | 등급 |
|---|---|---|---|
| **console-bff** (inbound) | **`platform-console-web` 하나**로 보인다 | 유일한 호출자가 console-web 서버사이드 라우트다 — `CONSOLE_BFF_URL` 기본 `http://console-bff:8080`(라우트 4곳), console-bff 는 **공개 호스트명이 없다**(`TEMPLATE.md:514`, `check-gateway-drift.sh` I2 가 강제). 그 라우트가 붙이는 토큰은 console-web 의 OIDC access token, client `platform-console-web`(`env.ts:60`) | 🟠 설정으로 «호출자는 console-web 하나» 는 **확정**. 「그 토큰의 `aud` 가 `platform-console-web`」은 IdP 프레임워크 기본값 + 696 AC-1 의 `FormLoginIntegrationTest` 단언(⚪ 그 IT 는 CI 권위) |
| 같음 — 다른 호출자 | `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:740` `CONSOLE_BFF_URL: http://console-bff:8080` | e2e 하네스. 이 하네스가 어떤 `aud` 를 민팅하는지 ⚪ **미측정** | ⚪ |
| **iam gateway** (inbound) | ⚪ **재지 못했다** | `iam.local` 에는 (1) 브라우저 OIDC 왕복(`/oauth2/authorize`·`/token` — **Bearer 없음**, 이 사슬을 타지 않는다), (2) console-bff IAM 레그(`CONSOLE_BFF_OUTBOUND_IAM_BASE_URL` 기본 `http://iam.local`) ⇒ 운영자 토큰 `platform-console-web`, (3) `/internal/tenants/{id}/**`(`JwtAuthenticationFilter.java:49` 가 테넌트 스코프를 강제) 가 섞인다. **어느 경로가 Bearer 를 들고 이 필터를 지나는지의 전수**는 이 창에서 대지 못했다 | ⚪ |

## (d) 섀도 측정이 각 표면에서 **가능한가** (게이트웨이의 `gateway.jwt.audience` 와 같은 방식)

| 표면 | 섀도 가능? | 왜 |
|---|---|---|
| 서블릿 19(S1+S2) | ✅ **가능, 그리고 싸다** | `AllowedAudiencesValidator` 가 이미 SHADOW/ENFORCE 2모드 + Micrometer Counter 를 갖는다. `libs/java-security` 로 옮기면 S1 17개는 `.validator(...)` 한 줄, S2 2개는 `validators.add(...)` 한 줄 |
| S3·S4·S5·S6 워크로드 사슬 | ✅ 가능 | 같은 클래스를 꽂으면 된다 |
| **console-bff** | 🔴 **Boot 속성만으로는 불가능** | `spring…jwt.audiences` 는 **거절만** 한다 — 섀도 모드도, 메트릭도, 로그도 없다. 섀도를 원하면 console-bff 도 **명시 디코더 빈**을 만들어 `AllowedAudiencesValidator` 를 꽂아야 한다(= 자동 구성을 버린다). ⇒ *"속성 한 줄"* 과 *"섀도 먼저"* 는 **양립하지 않는다** |
| **iam gateway** | 🔴 **불가능(현 코드로는)** | `Rs256JwtVerifier` 는 `OAuth2TokenValidator` 가 아니고 Micrometer 도 없다. 섀도를 원하면 `TokenValidator` 에 직접 카운터를 넣어야 한다 |
| 측정 없이 바로 거절 | 가능하되 대가가 크다 | 위 (b)(c) 의 ⚪ 칸(wms 5 · erp 3 · scm 2 · fan 2 · iam 전부 · iam gateway)이 **전부 미측정**인 채로 닫는 것이다. 🔴 게이트웨이 6은 섀도로 이 ⚪ 를 메우는 중인데(`TASK-MONO-697` AC-0), 여기서 섀도를 건너뛰면 **같은 미지를 같은 방식으로 두 번째로 만들게 된다** |

---

# 🟢 AC-2 선택지 기안 (2026-09-18 UTC) — **기안이지 결정이 아니다**

## 공통 전제 (어느 선택지를 고르든 참)

1. 🔴 **대상 수는 14 가 아니라 22~28 이다**(AC-0 (a)). Target Service 절을 고쳐야 한다.
2. 🔴 **이미 벌어진 스펙 불일치가 하나 있다.** `platform/service-types/identity-platform.md:219,225` 는
   **relying party(«typically `rest-api` services and the gateway»)** 가 `aud` 를 «declared audience allowlist»
   와 교집합으로 검사해야 하고 «an empty or absent one is a **startup failure**» 라고 **MUST 로** 쓴다
   (`TASK-MONO-696` AC-3 이 그렇게 개정했다). 그런데 22개 리소스 서버에는 allowlist 가 **없고**, 기동은 **된다**.
   ⇒ 계약서는 공백이지만 **서비스타입 문서는 공백이 아니다** — 지금 상태는 「미결정」이 아니라 **위반**이다.
   🔵 다만 그 MUST 는 **읽는 사람이 없는 자리**에 있다: `rest-api` 서비스는 CLAUDE.md 규칙상
   `platform/service-types/rest-api.md` **하나만** 읽고, 그 파일에는 audience 문장이 **0건**이다.
   ⇒ 「적용 안 함」을 고르더라도 `identity-platform.md:225` 를 **같이 고쳐야** 문서가 참이 된다.
3. 🔴 **어느 선택지를 고르든 픽스처 작업이 딸려 온다**: console-bff IT 7(`aud="console-bff"`) ·
   order-service 헬퍼 1(`AUDIENCE="order-service"`) — **둘 다 서비스 이름이고 운영이 만들 수 없는 값**.
   선택지 B(적용 안 함)에서도 **그대로 두면 안 된다**(거짓을 증명하는 테스트다).

## 선택지

| | 내용 | 깨지는 토큰 (AC-1 표 기준) | 섀도 | 대가 |
|---|---|---|---|---|
| **A. 전면 적용** — 서블릿 19 + 워크로드 3 + iam 4 에 client allowlist | `AllowedAudiencesValidator` 를 `libs/java-gateway` → **`libs/java-security`** 로 승격하고 S1 의 `.validator(...)` 자리에 꽂는다 | **0** — allowlist 를 «게이트웨이 값 ∪ 직행 워크로드 값» 으로 만들면 깨지는 **운영** 토큰은 없다. 직행 셋(erp/scm/finance)은 `platform-console-web` 이라 이미 포함, 워크로드는 리소스 서버당 **client 1개 추가** | ✅ 가능 · 싸다 | 🟢 **예상보다 싸다** — 그 클래스는 이미 프레임워크 중립이다(import: micrometer·slf4j·`oauth2-core`/`-jose` 뿐, WebFlux **0**). `AllowedIssuersValidator`·`TenantClaimValidator` 가 이미 `libs/java-security` 에 산다. 🔴 대가: ① `libs/java-security` 에 **micrometer-core 새 의존성**(중립성 가드 `assertClasspathNeutrality` 는 통과 — servlet/reactive 아님) — 또는 Counter 를 인터페이스로 빼서 회피 ② 메트릭 이름 `gateway.jwt.audience` 와 태그 `gateway` 가 **틀린 명사**가 된다(리소스 서버인데) ③ **19 스위트 픽스처 교정**(현재 `aud` 미민팅) ④ finance 2개는 assembler 밖이라 손으로 ⑤ **공유 라이브러리 확장 = `shared-library-policy.md` § Review Rule + ADR**(`architecture-decision-rule.md`) |
| **B. 엣지만** — «rule 5 는 엣지의 규칙이고 뒤는 issuer+tenant+roles» 를 **계약으로** | 계약서에 절을 추가하고 `identity-platform.md:225` 의 relying-party MUST 를 **게이트웨이로 좁힌다** | **0** (코드 무변경) | — | 🔴 **AC-1 (b) 가 이 선택지의 근거를 약화시킨다** — 직행 경로가 **7건 실재**하고(운영자 토큰 3 · 워크로드 4), 그중 **교차 프로젝트 직행**(ecommerce `product-service` → iam `account-service`)까지 있다. 엣지 allowlist 는 이 일곱을 **구조적으로 못 본다**. 🔵 반론: 그 일곱 중 워크로드 넷은 **이미** 다른 축으로 판별된다(iam=`RequiredScopeValidator`, order-service=`SystemClientSubjectValidator`, fan=`hasRole(INTERNAL)`) ⇒ *"aud 를 안 본다"* 가 *"누가 부르는지 안 본다"* 는 **아니다** |
| **C. 직행 경로만** — 워크로드 `/internal/**` 사슬에만 allowlist | S3·S4·S5·S6 (**9개**) 에만 적용. 엔드유저 사슬 19개는 무변경 | **0** | ✅ | 🔵 가장 작다. 🔴 그러나 **이 아홉은 이미 판별자가 있는 바로 그 아홉이다**(scope / sub / role) — 네 번째 축을 더하는 것이고, 얻는 것은 «판별자가 없는 iam `admin-service` 하나»뿐이다. ⇒ **C 를 고를 이유가 있다면 그것은 audience 가 아니라 «admin-service 에 판별자가 없다» 이고, 그건 다른 티켓이다** |
| **D. 비-게이트웨이 엣지만** — console-bff + iam gateway | 뒤의 22 는 그대로, **엣지 2곳**만 맞춘다 | console-bff: **IT 7칸**(`aud="console-bff"`). 운영 토큰 **0** | console-bff △ · iam gateway ✗ (AC-1 (d)) | 🔵 논리가 가장 단정하다 — 계약서 rule 5 는 «엣지» 의 규칙이고 이 둘은 **엣지인데 빠져 있다**. 🔴 대가: console-bff 에 섀도를 원하면 **자동 구성을 버리고 명시 디코더**를 만들어야 하고, iam gateway 는 `Rs256JwtVerifier` 다중값 판을 **새로 내야** 한다 |
| **E.(추가 기안) D + B** — 엣지 2곳은 적용, 뒤는 «적용 안 함» 을 계약으로 명문화 | 위 둘의 합 | 위와 같음 | 위와 같음 | 🔵 **이것이 계약서를 가장 적은 거짓으로 만든다**: rule 5 = 모든 **엣지**(게이트웨이 6 + console-bff + iam gateway = 8), 뒤의 리소스 서버 = issuer+tenant+**기존 판별자**(scope/sub/role)를 명문화. AC-1 의 직행 일곱이 **무방비가 아니라는 사실**을 계약서가 처음으로 적게 된다 |

## `order-service` `AudienceValidator` 처분 — **어느 선택지에서도 as-is 를 남기지 않는다**

| 선택지 | 처분 | 근거 |
|---|---|---|
| A · C | **`AllowedAudiencesValidator` 로 교체**(allowlist=`ecommerce-internal-services-client`, 빈 값=기동 실패) + 헬퍼/IT 의 `"order-service"` → 그 client id | 같은 사슬 안에 fail-open 판과 fail-closed 판이 공존하게 두지 않는다 |
| B · D · E | 🔵 **삭제를 권한다**(fail-closed 로 바꾸는 것이 아니라) | 이 서피스의 audience 축은 `SystemClientSubjectValidator` 의 **sub 축과 같은 것을 잰다**(client_credentials 에서 `sub`=`aud`=client id). 남겨서 fail-closed 로 만들면 **같은 판정을 두 번** 하고, 값이 두 곳으로 갈라진다. 🔴 삭제 시 `application.yml:82` 의 속성과 두 IT 의 `DynamicPropertySource` 줄도 같이 지운다 — 「설정돼 있는데 안 읽힘」을 남기지 않는다(696 의 교훈) |
| **모든 선택지 공통** | 🔴 **`"order-service"` 를 `application.yml` 기본값으로 승격하는 선택지는 없다** | 운영 토큰이 그 값을 **절대 갖지 않는다**. 테스트를 초록으로 만들려고 픽스처 값을 설정에 넣는 것이 `TASK-MONO-696` AC-5 가 명시적으로 금지한 동작이다 |

## 🔵 내 추천 (**추천일 뿐이다 — AC-3 은 소유자가 쓴다**)

**E (= D + B), 단 A 를 죽이지 않고 조건부로 남긴다.**

1. **지금 한다**: console-bff + iam gateway 에 rule 5 적용(엣지를 8로 완성) · order-service `AudienceValidator`
   **삭제** · 픽스처 8곳 교정 · `identity-platform.md:225` 의 relying-party MUST 를 **엣지로 좁히고**
   뒤의 리소스 서버는 «issuer + tenant + 워크로드 판별자(scope/sub/role)» 로 명문화.
2. **지금 하지 않는다**: 서블릿 19. 이유 — AC-1 이 잰 직행 경로는 **이미 판별되고 있고**, 얻는 보안 증분보다
   19 스위트 픽스처 + 공유 라이브러리 확장(ADR) 의 반경이 크다.
3. **되살리는 조건을 숫자로 적어 둔다**(A 를 「나중에」로 미루지 않기 위해):
   ⓐ `TASK-MONO-697` 섀도가 게이트웨이에서 **allowlist 밖 client 를 1건이라도** 보이거나,
   ⓑ 판별자 없이 워크로드 토큰을 받는 사슬이 **1개라도 새로 생기면**(지금은 iam `admin-service` 하나)
   → A 를 ADR 로 올린다.

🔴 **내가 A 를 2순위로 둔 이유는 «비싸서» 가 아니다** — AC-0 이 오히려 A 가 **예상보다 싸다**는 것을 보였다
(중립 클래스 + 꽂을 자리 17/19 준비됨). 2순위인 이유는 **얻는 것이 작아서**다: A 가 막는 토큰은
AC-1 표 기준 **0건**이고, 막게 될 가상의 토큰은 이미 scope/sub/role 로 막히고 있다.

---

# 🟢 AC-3 — 소유자 결정 (2026-09-18 UTC)

> 🔴 **이것은 소유자의 결정이다.** 위 AC-2 의 선택지 표와 「내 추천」 절은 이 결정의 **입력**이었고,
> 추천이 채택된 항목이 있다고 해서 그 항목이 내 판단이 되는 것은 아니다. 아래 여섯 항목은
> 소유자가 답한 **정확한 형태** 그대로 적는다 — 다듬거나 «개선»하지 않는다.
> 직전 판(«소유자가 답해야 하는 것» 6행 표)은 이 절이 대체한다.

| # | 질문 | **결정** |
|---|---|---|
| 1 | 선택지 | **E (= D + B)** |
| 2 | 섀도 | **「엣지는 바로 거절, 서블릿은 섀도」** |
| 3 | 거절 상태코드 | **403** (401 아님) |
| 4 | iam gateway | **포함**, 단 «인바운드 `aud` 모집단 실측» 을 **선행**으로 |
| 5 | ecommerce `order-service` `AudienceValidator` | **삭제** (fail-closed 로 교체하지 않는다) |
| 6 | 서블릿 19 부활 트리거 | **ⓐⓑ 채택**, CI 가드는 만들지 않는다 |

## 1. 선택지 = **E (= D + B)**

빠져 있던 **비-게이트웨이 엣지 두 곳**(console-bff · iam gateway)에 rule 5 를 적용해 **엣지 집합을 8 로
완성**한다. **지금의 서블릿 엔드유저 사슬 19 에는 적용하지 않는다.** 그리고 계약서에
«엣지 뒤의 posture 는 **issuer + tenant + 기존 워크로드 판별자(scope/sub/role)**» 임을 **사유와 함께**
적는다.

## 2. 섀도 = **「엣지는 바로 거절, 서블릿은 섀도」**

두 엣지는 **섀도 단계 없이 바로 거절**로 간다. 사유: 인바운드 `aud` 모집단이 **단일 client**
(console-bff)이거나, **먼저 측정된다**(iam gateway — 항목 4). 🔵 서블릿 19 를 언젠가 되살린다면
**그 작업은 섀도 단계에서 시작한다**.

## 3. 거절 상태코드 = **403**

게이트웨이 6과 **같은 축**이다(`TASK-MONO-696`/`697`). **401 이 아니다.**

## 4. iam gateway = **포함**, 선행 = 인바운드 `aud` 모집단 실측

집행 티켓(`TASK-MONO-713`)의 **첫 번째 acceptance criterion 이 측정**이다: `iam.local` 로 Bearer 를
들고 와서 `JwtAuthenticationFilter` 를 **실제로 지나는 것**이 무엇인가(AC-1 (c) 가 이름 붙인 세 갈래
혼합 모집단). 🔴 **그 측정 전에 allowlist 를 쓰지 않는다.** 다중값 `Rs256JwtVerifier` 도 그 티켓 소관이다 —
기존 3-인자 판은 `String` 하나를 받아 allowlist 를 **표현할 수 없으므로** *"값만 주면 된다"* 는 **거짓**이다.

## 5. ecommerce `order-service` `AudienceValidator` = **삭제**

fail-closed 판으로 **교체하지 않는다**. 사유: `client_credentials` 토큰에서 `sub` == `aud` == client id
이므로 **같은 사슬의 `SystemClientSubjectValidator` 가 이미 그 축을 fail-closed 로 재고 있다**. 두 번째
사본을 남기면 **같은 것을 두 번 판정**하면서 값이 두 곳으로 갈라진다. 검증기 · `application.yml` 의 속성
선언 · 두 IT 의 `DynamicPropertySource` 줄을 **모두** 지우고 «설정돼 있는데 아무도 안 읽음» 잔여물을
**남기지 않는다**.

## 6. 서블릿 19 부활 트리거 = **ⓐⓑ 채택**

ⓐ `TASK-MONO-697` 의 섀도가 **어느 게이트웨이에서든 allowlist 밖 client 를 1건 이상** 보이거나,
ⓑ **판별자 없이 워크로드 토큰을 받는 사슬이 나타나면**(오늘 그 집합은 정확히 **`{iam admin-service}`**)
→ **선택지 A 를 ADR 로 올린다.** 🔵 이에 대한 **CI 가드는 만들지 않는다** — 소유자가 «산문 + 이미 존재하는
두 계수 지점» 을 명시적으로 골랐고, 새 `scripts/` 가드를 고르지 않았다.

---

## 🔴 결정과 내 측정이 어긋나는 곳 — 조용히 맞추지 않고 적는다

1. **항목 2 «console-bff 는 단일 client 라 섀도가 필요 없다» — 내 AC-1 (c) 의 등급은 🟠 이지 ✅ 가 아니다.**
   설정으로 **확정된 것은 «호출자가 console-web 하나»** 이고(공개 호스트명 없음 + `CONSOLE_BFF_URL`
   기본값 4곳), *«그 토큰의 `aud` 가 `platform-console-web`»* 는 IdP 프레임워크 기본값 + CI 권위인 IT 단언에
   기대고 있다. 게다가 **⚪ 가 하나 남아 있다**: federation e2e 하네스
   (`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:740`)가 같은 BFF 를 부르는데
   **그 하네스가 어떤 `aud` 를 민팅하는지 재지 않았다**. ⇒ 결정을 바꾸지는 않지만, `TASK-MONO-712` 가
   **그 한 칸을 켜기 전에 닫아야 한다**(그 티켓 AC-0). 「단일 client」를 근거로 섀도를 건너뛰는 이상,
   그 근거는 **측정으로 서 있어야** 한다.
2. **항목 2 + 항목 3 을 합치면 console-bff 는 «속성 한 줄» 이 아니다.** Boot 의 `audiences` 속성은
   거절을 **401** 로 낸다(디코더 사슬 안의 실패). 403 을 요구하면 게이트웨이가 하는 것과 같은
   **예외 원인 사슬 걷기**가 필요하고, 그건 명시 설정 지점을 만든다는 뜻이다. AC-0 (d) 가 이미 같은 결론에
   **다른 이유**로 도달해 있었다(빈 목록 = 검사 소멸 ⇒ 계약서의 «빈 allowlist = 기동 실패» 를 속성만으로는
   만족 못 함). ⇒ **독립적인 두 이유가 같은 곳을 가리킨다** — 712 는 «속성이냐 명시 검증기냐» 를
   **테스트로 핀해야** 하고, 이 티켓은 그것을 대신 결정하지 않는다.
3. **항목 5 의 «잔여물» 목록에 한 칸이 더 있다 — 그리고 그건 파일이 아니라 «테스트 칸»이다.**
   `ConfirmPaidStaleIT#wrongAudienceBearer_returns401`(`:183-192`)은 `aud="some-other-service"` 토큰이
   **401** 이 되는 것을 단언한다. 🔴 그 칸이 초록인 이유는 **같은 IT 가 `DynamicPropertySource` 로
   속성을 켜 주기 때문**이고, 운영에서는 그 속성이 비어 있어 **이 칸이 재는 동작이 존재하지 않는다**.
   검증기를 지우면 이 칸은 「깨지는 테스트」가 아니라 **대상이 사라진 테스트**다 ⇒ `TASK-MONO-714` 가
   삭제 목록에 **명시적으로** 넣어야 한다(조용히 빨개지게 두면 «삭제가 회귀를 만들었다» 로 읽힌다).
4. **항목 6 의 트리거 ⓑ 는 «게이트가 없는 숫자» 다.** *"오늘 그 집합은 정확히 `{iam admin-service}`"* 는
   **아무것도 실패할 수 없는 문장**이고, 이 저장소가 반복해서 대가를 치른 부류다. 🔵 소유자가 CI 가드를
   명시적으로 배제했으므로 **가드를 만들지 않는다.** 대신 내가 결정 범위 안에서 한 것: 그 사실을
   **얼지 않는 자리**에 한 문장 심었다 — `platform/contracts/jwt-standard-claims.md` rule 5
   *Behind the edge* 의 «판별자가 하나도 없는 사슬은 이 규칙 아래에서 **결함**이다» 줄 + `TASK-MONO-716`
   포인터. 🔴 이 티켓의 AC-0 (b) 표는 `done/` 에서 **얼어붙어 다시 읽히지 않는다**는 전제로 적는다.

## 🔴 내가 결정하지 않은 것 (소유자 확인 필요) — ADR 게이트

`platform/service-types/identity-platform.md` § Change Rule 은 *"New constraints affecting deployed
services require an ADR"* 라고 쓴다. 이번 개정은 **두 방향**이다: (a) relying-party MUST 를 엣지로 **좁힘**
= 완화 ⇒ ADR 대상 아님. (b) rule 5 의 주어를 «게이트웨이» → «엣지» 로 **넓힘** = 배포된 서비스 두 개가
새로 규칙 안에 들어온다 ⇒ **ADR 이 필요한지 판단이 갈릴 수 있다.** 🔵 내 판단: **계약서 개정 자체는
`jwt-standard-claims.md` § Change Rule 이 요구하는 «구현 전 문서화» 이고**(696 도 같은 경로였다),
**배포된 서비스를 실제로 바꾸는 것은 712/713 이다** ⇒ ADR 필요 여부는 **그 두 티켓 착수 시점의
소유자 판단**으로 넘긴다(두 티켓 § Definition of Done 에 그 줄을 박아 뒀다). 🔴 조용히 «불필요» 로
결론내지 않았다는 것을 여기 남긴다.

---

# 🟢 AC-4 — 후속 (2026-09-18 UTC)

## (a) 스펙 개정 — **이 PR 에 포함** (구현 전 스펙)

| 파일 | 무엇을 바꿨나 |
|---|---|
| `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 | 주어를 «게이트웨이» → **«엣지»**(위치로 정의, service type 아님)로. 새 절 ***Behind the edge*** = 엣지 뒤 리소스 서버는 allowlist 를 **안 둔다**, posture 는 `iss` + `tenant_id` + 기존 판별자, **사유**(client_credentials 에서 `sub`==`aud` ⇒ 같은 축 두 번 판정 + 값 분산)와 그 사유가 **기대고 있는 측정**, 그리고 **되살리는 두 트리거**. fail-closed 요구를 «프레임워크 내장 속성» 경우로 날카롭게(빈 목록에 검사가 사라지는 속성은 이 요구를 만족하지 않는다 ⇒ 출하값 테스트 핀). 섀도는 **필수가 아니다**(모집단이 이미 측정된 엣지). ***Implementation status* 절**로 «아직 구현 안 된 부분»을 명시. § Change Rule 대로 **change log 항목** 추가. |
| 같은 파일 § Signing Strategy · `aud` 행 · § Error Handling | 같은 사실이 사는 나머지 세 자리를 같이 고쳤다(한쪽만 고치면 갈라진다). 403 이 «게이트웨이만의 상태코드가 아니다» 를 § Error Handling 이 말한다. |
| `platform/service-types/identity-platform.md` § Integration Rules | relying party 를 **엣지 / 엣지 뒤** 두 종류로 가르고, `aud` 규칙을 **엣지 전용 하위 항목**으로 내렸다. 규칙 3 본문은 서명·`iss`·`exp`/`nbf`·`tenant_id`. 🔴 **22개가 위반 중이었다는 사실을 삭제하지 않고 그 자리에 적었다**(왜 22개 코드 수정이 아니라 좁히기인지 포함). |
| 같은 파일 § Testing Requirements | negative test 목록의 `aud` 세 칸이 **엣지에 대한 것**임을 명시. |
| `platform/service-types/rest-api.md` § Authentication and Authorization | **포인터 신설.** 판단 = **필요하다**(아래). |

### `rest-api.md` 에 포인터를 다는가 — **판단: 단다. 그리고 AC-2 의 근거보다 강하다**

AC-2 공통전제 2 는 *"그 MUST 는 읽는 사람이 없는 자리에 있다 — `rest-api` 서비스는
`platform/service-types/rest-api.md` 하나만 읽고 그 파일에는 audience 문장이 0건"* 이라고 적었다.
🔴🔴 **이번에 재서 그보다 한 칸 더 나빴다**: 소유자 결정이 rule 5 아래로 끌어들인 **엣지 두 곳이 바로
`rest-api` 다** — `projects/platform-console/specs/services/console-bff/architecture.md:50`
`Service Type: rest-api`, `projects/iam-platform/specs/services/gateway-service/architecture.md:15`
`Service Type: rest-api`. ⇒ 「규칙이 구속하는 당사자가 읽는 유일한 파일이 그 규칙을 한 글자도 말하지
않는다」가 된다. **포인터를 단다.** 형식은 그 파일이 이미 쓰는 포인터 관행 그대로
(§ Versioning · § Idempotency: *"canonical 은 저기 있다, 여기 재진술 금지"*) — 규칙을 복제하지 않으므로
**새 제약이 아니고**, 따라서 그 파일 § Change Rule 의 ADR 조항에 걸리지 않는다.

## (b) 집행 티켓 3건 — `tasks/ready/` 에 기안 (이 티켓을 닫기 **전에**)

| ID | 한 줄 | 모델 |
|---|---|---|
| `TASK-MONO-712` | console-bff **엣지**에 rule 5 — 403, 섀도 없음. 🔴 Boot `audiences` 속성은 **빈 목록이면 검사가 조용히 사라지므로** 계약서의 «빈 allowlist = 기동 실패» 를 속성만으로 만족 못 한다 ⇒ **기전을 테스트로 핀**. IT **7개**가 `aud="console-bff"`(서비스 이름)를 민팅 중 — **고치기 전의 초록은 아무것도 증명하지 않는다** | 분석=Opus 5 / 구현 권장=Opus |
| `TASK-MONO-713` | iam gateway **엣지**에 rule 5 — **AC-0 = 인바운드 `aud` 모집단 실측**(선행, 닫히기 전엔 allowlist 를 쓰지 않는다) · 다중값 `Rs256JwtVerifier` 신규 · 403 | 분석=Opus 5 / 구현 권장=Opus |
| `TASK-MONO-714` | ecommerce `order-service` fail-open `AudienceValidator` **삭제** + 속성 + 두 IT 의 `DynamicPropertySource` + 헬퍼 `AUDIENCE` 상수 + **대상이 사라진 테스트 칸 1**. 🔴 `"order-service"` 를 설정으로 승격하는 선택지는 **없다** | 분석=Opus 5 / 구현 권장=Sonnet |

## (c) 곁발견 2건 — 기록하고, 하나는 티켓으로 (`715`), 하나도 티켓으로 (`716`)

> 🔵 **왜 여기 «기록» 과 «티켓» 을 둘 다 두는가**: 이 티켓은 곧 `review/` → `done/` 로 얼고,
> 얼어붙은 파일에 남긴 의무는 다시 읽히지 않는다. 그러므로 **여기엔 관측만** 남기고,
> **해야 할 일은 살아 있는 큐에** 둔다.

### ① `.claude/skills/service-types/identity-platform-setup/SKILL.md:79` — 죽은 속성 + 플랫폼 이름 오해를 **계속 가르친다**

```yaml
audiences: ${GATEWAY_AUDIENCE}   # e.g., wms
```

두 가지가 동시에 틀렸다: (i) 이 속성은 게이트웨이가 **쓰지 않는 디코더**를 설정한다(`TASK-MONO-696` 의
발견 그 자체 — 696 AC-4 는 `projects/**` 안에서만 0 으로 만들었다), (ii) 예시값 `wms` 는 **플랫폼 이름**인데
`aud` 는 **client id** 다(696 이 계약서에서 고친 바로 그 오해). ⇒ **새 게이트웨이를 만드는 스킬이
696 이 고친 두 가지를 그대로 재생산한다.**
**판단: 티켓이 필요하다 — `TASK-MONO-715`.** 사유: 공유 경로(`.claude/`)라 이 티켓(698)의 Scope 밖이고,
AC-0 (g) 자신이 «범위 밖» 으로 적었다. 한 줄 고치기지만 **트리거가 실재**한다(스킬이 읽힐 때마다).
🔴 스코프를 늘리지 않는다 — AC-0 이 관측한 **그 한 줄과 그 두 오해**뿐이다.

### ② iam `admin-service` `/internal/**` 에 판별자가 없다 — **이번에 판정까지 했다**

AC-0 (b) 는 *"결함이라고 단정하지 않는다 — admin-service 가 필터 층에서 보완하는지 확인하지 않았다(미측정)"*
로 남겼다. **그 ⚪ 를 이 창에서 닫았다**(저장소 파일 읽기, 모집단은 이름을 댄다):

- `apps/admin-service/.../infrastructure/config/SecurityConfig.java:123-140` — `@Order(0)` `/internal/**`
  사슬의 게이트는 **`.requestMatchers("/internal/**").authenticated()`** 한 줄이고, 디코더는
  `createDefaultWithIssuer(issuer)` **단 하나**(`:104-108`).
- 같은 패키지 `InternalApiFilter.java:21-35, 60-68` — **운영 프로파일에서는 비활성(non-terminal)** 이고
  *"It never rejects"* 라고 스스로 밝힌다. dev/test 바이패스 전용이다 ⇒ **보완 필터가 아니다.**
- `presentation/internal/` 두 컨트롤러 — `@PreAuthorize` **0건**.
- 🔴 **모집단의 한계**: 본 것은 위 세 자리뿐이다. 전역 method-security 설정이나 다른 인터셉터는 **안 봤다**.

⇒ 같은 issuer 가 **사용자 토큰과 워크로드 토큰을 둘 다 민팅**하므로, 서명+issuer+시간만 보는 이 사슬은
**IdP 가 발급한 아무 토큰이나** `/internal/**` 에 들여보낸다. 형제 셋(`account`·`auth`·`security`)은
`RequiredScopeValidator("internal.invoke")` 로 정확히 이것을 막는다.
**판단: 티켓이 필요하다 — `TASK-MONO-716`.** 🔴 **관측을 결함으로 «승격» 하지 않는다**: 716 의 AC-0 은
**호출자 실측**(그 사슬에 실제로 도달하는 client 와 그 토큰의 scope)이고, 판별자를 켜는 것은 그 다음이다 —
`internal.invoke` scope 이 없는 정당한 호출자가 있으면 그걸 먼저 알아야 한다(713 과 같은 규율).
🔵 그리고 이 티켓은 **AC-3 항목 6 의 트리거 ⓑ 가 이름 부른 바로 그 하나**다 ⇒ 716 이 닫히면
그 집합이 비고, 트리거 ⓑ 는 «새로 생기면» 이라는 원래 의미로 작동한다.
