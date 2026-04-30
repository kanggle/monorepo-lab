'use client';

import { useEffect } from 'react';
import { Button } from '@/shared/ui/button';

export default function ConsoleError({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => {
    // eslint-disable-next-line no-console
    console.error(JSON.stringify({ level: 'error', msg: 'console_error_boundary', err: String(error) }));
  }, [error]);
  return (
    <div className="flex flex-col items-start gap-3 p-6">
      <h2 className="text-lg font-semibold">문제가 발생했습니다</h2>
      <p className="text-sm text-muted-foreground">{error.message}</p>
      <Button onClick={reset}>다시 시도</Button>
    </div>
  );
}
