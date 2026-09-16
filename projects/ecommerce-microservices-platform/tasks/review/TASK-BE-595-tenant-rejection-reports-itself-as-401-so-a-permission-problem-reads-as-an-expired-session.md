# Task ID

TASK-BE-595

# Title

테넌트 거절이 **401 로 보고된다** — `TASK-BE-501` 이 넣은 403 분기는 **실제 예외 사슬에서 도달 불가**이고, 그래서 권한 문제가 «세션 만료» 로 읽힌다

# Status

review

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

- [x] 실제 디코더 경로로 **오늘의 동작이 401** 임을 단언하는 테스트를 쓴다(엔타이틀먼트 없는 외부
      테넌트 토큰 → `expectStatus().isUnauthorized()`). 🔴 이 칸은 **고치기 전 트리에서 초록**이어야
      한다 — 그것이 «분기가 도달 불가» 의 증거다.
      - **증거 (2026-09-16 UTC)**: 새 `SecurityConfigRealDecoderPathTest` 의 AC-0 판(칸 `unentitledForeignTenant_isReportedAs401Today`).
        🔵 **Docker 없이 실제 경로**다 — 실제 `SecurityConfig` 필터 체인 + `OAuth2ResourceServerConfig#reactiveJwtDecoder()`
        (공유 validator 사슬 + ecommerce 테넌트 게이트) + MockWebServer 가 HTTP 로 내주는 JWKS 에 대한 실제 RS256 서명 검증.
        예외는 테스트가 만들지 않고 Spring 이 만든다.
      - 명령: `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:test --tests "com.example.gateway.config.SecurityConfigRealDecoderPathTest"`
        (고치기 전 트리) → **rc=0**, `tests="2" failures="0" errors="0"`. ⇒ 401 이 오늘의 동작임을 고정.
      - 🔵 고친 뒤 이 칸은 같은 파일에서 `unentitledForeignTenant_is403TenantForbidden` 으로 **뒤집혔다**(AC-1). AC-0 판은 커밋에 남지 않는다
        (고치기 전 트리에만 존재) — 이 기록이 그 판의 유일한 흔적이다.
- [x] 🔴 그 테스트가 **정말 게이트를 타는지** 먼저 증명하라(주입 확인): 같은 토큰에서 테넌트만
      맞추면 401 이 아니다 — 대조군을 같이 둔다.
      - **증거**: 같은 AC-0 실행의 두 번째 칸 `control_matchingTenant_passes` — 같은 헬퍼·같은 발급자로 `tenant_id` 만
        `ecommerce` 로 바꾸면 **200 `reached`**(401 아님). JWKS 조회·서명 검증·다운스트림 도달까지 통과한다는 뜻이라,
        401 칸의 원인은 테넌트뿐이다. 커밋된 판에는 대조군이 둘이다(엔타이틀먼트 있는 외부 테넌트 → 200 추가).
      - 🔵 사슬 모양 실측(임시 프로브, 커밋 안 함): 디코더가 던지는 것은 `JwtValidationException errs=[[tenant_mismatch] tenant_id 'globex' is not allowed]`
        — 안쪽에만 `tenant_mismatch` 가 있다(티켓 § 요지 표와 일치).

## AC-1 — 고침

- [x] 엔타이틀먼트 없는 외부 테넌트 토큰 → **403** `TENANT_FORBIDDEN`, 본문에 `UNAUTHORIZED` 없음.
      - `SecurityConfig.findTenantMismatch` — 술어 = **«`tenant_mismatch` 가 사슬 어디에든 있으면(OAuth2 예외의
        `getError()` 든 `JwtValidationException.getErrors()` 든) 403»**. 그 문장을 javadoc 첫 줄에 남겼다.
      - 증거: `SecurityConfigRealDecoderPathTest.unentitledForeignTenant_is403TenantForbidden` (+ `tenantEntitledElsewhere_is403`,
        `missingTenant_is403`) 초록.
- [x] 메트릭이 `REASON_TENANT_MISMATCH` 로 증가하고 `invalid` 는 증가하지 않는다.
      - 같은 칸이 실제 경로에서 델타로 단언: `tenant_mismatch` +1, `invalid` +0. (단위 쪽은 실제 사슬 모양 칸에서 같은 단언.)
