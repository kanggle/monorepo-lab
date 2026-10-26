import { describe, it, expect } from 'vitest';
import {
  reorderBarGeometry,
  reorderBarText,
} from '@/features/scm-replenishment/lib/reorder-bar';

/** TASK-PC-FE-377 — pure bar geometry for the 「재주문점 · 발동 재고 · 제안」 cell. */

function finiteWithin(n: number) {
  expect(Number.isFinite(n)).toBe(true);
  expect(n).toBeGreaterThanOrEqual(0);
  expect(n).toBeLessThanOrEqual(100);
}

describe('reorderBarGeometry', () => {
  it('normal: scale = reorderPoint + suggested; trigger/suggested/marker percents', () => {
    const g = reorderBarGeometry({ reorderPoint: 10, triggerQty: 5, suggestedQty: 50 });
    expect(g).not.toBeNull();
    expect(g!.triggerPct).toBeCloseTo((5 / 60) * 100);
    expect(g!.suggestedPct).toBeCloseTo((50 / 60) * 100);
    expect(g!.reorderPointPct).toBeCloseTo((10 / 60) * 100);
    expect(g!.triggerAboveReorderPoint).toBe(false);
  });

  it.each([null, undefined, Number.NaN, Number.POSITIVE_INFINITY, -3])(
    'no usable reorder point (%s) → null (no bar, nothing invented)',
    (rp) => {
      expect(
        reorderBarGeometry({ reorderPoint: rp, triggerQty: 5, suggestedQty: 50 }),
      ).toBeNull();
    },
  );

  it('reorder point 0: no divide-by-zero, marker omitted, percents finite', () => {
    const g = reorderBarGeometry({ reorderPoint: 0, triggerQty: 0, suggestedQty: 20 });
    expect(g).not.toBeNull();
    expect(g!.reorderPointPct).toBeNull();
    finiteWithin(g!.triggerPct);
    finiteWithin(g!.suggestedPct);
    expect(g!.suggestedPct).toBe(100);
  });

  it('reorder point 0 with everything 0 → null (track has no length)', () => {
    expect(
      reorderBarGeometry({ reorderPoint: 0, triggerQty: 0, suggestedQty: 0 }),
    ).toBeNull();
  });

  it('trigger > reorder point: widens the scale, stays within 100%, flagged', () => {
    const g = reorderBarGeometry({ reorderPoint: 10, triggerQty: 40, suggestedQty: 10 });
    expect(g).not.toBeNull();
    expect(g!.triggerAboveReorderPoint).toBe(true);
    finiteWithin(g!.triggerPct);
    finiteWithin(g!.suggestedPct);
    finiteWithin(g!.reorderPointPct as number);
    expect(g!.triggerPct + g!.suggestedPct).toBeLessThanOrEqual(100 + 1e-9);
  });

  it('suggested 0 (or absent/negative): bar still drawn, suggested width 0', () => {
    for (const suggestedQty of [0, null, undefined, -5]) {
      const g = reorderBarGeometry({ reorderPoint: 10, triggerQty: 5, suggestedQty });
      expect(g).not.toBeNull();
      expect(g!.suggestedPct).toBe(0);
      expect(g!.triggerPct).toBeCloseTo(50);
      expect(g!.reorderPointPct).toBe(100);
    }
  });

  it('absent/NaN/negative trigger counts as 0, never NaN', () => {
    for (const triggerQty of [null, undefined, Number.NaN, -1]) {
      const g = reorderBarGeometry({ reorderPoint: 10, triggerQty, suggestedQty: 10 });
      expect(g!.triggerPct).toBe(0);
      finiteWithin(g!.suggestedPct);
    }
  });
});

describe('reorderBarText', () => {
  it('prints all three numbers', () => {
    expect(reorderBarText({ reorderPoint: 10, triggerQty: 5, suggestedQty: 50 })).toBe(
      '재주문점 10 · 발동 5 · 제안 50',
    );
  });

  it('absent reorder point prints — and keeps the real numbers (no substitution)', () => {
    expect(reorderBarText({ reorderPoint: undefined, triggerQty: 5, suggestedQty: 50 })).toBe(
      '재주문점 — · 발동 5 · 제안 50',
    );
  });

  it('0 is a real value, not absent', () => {
    expect(reorderBarText({ reorderPoint: 0, triggerQty: 0, suggestedQty: 0 })).toBe(
      '재주문점 0 · 발동 0 · 제안 0',
    );
  });
});
