# Task ID

TASK-PC-FE-022

# Title

platform-console e2e fixture migration — replace `client_credentials backdoor` with true OIDC PKCE browser-driven form-fill flow now that auth-service ships `/login` HTML form (TASK-BE-309); closes TASK-PC-FE-019 honest gap (b) "fixture programmatic-token path is NOT the production OIDC PKCE authorization_code flow"

# Status

ready

# Owner

frontend

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

- **depends on**: TASK-BE-309 (auth-service `formLogin()` HTML `/login` surface, **DONE** 2026-05-22, PR #732). BE-309 is the *enabling* prerequisite — without an HTML login form, the OIDC `authorization_code + PKCE` flow cannot be driven from a headless browser, which is why PC-FE-019's first-cut fixture used a `client_credentials backdoor` (seed-applied `grant_type=client_credentials` extension to the `platform-console-web` OAuth client).
- **origin**: TASK-PC-FE-019 § Honest gaps (b) "the Playwright login fixture uses the `platform-console-web` client_credentials grant (extended at runtime via seed.sql) to mint an access token — it does NOT exercise the production OIDC PKCE *authorization_code* flow. The OIDC flow remains the production path; the e2e fixture diverges deliberately because auth-service has no HTML login form for Playwright to drive. Future task: replace with browser-driven form-fill once a SAS HTML login surface lands." Also referenced in `fixtures/login.ts` JSDoc lines 38-44.
- **prerequisite for**: nothing (closes the documented honest gap; no downstream consumer).
- **spec-first**: spec PR (this file + INDEX) → impl PR (fixture rewrite + seed.sql edits + docker-compose extra_hosts + Playwright Chromium flag) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): the migration is *infrastructure parity* — no architectural decision change. Reuses the **established production OIDC PKCE flow** from `apps/console-web/src/app/api/auth/login/route.ts` + `…/api/auth/callback/route.ts` verbatim — the fixture now drives the SAME path the production browser would. No new permission, contract, or per-domain test policy.

---

# Goal

PC-FE-019 의 nightly full-stack harness 가 이미 활성화 (UTC 18:00 cron) 이지만 fixture 의 token-mint 경로는 **production OIDC PKCE 가 아니라 client_credentials backdoor** — seed.sql 의 `UPDATE oauth_clients SET authorization_grant_types = '[..., "client_credentials"]'` 가 production V0015 의 PUBLIC-client-only (PKCE-mandatory) 정의를 e2e 환경에서만 깨뜨려 server-to-server 인증을 가능케 함. BE-309 가 ship 되면서 auth-service `/login` 이 HTML form 을 노출 → Playwright 가 production 동일 경로를 드라이브 가능.

본 task 의 결과로 fixture 는:

1. **Production OIDC PKCE 정확 동일 경로**:
   - `page.goto('http://localhost:3000/api/auth/login?redirect=/')`
   - console-web 가 PKCE verifier/state cookie set + 302 → SAS `/oauth2/authorize?…`
   - SAS 가 no-session 감지 → 302 → auth-service `/login` (BE-309 HTML form)
   - Playwright `page.fill('input[name=username]', email)` + `page.fill('input[name=password]', pw)` + `page.click('button[type=submit]')`
   - SAS 가 session-fixation migrate + 302 → `/oauth2/authorize?…` (재드라이브)
   - SAS 가 authorized session 감지 → issue authorization_code + 302 → `http://localhost:3000/api/auth/callback?code=…&state=…`
   - console-web callback 이 SAS `/oauth2/token` 으로 code 교환 (PKCE verifier 포함) + admin-service `/api/admin/auth/token-exchange` (RFC 8693) 호출 + 3개 cookie (ACCESS / OPERATOR / TENANT) set
   - 최종 302 → `/`
2. **client_credentials backdoor 완전 제거**:
   - seed.sql 의 `UPDATE oauth_clients` 블록 (현재 라인 88-96) 삭제 — `platform-console-web` 가 V0015 PUBLIC-client (PKCE-only, no secret) 그대로.
   - fixture 의 `mintTokens()` 함수 (현재 라인 67-136) 삭제.
   - admin_operators 의 `oidc_subject` 를 client_id (`'platform-console-web'`) 에서 user-email (`'e2e-super-admin@example.com'`) 로 업데이트 — production user-based authorization_code 흐름의 `sub=email` 클레임에 매칭.
