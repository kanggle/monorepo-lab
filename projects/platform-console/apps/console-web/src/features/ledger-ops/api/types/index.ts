/**
 * Feature-local types for the finance `ledger-service`'s read-only
 * double-entry general-ledger surface (TASK-PC-FE-072 — § 2.4.7.1; the
 * SECOND finance-product service bound by the console alongside the
 * § 2.4.7 `account-service`, exactly as § 2.4.5.1 binds a second wms
 * service alongside § 2.4.5). STRICTLY READ-ONLY.
 *
 * Authoritative producer contracts (do NOT redefine — consume read-only):
 *   `finance-platform/specs/contracts/http/ledger-api.md`
 *     § 1 `GET /api/finance/ledger/entries/{entryId}` (entry by id)
 *     § 4 `GET /api/finance/ledger/trial-balance`
 *     § 7 `GET /api/finance/ledger/periods` (paginated list)
 *     § 8 `GET /api/finance/ledger/periods/{periodId}` (detail + snapshot)
 *   `finance-platform/specs/contracts/http/reconciliation-api.md`
 *     § 4 `GET /api/finance/ledger/reconciliation/discrepancies` (queue)
 *     § 5 `GET /api/finance/ledger/reconciliation/discrepancies/{id}`
 * Consumer obligation: `console-integration-contract.md` § 2.4.7.1 (reuses
 * the § 2.4.5 per-domain credential rule VIA the § 2.4.7 finance binding —
 * NOT re-derived). finance-side spec-first basis:
 * `finance-platform/specs/integration/iam-integration.md` § *platform-
 * console Operator Read Consumer* (TASK-FIN-BE-005 — the same finance
 * tenant gate the ledger shares with the account-service).
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per architecture.md
 * § Allowed Dependencies.
 *
 * F5 MONEY INVARIANT — MULTI-CURRENCY LEDGER FORM (CONTRACT obligation,
 * NOT a UX nicety — § 2.4.7.1): every money is
 * `{ amount: "<string-integer-minor-units>", currency }` — `amount` is a
 * **string-encoded integer in minor units** (KRW scale 0, USD scale 2). A
 * journal line carries THREE money/rate fields — the transaction `money`,
 * the `exchangeRate` (an exact-decimal **string** factor in minor units,
 * never a float — e.g. `"13.5"`), and the `baseAmount` (the line's value in
 * the fixed base currency **KRW**, which is balance-authoritative). The
 * console MUST render all of them faithfully from the **string** and MUST
 * NOT coerce any `amount` or `exchangeRate` to a JS `Number` / float
 * anywhere (parse / store / arithmetic / display) — the precision-
 * preservation contract. The `Money` schema therefore enforces
 * `amount: z.string().regex(/^-?\d+$/)`, NEVER `z.number()`; `exchangeRate`
 * is a free decimal **string** (`/^-?\d+(\.\d+)?$/`), NEVER a number.
 * `formatMoney(...)` is the only sanctioned way to render a Money value; it
 * uses string manipulation (no float math, no `Number(...)`). A test
 * grep-asserts that `Number()` / `parseFloat()` / `parseInt()` never appear
 * on a line that references `amount` or `exchangeRate` anywhere under
 * `features/ledger-ops/`.
 *
 * TOLERANCE invariant (§ 2.4.7.1 / task Edge Case "Unknown/future enum"):
 * every read shape is permissive — unknown / future `source.sourceType`,
 * period `status`, discrepancy `type`/`status` values parse to a generic
 * string value and NEVER throw. Only the fields the UI strictly needs are
 * required; everything else is passthrough.
 *
 * MODULE LAYOUT (TASK-PC-FE-233 — split out of the former single-file
 * `api/types.ts` god-file into concept modules; this barrel re-exports
 * every module so `from '.../api/types'` resolves here unchanged):
 *   - `money.ts` — Money / ExchangeRate / LedgerMeta / shared pagination
 *     constants (the leaf module every other module imports from).
 *   - `trial-balance.ts` — trial balance read.
 *   - `journal.ts` — journal entry read.
 *   - `period.ts` — accounting periods read.
 *   - `reconciliation.ts` — discrepancy queue/detail + resolve mutation +
 *     statement-detail read (statement reuses the discrepancy schema).
 *   - `account.ts` — account-level balance/entries drill reads.
 *   - `fx.ts` — FX position open-lots + FX rate feed/history/refresh.
 *
 * TASK-PC-FE-259 — FOUR of those concept modules (`trial-balance` · `period` ·
 * `reconciliation` · `fx`) back reads that `features/finance-overview` also
 * consumes for the `/finance` landing snapshot, so they were promoted to
 * `shared/api/ledger-types/` (with their leaf `money`/meta primitives) per
 * `architecture.md` § Forbidden Dependencies — "공유 가치는 `shared/` 로
 * 승격", never a `features/A → features/B` import. `journal.ts` and
 * `account.ts` have a single consumer and stay feature-local. This barrel
 * still re-exports all seven, so `from '.../api/types'` is unchanged for every
 * ledger-ops component, hook, proxy route and test.
 */

export * from './money';
export * from '@/shared/api/ledger-types/trial-balance';
export * from './journal';
export * from '@/shared/api/ledger-types/period';
export * from '@/shared/api/ledger-types/reconciliation';
export * from './account';
export * from '@/shared/api/ledger-types/fx';
