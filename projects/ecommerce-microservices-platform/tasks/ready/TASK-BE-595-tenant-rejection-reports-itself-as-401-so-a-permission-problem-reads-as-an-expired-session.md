# Task ID

TASK-BE-595

# Title

테넌트 거절이 **401 로 보고된다** — `TASK-BE-501` 이 넣은 403 분기는 **실제 예외 사슬에서 도달 불가**이고, 그래서 권한 문제가 «세션 만료» 로 읽힌다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- security

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (인증 경계 + 죽은 분기의 부활이라 테스트 설계가 본체다)

---

# Required Sections (must exist)

- Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios

---

# Goal

**테넌트 때문에 거절된 토큰이 403 `TENANT_FORBIDDEN` 으로 보고된다.** 오늘은 401 `UNAUTHORIZED`
로 나가고, 소비자(콘솔)는 그것을 «세션이 만료되었습니다» 로 번역해 **재로그인해도 소용없는
상태에서 사용자를 재로그인시킨다.**

---

# 🔴🔴 요지 — `TASK-BE-501` 은 분기를 넣었고, 그 분기는 **한 번도 실행된 적이 없다**

`SecurityConfig.unauthorizedEntryPoint`(`:113-130`)는 예외 **원인 사슬에서 처음 만나는**
`OAuth2AuthenticationException` 의 코드가 `tenant_mismatch` 면 403 을 낸다(`:140 extractOAuth2Error`).
그런데 실제 사슬의 모양은 그렇지 않다. 2026-09-16 **바이트코드로 확인**(로컬 Gradle 캐시,
`spring-security-*-6.4.2.jar`, `javap -c`):

| 단계 | 확인된 사실 |
|---|---|
| 테넌트 게이트 실패 | `TenantClaimValidator` → `JwtValidationException` |
| 상속 | `JwtValidationException extends BadJwtException extends JwtException` |
| Spring 의 변환 | `JwtReactiveAuthenticationManager.onError`: `instanceof BadJwtException` → **`new InvalidBearerTokenException(msg, cause)`** |
| 그 예외의 코드 | `InvalidBearerTokenException` → `BearerTokenErrors.invalidToken(...)` → **`ldc "invalid_token"`** (`OAuth2ErrorCodes.INVALID_TOKEN`) |
| ⇒ 결과 | 사슬의 **바깥**이 `invalid_token`, `tenant_mismatch` 는 그 **안쪽 비-OAuth2 예외**(`JwtValidationException.getErrors()`)에만 있다 ⇒ 술어가 바깥을 먼저 만나 **항상 401** |

## 🔴 왜 테스트가 초록인가 (두 겹 다 빗나갔다)

- `SecurityConfigTenantErrorMappingTest:62` 는 «감싸인 경우» 를 재지만, **자기가 만든 사슬의 모양이
  실제와 다르다** — 안쪽에 `tenant_mismatch` 를 든 **`OAuth2AuthenticationException`** 을 넣는다.
  실제 안쪽은 `JwtValidationException`(OAuth2 예외가 아니다)이다.
- `GatewayIntegrationTest:283-286`·`:302-303` 은 **`isIn(401, 403)`** 으로 둘 다 허용한다.
  ⇒ 이 결함은 **어느 쪽으로 가든 초록**이다.

## 🔵 사용자에게 보이는 것 (2026-09-16 라이브)

콘솔이 도메인 화면에서 이 401 을 받고 `/login?error=session_expired` 로 보낸다. 사용자는 세션이
멀쩡한데 «만료» 를 보고, 재로그인해도 같은 곳으로 돌아온다. 그 사건의 근본 원인(콘솔이 운영용
슬러그 `iam` 을 활성 테넌트로 삼는 것)은 **`TASK-PC-FE-292`** 가 따로 다룬다 — 이 티켓은
«거절을 어떻게 보고하는가» 만 고친다. 🔴 **둘은 독립이다**: 292 를 고쳐도 다른 이유로 테넌트가
거절되는 날 이 거짓말은 그대로 돌아온다.

---

# Scope

## In Scope

- `SecurityConfig.extractOAuth2Error`(또는 그 자리)가 **실제 사슬에서** `tenant_mismatch` 를 찾아낸다.
  최소한 `JwtValidationException.getErrors()` 를 들여다봐야 한다(사슬을 훑되, OAuth2 예외가 아닌
  프레임의 오류 목록도 본다).
- `GatewayIntegrationTest` 의 `isIn(401, 403)` 두 칸을 **정확한 기대값**으로 좁힌다.
- `SecurityConfigTenantErrorMappingTest` 에 **실제 모양의 사슬**(`InvalidBearerTokenException` 이
  `JwtValidationException` 을 감싼 것) 칸을 추가한다.
- 🔵 형제 게이트웨이 점검: wms·scm·erp·finance·fan 이 같은 자리를 어떻게 처리하는지 **세고**,
  같은 결함이면 이 티켓의 범위에 넣을지 판단해 적는다(`libs/java-gateway` 가 검증기 사슬을 공유하므로
  같은 모양일 가능성이 높다 — 세기 전에는 단정하지 않는다).

## Out of Scope

- 콘솔의 활성 테넌트 기본값 결함 → **`TASK-PC-FE-292`**.
- 테넌트 게이트의 **판정 자체**(누가 통과해야 하는가)는 변경하지 않는다 — 거절은 그대로 거절이고,
  바뀌는 것은 **보고 코드**뿐이다.
- 401 을 받는 소비자(콘솔)의 «세션 만료» 번역 규칙. 403 이 오면 기존 403 처리(inline «권한 없음»)를
  탄다.

