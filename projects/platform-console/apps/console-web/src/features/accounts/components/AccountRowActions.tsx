'use client';

import { Button } from '@/shared/ui/Button';
import type { AccountSummary } from '../api/types';
import type { ActionKind } from './accounts-screen-helpers';

/**
 * The per-row action button cluster for one account (TASK-PC-FE-210 split of
 * `AccountsTable`): 잠금 / 잠금 해제 / 세션 종료 / 내보내기 / GDPR 삭제.
 * Presentational — the single-target op opener + the export handler live in the
 * container and arrive via props. Every `data-testid` (`action-*-{id}`) / label
 * / class is byte-identical.
 */
export interface AccountRowActionsProps {
  account: AccountSummary;
  /** Single-target row ops (lock / unlock / revoke-session / gdpr-delete). */
  onAction: (kind: ActionKind, account: AccountSummary) => void;
  onExport: (account: AccountSummary) => void;
}

export function AccountRowActions({
  account,
  onAction,
  onExport,
}: AccountRowActionsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onAction('lock', account)}
        data-testid={`action-lock-${account.id}`}
      >
        잠금
      </Button>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onAction('unlock', account)}
        data-testid={`action-unlock-${account.id}`}
      >
        잠금 해제
      </Button>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onAction('revoke-session', account)}
        data-testid={`action-revoke-${account.id}`}
      >
        세션 종료
      </Button>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onExport(account)}
        data-testid={`action-export-${account.id}`}
      >
        내보내기
      </Button>
      <Button
        variant="secondary"
        size="sm"
        className="text-destructive"
        onClick={() => onAction('gdpr-delete', account)}
        data-testid={`action-gdpr-${account.id}`}
      >
        GDPR 삭제
      </Button>
    </div>
  );
}
