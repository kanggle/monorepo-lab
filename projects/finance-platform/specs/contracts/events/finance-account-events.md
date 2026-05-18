# Event Contract — finance-account-events

Kafka events published by `finance-platform/apps/account-service` through the
**transactional outbox** (libs/java-messaging `BaseEventPublisher` +
`AccountOutboxPollingScheduler extends OutboxPollingScheduler`).

Authoritative architecture: [`account-service/architecture.md`](../../services/account-service/architecture.md).
Domain rules: [`rules/domains/fintech.md`](../../../../../rules/domains/fintech.md)
§ Internal Event Catalog + F1/F3/F4/F6.

- **Producer source**: `"finance-platform-account-service"` on every envelope.
- **Topic convention**: `<eventType>` + `.v1`. Breaking change → `.v2`,
  dual-publish during the deprecation window (platform convention).
- **Delivery**: at-least-once (outbox poll). Consumers MUST dedupe on
  `eventId` (`processed_events` pattern). Producer `acks=all`,
  `enable.idempotence=true`.
- **Ordering**: partition key = `accountId` → per-account ordering. A
  transfer emits events for both accounts; cross-account ordering is **not**
  guaranteed (consumers treat each account stream independently).
- **Money** in payloads = `{ "amount": "<integer-minor-units-string>",
  "currency": "<ISO-4217>" }` (F5 — never a float).
- **PII**: payloads carry `accountId` / `ownerRef` only; no KYC raw data,
  no external account refs, no secrets (F7 — masked/omitted).

## Envelope (libs/java-messaging standard)

```json
{
  "eventId": "<uuid>",
  "eventType": "finance.account.opened",
  "occurredAt": "<ISO-8601 UTC>",
  "tenantId": "finance",
  "source": "finance-platform-account-service",
  "aggregateType": "account|transaction",
  "aggregateId": "<accountId|transactionId>",
  "traceId": "<w3c-traceparent trace-id>",
  "payload": { ... }
}
```

## Topics (v1)

| eventType constant | Kafka topic | aggregate | Emitted when |
|---|---|---|---|
| `EVENT_ACCOUNT_OPENED` | `finance.account.opened.v1` | account | account created (`PENDING_KYC`) |
| `EVENT_ACCOUNT_KYC_UPGRADED` | `finance.account.kyc.upgraded.v1` | account | KYC level raised (may → `ACTIVE`) |
| `EVENT_ACCOUNT_STATUS_CHANGED` | `finance.account.status.changed.v1` | account | RESTRICTED/FROZEN/ACTIVE/CLOSED transition |
| `EVENT_BALANCE_HELD` | `finance.balance.held.v1` | account | hold placed |
| `EVENT_BALANCE_CAPTURED` | `finance.balance.captured.v1` | account | hold captured (full/partial) |
| `EVENT_BALANCE_RELEASED` | `finance.balance.released.v1` | account | hold released/expired |
| `EVENT_TRANSACTION_SETTLED` | `finance.transaction.settled.v1` | transaction | txn reached `SETTLED` |
| `EVENT_TRANSACTION_COMPLETED` | `finance.transaction.completed.v1` | transaction | txn `COMPLETED` (incl. transfer) |
| `EVENT_TRANSACTION_FAILED` | `finance.transaction.failed.v1` | transaction | txn `FAILED` (gate/validation) |
| `EVENT_TRANSACTION_REVERSED` | `finance.transaction.reversed.v1` | transaction | reversal txn settled (original immutable) |
| `EVENT_COMPLIANCE_SANCTION_HIT` | `finance.compliance.sanction.hit.v1` | account | sanction/watchlist match → operator queue (F4) |

> `finance.reconciliation.*` and `finance.ledger.*` are **forward-declared in
> fintech.md** but NOT emitted in v1 (no external settlement / no ledger-service).

## Payload schemas (v1)

`finance.account.opened`:
```json
{ "accountId": "...", "ownerRef": "...", "currency": "KRW",
  "kycLevel": "NONE", "status": "PENDING_KYC" }
```

`finance.account.kyc.upgraded`:
```json
{ "accountId": "...", "fromLevel": "NONE", "toLevel": "BASIC",
  "resultingStatus": "ACTIVE" }
```

`finance.account.status.changed`:
```json
{ "accountId": "...", "fromStatus": "ACTIVE", "toStatus": "FROZEN",
  "actorType": "OPERATOR|COMPLIANCE|SYSTEM|HOLDER", "reason": "..." }
```

`finance.balance.held` / `.captured` / `.released`:
```json
{ "accountId": "...", "holdId": "...", "transactionId": "...",
  "money": { "amount": "150000", "currency": "KRW" },
  "available": { "amount": "...", "currency": "KRW" } }
```
(`.captured` adds `"released": {money}` for the auto-released remainder.)

`finance.transaction.settled` / `.completed` / `.failed`:
```json
{ "transactionId": "...", "accountId": "...", "type": "HOLD|CAPTURE|RELEASE|TRANSFER|REVERSAL",
  "money": { "amount": "...", "currency": "KRW" },
  "counterpartyAccountId": "...?", "status": "SETTLED|COMPLETED|FAILED",
  "failureCode": "...?" }
```

`finance.transaction.reversed`:
```json
{ "transactionId": "<reversal-txn>", "reversalOfTransactionId": "<original>",
  "accountId": "...", "money": { "amount": "...", "currency": "KRW" } }
```

`finance.compliance.sanction.hit`:
```json
{ "accountId": "...", "transactionId": "...",
  "screeningRef": "...", "queuedReviewId": "..." }
```
(no matched-list detail in the event — operator queue holds the regulated
detail; F7.)

## Consumer rules

- Dedupe on `eventId` (at-least-once). Per-`accountId` ordering only.
- `finance.transaction.*` is the integration point for a future
  `ledger-service` (v2) auto-journal and `notification-service` (v2)
  fan-out — neither exists in v1; this contract is the forward interface.
- A `REVERSED` event never mutates the original transaction's prior events;
  consumers treat reversal as a new compensating fact (F3).
- `sanction.hit` consumers MUST NOT auto-action funds — the operator queue
  is authoritative (F4/F8 no-auto-close principle).

## Relationship to platform / rules

- Envelope + outbox + dedupe = `rules/traits/transactional.md` T2/T3 +
  `libs/java-messaging` standard (TASK-MONO-049 `BaseEventPublisher`).
- Event-catalog naming follows `rules/domains/fintech.md` § Internal Event
  Catalog (`<prefix>.account.* / .balance.* / .transaction.* /
  .compliance.*`), prefix = `finance`.
- No event carries regulated PII (F7); audit detail lives in `audit_log` /
  operator queue, not the bus.
