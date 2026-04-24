import { describe, it, expect } from 'vitest';
import { computeLineChartGeometry } from '@/widgets/dashboard/lib/chart-geometry';

const PADDING = { top: 20, right: 24, bottom: 32, left: 56 };

describe('computeLineChartGeometry', () => {
  it('단일 포인트는 시작 x에 배치된다', () => {
    const g = computeLineChartGeometry([{ value: 100, data: 'a' }], 640, 220, PADDING);
    expect(g.coords).toHaveLength(1);
    expect(g.coords[0]?.x).toBe(PADDING.left);
    expect(g.max).toBe(100);
    expect(g.path).toBe(`M ${PADDING.left} ${g.coords[0]?.y}`);
  });

  it('여러 포인트는 균등 간격으로 배치되고 path는 M→L로 이어진다', () => {
    const points = [
      { value: 0, data: 'a' },
      { value: 50, data: 'b' },
      { value: 100, data: 'c' },
    ];
    const g = computeLineChartGeometry(points, 640, 220, PADDING);
    const innerW = 640 - PADDING.left - PADDING.right;
    const step = innerW / 2;
    expect(g.coords[0]?.x).toBe(PADDING.left);
    expect(g.coords[1]?.x).toBe(PADDING.left + step);
    expect(g.coords[2]?.x).toBe(PADDING.left + step * 2);
    expect(g.path.startsWith('M')).toBe(true);
    expect(g.path.includes('L')).toBe(true);
  });

  it('max가 0인 경우 1로 보정해 NaN을 피한다', () => {
    const g = computeLineChartGeometry([{ value: 0, data: 'a' }], 640, 220, PADDING);
    expect(g.max).toBe(1);
    expect(Number.isFinite(g.coords[0]?.y ?? NaN)).toBe(true);
  });

  it('value가 큰 포인트는 baseline 대비 더 위에 그려진다', () => {
    const points = [
      { value: 10, data: 'low' },
      { value: 100, data: 'high' },
    ];
    const g = computeLineChartGeometry(points, 640, 220, PADDING);
    expect((g.coords[1]?.y ?? 999)).toBeLessThan(g.coords[0]?.y ?? 0);
  });

  it('baselineY는 padding.top + innerHeight와 같다', () => {
    const g = computeLineChartGeometry([{ value: 1, data: 'a' }], 640, 220, PADDING);
    expect(g.baselineY).toBe(PADDING.top + g.innerHeight);
  });
});
