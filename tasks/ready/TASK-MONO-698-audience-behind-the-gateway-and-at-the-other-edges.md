# Task ID

TASK-MONO-698

# Title

🔴 게이트웨이 **뒤**(서비스 레벨 디코더 14개)와 **다른 엣지**(console-bff · iam gateway)는 `aud` 를 여전히 안 본다 — 같은 원칙을 적용할지, 적용하지 않을지를 **측정한 뒤** 소유자가 정한다

# Status

ready

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

| 표면 | 위치 (2026-09-16 UTC 측정) | audience 검증 |
|---|---|---|
| finance `account-service` | `…/account/infrastructure/security/ServiceLevelOAuth2Config.java` (자체 `JwtDecoder`, 게이트웨이와 같은 4단 사슬) | 없음 |
| 그 밖의 `ServiceLevelOAuth2Config` 13개 | erp 4 · fan 4 · finance ledger · scm 4 | 없음 |
| ecommerce `order-service` | `OrderSecurityConfig.java` `AudienceValidator` — 기본값 **빈 문자열 = 통과** (`application.yml` `ORDER_INTERNAL_OAUTH2_AUDIENCE:`) | 사실상 없음 (fail-open) |
| platform-console `console-bff` | `SecurityConfig.java` `.jwt(jwt -> {})` — Boot 자동 구성, `audiences` 없음 | 없음 (issuer·서명·시간만) |
| iam `gateway-service` | `TokenValidator.java` `new Rs256JwtVerifier(publicKey)` 1-인자 생성자 (`expectedAudience=null`) | 없음 |
| iam `admin-service` subject token | `IamOidcJwksSubjectTokenValidator` `.requireAudience(platform-console-web)` | **있음** (참고 — 값이 client id) |

계약서는 이제 «게이트웨이» 에 대해 rule 5(`aud` ∩ client allowlist) 를 말한다(`platform/contracts/jwt-standard-claims.md` § JWT Validation). 게이트웨이 **뒤의 리소스 서버**와 **게이트웨이가 아닌 엣지**에 같은 규칙이 적용되는지는 계약서가 말하지 않는다 — 그 공백을 결정으로 메우는 것이 이 티켓이다.

🔵 왜 게이트웨이와 같은 답이 자동으로 나오지 않는가:

- **서비스 직행 호출** — 내부 `client_credentials` 토큰이 게이트웨이를 안 거치고 서비스로 간다(`TASK-MONO-696` § AC-1 (b) — batch-worker → order/product/search/promotion 직행, community → artist 직행). 게이트웨이 allowlist 는 이 경로를 보지 못한다. 서비스 레벨 allowlist 는 **게이트웨이 경유 토큰 + 직행 워크로드 토큰** 합집합이어야 한다.
- **console-bff** 는 소비자가 아니라 **콘솔 토큰을 받는 BFF** 다 — 들어오는 `aud` 는 사실상 `platform-console-web` 하나일 것으로 예상되나 **미측정**.
- **iam gateway** 는 `libs/java-gateway` 를 쓰지 않는다(ADR-MONO-048 § D2). 공유 사슬에 넣는 방식이 그대로 적용되지 않는다 — `Rs256JwtVerifier` 3-인자 판이 이미 있다.
- 서블릿 서비스 14개는 `libs/java-gateway`(WebFlux)를 볼 수 없다(ADR-MONO-049 § D1). 같은 검증기를 공유하려면 프레임워크 중립인 `libs/java-security` 로 옮겨야 한다 — **공유 라이브러리 확장 = 정책 검토 대상**(`platform/shared-library-policy.md` § Review Rule).

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

- [ ] **AC-0 — 표 재측정 (착수 시점 `origin/main`).** 위 표를 grep 이 아니라 **디코더 빈이 실제로 쓰는 사슬**로 다시 댄다(각 서비스 `ServiceLevelOAuth2Config` 가 만드는 validator 목록, console-bff 의 자동 구성 디코더가 실제로 붙이는 validator — Boot 버전 확인). 14 라는 수도 다시 센다(`TASK-MONO-696` 이후 바뀌었을 수 있다). ecommerce `order-service` `AudienceValidator` 의 운영 값(env)이 어디서도 설정되지 않는지 확인.
- [ ] **AC-1 — 도달 client 실측.** 각 리소스 서버에 대해 «게이트웨이 경유로 오는 client» 와 «직행으로 오는 client» 를 **설정된 base URL 로** 표로 댄다(`TASK-MONO-696` § AC-1 (b) 와 같은 규율 — grep 부재 판정 금지). console-bff 로 들어오는 토큰의 `aud` 와 iam gateway 로 들어오는 토큰의 `aud` 도. 측정할 수 없는 칸은 ⚪ 로 남기고 이유를 적는다. 가능하면 6 게이트웨이 섀도 메트릭(`gateway.jwt.audience`)과 같은 방식의 **섀도 측정**을 제안에 포함한다(측정 없이 거절로 가는 선택지는 표에 넣되 대가를 적는다).
- [ ] **AC-2 — 선택지 기안.** 최소: (A) 서블릿 서비스도 client allowlist(검증기를 `libs/java-security` 로 승격 — 공유 라이브러리 정책 검토 + 필요 시 ADR) · (B) 서비스는 «게이트웨이 경유 + 서명·issuer·tenant» 로 충분, `aud` 는 엣지만(계약서 개정) · (C) 직행 워크로드 경로만 서비스에서 allowlist · (D) console-bff / iam gateway 는 별도 판정. 각 선택지의 **깨지는 토큰**(AC-1 표 기준)과 섀도 가능 여부를 적는다. ecommerce `order-service` 의 «빈 값 = 통과» `AudienceValidator` 는 어느 선택지에서든 처분을 명시한다(fail-open 을 남기지 않는다 — 삭제 또는 fail-closed).
- [ ] **AC-3 — 소유자 결정 기록.** 선택지와 부속(섀도 유무, 거절 상태코드 401/403, iam gateway 포함 여부)을 **정확한 형태로** 기록. 내 추천은 추천일 뿐이다.
- [ ] **AC-4 — 후속.** 결정이 «적용» 이면 집행 티켓(들)을 이 티켓을 `done/` 으로 닫기 **전에** 기안한다. «적용 안 함» 이면 계약서 개정(스펙 먼저)을 이 티켓 PR 에 포함한다.

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

- `ServiceLevelOAuth2Config` × 14 (erp 4 · fan 4 · finance 2 · scm 4 — AC-0 에서 재집계)
- ecommerce `order-service` (`AudienceValidator`)
- platform-console `console-bff`
- iam `gateway-service`
- (선택지 A) `libs/java-security`

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

- [ ] AC-0 ~ AC-4
- [ ] 계약서가 게이트웨이 뒤의 리소스 서버와 비-게이트웨이 엣지에 대해 `aud` 를 어떻게 다루는지 **말한다**(적용이든 비적용이든)
