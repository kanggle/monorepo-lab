'use client';

import { Button } from '@/shared/ui/Button';
import { useOperatorOverview } from '../hooks/use-operator-overview';
import type { OperatorOverview } from '../api/operator-overview-types';

/**
 * Operator-overview explicit retry button (TASK-PC-FE-011).
 *
 * THE ONLY CLIENT COMPONENT in `features/operator-overview/components/`
 * (`'use client'`). The screen + per-card + degrade-banner are server
 * components by design (architecture.md § Server vs Client Components;
 * AC-24). This button consumes the React Query hook and triggers an
 * explicit refetch on click — there is NO auto-poll / interval / focus
 * refetch (§ 2.4.4 / § 2.4.9 invariant).
 *
 * The button is intentionally minimal — it owns ONLY the refetch
 * action; the visual treatment of degraded cards lives in the server
 * components. This keeps the client-component surface as narrow as
 * possible (server-component-first).
 */

export interface RetryButtonProps {
  /** The SSR-composed initial envelope (passed from the page) — seeds
   *  React Query's `initialData` so the first render does not refetch. */
  initial?: OperatorOverview;
  /** Optional override label (defaults to Korean copy). */
  label?: string;
  /** Optional data-testid suffix for snapshot tests. */
  testidSuffix?: string;
}

export function RetryButton({ initial, label, testidSuffix }: RetryButtonProps) {
  const overview = useOperatorOverview(initial);
  const testid = testidSuffix
    ? `operator-overview-retry-${testidSuffix}`
    : 'operator-overview-retry';
  const text = overview.isFetching
    ? (label ? `${label}…` : '새로고침 중…')
    : (label ?? '다시 시도');
  return (
    <Button
      type="button"
      variant="secondary"
      onClick={() => overview.refetch()}
      disabled={overview.isFetching}
      data-testid={testid}
    >
      {text}
    </Button>
  );
}
