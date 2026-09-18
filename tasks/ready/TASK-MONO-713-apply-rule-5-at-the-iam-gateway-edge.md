# Task ID

TASK-MONO-713

# Title

🔴 iam `gateway-service` 는 **엣지인데 `aud` 를 안 본다** — rule 5 를 적용한다(403). 🔴 **AC-0 = 인바운드 `aud` 모집단 실측이고, 그게 닫히기 전에는 allowlist 를 한 글자도 쓰지 않는다**. `Rs256JwtVerifier` 는 단일값이라 «값만 주면 된다» 가 **거짓**이다

# Status

ready

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
