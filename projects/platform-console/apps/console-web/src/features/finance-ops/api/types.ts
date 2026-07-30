import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';
import { MoneySchema } from '@/shared/lib/money';
import {
  FinanceMetaSchema,
  type FinanceMeta,
} from '@/shared/api/finance-accounts-types';

/**
 * Feature-local types for the finance `account-service`'s read-only
 * account + balances + transactions surface (TASK-PC-FE-009 — ADR-MONO-013
 * Phase 5, the THIRD non-IAM federated domain — closes the non-GAP
 * federation cycle: wms → scm → finance).
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `finance-platform/specs/contracts/http/account-api.md`
 *     § `GET /api/finance/accounts/{id}` (account + balances)
 *     § `GET /api/finance/accounts/{id}/balances`
 *     § `GET /api/finance/accounts/{id}/transactions` (paginated)
 * Consumer obligation: `console-integration-contract.md` § 2.4.7 (reuses
 * the § 2.4.5 per-domain credential rule — NOT re-derived; same outcome
 * as § 2.4.6 scm). finance-side spec-first basis:
 * `finance-platform/specs/integration/iam-integration.md` § *platform-
 * console Operator Read Consumer* (TASK-FIN-BE-005).
 *
 * These zod schemas are the runtime parsers the api-client / tests
 * assert against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * F5 MONEY INVARIANT (CONTRACT obligation, NOT a UX nicety — § 2.4.7):
 * `account-api.md` § Money: every money is
 * `{ amount: "<string-integer-minor-units>", currency }` — `amount` is a
 * **string-encoded integer in minor units** (KRW scale 0, USD scale 2).
 * The console MUST render money faithfully from the **string** and MUST
 * NOT coerce it to a JS `Number` / float anywhere (parse / store /
 * arithmetic / display) — the precision-preservation contract. The
 * `Money` schema therefore enforces `amount: z.string().regex(/^-?\d+$/)`,
 * NEVER `z.number()`. `formatMoney(...)` is the only sanctioned way to
 * render a Money value; it uses string manipulation (no float math, no
 * `Number(...)`). A test grep-asserts that `Number()` / `parseFloat()` /
 * `parseInt()` never appear on a line that references `amount` anywhere
 * under `features/finance-ops/`.
 *
 * TOLERANCE invariant (§ 2.4.7 / task Edge Case "Unknown/future enum"):
 * every read shape is permissive — unknown / future account `status`,
 * txn `status`, or txn `type` values parse to a generic string value
 * and NEVER throw. Only the fields the UI strictly needs are required;
 * everything else is passthrough.
 */

// ---------------------------------------------------------------------------
// F5 money — string-encoded integer minor units + ISO-4217 currency.
//
// TASK-PC-FE-259: the Money primitive itself now lives in
// `shared/lib/money.ts`. It was declared here AND, character-for-character
// identically, in `features/ledger-ops/api/types/money.ts` — and a third
// feature (`features/finance-overview`) cross-imported this copy, which
// `architecture.md` § Forbidden Dependencies forbids ("공유 가치는 `shared/`
// 로 승격"). The single definition is promoted; this module re-exports it so
// every finance-ops consumer (`BalancesTable`, `TransactionsTable`, the
// `TransactionSchema` that composes `MoneySchema`, …) keeps its existing
// `../api/types` import unchanged. 0 behavior change — the promoted
// implementation is byte-identical.
// ---------------------------------------------------------------------------

export { MoneySchema, DEFAULT_CURRENCY_SCALES, formatMoney } from '@/shared/lib/money';
export type { Money } from '@/shared/lib/money';

// ---------------------------------------------------------------------------
// Account + balances read shapes — promoted to `shared/api/finance-accounts-types.ts`
// (TASK-PC-FE-259).
//
// `features/finance-overview` (the `/finance` landing) renders the operator's
// default-account snapshot from the SAME shapes, so they are consumed by two
// features — `architecture.md` § Forbidden Dependencies says a shared value is
// promoted to `shared/`, not cross-imported. Re-exported here so this module
// stays the finance-ops public type surface (console-integration-contract
// § 2.4.7) and every existing `../api/types` import is unchanged. The
// TRANSACTION shapes below are NOT promoted — single consumer, feature-local.
// ---------------------------------------------------------------------------