- [x] 🔴 **회귀 금지**: 만료·서명 불일치·발급자 불일치·토큰 부재는 **여전히 401**(그 넷 각각 칸을 둔다).
      - `SecurityConfigRealDecoderPathTest$StillUnauthorized` 4칸: `expired_is401`(+ `invalid` +1 / `tenant_mismatch` +0),
        `badSignature_is401`, `wrongIssuer_is401`, `missingToken_is401` — 모두 `code=UNAUTHORIZED` 까지 단언.
      - 🔴 **각 칸이 정말 그 사유로 거절되는지 프로브로 확인했다**(칸이 «아무 이유로나 401» 이면 공허하므로):
        만료 → `JwtValidationException [invalid_token] Jwt expired…` · 서명 → `BadJwtException ← BadJWSException: Invalid signature` ·
        발급자 → `JwtValidationException [invalid_issuer] …not in the allowed list`.
      - 모듈 전체 실행 결과는 § 검증 로그.

## AC-2 — 빗나간 테스트를 고친다

- [x] `GatewayIntegrationTest` 의 `isIn(401, 403)` 두 칸 → `403` 단독 단언.
      - `protectedRoute_unentitledForeignTenant_isRefused` · `protectedRoute_tenantEntitledElsewhere_isRefused` → `isForbidden()` + `$.code=TENANT_FORBIDDEN`.
      - 🔴 **같은 파일의 세 칸이 더 바뀌어야 했다 — 셋 다 이름과 다른 것을 재고 있었다** (§ 편차 · 발견 2).
      - ⚪ **이 IT 는 이 호스트에서 돌지 못했다**: `./gradlew …:gateway-service:integrationTest --tests "com.example.gateway.GatewayIntegrationTest"`
        → **rc=1**, `initializationError` — `IllegalStateException: Could not find a valid Docker environment`
        (Docker 데몬 꺼짐: `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`).
        판정은 CI `ecommerce-integration-tests` 잡이 한다. 같은 토큰 모양의 판정은 Docker 없는 실제 경로 칸이 로컬에서 대신 쟀다.
- [x] `SecurityConfigTenantErrorMappingTest` 에 **실제 사슬 모양** 칸 추가(`InvalidBearerTokenException`
      이 `JwtValidationException` 을 감싼 형태). 🔵 기존 칸은 지우지 않는다 — 그것은 «직접 던져진
      tenant_mismatch» 를 지키는 다른 술어다.
      - 추가 2칸: `realChainShape_invalidBearerTokenWrappingJwtValidationException_mapsToForbidden`(안쪽 errs = 만료 + tenant_mismatch,
        바깥 코드가 `invalid_token` 임을 먼저 단언) · `realChainShape_withoutTenantMismatch_staysUnauthorized`(만료만 → 401). 기존 4칸 그대로.
- [x] bite: 고침을 되돌리면 새 칸들이 빨개진다(rc 기록).
      - 방법: 고친 `SecurityConfig.java` 를 스크래치로 복사 → `git show HEAD:<path> > <path>` 로 원본 복원 → 실행 → 복사본으로 되돌림
        (`git checkout` 미사용).
      - 명령: `./gradlew …:gateway-service:test --tests "…SecurityConfigRealDecoderPathTest*" --tests "…SecurityConfigTenantErrorMappingTest"` → **rc=1**,
        `16 tests completed, 5 failed`. 빨강 = 정확히 새 테넌트 칸 5개: `unentitledForeignTenant_is403TenantForbidden`,
        `tenantEntitledElsewhere_is403`, `missingTenant_is403`, `expiredAndForeignTenant_is403`(넷 다 `isForbidden()` 줄의 `AssertionError`
        — 타임아웃 아님), `realChainShape_…_mapsToForbidden`. 대조군 2 · 401 회귀 4 · 기존 매핑 4 · 만료만 칸은 초록(그래야 맞다).

## AC-3 — 형제 전수 (🔴 모집단을 세라)

