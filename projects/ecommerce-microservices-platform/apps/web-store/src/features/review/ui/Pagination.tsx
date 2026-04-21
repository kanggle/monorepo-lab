'use client';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (newPage: number) => void;
  ariaLabel?: string;
  style?: React.CSSProperties;
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  ariaLabel = '페이지네이션',
  style,
}: PaginationProps) {
  return (
    <nav
      aria-label={ariaLabel}
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        gap: 'var(--space-2)',
        ...style,
      }}
    >
      <button
        type="button"
        className="btn"
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        aria-label="이전 페이지"
      >
        이전
      </button>
      <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className="btn"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="다음 페이지"
      >
        다음
      </button>
    </nav>
  );
}
