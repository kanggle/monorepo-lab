import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

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
// ---------------------------------------------------------------------------

/**
 * Money — F5 contract shape: `{ amount, currency }` where `amount` is a
 * precision-exact **string** of integer minor units (e.g. KRW
 * `"1234567890123"`), NEVER a `number`. `currency` is ISO-4217 (3 chars).
 * Producer-side scale: KRW=0 (no decimals), USD=2 (cents).
 *
 * Why a string regex (and NEVER `z.number()`): a JS `Number` is an IEEE
 * 754 float — precision loss on large minor-units values (e.g. KRW
 * `2^54+1`). The regex is the parser-level guarantee that we never
 * accidentally hand the UI a Number-shaped amount.
 */
export const MoneySchema = z.object({
  amount: z.string().regex(/^-?\d+$/, 'amount must be an integer string (F5)'),
  currency: z.string().min(3).max(3),
});
export type Money = z.infer<typeof MoneySchema>;

/** Per-currency minor-unit scale (digits after the decimal point in the
 *  presentation form). Producer source = `account-api.md` § Money. */
export const DEFAULT_CURRENCY_SCALES: Readonly<Record<string, number>> = {
  KRW: 0,
  USD: 2,
  EUR: 2,
  JPY: 0,
  GBP: 2,
};

/**
 * Renders a Money value scale-correct, **from the string minor-units**
 * — no float / `Number(...)` / `parseFloat(...)` / `parseInt(...)` is
 * applied to `amount` (F5 invariant; a test grep-asserts this).
 *
 * String manipulation only:
 *   - locate the sign (if any) and operate on the digit body;
 *   - left-pad to >= scale+1 digits;
 *   - splice in a decimal point (scale > 0) or use the digits as-is
 *     (scale = 0);
 *   - re-attach the sign + the currency.
 *
 * An unknown currency falls back to a sensible default scale (0,
 * tolerant-parser discipline) — no throw.
 */
export function formatMoney(
  money: Money,
  scales: Readonly<Record<string, number>> = DEFAULT_CURRENCY_SCALES,
): string {
  const scale = scales[money.currency] ?? 0;
  const isNegative = money.amount.startsWith('-');
  const digits = isNegative ? money.amount.slice(1) : money.amount;
  // We deliberately work with the string; integer length comparisons are
  // string-length, not numeric — no Number coercion of `amount`.
  let body: string;
  if (scale <= 0) {
    body = digits;
  } else {
    // Left-pad so we have at least `scale + 1` digits, then splice the
    // decimal in.
    const padded =
      digits.length > scale ? digits : '0'.repeat(scale - digits.length + 1) + digits;
    const intPart = padded.slice(0, padded.length - scale);
    const fracPart = padded.slice(padded.length - scale);
    body = `${intPart}.${fracPart}`;
  }
  return `${isNegative ? '-' : ''}${body} ${money.currency}`;
}

// ---------------------------------------------------------------------------
// Balances — per-currency ledger/available/held as F5 money.
//   GET /api/finance/accounts/{id}/balances → { data: [ Balance ], meta }
// ---------------------------------------------------------------------------

export const BalanceSchema = z.object({
  currency: z.string().min(3).max(3),
  // ledger / available / held are all F5 minor-units STRINGS (the
  // producer balances response carries these as raw minor-units strings,
  // not wrapped Money objects — `account-api.md` § GET balances). They
  // are REQUIRED money fields (never optional/discardable — F5).
  ledger: z.string().regex(/^-?\d+$/, 'ledger must be an integer string (F5)'),
  available: z
    .string()
    .regex(/^-?\d+$/, 'available must be an integer string (F5)'),
  held: z.string().regex(/^-?\d+$/, 'held must be an integer string (F5)'),
});
export type Balance = z.infer<typeof BalanceSchema>;

/** finance success envelope: `{ data, meta: { timestamp } }`. */
export const FinanceMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type FinanceMeta = z.infer<typeof FinanceMetaSchema>;

export const BalancesResponseSchema = z.object({
  data: z.array(BalanceSchema),
  meta: FinanceMetaSchema,
});
export type BalancesResponse = z.infer<typeof BalancesResponseSchema>;

/**
 * Convenience accessor that materialises a Balance row's three
 * minor-units strings as Money objects for the same `currency`. Pure
 * string transformation (no `Number(...)`).
 */
export function balanceMoney(b: Balance): {
  ledger: Money;
  available: Money;
  held: Money;
} {
  return {
    ledger: { amount: b.ledger, currency: b.currency },
    available: { amount: b.available, currency: b.currency },
    held: { amount: b.held, currency: b.currency },
  };
}

// ---------------------------------------------------------------------------
// Account — GET /api/finance/accounts/{id}
//   account-api.md: { data: { accountId, status, currency, kycLevel,
//     balances: [...], createdAt, updatedAt }, meta }
// ---------------------------------------------------------------------------

/** Producer status enum surfaced HONESTLY (FROZEN / RESTRICTED / CLOSED
 *  shown as-is, never hidden — § 2.4.7). Stored as a free string so
 *  unknown / future values render generically (no parser throw,
 *  tolerant-parser discipline). */
export const KNOWN_ACCOUNT_STATUSES = [
  'PENDING_KYC',
  'ACTIVE',
  'RESTRICTED',
  'FROZEN',
  'CLOSED',
] as const;
export type KnownAccountStatus = (typeof KNOWN_ACCOUNT_STATUSES)[number];

/**
 * Account status → shared semantic {@link StatusTone} (rendered via the shared
 * `<StatusBadge>` — TASK-PC-FE-159). The regulated states are surfaced HONESTLY
 * (§ 2.4.7): ACTIVE is good (success); PENDING_KYC / RESTRICTED need attention
 * (warning); FROZEN is a hard block (danger); CLOSED is terminal-inactive
 * (neutral). An unknown/future status → `neutral` (tolerant — never a throw).
 */
const ACCOUNT_STATUS_TONE: Record<KnownAccountStatus, StatusTone> = {
  PENDING_KYC: 'warning',
  ACTIVE: 'success',
  RESTRICTED: 'warning',
  FROZEN: 'danger',
  CLOSED: 'neutral',
};

export function accountStatusTone(status: string): StatusTone {
  return ACCOUNT_STATUS_TONE[status as KnownAccountStatus] ?? 'neutral';
}

export const KNOWN_KYC_LEVELS = ['NONE', 'BASIC', 'FULL'] as const;
export type KnownKycLevel = (typeof KNOWN_KYC_LEVELS)[number];

export const AccountSchema = z
  .object({
    accountId: z.string(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    currency: z.string().min(3).max(3),
    kycLevel: z.string().optional(),
    balances: z.array(BalanceSchema).optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type Account = z.infer<typeof AccountSchema>;

export const AccountResponseSchema = z.object({
  data: AccountSchema,
  meta: FinanceMetaSchema,
});
export type AccountResponse = z.infer<typeof AccountResponseSchema>;

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
