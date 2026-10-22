import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OutboundOpsScreen } from '@/features/wms-outbound-ops';
import type {
  OutboundOrderPage,
  OutboundStatusCount,
} from '@/features/wms-outbound-ops';
import {
  OutboundStatusPipeline,
  buildOutboundFlowSegments,
} from '@/features/wms-outbound-ops/components/OutboundStatusPipeline';
import {
  OUTBOUND_FLOW,
  OUTBOUND_SIDE_BRANCHES,
} from '@/features/wms-outbound-ops/components/outbound-ops-helpers';
import {
  OperatorsCard,
  AccountsCard,
} from '@/features/iam-overview/components/IamOverviewSummaryCards';

/**
 * TASK-PC-FE-362 — outbound pipeline bar on `/wms/outbound` (SSR-counted
 * once; flow in the contract order; side branches outside the bar) and the
 * IAM overview ratio bars (zero new reads).
 */

vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...rest
  }: { children: ReactNode; href: string } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const pct = (el: HTMLElement) => Number(el.getAttribute('data-pct'));

const cnt = (
  status: string,
  count: number | null,
  cellStatus: OutboundStatusCount['cellStatus'] = 'ok',
): OutboundStatusCount => ({ status, count, cellStatus });

const ALL = (c: number): OutboundStatusCount[] =>
  [...OUTBOUND_FLOW, ...OUTBOUND_SIDE_BRANCHES].map((s) => cnt(s, c));

const SAMPLE: OutboundStatusCount[] = [
  cnt('PICKING', 2),
  cnt('PICKED', 2),
  cnt('PACKING', 0),
  cnt('PACKED', 4),
  cnt('SHIPPED', 12),
  cnt('CANCELLED', 7),
  cnt('BACKORDERED', 1),
];

