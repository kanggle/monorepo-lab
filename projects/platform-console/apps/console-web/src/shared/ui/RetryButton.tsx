'use client';

import { Button } from '@/shared/ui/Button';

/**
 * Shared explicit-retry button shell (TASK-PC-FE-263).
 *
 * `features/domain-health` and `features/operator-overview` each
 * reimplemented the SAME button — same props shape, same disabled/label
 * logic, same JSX — differing only in which feature hook fed it and its
 * testid prefix. Per `platform/refactoring-policy.md` § Prioritization
 * duplication outranks naming, so the presentational shell is promoted
 * here (the `shared/ui/ConfirmDialog` precedent, TASK-PC-FE-262); each
 * feature keeps a thin wrapper that owns its own React Query hook
 * selection and testid string, and delegates rendering to this component.
 *
 * Presentational-only — does NOT call any data hook itself, so it stays a
 * leaf client component with no feature coupling.
 */

export interface RetryButtonProps {
  /** Whether a refetch is currently in flight (disables the button, swaps
   *  the label to its "…" variant). */
  isFetching: boolean;
  /** Click handler — the caller's hook `refetch()`. */
  onRetry: () => void;
  /** Optional override label (defaults to Korean copy). */
  label?: string;
  /** Fully-resolved testid string (the two features' schemes are not
   *  identical — callers pass the complete string, not a prefix). */
  testid: string;
}

export function RetryButton({ isFetching, onRetry, label, testid }: RetryButtonProps) {
  const text = isFetching
    ? (label ? `${label}…` : '새로고침 중…')
    : (label ?? '다시 시도');
  return (
    <Button
      type="button"
      variant="secondary"
      onClick={onRetry}
      disabled={isFetching}
      data-testid={testid}
    >
      {text}
    </Button>
  );
}
