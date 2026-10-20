# TASK-MONO-800 — a request the servlet firewall rejects is answered 500 `INTERNAL_ERROR` instead of 400

- **Status**: ready
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

- [ ] **AC-0** — the reach (services × handler), the landing point of the exception, and the shapes, recorded with file:line.
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