export {
  BalanceSchema,
  FinanceMetaSchema,
  BalancesResponseSchema,
  balanceMoney,
  KNOWN_ACCOUNT_STATUSES,
  accountStatusTone,
  KNOWN_KYC_LEVELS,
  AccountSchema,
  AccountResponseSchema,
} from '@/shared/api/finance-accounts-types';
export type {
  Balance,
  FinanceMeta,
  BalancesResponse,
  KnownAccountStatus,
  KnownKycLevel,
  Account,
  AccountResponse,
} from '@/shared/api/finance-accounts-types';

// ---------------------------------------------------------------------------
// Transactions — GET /api/finance/accounts/{id}/transactions
//   account-api.md: { data: [ Transaction ],
//     meta: { page, size, totalElements, timestamp } }
//   Filters: ?page=&size=&type=&status=
// ---------------------------------------------------------------------------

/** Producer txn status enum surfaced HONESTLY (FAILED / REVERSED rendered
 *  as-is, never hidden — § 2.4.7). Free string for tolerance. */
export const KNOWN_TXN_STATUSES = [
  'PENDING',
  'COMPLETED',
  'FAILED',
  'REVERSED',
  'CAPTURED',
  'RELEASED',
  'ACTIVE',
  'SETTLED',
] as const;
export type KnownTxnStatus = (typeof KNOWN_TXN_STATUSES)[number];

/**
 * Transaction status → shared semantic {@link StatusTone} (rendered via the
 * shared `<StatusBadge>` — TASK-PC-FE-159). COMPLETED / SETTLED are the happy
 * terminals (success); ACTIVE / CAPTURED are mid-lifecycle (progress); PENDING
 * awaits settlement (warning); FAILED / REVERSED are surfaced HONESTLY as
 * terminal-bad (danger); RELEASED is a benign hold-release (neutral). An
 * unknown/future status → `neutral` (tolerant — never a throw).
 */
const TXN_STATUS_TONE: Record<KnownTxnStatus, StatusTone> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  FAILED: 'danger',
  REVERSED: 'danger',
  CAPTURED: 'progress',
  RELEASED: 'neutral',
  ACTIVE: 'progress',
  SETTLED: 'success',
};

export function txnStatusTone(status: string): StatusTone {
  return TXN_STATUS_TONE[status as KnownTxnStatus] ?? 'neutral';
}

/** Producer txn type enum. Free string for tolerance — unknown/future
 *  values render with a generic label. */
export const KNOWN_TXN_TYPES = [
  'HOLD',
  'CAPTURE',
  'RELEASE',
  'TRANSFER',
  'REVERSAL',
  'CREDIT',
  'DEBIT',
] as const;
export type KnownTxnType = (typeof KNOWN_TXN_TYPES)[number];

export const TransactionSchema = z
  .object({
    transactionId: z.string(),
    // tolerated as free string (unknown → generic label).
    type: z.string(),
    status: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
    counterpartyAccountId: z.string().nullable().optional(),
    reversalOfTransactionId: z.string().nullable().optional(),
    createdAt: z.string().optional(),
    settledAt: z.string().nullable().optional(),
  })
  .passthrough();
export type Transaction = z.infer<typeof TransactionSchema>;

export const TransactionsResponseSchema = z.object({
  data: z.array(TransactionSchema),
  meta: FinanceMetaSchema,
});
export type TransactionsResponse = z.infer<typeof TransactionsResponseSchema>;

// ---------------------------------------------------------------------------
// query params
// ---------------------------------------------------------------------------

export const FINANCE_DEFAULT_PAGE_SIZE = 20;
export const FINANCE_MAX_PAGE_SIZE = 100;

export interface TransactionsQueryParams {
  /** Producer txn type filter. */
  type?: string;
  /** Producer txn status filter. */
  status?: string;
  page?: number;
  size?: number;
}

/** A page-aware transactions result + the producer-supplied meta. */
export interface TransactionsResult {
  data: Transaction[];
  meta: FinanceMeta;
}
