# Task ID

TASK-BE-642

# Title

`/oauth2/authorize` 의 **redirect 할 수 없는 오류**(미등록 `redirect_uri` · 모르는 `client_id` 등)가 400 대신 **401 «Missing or invalid internal credentials»** 로 나간다 — `TASK-BE-641` 과 같은 `/error` → Order 2 `denyAll` 기전

# Status

done

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

- [x] **AC-0** — 위 측정을 file:line 으로. `sendError` 갈래 목록 · 같은 기전의 다른 엔드포인트 유무 · 본문 형식 선택지(JSON / HTML 오류 화면)와 그 대가. → § AC-0 측정 (바이트코드 + CI 실제 Tomcat).
- [ ] **AC-1** — 미등록 `redirect_uri` · 모르는 `client_id` 의 `/oauth2/authorize` → **400**, 본문이 OAuth 오류 코드를 말한다(내부 자격 401 아님 · 요청 값을 되풀이하지 않음). 형식은 소유자 결정대로. → § 구현: 400 OAuth JSON 은 CI 실제 Tomcat 으로 측정됨. 🔴 **«형식은 소유자 결정대로» 가 아직 안 닫혔다** — 641 D1 (a) 를 따라 JSON 으로 구현했고, 이 티켓의 소유자 확인(§ 사용자에게 보이는 본문)을 받으면 닫는다.
- [x] **AC-2** — 실제 Tomcat 시험 빨강(401) → 초록 · 대조: 정상 authorize 302 · `/internal/auth/…` Bearer 없음 여전히 401 내부 자격 · redirect 형 오류는 그대로 302 `?error=`. → § 구현 — CI 빨강 4/11 실패(전부 401) → 초록 11/11.
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

## 구현 (2026-10-21 UTC · 구현=Opus 5.5 · PR #4445)

브랜치 `task/be-642-authorize-error-400`(base `a7ed65949`). 커밋 순서 = 계약 + 시험 + AC-0(빨강 `56ef1b268`) → 수정(초록 `199f11476`) → 이 기록.

### 바뀐 것

- **계약 먼저**: `specs/contracts/http/auth-api.md` § GET /oauth2/authorize **Errors** 를 두 갈래로 다시 썼다 — (1) redirect 불가 → 400 `application/json` `{error, error_description}` 표(converter · provider · validator 갈래 전부), (2) redirect 형 → 302 `redirect_uri?error=…` 표(그대로). 옛 표의 «미등록 client → `invalid_client`»(실제 `invalid_request`/`client_id`) · «PKCE 미포함 → 400»(실제 302 redirect) 정정.
- **수정**: `apps/auth-service/.../oauth2/AuthorizationEndpointErrorResponseHandler.java` (신규) — SAS 가 오류에 `redirect_uri` 를 남겼으면(`redirectableRequest`, SAS 기본과 같은 판정) SAS 기본의 redirect 갈래를 **그대로 옮겨** 302, 아니면 400 본문을 직접 쓴다(`sendError` 없음 → `/error` 없음). INFO 한 줄 `Authorization request rejected without redirect: error=… description=…`(요청 값 없음). 배선 `AuthorizationServerConfig.java:256-258` `.authorizationEndpoint(authorization -> authorization.errorResponseHandler(...))`.
  - 🔵 «SAS 기본에 위임» 은 할 수 없었다 — 기본 핸들러는 `OAuth2AuthorizationEndpointFilter` 의 **private** `sendErrorResponse` 메서드 참조라 밖에서 부를 길이 없다(리플렉션은 버전마다 깨질 표면). 대신 redirect 갈래(`redirectToClient`)를 그대로 옮기고, 단위 시험이 **같은 요청을 SAS 기본 필터에도 통과시켜 `Location` 이 같음**을 단언한다(SAS 를 올리면 그 시험이 먼저 갈라짐을 알린다).
