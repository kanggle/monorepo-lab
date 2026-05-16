'use client';

import { useEffect } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * `(console)` segment error boundary. Keeps the shell from blank-crashing on
 * an unexpected render error (integration-heavy resilience — the registry
 * degraded path is handled in-page; this catches everything else).
 */
export default function ConsoleError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // eslint-disable-next-line no-console
    console.error(JSON.stringify({ level: 'error', msg: 'console_render_error', digest: error.digest }));
  }, [error]);

  return (
    <div
      role="alert"
      className="mx-auto max-w-md rounded-lg border border-border bg-background p-8 text-center"
    >
      <h2 className="text-lg font-semibold text-foreground">
        문제가 발생했습니다
      </h2>
      <p className="mt-2 text-sm text-muted-foreground">
        화면을 표시하는 중 오류가 발생했습니다. 다시 시도해주세요.
      </p>
      <Button className="mt-6" onClick={reset}>
        다시 시도
      </Button>
    </div>
  );
}
