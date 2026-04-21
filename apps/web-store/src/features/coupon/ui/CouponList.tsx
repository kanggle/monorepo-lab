'use client';

import { useState } from 'react';
import type { CouponStatus } from '@repo/types';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { PaginationNav } from '@/shared/ui/PaginationNav';
import { usePagination } from '@/shared/hooks/use-pagination';
import { useCoupons } from '../model/use-coupons';
import { CouponCard } from './CouponCard';

const STATUS_FILTERS: { value: CouponStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'ISSUED', label: '사용가능' },
  { value: 'USED', label: '사용완료' },
  { value: 'EXPIRED', label: '만료' },
];

export function CouponList() {
  const [statusFilter, setStatusFilter] = useState<CouponStatus | 'ALL'>('ALL');
  const status = statusFilter === 'ALL' ? undefined : statusFilter;

  const { page, size, handlePageChange, handleSizeChange } = usePagination(0);

  const { data, isLoading, isError, refetch } = useCoupons(page, size, status);

  const coupons = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const error = isError ? '쿠폰 목록을 불러오는데 실패했습니다.' : '';

  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  function handleStatusChange(newStatus: CouponStatus | 'ALL') {
    setStatusFilter(newStatus);
    handlePageChange(0);
  }

  return (
    <div>
      <h1 className="page-title" style={{ marginBottom: 'var(--space-4)' }}>쿠폰</h1>

      <div
        data-testid="status-filter"
        style={{
          display: 'flex',
          gap: 'var(--space-2)',
          marginBottom: 'var(--space-4)',
          flexWrap: 'wrap',
        }}
      >
        {STATUS_FILTERS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            onClick={() => handleStatusChange(value)}
            className="btn"
            style={{
              fontSize: 'var(--font-size-sm)',
              background: statusFilter === value ? 'var(--color-primary)' : 'transparent',
              color: statusFilter === value ? 'var(--color-white)' : 'var(--color-text-secondary)',
              border: statusFilter === value ? 'none' : '1px solid var(--color-border-light)',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border-light)', borderRadius: 'var(--radius-md)' }}>
              <Skeleton width="50%" height="20px" />
              <div style={{ marginTop: 'var(--space-2)' }}>
                <Skeleton width="70%" height="14px" />
              </div>
              <div style={{ marginTop: 'var(--space-2)' }}>
                <Skeleton width="40%" height="12px" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}
      {!isLoading && !error && coupons.length === 0 && (
        <EmptyState message="보유한 쿠폰이 없습니다." />
      )}
      {coupons.map((coupon) => (
        <CouponCard key={coupon.couponId} coupon={coupon} />
      ))}

      {!isLoading && !error && totalElements > 0 && (
        <PaginationNav
          page={page}
          totalPages={totalPages}
          size={size}
          onPageChange={handlePageChange}
          onSizeChange={handleSizeChange}
          pageSizeSelectId="couponPageSize"
        />
      )}
    </div>
  );
}
