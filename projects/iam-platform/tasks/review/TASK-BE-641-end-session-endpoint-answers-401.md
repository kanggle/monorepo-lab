# Task ID

TASK-BE-641

# Title

IdP 가 discovery 로 알리는 `end_session_endpoint`(`/connect/logout`)가 **401 «Missing or invalid internal credentials»** 를 낸다 — 콘솔 로그아웃이 IdP 세션을 끝내지 못하고 JSON 오류 화면에서 멈춘다

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus(auth-service 다중 보안 체인 · SAS OIDC 로그아웃 설정)
> 기안 2026-10-20 UTC — 32차 데모 창(`TASK-MONO-672` § 32nd window — harvest)의 발견. 측정 먼저(AC-0).

# Dependency Markers

- 출처: 32차 창 — `requester@demo.com` 이 콘솔에서 로그아웃 → 브라우저가 `https://auth.hubwang.com/connect/logout` 로 가서(쿼리: `id_token_hint` · `post_logout_redirect_uri` = 콘솔 `/login` 을 퍼센트 인코딩한 값 · `client_id=platform-console-web`) `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}` 를 받았다(소유자).
- 관련: `TASK-MONO-801` AC-0 — iam auth-service `SecurityConfig.java:188`(Order 2) 의 진입점이 같은 문구 «Missing or invalid internal credentials» 를 낸다(추론으로 적혀 있던 것).
- 선행 없음.

# Goal

RP-initiated logout 이 동작한다: `GET /connect/logout`(유효한 `id_token_hint` + 등록된 `post_logout_redirect_uri`)이 IdP 세션을 끝내고 그 주소로 보낸다. 잘못된 요청은 OIDC 규약의 오류 응답(내부 자격 오류가 아니라)을 낸다.

# Scope

## In Scope

- **AC-0 측정**:
  - 실측(에이전트, 2026-10-20 18:45Z): `GET https://auth.hubwang.com/.well-known/openid-configuration` → `end_session_endpoint = https://auth.hubwang.com/connect/logout`. 같은 주소를 `id_token_hint` 없이 부르면 **401 `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}`**, 소유자 브라우저(`id_token_hint` 있음)도 같은 401. auth-service 로그에 그 줄 없음.
  - 요청이 어느 체인에 걸리는가 — SAS 체인의 `securityMatcher` 가 `/connect/logout` 을 덮는가, OIDC logout 이 켜져 있는가(`oidc(...)` · `logoutEndpoint`), Order 2 체인이 그것을 가로채는가(file:line).
  - 왜 지금까지 안 보였나 — nightly «Web-store GAP logout e2e (RP-initiated end_session, real GAP)» 가 초록인데 무엇을 부르는가(같은 주소 · 같은 메서드 · 같은 호스트인가). 콘솔 로그아웃 경로(`app/api/auth/logout` 류)가 이 주소를 언제부터 쓰는가.
  - 공개 호스트 `auth.hubwang.com` 앞단(Cloudflare/Traefik)이 경로를 바꾸는가.
- 수선(AC-0 이 정한다) + 시험(실제 체인으로 — 슬라이스 금지).

## Out of Scope

- 콘솔 쪽 로그아웃 흐름 변경 — 서버가 고쳐지면 그대로 동작해야 한다. AC-0 이 콘솔 결함을 보이면 콘솔 티켓.

# Acceptance Criteria

- [x] **AC-0** — 위 측정을 file:line 으로. nightly 가 왜 못 잡았는지 포함. → § AC-0 측정 (2026-10-21 UTC). 정상 요청은 302(실측) · 401 은 오류 경로의 /error 디스패치.
- [ ] **AC-1** — `GET /connect/logout` 정상 요청 → 302 `post_logout_redirect_uri` · IdP 세션 종료. 잘못된 요청 → OIDC 오류(내부 자격 401 아님). → § 구현: **«잘못된 요청 → OIDC 오류» 절반은 닫힘**(실제 Tomcat, CI). «정상 → 302» 는 시험의 정상 요청(방금 로그인한 세션)에서 302 · 세션 종료를 실제 Tomcat 으로 측정했지만, 🔴 **소유자 브라우저 사례(hint 있음)가 302 가 되는지는 아직 모른다** — 그 요청의 안쪽 400 원인(`sid`/`sub`/`id_token_hint`…)이 이번 수정으로 처음 응답 본문 · 로그에 보이게 될 뿐이다. D1 대로 이 AC 는 안쪽 원인이 정해진 뒤(AC-3 라이브 다음) 닫는다 — 그래서 열어 둔다.
- [x] **AC-2** — 실제 보안 체인을 지나는 시험 빨강 → 초록 · 대조(내부 경로는 여전히 내부 자격 401). → § 구현 — CI 빨강 2/5(401) → 초록 5/5.
- [ ] **AC-3** — 배포(굽기) · 라이브(콘솔 로그아웃 → `console.hubwang.com/login` 착지, 다음 창).

