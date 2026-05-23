# Task ID

TASK-BE-311

# Title

auth-service `e2e` profile `/login` form rendering — investigate + restore SAS `DefaultLoginPageGeneratingFilter` activation. PC-FE-028 iter 7 trace evidence (dispatch run `26327957129`, 2026-05-23) shows `GET /login` returns **500 INTERNAL_ERROR** with `org.springframework.web.servlet.resource.NoResourceFoundException: No static resource login.`, meaning Spring routes the request through `ResourceHttpRequestHandler` (static-resource path) instead of `WebLoginSecurityConfig#webLoginFilterChain`'s `formLogin()` pipeline. TASK-BE-309 added `WebLoginSecurityConfig.java` with `@Order(0)` chain + `formLogin(loginPage = "/login")` and its `FormLoginIntegrationTest` passes (5/5 with `@Tag("integration")` MySQL Testcontainers). The e2e docker-compose deployment behaves differently — investigation needed to determine why the form-login filter chain is NOT intercepting `/login` in the e2e profile despite `application-e2e.yml` not disabling security.

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- fix

---

# Dependency Markers

- **depends on**: TASK-BE-309 (close `3a749fdd` — `WebLoginSecurityConfig.java` + `CredentialAuthenticationProvider.java` added). TASK-PC-FE-028 (close `7d312802` — DNS layer cleared, full URL chain reached; close chore PR #782).
- **prerequisite of**: nightly main GREEN restoration. TASK-PC-FE-028 closed with AC-2 DEFERRED to this task; AC-2 restoration depends on `/login` form rendering correctly so the Playwright fixture's `input[name="username"]` selector matches.

---

# Goal

Make the next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` SUCCESS. The fixture's `page.waitForSelector('input[name="username"]')` step (login.ts:178) must locate the SAS-generated HTML login form's username input within the 30s default timeout. After this fix lands, the auth-service e2e profile's `GET /login` returns Spring Security's `DefaultLoginPageGeneratingFilter`-produced HTML (the same form `FormLoginIntegrationTest` exercises) instead of routing the request through `ResourceHttpRequestHandler`.

## Root cause evidence (PC-FE-028 iter 7 dispatch `26327957129`)

- **trace.network full URL chain** captured (downloaded artifact `platform-console-playwright-report-nightly` → `trace.zip` → `trace.network`):
  1. `GET http://localhost:3000/api/auth/login?redirect=/` — Playwright `page.goto` → 307 Temporary Redirect → location `http://auth-service:8081/oauth2/authorize?...` ✓
  2. `GET http://auth-service:8081/oauth2/authorize?response_type=code&client_id=platform-console-web&...` — `serverIPAddress: 127.0.0.1`, `_serverPort: 8081` → 302 → location `http://auth-service:8081/login` ✓
  3. `GET http://auth-service:8081/login` — `serverIPAddress: 127.0.0.1`, `_serverPort: 8081` → **500 INTERNAL_ERROR** ✗

- **Response body** (`.json` extracted from trace artifact):

  ```json
  {"code":"INTERNAL_ERROR","message":"An unexpected error occurred","timestamp":"2026-05-23T08:26:46.315836094Z"}
  ```

- **auth-service compose log** (from workflow's `Dump docker compose logs on failure` step):

  ```
  auth-service-1 | [ERROR] Unexpected error
    logger=com.example.web.exception.CommonGlobalExceptionHandler
    stackTrace=org.springframework.web.servlet.resource.NoResourceFoundException: No static resource login.
        at org.springframework.web.servlet.resource.ResourceHttpRequestHandler.handleRequest(ResourceHttpRequestHandler.java:585)
        at org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter.handle(HttpRequestHandlerAdapter.java:52)
        at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1088)
        ...
  ```

- **Significance**: `ResourceHttpRequestHandler` is Spring's default static-resource path. If `webLoginFilterChain` (BE-309) had matched `GET /login`, Spring Security's `DefaultLoginPageGeneratingFilter` would have intercepted *before* DispatcherServlet's mapping resolution. The filter chain did NOT match.

## Hypothesis pool (to narrow during impl)

1. **`WebLoginSecurityConfig` bean not loaded in e2e profile** — `WebLoginSecurityConfig.java` has no `@Profile` annotation (visible at file top), so it should load for `e2e` profile. But if some `e2e`-specific config disables it (auto-configuration exclusion, bean override), the chain wouldn't register. Investigation: actuator `/beans` listing (if exposed) or boot log search for `webLoginFilterChain`.
2. **`CredentialAuthenticationProvider` constructor injection fails in e2e profile** — the bean takes `PasswordHasher` + `CredentialRepository` constructor args. If either is missing/different in the e2e profile (e.g. an embedded test bean vs production), the provider bean creation fails, cascading to `WebLoginSecurityConfig#webLoginFilterChain` bean creation failure, silently demoting the chain.
3. **Filter chain order regression** — another `@Order(N)` SecurityFilterChain with broader `securityMatcher` may be registered with lower N (and thus higher precedence) than `WebLoginSecurityConfig`'s `@Order(0)`, swallowing `/login` requests through a chain that doesn't render the form. Investigation: full `SecurityFilterChain` bean list with their order values.
4. **MVC handler mapping precedence over Spring Security** — Spring's `ResourceHttpRequestHandler` typically only runs AFTER all security filter chains decline the request. If somehow the chain ran but didn't end the request, MVC dispatch could reach the resource handler. Less likely but possible if `formLogin(loginPage = "/login")` configuration was tampered with.
5. **`FormLoginIntegrationTest` passes but e2e profile differs** — the IT uses `@SpringBootTest` with default test profile (no `@ActiveProfiles("e2e")`). Compare the IT's loaded profile vs the e2e docker deployment's `SPRING_PROFILES_ACTIVE=e2e` to find divergence.

## Decision authority — defer

Spec does not pre-select an implementation option. The impl PR's first commit should add diagnostic instrumentation (e.g. a startup log of the loaded SecurityFilterChain bean names + their securityMatcher patterns) and re-dispatch to narrow the hypothesis pool to 1. Subsequent commits apply the targeted fix. Cycle pattern's evidence-first → fix-first principle, mirroring PC-FE-027 (diagnostic) → PC-FE-028 (fix) chain shape.

---

# Scope

## In Scope

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/config/WebLoginSecurityConfig.java` — modify if hypothesis 1/3/4 lands.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java` — modify if hypothesis 2 lands.
- `projects/global-account-platform/apps/auth-service/src/main/resources/application-e2e.yml` — modify if hypothesis 1 lands (re-enable security / remove exclusion).
- Optional new diagnostic instrumentation (startup log of SecurityFilterChain registry) — drop after closure if not load-bearing.
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/` byte-diff — the e2e fixture is byte-correct (PC-FE-022 verified; FormLoginIntegrationTest exercises the same selector). AC-3.
- `.github/workflows/` byte-diff — PC-FE-028 iter 7 already added the /etc/hosts step; no further workflow change. AC-4.
- `projects/platform-console/docker-compose.e2e.yml` byte-diff — PC-FE-028 iter 4 already aligned ports; no further compose change. AC-5.
- 5 other producers (`wms / scm / erp / fan / ecommerce / finance / platform-console`) byte-diff — **24회째 zero-retrofit invariant** (ADR-MONO-017 D4). AC-6.
- Production code path changes that affect non-e2e profiles — fix must be scoped to e2e (or any profile-agnostic regression fix must explicitly preserve production behavior via `FormLoginIntegrationTest` continuing to pass). AC-7.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` **SUCCESS** (full job GREEN). Verified by `gh run view <id>` step 17 conclusion = success + `gh run view <id>` overall conclusion = success.
- [ ] **AC-2 (functional, secondary)** — `auth-service` container log shows `GET /login` returning 200 OK with HTML content (no `NoResourceFoundException` stack trace).
- [ ] **AC-3 (hard invariant — platform-console byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/` = empty (excluding INDEX cross-ref lines if any).
- [ ] **AC-4 (hard invariant — workflow byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/` = empty.
- [ ] **AC-5 (hard invariant — docker-compose byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/docker-compose.e2e.yml` = empty.
- [ ] **AC-6 (hard invariant — 6 other producers byte-unchanged, 24회째 zero-retrofit)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty.
- [ ] **AC-7 (production-path regression check)** — `FormLoginIntegrationTest` (5 cases) continues to PASS post-fix. Verified via `Integration (global-account-platform, Testcontainers)` push CI job GREEN.
- [ ] **AC-8 (diagnostic instrumentation cleanup, if added)** — if hypothesis-narrowing diagnostic instrumentation was added to a first commit, it is removed (or demoted to behind a `@Profile("diagnostic")` gate) before merge. `git grep -n 'TASK-BE-311.*diagnostic'` returns 0 lines in production code paths (test code OK).
- [ ] **AC-9 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/iam.md` (if present, else common only) + `rules/traits/multi-tenant.md`.

- [`projects/global-account-platform/PROJECT.md`](../../PROJECT.md).
- [`projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/config/WebLoginSecurityConfig.java`](../../apps/auth-service/src/main/java/com/example/auth/infrastructure/config/WebLoginSecurityConfig.java) — the `@Order(0)` chain that should intercept `/login` but empirically does not in e2e profile.
- [`projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java`](../../apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java) — constructor injection target; investigate hypothesis 2.
- [`projects/global-account-platform/apps/auth-service/src/main/resources/application-e2e.yml`](../../apps/auth-service/src/main/resources/application-e2e.yml) — e2e profile config (currently no security override visible).
- [`projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/FormLoginIntegrationTest.java`](../../apps/auth-service/src/test/java/com/example/auth/integration/FormLoginIntegrationTest.java) — green reference: verifies `GET /login` returns the form HTML in test profile. Profile divergence with e2e deployment is the lead clue.
- [`projects/platform-console/tasks/done/TASK-PC-FE-028-chromium-host-resolver-rules.md`](../../../platform-console/tasks/done/TASK-PC-FE-028-chromium-host-resolver-rules.md) — predecessor; full DNS-layer fix saga + closure narrative + 8th-layer pointer.
- [`projects/global-account-platform/tasks/done/TASK-BE-309-auth-service-form-login-html-surface.md`](../done/TASK-BE-309-auth-service-form-login-html-surface.md) — added `WebLoginSecurityConfig` originally; reference for the green path.

# Related Contracts

- None.

# Related Skills

- None additional.

---

# Edge Cases

- **Multiple SecurityFilterChain beans with conflicting securityMatcher** — only one chain handles each request (first match wins by order). If a chain with broader matcher and lower order than `@Order(0)` exists, it swallows `/login`. Check by listing all `@Bean SecurityFilterChain` in the boot log + their `Order` values.
- **`/login` static resource accident** — if anyone added a `src/main/resources/static/login.html` (or similar), Spring's `ResourceHttpRequestHandler` would resolve it directly. Currently no such file exists (verified via `Glob` `**/static/login*`). Worth a defensive check.
- **Spring Boot 3.4 auto-configuration change** — between BE-309's land time and now, no Spring Boot bump occurred. But `SecurityAutoConfiguration` exclusion via `EnableAutoConfiguration#exclude` would be a smoking gun.

# Failure Scenarios

- **AC-1 PASS but `FormLoginIntegrationTest` FAILS (AC-7 violated)** — means the fix targeted only the e2e profile but inadvertently regressed the test profile. Mitigation: scope the fix to e2e profile via `@Profile("e2e")` or `application-e2e.yml`-only changes.
- **AC-1 still fails with a DIFFERENT error class** — means hypothesis 1-5 are all wrong; 9th cycle layer surfaced. Author next-cycle task (TASK-BE-312 or similar) with the new error class.
- **Cannot reproduce locally** — `FormLoginIntegrationTest` runs Testcontainers MySQL and passes; e2e profile runs the same auth-service against compose MySQL. Differences are: profile name, MySQL connection params (Hikari), and bean-loading sequence. Investigation may require a one-off `workflow_dispatch` with extra logging to surface the bean registry state.

---

# Test Requirements

- `FormLoginIntegrationTest` continues to pass (AC-7).
- New diagnostic test (if added) lives under `@Tag("integration")` so the push CI job exercises it. Optional — only if the diagnostic step warrants a regression guard.
- AC-1 verification = `workflow_dispatch` on impl branch (≤30 min signal) or post-merge nightly push.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + GAP `tasks/INDEX.md` ready entry), (2) impl PR (auth-service source + tests + lifecycle), (3) close-chore PR with BE-303 3-dim + AC-1 dispatch verify result + post-merge nightly result.
- [ ] AC-1 through AC-9 all checked off in the close-chore PR description.
- [ ] If AC-1 still fails with a different error class, next-cycle task named in close chore narrative.

---

# 메타 (intended)

① **8th cycle layer in TASK-MONO-014 chain** — PC-FE-023 → 024 → MONO-132 → 025 → 026 → MONO-133 (diagnostic) → PC-FE-027 (diagnostic) → PC-FE-028 (DNS root-cause + 7-iter sub-cycle) → **TASK-BE-311 (this — form rendering 8th layer)**. Cycle pattern's progressive-surface principle continues to operate at its designed scale.

② **PC-FE-028 close chore documents `AC-1 (b)` clause first-class realization** — DNS fix succeeded; spec § Acceptance Criteria explicitly accepts "DIFFERENT error after DNS fix" as primary-scope success. This task picks up where PC-FE-028 left off.

③ **Investigation-first cycle pattern, mirroring PC-FE-027 → PC-FE-028** — spec does NOT pre-select an option; impl PR's first commit narrows hypothesis pool, subsequent commits apply targeted fix. Avoid the iter 1-3 trap of PC-FE-028 (committing to a config approach before evidence narrowed which approach worked in the deployment environment).

④ **`FormLoginIntegrationTest` is the green-path reference** — the IT proves `WebLoginSecurityConfig` works in the test profile. The e2e deployment must converge to the same behavior. Profile divergence (`@ActiveProfiles` vs `SPRING_PROFILES_ACTIVE=e2e` env) is the smoking-gun candidate.

⑤ **24회째 zero-retrofit (AC-6)** — fix lives entirely in `global-account-platform/`. ADR-MONO-017 D4 HARD INVARIANT continued.

⑥ **memory update post-cycle** — after BE-311 close, audit-memory cycle: 8-layer terminal documentation, 7-iter sub-cycle lessons from PC-FE-028 (Chromium flag environment-broken pattern; /etc/hosts as alternative DNS-layer fix), profile-divergence diagnostic pattern (FormLoginIntegrationTest green + e2e deployment red).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (diagnostic-first impl pattern; first commit = startup log instrumentation, second commit = targeted fix after evidence narrowing).