- [x] wms·scm·erp·finance·fan 게이트웨이의 같은 자리를 **열어서** 센다. 같은 결함이면 몇 개인지
      적고, 이 티켓에서 함께 고칠지/분리할지 판단을 적는다.
      🔵 `libs/java-gateway` 의 `GatewayJwtDecoders.validatorChain` 이 공유이므로 **거절 경로는 같고
      보고 지점만 도메인별**이다 — 그래서 «하나만 고치면 형제가 낙오» 하는 전형적 모양이다.
      - **셈: 5 중 0 이 같은 결함.** 🔴 위 전제가 틀렸다 — 보고 지점은 도메인별이 **아니다**. 다섯 모두 보고 지점까지 공유한다.
      - 연 것: 다섯 `GatewayServiceApplication` 이 전부 `scanBasePackages = {OWN_PACKAGE, "com.example.apigateway"}` —
        `libs/java-gateway` 의 `com.example.apigateway.config.SecurityConfig` 를 그대로 등록한다. 다섯의 `src/main` 에서
        `SecurityWebFilterChain|AuthenticationEntryPoint|EnableWebFluxSecurity` grep → **0건**(자기 체인·자기 진입점 없음).
        그 공유 `SecurityConfig.extractOAuth2Error` 는 **이미 `JwtValidationException.getErrors()` 를 읽는다**(첫 번째 비-`invalid_token` 오류를 고른다).
        ecommerce 만 ADR-MONO-048 § D4 로 자기 `SecurityConfig` 를 가져서 그 판을 받지 못했다.
      - 교차 증거(Docker IT, 로컬 미실행): 다섯 모두 부팅된 게이트웨이에서 **정확한** `isForbidden()` + `TENANT_FORBIDDEN` 을 단언한다 —
        wms `GatewayRoutingAuthIntegrationTest:131` · scm `GatewayBootstrapIntegrationTest:63` · erp `GatewayRoutingIntegrationTest:137` ·
        finance `GatewayEdgeIntegrationTest:171` · fan `GatewayBootstrapIntegrationTest:45`. (`isIn(401, 403)` 는 게이트웨이 테스트 중 ecommerce 에만 있었다.)
      - **판단: 이 티켓에서 형제를 고치지 않는다. 후속 티켓도 기안하지 않는다** — 같은 결함이 없다.
      - 🔵 남는 차이 하나(결함 판정 아님, 기록만): 공유 판은 «첫 비-`invalid_token` 오류» 를 고르므로 **발급자 불일치 + 외부 테넌트**가
        함께인 토큰은 형제에서 401(`invalid_issuer` 가 먼저 나열), ecommerce 에서 403 이다. 만료 + 외부 테넌트는 양쪽 다 403.
        이 차이는 `SecurityConfig` javadoc 에 적었다.
      - 🔵 `iam-platform` 게이트웨이도 열었다(티켓 목록 밖): `src/main` 에 `tenant_mismatch`/`TenantClaimValidator`/진입점 0건 — 디코드 단계
        테넌트 게이트가 없어 이 부류에 해당 없음.

## AC-4 — 라이브 판정 (⚪ 창이 필요하면 적어라)

- [ ] ⚪ 데모 창에서 테넌트가 안 맞는 토큰으로 이커머스 게이트웨이를 부르면 **403** 이 오고, 콘솔이
      «세션 만료» 가 아니라 권한 문구를 보인다.
      - ⚪ **못 쟀다 — 데모 인스턴스가 꺼져 있다.** 이 티켓만을 위해 켜지 않았다(`aws` 명령 미실행).
- [x] 창을 못 열면 ⚪ 로 두고 갈 곳을 적는다(`TASK-MONO-672`).
      - `tasks/ready/TASK-MONO-672-…` § 넘겨받은 항목에 **항목 4** 로 넘겼다(무엇을 재나 · 창만으로 풀리는가 · 출처).

---

# 검증 로그 (2026-09-16 UTC)