3. **OIDC issuer URL host-resolution 문제 해결**:
   - browser 가 `http://auth-service:8081/oauth2/authorize` 로 302 점프하지만 host (Playwright 실행 환경) 에서 `auth-service` DNS 미해결. 두 가지 옵션:
     - **Option A (선호)**: Playwright `context.route('http://auth-service:8081/**', …)` 인터셉트 → `route.fetch({ url: rewrittenLocalhost18081 })` 으로 host-published 포트로 재발신 + `route.fulfill({ response })` 로 응답 회신. DNS lookup 우회. browser URL bar 는 `auth-service` 유지 → SAS 가 relative `/login` 302 회신 시 동일 패턴 매치 + 인터셉트 chain. JWT `iss=http://auth-service:8081` 와 console-web `OIDC_ISSUER_URL=http://auth-service:8081` 동일 → 검증 통과.
     - **Option B**: Chromium `--host-resolver-rules="MAP auth-service 127.0.0.1"` flag + docker-compose auth-service publish 변경 `18081:8081 → 8081:8081` (host port 일치). 더 stable 하지만 host 의 8081 점유 충돌 위험.
   - 선택은 impl-time decision; Option A 부터 시도, blocker 발생 시 Option B fallback (impl 노트에 결정 근거 기록).
4. **seed.sql 의 credentials row insert** — auth_db.credentials 에 `e2e-super-admin@example.com` 의 Argon2id 해시 row 추가 (`devpassword123!` 평문, 기존 admin_operators / V0014__seed_dev_super_admin_password.sql 동일 hash). tenant_id='gap' (V0015 platform-console-web client 의 tenant_id 일치). account_id=신규 UUID.

After this task, e2e 환경의 `oauth_clients.platform-console-web` 가 production 정의와 **완전 byte-identical** (PKCE-mandatory PUBLIC client; no client secret) — 모든 backdoor 제거. fixture 는 production user-flow 그대로 reproducer 역할.

# Decision authority

- **Why HTML form-fill (NOT keep client_credentials backdoor)**:
  - PC-FE-019 의 documented honest gap (b) 정면 해소. backdoor 가 production V0015 의 PKCE-only 정의를 e2e 환경에서만 깨뜨리는 *test-only divergence* — 향후 V0015 가 변경되면 backdoor 와 production 간 drift 가 silent fail 로 surface. true OIDC PKCE 로 미러링 하면 production-parity 강제.
- **Why now (NOT defer)**:
  - BE-309 가 막 ship 되어 enabling prerequisite 가 갓 unlock. 첫 nightly cron 이 fire 되기 전에 fixture 가 production-grade 가 되는 게 architectural cleanliness. backdoor 와 form-fill 의 차이가 *향후 silent drift signal* 이 될 수 있어 이른 closure 가 가치.
- **Why Option A (page.route rewrite) preferred over Option B (publish port change + Chromium flag)**:
  - Option A 가 docker-compose.e2e.yml 변경 0 + Playwright config 변경 0 (fixture 내부에서만 처리) — 가장 작은 blast radius. fixture file isolation 으로 향후 변경도 fixture-only.
  - Option B 는 docker-compose 8081 publish 가 host 에서 다른 8081 (예: local dev mode 의 auth-service) 와 충돌 가능. 환경 의존적.
  - Option A 가 impl-time 에 blocker (e.g., Playwright `route.fulfill` 의 redirect-chain 처리 미흡) surface 하면 B 로 fallback — impl 노트에 명시.
- **Why no producer change**:
  - auth-service `/login` HTML form (BE-309), `/oauth2/authorize`, `/oauth2/token` 모두 그대로. admin-service `/api/admin/auth/token-exchange` 그대로. console-web `/api/auth/login` + `/api/auth/callback` 그대로. fixture 가 production user 와 동일 경로 reuse.
- **Why no console-bff change**:
  - console-bff 는 backend resource server — fixture 변경과 무관. ADR-MONO-017 D4 HARD INVARIANT (console-bff zero retrofit) 16회째 보존.
- **Why no spec change**:
  - PC-FE-016 / 017 / 018 의 click sequence 보존. fixture 변경 후에도 두 spec 의 `loginAsSuperAdmin(context)` API 시그니처 동일 — spec body 0 diff.

---

# Scope

## In scope

