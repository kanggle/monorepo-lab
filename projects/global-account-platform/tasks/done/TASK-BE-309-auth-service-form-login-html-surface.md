# Task ID

TASK-BE-309

# Title

auth-service formLogin() HTML 로그인 surface — Spring Authorization Server `/oauth2/authorize` 가 unauthenticated browser request 에서 *실제로* 작동하도록 (현재 `LoginUrlAuthenticationEntryPoint("/api/auth/login")` redirect 대상이 JSON-only POST endpoint → 브라우저 dead-end). minimal viable surface: 신규 `@Order(0)` `SecurityFilterChain` (formLogin + STATEFUL session) + `CredentialAuthenticationProvider` (reuse `PasswordHasher` + `CredentialRepository`; multi-tenant ambiguity → tenant-chooser placeholder) + Spring Security `DefaultLoginPageGeneratingFilter` 자동 생성 HTML page (v1 placeholder UI, 커스텀 Thymeleaf 별 task) + tenant-aware `Authentication.principal` 가 `TenantClaimTokenCustomizer.customizeForAuthorizationCode` 에 의해 OIDC token 의 `tenant_id`/`tenant_type` claim 으로 흐름. 추가 endpoint `/logout` (session 만료) + CSRF protection 보존. 기존 SAS `@Order(1)` + legacy `SecurityConfig @Order(2)` byte-unchanged 또는 minimum surgical edit. Closes TASK-PC-FE-019 honest gap (architectural — OIDC PKCE browser-driven 경로 실현 불가).

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: 없음 (auth-service standalone change). Spring Authorization Server `@Order(1)` filter chain + legacy `SecurityConfig @Order(2)` 양쪽이 이미 main 에 ACCEPTED. `LoginUseCase` / `CredentialRepository` / `PasswordHasher` / `TenantClaimTokenCustomizer` / `OAuthClientMapper` 의 Option B (`custom.tenant_id` ClientSettings 키) 모두 기존.
- **origin**: TASK-PC-FE-019 § Honest gap (architectural) "auth-service HTML login form 부재로 OIDC PKCE browser-driven 경로 실현 불가" + PC-FE-019 fixture `login.ts` docstring 의 `client_credentials backdoor` step-down note + `project_operator_overview_finance_card_resolution_complete.md` 메타 #17. 본 task 가 honest gap 의 architectural closure.
- **prerequisite for**: future TASK-PC-FE-XXX (PC-FE-019 fixture true-OIDC-PKCE migration — `loginAsSuperAdmin` 가 Playwright `.fill()` / `.click()` 으로 SAS HTML form 을 driver 하도록 단순 교체). 본 task 가 완료되면 단일 fixture 파일 1 commit 으로 처리 가능.
- **spec-first**: spec PR (this file + INDEX) → impl PR (SecurityFilterChain + AuthenticationProvider + IT) → close chore PR.
- **no ADR amendment**: HARDSTOP-09 not triggered. Spring Authorization Server formLogin() 의 도입은 `architecture.md § Identity & Sessions` 의 *expected* SAS form-login 경로 활성화이지 새 architectural decision 이 아님. ADR-MONO-001/002/003 + ADR-003 (public client) 의 underlying SAS framework feature 호출.

---

# Goal

TASK-PC-FE-019 가 platform-console e2e harness standup 중 surface 한 architectural gap closure:

```
현재 (broken — browser-driven 실현 불가):
  Browser → GET /oauth2/authorize?client_id=platform-console-web&response_type=code&...
  SAS @Order(1) → unauthenticated session →
    `LoginUrlAuthenticationEntryPoint("/api/auth/login")` (buildHtmlOnlyRequestMatcher 통과 — text/html)
  302 → /api/auth/login
  /api/auth/login → JSON-only POST handler (HTML body 없음); GET 시 405 Method Not Allowed
  → dead-end. PC-FE-019 fixture 는 `client_credentials` 우회 (real SAS JWT 발급은 가능하지만 OIDC PKCE *browser-driven* 검증 불가).
```

