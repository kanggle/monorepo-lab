/**
 * Tolerant "unknown-enum label" helper (TASK-PC-FE-234 shared extraction).
 *
 * Renders a producer enum value HONESTLY: a value present in the known
 * vocabulary renders as-is; an unknown / future value renders with a
 * generic `${value} (unknown)` suffix instead of throwing (tolerant-parser
 * discipline — the console must never crash on a value it doesn't yet
 * recognise). A falsy value (`null` / `undefined` / `''`) renders as the
 * em-dash placeholder `'—'` (nullable-aware).
 *
 * Originally centralized in erp-ops as `labelForUnknownEnum`
 * (TASK-PC-FE-109); extracted here VERBATIM so ledger-ops / finance-ops /
 * finance-overview can share it instead of re-implementing the same 4–6
 * line ternary (TASK-PC-FE-234). Zero domain coupling — generic
 * string/array in, string out, same location class as `formatDateTime` /
 * `clampPageSize`.
 */
export function labelForUnknown<T extends string>(
  value: string | undefined | null,
  known: readonly T[],
): string {
  if (!value) return '—';
  return (known as readonly string[]).includes(value)
    ? value
    : `${value} (unknown)`;
}
