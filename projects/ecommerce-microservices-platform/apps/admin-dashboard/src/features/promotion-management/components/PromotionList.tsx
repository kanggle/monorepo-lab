'use client';

import { useRouter } from 'next/navigation';
import { DataTable, StatusBadge, FilterBar, ListError } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { usePromotions } from '../hooks/use-promotions';
import { PROMOTION_STATUS_OPTIONS } from '@/shared/lib/status-options';
import type { PromotionSummary } from '@repo/types';

const columns: ColumnDef<PromotionSummary>[] = [
  { key: 'name', header: '프로모션명', sortable: true },
  {
    key: 'discountType',
    header: '할인 유형',
    render: (item: PromotionSummary) =>
      item.discountType === 'FIXED' ? '정액' : '정률',
  },
  {
    key: 'discountValue',
    header: '할인값',
    render: (item: PromotionSummary) =>
      item.discountType === 'FIXED'
        ? `${item.discountValue.toLocaleString()}원`
        : `${item.discountValue}%`,
  },
  {
    key: 'issuedCount',
    header: '발급',
    render: (item: PromotionSummary) =>
      `${item.issuedCount} / ${item.maxIssuanceCount}`,
  },
  {
    key: 'startDate',
    header: '시작일',
    render: (item: PromotionSummary) =>
      new Date(item.startDate).toLocaleDateString('ko-KR'),
  },
  {
    key: 'endDate',
    header: '종료일',
    render: (item: PromotionSummary) =>
      new Date(item.endDate).toLocaleDateString('ko-KR'),
  },
  {
    key: 'status',
    header: '상태',
    sortable: true,
    render: (item: PromotionSummary) => <StatusBadge status={item.status} />,
  },
];

export function PromotionList() {
  const router = useRouter();
  const { data, isLoading, isError, refetch, pagination, filters } = usePromotions();

  if (isError) {
    return <ListError message="프로모션 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <div>
      <FilterBar
        statusOptions={PROMOTION_STATUS_OPTIONS}
        statusValue={filters.status}
        onStatusChange={(value) => filters.setFilter('status', value)}
      />
      <DataTable<PromotionSummary>
        columns={columns}
        data={data?.content ?? []}
        pagination={pagination}
        isLoading={isLoading}
        emptyMessage="등록된 프로모션이 없습니다."
        rowKey={(item) => item.promotionId}
        onRowClick={(item) => router.push(`/promotions/${item.promotionId}`)}
      />
    </div>
  );
}