---

# Acceptance Criteria

## AC-0 — 재현을 먼저 고정한다 (🔴 고치기 전에)

- [ ] 실제 디코더 경로로 **오늘의 동작이 401** 임을 단언하는 테스트를 쓴다(엔타이틀먼트 없는 외부
      테넌트 토큰 → `expectStatus().isUnauthorized()`). 🔴 이 칸은 **고치기 전 트리에서 초록**이어야
      한다 — 그것이 «분기가 도달 불가» 의 증거다.
- [ ] 🔴 그 테스트가 **정말 게이트를 타는지** 먼저 증명하라(주입 확인): 같은 토큰에서 테넌트만
      맞추면 401 이 아니다 — 대조군을 같이 둔다.

## AC-1 — 고침

- [ ] 엔타이틀먼트 없는 외부 테넌트 토큰 → **403** `TENANT_FORBIDDEN`, 본문에 `UNAUTHORIZED` 없음.
- [ ] 메트릭이 `REASON_TENANT_MISMATCH` 로 증가하고 `invalid` 는 증가하지 않는다.
- [ ] 🔴 **회귀 금지**: 만료·서명 불일치·발급자 불일치·토큰 부재는 **여전히 401**(그 넷 각각 칸을 둔다).

## AC-2 — 빗나간 테스트를 고친다

- [ ] `GatewayIntegrationTest` 의 `isIn(401, 403)` 두 칸 → `403` 단독 단언.
- [ ] `SecurityConfigTenantErrorMappingTest` 에 **실제 사슬 모양** 칸 추가(`InvalidBearerTokenException`
      이 `JwtValidationException` 을 감싼 형태). 🔵 기존 칸은 지우지 않는다 — 그것은 «직접 던져진
      tenant_mismatch» 를 지키는 다른 술어다.
- [ ] bite: 고침을 되돌리면 새 칸들이 빨개진다(rc 기록).

## AC-3 — 형제 전수 (🔴 모집단을 세라)

- [ ] wms·scm·erp·finance·fan 게이트웨이의 같은 자리를 **열어서** 센다. 같은 결함이면 몇 개인지
      적고, 이 티켓에서 함께 고칠지/분리할지 판단을 적는다.
      🔵 `libs/java-gateway` 의 `GatewayJwtDecoders.validatorChain` 이 공유이므로 **거절 경로는 같고
      보고 지점만 도메인별**이다 — 그래서 «하나만 고치면 형제가 낙오» 하는 전형적 모양이다.

## AC-4 — 라이브 판정 (⚪ 창이 필요하면 적어라)

- [ ] 데모 창에서 테넌트가 안 맞는 토큰으로 이커머스 게이트웨이를 부르면 **403** 이 오고, 콘솔이
      «세션 만료» 가 아니라 권한 문구를 보인다.
- [ ] 창을 못 열면 ⚪ 로 두고 갈 곳을 적는다(`TASK-MONO-672`).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` → 선언된 domain/trait.

- `platform/api-gateway-policy.md`
- `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md` § 2
- `ADR-MONO-019` § D5 (엔타이틀먼트 신뢰) · `ADR-MONO-030` § D1-A
- `TASK-BE-501` — 403 분기를 넣은 티켓(그 분기가 실행되지 않는다는 것이 이 티켓이다)
- `TASK-MONO-388` — 게이트가 «아무 테넌트나» 에서 «엔타이틀먼트» 로 좁혀진 변경

# Related Skills

- `.claude/skills/INDEX.md` 참조

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.5 (401→재로그인 / 403→inline 매핑의 소비자 쪽)

---

# Target Service

- `gateway-service` (ecommerce), 형제 판단 결과에 따라 wms/scm/erp/finance/fan 게이트웨이

---

# Architecture

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- 판정 자리: `apps/gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java:140`
  (`extractOAuth2Error`) — 사슬을 훑되 **`JwtValidationException` 의 `getErrors()`** 도 본다.
- 🔴 `OAuth2Error` 를 «처음 만나는 것» 으로 고르는 규칙 자체가 함정이었다. 고칠 때
  **«tenant_mismatch 가 사슬 어디에든 있으면 403»** 이 되도록 술어를 바꾸고, 그 문장을 주석으로 남겨라.
- 바이트코드 근거(재현 명령 포함)는 이 티켓 § 요지 표에 있다 — 다시 재려면 `javap -p -c` 로
  `JwtReactiveAuthenticationManager` · `InvalidBearerTokenException` · `BearerTokenErrors` 를 본다.

---

# Edge Cases

- 토큰이 아예 없다 → 401(변화 없음)
- 만료 토큰 → 401(변화 없음)
- 서명·발급자 불일치 → 401(변화 없음)
- `tenant_id` 가 blank → `tenant_mismatch` 계열이므로 **403** (오늘도 의도는 그랬다)
- SUPER_ADMIN 와일드카드 + 쓰기 메서드 → 기존 `AccountTypeEnforcementFilter` 의 403 경로 불변

# Failure Scenarios

1. **술어만 고치고 통합 테스트의 `isIn(401,403)` 을 그대로 둔다** → 다음 회귀가 조용히 통과한다.
2. **401 을 전부 403 으로 바꾼다** → 만료 토큰에도 «재인증해도 소용없다» 고 말하게 되어 정반대의
   거짓말이 된다(AC-1 의 회귀 칸이 그것을 막는다).
3. **형제 게이트웨이를 안 센다** → 같은 결함이 네 곳에 남고, 다음 사람이 «ecommerce 는 고쳐졌으니
   이 부류는 끝났다» 고 읽는다.
