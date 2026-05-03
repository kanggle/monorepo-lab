import type { ReactNode } from 'react';

/** Empty state for paginated lists / search results. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-ink-200 bg-ink-50/40 py-16 text-center">
      <p className="text-base font-semibold text-ink-800">{title}</p>
      {description ? <p className="max-w-md text-sm text-ink-600">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
