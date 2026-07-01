# Task ID

TASK-BE-462

# Title

Correct `auth-service` account-status error HTTP statuses to match the platform `error-handling.md` contract — `ACCOUNT_LOCKED`/`ACCOUNT_DORMANT` → `423`, `ACCOUNT_DELETED` → `410`, `ACCOUNT_STATUS_UNKNOWN` → `500` (all currently return `403`)

# Status

review

# Owner

backend

# Task Tags

- code
- bug
- spec-reconcile

---

# Goal

`platform/error-handling.md` (§ Account) is the authoritative error-code → HTTP-status
contract. It maps:

| Code | HTTP (spec) |
|---|---|
| `ACCOUNT_LOCKED` | `423` |
| `ACCOUNT_DORMANT` | `423` |
| `ACCOUNT_DELETED` | `410` |
| `ACCOUNT_STATUS_UNKNOWN` | `500` |

The IAM `auth-service` handler does **not** honour this. In
`AuthExceptionHandler`:

- `AccountLockedException` → `HttpStatus.FORBIDDEN` (`403`) — spec says `423`
  ([`AuthExceptionHandler.java:82-86`](../../apps/auth-service/src/main/java/com/example/auth/presentation/exception/AuthExceptionHandler.java)).
- `AccountStatusException` → blanket `HttpStatus.FORBIDDEN` (`403`) for **every**
  carried code — but `AccountStatusException` is thrown with `ACCOUNT_DORMANT`
  (spec `423`), `ACCOUNT_DELETED` (spec `410`) **and** `ACCOUNT_STATUS_UNKNOWN`
  (spec `500`) (`LoginUseCase.checkAccountStatus`, `OAuthLoginTransactionalStep`,
  `SocialIdentityPersistStep`). So three distinct codes are all mis-mapped to `403`.

`platform/error-handling.md:470,472` already carries an explicit `**TODO**: IAM
auth-service handler currently returns 403; correct to 423 in a follow-up code-side
fix`. This task is that follow-up, extended to cover the full `AccountStatusException`
code set (not just DORMANT) since the same blanket-`403` handler mis-maps `DELETED`
and `STATUS_UNKNOWN` too.

This is spec-code drift only — no contract or wire-format field changes; only the
HTTP status line changes to what the contract already specifies. The fix lives in the
shared `AuthExceptionHandler` and is therefore **independent of the deprecated
`LoginController`** (scheduled for removal by TASK-BE-398 on 2026-08-01) — the SAS
browser flow and the social-login flow share the same handler.

# Scope

## In Scope

- `AuthExceptionHandler.handleAccountLocked` → return `423 LOCKED` for
  `ACCOUNT_LOCKED`.
- `AuthExceptionHandler.handleAccountStatus` → map **per carried error code**
  instead of blanket `403`: `ACCOUNT_DORMANT` → `423`, `ACCOUNT_DELETED` → `410`,
  `ACCOUNT_STATUS_UNKNOWN` → `500`. Any unrecognised code → `500` (defensive, matches
  the `ACCOUNT_STATUS_UNKNOWN` intent).
- Update every test that currently asserts `403` for these codes to assert the
  contract status (`AuthIntegrationTest`, `LoginControllerTest`, and any social-login
  browser test asserting an account-status status line).
- Remove the two `**TODO**` notes from `platform/error-handling.md:470,472` (the
  code now matches the contract).

## Out of Scope

- Any error code **not** in the account-status set above (rate-limit, token, credential
  errors keep their current statuses).
- The `ErrorResponse` body shape / `code` values (unchanged — only the HTTP status line
  changes).
- Removing/altering the deprecated `LoginController` itself (that is TASK-BE-398,
  date-gated to 2026-08-01). This task only corrects the shared handler's status codes;
  `LoginController` tests are updated only where they assert one of the affected codes.

---

# Acceptance Criteria

- [ ] **AC-1** — `ACCOUNT_LOCKED` returns `423 LOCKED` (was `403`). `code` body unchanged.
- [ ] **AC-2** — `ACCOUNT_DORMANT` returns `423 LOCKED` (was `403`).
- [ ] **AC-3** — `ACCOUNT_DELETED` returns `410 GONE` (was `403`).
- [ ] **AC-4** — `ACCOUNT_STATUS_UNKNOWN` (and any unrecognised status) returns `500`
  (was `403`).
- [ ] **AC-5** — All `auth-service` unit + slice tests GREEN; every prior `403`
  assertion for the above codes updated to the new status; `skipped` count unchanged.
- [ ] **AC-6** — `platform/error-handling.md` §Account no longer carries the two
  `TODO: ... returns 403` notes (code now matches the `423`/`410`/`500` contract rows);
  the table rows themselves are unchanged.
- [ ] **AC-7** — No other error-code mapping changes; grep confirms no stray `FORBIDDEN`
  removal outside the two account-status handlers.

---

# Related Specs

- `platform/error-handling.md` § Account (authoritative code → HTTP-status table; source
  of the drift + the two TODO notes removed by AC-6).

# Related Contracts

- 없음 (no API/event contract field change; only the HTTP status line is corrected to the
  value the contract already specifies).

---

# Target Service

- `auth-service` (iam-platform)

---

# Edge Cases

- `AccountStatusException` is generic and carries the error code at runtime; the handler
  must switch on `getErrorCode()` rather than apply a single blanket status. An
  unrecognised/future code must fall through to `500` (not `423`/`410`), matching the
  `ACCOUNT_STATUS_UNKNOWN` defensive-guard intent.
- `423 LOCKED` (`HttpStatus.LOCKED`, WebDAV) and `410 GONE` are standard Spring
  `HttpStatus` values — no custom status construction needed.
- Both the SAS browser login flow and the deprecated `LoginController` route through the
  same handler; both must observe the corrected status (verified by the respective tests).

# Failure Scenarios

- **F1 — blanket remap** — mapping all `AccountStatusException` codes to `423` would make
  `ACCOUNT_DELETED` (`410`) and `ACCOUNT_STATUS_UNKNOWN` (`500`) wrong. Guarded by
  AC-3/AC-4 (per-code mapping).
- **F2 — spec drift persists** — fixing the code but leaving the `TODO` notes in
  `error-handling.md` re-creates drift. Guarded by AC-6.
- **F3 — stale test masks regression** — a test still asserting `403` would fail the
  corrected build; all such assertions must be updated (AC-5).

---

# Test Requirements

- Unit/slice assertions updated to the corrected statuses (`423`/`410`/`500`).
- No new integration lane required (pure status-line change on existing paths); existing
  `auth-service` suites must stay GREEN.

---

# Definition of Done

- [ ] AC-1…AC-7 satisfied
- [ ] `platform/error-handling.md` TODO notes removed (shared change → verify draft PR on push)
- [ ] Ready for review

---

# Notes on Task Placement

The dominant change (handler + tests) is project-internal to `iam-platform/auth-service`;
the only shared-path edit is removing two `TODO` sentences from `platform/error-handling.md`
that point **at this exact code**. Filed as an iam-platform project task with the doc
reconcile bundled (the atomic code+doc reconcile is cleaner than splitting a two-sentence
TODO removal into a separate root task). The shared edit is a pure TODO deletion — it adds
no project-specific content to the shared file, so no HARDSTOP-03 concern.