- **공유**: 641 의 본문 기록부(경계 · `error_uri` 제거 · `no-store` · `OAuth2ErrorHttpMessageConverter`)를 `DirectOAuth2ErrorResponse.java`(신규, package-private)로 뽑고 `OidcLogoutErrorResponseHandler` 가 그것을 쓰게 했다 — logout 의 동작 · 로그 문구는 그대로(641 단위 4/4 · 실제 Tomcat 5/5 가 초록 런에서 다시 통과).
- **Order 2 · `/error` · `/connect/**` 는 손대지 않았다.** 적용된 마이그레이션(`V0031` · `V0033` · `V0034`) 주석도 그대로(AC-0 7번).

### 사용자에게 보이는 본문 (소유자 확인용)

상태 · `Content-Type` 접두 · 본문 = CI 실제 Tomcat 단언, `Cache-Control` = 단위 시험 단언. 모르는 `client_id` · `client_id` 없음:

```
HTTP/1.1 400
Content-Type: application/json
Cache-Control: no-store

{"error":"invalid_request","error_description":"OAuth 2.0 Parameter: client_id"}
```

미등록 `redirect_uri`(«미등록 콜백» — V0034 이 적은 사례): `{"error":"invalid_request","error_description":"OAuth 2.0 Parameter: redirect_uri"}`. `response_type=token`: `{"error":"unsupported_response_type","error_description":"OAuth 2.0 Parameter: response_type"}`. 수정 전 넷 모두 401 `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}`(빨강 런 본문 그대로). redirect 형(`invalid_scope` 등)은 바뀌지 않았다 — 예 `http://localhost:3000/api/auth/callback?error=invalid_scope&error_description=OAuth%202.0%20Parameter%3A%20scope&error_uri=…&state=be-642`.

### 시험

- **`AuthorizeEndpointErrorRealServerIntegrationTest`** (신규, `RANDOM_PORT` + `java.net.http.HttpClient`, redirect 안 따름, 프로필 `integration-test` — 641 IT 와 같은 이유). 로그인 없이(검증이 인증 확인보다 먼저 돈다):
  | 케이스 | 빨강 `56ef1b268` (CI run 39586629474 · iam B job 119177636195) | 초록 `199f11476` (CI run 39587464433 · iam B job 119180479895) |
  |---|---|---|
  | 모르는 `client_id` → 400 `invalid_request`/`client_id` | **FAILED** `expected: 400 but was: 401` | PASSED |
  | 미등록 `redirect_uri` → 400 `invalid_request`/`redirect_uri` | **FAILED** 401 | PASSED |
  | `client_id` 없음 → 400 | **FAILED** 401, body `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}` | PASSED |
  | `response_type=token` → 400 `unsupported_response_type` | **FAILED** 401 | PASSED |
  | 대조 `invalid_scope` → 302 `redirect_uri?error=invalid_scope…&state=be-642` | PASSED | PASSED |
  | 대조 `code_challenge` 없음 → 302 `?error=invalid_request…code_challenge` | PASSED | PASSED |
  | 대조 `prompt=none` 미인증 → 302 `?error=login_required` (Edge Case 3) | PASSED | PASSED |
  | 대조 정상 · 미인증 → 302 `/login` | PASSED | PASSED |
  | 대조 `POST /internal/auth/credentials` Bearer 없음 → 401 내부 자격 | PASSED | PASSED |
  | AC-0 `/oauth2/token` 오류 → 자기 OAuth JSON(내부 자격 아님) | PASSED | PASSED |
  | AC-0 `/oauth2/userinfo` Bearer 없음 → 내부 자격 아님 | PASSED | PASSED |
  빨강 런 iam B 전체 181 tests / 4 failed(이 넷뿐), 초록 런 iam B `BUILD SUCCESSFUL`(같은 런에서 `OidcLogoutEndpointRealServerIntegrationTest` 5/5 PASSED — 공유 추출의 회귀 없음).
