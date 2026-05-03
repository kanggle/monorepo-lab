import Link from 'next/link';

/**
 * Stateless pagination. Consumers control the URL — passes `?page=` query
 * params so the server component can re-render without client state.
 */
export function Pagination({
  page,
  totalPages,
  hrefFor,
}: {
  page: number;
  totalPages: number;
  hrefFor: (page: number) => string;
}) {
  if (totalPages <= 1) return null;
  const prev = Math.max(0, page - 1);
  const next = Math.min(totalPages - 1, page + 1);
  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-center gap-2 py-6">
      <Link
        aria-disabled={page === 0}
        className={[
          'rounded-md border border-ink-200 px-3 py-1.5 text-sm',
          page === 0 ? 'pointer-events-none text-ink-400' : 'text-ink-800 hover:bg-ink-50',
        ].join(' ')}
        href={hrefFor(prev)}
      >
        이전
      </Link>
      <span className="text-sm text-ink-600" aria-current="page">
        {page + 1} / {totalPages}
      </span>
      <Link
        aria-disabled={page >= totalPages - 1}
        className={[
          'rounded-md border border-ink-200 px-3 py-1.5 text-sm',
          page >= totalPages - 1
            ? 'pointer-events-none text-ink-400'
            : 'text-ink-800 hover:bg-ink-50',
        ].join(' ')}
        href={hrefFor(next)}
      >
        다음
      </Link>
    </nav>
  );
}
