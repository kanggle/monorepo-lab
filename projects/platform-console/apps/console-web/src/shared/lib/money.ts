import { z } from 'zod';

/**
 * Shared **F5 money primitive** — the console-wide precision-exact
 * minor-units money value (TASK-PC-FE-259).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * `MoneySchema` / `Money` / `DEFAULT_CURRENCY_SCALES` / `formatMoney` were
 * previously declared **twice, character-for-character identically**, in
 * `features/finance-ops/api/types.ts` (§ 2.4.7 `account-service`) and
 * `features/ledger-ops/api/types/money.ts` (§ 2.4.7.1 `ledger-service`),
 * and a third feature (`features/finance-overview`) reached across a
 * feature boundary to borrow one of them. Per `architecture.md`
 * § Forbidden Dependencies —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * — a value consumed by more than one feature is promoted here rather than
 * duplicated or cross-imported. Same promotion pattern as
 * `shared/api/rbac-catalog.ts`. `shared/lib/` (not `shared/api/`) is the
 * right home: this module performs no I/O and knows nothing about any
 * producer — it is a pure value type + a pure renderer, alongside
 * `shared/lib/datetime.ts` and `shared/lib/tolerant-label.ts`.
 *
 * ── F5 MONEY INVARIANT (CONTRACT obligation, NOT a UX nicety) ──
 * `console-integration-contract.md` § 2.4.7 / § 2.4.7.1, sourced from the
 * finance `account-api.md` § Money and `ledger-api.md` § Common shapes:
 * every money is `{ amount: "<string-integer-minor-units>", currency }` —
 * `amount` is a **string-encoded integer in minor units** (KRW scale 0,
 * USD scale 2). The console MUST render money faithfully from the
 * **string** and MUST NOT coerce it to a JS `Number` / float anywhere
 * (parse / store / arithmetic / display) — the precision-preservation
 * contract. `formatMoney(...)` is the only sanctioned way to render a
 * Money value; it uses string manipulation (no float math, no
 * `Number(...)`). Tests grep-assert that `Number()` / `parseFloat()` /
 * `parseInt()` never appear on a line referencing `amount` anywhere under
 * `features/finance-ops/`, `features/ledger-ops/` **or this module**.
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
 *  presentation form). Producer sources = finance `account-api.md` § Money
 *  and `ledger-api.md` § Common shapes (identical table). */
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
