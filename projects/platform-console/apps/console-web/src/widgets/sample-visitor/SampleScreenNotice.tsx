'use client';

import { usePathname } from 'next/navigation';
import { messageForCode } from '@/shared/api/errors';
import { SAMPLE_NOT_READY } from '@/shared/sample/codes';
import { screenStatusFor } from '@/shared/sample/coverage';

/**
 * «이 화면의 샘플 데이터는 준비 중입니다» on a screen whose sample data is still
 * `pending` in the ledger (ADR-MONO-074 A9).
 *
 * The screen's sections degrade on their own — each core answers a pending GET
 * with `503 SAMPLE_NOT_READY`, and the existing section-degrade state takes
 * over. 🔴 But those states store `degraded: true`, not the code, so a section
 * cannot tell «not ready» from «broken». The shell can: it knows the route and
 * the ledger. The copy still comes from the code (`messageForCode`).
 *
 * A client component because the `(console)` layout is NOT re-rendered on a
 * client-side navigation — a server-read pathname would go stale.
 */
export function SampleScreenNotice() {
  const pathname = usePathname() ?? '';
  if (screenStatusFor(pathname) !== 'pending') return null;
  return (
    <div
      role="status"
      data-testid="sample-screen-not-ready"
      className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
    >
      {messageForCode(SAMPLE_NOT_READY)}
    </div>
  );
}