- **`AuthorizationEndpointErrorResponseHandlerTest`** (신규, 단위, Docker 불필요, 8) — SAS 1.4.1 실제 `OAuth2AuthorizationEndpointFilter`(기본 converter) 뒤: converter 오류 → 400 · 두 필드 · `getErrorMessage()==null` · AM/chain 미호출; **같은 요청에 SAS 기본은 `sendError`(결함의 증인)**; provider 거절(redirect 비움) → `error_uri` · 요청 값(`attacker.example` · client id · state) 없음; **redirect 형 두 개(인코딩이 필요한 `state` 포함 · state 없음)의 `Location` = SAS 기본 필터의 `Location`**; `redirectableRequest` 판정표; 비-OAuth2 예외 → `{"error":"invalid_request"}`; 200자 상한.
- 로컬: `./gradlew :projects:iam-platform:apps:auth-service:test` rc=0 — 1303 tests / 33 skipped / 0 failed (641 기록 1295 + 신규 8). 🔴 **이 호스트에 Docker 없음** → 통합 시험은 로컬에서 안 돌았고 빨강 · 초록 둘 다 **CI 에서만** 측정.
- 필수 가드 셋(`check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift`) `git add` 뒤 로컬 rc=0.

### 열린 것

- **AC-1 의 «형식은 소유자 결정대로»** — 위 본문을 소유자에게 보이고 확인받는다(JSON 유지 / HTML 화면은 별도 티켓).
- **AC-3** — 굽기 · 라이브(미등록 콜백 하나로 400 확인), 다음 창.
- 🔵 남는 서술: `infra/demo/seed-demo-domain.sh:21` 헤더의 «401» 예시는 배포 뒤 낡는다(루트 경로 — 고치려면 monorepo-level 정정). 적용된 마이그레이션 `V0031` · `V0033` · `V0034` 헤더는 불변(체크섬).

## CORRECTION (2026-10-23 UTC)

- **AC-1 «형식은 소유자 결정대로» — 닫힘.** 소유자가 **2026-10-21** 에 § 사용자에게 보이는 본문 의 형식(400 OAuth JSON `{error, error_description}` — `TASK-BE-641` D1 (a) 와 같은 모양)을 승인했다(소유자의 상시 지시). 위 AC-1 의 «🔴 아직 안 닫혔다» 와 § 열린 것 첫 줄은 그 승인을 이 파일에 적기 전의 기록이다.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `d3988188a`(#4445) 는 `283fd39cf` 의 조상.
- **AC-3 ✅** — `GET https://auth.hubwang.com/oauth2/authorize?client_id=no-such-client&redirect_uri=https://evil.example/cb…` → **400 `application/json`** `{"error":"invalid_request","error_description":"OAuth 2.0 Parameter: client_id"}` (굽기 전 같은 갈래 = 401 내부 자격).
- 🔵 잰 입력은 «모르는 `client_id` + 미등록 콜백» 이라 거절 사유는 `client_id` 다. 등록된 client 에 미등록 콜백만 준 갈래(`redirect_uri`)는 라이브로 따로 재지 않았다 — 같은 핸들러(`AuthorizationEndpointErrorResponseHandler`)의 같은 400 갈래이고, CI 실제 Tomcat 시험이 그 갈래의 400 `redirect_uri` 를 단언한다(§ 시험).

### 닫기 — 4차원 확인 (2026-10-23 UTC)

- (a) PR #4445 `state=MERGED` · `mergedAt` 2026-10-21T11:23:23Z.
- (b) 머지 커밋 `d3988188a` = `origin/main` 조상(`git merge-base --is-ancestor`), 굽기 커밋 `283fd39cf` 의 조상이기도 하다.
- (c) 머지된 PR 의 `statusCheckRollup` 68 칸 중 실패 0 (SUCCESS 18 · SKIPPED 50).
- (d) `# Acceptance Criteria` 를 열어 읽음 — AC-0 · AC-2 ✅(기존) · AC-1 ✅(위 CORRECTION) · AC-3 ✅(위). 체크 칸은 `review/` 동결 규칙으로 고치지 않았다 — 닫힘은 이 절이 기록한다.
- ⇒ `review/ → done/`.
