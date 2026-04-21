'use client';

import { useState, useMemo } from 'react';
import { LoadingSpinner } from '@repo/ui';
import { EmptyState } from '@repo/ui';
import { buildPageNumbers } from '@repo/utils/pagination';

export interface ColumnDef<T> {
  key: string;
  header: string;
  sortable?: boolean;
  render?: (item: T) => React.ReactNode;
}

interface PaginationState {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

type SortDirection = 'asc' | 'desc';

interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[];
  pagination?: PaginationState;
  isLoading: boolean;
  emptyMessage?: string;
  onRowClick?: (item: T) => void;
  rowKey?: (item: T, index: number) => string;
}

function sortData<T>(data: T[], sortKey: string, sortDirection: SortDirection): T[] {
  return [...data].sort((a, b) => {
    const aVal = (a as Record<string, unknown>)[sortKey];
    const bVal = (b as Record<string, unknown>)[sortKey];
    if (aVal == null && bVal == null) return 0;
    if (aVal == null) return 1;
    if (bVal == null) return -1;
    let cmp = 0;
    if (typeof aVal === 'number' && typeof bVal === 'number') {
      cmp = aVal - bVal;
    } else {
      cmp = String(aVal).localeCompare(String(bVal), 'ko');
    }
    return sortDirection === 'asc' ? cmp : -cmp;
  });
}

function SortIcon({ direction, active }: { direction?: SortDirection; active: boolean }) {
  return (
    <span
      style={{
        marginLeft: '6px',
        display: 'inline-flex',
        flexDirection: 'column',
        gap: '1px',
        verticalAlign: 'middle',
        position: 'relative',
        top: '-1px',
      }}
    >
      <svg width="8" height="5" viewBox="0 0 8 5" fill={active && direction === 'asc' ? '#111827' : '#d1d5db'}>
        <path d="M4 0L7.5 5H0.5L4 0Z" />
      </svg>
      <svg width="8" height="5" viewBox="0 0 8 5" fill={active && direction === 'desc' ? '#111827' : '#d1d5db'}>
        <path d="M4 5L0.5 0H7.5L4 5Z" />
      </svg>
    </span>
  );
}

function DataTablePagination({ page, totalPages, onPageChange }: PaginationState) {
  if (totalPages <= 1) return null;

  return (
    <nav
      aria-label="pagination"
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        gap: '4px',
        padding: '16px 0',
        borderTop: '1px solid #f3f4f6',
      }}
    >
      <button
        aria-label="이전 페이지"
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        style={{
          padding: '6px 14px',
          borderRadius: '6px',
          border: '1px solid #e5e7eb',
          backgroundColor: '#fff',
          cursor: page === 0 ? 'not-allowed' : 'pointer',
          opacity: page === 0 ? 0.4 : 1,
          fontSize: '0.8125rem',
          color: '#374151',
        }}
      >
        이전
      </button>
      {buildPageNumbers(page, totalPages).map((item, idx) =>
        item === '...' ? (
          <span
            key={`ellipsis-${idx}`}
            style={{ padding: '4px 8px', color: '#9ca3af', fontSize: '0.8125rem' }}
          >
            ...
          </span>
        ) : (
          <button
            key={`page-${item}`}
            aria-label={`${item + 1} 페이지`}
            aria-current={item === page ? 'page' : undefined}
            onClick={() => onPageChange(item)}
            style={{
              padding: '6px 12px',
              borderRadius: '6px',
              border: item === page ? 'none' : '1px solid transparent',
              backgroundColor: item === page ? '#1A1A2E' : 'transparent',
              color: item === page ? '#fff' : '#374151',
              fontWeight: item === page ? 600 : 400,
              cursor: 'pointer',
              fontSize: '0.8125rem',
            }}
          >
            {item + 1}
          </button>
        ),
      )}
      <button
        aria-label="다음 페이지"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        style={{
          padding: '6px 14px',
          borderRadius: '6px',
          border: '1px solid #e5e7eb',
          backgroundColor: '#fff',
          cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
          opacity: page >= totalPages - 1 ? 0.4 : 1,
          fontSize: '0.8125rem',
          color: '#374151',
        }}
      >
        다음
      </button>
    </nav>
  );
}

export function DataTable<T>({
  columns,
  data,
  pagination,
  isLoading,
  emptyMessage = '데이터가 없습니다.',
  onRowClick,
  rowKey,
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDirection('asc');
    }
  };

  const sortedData = useMemo(() => {
    if (!sortKey) return data;
    return sortData(data, sortKey, sortDirection);
  }, [data, sortKey, sortDirection]);

  if (isLoading) {
    return <LoadingSpinner />;
  }

  if (data.length === 0) {
    return <EmptyState message={emptyMessage} />;
  }

  function getRowKey(item: T, index: number): string {
    if (rowKey) return rowKey(item, index);
    const record = item as Record<string, unknown>;
    if (record['id'] != null) return String(record['id']);
    return columns.map((col) => String(record[col.key] ?? '')).join('::') || String(index);
  }

  return (
    <div
      style={{
        backgroundColor: '#fff',
        borderRadius: '12px',
        border: '1px solid #e5e7eb',
        overflow: 'hidden',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
      }}
    >
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ backgroundColor: '#f9fafb' }}>
            {columns.map((col) => (
              <th
                key={col.key}
                onClick={col.sortable ? () => handleSort(col.key) : undefined}
                style={{
                  padding: '10px 20px',
                  textAlign: 'left',
                  borderBottom: '1px solid #e5e7eb',
                  fontWeight: 600,
                  fontSize: '0.8125rem',
                  color: '#6b7280',
                  textTransform: 'uppercase' as const,
                  letterSpacing: '0.03em',
                  cursor: col.sortable ? 'pointer' : 'default',
                  userSelect: col.sortable ? 'none' : undefined,
                }}
              >
                {col.header}
                {col.sortable && <SortIcon direction={sortDirection} active={sortKey === col.key} />}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedData.map((item, index) => (
            <tr
              key={getRowKey(item, index)}
              onClick={() => onRowClick?.(item)}
              style={{
                cursor: onRowClick ? 'pointer' : 'default',
                borderBottom: index < data.length - 1 ? '1px solid #f3f4f6' : 'none',
                transition: 'background-color 0.1s',
              }}
              onMouseEnter={(e) => { if (onRowClick) e.currentTarget.style.backgroundColor = '#f9fafb'; }}
              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = ''; }}
            >
              {columns.map((col) => (
                <td
                  key={col.key}
                  style={{
                    padding: '10px 20px',
                    fontSize: '0.875rem',
                    color: '#111827',
                  }}
                >
                  {col.render
                    ? col.render(item)
                    : String((item as Record<string, unknown>)[col.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {pagination && <DataTablePagination {...pagination} />}
    </div>
  );
}