- `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` — `mintTokens()` 삭제 + 신규 `driveOidcPkceLogin(context, page, credentials)` 함수 (browser-driven form-fill). `loginAsSuperAdmin(context)` 의 signature 유지 (caller-side 변경 0). docstring 전면 rewrite — backdoor 근거 삭제 + production parity 명시.
- `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` — § 2 의 `UPDATE oauth_clients` 블록 (라인 70-96) 완전 삭제 (`platform-console-web` 는 V0015 PUBLIC-client 그대로). § 3 의 `INSERT INTO admin_operators … VALUES ('e2e-super-admin', ..., 'platform-console-web', ...)` 의 `oidc_subject` 를 `'e2e-super-admin@example.com'` 로 변경. § 신규 (§ 2.5 추가): `auth_db.credentials` 에 `e2e-super-admin@example.com` Argon2id-hashed row INSERT (tenant_id='gap'; account_id=신규 UUID; credential_hash=`devpassword123!` 해시).
- `projects/platform-console/apps/console-web/playwright.config.ts` — IF Option B fallback 필요 시 Chromium `launchOptions.args` 에 `--host-resolver-rules` 추가. Option A 가 동작하면 0 변경.
- `projects/platform-console/docker-compose.e2e.yml` — IF Option B fallback 필요 시 `auth-service.ports` 를 `18081:8081 → 8081:8081` 변경. Option A 가 동작하면 0 변경. **docstring (라인 351-356) 의 client_credentials 설명 삭제 + production OIDC PKCE 정확 mirror 로 변경**.
- `projects/platform-console/tasks/ready/TASK-PC-FE-022-fixture-oidc-pkce-form-fill-migration.md` (this file).
- `projects/platform-console/tasks/INDEX.md` ready list.

## Out of scope

- Production code change in console-web `/api/auth/login` + `/api/auth/callback`. Reuse as-is.
- auth-service `/login` form 의 UI/UX 개선 — DefaultLoginPageGeneratingFilter 의 stock HTML form 그대로.
- admin-service `/api/admin/auth/token-exchange` 변경 — RFC 8693 production 그대로.
- 새 e2e spec 추가 — PC-FE-016 / 017 / 018 두 spec 그대로 reuse.
- PR-time smoke 경로 — TASK-PC-FE-021 가 별도 path. 본 task 는 nightly full-stack 의 fixture migration 만.

---

# Acceptance Criteria

- **AC-1**: `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` 가 `client_credentials grant` 호출 0; `fetch()` 호출 0 (모든 인증 navigation 이 `page.goto` + `page.fill` + `page.click` browser action 으로 진행); 신규 `driveOidcPkceLogin()` helper 가 production `/api/auth/login → /oauth2/authorize → /login form → /oauth2/authorize → /api/auth/callback` 의 전 경로 navigation 수행 + 최종 3 cookie (console_access_token / console_operator_token / console_active_tenant) set 검증.
- **AC-2**: `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` 가:
  - `UPDATE oauth_clients` 블록 (V0015 PUBLIC-client 정의 깨는 부분) 완전 부재 (`grep -c "UPDATE oauth_clients" seed.sql` = 0).
  - `auth_db.credentials` 의 `INSERT` 가 `e2e-super-admin@example.com` row 를 fixed Argon2id hash + tenant_id='gap' + account_id=UUID 형태로 idempotent (INSERT IGNORE) 으로 추가.
  - `admin_operators.oidc_subject` 가 `e2e-super-admin@example.com` (이전: `platform-console-web`).
- **AC-3** (byte-diff invariant): `projects/global-account-platform/` 0 byte diff. `projects/finance-platform/` 0 byte diff. `projects/platform-console/apps/console-bff/src/**` 0 byte diff (ADR-MONO-017 D4 HARD INVARIANT 16회째 보존).
- **AC-4** (regression — nightly): 첫 nightly cron 이 `platform-console-e2e-fullstack` job 에서 `operators-profile.spec.ts` + `operators-admin-profile.spec.ts` 모두 PASS. fixture migration 이 production 동일 user-flow 라서 회귀 검증 효과 강화.
- **AC-5** (docstring): `fixtures/login.ts` JSDoc 가 backdoor 근거 (라인 8-44) 완전 삭제 + production OIDC PKCE 정확 동일 경로 명시 + `driveOidcPkceLogin` 의 navigation step-by-step 기록.
- **AC-6** (Option A vs B impl 노트): impl PR 의 PR body 또는 task 의 § Notes 에 어떤 option 선택했는지 + 근거 기록 (Option A 성공 시 "page.route rewrite 동작 확인"; Option B fallback 시 blocker 의 정확한 stack trace + Option B 적용 후 동작 확인).

---

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Authentication (OIDC PKCE production 경로; fixture 가 이를 mirror).
- `projects/global-account-platform/specs/features/authentication.md` § OIDC Authorization Code + PKCE.
- `projects/global-account-platform/specs/services/auth-service/dependencies.md` § BE-309 `/login` HTML form surface.
- TASK-PC-FE-019 (done) — fixture 가 본 task 의 baseline.
- TASK-BE-309 (done) — enabling prerequisite.

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6 admin operator-token-exchange (RFC 8693) — fixture 가 production callback 동일 경로로 호출.
- `projects/global-account-platform/specs/contracts/http/internal/auth-internal.md` § OAuth2 endpoints (authorize / token / login form) — fixture 가 browser-driven 으로 통과.

