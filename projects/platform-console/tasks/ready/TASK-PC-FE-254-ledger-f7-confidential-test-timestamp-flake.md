# TASK-PC-FE-254 — console-web: ledger-api F7 confidential test flakes on a timestamp-substring false positive

Status: ready

`(분석=Opus 4.8 / 구현 권장=Sonnet — test-only flake fix, 프로덕션 무변경)`

---

## Goal

Fix a time-dependent flake in `console-web`'s `ledger-api.test.ts` F7
confidential test that randomly REDs the shared `Frontend unit tests` CI job on
**unrelated** PRs. Surfaced 2026-07-22 on `TASK-BE-555` (a backend-only gateway
PR): the `Frontend unit tests` job failed with 1/274 files, cleared on rerun.

## Re-measured evidence (line numbers = hypotheses, re-verify at start)

`projects/platform-console/apps/console-web/tests/unit/ledger-api.test.ts`,
test `"logs neither the token nor the response body (success path)"`
(§ 2.4.7.1 F7, ≈ line 541):

- The test stubs a journal-entry response whose confidential fields include
  `exchangeRate: '13.5'` (≈ 550), calls `getJournalEntry`, concatenates every
  captured `console.{log,info,warn,error}` call into a single string `all`
  (≈ 568–575), and asserts `expect(all).not.toContain('13.5')` (≈ 579) — plus
  the token, entry id, `182250`, and account code.
- **The production log is correct** — the `ledger_ok` success line carries only
  `{ts, level, msg, requestId, status, path}`, never the response body. So there
  is no real leak.
- **The assertion is too broad:** `all` includes the log line's ISO-8601
  timestamp `"ts":"…T00:01:13.594Z"`, and `13.594` contains the substring
  `13.5`. When the wall clock lands on a moment whose `SS.mmm` spells `13.5`
  (or any coincidence of the confidential digits inside the timestamp), the
  assertion fails — deterministically at that instant, invisibly otherwise.
- **Fingerprint:** `AssertionError: expected '{"ts":"…:13.594Z",…}' not to
  contain '13.5'`, `1 failed | 273 passed`. The assertion message itself names
  the root cause (timestamp), so this is a confirmed test bug, not infra flake.

## Scope

**In:** make the F7 confidential-substring assertions robust against the
non-confidential ISO-8601 timestamp (a test-only change).
**Out:** the production `ledger-client` logging (correct — no body logged); the
other F7 assertions' *intent* (they must still catch a real leak); any other
ledger/finance test.

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that (a) the F7 test asserts
  `not.toContain('13.5')` (and the other confidential values) against a string
  that includes the log `ts`, and (b) the production `ledger_ok` log carries no
  response body (only metadata) — i.e. the failure is a timestamp coincidence,
  not a real leak. Re-verify file:line — code wins.
- **AC-1:** redact ISO-8601 timestamps from the captured log string before the
  confidential-substring assertions (e.g. replace
  `/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z/g` with a placeholder), so a
  timestamp can never satisfy a confidential substring. Keep all existing
  confidential assertions.
- **AC-2 (protection preserved):** the test must **still fail** if the log
  genuinely leaks a confidential value in a non-timestamp field — verify by a
  throwaway mutation (temporarily log the body → RED), then revert. A real leak
  of `13.5`/token/id/code is not inside a timestamp, so redaction does not mask
  it.
- **AC-3:** `pnpm --filter console-web test` (vitest) green locally
  (Docker-free); the fix is deterministic (no longer clock-dependent).

## Related Specs

- `projects/platform-console/docs/conventions/frontend-ui.md` — console-web
  conventions (datetime/logging live here; F7 confidentiality per § 2.4.7.1 of
  the ledger contract this test guards).

## Related Contracts

- None (test-only; no API/event contract change).

## Edge Cases

- Redact **only** the ISO-8601 timestamp shape — do not strip arbitrary digits,
  or a real numeric leak (`13.5`, `182250`) would be masked (AC-2 guards this).
- Other confidential assertions (`GAP-OIDC-ACCESS`, `je-secret-id`,
  `LEDGER-ACCT-CODE-SECRET`) cannot collide with a timestamp, but running them
  against the redacted string is harmless and keeps one code path.
- The same shared request-logger backs `erp-api`/`finance-api`/etc.; if any of
  those has an identical F7 confidential test with a numeric value that can
  collide with a timestamp, note it (sibling parity) — but do not widen this
  ticket unless a second flake is actually observed.

## Failure Scenarios

- **Pin a fixed fake timestamp instead of redacting:** brittle (must dodge every
  confidential digit sequence) and collides with the module-level fake-timer
  teardown flake already documented for this suite — redaction is the robust fix.
- **Change the confidential fixture value to dodge `13.5`:** hides this instance
  but leaves the assertion structurally able to false-positive on the next value
  or a future timestamp; fix the assertion, not the fixture.
- **Delete the `13.5` assertion:** removes real F7 leak protection — the point is
  to keep the protection while removing the timestamp false positive.
