import { z } from 'zod';

/**
 * Shared money / pagination primitives for the ledger-ops feature-local
 * types (TASK-PC-FE-233 — split out of the former `api/types.ts` god-file;
 * this is the LEAF module every other `api/types/*` module imports from —
 * no reverse dependency, keeping the module graph acyclic).
 *
 * F5 MONEY INVARIANT — see `api/types/index.ts` for the full contract
 * narrative (§ 2.4.7.1). `Money` is `{ amount: "<string-integer-minor-
 * units>", currency }` — `amount` is a **string-encoded integer in minor
 * units**, NEVER a JS `Number` / float. `formatMoney(...)` is the only
 * sanctioned way to render a Money value.
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
 *  presentation form). Producer source = `ledger-api.md` § Common shapes. */
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

/**
 * The `exchangeRate` on a journal line is an exact-decimal provenance
 * factor in minor units (e.g. `"13.5"`, `"1"`) — a **string**, NEVER a
 * float (F5). It is surfaced verbatim (no arithmetic). The schema is a
 * permissive decimal-string regex; a stray non-decimal value is tolerated
 * as a free string (rendered as-is, never throws — defensive tolerant
 * parser). There is intentionally NO numeric coercion helper for it.
 */
export const ExchangeRateSchema = z
  .string()
  .regex(/^-?\d+(\.\d+)?$/, 'exchangeRate must be a decimal string (F5)')
  .or(z.string()); // tolerant fallback — render verbatim, never throw

// ---------------------------------------------------------------------------
// finance success envelope meta (timestamp + optional pagination).
// ---------------------------------------------------------------------------

export const LedgerMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    totalPages: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type LedgerMeta = z.infer<typeof LedgerMetaSchema>;

// ---------------------------------------------------------------------------
// shared pagination constants — consumed by the per-concept
// `*QueryParams` interfaces (period.ts / reconciliation.ts / account.ts)
// and by `ledger-client.ts`'s page-size clamping. Kept here (the leaf
// module) rather than in any single concept module to avoid a
// concept-to-concept import.
// ---------------------------------------------------------------------------

export const LEDGER_DEFAULT_PAGE_SIZE = 20;
export const LEDGER_MAX_PAGE_SIZE = 100;
