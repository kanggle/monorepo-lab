'use client';

import { ELEVATED_ROLE } from '../api/types';

/**
 * Role chips + status badge for the operators list. Role rendering is
 * forward-compatible: an unknown/future role (not in the producer enum)
 * still renders as a generic chip — never a crash
 * (console-integration-contract § 2.4.3 "Role tolerance" / task Edge Case).
 * The privilege-elevating role (`SUPER_ADMIN`) is visually distinct so an
 * operator's elevated privilege is obvious at a glance (security UX).
 */

export function OperatorRoleChips({ roles }: { roles: string[] }) {
  if (roles.length === 0) {
    return (
      <span
        data-testid="operator-roles-empty"
        className="text-xs text-muted-foreground"
      >
        역할 없음
      </span>
    );
  }
  return (
    <span className="flex flex-wrap gap-1">
      {roles.map((r) => {
        const elevated = r === ELEVATED_ROLE;
        return (
          <span
            key={r}
            data-testid={`operator-role-chip-${r}`}
            data-elevated={elevated ? 'true' : 'false'}
            className={
              elevated
                ? 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs font-medium text-destructive'
                : 'rounded bg-muted px-1.5 py-0.5 text-xs text-foreground'
            }
          >
            {r}
          </span>
        );
      })}
    </span>
  );
}

export function OperatorStatusBadge({ status }: { status: string }) {
  const suspended = status === 'SUSPENDED';
  return (
    <span
      data-testid="operator-status"
      className={
        suspended
          ? 'rounded bg-destructive/15 px-2 py-0.5 text-xs font-medium text-destructive'
          : 'rounded bg-muted px-2 py-0.5 text-xs text-foreground'
      }
    >
      {status}
    </span>
  );
}
