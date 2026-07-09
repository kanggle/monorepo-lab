import { z } from 'zod';
import { type Money } from './money';

// ---------------------------------------------------------------------------
// FX position open-lots drill — TASK-PC-FE-091
//   § 12 GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots
//   (ledger-api.md § 12 — the 20th increment, FIN-BE-028). STRICTLY READ-ONLY.
//   id-driven by (ledgerAccountCode, currency) — the colon-form account code
//   is `encodeURIComponent`-encoded on the producer path; `currency` is a
//   3-letter ISO-4217 code. An empty position → 200 with `lots: []`, all
//   totals `"0"`, `lotCount: 0` (NOT a 404 — an empty-state, never an error).
//
// F5 MONEY (CONTRACT obligation — § 2.4.7.1): EVERY `*Minor` field
// (`originalForeignMinor`, `remainingForeignMinor`, `originalBaseMinor`,
// `carryingBaseMinor`, `totalRemainingForeignMinor`, `totalCarryingBaseMinor`)
// is a precision-exact **string** of integer minor units — NEVER a number.
// The console MUST render these from the string ONLY (no float / `Number(...)`
// / `parseFloat(...)` / `parseInt(...)` — a test grep-asserts this); `seq` and
// `lotCount` ARE numbers (lot index / count, not money — the F5 invariant is
// amount-only). `acquiredAt` is an ISO-8601 string.
//
// TOLERANCE: only the fields the UI strictly needs are required; everything
// else is passthrough (forward-compatible with later producer fields).
// ---------------------------------------------------------------------------

/**
 * One open FX acquisition lot (`ledger-api.md` § 12 `lots` array element).
 *
 * `originalForeignMinor` + `originalBaseMinor` are the acquisition-time
 * values (never change); `remainingForeignMinor` + `carryingBaseMinor`
 * reflect FIFO consumption (17th incr) + revaluation mark-to-spot (18th
 * incr). `sourceJournalEntryId` is the journal entry that created the lot
 * (acquisition provenance — drill key into the entry view). Every `*Minor`
 * field is an F5 minor-units **string** (NEVER a number); `seq` is the
 * lot's per-`acquiredAt` ordering index (a number, NOT money).
 */
export const PositionLotSchema = z
  .object({
    lotId: z.string(),
    currency: z.string().min(3).max(3),
    acquiredAt: z.string(),
    // ordering index within an `acquiredAt` — a number, NOT money (F5 is
    // amount-only). Tolerant: a non-integer/absent value degrades to 0.
    seq: z.number().int().nonnegative().optional().default(0),
    // F5 — minor-units STRINGS, never coerced to a number.
    originalForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'originalForeignMinor must be an integer string (F5)'),
    remainingForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'remainingForeignMinor must be an integer string (F5)'),
    originalBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'originalBaseMinor must be an integer string (F5)'),
    carryingBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'carryingBaseMinor must be an integer string (F5)'),
    sourceJournalEntryId: z.string(),
  })
  .passthrough();
export type PositionLot = z.infer<typeof PositionLotSchema>;

/**
 * PositionLotsResponse — § 12 `GET .../settlements/{code}/{currency}/lots`
 * 200 body `data` sub-object: the open lots + the position summary
 * (Σremaining foreign, Σcarrying base, lot count). An empty position →
 * `lots: []`, totals `"0"`, `lotCount: 0` (an empty-state — never a 404 /
 * error). The two `total*Minor` fields are F5 minor-units **strings**;
 * `lotCount` is a number.
 */
export const PositionLotsResponseSchema = z
  .object({
    lots: z.array(PositionLotSchema),
    totalRemainingForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'totalRemainingForeignMinor must be an integer string (F5)'),
    totalCarryingBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'totalCarryingBaseMinor must be an integer string (F5)'),
    lotCount: z.number().int().nonnegative(),
  })
  .passthrough();
export type PositionLotsResponse = z.infer<typeof PositionLotsResponseSchema>;

