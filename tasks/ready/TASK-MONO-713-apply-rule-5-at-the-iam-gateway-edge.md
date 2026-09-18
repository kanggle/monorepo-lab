# Task ID

TASK-MONO-713

# Title

🔴 iam `gateway-service` 는 **엣지인데 `aud` 를 안 본다** — rule 5 를 적용한다(403). 🔴 **AC-0 = 인바운드 `aud` 모집단 실측이고, 그게 닫히기 전에는 allowlist 를 한 글자도 쓰지 않는다**. `Rs256JwtVerifier` 는 단일값이라 «값만 주면 된다» 가 **거짓**이다

# Status

ready (🟡 2026-09-18 UTC — AC-0 측정 완료, 그 결과가 «모집단 0» 이라 AC-3 의 정지 조건에 따라 멈춤. allowlist 미작성, 코드 변경 0)

# Owner

monorepo

# Task Tags

- security
- gateway
- contract

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus (AC-0 의 측정 설계 + 다중값 검증기 신규. 어느 경로가 Bearer 를 들고 이 필터를 지나는지를 **부재 grep 이 아니라 설정·런타임으로** 대는 것이 이 티켓의 난점이고, 그 판정이 allowlist 의 내용을 정한다)
>
> 📎 **선행 결정**: `TASK-MONO-698` § AC-3 (소유자 결정, 2026-09-18 UTC) — 선택지 **E**, iam gateway **포함**, 상태코드 **403**, 섀도 **없음(단, 모집단을 먼저 잰다)**.

# Goal

계약서 rule 5 의 주어는 이제 «엣지» 이고, iam `gateway-service` 는 그 정의상 **엣지**다(`iam.local` 로 들어오는 외부 제시 토큰을 처음 검증한다). 그런데 이 엣지는 `aud` 를 **전혀 보지 않는다**.

착수 시점의 상태(`TASK-MONO-698` § AC-0 (e) 실측, **AC-1 에서 재확인한다**):

- `TokenValidator.java:128` — `new Rs256JwtVerifier(publicKey)` **1-인자 생성자** ⇒ `expectedIssuer=null`, `expectedAudience=null`(`Rs256JwtVerifier.java:33-35`).
- `iss` 는 이 파일이 **직접** allowlist 로 본다(`:130-135`) — 3-인자 판의 `requireIssuer` 가 **단일 값**이라 allowlist 를 표현할 수 없기 때문이라고 주석이 밝힌다(`:119-125`).
- 🔴 **`expectedAudience` 도 `String` 단일 값이다**(`:28,:52-56` — JJWT `parser.requireAudience(expectedAudience)`). ⇒ **3-인자 판으로는 client id allowlist(집합 교집합)를 표현할 수 없다.** *"이미 3-인자 판이 있으니 값만 주면 된다"* 는 **거짓**이고, 이 티켓을 「설정 한 줄」로 오해하지 않기 위해 여기 적는다.

이 티켓이 끝나면: iam gateway 가 **측정된 모집단에 근거한** audience allowlist 를 선언하고, 교집합이 비면 **403** 으로 거절한다.

# Scope

## In Scope

- **AC-0 인바운드 `aud` 모집단 실측** — 이 티켓의 첫 acceptance criterion이고 **선행**이다
- `Rs256JwtVerifier` 의 **다중값(allowlist) 판** 신규 — 또는 `TokenValidator` 가 `iss` 를 직접 보듯 `aud` 도 직접 보는 경로(둘 중 선택 + 사유 기록)
- allowlist 선언(내용은 **AC-0 이 정한다**) + 불일치 → **403**
- 영향받는 iam 게이트웨이 테스트의 기대값

## Out of Scope

