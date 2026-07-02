import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { EcommerceOverview } from '@/features/ecommerce-ops';
import type {
  EcommerceOverviewState,
  AreaCount,
} from '@/features/ecommerce-ops';

/**
 * TASK-PC-FE-156 / TASK-PC-FE-160 — `EcommerceOverview` presentation:
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
  ...over,
});

describe('EcommerceOverview (TASK-PC-FE-156 / TASK-PC-FE-160)', () => {
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
});
