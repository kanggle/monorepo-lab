# TASK-PC-BE-011 — console-bff notification mark-read swallows every downstream 4xx/5xx into a generic 500 (contract § 4.5 passthrough unenforced)

- **Type**: TASK-PC-BE
- **Status**: done
- **Service**: console-bff (platform-console)
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (BFF error-handling, non-security)

## Goal

The notification-bell **mark-read** BFF leg proxies to the owning domain (contract § 4.5), but any
downstream non-2xx (`RestClient.retrieve()` throws) fell through to the catch-all
`@ExceptionHandler(Exception.class)` → a generic **500 INTERNAL_ERROR** (then **502** at console-web).
The contract requires the producer status to **pass through** — most importantly a producer **404 →
404 `NOTIFICATION_NOT_FOUND`** (mark-read on another recipient's notification, existence-leak-safe,
"passed through inline-actionably"). Map the downstream status faithfully so a should-degrade stops
surfacing as a hard error.

## AC-0 — Finding (audit, verified 2026-07-17)

- **`NotificationAggregationUseCase.markRead`** calls `markReadFor → erpPort.markRead(...)` with **no
  try/catch/classifier** — unlike the GET aggregate/overview legs, which route every leg through
  `CompositionEngine.time(...) + classifyError(...)` and always degrade to a 200 partial. So the GET
  legs never propagate; only mark-read does.
- **`ErpNotificationsReadAdapter.markRead`** = `client.post()...retrieve().body(Map.class)` — no
  `onStatus` override anywhere (grep-clean), so RestClient throws `HttpClientErrorException` /
  `HttpServerErrorException` on any non-2xx.
- **`GlobalExceptionHandler`** had no arm for `HttpStatusCodeException` / `ResourceAccessException`,
  so the mark-read exception fell to `handleGeneric(Exception)` → 500 `INTERNAL_ERROR`. Only the
  `UnknownNotificationDomainException` path 404s (an unknown `sourceDomain`, thrown before any
  outbound); a genuine producer 404/403/503/timeout became a 500.
- **Contract** (`console-integration-contract.md` § 4.5): mark-read on another recipient's
  notification → **404 `NOTIFICATION_NOT_FOUND`** passed through inline-actionably; `403`/`503`/timeout
  degrade the bell (not a hard error). The console-web read route confirms the expectation
  (`…/read/route.ts`: BFF 404 → 404; else → 502).
- **Vacuous**: no test enqueued a downstream 4xx/5xx for mark-read — the integration mark-read tests
  covered only 200 (happy) + unknown-domain 404 (thrown before outbound). The swallow shipped green.
- **Blast radius**: LOW-MED, **non-security** — one idempotent action (bell mark-read). The 404 case is
  unreachable on the own-inbox happy path, but a degraded notification-service (503/timeout) on a bell
  click surfaced as a hard 500→502 instead of the graceful degrade the bell is designed for.

## Scope

- **In**: add `@ExceptionHandler(HttpStatusCodeException.class)` + `@ExceptionHandler(ResourceAccessException.class)`
  to `GlobalExceptionHandler` mapping the propagated producer status (404 → NOTIFICATION_NOT_FOUND,
  401 → TOKEN_INVALID, 403 → PERMISSION_DENIED, else/5xx/timeout → 503 DOWNSTREAM_ERROR); integration
  tests enqueuing a downstream 404/503/403 for mark-read and asserting the passthrough.
- **Out**: the GET aggregate/overview degrade path (already correct — degrades to 200 internally);
  console-web (its read route already maps BFF 404→404 / else→502); the F2 doc-drift on
  `CredentialSelectionAdapter` (a stale "dormant" comment — separate trivial note, not this task).

## Acceptance Criteria

- **AC-1**: A producer **404** on mark-read → BFF **404 `NOTIFICATION_NOT_FOUND`** (contract § 4.5).
- **AC-2**: A producer **503** (or any 5xx / timeout / connect failure) on mark-read → BFF **503
  `DOWNSTREAM_ERROR`** (never a bare 500).
- **AC-3**: A producer **403** → BFF **403 `PERMISSION_DENIED`**; a producer **401** → BFF **401
  `TOKEN_INVALID`**.
- **AC-4**: The GET aggregate/overview legs are unaffected (they never propagate — degrade to 200).
- **AC-5**: Integration tests (WireMock downstream) cover the 404/503/403 mark-read passthrough.
  console-bff test lane green.

## Related Specs / Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 4.5 (mark-read passthrough)

## Edge Cases / Failure Scenarios

- Only mark-read propagates raw RestClient errors; the new handler arms therefore only affect that leg.
- A producer 4xx the console-web layer treats as 502 (non-404) still surfaces as the mapped BFF status
  (console-web maps BFF-non-404 → 502 itself).