- 6 도메인 게이트웨이 — `TASK-MONO-696`/`697`
- console-bff — `TASK-MONO-712`
- **iam 내부 서비스들의 `/internal/**` 사슬** — 그건 「엣지 뒤」이고 `TASK-MONO-698` § AC-3 항목 1 이 «적용 안 함» 으로 결정했다. 판별자가 아예 없는 `admin-service` 한 건만 `TASK-MONO-716` 이 따로 다룬다
- `libs/java-gateway` 편입 — iam gateway 는 그 라이브러리를 쓰지 않는다(ADR-MONO-048 § D2). 이 티켓은 그 결정을 뒤집지 않는다

# Acceptance Criteria

- [ ] **AC-0 (선행, verify-then-act) — `iam.local` 에 Bearer 를 들고 도착하는 것이 무엇인지 잰다. 🔴 이게 닫히기 전에 allowlist 를 쓰지 않는다.** `TASK-MONO-698` § AC-1 (c) 가 남긴 ⚪ 를 **그대로 인용**한다(이 티켓의 AC-0 이 존재하는 이유이므로 요약하지 않는다):

  > | **iam gateway** (inbound) | ⚪ **재지 못했다** | `iam.local` 에는 (1) 브라우저 OIDC 왕복(`/oauth2/authorize`·`/token` — **Bearer 없음**, 이 사슬을 타지 않는다), (2) console-bff IAM 레그(`CONSOLE_BFF_OUTBOUND_IAM_BASE_URL` 기본 `http://iam.local`) ⇒ 운영자 토큰 `platform-console-web`, (3) `/internal/tenants/{id}/**`(`JwtAuthenticationFilter.java:49` 가 테넌트 스코프를 강제) 가 섞인다. **어느 경로가 Bearer 를 들고 이 필터를 지나는지의 전수**는 이 창에서 대지 못했다 | ⚪ |

  판정 규율(`TASK-MONO-696` § AC-1 (b) 와 같다): **설정된 base URL 로 간선을 댄다**(`application.yml` / compose env / `env.ts`). «이 client 는 안 온다» 를 **grep 부재로 말하지 않는다** — 설정에서 못 대면 ⚪ 이고 이유를 적는다. 🔵 런타임으로 잴 수 있으면(데모 창의 게이트웨이 로그 / 액추에이터) 그쪽이 더 강한 증거다 — 단 **트래픽이 0 이면 «관측 0» 은 «부재» 가 아니다**(697 AC-0 의 분모 규율). 세 갈래를 각각 판정한다: (1) 은 Bearer 가 없으므로 이 사슬 밖임을 **코드로** 확정, (2) 는 `platform-console-web` 하나인지, (3) 은 어떤 client 가 `/internal/tenants/**` 를 부르는지.
