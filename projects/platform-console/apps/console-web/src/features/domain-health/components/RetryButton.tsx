'use client';

import { Button } from '@/shared/ui/Button';
import { useDomainHealth } from '../hooks/use-domain-health';
import type { DomainHealth } from '../api/types';

/**
 * Domain-health explicit retry button (TASK-PC-FE-013).
 *
 * THE ONLY CLIENT COMPONENT in `features/domain-health/components/`
 * (`'use client'`). The screen + per-card + degrade-banner are server
 * components by design (architecture.md § Server vs Client Components).
 * This button consumes the React Query hook and triggers an explicit
 * refetch on click — there is NO auto-poll / interval / focus refetch
 * (§ 2.4.4 / § 2.4.9 invariant).
 */

export interface RetryButtonProps {
  /** The SSR-composed initial envelope (passed from the page) — seeds
   *  React Query's `initialData` so the first render does not refetch. */
  initial?: DomainHealth;
  /** Optional override label (defaults to Korean copy). */
  label?: string;
  /** Optional data-testid suffix for snapshot tests. */
  testidSuffix?: string;
}

export function RetryButton({ initial, label, testidSuffix }: RetryButtonProps) {
  const health = useDomainHealth(initial);
  const testid = testidSuffix
    ? `domain-health-retry-${testidSuffix}`
    : 'domain-health-retry';
  const text = health.isFetching
    ? (label ? `${label}…` : '새로고침 중…')
    : (label ?? '다시 시도');
  return (
    <Button
      type="button"
      variant="secondary"
      onClick={() => health.refetch()}
      disabled={health.isFetching}
      data-testid={testid}
    >
      {text}
    </Button>
  );
}