```
목표 (working browser-driven OIDC PKCE):
  Browser → GET /oauth2/authorize?client_id=platform-console-web&...
  SAS @Order(1) → unauthenticated session →
    NEW @Order(0) SecurityFilterChain 또는 modified @Order(1) → formLogin() entry point
  302 → /login (DefaultLoginPageGeneratingFilter 또는 minimal Thymeleaf form)
  User → fills email + password + (선택) tenantId
  POST /login → CredentialAuthenticationProvider 가 PasswordHasher + CredentialRepository 로 검증
  Spring Security 가 SecurityContext + HTTP session 생성 (session-fixation rotation)
  302 → /oauth2/authorize (original URL — SavedRequest from session)
  SAS sees authenticated session → consent skip (require-authorization-consent=false in V0015) → code 발급
  302 → ${redirect_uri}?code=...&state=...
  console-web /api/auth/callback → POST /oauth2/token (PKCE verifier) → access + refresh token + id_token
```

본 task 의 surface (minimal viable — v1):

1. **신규 `@Order(0)` `SecurityFilterChain`** (new file `WebLoginSecurityConfig.java`):
   - `securityMatcher`: `/login`, `/logout`, `/oauth2/authorize` (한 chain 으로 brower-flow 가 모두 cover 되도록), 그 외는 SAS @Order(1) + 기존 @Order(2) 가 처리.
   - `formLogin()`: default Spring Security 자동 생성 login page (`DefaultLoginPageGeneratingFilter`) 사용. v1 placeholder — 커스텀 Thymeleaf UI 는 별 future task.
   - `logout()`: `/logout` POST 가 session 만료 + redirect to `/login?logout`. CSRF 보호 default 동작.
   - `sessionManagement`: `IF_REQUIRED` (form-login 가 session 필요; 기존 `STATELESS` 와 chain 분리되어 conflict 없음).
   - `csrf`: default enabled (form-login 가 CSRF 토큰 검증 — Spring Security 자동).
   - `authenticationProvider`: 신규 `CredentialAuthenticationProvider` 주입.

2. **`CredentialAuthenticationProvider`** (new file):
   - `supports(UsernamePasswordAuthenticationToken.class)`.
   - `authenticate(auth)`:
     - `email = auth.getName()`, `password = auth.getCredentials()`, optional `tenantId = auth.getDetails().get("tenantId")` (HTML form 의 추가 hidden input — multi-tenant ambiguity 해결용; v1 은 single-tenant 가정 가능, ambiguity → `AuthenticationException` 으로 fail-closed → login page 가 error 표시).
     - `credentialRepository.findByTenantIdAndEmail(...)` (tenantId 지정) 또는 `findAllByEmail(...)` (ambiguity check). 기존 `LoginUseCase` § "TASK-BE-229 tenant-aware credential lookup" 패턴 verbatim 재사용.
     - `passwordHasher.verify(...)` → mismatch → `BadCredentialsException`.
     - 성공 시: `new UsernamePasswordAuthenticationToken(principal, null, authorities)` 반환. `principal` 은 `TenantAwarePrincipal` (record: `accountId`, `email`, `tenantId`, `tenantType`) — Spring Security `Authentication.getPrincipal()` 으로 `TenantClaimTokenCustomizer.customizeForAuthorizationCode` 가 읽어 `tenant_id` claim 주입.
   - **rate-limit / login-event publishing 은 NOT 호출** (`LoginUseCase` 의 부수 효과는 deprecated JSON path 의 책임 — `formLogin()` path 는 minimal credential check + session). 추후 enhancement 로 publish 가능 (별 task).

3. **`TenantAwarePrincipal`** (new record):
   - `accountId: String`, `email: String`, `tenantId: String`, `tenantType: String`.
   - `TenantClaimTokenCustomizer.customizeForAuthorizationCode` 가 `context.getPrincipal().getPrincipal()` 으로 cast 시도하는 path 가 이미 있는지 확인 — 현재 `principal.getDetails() map` fallback 만 있을 수 있음. 필요 시 customizer 의 *우선순위 1 path* 추가 (record 의 tenant_id/tenant_type 을 직접 읽기).

