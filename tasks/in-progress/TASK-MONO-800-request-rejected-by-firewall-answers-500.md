# TASK-MONO-800 — a request the servlet firewall rejects is answered 500 `INTERNAL_ERROR` instead of 400

- **Status**: in-progress (AC-0 measured + owner decisions 2026-10-20 UTC)
- **Type**: fix (shared library — `libs/java-web-servlet`)
- **Created**: 2026-10-20 UTC
- **Scope class**: monorepo-level (`libs/java-web-servlet` is used by every servlet service)

> **Analysis model:** Opus 5.5 / **recommended implementation:** Sonnet (one handler + tests; AC-0 measures the reach first)

# Origin

Found in the 31st demo window (`TASK-MONO-672` § 31st window — harvest, 2026-10-20 UTC). A measurement script sent `X-Operator-Reason` with one non-ASCII character (`④`) to iam admin-service. Spring Security's `StrictHttpFirewall` threw `org.springframework.security.web.firewall.RequestRejectedException` («The request was rejected because the header "X-Operator-Reason " has a value …»), and `CommonGlobalExceptionHandler` (`libs/java-web-servlet/src/main/java/com/example/web/exception/CommonGlobalExceptionHandler.java`) answered it through its catch-all `@ExceptionHandler(Exception.class)` → **500 `INTERNAL_ERROR` «An unexpected error occurred»**. Nothing was written. The console is not affected — it percent-encodes that header (`TASK-PC-FE-308`) — but any other client gets a 500 that names no cause, and the service logs an `ERROR` for what is a client mistake.

# Goal

A request the firewall rejects is a client error: answer **400** with a stable code, log it below `ERROR`, and keep the reason out of the body if it would echo the rejected value.

# Scope

- **AC-0 (measure first)**:
  - Which services use `CommonGlobalExceptionHandler` (servlet) and which have their own handler that would answer differently. Reactive services (`libs/java-web-reactive` or equivalent) are out unless they show the same 500.
  - How the exception reaches the MVC handler today (it is thrown in `FilterChainProxy`, before the dispatcher) — whether Spring Security's `RequestRejectedHandler` is configured anywhere, and whether the 500 is produced by the MVC handler or by an error dispatch. The fix goes where the exception actually lands.
  - Which header / URL shapes trip the firewall in practice (non-ASCII header value, encoded slash, `;`, `//`, …) — one live example is enough per shape, measured in a test.
- Fix: map `RequestRejectedException` to **400** with code `REQUEST_REJECTED` (or the closest existing code — AC-0 decides) and a fixed message that does not echo the rejected header value. Log at `WARN` (one line, no value).
- Tests: red first — a request with a non-ASCII `X-Operator-Reason` (and one other shape from AC-0) → today 500 → after 400; control: a normal request unchanged; the catch-all still answers 500 for a real unexpected exception.

## Out of scope

- Relaxing the firewall (allowing non-ASCII header values). The console already encodes; other clients should encode too.
- Changing the console.

# Acceptance Criteria

- [x] **AC-0** — the reach (services × handler), the landing point of the exception, and the shapes, recorded with file:line. → § AC-0 result.
- [ ] **AC-1** — a firewall-rejected request → 400 with a stable code; the body does not echo the rejected value; one `WARN` line, no `ERROR`.
- [ ] **AC-2** — red → green tests in `libs/java-web-servlet` (+ one service-level slice if AC-0 shows the handler is not the landing point); controls: normal request unchanged, real unexpected exception still 500.
- [ ] **AC-3** — the error-code catalog / contract that lists common error codes is updated (find it in AC-0; if none lists them, say so).
- [ ] **AC-4** — deploy state recorded (AMI bake needed for the demo; no live AC beyond «same request → 400» in the next window).

# Related Specs

- `platform/shared-library-policy.md` (project-agnostic shared code)
- `platform/lint-remediation-message-standard.md` (not applicable — runtime error, listed to rule it out)

