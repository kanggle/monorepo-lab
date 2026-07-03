import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { EcommerceOverview } from '@/features/ecommerce-ops';
import type {
  EcommerceOverviewState,
  AreaCount,
} from '@/features/ecommerce-ops';

/**
 * TASK-PC-FE-156 / TASK-PC-FE-164 — `EcommerceOverview` presentation:
 * count cards show period metrics (오늘/주간/월간) with 전체 total as
 * secondary context. Back-compat testids (`<key>-count`, `<key>-count-degraded`)
 * are preserved; new testids (`<key>-count-today/week/month`) added.
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

const area = (over: Partial<AreaCount> & Pick<AreaCount, 'key'>): AreaCount => ({
  label: over.key,
  href: `/ecommerce/${over.key}`,
  testid: `ecommerce-${over.key}-link`,
  count: 0,
  today: 0,
  week: 0,
  month: 0,
  status: 'ok',
  ...over,
});

const baseState = (over: Partial<EcommerceOverviewState> = {}): EcommerceOverviewState => ({
  notEligible: false,
  counts: [
    area({ key: 'products', testid: 'ecommerce-products-link', count: 100, today: 3, week: 12, month: 30 }),
    area({ key: 'orders', testid: 'ecommerce-orders-link', count: 70, today: 5, week: 25, month: 60 }),
  ],
  orderStatus: [
    { status: 'PENDING', count: 3, cellStatus: 'ok' },
    { status: 'CANCELLED', count: 1, cellStatus: 'ok' },
  ],
  recentOrders: [
    {
      orderId: 'o1',
      userId: 'u1',
      status: 'PENDING',
      totalPrice: 5000,
      itemCount: 2,
      firstItemName: '상품A',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
  recentOrdersStatus: 'ok',
  recentSellers: [
    {
      sellerId: 's1',
      displayName: '셀러A',
      status: 'ACTIVE',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
  recentSellersStatus: 'ok',
  insights: {
    topProductsByOrderCount: [{ id: 'p1', label: '상품P', value: 50 }],
    topProductsByRevenue: [{ id: 'p1', label: '상품P', value: 500000 }],
    topSellersByOrderCount: [{ id: 's1', label: '셀러 원', value: 40 }],
    topSellersByRevenue: [{ id: 's1', label: '셀러 원', value: 400000 }],
  },
  insightsStatus: 'ok',
  ...over,
});

describe('EcommerceOverview (TASK-PC-FE-156 / TASK-PC-FE-164)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <EcommerceOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders count cards as quick-launch links with period values and total', () => {
    render(<EcommerceOverview state={baseState()} />);
    // Link href + testid
    const products = screen.getByTestId('ecommerce-products-link');
    expect(products).toHaveAttribute('href', '/ecommerce/products');
    expect(screen.getByTestId('ecommerce-orders-link')).toBeInTheDocument();

    // Period counts (오늘/주간/월간)
    expect(screen.getByTestId('products-count-today')).toHaveTextContent('3');
    expect(screen.getByTestId('products-count-week')).toHaveTextContent('12');
    expect(screen.getByTestId('products-count-month')).toHaveTextContent('30');

    // Total — back-compat testid `<key>-count`
    expect(screen.getByTestId('products-count')).toHaveTextContent('100');
  });

  it('zero period values render as "0", not degraded', () => {
    render(
      <EcommerceOverview
        state={baseState({
          counts: [
            area({ key: 'products', testid: 'ecommerce-products-link', count: 0, today: 0, week: 0, month: 0 }),
          ],
        })}
      />,
    );
    expect(screen.getByTestId('products-count-today')).toHaveTextContent('0');
    expect(screen.getByTestId('products-count')).toHaveTextContent('0');
    expect(screen.queryByTestId('products-count-degraded')).toBeNull();
  });

  it('a degraded count cell renders a placeholder instead of period numbers', () => {
    render(
      <EcommerceOverview
        state={baseState({
          counts: [
            area({ key: 'products', testid: 'ecommerce-products-link', count: null, today: null, week: null, month: null, status: 'degraded' }),
            area({ key: 'users', testid: 'ecommerce-users-link', count: null, today: null, week: null, month: null, status: 'forbidden' }),
          ],
        })}
      />,
    );
    expect(screen.getByTestId('products-count-degraded')).toHaveTextContent('점검 필요');
    expect(screen.getByTestId('users-count-degraded')).toHaveTextContent('권한 없음');
    // Period + total testids must NOT appear for degraded cells.
    expect(screen.queryByTestId('products-count')).toBeNull();
    expect(screen.queryByTestId('products-count-today')).toBeNull();
    expect(screen.queryByTestId('products-count-week')).toBeNull();
    expect(screen.queryByTestId('products-count-month')).toBeNull();
  });

  it('each count card shows a per-service status indicator reflecting its cell status', () => {
    render(
      <EcommerceOverview
        state={baseState({
          counts: [
            area({ key: 'products', testid: 'ecommerce-products-link', count: 12, status: 'ok' }),
            area({ key: 'orders', testid: 'ecommerce-orders-link', count: null, status: 'degraded' }),
            area({ key: 'users', testid: 'ecommerce-users-link', count: null, status: 'forbidden' }),
          ],
        })}
      />,
    );
    const products = screen.getByTestId('products-service-status');
    expect(products).toHaveAttribute('data-status', 'ok');
    expect(products).toHaveTextContent('정상');
    expect(screen.getByTestId('orders-service-status')).toHaveAttribute('data-status', 'degraded');
    expect(screen.getByTestId('orders-service-status')).toHaveTextContent('점검 필요');
    expect(screen.getByTestId('users-service-status')).toHaveAttribute('data-status', 'forbidden');
    expect(screen.getByTestId('users-service-status')).toHaveTextContent('권한 없음');
  });

  it('renders the order-status distribution and recent panels', () => {
    render(<EcommerceOverview state={baseState()} />);
    expect(screen.getByTestId('order-status-PENDING')).toHaveTextContent('3');
    expect(screen.getByTestId('ecommerce-recent-orders')).toHaveTextContent('상품A');
    expect(screen.getByTestId('ecommerce-recent-sellers')).toHaveTextContent('셀러A');
  });

  it('a degraded recent panel shows a placeholder, not rows', () => {
    render(
      <EcommerceOverview
        state={baseState({ recentOrders: null, recentOrdersStatus: 'degraded' })}
      />,
    );
    expect(screen.getByTestId('ecommerce-recent-orders')).toHaveTextContent('점검 필요');
  });

  // ── TASK-PC-FE-170 — the four ranking charts ─────────────────────────────

  it('renders the four ranking charts with titles, rows, and formatted values', () => {
    render(<EcommerceOverview state={baseState()} />);

    expect(screen.getByText('판매 순위')).toBeInTheDocument();
    // The four chart panels are present.
    expect(screen.getByTestId('ecommerce-rank-products-orders')).toBeInTheDocument();
    expect(screen.getByTestId('ecommerce-rank-products-revenue')).toBeInTheDocument();
    expect(screen.getByTestId('ecommerce-rank-sellers-orders')).toBeInTheDocument();
    expect(screen.getByTestId('ecommerce-rank-sellers-revenue')).toBeInTheDocument();
    // Chart titles.
    expect(screen.getByText('상품별 주문횟수')).toBeInTheDocument();
    expect(screen.getByText('상품별 매출')).toBeInTheDocument();
    expect(screen.getByText('셀러별 주문횟수')).toBeInTheDocument();
    expect(screen.getByText('셀러별 매출')).toBeInTheDocument();

    // count format → `건`; currency format → `₩` KRW.
    const ordersRow = screen.getByTestId('ecommerce-rank-products-orders-row-p1');
    expect(ordersRow).toHaveTextContent('상품P');
    expect(ordersRow).toHaveTextContent('50건');
    const revenueRow = screen.getByTestId('ecommerce-rank-products-revenue-row-p1');
    expect(revenueRow).toHaveTextContent('₩500,000');

    // Seller charts display the overlaid displayName.
    expect(
      screen.getByTestId('ecommerce-rank-sellers-orders-row-s1'),
    ).toHaveTextContent('셀러 원');
  });

  it('insights degraded → the four charts show the degrade placeholder', () => {
    render(
      <EcommerceOverview
        state={baseState({ insights: null, insightsStatus: 'degraded' })}
      />,
    );
    const chart = screen.getByTestId('ecommerce-rank-products-orders');
    expect(chart).toHaveTextContent('데이터를 불러올 수 없습니다');
  });

  it('insights forbidden → the four charts show 권한 없음', () => {
    render(
      <EcommerceOverview
        state={baseState({ insights: null, insightsStatus: 'forbidden' })}
      />,
    );
    expect(
      screen.getByTestId('ecommerce-rank-sellers-revenue'),
    ).toHaveTextContent('권한 없음');
  });

  it('insights ok but a ranking empty → that chart shows the empty note', () => {
    render(
      <EcommerceOverview
        state={baseState({
          insights: {
            topProductsByOrderCount: [],
            topProductsByRevenue: [{ id: 'p1', label: '상품P', value: 1 }],
            topSellersByOrderCount: [],
            topSellersByRevenue: [],
          },
          insightsStatus: 'ok',
        })}
      />,
    );
    expect(
      screen.getByTestId('ecommerce-rank-products-orders'),
    ).toHaveTextContent('데이터가 없습니다.');
    // A sibling with data still renders its row.
    expect(
      screen.getByTestId('ecommerce-rank-products-revenue-row-p1'),
    ).toHaveTextContent('상품P');
  });
});
