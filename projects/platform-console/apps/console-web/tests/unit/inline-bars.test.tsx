import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  MIN_BAR_PCT,
  ProportionBar,
  StackedBar,
  barPercent,
  stackedBase,
  stackedPercents,
  type BarSegment,
} from '@/shared/ui/InlineBar';
import {
  OrderStatusBar,
  buildOrderStatusSegments,
} from '@/features/ecommerce-ops/components/OrderStatusBar';
import { IssuanceBar } from '@/features/ecommerce-ops/components/IssuanceBar';
import { AsnInspectionPanel } from '@/features/wms-ops/components/AsnInspectionPanel';
import {
  WmsOperationsScreen,
  maxLagSeconds,
} from '@/features/wms-ops/components/WmsOperationsScreen';
import {
  ScmSkuBreakdown,
  maxNodeQuantity,
} from '@/features/scm-ops/components/ScmSkuBreakdown';
import type { OrderStatusCount } from '@/features/ecommerce-ops/api/overview-state';
import type { WmsOperationsSectionState } from '@/features/wms-ops';

/**
 * TASK-PC-FE-357 — inline CSS bars over data the screens already hold:
 * the shared primitive's maths (ratio · 0 · null · sum mismatch · max 0) and the
 * five call sites (order status · issuance rate · ASN inspection · SCM per-node
 * quantity · WMS projection lag + lifetime totals). Bars are additive: the
 * number text must remain.
 */

const pct = (el: HTMLElement) => Number(el.getAttribute('data-pct'));

describe('barPercent', () => {
  it('is the value/max ratio', () => {
    expect(barPercent(50, 100)).toBe(50);
    expect(barPercent(100, 100)).toBe(100);
  });
  it('0 / negative / null / undefined / NaN value → 0', () => {
    expect(barPercent(0, 100)).toBe(0);
    expect(barPercent(-3, 100)).toBe(0);
    expect(barPercent(null, 100)).toBe(0);
    expect(barPercent(undefined, 100)).toBe(0);
    expect(barPercent(Number.NaN, 100)).toBe(0);
  });
  it('max 0 / negative / non-finite → 0 (no divide-by-zero)', () => {
    expect(barPercent(5, 0)).toBe(0);
    expect(barPercent(5, -1)).toBe(0);
    expect(barPercent(5, Number.POSITIVE_INFINITY)).toBe(0);
  });
  it('a tiny positive value keeps a min sliver; value > max clamps to 100', () => {
    expect(barPercent(1, 100000)).toBe(MIN_BAR_PCT);
    expect(barPercent(250, 100)).toBe(100);
  });
});

describe('stacked maths', () => {
  const seg = (key: string, value: number | null): BarSegment => ({
    key,
    value,
    tone: 'success',
  });

  it('sum = expected → proportional parts that fill the track', () => {
    expect(stackedPercents([seg('a', 80), seg('b', 20)], 100)).toEqual([80, 20]);
  });
  it('sum < expected → parts scaled to expected, the shortfall stays empty', () => {
    expect(stackedPercents([seg('a', 40), seg('b', 10)], 100)).toEqual([40, 10]);
  });
  it('sum > expected → scaled to the sum (overflow is not hidden)', () => {
    expect(stackedBase([seg('a', 150), seg('b', 50)], 100)).toBe(200);
    expect(stackedPercents([seg('a', 150), seg('b', 50)], 100)).toEqual([75, 25]);
  });
  it('null / missing expected falls back to the sum; null parts count as 0', () => {
    expect(stackedPercents([seg('a', 30), seg('b', null)], null)).toEqual([100, 0]);
    expect(stackedPercents([seg('a', null), seg('b', null)], 0)).toEqual([0, 0]);
  });
});

