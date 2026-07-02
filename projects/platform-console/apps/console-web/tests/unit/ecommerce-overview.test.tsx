import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { EcommerceOverview } from '@/features/ecommerce-ops';
import type {
  EcommerceOverviewState,
  AreaCount,
} from '@/features/ecommerce-ops';

/**
 * TASK-PC-FE-156 — `EcommerceOverview` presentation: count cards (each a
 * quick-launch link with back-compat testid), degraded/forbidden placeholders,
 * order-status distribution, and recent activity panels.
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
  status: 'ok',
  ...over,
});

const baseState = (over: Partial<EcommerceOverviewState> = {}): EcommerceOverviewState => ({
  notEligible: false,
  counts: [
    area({ key: 'products', testid: 'ecommerce-products-link', count: 12 }),
    area({ key: 'orders', testid: 'ecommerce-orders-link', count: 7 }),
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

describe('EcommerceOverview (TASK-PC-FE-156)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <EcommerceOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders count cards as quick-launch links with the count value', () => {
    render(<EcommerceOverview state={baseState()} />);
    const products = screen.getByTestId('ecommerce-products-link');
    expect(products).toHaveAttribute('href', '/ecommerce/products');
    expect(screen.getByTestId('products-count')).toHaveTextContent('12');
    expect(screen.getByTestId('ecommerce-orders-link')).toBeInTheDocument();
  });

  it('a degraded count cell renders a placeholder instead of a number', () => {
    render(
      <EcommerceOverview
        state={baseState({
          counts: [
            area({ key: 'products', testid: 'ecommerce-products-link', count: null, status: 'degraded' }),
            area({ key: 'users', testid: 'ecommerce-users-link', count: null, status: 'forbidden' }),
          ],
        })}
      />,
    );
    expect(screen.getByTestId('products-count-degraded')).toHaveTextContent('점검 필요');
    expect(screen.getByTestId('users-count-degraded')).toHaveTextContent('권한 없음');
    expect(screen.queryByTestId('products-count')).toBeNull();
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
});
