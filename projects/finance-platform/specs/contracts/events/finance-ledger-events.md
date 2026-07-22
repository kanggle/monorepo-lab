# Event Contract — finance-ledger-events

Kafka events **consumed** and (forward-declared) **published** by
`finance-platform/apps/ledger-service`. Authored by TASK-FIN-BE-007 **before**
implementation. Architecture:
[`../../services/ledger-service/architecture.md`](../../services/ledger-service/architecture.md).
Prefix `finance` (`rules/domains/fintech.md` § Internal Event Catalog).

## Consumed (account-service outbox → ledger auto-journal)

ledger-service subscribes the existing account-service transaction events
(`finance-account-events.md`) — the v2 forward interface that contract already
named. Consumer group `finance-ledger-v1`; `@RetryableTopic` (3 retries → DLT);
manual ACK; **dedupe on `eventId`** (at-least-once delivery); per-`accountId`
ordering (partition key).

| Topic | Trigger | ledger action |
|---|---|---|
| `finance.transaction.completed.v1` | account-service transaction COMPLETED | post the balanced journal entry per the **Posting Policy** (architecture.md); `HOLD` / `RELEASE` types → **no entry** (no confirmed-balance change) |
| `finance.transaction.reversed.v1` | account-service operator reversal | post a **REVERSAL** entry: the original entry's lines with debit/credit swapped, `reversalOfEntryId` set (looked up by `reversalOfTransactionId`) |

Consumed payloads (verbatim from `finance-account-events.md` — ledger is tolerant of
unknown fields):

`finance.transaction.completed.v1`:
```json
{ "transactionId": "...", "accountId": "...",
  "type": "TOPUP|WITHDRAW|HOLD|CAPTURE|RELEASE|TRANSFER|REVERSAL",
  "money": { "amount": "<minor>", "currency": "KRW" },
  "counterpartyAccountId": "...?", "status": "COMPLETED" }
```
> The `type` is the full `TransactionType.name()` the producer emits
> (`AccountEventPublisher` `p.put("type", t.getType().name())`). The Posting Policy
> maps each: `TOPUP`→DR `CASH_CLEARING`/CR wallet; `WITHDRAW`→reverse; `CAPTURE`→DR
> wallet/CR `SETTLEMENT_SUSPENSE`; `TRANSFER`→DR `CUSTOMER_WALLET:{accountId}`/CR
> `CUSTOMER_WALLET:{counterpartyAccountId}`; `HOLD`/`RELEASE`→no entry. v1
> account-service exposes only hold/capture/release/transfer endpoints, so
> `CAPTURE`/`TRANSFER` are the entries that occur in practice; the policy is
> complete for all types.

`finance.transaction.reversed.v1`:
```json
{ "transactionId": "<reversal-txn>", "reversalOfTransactionId": "<original>",
  "accountId": "...", "money": { "amount": "<minor>", "currency": "KRW" } }
```

### Consumer rules

- **Idempotent** — `eventId` is recorded in `processed_events` in the same Tx as the
  entry; a re-delivered event posts nothing (at-most-once entry).
- **GL/AP feed (3rd increment)** — posting an entry / closing a period appends a
  `finance.ledger.*` outbox row in the same Tx (§ Published — emitted); the consumer
  itself does not synchronously re-publish (the relay does, asynchronously).
- **Unmappable / malformed envelope** → DLT (no poison loop).
- **Unbalanced policy result** (a guard, should never happen) → `LEDGER_ENTRY_UNBALANCED`
  → DLT after retries; the books never persist unbalanced.
- A `reversed.v1` whose original entry is not found → DLT (per-`accountId` ordering
  makes a missing original a real anomaly, surfaced not swallowed).
- The ledger never mutates account-service state or writes to `finance_db`.

## Published — emitted (3rd + 4th increments)

From the **3rd increment** (TASK-FIN-BE-009) ledger-service is a **publishing
consumer**: it gains a **per-service transactional outbox** (`OutboxRow` path — see
architecture.md § Event publication) and emits these events as the forward interface
for an external accounting / ERP / AP / reconciliation-ops system. All are appended
**inside the domain write `@Transactional`** (atomic — the feed can never diverge
from the books/records) and relayed to Kafka at-least-once (downstream consumers
dedupe on the envelope `eventId`). The **4th increment** (TASK-FIN-BE-010) adds the
two reconciliation events, reusing the same outbox (the relay's generic
`TopicResolver finance.ledger.X → finance.ledger.X.v1` covers them — no relay change).

Each event is wrapped in the **canonical envelope** (the same shape ledger-service's
own consumer parses):
```json
{ "eventId": "<uuidv7>", "eventType": "finance.ledger.entry.posted",
  "occurredAt": "<ISO-8601>", "tenantId": "finance",
  "source": "finance-platform-ledger-service",
  "aggregateType": "JournalEntry", "aggregateId": "<entryId>",
  "payload": { … } }
```