| 단계 | 명령 | rc | 결과 |
|---|---|---|---|
| AC-0 (고치기 전) | `gradlew …:gateway-service:test --tests "…SecurityConfigRealDecoderPathTest"` | 0 | 2/2 초록 (401 고정 + 대조군 200) |
| 고친 뒤 모듈 전체 | `gradlew …:gateway-service:test` | 0 | 실제 경로 10 · 매핑 6 포함, 실패 0 |
| bite (고침 되돌림) | `gradlew …:gateway-service:test --tests "…RealDecoderPathTest*" --tests "…TenantErrorMappingTest"` | 1 | 16 중 5 빨강 = 새 테넌트 칸 전부 |
| bite 복원 후 모듈 전체 | `gradlew …:gateway-service:test` | 0 | 실패 0 |
| 최종 트리 모듈 전체 (캐시 배제) | `gradlew …:gateway-service:test --rerun` | 0 | 124 칸 · 실패 0 · 오류 0 (실제 실행, `FROM-CACHE` 아님) |
| Docker IT | `gradlew …:gateway-service:integrationTest --tests "…GatewayIntegrationTest"` | 1 | ⚪ 환경 차단 — `Could not find a valid Docker environment` |

🔴 `test` 태스크는 `@Tag("integration")` 을 제외한다(`projects/ecommerce-microservices-platform/build.gradle` `excludeTags 'integration'`) —
위 «모듈 전체» 초록은 `GatewayIntegrationTest` 를 **포함하지 않는다.**

---

# 편차 · 발견 (구현 중)

1. **테스트 파일 하나 추가** — `SecurityConfigRealDecoderPathTest`(Docker 없는 실제 경로). AC-0 은 «실제 디코더 경로» 를 요구했고,
   기존 실제 경로 스위트(`GatewayIntegrationTest`)는 Docker 가 필요해 이 호스트에서 고치기 전/후를 가를 수 없었다.
2. 🔴 **`GatewayIntegrationTest` 에서 `isIn` 두 칸 외에 세 칸이 더 바뀌었다 — 셋 다 이름과 다른 것을 쟀다.**
   - `protectedRoute_expiredToken_returns401`: 픽스처가 `TTL -1s` · `iat=now` 였다. `exp < iat` 이면 Spring 의 `Jwt` 빌더가
     `expiresAt must be after issuedAt` 로 **어떤 validator 보다 먼저** `BadJwtException` 을 던진다(프로브 실측) — 401 은 «만료» 가 아니라
     «형식 오류» 였다. `iat` 를 2시간 전으로 덮어쓰고 `tenant_id=ecommerce` 를 넣어 만료가 **유일한** 사유가 되게 했다. 기대값 401 은 그대로.
   - `protectedRoute_wrongAudience_returns401`: 🔴🔴 **audience 는 검증되지 않는다.** 게이트웨이 디코더는 `OAuth2ResourceServerConfig` 의
     자체 빈이라 Boot 의 `…jwt.audiences` 속성이 적용되지 않는다. 실측(임시 프로브): `aud` 없음 + `tenant_id=ecommerce` → **200**.
     이 칸의 토큰은 `tenant_id` 도 없어서 거절 사유는 테넌트뿐이었다 → 고친 뒤 403. 이름을 `protectedRoute_noAudienceNoTenant_returns403TenantForbidden`
     으로 바꾸고 403 `TENANT_FORBIDDEN` 을 단언했다. **audience 미검증 자체는 이 티켓 범위 밖이라 고치지 않았고 티켓도 기안하지 않았다 — 판단이 필요하다.**
   - `protectedRoute_missingTenant_returns401` → `…_returns403`: Edge Case(«`tenant_id` blank → 403») 대로. 이 칸은 옛 401 을 단언하고 있었다.
3. **혼합 사유의 선택**: 술어를 티켓 문장 그대로(«어디에든 있으면 403») 두었으므로 **만료 + 외부 테넌트 → 403** 이다. 재인증으로 테넌트는
   고쳐지지 않으므로 의도된 선택으로 보고 칸(`expiredAndForeignTenant_is403`)으로 고정했다. Failure Scenario 2 가 막는 것은 «테넌트가 멀쩡한
   만료 토큰» 의 403 이고, 그것은 `expired_is401` 이 지킨다. **발급자 불일치 + 외부 테넌트** 도 같은 술어로 403 이지만 칸으로 고정하지 않았다(AC-3 의 형제 차이).
4. `WebTestClient` 응답 타임아웃 60s — 첫 칸이 컨텍스트 기동 + 첫 JWKS 조회를 떠안아 기본 5s 에서 정확히 5s 타임아웃이 났다(그 칸 12.7s 실측).

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
