'use client';

import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import type { Group, GroupPage } from '../api/types';

/**
 * Presentational operator-group list — an optional tenantId filter bar + table
 * + pagination (TASK-PC-FE-250; mirrors `TenantsTable` — the container
 * `OperatorGroupsScreen` owns all state). Selecting a row drives the
 * master-detail panel (no route change — the detail renders inline).
 */
export interface GroupsTableProps {
  tenantFilter: string;
  onTenantFilterChange: (value: string) => void;
  onSubmitFilter: (e: React.FormEvent) => void;
  isListError: boolean;
  rows: Group[];
  page: GroupPage | undefined;
  currentPage: number;
  selectedId: string | null;
  onSelect: (groupId: string) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function GroupsTable({
  tenantFilter,
  onTenantFilterChange,
  onSubmitFilter,
  isListError,
  rows,
  page,
  currentPage,
  selectedId,
  onSelect,
  onPrevPage,
  onNextPage,
}: GroupsTableProps) {
  return (
    <div>
      <form
        onSubmit={onSubmitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        aria-label="그룹 필터"
      >
        <div>
          <label
            htmlFor="groups-tenant-filter"
            className="block text-xs text-muted-foreground"
          >
            테넌트 ID
          </label>
          <input
            id="groups-tenant-filter"
            type="text"
            value={tenantFilter}
            onChange={(e) => onTenantFilterChange(e.target.value)}
            data-testid="groups-filter-tenant"
            placeholder="전체"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          />
        </div>
        <Button type="submit" variant="secondary" data-testid="groups-filter-submit">
          적용
        </Button>
      </form>

      {isListError ? (
        <div
          role="status"
          data-testid="groups-list-error"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          그룹 목록을 불러오지 못했습니다. 잠시 후 다시 시도하세요.
        </div>
      ) : rows.length === 0 ? (
        <div
          role="status"
          data-testid="groups-empty"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          등록된 운영자 그룹이 없습니다.
        </div>
      ) : (
        <table className="w-full border-collapse text-sm" data-testid="groups-table">
          <thead>
            <tr className="border-b border-border text-left text-muted-foreground">
              <th className="py-2 pr-4">이름</th>
              <th className="py-2 pr-4">테넌트</th>
              <th className="py-2 pr-4">멤버</th>
              <th className="py-2 pr-4">Grant</th>
              <th className="py-2 pr-4">등록일</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((g) => (
              <tr
                key={g.groupId}
                data-testid={`groups-row-${g.groupId}`}
                data-selected={g.groupId === selectedId ? 'true' : undefined}
                className={
                  g.groupId === selectedId
                    ? 'border-b border-border bg-muted last:border-0'
                    : 'border-b border-border last:border-0'
                }
              >
                <td className="py-2 pr-4">
                  <button
                    type="button"
                    onClick={() => onSelect(g.groupId)}
                    data-testid={`groups-row-select-${g.groupId}`}
                    className="font-medium text-foreground underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  >
                    {g.name}
                  </button>
                </td>
                <td className="py-2 pr-4 font-mono text-xs">{g.tenantId}</td>
                <td className="py-2 pr-4">{g.memberCount}</td>
                <td className="py-2 pr-4">{g.grantCount}</td>
                <td className="py-2 pr-4 text-xs text-muted-foreground">
                  {formatDateTime(g.createdAt)}
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
            data-testid="groups-prev"
          >
            이전
          </Button>
          <span className="text-sm text-muted-foreground" data-testid="groups-pageinfo">
            {page.page + 1} / {Math.max(1, page.totalPages)} 페이지 · 총{' '}
            {page.totalElements}건
          </span>
          <Button
            variant="secondary"
            disabled={page.page + 1 >= page.totalPages}
            onClick={onNextPage}
            data-testid="groups-next"
          >
            다음
          </Button>
        </nav>
      )}
    </div>
  );
}
