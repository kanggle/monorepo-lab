'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  TENANT_STATUSES,
  TENANT_TYPES,
  type Tenant,
  type TenantPage,
  type TenantStatus,
  type TenantType,
} from '../api/types';

function tenantStatusTone(status: string): StatusTone {
  return status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'warning' : 'neutral';
}

function tenantTypeLabel(tenantType: string): string {
  if (tenantType === 'B2C_CONSUMER') return 'B2C';
  if (tenantType === 'B2B_ENTERPRISE') return 'B2B';
  return tenantType;
}

export interface TenantsTableProps {
  statusFilter: '' | TenantStatus;
  onStatusFilterChange: (value: '' | TenantStatus) => void;
  tenantTypeFilter: '' | TenantType;
  onTenantTypeFilterChange: (value: '' | TenantType) => void;
  onSubmitFilter: (e: React.FormEvent) => void;
  isListError: boolean;
  rows: Tenant[];
  page: TenantPage | undefined;
  currentPage: number;
  onPrevPage: () => void;
  onNextPage: () => void;
}

/**
 * Presentational tenant list — filter bar (status / tenantType) + table +
 * pagination (TASK-PC-FE-226; mirrors `AccountsTable` / `OperatorsTable`
 * split — the container (`TenantsScreen`) owns all state).
 */
export function TenantsTable({
  statusFilter,
  onStatusFilterChange,
  tenantTypeFilter,
  onTenantTypeFilterChange,
  onSubmitFilter,
  isListError,
  rows,
  page,
  currentPage,
  onPrevPage,
  onNextPage,
}: TenantsTableProps) {
  return (
    <div>
      <form
        onSubmit={onSubmitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        aria-label="테넌트 필터"
      >
        <div>
          <label htmlFor="tenants-status-filter" className="block text-xs text-muted-foreground">
            상태
          </label>
          <select
            id="tenants-status-filter"
            value={statusFilter}
            onChange={(e) => onStatusFilterChange(e.target.value as '' | TenantStatus)}
            data-testid="tenants-filter-status"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          >
            <option value="">전체</option>
            {TENANT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="tenants-type-filter" className="block text-xs text-muted-foreground">
            구분
          </label>
          <select
            id="tenants-type-filter"
            value={tenantTypeFilter}
            onChange={(e) => onTenantTypeFilterChange(e.target.value as '' | TenantType)}
            data-testid="tenants-filter-type"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          >
            <option value="">전체</option>
            {TENANT_TYPES.map((t) => (
              <option key={t} value={t}>
                {tenantTypeLabel(t)}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" variant="secondary" data-testid="tenants-filter-submit">
          적용
        </Button>
      </form>

      {isListError ? (
        <div
          role="status"
          data-testid="tenants-list-error"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          테넌트 목록을 불러오지 못했습니다. 잠시 후 다시 시도하세요.
        </div>
      ) : rows.length === 0 ? (
        <div
          role="status"
          data-testid="tenants-empty"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          등록된 테넌트가 없습니다.
        </div>
      ) : (
        <table className="w-full border-collapse text-sm" data-testid="tenants-table">
          <thead>
            <tr className="border-b border-border text-left text-muted-foreground">
              <th className="py-2 pr-4">명칭</th>
              <th className="py-2 pr-4">테넌트 ID</th>
              <th className="py-2 pr-4">구분</th>
              <th className="py-2 pr-4">상태</th>
              <th className="py-2 pr-4">등록일</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr
                key={t.tenantId}
                className="border-b border-border last:border-0"
                data-testid={`tenants-row-${t.tenantId}`}
              >
                <td className="py-2 pr-4">
                  <Link
                    href={`/tenants/${encodeURIComponent(t.tenantId)}`}
                    className="font-medium text-foreground underline-offset-2 hover:underline"
                    data-testid={`tenants-row-link-${t.tenantId}`}
                  >
                    {t.displayName}
                  </Link>
                </td>
                <td className="py-2 pr-4 font-mono text-xs">{t.tenantId}</td>
                <td className="py-2 pr-4">{tenantTypeLabel(t.tenantType)}</td>
                <td className="py-2 pr-4">
                  <StatusBadge tone={tenantStatusTone(t.status)}>{t.status}</StatusBadge>
                </td>
                <td className="py-2 pr-4 text-xs text-muted-foreground">
                  {formatDateTime(t.createdAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {page && (
        <nav className="mt-4 flex items-center justify-between" aria-label="페이지 이동">
          <Button
            variant="secondary"
            disabled={currentPage <= 0}
            onClick={onPrevPage}
            data-testid="tenants-prev"
          >
            이전
          </Button>
          <span className="text-sm text-muted-foreground" data-testid="tenants-pageinfo">
            {page.page + 1} / {Math.max(1, page.totalPages)} 페이지 · 총 {page.totalElements}건
          </span>
          <Button
            variant="secondary"
            disabled={page.page + 1 >= page.totalPages}
            onClick={onNextPage}
            data-testid="tenants-next"
          >
            다음
          </Button>
        </nav>
      )}
    </div>
  );
}