# Related Specs

- `projects/iam-platform/specs/services/auth-service/architecture.md`

# Related Contracts

- OIDC RP-Initiated Logout 1.0 · `projects/iam-platform/specs/contracts/http/` 의 auth 계약(AC-0 이 이름을 적는다)

# Edge Cases

- `id_token_hint` 가 만료된 경우 — 규약상 처리(거절 또는 확인 화면)를 AC-0 이 적는다.
- 32차 창에서 로그아웃 직후 콘솔로 직접 들어가면 IdP 세션이 남아 있어 비밀번호 없이 다시 로그인될 수 있다 — 지금의 실질적 영향.

# Failure Scenarios

1. Order 2 체인을 `/connect/**` 전체에 열어 내부 경로가 노출된다 — 대조 시험이 막는다.
2. 슬라이스 시험으로 초록 — 실제 다중 체인 순서를 안 지난다.

---

## AC-0 측정 (2026-10-21 UTC · 창 없음 · 분석=Opus 5.5)

**결론: `/connect/logout` 은 SAS 체인(Order 1)이 받는다 — Order 2 가 가로채는 것이 아니다. SAS 가 검증에 실패하면 `sendError(400)` 하고, 그 `/error` 디스패치가 Order 2 의 `denyAll` 에 걸려 401 로 바뀐다.** (`TASK-MONO-801` 과 같은 기전.)

1. 엔드포인트는 켜져 있다 [실측]: Boot 3.4.1 · SAS 1.4.1. `auth-service/.../oauth2/AuthorizationServerConfig.java:170` `@Order(1)` · `:232` `securityMatcher(getEndpointsMatcher())` · `:235-237` `.oidc(...)`(userinfo 만 사용자 지정) · `:556-571` 은 userinfo 경로만 바꿔 logout = 기본 `/connect/logout`. 정상 경로: `ConsumerPoolSsoIntegrationTest.java:626-630`(필터 포함 MockMvc) 3xx + 세션 무효화.
2. 오류 → 401 [실측, javap]: SAS 1.4.1 `OidcLogoutEndpointFilter.sendErrorResponse` = `sendError(400, …)`(본문 없음) · hint 없음 → `OidcLogoutAuthenticationConverter` `invalid_request`. `/error` 는 Order 0(`WebLoginSecurityConfig.java:88-159`)·Order 1 에 안 맞아 `config/SecurityConfig.java:146` `@Order(2)`(securityMatcher 없음 — 나머지 전부) → `:190` `anyRequest().denyAll()` → 진입점 → `:243` 401 `Missing or invalid internal credentials`. 같은 무리: `V0034…sql:85-86`(authorize 의 redirect_uri 불일치도 같은 401). 로그가 빈 이유: 진입점은 로그 없음 · SAS 는 trace 만.
3. 소유자 브라우저 사례(hint 있음)의 **안쪽 400 은 아직 모른다** — 401 이 모두 덮는다. SAS 1.4.1 provider 검사(바이트코드): hint 토큰 없음/무효/미사용 가능 → `invalid_token`(`id_token_hint`) · `aud`/`client_id` 불일치 · `post_logout_redirect_uri` 미등록 · 현재 principal ≠ authorization principal → `sub` · 세션 레지스트리의 현재 세션 ≠ `sid` → `sid`. **exp 는 검사하지 않는다** — 만료 hint 는 지금도 받아들여진다(Edge Case). 후보[추론]: `sid`/`sub` 불일치(같은 브라우저 재로그인 · 계정 전환) > hint 미발견 > URI 미등록(낮음 — `V0034:132-140` 등록 · 콘솔 `app/api/auth/logout/route.ts:75`). 배제[실측]: refresh 로 받은 id_token 의 sid 누락(`SasRefreshTokenAuthenticationProvider.java:396-398`).
4. 앞단은 경로를 안 바꾼다[실측]: `infra/demo/auth-forwarder/src/app/[[...path]]/route.ts:153-155` · `iam-traefik.override.yml:311` `PathPrefix(/connect)`.

