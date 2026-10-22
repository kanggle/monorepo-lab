/**
 * TASK-PC-FE-365 — the console knows the ecommerce order status `BACKORDERED`
 * (재고부족 이월): it is a status-filter option (the operator can FIND the held
 * orders to cancel), it keeps its label/tone in the distribution, and the
 * sample fixture carries exactly one such order that the list filter returns.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OrdersScreen } from '@/features/ecommerce-ops';
import { ORDER_STATUS_VALUES } from '@/features/ecommerce-ops/api/order-types';
import { ORDER_STATUS_LABELS } from '@/features/ecommerce-ops/components/overview-labels';
import { buildOrderStatusSegments } from '@/features/ecommerce-ops/components/OrderStatusBar';
import type { OrderStatusCount } from '@/features/ecommerce-ops/api/overview-state';
import { ECOMMERCE_ORDERS } from '@/shared/sample/fixtures/ecommerce';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/ecommerce/orders',
}));

beforeEach(() => {
  vi.unstubAllGlobals();
});

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe('TASK-PC-FE-365 — BACKORDERED in the console order surfaces', () => {
  it('the 주문 list status filter offers BACKORDERED', () => {
    render(
      <OrdersScreen orders={{ content: [], page: 0, size: 20, totalElements: 0 }} />,
      { wrapper },
    );
    const select = screen.getByTestId('order-status-filter') as HTMLSelectElement;
    const values = [...select.options].map((o) => o.value);
    expect(values).toContain('BACKORDERED');
    // one option per status + the «전체» blank — nothing dropped, nothing doubled.
    expect(values).toEqual(['', ...ORDER_STATUS_VALUES]);
  });

  it('every status has a Korean label; BACKORDERED reads «재고부족 이월»', () => {
    for (const s of ORDER_STATUS_VALUES) expect(ORDER_STATUS_LABELS[s], s).toBeTruthy();
    expect(ORDER_STATUS_LABELS.BACKORDERED).toBe('재고부족 이월');
  });

  it('the distribution bar includes the BACKORDERED bucket (sum = total)', () => {
    const counts: Record<string, number> = { PENDING: 3, BACKORDERED: 2, CONFIRMED: 4 };
    const buckets: OrderStatusCount[] = ORDER_STATUS_VALUES.map((s) => ({
      status: s,
      count: counts[s] ?? 0,
      cellStatus: 'ok',
    }));
    const segs = buildOrderStatusSegments(buckets)!;
    expect(segs.map((s) => s.key)).toContain('BACKORDERED');
    expect(segs.reduce((a, s) => a + (s.value ?? 0), 0)).toBe(9);
    expect(segs.find((s) => s.key === 'BACKORDERED')?.tone).toBe('warning');
  });

  it('sample fixture: exactly one BACKORDERED order, with no confirmed-only side effects', () => {
    const held = ECOMMERCE_ORDERS.filter((o) => o.status === 'BACKORDERED');
    expect(held).toHaveLength(1);
    // Every sample status is one the console enum knows (no silent drop).
    for (const o of ECOMMERCE_ORDERS) {
      expect(ORDER_STATUS_VALUES as readonly string[]).toContain(o.status);
    }
  });
});
