# Task ID

TASK-MONO-712

# Title

🔴 console-bff 는 **엣지인데 `aud` 를 안 본다** — rule 5 를 적용한다(403, 섀도 없음). 🔴 «속성 한 줄» 은 절반만 참이고, IT 7개가 **운영이 만들 수 없는 `aud`** 를 민팅하는 한 초록은 아무것도 증명하지 않는다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- contract
- test

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus (기전 선택이 이 티켓의 전부다 — «Boot 속성이냐 명시 검증기냐» 를 계약서의 fail-closed 요구와 403 요구 **둘 다** 만족하는 쪽으로 고르고 테스트로 박는 일. 픽스처 7곳 교정 자체는 기계적이다)
>
> 📎 **선행 결정**: `TASK-MONO-698` § AC-3 (소유자 결정, 2026-09-18 UTC) — 선택지 **E**, 엣지는 **바로 거절**, 상태코드 **403**. 이 티켓은 그 결정의 집행이지 재논의가 아니다.

# Goal

`platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 는 이제 **모든 엣지**에 대해 말한다 — «엣지» 는 신뢰 경계 **밖에서 제시된 토큰을 처음으로 검증하는 위치**이고, service type 이 아니다. console-bff 는 그 정의상 엣지인데(브라우저의 토큰을 console-web 서버사이드 라우트를 통해 직접 받는다) **`aud` 를 보지 않는다**.

착수 시점의 상태(`TASK-MONO-698` § AC-0 (d) 실측, **AC-0 에서 재확인한다**):

- `apps/console-bff/.../infrastructure/security/SecurityConfig.java:93-94` — `.oauth2ResourceServer(rs -> rs.jwt(jwt -> {}))`, **디코더 빈 없음** ⇒ Boot 자동 구성이 디코더를 만든다.
- `application.yml:24-25` — `issuer-uri` + `jwk-set-uri` **둘 다** 있고 `audiences` 는 **없다** ⇒ 붙는 것은 서명 + `iss` + `exp`/`nbf` 뿐.
- Boot **3.4.1**(`gradle.properties:5`).

이 티켓이 끝나면: console-bff 가 **자기 audience allowlist 를 선언**하고, 교집합이 비면 **403** 으로 거절하며, **allowlist 가 비거나 없으면 그 상태가 테스트에서 빨갛다**.

# Scope

## In Scope

- AC-0 재측정(아래) — 특히 **인바운드 `aud` 모집단의 ⚪ 한 칸**
- console-bff 의 audience allowlist 선언 + 기전 선택(속성 / 명시 검증기) + 그 선택의 **테스트 핀**
- 불일치 → **403** 매핑(디코더 사슬의 기본 401 을 원인 사슬 걷기로 403 으로)
- IT **7개**의 `aud` 픽스처 교정
- 필요 시 `projects/platform-console/specs/services/console-bff/architecture.md` 의 보안 절 갱신(스펙 먼저)

## Out of Scope

- 6 게이트웨이의 섀도 → 거절 — `TASK-MONO-697`
- iam gateway — `TASK-MONO-713`
- ecommerce `order-service` 의 fail-open 검증기 — `TASK-MONO-714`
- **서블릿 엔드유저 사슬 19** — `TASK-MONO-698` § AC-3 항목 1 이 «지금 하지 않는다» 로 결정했다
- console-web(프런트) 쪽 OIDC client 등록 변경

# Acceptance Criteria

- [ ] **AC-0 (선행) — 인바운드 `aud` 모집단의 남은 ⚪ 를 닫는다.** `TASK-MONO-698` § AC-1 (c) 는 console-bff 의 호출자를 **«console-web 서버사이드 라우트 하나»** 로 **설정에서 확정**했지만, 그 토큰의 `aud` 값 자체는 🟠(IdP 프레임워크 기본값 + CI 권위 IT 단언)이고, **⚪ 가 한 칸 남아 있다**: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:740` 의 federation e2e 하네스가 같은 BFF 를 부르는데 **그 하네스가 민팅하는 `aud` 를 재지 않았다**. 🔴 **소유자 결정이 「섀도 없이 바로 거절」인 근거가 «모집단이 단일 client」 이므로, 그 근거는 측정으로 서 있어야 한다** — 이 칸을 닫기 전에 거절을 켜지 않는다. 판정이 «다른 값을 민팅한다» 로 나오면 (a) 그 값을 allowlist 에 넣을지 (b) 하네스를 고칠지는 **기록하고 진행**(픽스처를 초록으로 만들려고 allowlist 를 늘리지 않는다 — Failure Scenario 2).
- [ ] **AC-1 — 기전 결정 + 그 결정을 테스트가 판정한다.** 🔴 *"속성 한 줄이면 된다"* 는 **절반만 참**이다(698 § AC-0 (d)): Boot 3.4.1 의 `spring.security.oauth2.resourceserver.jwt.audiences` 는 (i) 의미가 **교집합**이고 (ii) `aud == null` 을 **거절**하지만, (iii) **목록이 비거나 없으면 audience validator 를 아예 추가하지 않는다** — 즉 **조용히 검사가 사라진다**. 계약서 rule 5 의 *"an absent or empty allowlist is a **startup failure**"* 는 **속성만으로 만족되지 않는다.** 이 티켓은 둘 중 하나를 고르고 **이유를 적는다**: (a) 속성 + **출하값 핀 테스트**(게이트웨이의 `AudienceShippedConfigTest` 와 같은 형태 — 값이 지워지면 빨갛다), 또는 (b) **명시 `JwtDecoder` 빈** + 공유 `AllowedAudiencesValidator` 계열(빈 목록 = 기동 실패가 검증기 자체의 성질). 🔴 어느 쪽을 고르든 **«속성을 지웠을 때 빨개지는 칸»** 이 있어야 AC 가 닫힌다 — (iii) 의 실측을 이 트리의 Boot 판으로 **테스트가 재현**하는 것이 그 칸이다(698 의 근거 등급은 🟠 «프레임워크 지식이지 이 트리의 jar 을 읽은 것이 아니다»).
- [ ] **AC-2 — 403.** 불일치가 **403** 으로 나간다(소유자 결정 항목 3). 🔴 디코더 사슬 안에서 난 audience 실패는 기본이 **401 `invalid_token`** 이므로, 게이트웨이가 하는 것과 같은 **예외 원인 사슬 걷기**가 필요하다. 401 이 나가는 칸이 하나라도 있으면 이 AC 는 안 닫힌다. 오류 코드 이름은 `TASK-MONO-697` AC-1 이 확정하는 이름을 따른다 — **그 확정 전이면 이 티켓에서 새 이름을 만들지 말고 697 을 기다리거나, 이름 없는 403 으로 낸다**(선택을 기록).
- [ ] **AC-3 — 섀도 없음.** 소유자 결정 항목 2: console-bff 는 **섀도 단계 없이 바로 거절**이다. 🔵 이것은 비용 절약이기도 하다 — 698 § AC-1 (d) 가 *"Boot 속성만으로는 섀도가 불가능하다(모드도 메트릭도 로그도 없다)"* 를 실측했다. AC-0 이 모집단을 닫는 것이 섀도를 **대체**한다.
- [ ] **AC-4 — IT 7개의 `aud` 픽스처 교정. 🔴 이걸 하기 전의 초록은 아무것도 증명하지 않는다.** 아래 7곳이 `aud = "console-bff"` — **서비스 이름**이고 운영 토큰은 그 값을 **절대 갖지 않는다**(운영은 console-web 의 OIDC client, `apps/console-web/.../env.ts:60` 기본값 `platform-console-web`):
  `ConsoleBffSmokeIntegrationTest:69` · `DomainHealthIntegrationTest:91` · `OperatorOverviewIntegrationTest:91` · `NotificationAggregatorIntegrationTest:79` · `CircuitBreakerIntegrationTest:101` · `CrossTenantDenyIntegrationTest:98` · `EntitlementPassThroughIntegrationTest:109`.
  🔴 **픽스처를 allowlist 값으로 «맞춰서» 초록을 만드는 것이 아니라, 픽스처가 운영과 같은 값을 민팅하도록 고치는 것이다** — 이 둘은 결과가 같아 보이지만 방향이 반대다(`TASK-MONO-696` AC-5 가 게이트웨이 헬퍼 6개에 대해 한 그 작업). allowlist 에 무엇을 넣을지는 **AC-0 의 측정**이 정한다.
