# Task ID

TASK-BE-642

# Title

`/oauth2/authorize` 의 **redirect 할 수 없는 오류**(미등록 `redirect_uri` · 모르는 `client_id` 등)가 400 대신 **401 «Missing or invalid internal credentials»** 로 나간다 — `TASK-BE-641` 과 같은 `/error` → Order 2 `denyAll` 기전

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus(SAS authorize 오류 처리 · 사람이 보는 화면 결정이 끼어 있다)
> 기안 2026-10-21 UTC — `TASK-BE-641` 소유자 결정 **D2**(«logout 만. authorize 오류의 같은 401 은 별도 티켓으로 기안»)의 산출물. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-BE-641` § 소유자 결정 D2 · § AC-0 측정 2번(«같은 무리: `V0034…sql:85-86`»).
- 선행: `TASK-BE-641` 이 머지된 뒤 착수 권장 — 같은 두 파일(`AuthorizationServerConfig.java` · `auth-api.md`)을 고치고, 641 의 `OidcLogoutErrorResponseHandler` · 실제-Tomcat 시험(`OidcLogoutEndpointRealServerIntegrationTest`)이 이 티켓의 본보기다(공유 파일 시리즈 = 직렬).
- 관련: `TASK-MONO-801` (같은 기전 — 방화벽 거절의 `sendError(400)` → `/error` → 401, `RequestRejectedHandler` 로 해결). 801 은 «`/error` · ERROR 디스패치 허용» 을 **기각**했다(`tasks/review/TASK-MONO-801-…md` § Options (b)) — 이 티켓도 그 선택지를 쓰지 않는다.

# Goal

`GET /oauth2/authorize` 가 **redirect 로 돌려줄 수 없는 오류**(RFC 6749 § 4.1.2.1 — `redirect_uri` 를 믿을 수 없거나 `client_id` 를 모를 때)를 낼 때, 응답이 내부 자격 401 이 아니라 **400 + 그 오류를 말하는 본문**이 된다. 지금은 소비자 사이트 운영자가 미등록 콜백 하나 때문에 «모든 컨테이너 정상 · 로그인 폼 200 · 콜백만 401, 메시지는 client 도 redirect_uri 도 말하지 않음»(`V0034__add_console_vercel_domain_redirect_uri.sql:82-91`)을 본다.

# Scope

## In Scope

- **AC-0 측정** (실제 Tomcat — MockMvc 는 ERROR 디스패치가 없어 401 을 원리상 못 본다, `TASK-BE-641` AC-0):
  - SAS 1.4.1 `OAuth2AuthorizationEndpointFilter` 의 오류 처리 중 `sendError` 로 가는 갈래 전부(file:line 또는 바이트코드) — 미등록 `redirect_uri` · 모르는 `client_id` · `redirect_uri` 없음/중복 등. redirect 로 가는 갈래(`?error=…`)는 이미 정상인지.
  - 같은 기전을 타는 다른 SAS 엔드포인트가 남아 있는가 — `/oauth2/token` · `/oauth2/revoke` · `/oauth2/introspect` · `/oauth2/userinfo` · discovery · `/connect/logout`(641 이후) 각각 오류 하나씩 실제 Tomcat 으로.
  - `AuthorizeSessionTenantGate` · `AuthorizeSecondFactorGate` · `PendingSiteConsentStore` 가 `sendError` 를 쓰는가.
  - 누가 이 응답을 보는가: 브라우저(사람) — 본문 형식(JSON · HTML 오류 화면)은 **소유자 결정**이다. 641 은 logout 에 JSON 을 골랐다(D1 a).
- 수선(AC-0 · 소유자 결정이 정한다) + 시험(실제 Tomcat, `RANDOM_PORT` + 실제 HTTP 클라이언트).
- `specs/contracts/http/auth-api.md` § GET /oauth2/authorize 의 **Errors** 표를 실제 응답으로 정정(지금 표는 «400 `invalid_request`» 라고 적지만 실제는 401 이다 — 계약 먼저).

## Out of Scope

- Order 2 체인에 `/error` 또는 `DispatcherType.ERROR` 를 여는 것(801 이 기각, Failure Scenarios 1).
- `/connect/logout` (`TASK-BE-641`).
- redirect 로 돌려주는 오류(`access_denied` 등)의 형식 변경.

# Acceptance Criteria

- [ ] **AC-0** — 위 측정을 file:line 으로. `sendError` 갈래 목록 · 같은 기전의 다른 엔드포인트 유무 · 본문 형식 선택지(JSON / HTML 오류 화면)와 그 대가.
- [ ] **AC-1** — 미등록 `redirect_uri` · 모르는 `client_id` 의 `/oauth2/authorize` → **400**, 본문이 OAuth 오류 코드를 말한다(내부 자격 401 아님 · 요청 값을 되풀이하지 않음). 형식은 소유자 결정대로.
- [ ] **AC-2** — 실제 Tomcat 시험 빨강(401) → 초록 · 대조: 정상 authorize 302 · `/internal/auth/…` Bearer 없음 여전히 401 내부 자격 · redirect 형 오류는 그대로 302 `?error=`.
- [ ] **AC-3** — 배포(굽기) · 라이브(미등록 콜백 하나로 400 확인, 다음 창).

# Related Specs

- `projects/iam-platform/specs/services/auth-service/architecture.md`
- `platform/error-handling.md` (JSON 을 고르면 그 형식과의 관계를 적는다 — OAuth 엔드포인트는 OAuth 오류 형식)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` § GET /oauth2/authorize (Errors 표 정정) · § GET /connect/logout (641, 같은 원칙)
- RFC 6749 § 4.1.2.1 (redirect 할 수 없는 오류는 사용자에게 알리고 redirect 하지 않는다)