4. **`AuthorizationServerConfig` SAS chain 의 entry point 변경**:
   - 기존: `defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/api/auth/login"), buildHtmlOnlyRequestMatcher())`.
   - 신규: `defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), buildHtmlOnlyRequestMatcher())`. 1-line surgical edit. `/api/auth/login` 은 deprecated JSON POST endpoint 만 유지 (browser-driven 으로는 사용 안 됨).

5. **Integration test (`FormLoginIntegrationTest`)** — Testcontainers MySQL + full Spring context:
   - **happy path**: seed credential (test profile) → MockMvc GET `/oauth2/authorize?client_id=platform-console-web&...` → 302 to `/login` → MockMvc POST `/login` with email+password+CSRF → 302 to original `/oauth2/authorize` → 302 to `redirect_uri` with `code=...` query → POST `/oauth2/token` with code+verifier → 200 with `access_token`+`id_token` (verify `tenant_id` claim via JWT decode).
   - **invalid credentials**: POST `/login` with wrong password → 302 to `/login?error` → page contains error indicator.
   - **session creation**: post-login POST `/oauth2/authorize` 는 session cookie 만 가지고 무인자 작동.
   - **logout**: POST `/logout` → 302 to `/login?logout` + session invalidated → 다음 `/oauth2/authorize` 는 다시 `/login` 으로 redirect.
   - **CSRF**: POST `/login` without CSRF token → 403.
   - **deprecated JSON path 보존**: POST `/api/auth/login` 의 기존 JSON 검증 unchanged (regression guard).

6. **`pnpm e2e:smoke` 보존**: PC-FE-021 의 closed-loopback smoke 가 OIDC_ISSUER_URL 을 127.0.0.1:1 으로 강제하므로 본 변경에 영향 없음.

# Decision authority

- **Why minimal viable (DefaultLoginPageGeneratingFilter, NOT custom Thymeleaf)**:
  - Spring Security 의 default login page 가 functional + accessible — v1 portfolio 가치 (architectural completeness) 가 UI 품질보다 우선. 향후 ADR-MONO-013 / fan-platform UX consistency 적용 시 custom Thymeleaf template 별 task.
  - Thymeleaf 도입 = 새 dependency + new view layer — 본 task 의 scope discipline 와 어긋남.

- **Why new `@Order(0)` filter chain (NOT modify SAS @Order(1))**:
  - SAS chain 의 `securityMatcher` 가 `authorizationServerConfigurer.getEndpointsMatcher()` — `/oauth2/**` + `/.well-known/**` 만. `/login` + `/logout` 은 cover 안 됨.
  - SAS chain 안에 `formLogin()` 직접 추가 시 SAS configurer 의 strict matcher + formLogin 의 default `/login` matcher 가 충돌 가능 (Spring Security order resolution).
  - 안전한 패턴: **new chain @Order(0)** 가 `/login` + `/logout` + `/oauth2/authorize` 를 cover, SAS chain @Order(1) 가 `/oauth2/token` + `/oauth2/jwks` + `/.well-known/**` 등 programmatic endpoints cover (HTML 요청만 entry point 가 새 `/login` 으로 redirect), 기존 `SecurityConfig @Order(2)` 가 나머지.
  - `@Order(0)` 가 `/oauth2/authorize` 도 cover 하는 이유: SAS configurer 가 `/oauth2/authorize` 를 자체 chain 으로 처리하지만 unauthenticated session redirect 가 `/login` 으로 가는 entry-point path 가 form-login chain 의 책임 (session-aware redirect-back).
  - Actually upon impl, may need `securityMatcher("/login", "/logout")` only + edit SAS chain entry point to use the new `/login` URL. **Impl-time 확정 — spec 은 두 surface (new chain OR surgical entry-point edit) 둘 다 acceptable 로 명시**.