describe('ProportionBar / StackedBar rendering', () => {
  it('ProportionBar is aria-hidden, carries no text, width = ratio', () => {
    render(<ProportionBar value={25} max={100} testid="pb" />);
    const bar = screen.getByTestId('pb');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
    expect(bar.textContent).toBe('');
    expect((bar.firstChild as HTMLElement).style.width).toBe('25%');
  });
  it('ProportionBar max 0 → zero-width fill', () => {
    render(<ProportionBar value={5} max={0} testid="pb" />);
    expect((screen.getByTestId('pb').firstChild as HTMLElement).style.width).toBe('0%');
  });
  it('StackedBar omits zero segments and flags overflow', () => {
    render(
      <StackedBar
        testid="sb"
        expected={100}
        segments={[
          { key: 'a', value: 150, tone: 'success' },
          { key: 'b', value: 0, tone: 'danger' },
        ]}
      />,
    );
    const bar = screen.getByTestId('sb');
    expect(bar.children).toHaveLength(1);
    expect(bar).toHaveAttribute('data-overflow', 'true');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('① order status bar', () => {
  const bucket = (
    status: OrderStatusCount['status'],
    count: number | null,
    cellStatus: OrderStatusCount['cellStatus'] = 'ok',
  ): OrderStatusCount => ({ status, count, cellStatus });

  it('segments follow the bucket order with the order-status tones', () => {
    const segs = buildOrderStatusSegments([
      bucket('PENDING', 1),
      bucket('SHIPPED', 3),
      bucket('CANCELLED', 0),
    ]);
    expect(segs?.map((s) => [s.key, s.value, s.tone])).toEqual([
      ['PENDING', 1, 'warning'],
      ['SHIPPED', 3, 'progress'],
      ['CANCELLED', 0, 'danger'],
    ]);
  });
  it('renders proportional segments', () => {
    render(<OrderStatusBar buckets={[bucket('PENDING', 1), bucket('SHIPPED', 3)]} />);
    const bar = screen.getByTestId('ecommerce-order-status-bar');
    expect(pct(bar.querySelector('[data-segment="PENDING"]') as HTMLElement)).toBe(25);
    expect(pct(bar.querySelector('[data-segment="SHIPPED"]') as HTMLElement)).toBe(75);
  });
  it('a degraded / null bucket → no bar (never a partial distribution)', () => {
    const { container } = render(
      <OrderStatusBar
        buckets={[bucket('PENDING', 1), bucket('SHIPPED', null, 'degraded')]}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });
  it('empty list → nothing; all zero → note instead of an empty bar', () => {
    const empty = render(<OrderStatusBar buckets={[]} />);
    expect(empty.container).toBeEmptyDOMElement();
    empty.unmount();
    render(<OrderStatusBar buckets={[bucket('PENDING', 0), bucket('SHIPPED', 0)]} />);
    expect(screen.getByTestId('ecommerce-order-status-empty')).toHaveTextContent(
      '주문이 없습니다.',
    );
    expect(screen.queryByTestId('ecommerce-order-status-bar')).toBeNull();
  });
});

describe('② issuance bar', () => {
  const width = () =>
    (screen.getByTestId('ib').firstChild as HTMLElement).style.width;

  it('42 / 100 → 42% progress', () => {
    render(<IssuanceBar issued={42} max={100} testid="ib" />);
    expect(width()).toBe('42%');
  });
  it('0 issued → empty fill; issued > max → clamped 100%', () => {
    const a = render(<IssuanceBar issued={0} max={50} testid="ib" />);
    expect(width()).toBe('0%');
    a.unmount();
    render(<IssuanceBar issued={120} max={100} testid="ib" />);
    expect(width()).toBe('100%');
  });
  it('max not positive → no bar', () => {
    const { container } = render(<IssuanceBar issued={3} max={0} testid="ib" />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('③ ASN inspection bar', () => {
  const inspection = (over: Record<string, unknown>) =>
    ({
      asnId: 'a1',
      inspectionCompletedAt: '2026-09-01T00:00:00Z',
      inspectorId: 'i1',
      totalLines: 1,
      discrepancyCount: 0,
      totalQtyExpected: 100,
      totalQtyPassed: 80,
      totalQtyDamaged: 10,
      totalQtyShort: 10,
      ...over,
    }) as never;

  const renderPanel = (data: never) =>
    render(
      <AsnInspectionPanel
        selected
        loading={false}
        forbidden={false}
        notFound={false}
        degraded={false}
        data={data}
      />,
    );

  it('passed · damaged · short split the expected quantity; numbers remain text', () => {
    renderPanel(inspection({}));
    const bar = screen.getByTestId('wms-asn-inspection-bar');
    const w = (k: string) => pct(bar.querySelector(`[data-segment="${k}"]`) as HTMLElement);
    expect([w('passed'), w('damaged'), w('short')]).toEqual([80, 10, 10]);
    expect(screen.getByTestId('wms-asn-inspection-passed')).toHaveTextContent('80');
    expect(screen.getByTestId('wms-asn-inspection-expected')).toHaveTextContent('100');
  });
  it('sum < expected leaves a gap; sum > expected scales to the sum', () => {
    renderPanel(inspection({ totalQtyPassed: 50, totalQtyDamaged: 0, totalQtyShort: 0 }));
    const under = screen.getByTestId('wms-asn-inspection-bar');
    expect(pct(under.querySelector('[data-segment="passed"]') as HTMLElement)).toBe(50);
  });
  it('overshoot → overflow flag', () => {
    renderPanel(inspection({ totalQtyPassed: 150 }));
    expect(screen.getByTestId('wms-asn-inspection-bar')).toHaveAttribute(
      'data-overflow',
      'true',
    );
  });
  it('null / 0 expected → no bar, the cells stay', () => {
    const a = renderPanel(inspection({ totalQtyExpected: null }));
    expect(screen.queryByTestId('wms-asn-inspection-bar')).toBeNull();
    expect(screen.getByTestId('wms-asn-inspection-expected')).toHaveTextContent('—');
    a.unmount();
    renderPanel(inspection({ totalQtyExpected: 0 }));
    expect(screen.queryByTestId('wms-asn-inspection-bar')).toBeNull();
  });
  it('null parts are treated as 0', () => {
    renderPanel(
      inspection({ totalQtyPassed: null, totalQtyDamaged: null, totalQtyShort: null }),
    );
    const bar = screen.getByTestId('wms-asn-inspection-bar');
    expect(bar.children).toHaveLength(0);
  });
  it('degraded → no bar', () => {
    render(
      <AsnInspectionPanel
        selected
        loading={false}
        forbidden={false}
        notFound={false}
        degraded
        data={undefined}
      />,
    );
    expect(screen.queryByTestId('wms-asn-inspection-bar')).toBeNull();
  });
});

describe('④ SCM per-node quantity bars', () => {
  it('maxNodeQuantity ignores missing quantities; empty → 0', () => {
    expect(maxNodeQuantity([{ quantity: 120 }, { quantity: 75 }, {}])).toBe(120);
    expect(maxNodeQuantity([])).toBe(0);
  });

  const renderBreakdown = (nodes: { nodeId: string; quantity?: number; staleness?: string }[]) =>
    render(
      <ScmSkuBreakdown
        headerWarning="발주 결정용 아님"
        skuFid="sku"
        skuInput=""
        onSubmit={() => {}}
        onSkuInputChange={() => {}}
        apiError={null}
        result={
          {
            data: { sku: 'S1', nodes, totalQuantity: 0 },
            meta: { warning: '발주 결정용 아님' },
          } as never
        }
      />,
    );
  const w = (i: number) =>
    (screen.getByTestId(`scm-sku-node-bar-${i}`).firstChild as HTMLElement).style.width;

  it('bars are relative to the largest node; numbers + S5 warning stay', () => {
    renderBreakdown([
      { nodeId: 'n1', quantity: 120, staleness: 'FRESH' },
      { nodeId: 'n2', quantity: 60, staleness: 'STALE' },
    ]);
    expect(w(0)).toBe('100%');
    expect(w(1)).toBe('50%');
    expect(screen.getByTestId('scm-sku-node-1')).toHaveTextContent('60');
    expect(screen.getAllByText(/발주 결정용 아님/).length).toBeGreaterThan(0);
  });
  it('single node → one full bar', () => {
    renderBreakdown([{ nodeId: 'n1', quantity: 7 }]);
    expect(w(0)).toBe('100%');
  });
  it('missing quantity → no bar for that row; all zero → empty fills', () => {
    renderBreakdown([{ nodeId: 'n1' }, { nodeId: 'n2', quantity: 0 }]);
    expect(screen.queryByTestId('scm-sku-node-bar-0')).toBeNull();
    expect(w(1)).toBe('0%');
  });
});

describe('⑤ WMS projection bars', () => {
  const stateWith = (
    projections: Record<string, unknown>[],
  ): WmsOperationsSectionState =>
    ({
      notEligible: false,
      settings: [],
      settingsStatus: 'ok',
      projection: { projections, worstLagSeconds: 4 },
      projectionStatus: 'ok',
    }) as never;

  it('maxLagSeconds ignores missing lag', () => {
    expect(maxLagSeconds([{ topic: 'a', lagSeconds: 2 }, { topic: 'b' }])).toBe(2);
    expect(maxLagSeconds([])).toBe(0);
  });

  it('lag bars are relative to the largest lag; a topic without lag gets no bar', () => {
    render(
      <WmsOperationsScreen
        state={stateWith([
          { topic: 't1', lagSeconds: 4 },
          { topic: 't2', lagSeconds: 1 },
          { topic: 't3' },
        ])}
      />,
    );
    const fill = (i: number) =>
      (screen.getByTestId(`wms-operations-projection-lag-bar-${i}`).firstChild as HTMLElement)
        .style.width;
    expect(fill(0)).toBe('100%');
    expect(fill(1)).toBe('25%');
    expect(screen.queryByTestId('wms-operations-projection-lag-bar-2')).toBeNull();
    expect(screen.getByTestId('wms-operations-projection-lag-0')).toHaveTextContent('4');
    expect(screen.getByTestId('wms-operations-worst-lag')).toHaveTextContent('최대 지연: 4초');
  });

  it('lifetime totals: stacked bar + text; all zero → text only; none reported → nothing', () => {
    render(
      <WmsOperationsScreen
        state={stateWith([
          { topic: 't1', lifetimeApplied: 90, lifetimeIgnoredDuplicate: 9, lifetimeFailed: 1 },
          { topic: 't2', lifetimeApplied: 0, lifetimeIgnoredDuplicate: 0, lifetimeFailed: 0 },
          { topic: 't3' },
        ])}
      />,
    );
    const bar = screen.getByTestId('wms-operations-projection-lifetime-bar-0');
    const w = (k: string) => pct(bar.querySelector(`[data-segment="${k}"]`) as HTMLElement);
    expect([w('applied'), w('ignored'), w('failed')]).toEqual([90, 9, 2]);
    expect(screen.getByTestId('wms-operations-projection-lifetime-0')).toHaveTextContent(
      '반영 90 · 중복 무시 9 · 실패 1',
    );
    expect(screen.queryByTestId('wms-operations-projection-lifetime-bar-1')).toBeNull();
    expect(screen.getByTestId('wms-operations-projection-lifetime-1')).toHaveTextContent(
      '반영 0 · 중복 무시 0 · 실패 0',
    );
    expect(screen.queryByTestId('wms-operations-projection-lifetime-2')).toBeNull();
  });
});
