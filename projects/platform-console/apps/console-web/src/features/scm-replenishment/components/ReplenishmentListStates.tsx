import { messageForCode } from '@/shared/api/errors';

export interface ReplenishmentListStatesProps {
  forbidden: boolean;
  rateLimited: boolean;
  degraded: boolean;
}

/**
 * The forbidden / rate-limited / degraded / empty status banners for
 * {@link ReplenishmentScreen} (TASK-PC-FE-215 split) — rendered in place of the
 * suggestion table when the list read is blocked or returns no rows (the parent
 * owns the loaded/non-empty branch via {@link ReplenishmentTable}). Rendered
 * ONLY when one of those states holds, so exactly one banner renders. Pure
 * presentation — markup + testids preserved verbatim (`repl-forbidden` /
 * `repl-ratelimited` / `repl-degraded` / `repl-empty`).
 */
export function ReplenishmentListStates({
  forbidden,
  rateLimited,
  degraded,
}: ReplenishmentListStatesProps) {
  if (forbidden) {
    return (
      <div
        role="status"
        data-testid="repl-forbidden"
        className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
      >
        {messageForCode('TENANT_FORBIDDEN')}
      </div>
    );
  }
  if (rateLimited) {
    return (
      <div
        role="status"
        data-testid="repl-ratelimited"
        className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
      >
        {messageForCode('RATE_LIMIT_EXCEEDED')}
      </div>
    );
  }
  if (degraded) {
    return (
      <div
        role="status"
        data-testid="repl-degraded"
        className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
      >
        scm 보충 추천 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
        기능은 계속 사용할 수 있습니다.
      </div>
    );
  }
  return (
    <p className="text-sm text-muted-foreground" data-testid="repl-empty">
      표시할 보충 추천이 없습니다.
    </p>
  );
}
