# TASK-MONO-801 — a request whose path the servlet firewall rejects up front is answered 401 «Authentication required» instead of 400

- **Status**: done (AC-0–3 closed 2026-10-20 UTC · 24 services, ecommerce auth-service is excluded from the build · AC-4 closed 2026-10-23 UTC, 33rd window)
- **Type**: fix (servlet security configuration — shared shape across services)
- **Created**: 2026-10-20 UTC
- **Scope class**: monorepo-level (the same security-chain shape repeats in every servlet service; the fix may land in a shared library or in each service — AC-0 decides)

> **Analysis model:** Opus 5.5 / **recommended implementation:** Opus (security configuration across many services; the choice between «permit the `/error` dispatch» and «a custom `RequestRejectedHandler`» changes what every service answers)

# Origin

Split from `TASK-MONO-800` AC-0 (2026-10-20 UTC, owner decision «별도 티켓»). `StrictHttpFirewall` rejects a path containing `;`, `//`, `%25` (and `%2E`, `/../`, `%00`, non-ASCII, …) **up front** inside `FilterChainProxy`. Spring Security 6.4.2's default `HttpStatusRequestRejectedHandler` then calls `sendError(400)`, the container dispatches to `/error`, and — measured in iam admin-service with a security chain shaped like admin's — `anyRequest().denyAll()` blocks that dispatch, so the client receives **401 `TOKEN_INVALID` «Authentication required»**. With `/error` permitted the same requests return Boot's default 400 body. Nothing in the repo permits `/error` (grep of main code, 2026-10-20). `TASK-PC-FE-133` once saw 400 for `%25` through a gateway, so not every service behaves the same (not measured).

The handler fix in `TASK-MONO-800` never sees these requests (they are rejected before the dispatcher).

# Goal

A request the firewall rejects for its path is a client error and is answered **400** in the common error envelope (`platform/error-handling.md` § Error Response Format), code `BAD_REQUEST` (same code `TASK-MONO-800` chose), without echoing the rejected path, and without a 401 that sends the client to re-authenticate.

# Scope

- **AC-0 (measure first)**:
  - Per servlet service (the 31 `CommonGlobalExceptionHandler` subclasses + wms master · admin + ecommerce auth, list in `TASK-MONO-800` § AC-0 result): does it run spring-security-web, how is `/error` treated (permit · deny · authenticated), and what does `GET /x;y` answer today — at least one measured service per distinct security-chain shape.
  - The gateways: what a client actually receives when the path goes through the reactive gateway first (the gateway may reject or normalise before the servlet service sees it — `TASK-PC-FE-133`).
  - Pick the fix from the measurement: (a) a shared `RequestRejectedHandler` bean that writes the common envelope directly (no `/error` dispatch), or (b) permit the `ERROR` dispatcher type in each chain. 🔴 (b) opens `/error` to every error path — measure what else that changes before choosing it.
- Fix + tests through the real filter chain (`@SpringBootTest` with a real server or `MockMvc` with `springSecurityFilterChain` — a slice without the chain proves nothing).

## Out of scope

- Relaxing the firewall.
- `%2F` · `%5C` — Tomcat rejects them before Spring (its own 400 HTML page); a separate decision if ever wanted.
- The header-shaped rejection (`TASK-MONO-800`).

# Acceptance Criteria

