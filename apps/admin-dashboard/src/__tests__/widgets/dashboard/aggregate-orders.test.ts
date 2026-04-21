import { describe, it, expect } from 'vitest';
import {
  aggregateToday,
  aggregateLast7DaysRevenue,
} from '@/widgets/dashboard/lib/aggregate-orders';
import type { AdminOrderSummary } from '@repo/types';

function order(
  overrides: Partial<AdminOrderSummary> & { createdAt: string },
): AdminOrderSummary {
  return {
    orderId: overrides.orderId ?? 'o1',
    userId: overrides.userId ?? 'u1',
    status: overrides.status ?? 'PENDING',
    totalPrice: overrides.totalPrice ?? 10000,
    itemCount: overrides.itemCount ?? 1,
    firstItemName: overrides.firstItemName ?? '상품',
    createdAt: overrides.createdAt,
  };
}

describe('aggregateToday', () => {
  const now = new Date('2026-04-13T05:00:00Z'); // KST 2026-04-13 14:00

  it('오늘 생성된 주문만 집계한다', () => {
    const orders = [
      order({ createdAt: '2026-04-12T23:59:00Z', totalPrice: 1000 }), // KST 4-13 08:59
      order({ createdAt: '2026-04-13T04:00:00Z', totalPrice: 2000 }), // KST 4-13 13:00
      order({ createdAt: '2026-04-11T00:00:00Z', totalPrice: 9999 }), // KST 4-11 09:00 (제외)
    ];
    const result = aggregateToday(orders, now);
    expect(result.count).toBe(2);
    expect(result.revenue).toBe(3000);
  });

  it('취소된 주문은 제외한다', () => {
    const orders = [
      order({ createdAt: '2026-04-13T04:00:00Z', totalPrice: 5000, status: 'CANCELLED' }),
      order({ createdAt: '2026-04-13T04:00:00Z', totalPrice: 3000, status: 'PENDING' }),
    ];
    const result = aggregateToday(orders, now);
    expect(result.count).toBe(1);
    expect(result.revenue).toBe(3000);
  });

  it('주문이 없으면 0을 반환한다', () => {
    expect(aggregateToday([], now)).toEqual({ count: 0, revenue: 0 });
  });
});

describe('aggregateLast7DaysRevenue', () => {
  const now = new Date('2026-04-13T05:00:00Z');

  it('7일의 데이터를 빈 날짜 0으로 채워 반환한다', () => {
    const result = aggregateLast7DaysRevenue([], now);
    expect(result).toHaveLength(7);
    expect(result.every((p) => p.revenue === 0)).toBe(true);
    expect(result[6]?.date).toBe('2026-04-13');
    expect(result[0]?.date).toBe('2026-04-07');
  });

  it('일자별로 매출을 합산하고 취소 주문은 제외한다', () => {
    const orders = [
      order({ createdAt: '2026-04-13T04:00:00Z', totalPrice: 1000 }),
      order({ createdAt: '2026-04-13T05:00:00Z', totalPrice: 2000 }),
      order({ createdAt: '2026-04-13T06:00:00Z', totalPrice: 9999, status: 'CANCELLED' }),
      order({ createdAt: '2026-04-10T03:00:00Z', totalPrice: 500 }),
    ];
    const result = aggregateLast7DaysRevenue(orders, now);
    const byDate = Object.fromEntries(result.map((p) => [p.date, p.revenue]));
    expect(byDate['2026-04-13']).toBe(3000);
    expect(byDate['2026-04-10']).toBe(500);
    expect(byDate['2026-04-11']).toBe(0);
  });

  it('7일 범위 밖 주문은 집계하지 않는다', () => {
    const orders = [order({ createdAt: '2026-04-01T00:00:00Z', totalPrice: 10000 })];
    const result = aggregateLast7DaysRevenue(orders, now);
    expect(result.reduce((s, p) => s + p.revenue, 0)).toBe(0);
  });
});
