'use client';

import { messageForCode } from '@/shared/api/errors';

/**
 * Presentational status notices for the operators-management surface
 * (TASK-PC-FE-209 split of `OperatorsScreen`). Pure — no state / effects;
 * the container decides which notice (if any) to render from the list-query
 * outcome and passes the resolved error `code` down.
 *
 * `OperatorsPermissionDenied` — the LIST read returned 403 (permission /
 * tenant-scope). The whole section renders as inline "not permitted", never
 * a crash, never a re-login loop. `OperatorsDegradedNotice` — the list read
 * failed with a 5xx / transport error → the section degrades only (the
 * console shell stays intact).
 */

export function OperatorsPermissionDenied({ code }: { code?: string }) {
  return (
    <div
      role="status"
      data-testid="operators-permission-denied"
      className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
    >
      {code === 'TENANT_SCOPE_DENIED'
        ? messageForCode('TENANT_SCOPE_DENIED')
        : messageForCode('OPERATOR_MANAGE_REQUIRED')}
    </div>
  );
}

export function OperatorsDegradedNotice() {
  return (
    <div
      role="status"
      data-testid="operators-degraded"
      className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
    >
      운영자 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
      계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
    </div>
  );
}