- [ ] **AC-5 — 검증.** `:projects:platform-console:apps:console-bff:check` rc=0(파이프 금지, rc 명시). Testcontainers IT 는 CI 가 권위. 🔴 **초록 자체는 AC-4 가 닫힌 뒤에만 의미가 있다** — AC-4 전의 초록은 «서비스 이름을 서비스 이름과 대조한 것» 이다.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 (엣지 정의 · fail-closed 와 «프레임워크 내장 속성» 단서 · 섀도가 필수가 아닌 조건 · *Implementation status*) · § Error Handling (403)
- `platform/service-types/rest-api.md` § Authentication and Authorization (포인터 — console-bff 는 `Service Type: rest-api` 다)
- `platform/service-types/identity-platform.md` § Integration Rules 규칙 3 (엣지 전용 `aud` 하위 항목)
- `projects/platform-console/specs/services/console-bff/architecture.md`
- `tasks/review/TASK-MONO-698-audience-behind-the-gateway-and-at-the-other-edges.md` § AC-0 (d) · § AC-1 (c)(d) · § AC-3

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (콘솔 한 토큰의 도메인 팬아웃)

# Target Service

- platform-console `console-bff`

# Edge Cases

- **`aud` 가 배열인 토큰** — 교집합이지 «정확히 일치» 가 아니다. 배열 토큰이 통과하는 칸이 있어야 한다.
- **`aud` 가 없는 토큰** — 빈 집합이라 교집합이 비고 **거절**이다(Boot 속성 판에서도 그렇다 — 698 § AC-0 (d) 3항).
- **`audiences` 속성을 env 로 덮어쓰기** — `docker-compose*.yml` · `.env*` · `infra/demo/*.override.yml` · `.github/workflows/**` 에서의 override 는 프로젝트 안만 보는 출하값 테스트에 **안 보인다**(`TASK-MONO-695` 가 이름 붙인 구멍, 697 § Edge Cases 와 같은 내용). 켠 뒤 데모에서 실제 값을 한 번 확인한다.
- **console-bff 는 공개 호스트명이 없다**(`TEMPLATE.md:514`, `check-gateway-drift.sh` I2 가 강제) — 그래서 인바운드 모집단이 좁다. 🔴 그러나 «공개 호스트명이 없다» 는 «호출자가 하나다» 의 **증명이 아니다**(같은 네트워크 안의 e2e 하네스 — AC-0 의 ⚪).
- **콘솔 토큰은 도메인마다 팬아웃한다** — console-bff 가 받은 토큰을 그대로 여섯 게이트웨이로 보낸다. 이 티켓은 **인바운드만** 다루고 아웃바운드 `aud` 는 게이트웨이 allowlist(696/697)의 소관이다.