# Edge Cases

- 오류가 redirect 형인지 아닌지는 `redirect_uri` 검증 **결과**에 달렸다 — 검증 전에 실패하는 요청(`client_id` 없음)과 검증 후 실패(`scope` 무효 → redirect)가 섞인다. 시험은 두 갈래를 다 본다.
- 미인증 브라우저 요청은 먼저 `/login` 으로 간다(entry point) — 오류가 로그인 **전**에 나는지 **후**에 나는지(저장된 요청 재개 시) 둘 다 확인.
- `prompt=none` 은 SAS 가 `login_required` redirect 로 처리 — 바뀌면 안 된다.

# Failure Scenarios

1. Order 2 에 `/error` 를 열어 Boot 기본 본문(`path` · 예외 메시지)이 새어 나간다 — 801 이 측정한 누출 경로.
2. MockMvc 로 초록 — ERROR 디스패치가 없어 고치기 전에도 400 으로 보인다.
3. 오류 본문에 `redirect_uri` · `client_id` 값을 그대로 실어 반사형 주입 표면을 만든다.

---

## AC-0 측정 (2026-10-21 UTC · 창 없음 · 분석=Opus 5.5)

**결론: `/oauth2/authorize` 의 오류는 SAS 1.4.1 `OAuth2AuthorizationEndpointFilter` 의 실패 핸들러 하나로 모인다. 그 기본 핸들러는 «SAS 가 오류에 믿을 수 있는 `redirect_uri` 를 붙였는가» 하나로 갈라 — 붙였으면 client 로 302, 안 붙였으면 `sendError(400)` 한다. 뒤쪽이 641 과 같은 `/error` → Order 2 `denyAll` → 401 이다.** 수단은 바이트코드(`javap -c -p -constants`, `spring-security-oauth2-authorization-server-1.4.1.jar`, Boot 3.4.1) — 🔴 이 호스트엔 Docker 가 없어 실제 Tomcat 측정은 CI 에서만 했다(§ 구현 · 시험).

1. **분기점** [실측, javap]: `OAuth2AuthorizationEndpointFilter.doFilterInternal` 은 converter · `AuthenticationManager` 의 `OAuth2AuthenticationException` 을 모두 잡아 `authenticationFailureHandler` 로 넘긴다(예외 표 offset 22-166 → 169). 기본 핸들러 `sendErrorResponse`: 예외를 `OAuth2AuthorizationCodeRequestAuthenticationException` 으로 cast → 그 토큰이 `null` 이거나 `getRedirectUri()` 가 비면 `sendError(400, error.toString())`(offset 36-53), 아니면 `redirect_uri?error=…[&error_description=…][&error_uri=…][&state=…]`(각각 `UriUtils.encode`, `build(true)`) 로 `DefaultRedirectStrategy.sendRedirect`(offset 54-244). 설정 지점 = `OAuth2AuthorizationEndpointConfigurer.errorResponseHandler(...)` — 이 서비스는 지금까지 쓰지 않았다(`authorizationEndpoint(` 0건).
2. **`sendError`(→ 401) 로 가는 갈래** [실측, javap]:
   - **converter** `OAuth2AuthorizationCodeRequestAuthenticationConverter.throwError` 는 **언제나 토큰 `null`** → 전부 400: `response_type` 없음·중복(`invalid_request`) · `response_type ≠ code`(`unsupported_response_type` — 🔴 RFC 는 redirect 를 허용하지만 SAS 1.4.1 은 안 한다) · `client_id` 없음·중복 · `redirect_uri` · `scope` · `state` · `code_challenge` · `code_challenge_method` · `prompt` 중복.
   - **provider** `OAuth2AuthorizationCodeRequestAuthenticationProvider.authenticate` offset 19-29: 모르는 `client_id` → `invalid_request`/`client_id`; 그 `throwError` 는 `invalid_request` + (`client_id` | `state`) 이면 redirect URI 를 `null` 로 지운다(offset 9-41).
   - **validator** `OAuth2AuthorizationCodeRequestAuthenticationValidator.validateRedirectUri` → `invalid_request`/`redirect_uri` 네 곳(fragment·파싱 불가 · 비-loopback 미등록 · loopback 불일치 · 생략인데 `openid` 이거나 등록 URI 가 여럿), 그 `throwError` 가 `invalid_request`+`redirect_uri` 면 `null`(offset 36-58) → 400.
   - 동의 POST(`OAuth2AuthorizationConsentAuthenticationProvider`) 의 `invalid_request`/`state` · `client_id` 도 `null` → 400 (이 서비스의 client 는 `require-authorization-consent=false` — V0015:94 — 라 도달은 드묾).
   - 오류 설명은 셋 다 고정 문장 `"OAuth 2.0 Parameter: " + 이름`(상수 풀 `OAuth 2.0 Parameter: \u0001`) — 요청 값을 담지 않는다.