- [x] **AC-0** (→ § AC-0 result) — the per-service table (security chain · `/error` treatment · today's status for `;` · `//` · `%25`), the gateway behaviour, and the chosen fix with its reason, recorded with file:line.
- [x] **AC-1** — an up-front firewall rejection → 400 `BAD_REQUEST` in the common envelope, no echo of the path, one `WARN` line, no `ERROR`; through the real filter chain. (→ § Implementation — MockMvc through the real `FilterChainProxy`, shapes A · B · C)
- [x] **AC-2** — red → green tests for at least two shapes; controls: an unauthenticated normal request still 401, an authorised normal request unchanged, a real unexpected exception still 500. (→ § Implementation — three shapes)
- [x] **AC-3** — `platform/error-handling.md` note under `BAD_REQUEST` covers the path case too (one line with `TASK-MONO-800`'s).
- [ ] **AC-4** — deploy state (AMI bake for the demo); live «same request → 400» in the next window.

# Related Specs

- `platform/error-handling.md`
- `platform/shared-library-policy.md` (if the fix lands in a shared library)
- `platform/security-rules.md` (if present — the `/error` permit question)

# Related Contracts

- The common error envelope (`platform/error-handling.md` § Error Response Format).

# Edge Cases

- A service whose `/error` is already `authenticated()` would answer 400 to an authenticated caller and 401 to an anonymous one — the test must cover both.
- A rejected path that is also unauthenticated: answer 400 (the request is malformed whoever sends it) — confirm no security test asserts 401 for such a path.

# Failure Scenarios

1. Permit `/error` everywhere and leak a stack trace or internal message through Boot's default error body — the fix must answer the common envelope.
2. Fix one service and assume the rest — AC-0's table exists to prevent it.
3. Test with a slice that skips `FilterChainProxy` — green test, production still 401.

# AC-0 result (2026-10-20 UTC, origin/main `9a060da42`)

**Ran vs read.** Ran: throwaway `@SpringBootTest(RANDOM_PORT)` probes on real Tomcat for scm logistics · wms master · ecommerce product — each importing the service's real `SecurityConfig` + real `GlobalExceptionHandler`, a stub `JwtDecoder` (token `good`), a probe controller and a deliberately throwing servlet filter; raw socket requests; three variants each (today · (b) approximated by a highest-order chain permitting the ERROR dispatch · (a) a `RequestRejectedHandler` bean). Two reactive probes on Netty + Spring Cloud Gateway with a path-recording stub upstream (one with a WebFlux security chain, one without). `dependencies --configuration runtimeClasspath` for iam gateway · ecommerce user · iam security-service · ecommerce gateway. Every Gradle rc read explicitly (all 0). Scratch worktree and branch removed. Everything else read only. Caveat: the probe forced `server.error.include-message=always` (ecommerce product doesn't set it).

## Chain shapes

No main code permits `/error` or `DispatcherType.ERROR`; no `WebSecurityCustomizer` · `ignoring()` · `RequestRejectedHandler` · `HttpFirewall` · `ServerWebExchangeFirewall` customisation anywhere. An up-front rejection does `sendError(400)` and the `/error` dispatch meets the first chain matching `/error`.

| Shape | Services | `/error` dispatch | Today `;` `//` `%25` |
|---|---|---|---|
| A `denyAll` + service JSON 401 entry point | assembler `anyRequestDenied()` (`libs/java-security-servlet/.../ResourceServerChainAssembler.java:530`): erp approval · masterdata · notification · read-model; scm procurement · logistics · demand-planning · inventory-visibility; fan artist · community · membership · notification. Hand-written: iam admin (`SecurityConfig.java:251`) · account (`:179`) · auth (`:188`); finance account (`:90`) · ledger (`:114`) | denied, anonymous | **measured (logistics): 401 `UNAUTHORIZED` «Authentication required» for all three — also with a valid token** |
| B `authenticated()` | wms master (`SecurityConfig.java:57`) · inbound · outbound · inventory · admin (nested `{"error":{…}}` envelope); ecommerce auth (`SecurityConfig.java:53`, custom filter — not measured) | anonymous → entry point | **measured (wms master): 401, also with a valid token** |
| C default chain `permitAll` | ecommerce product (`ProductSecurityConfig.java:152`) · order (`OrderSecurityConfig.java:155`) | permitted | **measured (product): already 400, but Boot's body `{timestamp,status,error,path}` — echoes the path, not the common envelope** |
| D no spring-security-web | ecommerce user · shipping · promotion · settlement · payment · notification · search · review; iam security-service | no firewall | out (runtime classpath confirmed for user · security-service) |

🔴 The Edge Case «authenticated caller → 400 when `/error` is `authenticated()`» **does not happen**: the rejection fires before any authentication filter, so the ERROR dispatch is always anonymous (measured 401 with a valid Bearer on A and B). Controls: no token 401 · valid token 200 · bad token 401.

## Gateways

- **Six reject first**: ecommerce (`gateway/config/SecurityConfig.java:29`) and erp · fan · finance · scm · wms via `libs/java-gateway/.../SecurityConfig.java:30` — Spring Security 6.4.2's `WebFilterChainProxy` applies `StrictServerWebExchangeFirewall` + `HttpStatusExchangeRejectedHandler`. **Measured**: `/x;y` · `//x` · `/%25` · `/api/x/%25EC%2585` · `/api/%2E%2E/x` → **400, empty body, no Content-Type**, upstream saw nothing.
- **iam gateway has no spring-security** (runtime classpath). **Measured**: `/x;y` · `/%25` forwarded verbatim; `//x` normalised to `/x`. ⇒ through the iam gateway `;` and `%25` reach iam admin · auth · account → 401.
- `TASK-PC-FE-133`'s 400 for `%25…` (`done/TASK-PC-FE-133-…md:15-19`) went through the ecommerce gateway — the console logged `HTTP_400` (its fallback for a body without `code`), consistent with the gateway's empty-body 400 (inferred).

## Options

- **(a) a `RequestRejectedHandler` bean** (measured on A · B · C): `/x;y` · `//x` · `/%25`, with or without a token → **400 with the envelope written directly**, no error dispatch, no path echo. Every control unchanged (401 · 200 · bad token 401 · 403/404 · filter-exception case). `WebSecurity` picks the bean up from the context — one per `FilterChainProxy`, so multi-chain services are covered. It also catches lazy rejections thrown inside a security filter (today `sendError` → 401); those inside MVC stay with `TASK-MONO-800`.
- **(b) permit `DispatcherType.ERROR`** (approximation): 400 with **Boot's body echoing `path`**. 🔴 And a servlet-filter exception with a valid token, today **403 `PERMISSION_DENIED`** on A, becomes **500 with Boot's body carrying the exception message + path** — 19 services set `server.error.include-message: always` (e.g. wms master `application.yml:68`). ecommerce auth's `OAuthController.java:56,87,96` `sendError` would switch from 401 to a Boot body. ⇒ (b) widens exactly the leak Failure Scenario 1 names.
- No servlet test asserts 401 for such a path (grep `%25` · `;jsessionid` · `"//` · `RequestRejected` · `HttpFirewall` in `src/test`). Two reactive gateway filter tests use such paths and are unaffected.

## Where (a) lives

`libs/java-web-servlet` has no spring-security-web; `libs/java-security` is shared with the reactive gateways. **`libs/java-security-servlet`** already depends on `spring-boot-starter-security` (`build.gradle:40`) and is servlet-only by a build gate. The library ships no auto-configuration (`ResourceServerChainAssembler.java:41-55`, `platform/shared-library-policy.md` § 83), so it ships a **factory** and **each service declares `@Bean RequestRejectedHandler`**. Reach: 19 services already on that library (erp 4 · finance 2 · scm 4 · fan 4 · wms 5) + 6 with spring-security but not the library (iam admin · auth · account; ecommerce product · order · auth).

Side finding (out of scope, synthetic filter only): on shape B a servlet-filter exception with a valid token already returns a Boot 500 carrying the exception message (`include-message: always`).

# Owner decisions (2026-10-20 UTC, choice window)

- **(a)** — factory in `libs/java-security-servlet` + a `RequestRejectedHandler` bean in **all 25 services**, one atomic PR.
- **wms keeps its envelope** — the factory takes a body writer; wms writes its nested `{"error":{…}}`, everyone else the common `ErrorResponse` (`BAD_REQUEST`, fixed message, no path echo, one `WARN`).
- Gateways' empty-body 400 and the shape-B filter-exception 500 stay out (separate decisions).

# Implementation (2026-10-20 UTC)

Worktree branch `task/mono-801-firewall-path-400` off `5b579488c`. Not committed.

## What landed

- **Factory** `libs/java-security-servlet/.../FirewallRejections.java` — `handler()` (flat `{code,message,timestamp}`, the shape the module's own 403 body already uses) and `handler(BodyWriter)` (`(code, message) -> String`, `throws IOException`). Sets 400 · `application/json` · UTF-8, writes `CODE = "BAD_REQUEST"` and the fixed `MESSAGE = "The request was rejected because its URL, method or headers are not allowed"`, logs exactly one `WARN` `Request rejected by the servlet firewall (RequestRejectedException)` (class name only — no path, no exception message, no stack), leaves an already-committed response alone. No auto-configuration.
  - Message says «URL, method or headers», not only «URL or method»: `FilterChainProxy` also routes here a *lazy* rejection thrown inside a security filter (a header read during authentication).
  - 🔴 **Exactly one bean per context.** Measured in `spring-security-config-6.4.2` `WebSecurity.setApplicationContext` (bytecode): `getBean(RequestRejectedHandler.class)` inside `catch (NoSuchBeanDefinitionException)` — two beans throw `NoUniqueBeanDefinitionException` (a subclass) and are **silently ignored** (default handler back). Recorded in the class javadoc. 25 declarations, one per service (grep `RequestRejectedHandler requestRejectedHandler` under `projects/` = 25 files). Also: a declared bean replaces the default `CompositeRequestRejectedHandler(ObservationMarking…, HttpStatus…)`, so the observation marking of a rejection is no longer emitted.
- **Beans** — one `@Bean RequestRejectedHandler` next to the chain in each service's security configuration, one-line why-comment citing TASK-MONO-801:
  - default `FirewallRejections.handler()`: erp approval · masterdata · notification · read-model; finance account · ledger; scm procurement · logistics · demand-planning · inventory-visibility; fan artist · community · membership · notification; iam admin · auth · account; ecommerce product · order.
  - body-writer overload with the service's own envelope type: wms master (`ApiErrorEnvelope` nested) · admin (`ApiErrorEnvelope` nested) · inbound · outbound · inventory (`com.example.web.dto.ErrorResponse` — 🔵 these three emit the **flat** envelope, not the nested one; their contracts say so since TASK-BE-567 — so «wms writes its nested envelope» holds for master · admin only and the other three keep their own flat type).
- **Dependency added** `implementation project(':libs:java-security-servlet')` to iam admin · auth · account, ecommerce product · order. Each already had `spring-boot-starter-security` + resource server (iam auth via the authorization-server starter), so the module adds no new framework surface; no documented rule against it found (ecommerce's TASK-BE-571 deferral concerns `TenantClaimEnforcer` only).
- **`platform/error-handling.md`** `BAD_REQUEST` row extended in the same cell as TASK-MONO-800's sentence.

## Deviation — 24 services, not 25

🔴 **ecommerce auth-service is not built**: `settings.gradle:61` — «apps:auth-service excluded from build by TASK-BE-132 (decommissioned)». AC-0's list counted it from source. A bean was written, the Gradle task did not exist (`Cannot locate tasks … project 'auth-service' not found`), and the edit was reverted — adding code to a module nothing compiles would be unverifiable dead code. Live reach is therefore **24**.

## Tests (red → green, MockMvc through the real `FilterChainProxy`)

Raw paths passed as `java.net.URI` so MockMvc keeps them (a template string is re-encoded, `//x` parses as an authority). 🔵 **What red looks like in MockMvc**: 400 with an **empty body** — MockMvc does no error dispatch, so the 401 (A · B) / Boot-body (C) that AC-0 measured on Tomcat cannot appear here; the envelope assertions are what fail. The handler writes the response itself, so the container's error path is no longer on the route.

| Test | Shape | Cases | Red (bean's `@Bean` commented out) | Green |
|---|---|---|---|---|
| `libs/java-security-servlet` `FirewallRejectionsTest` | unit, real `StrictHttpFirewall` rejections | 9 | compile failure (class absent) rc=1 | 9/9 rc=0 |
| scm logistics `config/FirewallRejectionSliceTest` | A (`denyAll`) | 9 — `/x;y` · `…;jsessionid=` · `//` · `%25` · `%2E%2E`, with-token, controls 401 · 404 · 500 | 6 fail «No value at JSON path `$.code`», 3 controls pass, rc=1 | 9/9 |
| wms master `controller/FirewallRejectionSliceTest` | B (`authenticated()`, nested) | 8 | 5 fail «No value at JSON path `$.error.code`», 3 controls pass, rc=1 | 8/8 |
| ecommerce product `controller/FirewallRejectionSliceTest` | C (permit-all + `/internal/**`) | 8 (incl. `/internal/sellers/s-1;x`, `$.path` absent) | 5 fail «No value at JSON path `$.code`», 3 controls pass, rc=1 | 8/8 |

Each rejection case asserts: 400 · `application/json` · `BAD_REQUEST` · the fixed message · timestamp · body does not contain the raw path · exactly one `WARN` from `FirewallRejections` · no `ERROR` on the root logger.

## Commands (all rc read explicitly, logs in the session scratchpad)

- `./gradlew :libs:java-security-servlet:test --tests '*FirewallRejectionsTest'` → rc=1 (red, compile) ; `./gradlew :libs:java-security-servlet:check` → rc=0
- `./gradlew <24 × :projects:…:compileTestJava> --continue` → rc=0 (first attempt rc=1 only because the ecommerce auth-service task does not exist)
- logistics slice: green rc=0 → red rc=1 → bean restored
- master + product slices: green rc=0 → red rc=1 → beans restored (grep `RED-PROBE` under `projects/**/src` = 0)
- `./gradlew :libs:java-security-servlet:check :projects:scm-platform:apps:logistics-service:test :projects:wms-platform:apps:master-service:test :projects:ecommerce-microservices-platform:apps:product-service:test --rerun --continue` → rc=0; logistics 89 tests / 0 skipped / 0 failed · wms master 759 / **43 skipped** / 0 failed · product 457 / 0 / 0

## Not done / open

- **Docker is not running on this host** (`docker info` → pipe not found). wms master's 43 skipped are its Docker-gated tests; no Testcontainers IT ran. Not affected by this change as far as read, but not measured.
- Full `test` ran for the three services above only; the other 21 were compiled (`compileTestJava`), not tested.
- No real-container (Tomcat, raw socket) run after the fix — AC-0's probe measured (a) on Tomcat for A · B · C; this run proves it through MockMvc's `FilterChainProxy`.
- AC-4 (deploy + live) open.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- Deploy state: baked — `283fd39cf` → AMI `ami-02b3ba00a3b2ff622` (tag RepoCommit matches) · pin PR #4498 (`c5e3ddad3`) · instance `i-0f39aec49ddbaa899`. The impl merge `4d35a61ee` (#4434) is an ancestor of `283fd39cf`.
- **AC-4 ✅** — `GET https://auth.hubwang.com/oauth2/authorize;y` → **400** `{"code":"BAD_REQUEST","message":"The request was rejected because its URL, method or headers are not allowed"}` (the common envelope, no path echo).
- 🔵 Limit of the path through the forwarder: `/x;y` is treated like `/x` by the Vercel auth-forwarder (→ 404), so a path whose `;` the forwarder strips never reaches the firewall. Not measured in this window: `TASK-MONO-672` § Order «Still open» 12 ② (`/api/admin/x;y` · `/%25` through the iam gateway with an operator token) and ③ (wms master `GET /x;y` direct, nested envelope). AC-4's verb («same request → 400») is met by the request above.

### Close — four dimensions (2026-10-23 UTC)

- (a) PR #4434 `state=MERGED` · `mergedAt` 2026-10-20T16:45:29Z.
- (b) merge commit `4d35a61ee` is an ancestor of `origin/main` (`git merge-base --is-ancestor`) and of the baked `283fd39cf`.
- (c) the merged PR's `statusCheckRollup`: 70 checks, 0 failing (SUCCESS 38 · SKIPPED 32).
- (d) `# Acceptance Criteria` opened and read — AC-0–3 ✅ (existing) · AC-4 ✅ (above). The AC-4 box is left as it was (a `review/` file is frozen); this section records the closure.
- ⇒ `review/ → done/`.
