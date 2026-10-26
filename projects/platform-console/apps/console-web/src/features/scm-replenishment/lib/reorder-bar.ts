/**
 * Bar geometry for the 「재주문점 · 발동 재고 · 제안」 cell — TASK-PC-FE-377. Pure (no React),
 * so the edge cases are unit-testable.
 *
 * One track, scaled 0 .. `max(reorderPoint, trigger) + suggested`:
 *   [ trigger (발동 재고) ][ suggested (제안 수량) ]      ┃ = reorder-point marker
 * The reorder point is drawn as a marker, not a fill, so a `trigger > reorderPoint`
 * oddity (producer rule says `trigger <= reorderPoint`, but BATCH paths may differ)
 * widens the scale instead of overflowing the track; the printed numbers stay the
 * real ones. Every percent is finite, within [0, 100].
 */

export interface ReorderBarInput {
  /** `reorderPointAtTrigger` — snapshot; null/undefined = no value (key absent). */
  reorderPoint: number | null | undefined;
  triggerQty: number | null | undefined;
  suggestedQty: number | null | undefined;
}

export interface ReorderBarGeometry {
  /** Width of the 발동 재고 segment, percent of the track. */
  triggerPct: number;
  /** Width of the 제안 수량 segment (drawn right after the trigger segment). */
  suggestedPct: number;
  /** Position of the reorder-point marker; null when the reorder point is 0. */
  reorderPointPct: number | null;
  /** True when the trigger qty is above the reorder point (unexpected input). */
  triggerAboveReorderPoint: boolean;
}

function usable(n: unknown): n is number {
  return typeof n === 'number' && Number.isFinite(n);
}

function nonNegative(n: number | null | undefined): number {
  return usable(n) && n > 0 ? n : 0;
}

function pct(part: number, whole: number): number {
  return Math.min(100, Math.max(0, (part / whole) * 100));
}

/**
 * Returns `null` (draw NO bar) when there is no usable reorder point, or the track
 * would have no length (everything 0). A reorder point is never invented from the
 * trigger qty or 0.
 */
export function reorderBarGeometry(input: ReorderBarInput): ReorderBarGeometry | null {
  const { reorderPoint } = input;
  if (!usable(reorderPoint) || reorderPoint < 0) return null;
  const trigger = nonNegative(input.triggerQty);
  const suggested = nonNegative(input.suggestedQty);
  const whole = Math.max(reorderPoint, trigger) + suggested;
  if (whole <= 0) return null;
  return {
    triggerPct: pct(trigger, whole),
    suggestedPct: pct(suggested, whole),
    reorderPointPct: reorderPoint > 0 ? pct(reorderPoint, whole) : null,
    triggerAboveReorderPoint: trigger > reorderPoint,
  };
}

/** `재주문점 10 · 발동 5 · 제안 50` — an absent number prints `—`, never a made-up one. */
export function reorderBarText(input: ReorderBarInput): string {
  const f = (n: number | null | undefined) => (usable(n) ? String(n) : '—');
  return `재주문점 ${f(input.reorderPoint)} · 발동 ${f(input.triggerQty)} · 제안 ${f(input.suggestedQty)}`;
}