3. **redirect 로 가는 갈래(이미 정상, 바꾸면 안 됨)**: `invalid_scope`(validator `validateScope`) · `unauthorized_client`(grant 없음, provider offset 124-130) · PKCE `invalid_request`/`code_challenge`·`code_challenge_method`(offset 137-225) · `prompt` 의 `invalid_request` · `login_required` · `consent_required`(offset 237-564) · 동의 `invalid_scope` · `access_denied`. 검증은 모두 «principal 이 인증됐나» **전에** 돈다 → 미인증 브라우저에도 같은 응답(Edge Case 2: 로그인 전 · 저장 요청 재개 후 둘 다 같은 핸들러).
4. **같은 기전의 다른 엔드포인트** [실측, javap — 13개 SAS 필터의 `sendError` 호출 전수]: `sendError` 는 `OAuth2AuthorizationEndpointFilter` · `OidcLogoutEndpointFilter`(641 이 고침) · `OAuth2DeviceVerificationEndpointFilter` 셋에만 있다. device 흐름은 이 서비스가 켜지 않았다(`deviceAuthorization`/`deviceVerification` 설정 0 → endpoints matcher 밖). token · revoke · introspect · client 인증 · userinfo · JWKS · discovery 는 본문을 직접 쓴다 → 해당 없음. 실제 Tomcat 대조(token 오류 · userinfo Bearer 없음)는 IT 에 넣어 CI 로 확인(§ 구현).
5. **게이트** [실측, grep]: `AuthorizeSessionTenantGate`(`:257` · `:264`) · `AuthorizeSecondFactorGate`(`:95`) 는 `sendRedirect` 만 쓰고, `PendingSiteConsentStore` 는 응답을 쓰지 않는다 — `sendError` 0건(`apps/auth-service/src/main` 전체).
6. **본문 형식** — 누가 보나: 브라우저(사람). 선택지: (a) 641 D1 (a) 와 같은 OAuth JSON 400 — 대가: 사람에게는 영문 JSON 한 줄, 하지만 `error` 코드가 원인(`client_id`/`redirect_uri`)을 말하고 logout 과 한 모양; (b) HTML 오류 화면 — 템플릿 · 문구 · i18n 이 새로 생기고 `/error` 를 쓰지 않는 별도 렌더 경로가 필요; (c) `/error` 열기 — 801 기각(Failure Scenario 1). **구현은 (a) — 641 소유자 결정 D1 (a) 를 그대로 따랐다(조정자 지시). 🔴 이 티켓 자체의 소유자 확인은 머지 전에 받아야 한다(보이는 본문 = § 구현).**
7. **`V0034…sql:82-91` 의 주석** — 적용된 Flyway 마이그레이션이라 **고치지 않았다**(Flyway 체크섬은 주석까지 포함 → 수정하면 기존 DB 의 `validate` 가 깨진다). 같은 401 서술이 `V0031__add_fan_web_demo_host_redirect_uri.sql` · `V0033__add_fan_vercel_domain_redirect_uri.sql` 헤더와 `infra/demo/seed-demo-domain.sh:21`(루트 경로 — 이 티켓 범위 밖) 에도 있다. 배포 뒤 미등록 콜백의 증상은 «401 내부 자격» 이 아니라 «400 `{"error":"invalid_request","error_description":"OAuth 2.0 Parameter: redirect_uri"}`» 다 — 그 정정은 새 마이그레이션 · 스크립트 수정이 아니라 계약(`auth-api.md` § GET /oauth2/authorize Errors)이 맡는다.