- [ ] **AC-1 — 코드 재확인.** 위 Goal 의 세 줄(1-인자 생성자 · `iss` 를 직접 봄 · `expectedAudience` 가 `String` 단일값)을 **착수 시점 `origin/main` 에서 다시 읽는다**. `aud` 가 **배열**일 때 JJWT `requireAudience` 가 「포함」인지 「동등」인지는 698 이 **재지 않았다**(⚪, JJWT 0.12.6) — 다중값 판을 새로 내므로 그 질문은 사라질 수도 있고, 남으면 테스트로 답한다.
- [ ] **AC-2 — 다중값 판.** `Rs256JwtVerifier` 에 **집합 교집합**을 표현하는 판을 내거나, `TokenValidator` 가 `iss` 를 직접 allowlist 로 보는 것과 **같은 방식으로** `aud` 를 직접 본다. 🔵 후자는 이미 이 파일에 선례가 있고(`:119-125` 의 주석이 그 선례의 이유를 적어 뒀다) 새 공개 API 를 안 만든다 — 어느 쪽을 골랐는지와 **왜** 를 이 파일에 적는다. 🔴 단일값 API 에 값을 하나 꽂고 «allowlist 를 구현했다» 로 적는 것은 이 AC 를 닫지 않는다.
- [ ] **AC-3 — 403 · 섀도 없음.** 불일치는 **403**(소유자 결정 항목 3). 🔴 `Rs256JwtVerifier` 는 `OAuth2TokenValidator` 가 아니고 Micrometer 도 없어서 **게이트웨이식 섀도가 현 코드로는 불가능**하다(698 § AC-1 (d)) — 그래서 AC-0 의 측정이 섀도를 **대체**한다. 측정이 「모집단을 못 댔다」로 끝나면 **여기서 멈추고** `ready/` 에 값·기간·출처를 덧붙인다(697 AC-0 과 같은 게이트).
- [ ] **AC-4 — allowlist 내용의 근거가 AC-0 이다.** allowlist 의 각 항목 옆에 **그 client 를 넣은 근거**(AC-0 의 어느 줄인지)를 적는다. 🔴 「있을 법해서」 넣은 항목이 하나라도 있으면 이 AC 는 안 닫힌다 — 그런 항목은 넣지 말고 ⚪ 로 남긴 뒤 거절되는지를 본다.
- [ ] **AC-5 — 검증.** `:projects:iam-platform:apps:gateway-service:check` rc=0(파이프 금지, rc 명시). 영향받는 IT 기대값 갱신. 데모에서 콘솔의 iam 경로(`/operators` 등) 1회 200 확인.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 (엣지 정의 · 섀도가 필수가 아닌 조건 · *Implementation status*) · § Error Handling (403)
- `platform/service-types/rest-api.md` § Authentication and Authorization (포인터 — iam `gateway-service` 는 `Service Type: rest-api` 다)
- `platform/service-types/identity-platform.md` § Integration Rules 규칙 3
- `docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md` § D2 (iam gateway 가 공유 라이브러리 밖인 이유)
- `tasks/review/TASK-MONO-698-audience-behind-the-gateway-and-at-the-other-edges.md` § AC-0 (e) · § AC-1 (c)(d) · § AC-3

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/iam-platform/specs/contracts/http/auth-api.md` § token exchange

# Target Service

- iam `gateway-service` (`TokenValidator`)
- `libs/java-security` `Rs256JwtVerifier` (AC-2 에서 (a) 를 고르면)

# Edge Cases

- **`aud` 배열** — 교집합이어야 한다. JJWT `requireAudience` 의 의미가 「동등」이면 배열 토큰이 **전부 거절**되므로, 다중값 판은 이 축을 반드시 테스트한다.
- **iam 은 rule 6(role) 에서 의도적으로 제외된 엣지다**(계약서 § JWT Validation 6 마지막 bullet — IdP 는 자기 `/internal/tenants/{id}/**` 표면을 **테넌트 스코프**로 인가한다). 🔵 그러므로 이 티켓의 `aud` 검사는 **rule 6 의 부재를 메우는 것이 아니다** — 두 축은 독립이고, 테넌트 강제(`JwtAuthenticationFilter.java:49`)는 **건드리지 않는다**.
- **`libs/java-security` 를 고치면 소비자가 iam gateway 만이 아니다** — `Rs256JwtVerifier` 의 다른 호출 지점을 전수로 대고(부재 grep 금지), 새 판이 기존 생성자를 깨지 않는지 확인한다.
- **브라우저 OIDC 왕복은 이 사슬 밖이다** — `/oauth2/authorize`·`/token` 은 Bearer 를 안 들고 온다. 🔴 이 경로에 audience 검사를 얹으면 **로그인 자체가 죽는다**. AC-0 (1) 이 코드로 확정하는 이유.

# Failure Scenarios

- 🔴 **측정 없이 allowlist 를 쓰기** — iam 은 콘솔의 로그인·테넌트 전환이 지나는 자리다. 빠뜨린 client 하나가 **콘솔 전체 로그인 불가**로 나타난다. AC-0 이 막는다.
- 🔴 **단일값 API 에 값 하나를 꽂고 닫기** — allowlist 가 아니라 «고정 audience» 이고, 두 번째 client 가 등록되는 날 조용히 전량 403 이 된다. AC-2 가 막는다.
- **401 로 거절** — 콘솔이 세션 만료로 읽고 재로그인 루프. 재로그인은 발급 client 를 바꾸지 못한다. AC-3 이 막는다.
- **테넌트 강제와 섞기** — `JwtAuthenticationFilter` 의 테넌트 스코프 로직을 audience 작업 중에 건드리면, 실패했을 때 **어느 축이 원인인지 구별되지 않는다**. 두 축을 같은 커밋에서 바꾸지 않는다.

# Test Requirements

- allowlist 안/밖 `aud` → 200 / **403**
- `aud` **배열** 중 하나만 allowlist 안 → 200
- `aud` 없는 토큰 → 거절
- allowlist 가 비거나 없으면 **기동 실패**(계약서 rule 5 fail-closed)
- 대조군: `iss` allowlist 거절 · 테넌트 거절이 **기존 상태코드 그대로** 남는지(audience 작업이 남의 축을 옮기지 않았다는 증거)
- 🔴 파이프로 판정하지 않는다 — 출력은 파일로, rc 는 명시

# Definition of Done

- [ ] AC-0 ~ AC-5
- [ ] iam gateway 가 **측정된 모집단에 근거한** allowlist 를 선언하고, 불일치가 403 이며, 빈 allowlist 는 기동 실패다
- [ ] AC-0 의 판정표가 이 파일에 남아 있다(⚪ 가 남았다면 그 이유와 함께)
- [ ] 🔴 **착수 시 소유자 확인 1건**: ADR 필요 여부 — `TASK-MONO-712` § Definition of Done 의 같은 줄과 동일한 질문이다(`TASK-MONO-698` § AC-3 「내가 결정하지 않은 것」)

---

# 🟡 AC-0 측정 (2026-09-18 UTC · 분석=Opus 5) — **여기서 멈춘다.** AC-3 의 정지 조건이다

AC-3 은 *"측정이 「모집단을 못 댔다」로 끝나면 **여기서 멈추고** `ready/` 에 값·기간·출처를 덧붙인다"*
라고 적는다. 그 상태에 도달했다 — 다만 **못 재서**가 아니라, **재고 나니 모집단이 비었기 때문**이다.
이 파일은 `ready/` 에 남는다.

## 출처 (전부 착수 시점 `origin/main` 의 설정·코드)

`gateway-service/src/main/resources/application.yml` · `JwtAuthenticationFilter.java` ·
`TokenValidator.java` · `console-web/src/shared/api/iam-gateway.ts` ·
`console-bff/.../IamAccountsReadAdapter.java` · `infra/demo/demo.env` ·
`platform-console/docker-compose.yml` · `product-service/application.yml`.

## 1단계 — 이 엣지가 **검증하는** 경로를 먼저 확정했다

`JwtAuthenticationFilter:82-84` 가 결정적이다: **`public-paths` 에 걸리면 인증을 통째로
건너뛴다**(`return chain.filter(...)`). 그러므로 «엣지에 도착하는 것» 과 «`TokenValidator` 를
지나는 것» 은 **다른 집합**이고, allowlist 는 후자에만 적용된다.

`public-paths` 전수(12행):

| 공개 | 비고 |
|---|---|
| `GET/POST:/oauth2/**` · `GET:/.well-known/openid-configuration` | 🟢 **AC-0 갈래 (1) 을 코드로 확정** — 브라우저 OIDC 왕복은 Bearer 도 없고 이 사슬도 안 탄다 |
| `POST:/api/accounts/signup` · `POST:/api/auth/refresh` · `GET:/actuator/health` | |
| 🔴 **`GET/POST/PUT/PATCH/DELETE:/api/admin/**` · `GET:/.well-known/admin/**`** | **다섯 동사 전부.** 그 자리의 주석이 이유를 적는다 — 운영자 토큰은 admin-service 가 **자기 RS256 키**로 민팅해(`iss=admin-service`) auth-service JWKS 로 검증 **불가**하고, 위임은 *"플랫폼 불변식"* 이며 **바꾸려면 ADR 이 선행**이다 |

⇒ `TokenValidator` 가 실제로 도는 경로는 **넷**뿐이다:
`/api/auth/**`(refresh 제외) · `/api/accounts/**`(signup 제외) · `/api/accounts/me/sessions**` ·
`/internal/tenants/**`.

## 2단계 — 그 넷에 **게이트웨이 경유로 설정된 호출자가 없다**

| 갈래 | 판정 | 근거 |
|---|---|---|
| (1) 브라우저 OIDC 왕복 | 🟢 **모집단 밖** | `public-paths` (위) |
| (2) **console-web → `/api/admin/**`** | 🟢 **모집단 밖** | 데모에서 `IAM_ADMIN_API_BASE=${IAM_PUBLIC_URL}` 로 **게이트웨이를 지나지만**, 그 경로가 **public** 이라 검증이 안 돈다. 🔴 자격도 `iam-gateway.ts:293` *"the `/api/admin/**` credential is the **EXCHANGED operator token** — never the IAM OIDC access token"* ⇒ **`aud` 클레임 자체가 없는 토큰**(`OperatorAccessTokenIssuer` 가 `sub`·`iss`·`jti`·`token_type`·`iat`·`exp` 만 민팅) |
| (3) **console-bff IAM 레그** | 🟢 **모집단 밖** | `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL=${IAM_PUBLIC_URL}` 로 게이트웨이를 지나지만, 치는 경로가 `IamAccountsReadAdapter:45` **`/api/admin/accounts`** — 역시 public. 자격도 운영자 토큰 |
| (4) `/internal/tenants/**` | ⚪ **게이트웨이 경유 호출자 0** | 저장소의 유일한 호출자는 ecommerce `product-service` `AccountServiceSellerProvisioner` 인데, base URL 이 `${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}` 이고 **그 변수를 설정하는 compose/env 가 저장소에 하나도 없다** ⇒ 게이트웨이를 지나지 않는다 |
| (5) `/api/auth/**` · `/api/accounts/**` 엔드유저 경로 | ⚪ **호출자 못 댐** | 프런트 전수에서 `iam.local` 의 그 경로를 Bearer 로 치는 코드를 못 찾았다. 🔴 **그러나 이것은 부재 증명이 아니다**(696 AC-1 (b) 규율) — «없다» 가 아니라 «설정에서 못 댔다» 로 적는다 |

## ⇒ 판정: **allowlist 에 넣을 항목이 하나도 없다. 그러므로 쓰지 않았다**

계약서 rule 5 의 allowlist 는 **비어 있을 수 없다**(빈 목록 = 기동 실패). 그런데 AC-4 는
*"allowlist 의 각 항목 옆에 그 client 를 넣은 근거(AC-0 의 어느 줄인지)를 적는다. 🔴 「있을 법해서」
넣은 항목이 하나라도 있으면 이 AC 는 안 닫힌다"* 라고 못박는다. **근거 있는 항목이 0개인데
비어 있을 수 없는 목록**은 쓸 수 없다. 지어내면 AC-4 위반이고, 지어낸 값이 그대로 운영 거절
기준이 된다.

🔵 **이것은 실패가 아니라 결과다.** 이 측정이 드러낸 것은 «iam 엣지에 allowlist 를 어떻게 넣나» 가
아니라 **«이 엣지의 검증 대상 표면에 오늘 클라이언트가 있는가»** 라는, 더 앞선 질문이다.

## 🔴 소유자에게 되묻는 것 (698 § AC-3 항목 4 의 전제가 흔들린다)

소유자 결정 **E** 는 «iam gateway 포함» 이었고, 그 근거는 «iam gateway 도 엣지다» 였다. 엣지인 것은
맞다. 그런데 **그 엣지가 실제로 검증하는 표면에 도달하는 client 를 설정으로 댈 수 없다.** 선택지:

- **ⓐ 창에서 재고 결정한다** — 데모 게이트웨이 로그/액추에이터로 그 넷에 실제 트래픽이 있는지 본다.
  🔴 697 AC-0 의 **분모 규율**이 그대로 적용된다: 트래픽이 0 이면 «관측 0» 은 «부재» 가 아니다.
  ⇒ `TASK-MONO-672`(창이 필요한 측정의 수령처)로 넘기는 것이 이 갈래다.
- **ⓑ 범위를 좁힌다** — 「iam gateway 에 rule 5 적용」을 **`/internal/tenants/**` 한 경로**로 좁히고,
  그 경로의 호출자(product-service)를 **게이트웨이 경유로 배선하는 것**을 선행으로 둔다. 🔵 지금은
  그 호출자가 `localhost:8081` 기본값으로 떨어져 있어 **데모에서 동작하지 않을 가능성**이 있다(아래).
- **ⓒ 적용 안 함으로 정정한다** — 계약서 *Implementation status* 에 «iam gateway 의 검증 표면에는
  오늘 client 가 없으므로 allowlist 를 두지 않는다» 를 **측정과 함께** 적는다. 🔵 rule 5 가 요구하는
  것은 «엣지는 allowlist 를 선언한다» 이고, **검증 자체를 안 하는 표면**은 그 규칙의 대상이 아니다
  (`/api/admin/**` 위임은 이미 «플랫폼 불변식» 으로 ADR 게이트가 걸려 있다).

🔴 **내 추천은 추천일 뿐이다** — ⓒ 가 가장 정직해 보이지만(측정이 그것을 가리킨다), 이것은
698 의 소유자 결정을 **부분 정정**하는 것이라 소유자 결정이다.

## 🔵 곁발견 둘 (이 티켓의 범위 밖 — 기록만)

1. **`ACCOUNT_SERVICE_BASE_URL` 이 저장소 어디에서도 설정되지 않는다.** ecommerce
   `product-service` 의 셀러 프로비저닝이 `http://localhost:8081` 기본값으로 떨어지는데, 그것은
   컨테이너 자기 자신을 가리킨다 ⇒ **데모에서 이 경로가 동작하지 않을 가능성**이 있다. 🔴 이 호출은
   **fail-soft** 라(ADR-MONO-042 D3) 실패가 조용하다 — 즉 «안 보이는 고장» 의 모양이다.
   판정하려면 창이 필요하다.
2. **698 § AC-1 (d) 의 «iam gateway 는 Micrometer 가 없어 섀도가 불가능» 은 부정확하다.**
   `JwtAuthenticationFilter:57` 이 **`MeterRegistry` 를 주입받고 있다**(이미
   `gateway_tenant_fallback_total` 을 쓴다). 섀도가 불가능한 이유는 «메트릭이 없어서» 가 아니라
   `Rs256JwtVerifier` 가 `OAuth2TokenValidator` 가 아니어서 게이트웨이식 **모드 분기**가 없기
   때문이다. 🔵 필터 층에서라면 섀도는 **가능**하다 — 위 ⓐ 를 고르면 그것이 수단이 된다.

## 남는 상태

- 이 티켓은 **`ready/` 에 남는다.** AC-0 은 «측정 완료 + 모집단 0» 으로 **답했고**, AC-1~AC-5 는
  손대지 않았다(코드 변경 0).
- 🔴 **allowlist 를 한 글자도 쓰지 않았다** — AC-0 이 닫히기 전에는 쓰지 않는다는 것이 이 티켓의
  선행 조건이고, 닫힌 결과가 «쓸 항목이 없다» 였다.
