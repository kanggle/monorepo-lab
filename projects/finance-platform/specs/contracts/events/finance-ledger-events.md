# finance-ledger-events — event contract (ledger-service)

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
- **Terminal** — the first-increment consumer does NOT re-emit (no outbox).
- **Unmappable / malformed envelope** → DLT (no poison loop).
- **Unbalanced policy result** (a guard, should never happen) → `LEDGER_ENTRY_UNBALANCED`
  → DLT after retries; the books never persist unbalanced.
- A `reversed.v1` whose original entry is not found → DLT (per-`accountId` ordering
  makes a missing original a real anomaly, surfaced not swallowed).
- The ledger never mutates account-service state or writes to `finance_db`.

## Published (forward-declared — NOT emitted in the first increment)

The first increment is a **terminal consumer** (no outbox). These topics are the
forward interface for the deferred increments and are declared here so consumers do
not re-derive them:

| Topic | When (deferred increment) | Payload sketch |
|---|---|---|
| `finance.ledger.entry.posted.v1` | each journal entry posted — the **GL/AP feed** for an external accounting system | `{ entryId, postedAt, lines:[{ledgerAccountCode,direction,money}], source }` |
| `finance.ledger.period.closed.v1` | an accounting period is locked (period-close increment) | `{ periodId, from, to, closedAt, entryCount }` |

When the GL/AP-feed increment lands, the service gains an outbox (it becomes a
publishing consumer); until then it emits nothing.

## Relationship to platform / rules

- Consumption + dedupe = `rules/traits/transactional.md` T2/T3 + `libs/java-messaging`
  standard; envelope shape = `BaseEventPublisher` (account-service producer).
- No event (consumed or future-published) carries regulated PII (F7) — amounts +
  account ids only; regulated detail stays in account-service.
- Event-catalog naming = `rules/domains/fintech.md` (`finance.ledger.*` was
  forward-declared there + in `finance-account-events.md`).
