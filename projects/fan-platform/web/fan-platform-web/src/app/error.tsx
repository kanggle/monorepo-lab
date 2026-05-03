'use client';
import { ErrorState } from '@/shared/ui/ErrorState';
import { Button } from '@/shared/ui/Button';

/** App-level error boundary. */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="mx-auto max-w-lg p-8">
      <ErrorState
        title="페이지를 표시할 수 없습니다"
        description={error.message ?? '잠시 후 다시 시도해주세요.'}
        action={<Button onClick={() => reset()}>다시 시도</Button>}
      />
    </main>
  );
}