- **Why bridge `CredentialRepository` directly (NOT call full `LoginUseCase`)**:
  - `LoginUseCase` 는 rate-limit + audit event publishing + tenant ambiguity throw + device session register 등 부수 효과가 많음. form-login path 는 *credential check + session creation* 만 필요.
  - 향후 form-login 에 rate-limit / audit 추가 시 `LoginUseCase` 의 일부 (credential verify) 만 추출한 shared service 를 만들거나 형변환된 helper 호출 — 별 enhancement task.
  - 단 본 task 의 IT 가 `LoginUseCase` JSON path 와 form-login path 둘 다 작동함을 검증 (regression guard).

- **Why tenantId 는 v1 optional / cross-tenant ambiguity → fail-closed**:
  - HTML form 에 `tenantId` 추가 입력 = UX 복잡도 ↑ (대부분 사용자는 본인 tenant 모름). v1 은 single-tenant assumption (email-uniqueness across tenants 가 일반적); ambiguity 발생 시 login fail + error message "복수 tenant 에 동일 email 등록 — 관리자 문의" + dev/test 환경 에서 `LoginCommand(tenantId=null)` 패턴 동일 활용.
  - 향후 tenant chooser UI (사용자가 등록된 tenant 들 중 선택) 별 task.

- **Why TenantAwarePrincipal record (NOT Spring Security UserDetails default)**:
  - SAS `TenantClaimTokenCustomizer.customizeForAuthorizationCode` 가 token 의 `tenant_id` claim 을 어떻게 resolve 하는지 — 현재 `principal.getDetails() map` + `ClientSettings custom.tenant_id` + clientName legacy split 3-fallback. form-login path 의 principal 이 record 라면 customizer 는 즉시 record 의 tenant_id 를 읽을 수 있도록 1-line 추가.
  - default `UserDetails` 사용 시 tenant 정보 carry 가 어색 — record 가 type-safe + 명확.

- **Why no producer change to other projects**:
  - 모두 GAP 내부 변경. AC-3 zero-retrofit verified for `projects/{wms,scm,erp,fan,ecommerce,finance,platform-console}-platform/`.

- **Why deprecated `/api/auth/login` JSON path 보존**:
  - 외부 legacy client 가 사용 중일 수 있음 (RFC 8594 Deprecation header 만 emit 중, sunset 2026-08-01). 본 task 는 새 form-login surface 추가만 — legacy 제거는 별 task.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- This task file.
