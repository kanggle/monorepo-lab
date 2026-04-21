'use client';

const PAGE_SIZE_OPTIONS = [10, 20, 50];

interface PaginationNavProps {
  page: number;
  totalPages: number;
  size: number;
  onPageChange: (newPage: number) => void;
  onSizeChange: (newSize: number) => void;
  pageSizeSelectId?: string;
}

export function PaginationNav({
  page,
  totalPages,
  size,
  onPageChange,
  onSizeChange,
  pageSizeSelectId = 'pageSize',
}: PaginationNavProps) {
  return (
    <nav
      aria-label="페이지네이션"
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginTop: 'var(--space-8)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
        <label htmlFor={pageSizeSelectId} className="label" style={{ marginBottom: 0 }}>페이지 크기:</label>
        <select
          id={pageSizeSelectId}
          value={size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          className="input"
          style={{ width: 'auto', padding: 'var(--space-1) var(--space-2)' }}
        >
          {PAGE_SIZE_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>{opt}개</option>
          ))}
        </select>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          aria-label="이전 페이지"
          className="btn"
        >
          이전
        </button>
        <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
          {page + 1} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="다음 페이지"
          className="btn"
        >
          다음
        </button>
      </div>
    </nav>
  );
}