/**
 * Materialises a lot's `remainingForeignMinor` (foreign currency) +
 * `carryingBaseMinor` (KRW base) strings as Money objects for rendering via
 * `formatMoney(...)`. Pure string transformation — NO `Number(...)`. The
 * base leg is always KRW (the fixed base currency, v1).
 */
export function positionLotMoney(lot: PositionLot): {
  originalForeign: Money;
  remainingForeign: Money;
  originalBase: Money;
  carryingBase: Money;
} {
  return {
    originalForeign: { amount: lot.originalForeignMinor, currency: lot.currency },
    remainingForeign: { amount: lot.remainingForeignMinor, currency: lot.currency },
    originalBase: { amount: lot.originalBaseMinor, currency: 'KRW' },
    carryingBase: { amount: lot.carryingBaseMinor, currency: 'KRW' },
  };
}

// ---------------------------------------------------------------------------
// FX 환율 피드 대시보드 — TASK-PC-FE-092
//   § GET /api/finance/ledger/fx-rates (FIN-BE-033)
//   Producer envelope = { data: { feedEnabled, rates: [...] }, meta }.
//   STRICTLY READ-ONLY — global list, no input parameters.
//
// F5 RATE INVARIANT — `rate` is a precision-exact **decimal string** from the
// producer (e.g. "1300.12345678" for KRW/USD; "0.00076923" for USD/KRW).
// It MUST be rendered verbatim (string as-is). NEVER apply `Number()`,
// `parseFloat()`, or `parseInt()` to `rate` — the ledger-ops grep guard
// covers `rate` as well as `amount`/`exchangeRate`. `ageSeconds` IS a number
// (duration, not money — F5 invariant is amount/rate-only).
// ---------------------------------------------------------------------------

/**
 * One FX rate entry from the feed cache (`ledger-api.md` FIN-BE-033
 * `rates` array element).
 *
 * `rate` is a precision-exact **decimal string** (NEVER a number — F5).
 * `ageSeconds` is a plain integer duration (NOT money — numbers allowed).
 * `stale` indicates the rate is beyond the configured freshness window.
 */
export const FxRateSchema = z
  .object({
    baseCurrency: z.string().min(3).max(3),
    foreignCurrency: z.string().min(3).max(3),
    // F5 — exact decimal string; NEVER coerce to Number/parseFloat/parseInt.
    rate: z.string(),
    asOf: z.string(),
    source: z.string(),
    fetchedAt: z.string(),
    // Duration in seconds — NOT money. F5 is amount/rate-only; `ageSeconds`
    // is a count/duration, so `z.number()` is correct here.
    ageSeconds: z.number().int(),
    stale: z.boolean(),
  })
  .passthrough();
export type FxRate = z.infer<typeof FxRateSchema>;

/**
 * FxRatesResponse — FIN-BE-033 `GET /api/finance/ledger/fx-rates` 200 body
 * `data` sub-object: the top-level feed status (`feedEnabled`) + the list of
 * cached rate entries. An empty cache → `rates: []`, `feedEnabled` may be
 * true or false — this is a 200 empty-state, NOT a 404.
 */
export const FxRatesResponseSchema = z
  .object({
    feedEnabled: z.boolean(),
    rates: z.array(FxRateSchema),
  })
  .passthrough();
export type FxRatesResponse = z.infer<typeof FxRatesResponseSchema>;

// ---------------------------------------------------------------------------
// FX 환율 history 드릴 — TASK-PC-FE-104
//   § 14.1 GET /api/finance/ledger/fx-rates/{foreignCurrency}/history?limit=N
//   (FIN-BE-040 — read over the `fx_rate_quote_history` append-only audit
//   trail). Producer envelope = { data: { base, foreign, quotes: [...] }, meta }.
//   STRICTLY READ-ONLY — per-pair time series, `fetched_at DESC` (newest first),
//   ties broken by surrogate id DESC (deterministic, producer-ordered).
//
// F5 RATE INVARIANT — `rate` is a precision-exact **decimal string** from the
// producer (e.g. "13.60000000"); rendered verbatim (string as-is). NEVER apply
// `Number()` / `parseFloat()` / `parseInt()` to `rate`. History rows carry NO
// staleness fields (history is raw provenance, not a live-cache freshness check).
//
// `limit` is a row count (NOT money — the F5 invariant is amount/rate-only):
// default 50, cap 500, `≤ 0` floored to 1. The client hook clamps it; the
// producer also floors/caps (double-defended). An unknown / never-polled
// foreign code → 200 with `quotes: []` (NOT a 404 — mirrors the feed cache's
// empty-200 stance).
// ---------------------------------------------------------------------------