- `projects/global-account-platform/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/WebLoginSecurityConfig.java` (NEW) — `@Order(0)` filter chain + `formLogin()` + `logout()` + `csrf default` + `authenticationProvider(credentialAuthenticationProvider)`.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java` (NEW) — `AuthenticationProvider` impl.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/domain/principal/TenantAwarePrincipal.java` (NEW) — record.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/AuthorizationServerConfig.java` — 1-line: `LoginUrlAuthenticationEntryPoint("/api/auth/login")` → `LoginUrlAuthenticationEntryPoint("/login")`.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/TenantClaimTokenCustomizer.java` — `customizeForAuthorizationCode` 에 `TenantAwarePrincipal` record 우선 path 추가 (existing principal.getDetails() map fallback 보존).
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/infrastructure/security/FormLoginIntegrationTest.java` (NEW) — Testcontainers MySQL + MockMvc 6 cases (happy / invalid creds / session / logout / CSRF / deprecated JSON regression).
- `projects/global-account-platform/apps/auth-service/src/test/resources/application-test.yml` 또는 IT 의 `@DynamicPropertySource` — test profile seed.

**Tests** (impl PR):

- 6-case Integration test 자체가 test plan.
- 기존 GAP IT 회귀 없음 검증 (`./gradlew :projects:global-account-platform:apps:auth-service:check` GREEN).
- nightly `gap-e2e-full` 회귀 없음 (nightly cron 이후 관찰).

## Out of Scope

- **Custom Thymeleaf login UI**: v1 default Spring Security page 로 충분. 별 future task.
- **Tenant chooser UI (multi-tenant ambiguity)**: v1 single-tenant assumption + fail-closed. 별 future task.
- **Rate-limit / audit-event publishing in form-login path**: 별 enhancement task — `LoginUseCase` shared credential-verify helper 추출 후 적용.
- **2FA challenge step**: TOTP 등 v1 form-login 에 통합 안 함. 별 future task (admin-service 의 2FA login flow 참고).
- **Remember-me cookie**: v1 session-only. 별 future task.
- **PC-FE-019 fixture migration (true OIDC PKCE)**: 별 future task — `loginAsSuperAdmin` 가 Playwright `.fill()` + `.click()` 으로 SAS HTML form driver 하도록 단순 교체.
- **Legacy `/api/auth/login` JSON endpoint 제거**: 별 sunset task (sunset 2026-08-01 per RFC 8594 Deprecation header).
- **`platform-console-web` OIDC client_credentials grant 제거**: PC-FE-019 seed.sql 의 UPDATE 가 추가한 client_credentials grant 는 e2e 환경 only — 본 task 가 deprecate 하지 않음 (e2e 의 backdoor 가 여전히 valid; HTML form 활성화 후 PC-FE-019 fixture migration 시점에 정리).
- **ADR amendment**: 없음.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR 이 정확히 **2 파일** 변경 — 본 task file + INDEX.
- **AC-2 (impl PR: WebLoginSecurityConfig)**: 신규 `@Order(0)` SecurityFilterChain 존재, `securityMatcher` 가 `/login` + `/logout` 최소 cover, `formLogin()` 설정.
- **AC-3 (impl PR: CredentialAuthenticationProvider)**: `AuthenticationProvider` 가 `PasswordHasher` + `CredentialRepository` 호출, principal 은 `TenantAwarePrincipal` record.
- **AC-4 (impl PR: AuthorizationServerConfig 1-line edit)**: `LoginUrlAuthenticationEntryPoint("/api/auth/login")` → `LoginUrlAuthenticationEntryPoint("/login")`. 다른 행 변경 없음.
- **AC-5 (impl PR: TenantClaimTokenCustomizer 호환)**: `customizeForAuthorizationCode` 가 `TenantAwarePrincipal` record 우선 path 추가, 기존 `principal.getDetails() map` + `ClientSettings` + `clientName legacy` 3-fallback 모두 보존.
- **AC-6 (impl PR: 6-case Integration test GREEN)**: `FormLoginIntegrationTest` 6 cases ALL PASS (happy / invalid creds / session-aware authorize / logout / CSRF / deprecated JSON regression).
- **AC-7 (`./gradlew :auth-service:check` GREEN)**: GAP auth-service 의 unit + integration suite 전체 PASS — 회귀 없음.
- **AC-8 (zero-retrofit other producers)**: `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce,finance,platform-console}-platform/'` = empty.
- **AC-9 (deprecated /api/auth/login JSON unchanged)**: `LoginController.java` byte-unchanged. RFC 8594 Deprecation header 보존.
- **AC-10 (BE-303 3-dim verified at close chore)**.
- **AC-11 (BE-299 done re-stage check at close chore)**.

# Related Specs

