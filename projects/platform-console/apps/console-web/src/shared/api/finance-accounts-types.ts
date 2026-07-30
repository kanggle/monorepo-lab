import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';
import { type Money } from '@/shared/lib/money';

/**
 * Shared **client-safe** finance `account-service` ACCOUNT + BALANCES wire
 * shapes (TASK-PC-FE-009 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * The account + balances read is consumed by two features:
 * `features/finance-ops` (the `/finance/accounts` screen) and
 * `features/finance-overview` (the `/finance` landing snapshot, which
 * previously reached into `@/features/finance-ops/api/types` directly).
 * `architecture.md` § Forbidden Dependencies bars a
 * `features/A → features/B` import ("공유 가치는 `shared/` 로 승격").
 *
 * ── WHY THE SHAPES ARE A SEPARATE MODULE FROM THE CLIENT ──
 * `shared/api/finance-accounts-read.ts` transitively reaches `next/headers`
 * (token + session resolution), which is **server-only**.
 * `features/finance-ops/api/types.ts` re-exports these shapes and is imported
 * by CLIENT components / hooks, so folding the shapes into the client module
 * would make `next build` fail with "You're importing a component that needs
 * next/headers".
 *
 * **Keep this module zod-only.** No `next/headers`, no cookies, no fetch.
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `finance-platform/specs/contracts/http/account-api.md`
 *     § `GET /api/finance/accounts/{id}` (account + balances)
 *     § `GET /api/finance/accounts/{id}/balances`
 * Consumer obligation: `console-integration-contract.md` § 2.4.7.
 *
 * F5 MONEY INVARIANT: balance amounts are minor-units **strings**, rendered
 * only through `shared/lib/money.ts` `formatMoney` — never coerced to a JS
 * `Number` (a test grep-asserts this against this module too).
 *
 * TOLERANCE invariant (§ 2.4.7): the read shapes are permissive — unknown /
 * future account `status` values parse to a generic string value and NEVER
 * throw. Only the fields the UI strictly needs are required; everything else
 * is passthrough.
 */

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
