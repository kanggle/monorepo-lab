import type { ReactNode } from 'react';

/**
 * Error placeholder. Used by `error.tsx` and inline by features that handle
 * `ApiError` themselves.
 */
export function ErrorState({
  title = '문제가 발생했습니다',
  description,
  action,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center gap-3 rounded-lg border border-accent-300/60 bg-accent-50 py-12 text-center"
    >
      <p className="text-base font-semibold text-accent-700">{title}</p>
      {description ? <p className="max-w-md text-sm text-ink-600">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