- `projects/global-account-platform/specs/services/auth-service/architecture.md` — byte-unchanged 또는 § Identity & Sessions 에 1-line "HTML form-login surface live as of TASK-BE-309" 추가 (impl PR).
- `projects/global-account-platform/specs/contracts/http/auth-api.md` — `/login` (GET, POST), `/logout` (POST) 2-line addition. 또는 impl PR 의 surgical edit.
- `docs/adr/ADR-003-public-client-pkce-lineage.md` — byte-unchanged.
- `docs/adr/ADR-MONO-014-operator-token-exchange.md` — byte-unchanged.

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` — `/login` GET (HTML render) + `/login` POST (form submit) + `/logout` POST. impl PR 의 surgical edit (~5 lines).

# Edge Cases

- **POST `/login` with no CSRF token**: Spring Security 의 default → 403. IT case 검증.
- **email matches 2+ tenants (LoginUseCase 의 `LoginTenantAmbiguousException` equivalent)**: form-login 도 same behavior — `BadCredentialsException` 으로 fail-closed (v1 single-tenant simplification; UX 가 명시적 tenant chooser 추가 시 별 task).
- **session-fixation attack**: Spring Security default `migrateSession` 이 이미 활성화 (form-login 의 hard default). IT 가 session id 회전 검증 (optional — substantial test setup).
- **`/oauth2/authorize` GET 시 session 있는 상태**: SAS configurer 가 즉시 code 발급 (consent skip — V0015 의 `require-authorization-consent=false`). 검증 IT.
- **logout 후 SavedRequest 재진입**: `/logout` → session invalidate; 새 `/oauth2/authorize` GET 은 fresh redirect to `/login`. IT case.
- **multi-tab login flow**: tab A 가 `/oauth2/authorize` → `/login` redirect; tab B 가 동일 client 로 별 `/oauth2/authorize`. 둘 다 SavedRequest 가 session 에 push 되어 conflict 가능. v1 에서는 standard Spring Security `RequestCache` 동작 (last-write-wins). 별 follow-up.
- **`/api/auth/login` JSON path 와 동시 호출**: separate filter chain (form-login chain `@Order(0)` 가 `/login` 만, `SecurityConfig @Order(2)` 가 `/api/auth/login` JSON path 유지). 충돌 없음.
- **`TenantAwarePrincipal` 의 tenant_id null**: form-login path 의 `CredentialAuthenticationProvider` 가 항상 resolved `tenant_id` 를 set (single match) — null 은 invariant violation, IT 가 검증.

# Failure Scenarios

- **CredentialAuthenticationProvider 가 STATELESS chain 의 영향 받음**: `WebLoginSecurityConfig @Order(0)` 의 `sessionManagement(IF_REQUIRED)` 가 chain 내부 default; 기존 `SecurityConfig @Order(2)` 의 STATELESS 정책은 본 chain 과 무관. impl 시 IT 가 session 생성 검증.
- **TenantClaimTokenCustomizer 가 TenantAwarePrincipal record 를 인식 못함**: `customizeForAuthorizationCode` 의 fallback 3-tier 가 모두 miss → 기존 `IllegalStateException("tenant_id is required")` throw. IT case 가 verify (happy path 의 JWT decode → `tenant_id` claim 존재).
- **CSRF 토큰 누락으로 form-login 차단**: default Spring Security 에서 form-login 가 자동 CSRF 토큰 발급 + DefaultLoginPageGeneratingFilter 가 input field 자동 삽입. IT case 가 verify.
- **SAS @Order(1) 의 `LoginUrlAuthenticationEntryPoint` 가 여전히 `/api/auth/login` 으로 redirect**: AC-4 의 1-line edit 누락 시. IT 의 happy path 가 즉시 fail (redirect URL mismatch).
- **기존 IT 회귀 (TenantClaimTokenCustomizer 의 fallback path 가 broken)**: AC-7 의 full `./gradlew :auth-service:check` 가 detect.
- **PC-FE-019 nightly e2e 가 회귀**: PC-FE-019 fixture 는 `client_credentials` path 사용 — `/login` URL 변경 무관. 검증 (nightly cron 결과 관찰).

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files.
2. Impl PR (separate, after spec PR merges):
   - `./gradlew :projects:global-account-platform:apps:auth-service:check` GREEN (unit + IT 전체).
   - `FormLoginIntegrationTest` 6/6 PASS.
   - AC-8 zero-retrofit grep.
   - manual verification (impl 작성자 또는 reviewer): docker compose up + browser → `http://localhost:8081/oauth2/authorize?client_id=demo-spa-client&response_type=code&scope=openid&code_challenge=...&code_challenge_method=S256&redirect_uri=...` → 실제 HTML 로그인 폼 노출 + 유효 credential 입력 시 redirect_uri 으로 code 발급. (CI 는 Testcontainers MockMvc 가 cover.)
3. Close chore (after impl GREEN): BE-303 3-dim + BE-299 re-stage.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Spring Authorization Server 의 multi-chain filter + AuthenticationProvider + tenant-aware principal + 6-case IT — substantial cross-cutting Spring Security 작업, mechanical pattern 아님 / SAS framework feature interaction 깊은 이해 필요) / 리뷰=Opus 4.7 (dispatcher 독립 재검증).