# Related Contracts

- The common error-response shape used by `ErrorResponse.of(code, message)` — AC-0 names the document that owns it.

# Edge Cases

- A service that runs the firewall but not this handler (custom handler or reactive) — AC-0 lists it; fix only what shares the library unless the owner widens the scope.
- The rejected value may itself be sensitive (a token in a header) — never echo it.

# Failure Scenarios

1. Map the exception in the MVC handler while it actually lands elsewhere — the test passes on a slice that never goes through `FilterChainProxy`, and production still answers 500. AC-2's red test must go through the real filter chain.
2. Echo the rejected header value in the message — leaks whatever the client put there.
3. Widen to «all 4xx-looking exceptions» and change other services' responses silently — keep it to this exception.

# AC-0 result (2026-10-20 UTC, origin/main `89dc24c70`)

**Ran vs read.** Ran: three throwaway `@SpringBootTest` probes inside iam admin-service's test source set (real Tomcat, real `AdminExceptionHandler`, real `OperatorReasonDecodingFilter`, a security chain shaped like admin's — permit the probe path, `denyAll` the rest, JSON 401 entry point), requests sent as raw socket bytes, Gradle rc=0 read explicitly; plus a bare `StrictHttpFirewall` shape table. Scratch worktree and branch removed, nothing committed. Everything about other services is code reading.

## Reach

- `CommonGlobalExceptionHandler` is `abstract` with no annotation (`libs/java-web-servlet/.../CommonGlobalExceptionHandler.java:21`); no auto-configuration — each service subclasses it and adds `@RestControllerAdvice`. The library does **not** depend on spring-security-web (`libs/java-web-servlet/build.gradle:20-45`).
- **31 subclasses**: iam admin (`AdminExceptionHandler.java:56`) · auth · account · security-service (`QueryExceptionHandler.java:24`); erp approval · masterdata · notification · read-model; finance account · ledger; scm procurement · logistics · demand-planning · inventory-visibility; wms inbound · outbound · inventory; fan artist · community · membership · notification (via `AbstractDomainExceptionHandler`); ecommerce product · order · user · notification · payment · search · review · promotion · settlement · shipping.
- Subclasses that override the catch-all and still answer 500: ecommerce user (`GlobalExceptionHandler.java:127`) · notification (`:121`) · payment (`:113`) — a more specific base-class handler wins by exception type (inferred).
- **Not reached by a base-class method**: wms master-service (`GlobalExceptionHandler.java:228`) and wms admin-service (`:201`) hold a private `SHARED` instance and forward per method (`SHARED.handleGeneral(ex)`) — they need their own forwarding. ecommerce auth-service (`GlobalExceptionHandler.java:115`) is fully independent (500 `INTERNAL_ERROR`, ERROR log).
- Services with no spring-security-web declared (no firewall — inferred, transitive classpath not run): ecommerce user · shipping · promotion · settlement · payment · notification · search · review; iam security-service.
- Reactive gateways (iam · ecommerce · erp · fan · finance · scm · wms) don't use this library; `ServerWebExchangeFirewall` not measured — out.

## Landing point

- Boot 3.4.1 (`gradle.properties:5`) · Spring Security 6.4.2. No `RequestRejectedHandler` · `HttpFirewall` · `HandlerExceptionResolver` customisation in main code; no `/error` permit anywhere.
- `FilterChainProxy`'s default handler is already `HttpStatusRequestRejectedHandler` (400) — read by reflection.
- Two kinds of rejection: **up-front** (path · method — thrown in `FilterChainProxy` before any filter) and **deferred** (header names · values · parameters — thrown only when someone *reads* them from the wrapped request).
- The live 500 (measured): raw `④` arrives as UTF-8 bytes, Tomcat decodes ISO-8859-1 → the middle byte is `U+0091` (C1 control) → `OperatorReasonDecodingFilter` (`HIGHEST_PRECEDENCE+20`, `OperatorReasonDecodingFilter.java:43,53`) passes it unchanged (nothing to percent-decode) → the firewall wraps without checking → inside the dispatcher `RequestHeaderMethodArgumentResolver` reads the header → `StrictFirewalledRequest.validateAllowedHeaderValue` throws → MVC's `handleGeneral` (`CommonGlobalExceptionHandler.java:153-157`) swallows it: ERROR log, 500. It never propagates, so the 400 handler never sees it; no `/error` dispatch on this path.
- **Rethrowing is wrong**: for up-front rejections the 400 handler calls `sendError(400)` → `/error` dispatch → admin's `anyRequest().denyAll()` → **401 `TOKEN_INVALID`**. Rethrowing from the catch-all would turn the header case into the same 401. **Map it in the handler.**

