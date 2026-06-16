import type { ReactNode } from 'react';
import { ApiError } from '@/shared/api/errors';
import type { AccountSummary } from '../api/types';

/**
 * `AccountsScreen` shared model + pure copy/util helpers (extracted from the
 * container in TASK-PC-FE-111 — behavior-preserving god-file split). No hooks;
 * `.tsx` only because {@link accountActionDescription} returns JSX.
 */

export type ActionKind =
  | 'lock'
  | 'unlock'
  | 'revoke-session'
  | 'gdpr-delete'
  | 'bulk-lock';

export interface PendingAction {
  kind: ActionKind;
  /** Single-target ops. */
  account?: AccountSummary;
  /** bulk-lock targets. */
  accountIds?: string[];
  /** Stable across retries of THIS confirmed action. */
  idempotencyKey: string;
}

export interface AccountsQuery {
  email?: string;
  page: number;
  size: number;
}

export const ACTION_META: Record<
  ActionKind,
  { title: string; confirm: string; destructive: boolean }
> = {
  lock: { title: '계정 잠금', confirm: '잠금', destructive: true },
  unlock: { title: '계정 잠금 해제', confirm: '잠금 해제', destructive: true },
  'revoke-session': {
    title: '모든 세션 강제 종료',
    confirm: '세션 종료',
    destructive: true,
  },
  'gdpr-delete': {
    title: 'GDPR 삭제 (되돌릴 수 없음)',
    confirm: '영구 삭제',
    destructive: true,
  },
  'bulk-lock': {
    title: '선택 계정 일괄 잠금',
    confirm: '일괄 잠금',
    destructive: true,
  },
};

/**
 * A 403 PERMISSION_DENIED (account.read absent) is an AUTHORIZATION denial, not
 * a transient outage — suppress the degraded banner so the empty-state shows the
 * dedicated 권한 없음 message instead (TASK-MONO-202). Initial-load denials are
 * caught at the page level; this covers a mid-session permission revocation
 * surfaced on a client re-query.
 */
export function isForbidden(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.status === 403 || error.code === 'PERMISSION_DENIED')
  );
}

export function newIdemKey(): string {
  const g = globalThis as unknown as { crypto?: { randomUUID?: () => string } };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

/** The {@link ConfirmActionDialog} description copy for a pending action. */
export function accountActionDescription(pending: PendingAction): ReactNode {
  return pending.kind === 'bulk-lock' ? (
    <>
      선택한 <strong>{pending.accountIds?.length ?? 0}</strong>개 계정을 일괄
      잠금합니다. 계정별 결과가 표시되며 일부 실패해도 나머지는 처리됩니다.
    </>
  ) : pending.kind === 'gdpr-delete' ? (
    <>
      <strong>{pending.account?.email}</strong> 계정을 GDPR 삭제 합니다. 이
      작업은 <strong>되돌릴 수 없으며</strong> 개인정보가 즉시 마스킹됩니다.
    </>
  ) : (
    <>
      <strong>{pending.account?.email}</strong> 계정에 대해 이 작업을
      수행합니다.
    </>
  );
}