---

# Edge Cases

- **EC-1 (Playwright `context.route` redirect chain)**: SAS `/oauth2/authorize` 가 relative `/login` 302 회신 시 browser URL bar 는 여전히 `auth-service:8081` (route.fulfill 의 응답 본문만 회신; URL 변경 X). 다음 navigation 도 동일 pattern 으로 인터셉트 → `route.fetch({ url: rewrittenLocalhost18081 })` chain. impl 시 verify.
- **EC-2 (CSRF token on /login form)**: Spring Security DefaultLoginPageGeneratingFilter 가 hidden CSRF input 자동 포함. Playwright `page.click('button[type=submit]')` 가 form submit + CSRF token 자동 포함. 별도 처리 X.
- **EC-3 (session-fixation migration)**: SAS 가 successful auth 후 새 HttpSession 발행 (JSESSIONID 변경). browser cookie store 가 자동 새 JSESSIONID 반영. fixture 별도 처리 X.
- **EC-4 (PKCE verifier cookie propagation)**: console-web 의 `/api/auth/login` 가 PKCE verifier 를 HttpOnly cookie 로 set, 후속 `/api/auth/callback` 가 동일 cookie 읽음. browser context cookie store 가 자동 처리 — 별 storage X.
- **EC-5 (storageState capture 시점)**: 모든 callback 종료 후 console-web 가 redirect 마무리한 후 `context.storageState()` 호출. 너무 일찍 호출하면 마지막 redirect 진행 중인 partial state 캡처 위험 — `page.waitForURL('http://localhost:3000/')` 으로 final redirect 완료 대기.
- **EC-6 (test-environment specific credentials)**: `devpassword123!` 는 hardcoded test-only password. seed.sql 의 idempotent INSERT IGNORE 가 매 CI 실행 시 동일 hash 유지 — 비밀 회전 불필요 (test-only).

---

# Failure Scenarios

- **FS-1 (Option A `route.fulfill` redirect chain blocker)**: Playwright `route.fulfill` 의 response 가 redirect 응답 시 browser 가 다음 navigation 을 trigger 하지 않을 수 있음 (Playwright bug or browser security). 발생 시: impl 노트 + Option B (Chromium `--host-resolver-rules` + docker publish port 변경) fallback.
- **FS-2 (Spring Security `/login` form selector drift)**: DefaultLoginPageGeneratingFilter 의 HTML 이 BE-309 의 stock 형태와 다를 경우 Playwright `page.fill('input[name=username]')` 실패. BE-309 의 IT 가 verify 한 form 구조 (`name=username`, `name=password`) 와 동일 selector 사용; selector mismatch 시 IT trace 와 cross-check.
- **FS-3 (token-exchange tenant mismatch)**: admin token-exchange 가 user-based JWT 의 `sub=email` 을 `admin_operators.oidc_subject` 로 lookup. seed.sql 가 `oidc_subject='e2e-super-admin@example.com'` 로 업데이트 — 매칭 실패 시 401. AC-2 가 이 변경 검증.
- **FS-4 (credential hash mismatch)**: seed.sql 의 `credentials.credential_hash` 가 BE-309 `PasswordHasher.verify(password, hash)` 와 일치하지 않으면 form login 시 `BadCredentialsException`. 동일 hash 가 admin_operators / V0014 dev seed 에서 verified — drift 가능성 0. impl 시 BE-309 IT (`FormLoginIntegrationTest`) 에서 사용한 동일 hash 재사용.
- **FS-5 (page.route OIDC_ISSUER_URL drift)**: 향후 docker-compose 의 `OIDC_ISSUER_URL` env 가 변경 (예: `http://auth-service:8081` → `http://gap-auth.local:8081`) 시 fixture 의 `context.route(pattern)` 와 mismatch. fixture 의 pattern 을 env-driven (`process.env.E2E_OIDC_ISSUER_URL ?? 'http://auth-service:8081'`) 으로 작성 + docker-compose 의 단일 source of truth 유지.
- **FS-6 (storageState invalidation 후 재로그인)**: storageState 가 캐시된 후 nightly 재실행 시 SAS access_token TTL (30min) 만료 → 401 → fixture 재실행 필요. global-setup 매 nightly 가 fresh login 수행 (현재 동작 동일) — 별 변경 X.
