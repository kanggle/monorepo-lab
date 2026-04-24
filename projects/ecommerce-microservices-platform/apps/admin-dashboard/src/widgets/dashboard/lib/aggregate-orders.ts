import type { AdminOrderSummary } from '@repo/types';
import { last7DaysKstKeys, toKstDateKey, todayKstKey } from './date-utils';

export interface TodayMetrics {
  count: number;
  revenue: number;
}

export function aggregateToday(orders: AdminOrderSummary[], now: Date = new Date()): TodayMetrics {
  const today = todayKstKey(now);
  let count = 0;
  let revenue = 0;
  for (const order of orders) {
    if (order.status === 'CANCELLED') continue;
    if (toKstDateKey(order.createdAt) !== today) continue;
    count += 1;
    revenue += order.totalPrice;
  }
  return { count, revenue };
}

export interface RevenuePoint {
  date: string;
  revenue: number;
}

export function aggregateLast7DaysRevenue(
  orders: AdminOrderSummary[],
  now: Date = new Date(),
): RevenuePoint[] {
  const keys = last7DaysKstKeys(now);
  const map = new Map<string, number>(keys.map((k) => [k, 0]));
  for (const order of orders) {
    if (order.status === 'CANCELLED') continue;
    const key = toKstDateKey(order.createdAt);
    if (map.has(key)) {
      map.set(key, (map.get(key) ?? 0) + order.totalPrice);
    }
  }
  return keys.map((date) => ({ date, revenue: map.get(date) ?? 0 }));
}