**nightly 가 못 잡은 이유**: run 39500637146(`cd5e7ab50`) 의 «Web-store GAP logout e2e» · «Platform Console E2E full-stack» 둘 다 success — **거짓 초록이 아니다**, 정상 요청은 실제로 302. 두 lane 모두 «방금 로그인 → 곧바로 로그아웃» 정상 경로 하나만(세션 변경 · 계정 전환 · 오래된 hint · 오류 요청 없음), `http://auth-service:8081` 직결. 실제 Tomcat 에서 오류 경로를 지나는 시험이 0 — 유일한 `/connect/logout` 시험(`ConsumerPoolSsoIntegrationTest.java:70-73,626`)은 `@AutoConfigureMockMvc` 라 ERROR 디스패치가 없어 400 으로 보인다(401 원리상 재현 불가). ⇒ AC-2 는 `RANDOM_PORT` + 실제 HTTP 클라이언트(801 의 프로브 방식). 예전 데모 해석 `iam-traefik.override.yml:277-279` «직격하면 401 이라 엔드포인트는 존재» 도 이 401 을 오독한 것.

### 소유자 결정 (2026-10-21 UTC)

- **D1 = (a)**: `oidc.logoutEndpoint(le -> le.errorResponseHandler(...))` 로 400 `{error, error_description}` 을 직접 쓴다(/error 를 안 거침). Order 2 는 그대로 — `/connect/**` 를 열지 않는다. 배포 뒤 다음 창에서 실제 오류 코드(`sid`/`sub`/`id_token_hint`…)를 보고 (b) 화면 · (c) 관대 처리를 다시 정한다. 🔴 (a) 만으로는 소유자 사례가 302 가 안 될 수 있다 — AC-1 «정상 → 302» 는 안쪽 원인이 정해진 뒤 닫힐 수 있다.
- **D2**: logout 만. authorize 오류의 같은 401 은 별도 티켓으로 기안. /error · ERROR 디스패치 허용은 801 이 기각(`TASK-MONO-801:89`) — 쓰지 않는다.
- **D3**: 구현 전에 `specs/contracts/http/auth-api.md` 에 `/connect/logout` 절 추가(302 · 오류 400 · 만료 hint 수용). 낡은 javadoc(`SecurityConfig.java:41-45` · `AuthorizationServerConfig.java:75-76,136`) 정정. (a) 라 ADR 불필요.
- **D4**: AC-2 = `RANDOM_PORT` 실제 Tomcat — hint 없음 → 400 OIDC 오류(지금 401 = 빨강) · 정상 → 302 · 대조 `/internal/auth/…` 는 여전히 401 내부 자격.
- 보이는 문구: 오류일 때 «Missing or invalid internal credentials» → 영문 OIDC JSON. 구현 = Opus.

## 구현 (2026-10-21 UTC · 구현=Opus 5.5 · PR #4440)

브랜치 `task/be-641-logout-error-400`(base `1bbdb5bf9`). 커밋 순서 = 계약 + 시험(빨강 `edd1ed586`) → 수정(초록 `6da554704`) → 이 기록.

### 바뀐 것 (file:line)

