# TASK-FIN-BE-062 — refactor-spec: finance-platform title normalization (clean Tier-1)

- **Type**: TASK-FIN-BE (spec-refactor — structural/format only, NO requirement/contract/decision change)
- **Status**: ready
- **Service**: finance-platform (contracts)
- **Domain/traits**: fintech / [event-driven, transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical spec polish)

## Goal

Apply the single **clean, meaning-preserving** title-normalization surfaced by a `/refactor-spec
finance-platform` scan (10 spec files; all H1 titles + relative `.md` links inventoried; the
highest-risk cross-project / root-task / ADR link targets existence-verified; **0 broken links**).

No API path, schema field, event payload, status code, or business rule is touched.

## Scope

**Applied — 1 edit / 1 file:**

| # | File | Fix | Category |
|---|---|---|---|
| 1 | `contracts/events/finance-ledger-events.md:1` | Title `# finance-ledger-events — event contract (ledger-service)` → `# Event Contract — finance-ledger-events` — aligns with the sibling producer contract `finance-account-events.md` (`# Event Contract — finance-account-events`) and the cross-project norm (`erp-approval-events.md` / `erp-masterdata-events.md` both use `# Event Contract — <file>`). The `(ledger-service)` qualifier is redundant (the two event contracts map 1:1 to their services — no disambiguation needed, unlike the HTTP case below). | naming |

## Acceptance Criteria

- [x] 1 edit applied in isolated worktree (`task/fin-be-062-refactor-spec-finance`).
- [x] No markdown link / heading anchor broken (title is an H1 referenced by path, not anchor; the cross-project/root/ADR link targets — `ADR-MONO-008/013/048`, `rules/domains/fintech.md`, `TASK-MONO-114`, scm `TASK-SCM-BE-015`, platform-console `console-integration-contract.md` / `PROJECT.md`, iam `auth-api.md` — all verified present; the finance→finance `#platform-console-operator-read-consumer-adr-mono-013` self-anchor and erp's inbound anchor to it both resolve).
- [x] Zero requirement / contract / schema / status-code / event-payload / business-rule changes.
- [x] Each divergence independently re-verified against siblings before deciding apply-vs-reject.

## Related Specs

- `projects/finance-platform/specs/services/{account,ledger,gateway}-service/architecture.md`
- `projects/finance-platform/specs/contracts/events/README.md` (convention census — topic/eventType consistent; envelope divergence recorded as ADR-gated follow-up)

## Related Contracts

- `projects/finance-platform/specs/contracts/{events,http}/*` — one title normalized, all else semantically unchanged.

## Out of Scope (rejected / deferred — recorded, NOT applied)

**Rejected (scanner-style over-classification — confirmed intentional, NOT drift):**

- **HTTP contract titles** `reconciliation-api.md` (`# reconciliation-api — HTTP contract (ledger-service, 4th increment)`) and `ledger-api.md` (`# ledger-api — HTTP contract (ledger-service)`) diverge from `account-api.md` (`# API Contract — account-service`) — **left as-is**. Both reconciliation-api and ledger-api are HTTP-contract docs **for the same ledger-service**; the file-first `# <file> — HTTP contract (ledger-service, …)` form is **required to disambiguate two API docs under one service** (normalizing both to `# API Contract — ledger-service` would collide). account-api is the 1-doc-per-service case; the divergence encodes real structure, not drift.
- The `万분율` gloss in `reconciliation-api.md:145` (basis-points explanation) and `특수 계약환율` in `ledger-api.md:666` (FX-override domain term) are intentional bilingual domain glosses inside otherwise-English contracts — content, not stray meta-tokens. Not touched.

**Report-only semantic (README already tracks — NOT refactor scope):**

- **Envelope shape divergence (README §5 / §Follow-up)** — `account-service` emits a **7-field** envelope while `ledger-service` emits an **8-field** one (`+tenantId/aggregateType/aggregateId`), and `finance-account-events.md`'s doc example still shows a stale **9-field** shape. README §5 records this verbatim as a live discrepancy; §Follow-up recommends (a) an **ADR-gated ticket** to resolve the 7-vs-8 divergence (breaking wire-format change per `platform/event-driven-policy.md § Contract Rule` + consumer migration) and (b) a **doc-fix** to correct `finance-account-events.md`'s example to the actual 7-field shape. Both are semantic contract-doc changes, out of refactor-spec scope.

## Edge Cases

- Title (H1) edit has no inbound anchor links — docs referenced by path, not by their own H1 fragment.

## Failure Scenarios

- If any doc linked `finance-ledger-events.md` by an H1 anchor fragment, the title change would 404 it — not the case (all references are path-based).
