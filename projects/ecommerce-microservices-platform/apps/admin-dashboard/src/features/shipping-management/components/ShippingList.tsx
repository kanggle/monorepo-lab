'use client';

import { useState } from 'react';
import { DataTable, StatusBadge, FilterBar, ListError, ConfirmDialog } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useShippings } from '../hooks/use-shippings';
import { useUpdateShippingStatus } from '../hooks/use-update-shipping-status';
import { useRefreshTracking } from '../hooks/use-refresh-tracking';
import { SHIPPING_STATUS_OPTIONS } from '@/shared/lib/status-options';
import { ShipFormDialog } from './ShipFormDialog';
import { StatusActionButton } from './StatusActionButton';
import { RefreshTrackingButton } from './RefreshTrackingButton';
import type { ShippingSummary, ShippingStatus } from '@repo/types';

export function ShippingList() {
  const { data, isLoading, isError, refetch, pagination, filters } = useShippings();
  const mutation = useUpdateShippingStatus();
  const refreshMutation = useRefreshTracking();
  // Shared pending guard so a status transition and a carrier sync cannot be
  // double-submitted across the two mutations (TASK-FE-073 edge case).
  const isAnyPending = mutation.isPending || refreshMutation.isPending;

  const [shipFormTarget, setShipFormTarget] = useState<ShippingSummary | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<{ shipping: ShippingSummary; target: ShippingStatus } | null>(null);

  function handleAction(shipping: ShippingSummary, target: ShippingStatus) {
    if (target === 'SHIPPED') {
      setShipFormTarget(shipping);
    } else {
      setConfirmTarget({ shipping, target });
    }
  }

  function handleShipConfirm(trackingNumber: string, carrier: string) {
    if (!shipFormTarget) return;
    mutation.mutate(
      { shippingId: shipFormTarget.shippingId, data: { status: 'SHIPPED', trackingNumber, carrier } },
      { onSuccess: () => setShipFormTarget(null) },
    );
  }

  function handleConfirm() {
    if (!confirmTarget) return;
    mutation.mutate(
      { shippingId: confirmTarget.shipping.shippingId, data: { status: confirmTarget.target } },
      { onSuccess: () => setConfirmTarget(null) },
    );
  }

  function handleSync(shipping: ShippingSummary) {
    refreshMutation.mutate({ shippingId: shipping.shippingId });
  }

  const columns: ColumnDef<ShippingSummary>[] = [
    {
      key: 'orderId',
      header: '주문 ID',
      render: (item: ShippingSummary) => item.orderId.slice(0, 8) + '...',
    },
    {
      key: 'status',
      header: '배송 상태',
      sortable: true,
      render: (item: ShippingSummary) => <StatusBadge status={item.status} />,
    },
    {
      key: 'carrier',
      header: '택배사',
      render: (item: ShippingSummary) => item.carrier ?? '-',
    },
    {
      key: 'trackingNumber',
      header: '운송장 번호',
      render: (item: ShippingSummary) => item.trackingNumber ?? '-',
    },
    {
      key: 'createdAt',
      header: '생성일',
      sortable: true,
      render: (item: ShippingSummary) => new Date(item.createdAt).toLocaleDateString('ko-KR'),
    },
    {
      key: 'actions',
      header: '상태 변경',
      render: (item: ShippingSummary) => (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <StatusActionButton shipping={item} isPending={isAnyPending} onAction={handleAction} />
          <RefreshTrackingButton shipping={item} isPending={isAnyPending} onSync={handleSync} />
        </div>
      ),
    },
  ];

  if (isError) {
    return <ListError message="배송 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <div>
      <FilterBar
        statusOptions={SHIPPING_STATUS_OPTIONS}
        statusValue={filters.status}
        onStatusChange={(value) => filters.setFilter('status', value)}
      />
      <DataTable<ShippingSummary>
        columns={columns}
        data={data?.content ?? []}
        pagination={pagination}
        isLoading={isLoading}
        emptyMessage="배송 건이 없습니다."
        rowKey={(item) => item.shippingId}
      />

      <ShipFormDialog
        open={shipFormTarget !== null}
        isPending={mutation.isPending}
        onConfirm={handleShipConfirm}
        onCancel={() => setShipFormTarget(null)}
      />

      <ConfirmDialog
        open={confirmTarget !== null}
        title="배송 상태 변경"
        message={
          confirmTarget
            ? `배송 상태를 '${SHIPPING_STATUS_OPTIONS.find((o) => o.value === confirmTarget.target)?.label ?? confirmTarget.target}'(으)로 변경하시겠습니까?`
            : ''
        }
        confirmLabel="변경"
        confirmDisabled={mutation.isPending}
        onConfirm={handleConfirm}
        onCancel={() => setConfirmTarget(null)}
      />
    </div>
  );
}