describe('① outbound pipeline bar', () => {
  it('flow order equals the contract order (order-status.md) and excludes the side branches', () => {
    expect([...OUTBOUND_FLOW]).toEqual([
      'PICKING',
      'PICKED',
      'PACKING',
      'PACKED',
      'SHIPPED',
    ]);
    // input order must not matter: segments follow OUTBOUND_FLOW
    const shuffled = [...SAMPLE].reverse();
    const segs = buildOutboundFlowSegments(shuffled);
    expect(segs?.map((s) => s.key)).toEqual([...OUTBOUND_FLOW]);
    expect(segs?.map((s) => s.tone)).toEqual([
      'warning',
      'progress',
      'progress',
      'progress',
      'success',
    ]);
  });

  it('renders proportional segments of the FLOW only; side branches are numbers outside the bar', () => {
    render(<OutboundStatusPipeline counts={SAMPLE} />);
    const bar = screen.getByTestId('outbound-status-pipeline-bar');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    // flow total 20
    expect(pct(seg('PICKING'))).toBe(10);
    expect(pct(seg('PICKED'))).toBe(10);
    expect(seg('PACKING')).toBeNull();
    expect(pct(seg('PACKED'))).toBe(20);
    expect(pct(seg('SHIPPED'))).toBe(60);
    expect(seg('CANCELLED')).toBeNull();
    expect(seg('BACKORDERED')).toBeNull();
    expect(screen.getByTestId('outbound-status-CANCELLED')).toHaveTextContent('7');
    expect(screen.getByTestId('outbound-status-BACKORDERED')).toHaveTextContent('1');
    expect(screen.getByTestId('outbound-status-SHIPPED')).toHaveTextContent('12');
    expect(screen.getByTestId('outbound-status-pipeline-scope')).toHaveTextContent(
      '테넌트 범위',
    );
  });

  it('ONE degraded cell (even a side branch) → no bar, no note; that number «—», others stay', () => {
    const counts = SAMPLE.map((c) =>
      c.status === 'BACKORDERED' ? cnt('BACKORDERED', null, 'degraded') : c,
    );
    render(<OutboundStatusPipeline counts={counts} />);
    expect(screen.queryByTestId('outbound-status-pipeline-bar')).toBeNull();
    expect(screen.queryByTestId('outbound-status-pipeline-empty')).toBeNull();
    expect(screen.getByTestId('outbound-status-BACKORDERED')).toHaveTextContent('—');
    expect(screen.getByTestId('outbound-status-SHIPPED')).toHaveTextContent('12');
  });

  it('a forbidden (403) cell → no bar', () => {
    const counts = SAMPLE.map((c) =>
      c.status === 'PICKED' ? cnt('PICKED', null, 'forbidden') : c,
    );
    render(<OutboundStatusPipeline counts={counts} />);
    expect(screen.queryByTestId('outbound-status-pipeline-bar')).toBeNull();
    expect(screen.getByTestId('outbound-status-PICKED')).toHaveTextContent('—');
  });

  it('all zero → «출고 주문이 없습니다.» instead of an empty bar', () => {
    render(<OutboundStatusPipeline counts={ALL(0)} />);
    expect(screen.getByTestId('outbound-status-pipeline-empty')).toHaveTextContent(
      '출고 주문이 없습니다.',
    );
    expect(screen.queryByTestId('outbound-status-pipeline-bar')).toBeNull();
  });

  it('flow all zero but a side branch non-zero → no bar, no empty note, numbers shown', () => {
    const counts = ALL(0).map((c) =>
      c.status === 'CANCELLED' ? cnt('CANCELLED', 3) : c,
    );
    render(<OutboundStatusPipeline counts={counts} />);
    expect(screen.queryByTestId('outbound-status-pipeline-bar')).toBeNull();
    expect(screen.queryByTestId('outbound-status-pipeline-empty')).toBeNull();
    expect(screen.getByTestId('outbound-status-CANCELLED')).toHaveTextContent('3');
  });

  it('no counts (omitted / empty) → section not shown', () => {
    const { container } = render(<OutboundStatusPipeline counts={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const ORDERS: OutboundOrderPage = {
  content: [
    { orderId: 'o-1', orderNo: 'ORD-1', status: 'PICKING', sagaState: 'RESERVED' },
  ],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'updatedAt,desc',
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('② OutboundOpsScreen — counts are SSR-only', () => {
  it('shows the pipeline at the top, above the orders table', () => {
    render(<OutboundOpsScreen orders={ORDERS} statusCounts={SAMPLE} />, {
      wrapper: wrapper(),
    });
    const pipeline = screen.getByTestId('outbound-status-pipeline');
    const table = screen.getByTestId('outbound-table');
    expect(
      pipeline.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('without statusCounts the screen is unchanged (no pipeline section)', () => {
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('outbound-status-pipeline')).toBeNull();
    expect(screen.getByTestId('outbound-table')).toBeInTheDocument();
  });

  it('a list filter change re-queries ONLY the list; the 7 totals are not re-read', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ...ORDERS, content: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} statusCounts={SAMPLE} />, {
      wrapper: wrapper(),
    });
    await user.selectOptions(screen.getByTestId('outbound-status-filter'), 'PACKED');
    await user.click(screen.getByTestId('outbound-filter-submit'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    // exactly the one filtered list read — none of the 7 `size=1` totals
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = new URL(String(fetchMock.mock.calls[0][0]), 'http://console.local');
    expect(url.searchParams.get('status')).toBe('PACKED');
    expect(url.searchParams.get('size')).not.toBe('1');
    // numbers unchanged
    expect(screen.getByTestId('outbound-status-SHIPPED')).toHaveTextContent('12');
  });
});

describe('③ IAM overview ratio bars (no new reads)', () => {
  const ops = (
    over: Partial<Parameters<typeof OperatorsCard>[0]['summary']> = {},
  ) => ({
    total: 10,
    active: 6,
    suspended: 2,
    status: 'ok' as const,
    ...over,
  });
  const accts = (
    over: Partial<Parameters<typeof AccountsCard>[0]['summary']> = {},
  ) => ({ total: 200, locked: 50, status: 'ok' as const, ...over });

  it('operators: 활성 · 정지 against the total; the remainder leaves the track empty', () => {
    render(<OperatorsCard summary={ops()} />);
    const bar = screen.getByTestId('iam-overview-operators-bar');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    expect(pct(seg('ACTIVE'))).toBe(60);
    expect(pct(seg('SUSPENDED'))).toBe(20);
    expect(screen.getByTestId('iam-overview-operators-active')).toHaveTextContent('6');
    expect(screen.getByTestId('iam-overview-operators-suspended')).toHaveTextContent('2');
  });

  it('operators: a missing split number → no bar, numbers stay', () => {
    render(<OperatorsCard summary={ops({ suspended: null })} />);
    expect(screen.queryByTestId('iam-overview-operators-bar')).toBeNull();
    expect(screen.getByTestId('iam-overview-operators-suspended')).toHaveTextContent('—');
    expect(screen.getByTestId('iam-overview-operators-active')).toHaveTextContent('6');
  });

  it('operators: total 0 → no bar', () => {
    render(<OperatorsCard summary={ops({ total: 0, active: 0, suspended: 0 })} />);
    expect(screen.queryByTestId('iam-overview-operators-bar')).toBeNull();
  });

  it('operators: a degraded total card → placeholder, no bar', () => {
    render(
      <OperatorsCard
        summary={{ total: null, active: null, suspended: null, status: 'degraded' }}
      />,
    );
    expect(screen.queryByTestId('iam-overview-operators-bar')).toBeNull();
    expect(screen.getByTestId('iam-overview-operators-degraded')).toBeInTheDocument();
  });

  it('accounts: 잠금 / 전체 ratio', () => {
    render(<AccountsCard summary={accts()} />);
    const bar = screen.getByTestId('iam-overview-accounts-bar');
    expect(pct(bar.querySelector('[data-segment="LOCKED"]') as HTMLElement)).toBe(25);
    expect(screen.getByTestId('iam-overview-accounts-locked')).toHaveTextContent('50');
  });

  it('accounts: locked unknown (sub-leg degraded) → no bar; total 0 → no bar', () => {
    const a = render(<AccountsCard summary={accts({ locked: null })} />);
    expect(screen.queryByTestId('iam-overview-accounts-bar')).toBeNull();
    a.unmount();
    render(<AccountsCard summary={accts({ total: 0, locked: 0 })} />);
    expect(screen.queryByTestId('iam-overview-accounts-bar')).toBeNull();
  });
});
