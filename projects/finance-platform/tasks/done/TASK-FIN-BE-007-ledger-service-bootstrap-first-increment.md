# TASK-FIN-BE-007 — ledger-service bootstrap (first increment: event-driven auto-journal + read)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (complex domain — double-entry invariant, event consumer, new Hexagonal service)

---

## Goal

Bootstrap `finance-platform/apps/ledger-service` — the **double-entry general ledger**
ADR-MONO-008 § D3 deferred to v2 — as a **first increment**: an event-driven
auto-journal consumer + a read API that proves the accounting depth (balanced
double-entry journal posted automatically from account-service transaction events).
This is finance-platform's **second service** (account-service is the only existing
one), deepening the thinnest domain with real accounting-domain logic.

Period close, GL/AP-feed emission, reconciliation matching, manual journal posting,
and multi-currency are **forward-declared** (later increments) per
`specs/services/ledger-service/architecture.md` § Increment Scope — mirroring the erp
`read-model-service` / `approval-service` first-increment discipline.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (`rest-api + event-consumer`,
Hexagonal+DDD, the Chart of Accounts + Posting Policy + immutability + dedupe) +
`specs/contracts/http/ledger-api.md` (4 read endpoints) +
`specs/contracts/events/finance-ledger-events.md` (consumed `finance.transaction.{completed,reversed}.v1`;
forward-declared `finance.ledger.*`) + `platform/error-handling.md` (claim the
pre-registered `LEDGER_*` codes + add `JOURNAL_ENTRY_NOT_FOUND` /
`LEDGER_ACCOUNT_NOT_FOUND`) + `PROJECT.md` service-map note.

**Impl PR — IN (the first increment):**
- New deployable `apps/ledger-service/` (Hexagonal, package `com.example.finance.ledger`),
  `@SpringBootApplication` excluding `OutboxAutoConfiguration` (terminal consumer).
- **Domain (pure)**: `Money`/`Currency` (minor-units, no float); `LedgerAccount`
  (code/type/normalSide) + `LedgerAccountType`(ASSET/LIABILITY…) + `NormalSide`;
  `JournalEntry` (aggregate; **balanced invariant `Σdebit==Σcredit`** self-validated →
  `LedgerEntryUnbalancedException`; immutable; `SourceRef`) + `JournalLine` +
  `EntryDirection`; `PostingPolicy.toEntry(txn)` (pure transaction-type → balanced
  lines per the architecture table; HOLD/RELEASE → no entry; reversal swap);
  `AuditLog`.
- **Application**: `PostJournalEntryUseCase` (`@Transactional`: balance-validate →
  persist entry+lines+audit one Tx) + `PostFromTransactionUseCase` (envelope →
  policy → post; idempotent on source event id) + `QueryLedgerUseCase` (entry detail /
  per-account entries+balance / trial balance) + outbound ports
  (`ProcessedEventStore`, `ClockPort`).
- **Messaging (inbound)**: `TransactionEventConsumer` (`@KafkaListener`
  `finance.transaction.{completed,reversed}.v1`, group `finance-ledger-v1`,
  `@RetryableTopic`+DLT, manual ACK, dedupe) + `TransactionEnvelope` (unknown-field
  tolerant) + `EnvelopeToCommandMapper`.
- **Persistence (MySQL `finance_ledger_db`, Flyway V1)**: `ledger_account`,
  `journal_entry`, `journal_line`, `audit_log`, `processed_events` + JPA
  entities/adapters. `@JdbcTypeCode(VARCHAR)` per `@Enumerated` (finance precedent).
- **Read REST**: `LedgerController` (the 4 `ledger-api.md` endpoints) +
  `GlobalExceptionHandler` (fintech error envelope) + DTOs (money as
  minor-units string).
- **Security/tenant**: RS256 JWKS resource-server + dual-accept tenant gate
  (`AllowedIssuersValidator`/`TenantClaimValidator`/`TenantClaimEnforcer`, mirror
  account-service) + `PublicPaths` (actuator only).
- **Deploy wiring (atomic)**: `settings.gradle` include `apps/ledger-service`;
  `build.gradle`; finance `docker-compose.yml` (`ledger.local` route + `finance_ledger_db`)
  ; `.github/workflows/ci.yml` finance `:check` + `:integrationTest` (or the finance
  Integration job) covers ledger-service.
- **Tests**: domain unit (`JournalEntryTest` balanced/unbalanced/immutable,
  `PostingPolicyTest` each type, `MoneyTest`, `LedgerAccountTest`); application unit
  (`PostFromTransactionUseCaseTest` mapping+dedupe, mock ports); `@WebMvcTest`
  `LedgerController` slice + error envelope; Testcontainers Integration (MySQL + real
  Kafka + WireMock JWKS): produce `transaction.completed.v1` → balanced entry + trial
  balance 0; re-deliver → dedupe (one entry); TRANSFER → two wallet lines; `reversed.v1`
  → reversal entry, trial balance still 0; cross-tenant read → 403; HOLD completed → no entry.

