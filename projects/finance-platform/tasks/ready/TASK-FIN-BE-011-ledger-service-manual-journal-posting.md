# TASK-FIN-BE-011 — ledger-service manual journal posting (5th increment: operator adjusting-entry REST endpoint funnelling the existing guarded write path)

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (complex domain — first journal mutation surface, idempotency on the REST path, operator-audit actor, reuse of the guarded write path without a second write boundary)

---

## Goal

Deliver the **manual journal posting** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope (and foreseen by
Architecture Style Rationale point 3 — "a future manual-posting REST endpoint …
reuses the same command path"). An operator posts an **adjusting entry** (a
correction / accrual / write-off the transaction event stream cannot express)
directly via `POST /api/finance/ledger/entries`. Until now journal entries were
posted **only** by the auto-journal consumer; this is the **first journal mutation
REST surface**.

The manual path adds **no new write boundary**: it builds a balanced `JournalEntry`
and funnels it through the existing **`PostJournalEntryUseCase.post`** (the single
guarded write path), inheriting the balance identity, the closed-period guard, the
audit row, and the `entry.posted` outbox append unchanged. A manual entry carries
`SourceRef` type **`MANUAL`**, is **immutable + reversal-only** (F3), and is
**idempotent** on a client `Idempotency-Key` (reuses the `processed_events` dedupe;
replay returns the original entry). It emits the **same**
`finance.ledger.entry.posted.v1` with `source.sourceType = "MANUAL"` (no new event).

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Increment Scope moves manual
journal posting IN as the 5th increment; new § Manual Journal Posting — endpoint,
`PostManualJournalEntryUseCase` flow, no-lazy-mint decision, idempotency, immutability,
emission; § REST endpoints + § Failure Modes [rows 21–24] + § Layer Structure
+ § Testing + fintech F1/F2/F3 mapping) + `specs/contracts/http/ledger-api.md` (§ 9
`POST /entries` + error codes surfaced synchronously + `Idempotency-Key`; removed from
Out-of-scope) + `specs/contracts/events/finance-ledger-events.md` (`entry.posted.v1`
`sourceType: "TRANSACTION"|"MANUAL"` + a MANUAL provenance note) + `platform/error-handling.md`
(5th increment in the ledger list; LEDGER_ENTRY_UNBALANCED / LEDGER_ACCOUNT_NOT_FOUND
/ LEDGER_PERIOD_CLOSED notes — now synchronous on the manual path).

**Impl PR — IN (manual posting increment):**
- **Domain** — `domain/journal`:
  - `SourceRef.ofManual(reference, sourceEventId)` + `TYPE_MANUAL = "MANUAL"`
    constant (the embeddable already carries `sourceType` / `sourceTransactionId` /
    `sourceEventId`; for a manual entry `sourceTransactionId` = the operator
    `reference` [nullable-tolerant: fall back to the entryId or a constant when the
    operator supplies none, since the column is NOT NULL], `sourceEventId` =
    `manual:{Idempotency-Key}`).
  - `JournalRepository.findBySourceEventId(sourceEventId, tenantId)` →
    `Optional<JournalEntry>` (the manual idempotent-replay return) + its JPA adapter
    query.
- **Application**:
  - `PostManualJournalEntryUseCase` (`@Transactional`): (1) **idempotency** — require
    the key; `processedEventStore.isProcessed("manual:{key}")` → if processed, return
    `journalRepository.findBySourceEventId("manual:{key}", tenant)` (replay, no
    re-post); else continue. (2) **account existence** — each line's
    `ledgerAccountCode` checked via `ledgerAccountRepository.existsByCode` →
    `LedgerAccountNotFoundException` if absent (**no lazy mint**). (3) **build** the
    `JournalLine`s + `JournalEntry.post(entryId, tenant, postedAt,
    SourceRef.ofManual(reference, "manual:{key}"), lines)` (factory self-validates
    balance → `LEDGER_ENTRY_UNBALANCED` / `CURRENCY_MISMATCH`). (4)
    `processedEventStore.markProcessed("manual:{key}", …)` + `postJournalEntryUseCase.post(entry,
    reason, operatorSubject)` in the SAME Tx (the unique constraint on
    `processed_events` makes a concurrent double-submit race-safe).
  - **`PostJournalEntryUseCase.post(entry, reason, actor)` overload** — the operator
    subject becomes the audit actor; the existing `post(entry, reason)` delegates with
    the `finance-ledger-service` default (auto-journal path **byte-identical** —
    net-zero).