## Shapes

| Shape (bare `StrictHttpFirewall`, default) | Result |
|---|---|
| UTF-8 `④` / `테스트` as Tomcat delivers it (ISO-8859-1-decoded) · `\u0001` in a header value or name | rejected when read |
| `%2F` `%2f` `;` `%3B` `//` `/../` `%2E` `%2e` `\` `%5C` `%25` `%00`, non-ASCII path, `TRACE` | rejected up front |
| Real Unicode `④` / `테스트` · `é` as delivered · TAB in a value · `\u0001` in a parameter | pass |

| Real Tomcat + admin's handler | Today |
|---|---|
| Endpoint reads the header, raw `④` | **500 `INTERNAL_ERROR`**, ERROR |
| `POST` + `@RequestBody`, endpoint does not read the header, raw `④` | **500** — the body reader enumerates every header (`ServletServerHttpRequest.getHeaders`) |
| Endpoint never reads the header | 200 |
| `%E2%91%A3` (what the console sends) | 200, value `④` |
| Path `;` `//` `%25` | **401 `TOKEN_INVALID`** — the `/error` dispatch is denied |
| Path `%2F` `%5C` | 400 Tomcat HTML — rejected before Spring |
| Real unexpected exception (control) | 500 |

## Error-code catalog

`platform/error-handling.md` owns the envelope (§ Error Response Format `:30-46`) and the common-code registry (`INTERNAL_ERROR` `:137`). Closest existing code: **`BAD_REQUEST`** (`:98`, «structural constraints not captured by a specific validation code»). No `REQUEST_REJECTED` exists.

## What changes in the scope

1. 🔴 **The red test as first written would pass today** — `header("X-Operator-Reason", "④")` in MockMvc passes the firewall (real Unicode is allowed). The red test must use the decoded form `new String("④".getBytes(UTF_8), ISO_8859_1)` or raw bytes to a real server.
2. The shape is any header whose UTF-8 bytes include 0x80–0x9F (most Korean, `④`) on any endpoint that reads it **or takes a `@RequestBody`** — not only `X-Operator-Reason`.
3. 🔴 **Dependency**: the library has no spring-security-web and several subclassing services don't either — a typed `@ExceptionHandler(RequestRejectedException.class)` would fail to load there. The mapping goes **by class name inside the catch-all** (no new dependency).
4. Up-front (path) rejections answering 401 are a **separate defect** this handler never sees → `TASK-MONO-801`.
5. `%2F` · `%5C` never reach Spring (Tomcat's own 400) — out.

# Owner decisions (2026-10-20 UTC, choice window)

- Code = **`BAD_REQUEST`** (existing). AC-3 adds one line under it in `platform/error-handling.md` (no new code).
- Path-shaped 401 = **separate ticket** `TASK-MONO-801`; this ticket fixes the header case only.
- Fix location (from AC-0): `CommonGlobalExceptionHandler.handleGeneral` recognises `org.springframework.security.web.firewall.RequestRejectedException` by class name → 400 `BAD_REQUEST`, fixed message, one `WARN` line without the value; wms master-service · admin-service forward it (they don't inherit). Not rethrown.