- **계약 먼저 (D3)**: `specs/contracts/http/auth-api.md` § `GET /connect/logout` 신설 — 정상 302(+`state`) · IdP 세션 종료 · 오류 400 `application/json` `{error, error_description}` 표(SAS 1.4.1 검사 순: `id_token_hint` · `aud` · `client_id` · `post_logout_redirect_uri` · `sub` · `sid`) · 오류는 redirect 하지 않음 · **만료 hint 수용**(SAS 1.4.1 은 `exp` 미검사). discovery 예시에 `end_session_endpoint` 추가.
- **수정 (D1 a)**: `apps/auth-service/.../oauth2/OidcLogoutErrorResponseHandler.java` (신규, `:39`) — `OAuth2ErrorHttpMessageConverter`(SAS 가 token 오류에 쓰는 것과 같은 writer)로 400 본문을 **직접** 쓴다 → `sendError` 없음 → `/error` 디스패치 없음. `error_uri` 제외, `error_description` 200자 상한, `Cache-Control: no-store`, INFO 한 줄(`error` · `error_description` 만 — 토큰 · 쿼리 없음). OAuth2 가 아닌 인증 예외는 `{"error":"invalid_request"}`(예외 메시지 안 실음). 배선 `AuthorizationServerConfig.java:247-249` `.oidc(...).logoutEndpoint(logout -> logout.errorResponseHandler(...))`. **Order 2 · `/error` · `/connect/**` 는 손대지 않았다.**
- **javadoc 정정 (D3)**: `SecurityConfig.java:41-48` · `AuthorizationServerConfig.java:75-80` · `:138-141` — SAS 체인 = `/oauth2/**` · `/.well-known/**` · `/connect/logout`, `/error` 디스패치는 Order 2 `denyAll` 로 간다.
- **D2**: `tasks/ready/TASK-BE-642-authorize-error-path-answers-401.md` 기안(같은 401 이 `/oauth2/authorize` 의 redirect 불가 오류에도 — `V0034…sql:85-86`). ID 는 저장소 전체 큐(`**/tasks/**/TASK-BE-*`) · `origin/main` · 원격 브랜치 · 열린 PR 을 훑어 642 가 비어 있음을 확인.

### 사용자에게 보이는 본문 (소유자 확인용)

`id_token_hint` 없이 부르면(상태 · `Content-Type` 접두 · 본문 = CI 실제 Tomcat 단언, `Cache-Control` = 단위 시험 단언):

```
HTTP/1.1 400
Content-Type: application/json
Cache-Control: no-store

{"error":"invalid_request","error_description":"OpenID Connect 1.0 Logout Request Parameter: id_token_hint"}
```

IdP 가 모르는 hint 이면 `{"error":"invalid_token","error_description":"OpenID Connect 1.0 Logout Request Parameter: id_token_hint"}`. 수정 전 두 경우 모두 401 `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}`.

### 시험

- **`OidcLogoutEndpointRealServerIntegrationTest`** (신규, `RANDOM_PORT` + `java.net.http.HttpClient`, redirect 안 따름, 쿠키 수동 — 801 프로브 방식). 프로필 `integration-test` + `application-test.yml` 을 additional location 으로 — `test` 프로필이면 `InternalApiFilter` bypass 가 `/internal/**` 를 인증해 대조가 엉뚱한 이유로 통과하기 때문. 5 케이스:
  | 케이스 | 빨강 `edd1ed586` (CI run 39561274718 · iam B) | 초록 `6da554704` (CI run 39568756728 · iam B) |
  |---|---|---|
  | hint 없음 → 400 `invalid_request` | **FAILED** `expected: 400 but was: 401` body `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}` | PASSED |
  | 모르는 hint → 400 `invalid_token` | **FAILED** 같은 401 | PASSED |
  | 정상: `/login`(CSRF) → 로그인 → authorize 코드 → token(id_token) → `/connect/logout` → 302 `http://localhost:3000/login?state=be-641` → 다음 authorize 302 `/login` | PASSED (수정 전에도 정상 경로는 302 — AC-0 1번과 일치) | PASSED |
  | 대조 `POST /internal/auth/credentials` Bearer 없음 → 401 내부 자격 | PASSED | PASSED |
  | 대조 `/connect/not-an-endpoint` → 401 내부 자격 (Failure Scenario 1) | PASSED | PASSED |
  빨강 런 iam B 전체 170 tests / 2 failed(이 둘뿐), 초록 런 iam B SUCCESS(`headRefOid=6da554704`).
