/**
 * F5 chart-geometry exception — the ONLY place in the console where a money /
 * rate **decimal string** becomes a JS `Number` (TASK-PC-FE-359, owner
 * decision 2026-10-22 KST; `console-web/architecture.md` § Integration Rules
 * «F5 의 유일한 예외», contract § 2.4.7 «Sole exception»).
 *
 * Use the result ONLY for chart geometry — a bar's width, a line point's y.
 * NEVER for anything a person reads (text, totals, «higher / lower / equal»
 * judgements): those stay the producer's original string or `formatMoney`.
 * The precision lost here is a fraction of a pixel, which is why the
 * exception is harmless — and also why it must not leak anywhere else.
 *
 * Guarded by `tests/unit/f5-chart-geometry-exception.test.ts`: this file is
 * the single allow-listed file, and it may hold exactly one coercion call.
 * Framework-agnostic, no `'use client'` — usable from server and client.
 */

/** Plain decimal, optional sign, optional exponent (`"1300.5"`, `"-2"`, `".5"`, `"1.3e3"`). */
const DECIMAL = /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/;

/**
 * The magnitude of a decimal string for chart geometry, or `null` when it
 * cannot be drawn (not a string, empty / whitespace, not a decimal, or a value
 * whose magnitude is not finite — e.g. `"1e400"`). Negative values are
 * returned as negative; the caller decides how its chart treats them.
 */
export function chartMagnitude(decimalString: unknown): number | null {
  if (typeof decimalString !== 'string') return null;
  const s = decimalString.trim();
  if (!DECIMAL.test(s)) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}
