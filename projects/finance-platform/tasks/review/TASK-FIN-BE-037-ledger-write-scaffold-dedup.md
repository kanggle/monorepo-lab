# TASK-FIN-BE-037 — ledger-service write use-case scaffold dedup (refactor)

- **Status:** review
- **Type:** refactor (behaviour-preserving — no contract / no API / no decision change)
- **Service:** ledger-service (finance-platform)
- **Increment:** N/A — pure internal refactor; does not advance the increment count

## Goal

Remove the byte-for-byte duplicated idempotency/replay scaffold shared across the three
operator-facing journal **write** use cases — `PostManualJournalEntryUseCase` (5th incr),
`RevalueForeignBalanceUseCase` (9th incr), `SettleForeignPositionUseCase` (10th incr) — by
extracting it into one package-private static helper. Behaviour, exceptions, messages, and
the 50-char idempotency-key limit stay **identical**. This is the code-side parallel of the
just-completed meaning-preserving spec readability refactor (FIN-BE-035).

## Scope

Each of the three use cases currently repeats, verbatim:

1. **Idempotency-key validation** — null/blank → `IdempotencyKeyRequiredException("Idempotency-Key header is required")`; `length > 50` → `IdempotencyKeyRequiredException("Idempotency-Key must be at most 50 characters")`. (Each declares its own `MAX_KEY_LENGTH = 50`.)
2. **Replay lookup** — `journalRepository.findBySourceEventId(dedupeKey, tenantId).orElseThrow(() -> new JournalEntryNotFoundException("<label> entry for idempotency key not found (replay): " + dedupeKey))` (only the `<label>` differs: `manual` / `revaluation` / `settlement`).
3. **Audit-reason fallback chain** — `memo` (non-blank) → `reference` (non-blank) → a per-use-case default string (`"manual adjusting entry"` / `"FX revaluation"` / `"FX settlement"`).
4. **Entry-id minting** — `UUID.randomUUID().toString()`.

IN:

- **New** `application/LedgerWriteSupport.java` — package-private `final` utility (no Spring bean, no state): `MAX_IDEMPOTENCY_KEY_LENGTH = 50`, `validateIdempotencyKey(String)`, `requireReplayEntry(JournalRepository, String dedupeKey, String tenantId, String entryLabel)`, `auditReason(String memo, String reference, String fallback)`, `newEntryId()`.
- **Edit** the three use cases to call the helper; delete the now-redundant per-class `MAX_KEY_LENGTH` constant, private `reason(...)` method, and private `newEntryId()` method. Keep each use case's own `DEDUPE_PREFIX` / `DEDUPE_TOPIC` (genuinely per-use-case) and all FX/journal math untouched.
- **New** `application/LedgerWriteSupportTest.java` — unit coverage for the four helpers (validate ok/null/blank/too-long; auditReason memo→reference→fallback precedence + blank handling; requireReplayEntry found/not-found; newEntryId non-blank/distinct).
- **Edit** `specs/services/ledger-service/architecture.md` Layer Structure tree — add one line for `LedgerWriteSupport.java` so the tree stays consistent with code (drift 0, per FIN-BE-034 reconciliation). Documentation reflection of the refactor only — no new decision.

OUT (explicitly not touched — behaviour/contract frozen):

- Any FX cost-flow / FIFO walk / mark-to-spot / settlement / revaluation arithmetic.
- `DEDUPE_PREFIX` / `DEDUPE_TOPIC` values, namespacing, or the `processed_events` dedupe semantics.
- Controllers, DTOs, contracts (`ledger-api.md`), events, migrations.
- The event-driven consumer paths (`PostFromTransactionUseCase`, `IngestStatementUseCase`) — they key on event id, not a client `Idempotency-Key`; different shape, out of scope.
- Other duplication candidates (Get/Set `FxCostFlowConfig` use-case pairs, controller response-mapping patterns) — reported as deferred findings, not done here.

## Acceptance Criteria

- AC-1: `LedgerWriteSupport` exists with the five members above; the three use cases delegate to it; their previous private `reason`/`newEntryId` methods and `MAX_KEY_LENGTH` fields are gone.
- AC-2: No behavioural change — same exception types, same messages (validation + replay-not-found are byte-identical; the replay label composes to the exact prior strings), same 50-char limit, same audit-reason precedence and default strings.
- AC-3: `:ledger-service:compileJava` + `:ledger-service:test` (the Docker-free unit/slice tests) pass locally; `LedgerWriteSupportTest` is green.
- AC-4: architecture.md Layer tree lists `LedgerWriteSupport.java`; dead-ref / self-contradiction grep stays clean (no new drift introduced).
- AC-5: CI green on push (the Testcontainers IT suite — `LedgerManualPostingIntegrationTest`, `LedgerFxRevaluationIntegrationTest`, `LedgerFxSettlementIntegrationTest`, and siblings — is the authoritative behaviour gate, since Testcontainers IT cannot run on this Windows host).

## Related Specs

- `specs/services/ledger-service/architecture.md` (§ Manual Journal Posting, § FX gain/loss revaluation, § FX settlement; Layer Structure tree)

## Related Contracts

- `specs/contracts/http/ledger-api.md` — **unchanged** (no endpoint, request, response, or status-code change). Listed only to assert the contract is unaffected.

## Edge Cases

- A use case with a different default reason string must keep its own string (passed as the `fallback` arg) — the helper must not hard-code one default.
- The replay-not-found message label differs per use case (`manual`/`revaluation`/`settlement`) — passed as `entryLabel`; the helper composes the exact prior sentence.
- `MAX_IDEMPOTENCY_KEY_LENGTH` must remain 50 (the `"<prefix>:" + key` longest form, `settle:`/`manual:` = 7 + 50 = 57, fits the 64-char `processed_events` key column).

## Failure Scenarios

- If extraction changed any message or exception type, the controller→error-code mapping (`GlobalExceptionHandler`) and the IT assertions would break → caught by CI (AC-5). Mitigation: byte-compare the composed strings against the originals before commit.
- If the helper were made a Spring bean, it would add a needless component and an injection point → kept as a stateless static `final` class with a private constructor.