- **Presentation**: `JournalController` —
  `POST /api/finance/ledger/entries` (`@RequestHeader("Idempotency-Key")` → a missing
  header maps to `IDEMPOTENCY_KEY_REQUIRED` 400; `ManualJournalEntryRequest` DTO:
  `postedAt?`, `reference?`, `memo?`, `lines[]` of
  `{ ledgerAccountCode, direction, money {amount, currency} }`) — `201` the posted
  entry (§ 1 shape, `sourceType=MANUAL`), `200` on idempotent replay. NO
  `@Transactional`; `.authenticated()` + the dual-accept tenant gate (parity with the
  period/reconciliation mutations). `GlobalExceptionHandler` maps
  `LedgerEntryUnbalancedException` → 422, `CurrencyMismatchException` → 422,
  `LedgerAccountNotFoundException` → 404, `LedgerPeriodClosedException` → **422**
  (now also synchronous), `MissingRequestHeaderException` (Idempotency-Key) → 400
  `IDEMPOTENCY_KEY_REQUIRED`.
- **Security**: add `POST /api/finance/ledger/entries` to the authenticated write
  matchers (parity with `/periods`); no new scope.
- **Tests** (unit + slice + Integration): `PostManualJournalEntryUseCaseTest`
  (balanced persist + emit `sourceType=MANUAL`; unbalanced → `LEDGER_ENTRY_UNBALANCED`;
  unknown account → `LEDGER_ACCOUNT_NOT_FOUND` no lazy mint; back-dated CLOSED →
  `LEDGER_PERIOD_CLOSED`; replay returns the original — no second post; operator actor
  recorded); `@WebMvcTest JournalControllerSliceTest` (201 happy, 400 missing key,
  error envelopes); **Integration** (Testcontainers + real Kafka, authoritative) —
  see Acceptance Criteria AC-6.

**Impl PR — OUT (still forward-declared):** manual-posting body-hash idempotency
**conflict** (`IDEMPOTENCY_KEY_CONFLICT` 409 on same-key/different-body — this
increment is replay-safe on the key alone); a maker/checker **approval** workflow for
manual entries; bulk multi-entry posting; multi-currency journals; reconciliation
period-lock; a console manual-posting view.

## Acceptance Criteria

- **AC-1 (manual posting → guarded write path)** — `POST /api/finance/ledger/entries`
  with a balanced operator entry funnels through `PostJournalEntryUseCase.post` and
  persists a `JournalEntry` (+ lines + audit + `entry.posted` outbox row) carrying
  `SourceRef` type `MANUAL`; the response is `201` with the § 1 entry shape
  (`source.sourceType = "MANUAL"`). The trial balance stays `== 0`.
- **AC-2 (balance + currency, synchronous)** — an unbalanced body (`Σ debit ≠ Σ
  credit`, or <2 lines) → `422 LEDGER_ENTRY_UNBALANCED`; cross-currency lines → `422
  CURRENCY_MISMATCH`. Nothing persists (the `JournalEntry` factory rejects before any
  write). A unit test proves the rejection.
- **AC-3 (no lazy mint)** — a line referencing a ledger account that does not exist →
  `404 LEDGER_ACCOUNT_NOT_FOUND`; the manual path does **not** create the account
  (unlike the auto-journal consumer). A test proves no account row is created on a
  rejected manual posting.
- **AC-4 (idempotency, F1)** — a missing `Idempotency-Key` → `400
  IDEMPOTENCY_KEY_REQUIRED`; replaying the SAME key returns `200` with the **original**
  entry (the `processed_events` dedupe — exactly **one** entry exists for the key). An
  Integration assertion proves a second POST with the same key creates no second entry.