**Impl PR — OUT (forward-declared, later tasks):** period close + `finance.ledger.period.closed.v1`;
GL/AP-feed `finance.ledger.entry.posted.v1` emission (gains an outbox then); reconciliation
matching; manual journal posting API; multi-currency consolidation.

## Acceptance Criteria

- **AC-1 (auto-journal)** — a `finance.transaction.completed.v1` (CAPTURE / TRANSFER /
  TOPUP) yields exactly one **balanced** journal entry per the Posting Policy; a HOLD /
  RELEASE completed event yields **no** entry.
- **AC-2 (balanced invariant)** — `JournalEntry` rejects an unbalanced line set
  (`LEDGER_ENTRY_UNBALANCED`); the trial balance (`GET /trial-balance`) is always in
  balance (Σdebit == Σcredit). A unit test proves the domain rejection; an Integration
  test proves the live trial balance == 0 after postings.
- **AC-3 (idempotent / immutable)** — a re-delivered event (same `eventId`) posts at
  most one entry (dedupe via `processed_events`); a `reversed.v1` posts a REVERSAL entry
  referencing the original (debit/credit swapped), leaving the original entry unmutated
  and the trial balance at 0.
- **AC-4 (read + tenant)** — the 4 read endpoints return the `ledger-api.md` shapes;
  cross-tenant JWT (dual-accept both branches fail) → 403 `TENANT_FORBIDDEN`; unknown
  entry/account → 404.
- **AC-5 (boundary)** — ledger-service does NOT write to `finance_db` / mutate
  account-service state (grep-zero); terminal consumer (no outbox / publish, grep-zero
  `KafkaTemplate` in the posting path); no `float`/`double` in `domain/money` (grep-zero).
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN** (the authoritative behavioural gate — real
  Kafka consume→post→read end-to-end). Deploy wiring (`settings.gradle`/compose/ci.yml)
  lands atomically.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing)
- `projects/finance-platform/specs/services/account-service/architecture.md` (the blueprint mirrored)
- `projects/erp-platform/specs/services/read-model-service/architecture.md` (the dual-type terminal-consumer precedent)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (this PR)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR)
- `projects/finance-platform/specs/contracts/events/finance-account-events.md` (the consumed transaction events)
- `platform/error-handling.md` (ledger codes claimed this PR)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D3 (the v2 ledger deferral being executed)

## Edge Cases

- **Transaction type without a balance effect** — HOLD / RELEASE completed events post
  no entry (documented, not silently dropped — the consumer recognises and ACKs them).
- **Per-account ordering** — partition key `accountId`; a `reversed.v1` arriving before
  its original `completed.v1` → DLT (real anomaly, not swallowed).
- **`{ledgerAccountCode}` colon** — `CUSTOMER_WALLET:{accountId}` contains a colon →
  URL-encoded in read paths; the controller decodes.
- **Lazy wallet account** — a `CUSTOMER_WALLET:{accountId}` ledger account is created on
  first posting for that account (no pre-seed of every customer); platform GL accounts
  (`CASH_CLEARING`, `SETTLEMENT_SUSPENSE`) are seeded.
- **Single-currency entry** — cross-currency lines in one entry → `CURRENCY_MISMATCH`
  (multi-currency consolidation deferred).
- **`@JdbcTypeCode(VARCHAR)` per `@Enumerated`** + `@JdbcTypeCode(JSON)` if any JSON
  column (finance/erp precedent — avoids the enum-storage + JSON pitfalls).

## Failure Scenarios

- **F1 — unbalanced entry persisted** — would corrupt the books. Guarded by the domain
  self-validation (AC-2) + the trial-balance Integration assertion.
- **F2 — double-posting on redelivery** — at-least-once Kafka would double-count.
  Guarded by `processed_events` dedupe in the posting Tx (AC-3).
- **F3 — writing back to account-service** — would break the downstream-derivation
  boundary. Guarded by AC-5 (separate schema, grep-zero `finance_db` access).
- **F4 — Docker-free `:check` passing but the consumer path broken** — the unit/slice
  tests don't exercise real Kafka consume→post; the Testcontainers Integration job is
  the authoritative gate (AC-6), per the finance/erp `§14` pattern.
- **F5 — green-wash on a new service with no CI Integration wiring** — the impl PR MUST
  wire ledger-service into the finance Integration CI job (account-service/erp precedent:
  a new service's IT must run in CI, not just compile).