| Topic | Appended when | `payload` |
|---|---|---|
| `finance.ledger.entry.posted.v1` | every posted `JournalEntry` (auto-journal + reversal; **(5th incr)** operator manual posting; **(9th incr)** FX revaluation adjusting entry; **(10th incr)** FX settlement entry), in `PostJournalEntryUseCase.post`'s `@Transactional` | `{ entryId, postedAt, lines:[{ ledgerAccountCode, direction: "DEBIT"\|"CREDIT", money:{amount,currency}, exchangeRate, baseAmount:{amount,currency} }], source:{ sourceType: "TRANSACTION"\|"MANUAL"\|"REVALUATION"\|"SETTLEMENT", sourceTransactionId, sourceEventId }, reversalOfEntryId? }` (**(8th incr)** each line carries `exchangeRate` + `baseAmount` [base currency KRW]; an all-KRW line has `exchangeRate:"1"` and `baseAmount==money` — the GL feed is base-currency-aware. **(9th incr)** a revaluation entry's foreign-account line carries `money.amount:"0"` [the foreign quantity is unchanged] with a non-zero `baseAmount` [the carrying delta]; its contra is an `FX_GAIN`/`FX_LOSS` KRW line. **(10th incr)** a settlement entry has three lines — a foreign position-removal line [`money={|F| foreign}` + `baseAmount={carrying KRW}`], a base proceeds line, and a realized `FX_GAIN`/`FX_LOSS` line) |
| `finance.ledger.period.closed.v1` | an accounting period closes, in `CloseAccountingPeriodUseCase.close`'s `@Transactional` | `{ periodId, from, to, closedAt, entryCount }` |
| `finance.ledger.reconciliation.completed.v1` | **(4th incr)** an external statement is ingested + matched, in `IngestStatementUseCase`'s `@Transactional` | `{ statementId, ledgerAccountCode, source, statementDate, matchedCount, discrepancyCount }` |
| `finance.ledger.reconciliation.discrepancy.detected.v1` | **(4th incr)** one per recorded discrepancy, in the same ingest `@Transactional` | `{ discrepancyId, ledgerAccountCode, type: "UNMATCHED_EXTERNAL"\|"UNMATCHED_INTERNAL"\|"AMOUNT_MISMATCH", expectedMinor, actualMinor, currency, externalRef?, journalEntryId? }` (**(11th incr)** `AMOUNT_MISMATCH` first activated = an **FX/base difference** on an otherwise-matched foreign line — carries BOTH `externalRef` + `journalEntryId`, `expectedMinor`=internal carrying base, `actualMinor`=bank base value, `currency`=KRW; no new type/topic) |

- **Money** is `{amount:"<minor-units-string>", currency}` (F5 — never a float).
- **No regulated PII** (F7) — ids + amounts only; the feed carries no KYC detail.
- **partition key**: `entry.posted` keyed by `entryId`, `period.closed` by `periodId`,
  `reconciliation.completed` by `statementId`, `discrepancy.detected` by `discrepancyId`
  (each independent — no cross-aggregate ordering requirement).
- **No in-repo consumer yet** — these increments ship the producer + topics only (the
  external GL/AP + reconciliation-ops systems are the intended consumers). `HOLD`/
  `RELEASE` post no entry, so they emit no `entry.posted`; a posting rejected by the
  closed-period guard rolls the Tx back → no `entry.posted` row. Reconciliation
  discrepancies are emitted but **never auto-resolved** (F8 — operator review).
- **(5th incr) `sourceType: "MANUAL"`** — an operator-posted adjusting entry
  (TASK-FIN-BE-011, `POST /api/finance/ledger/entries`) emits the same
  `entry.posted.v1`, tagged `source.sourceType = "MANUAL"`
  (`sourceTransactionId` = the operator `reference`, `sourceEventId` =
  `manual:{Idempotency-Key}`) — the GL/AP feed distinguishes operator adjustments from
  transaction-driven postings by provenance. No new topic.
- **(9th incr) `sourceType: "REVALUATION"`** — an FX revaluation adjusting entry
  (TASK-FIN-BE-015, `POST /api/finance/ledger/revaluations`) emits the same
  `entry.posted.v1`, tagged `source.sourceType = "REVALUATION"`
  (`sourceTransactionId` = the operator `reference`, `sourceEventId` =
  `reval:{Idempotency-Key}`) — the GL/AP feed sees unrealized FX gain/loss adjustments
  tagged by provenance, balanced in the base currency. No new topic.
- **(10th incr) `sourceType: "SETTLEMENT"`** — an FX settlement entry
  (TASK-FIN-BE-016, `POST /api/finance/ledger/settlements`) emits the same
  `entry.posted.v1`, tagged `source.sourceType = "SETTLEMENT"`
  (`sourceTransactionId` = the operator `reference`, `sourceEventId` =
  `settle:{Idempotency-Key}`) — the GL/AP feed sees the **realized** FX gain/loss + the base
  proceeds, the foreign position removed at its carrying value. No new topic.
- **(ADR-002, 23rd–25th incr) FX rate feed = no event surface (net-zero on this contract).** The
  live FX rate feed (`fx_rate_quote` cache + scheduled poller + omitted-rate settlement/revaluation
  fallback + fx-rates read endpoint) is an **outbound HTTP** fetch plus a synchronous read — it
  neither consumes nor publishes any Kafka event, so it adds **no** topic here by design. Operator
  detail = `ledger-api.md § FX rates (read)`; provider / cache / poller detail = architecture.md
  § FX rate feed.

> **Outbox path.** ledger-service uses the `AbstractOutboxPublisher` + per-service
> `LedgerOutboxJpaEntity` path (ADR-MONO-004) — the dedupe/outbox tables are mapped by
> the service's own entities. The libs `OutboxAutoConfiguration` it once had to exclude
> (its `ProcessedEventJpaEntity` collided with the ledger's own consumer-dedupe
> `processed_events`) was deleted by TASK-MONO-406; `libs/java-messaging` now ships no
> `@Entity`.

## Relationship to platform / rules

- Consumption + dedupe = `rules/traits/transactional.md` T2/T3 + `libs/java-messaging`
  standard; envelope shape = `BaseEventPublisher` (account-service producer).
- No event (consumed or future-published) carries regulated PII (F7) — amounts +
  account ids only; regulated detail stays in account-service.
- Event-catalog naming = `rules/domains/fintech.md` (`finance.ledger.*` was
  forward-declared there + in `finance-account-events.md`).