/** Limit bounds for the FX history read (FIN-BE-040 — default 50, cap 500). */
export const FX_HISTORY_DEFAULT_LIMIT = 50;
export const FX_HISTORY_MAX_LIMIT = 500;

/**
 * One FX rate history row (`ledger-api.md` § 14.1 `quotes` array element).
 *
 * `rate` is a precision-exact **decimal string** (NEVER a number — F5).
 * `asOf` is the provider-stated rate instant; `fetchedAt` is when the quote
 * was pulled from the provider; `source` is the provider identifier (audit
 * provenance). No staleness fields — history is raw provenance.
 */
export const FxRateHistoryQuoteSchema = z
  .object({
    // F5 — exact decimal string; NEVER coerce to Number/parseFloat/parseInt.
    rate: z.string(),
    asOf: z.string(),
    fetchedAt: z.string(),
    source: z.string(),
  })
  .passthrough();
export type FxRateHistoryQuote = z.infer<typeof FxRateHistoryQuoteSchema>;

/**
 * FxRateHistoryResponse — FIN-BE-040 § 14.1 200 body `data` sub-object: the
 * `(base, foreign)` pair + the ordered `quotes` time series (newest first).
 * `base` is always `KRW` in v1 (the fixed reporting currency). An unknown /
 * never-polled foreign code → `quotes: []` — a 200 empty-state, NOT a 404.
 */
export const FxRateHistoryResponseSchema = z
  .object({
    base: z.string().min(3).max(3),
    foreign: z.string().min(3).max(3),
    quotes: z.array(FxRateHistoryQuoteSchema),
  })
  .passthrough();
export type FxRateHistoryResponse = z.infer<typeof FxRateHistoryResponseSchema>;

/** FxRateHistoryQueryParams — the optional `limit` (row count, not money). */
export interface FxRateHistoryQueryParams {
  limit?: number;
}

// ---------------------------------------------------------------------------
// FX 환율 수동 refresh — TASK-MONO-300 (Scope B)
//   POST /api/finance/ledger/fx-rates/refresh (FIN-BE-???; finance side Scope A)
//   Producer envelope = { data: { feedEnabled, refreshed }, meta }.
//
// F5 INVARIANT: `refreshed` is a plain integer COUNT (not money — the F5
// invariant is amount/rate-only); `feedEnabled` is a boolean. No `rate`
// string on this path. Feed-disabled → 200 no-op (`feedEnabled:false,
// refreshed:0`). Provider failure → 200 with the count that succeeded
// (best-effort). The console presents the result via toast / status message.
// ---------------------------------------------------------------------------

/**
 * FxRatesRefreshResponse — `POST /api/finance/ledger/fx-rates/refresh` 200
 * body `data` sub-object: the feed-enabled flag + the count of pairs that
 * were upserted. `refreshed` is a non-negative integer count (NOT money —
 * F5 is amount/rate-only). Feed-disabled → `{feedEnabled:false, refreshed:0}`
 * (a 200 no-op, consistent with GET `feedEnabled:false, rates:[]`).
 */
export const FxRatesRefreshResponseSchema = z
  .object({
    feedEnabled: z.boolean(),
    // Count of pairs upserted — NOT money; z.number() is correct here.
    refreshed: z.number().int().nonnegative(),
  })
  .passthrough();
export type FxRatesRefreshResponse = z.infer<typeof FxRatesRefreshResponseSchema>;
