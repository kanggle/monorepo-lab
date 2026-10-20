# TASK-MONO-801 — a request whose path the servlet firewall rejects up front is answered 401 «Authentication required» instead of 400

- **Status**: ready
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

- [ ] **AC-0** — the per-service table (security chain · `/error` treatment · today's status for `;` · `//` · `%25`), the gateway behaviour, and the chosen fix with its reason, recorded with file:line.
- [ ] **AC-1** — an up-front firewall rejection → 400 `BAD_REQUEST` in the common envelope, no echo of the path, one `WARN` line, no `ERROR`; through the real filter chain.
- [ ] **AC-2** — red → green tests for at least two shapes; controls: an unauthenticated normal request still 401, an authorised normal request unchanged, a real unexpected exception still 500.
- [ ] **AC-3** — `platform/error-handling.md` note under `BAD_REQUEST` covers the path case too (one line with `TASK-MONO-800`'s).
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