- **`OidcLogoutErrorResponseHandlerTest`** (신규, 단위, Docker 불필요) — SAS 1.4.1 실제 `OidcLogoutEndpointFilter`(기본 converter) 뒤: hint 없음 → 400 · 두 필드 · `getErrorMessage()==null`(sendError 없음) · chain/AM 미호출, provider 거절(`sid`) → `error_uri` 없음, 200자 상한, 비-OAuth2 예외. 🔵 처음 한 번 빨강은 시험 결함이었다 — SAS 는 GET 파라미터를 query string 에서 읽어 `setParameter` 만으로는 hint 가 없는 요청이 됐다(`setQueryString` 추가).
- 로컬: `./gradlew :projects:iam-platform:apps:auth-service:test` rc=0 — 1295 tests / 33 skipped / 0 failed. 🔴 **이 호스트에 Docker 없음**(`docker info` → pipe 없음) → 통합 시험은 로컬에서 안 돌았고 빨강 · 초록 둘 다 **CI 에서만** 측정.
- 필수 가드 셋(`check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift`) `git add` 뒤 로컬 rc=0. (빨강 커밋의 CI 는 INDEX `§ ready` 가 비고 `(empty)` 표시가 없어 PARSE ERROR — 초록 커밋에서 BE-642 행이 들어가며 해소, CI pass.)

### 열린 것

- **AC-1 «정상 → 302» 의 소유자 사례** — 위 AC-1 참고. 배포 뒤 첫 창에서 그 브라우저의 응답 본문(또는 auth-service INFO `RP-initiated logout rejected: error=… description=…`)이 안쪽 원인을 말해 준다 → D1 (b) 화면 · (c) 관대 처리 재결정.
- **AC-3** — 굽기 · 라이브(콘솔 로그아웃 → `console.hubwang.com/login`), 다음 창.
- 🔵 디자인 메모: `OidcLogoutEndpointRealServerIntegrationTest` 는 형제 IT 와 달리 `test` 프로필을 안 쓴다(위 이유). CI 에서 기동 · 5/5 확인됨.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `c118382d8`(#4440) 는 `283fd39cf` 의 조상.
- **AC-3 ✅** — 콘솔 계정 메뉴 → 로그아웃 → `https://console.hubwang.com/login` 착지. 대조: 잘못된 `GET /connect/logout?id_token_hint=garbage…` → **400 JSON `invalid_token`**(내부 자격 401 아님).
- **AC-1 — 열림 그대로.** «소유자 브라우저 hint 사례» 절반은 이번 창에서 재지 않았다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-3 — 닫힘.** 34차 창(2026-10-24 UTC): ✅ 소유자가 콘솔에서 로그아웃하자 `console.hubwang.com/login` 에 착지했다.
- **AC-1 — 34차 창 결과 (열린 채 유지).** «소유자 브라우저 사례(hint 있음)» 절반은 이 측정으로 따로 판정하지 않았으므로 AC-1 은 열어 둔다 — 티켓은 `review` 에 남는다.
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 AC-3 의 닫힘 기록이다.)

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-1 ✅ — «소유자 브라우저 사례(hint 있음)» 절반이 닫힘.** 35차 창(굽기 `54b79233c`, 신선 볼륨): 콘솔에서 로그아웃.
  - edge 로그 08:34:37Z: `GET /connect/logout?id_token_hint=…&post_logout_redirect_uri=https%3A%2F%2Fconsole.hubwang.com%2Flogin&client_id=platform-console-web` → **302** (8 ms). 브라우저는 `console.hubwang.com/login` 에 착지했다.
  - 다시 «로그인» 을 누르자 비밀번호 폼이 나왔다 — 콘솔 코드에는 `prompt`/`max_age` 가 없으므로 IdP 세션이 실제로 끝난 것이다.
  - 호출은 서버 쪽에서 나갔다(source `13.217.225.232`, UA «-»).
  - AC-1 의 나머지 절반(잘못된 요청 → OIDC 오류)은 CI 실제 Tomcat(5/5)과 33차 창(`id_token_hint=garbage…` → 400 `invalid_token`)에서 이미 닫혀 있다.
- AC-3 은 34차 창에서 닫혔다(위 CORRECTION). AC-0 · AC-2 는 처음부터 닫혀 있었다.

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상