# Failure Scenarios

- 🔴 **속성 한 줄만 넣고 닫기** — 값이 지워지는 날 검사가 **조용히 사라지고** 아무것도 빨개지지 않는다. 계약서가 요구하는 것은 «검사가 켜졌다» 가 아니라 «꺼질 수 없다» 다. AC-1 이 막는다.
- 🔴 **픽스처를 allowlist 값으로 바꿔 IT 를 초록으로** — 테스트 초록·운영 거절(`TASK-MONO-696` Failure Scenario 1). AC-4 가 금지한다.
- 🔴 **`aud = "console-bff"` 를 allowlist 에 넣어 7개 IT 를 살리기** — **서비스 이름을 진짜 값으로 승격**하는 것이고, 696 AC-5 가 명시적으로 금지한 동작이다(714 의 `"order-service"` 와 같은 부류).
- **401 로 거절** — 콘솔이 «세션 만료» 로 읽고 재로그인 루프를 돈다. 재로그인은 발급 client 를 바꾸지 못하므로 사용자는 영원히 빠져나오지 못한다. AC-2 가 막는다.
- **AC-0 을 건너뛰고 켜기** — 소유자가 섀도를 생략한 근거 자체(«모집단이 단일 client»)가 미측정인 채 남는다 ⇒ 게이트웨이 6이 섀도로 메우고 있는 그 미지를 **두 번째로** 만든다(698 § AC-1 (d) 마지막 행).

# Test Requirements

- **기전 핀**(AC-1): allowlist 를 지우면 빨개지는 칸. 속성 판을 고르면 출하값 핀 테스트, 명시 검증기 판을 고르면 «빈 목록 → 기동 실패» 단언.
- **거절 경로**(AC-2): 실제 디코더 경로에서 allowlist 밖 `aud` → **403**. 대조군 — allowlist 안 `aud` → 200, `aud` 배열 중 하나만 일치 → 200.
- **`aud` 없는 토큰** → 거절.
- **IT 7개**(AC-4): 운영과 같은 client id 로 민팅하도록 교정한 뒤 전부 통과.
- 🔴 파이프로 판정하지 않는다 — 출력은 파일로, rc 는 명시.

# Definition of Done

- [ ] AC-0 ~ AC-5
- [ ] console-bff 가 rule 5 의 «엣지» 로서 allowlist 를 선언하고, 불일치가 403 이며, allowlist 부재/공백이 **테스트에서 빨갛다**
- [ ] IT 7개가 운영이 실제로 만드는 `aud` 를 민팅한다
- [ ] 🔴 **착수 시 소유자 확인 1건**: 이 변경은 «배포된 서비스를 새로 규칙 아래로 넣는 것» 이라 `platform/service-types/identity-platform.md` § Change Rule 의 ADR 조항에 걸리는지 판단이 갈릴 수 있다(`TASK-MONO-698` § AC-3 「내가 결정하지 않은 것」). 계약서 개정은 698 PR 에서 **이미 선행**했다 — 남은 질문은 «ADR 을 별도로 낼 것인가» 뿐이고, 그 답을 이 파일에 적고 진행한다