- **AC-5 (closed-period guard, synchronous + audit actor)** — a manual entry whose
  `postedAt` falls in a CLOSED accounting period → `422 LEDGER_PERIOD_CLOSED`
  (synchronous, not the consumer DLT route); a successful manual posting records the
  **operator subject** as the audit actor (not `finance-ledger-service`). The
  auto-journal path's audit actor is unchanged (net-zero). Cross-tenant JWT → `403
  TENANT_FORBIDDEN`. No write-back to `finance_db`.
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN**: `POST /entries` (balanced DR
  `CASH_CLEARING` / CR `CUSTOMER_WALLET:{acct}`, accounts pre-existing) → 201 → entry
  + lines persist, trial balance == 0, **`finance.ledger.entry.posted.v1` with
  `source.sourceType=MANUAL`** consumed off Kafka; replay same key → 200 same entryId
  (one entry); unbalanced → 422; back-dated into a closed window → 422; cross-tenant
  → 403. No new outbox infra (reuses FIN-BE-009; `OutboxAutoConfiguration` still
  excluded); no deploy-wiring change.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Manual Journal Posting)
- `rules/domains/fintech.md` § F1 (idempotency), § F2 (double-entry balance), § F3 (immutability) — governing
- `rules/traits/transactional.md` § T1 (idempotency-key on a mutating endpoint)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR — § 9 `POST /entries`)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — `entry.posted.v1` `sourceType` MANUAL note)
- `platform/error-handling.md` (this PR — 5th increment; ledger code notes; reuses Platform-Common `IDEMPOTENCY_KEY_REQUIRED` 400)

## Edge Cases

- **No new write boundary** — the manual path reuses `PostJournalEntryUseCase.post`;
  it does not open a second `@Transactional` write site (T4 — one guarded write path).
- **No lazy mint** — the auto-journal consumer creates a `CUSTOMER_WALLET:{acct}` on
  first posting; the operator path rejects an unknown account (`LEDGER_ACCOUNT_NOT_FOUND`)
  rather than minting a chart node by posting to it.
- **Back-dated `postedAt`** — adjusting entries are commonly back-dated; the
  closed-period guard protects a CLOSED month from a back-dated manual posting
  (`LEDGER_PERIOD_CLOSED`, synchronous). An open / undefined period proceeds (net-zero).
- **Idempotent replay** — a re-submit with the same key returns the original entry
  (200), never a second post; namespaced `manual:{key}` so a manual key cannot collide
  with a transaction `eventId` in `processed_events`.
- **Operator narrative** — `reference` / `memo` are optional; recorded as the audit
  reason + `source.sourceTransactionId` (the column is NOT NULL — fall back to the
  entryId / a constant when absent).
- **Money** — minor-units integer (string in JSON) + currency; never float (F5). No
  new regulated PII.

## Failure Scenarios

- **F1 — a second write boundary drifts from the guarded path** — would bypass the
  closed-period guard / audit / outbox. Guarded by funnelling through
  `PostJournalEntryUseCase.post` (AC-1); no `@Transactional` on the controller.
- **F2 — an unbalanced operator entry persists** — would break the double-entry
  invariant. Guarded by the `JournalEntry` factory self-validation BEFORE any write
  (AC-2); the trial balance can never go non-zero.
- **F3 — double-submit posts twice (lost idempotency)** — would duplicate an
  adjusting entry (money movement). Guarded by the `processed_events` dedupe in the
  posting Tx (unique constraint) + the replay return (AC-4); an Integration assertion
  proves one entry per key.
- **F4 — operator mints arbitrary GL accounts** — would pollute the chart / mask
  typos. Guarded by the account-existence check (`LEDGER_ACCOUNT_NOT_FOUND`, AC-3).
- **F5 — auto-journal audit actor regresses** — the `post(entry, reason, actor)`
  overload must keep the no-actor path's `finance-ledger-service` default. Guarded by
  the existing consumer Integration (net-zero) + a unit test on both overloads.
- **F6 — Docker-free `:check` passes but the REST/emit path is broken** — the unit
  tests don't exercise the real Kafka emit or the HTTP round-trip; the Testcontainers
  Integration job is the authoritative gate (AC-6).
