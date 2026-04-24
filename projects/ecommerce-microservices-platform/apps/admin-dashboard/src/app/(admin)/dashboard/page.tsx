'use client';

import { PageLayout, Section } from '@/shared/ui';
import {
  TodayOrdersKpi,
  PendingOrdersKpi,
  OutOfStockKpi,
  RevenueTrendChart,
  RecentOrdersTable,
} from '@/widgets/dashboard';

export default function DashboardPage() {
  return (
    <PageLayout title="대시보드">
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '16px',
          marginBottom: '28px',
        }}
      >
        <TodayOrdersKpi />
        <PendingOrdersKpi />
        <OutOfStockKpi />
      </div>
      <Section title="최근 7일 매출 추이">
        <RevenueTrendChart />
      </Section>
      <Section title="최근 주문">
        <RecentOrdersTable />
      </Section>
    </PageLayout>
  );
}
