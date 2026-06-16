import type { ReactNode } from 'react';
import {
  ELEVATED_ROLE,
  type OperatorSummary,
  type OperatorStatus,
  type CreateOperatorInput,
} from '../api/types';

/**
 * Pending-action model + the `OperatorConfirmDialog` copy builders for the
 * operators-management screen (TASK-PC-FE-105 split). The title / description /
 * confirm-label are pure functions of the pending action; extracting them keeps
 * the container's dialog mount thin. The description returns rich JSX
 * (`ReactNode`).
 */

export type PendingKind = 'create' | 'edit-roles' | 'change-status';

export interface PendingAction {
  kind: PendingKind;
  operator?: OperatorSummary;
  /** create draft. */
  draft?: CreateOperatorInput;
  /** change-status target value. */
  nextStatus?: OperatorStatus;
  /** create only — stable across retries of THIS confirmed create. */
  idempotencyKey?: string;
  /** Privilege-high → elevated confirm copy. */
  elevated: boolean;
}

export function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export function operatorConfirmTitle(pending: PendingAction): string {
  return pending.kind === 'create'
    ? '운영자 생성 (특권 작업)'
    : pending.kind === 'edit-roles'
      ? '운영자 역할 변경 (특권 작업)'
      : pending.nextStatus === 'SUSPENDED'
        ? '운영자 정지 (특권 작업)'
        : '운영자 활성화';
}

export function operatorConfirmDescription(pending: PendingAction): ReactNode {
  return pending.kind === 'create' ? (
    <>
      <strong>{pending.draft?.email}</strong> 운영자를{' '}
      <strong>{pending.draft?.tenantId}</strong> 테넌트에 생성합니다.
      {pending.draft?.roles.includes(ELEVATED_ROLE) && (
        <>
          {' '}
          이 운영자는 <strong>SUPER_ADMIN</strong> 특권을 가집니다.
        </>
      )}{' '}
      이 작업은 운영자 권한 부여에 해당하므로 사유가 요구됩니다.
    </>
  ) : pending.kind === 'edit-roles' ? (
    <>
      <strong>{pending.operator?.email}</strong> 운영자의 역할을 전체
      교체합니다. 역할을 모두 비우면 이 운영자는 어떤 운영 권한도 갖지 않으며,{' '}
      <strong>SUPER_ADMIN</strong> 부여는 특권 상승입니다. 사유가 요구됩니다.
    </>
  ) : pending.nextStatus === 'SUSPENDED' ? (
    <>
      <strong>{pending.operator?.email}</strong> 운영자를{' '}
      <strong>정지(SUSPENDED)</strong> 합니다. 정지 시 해당 운영자의 모든
      세션이 즉시 종료됩니다. 사유가 요구됩니다.
    </>
  ) : (
    <>
      <strong>{pending.operator?.email}</strong> 운영자를 활성화(ACTIVE)합니다.
      사유가 요구됩니다.
    </>
  );
}

export function operatorConfirmLabel(pending: PendingAction): string {
  return pending.kind === 'create'
    ? '운영자 생성'
    : pending.kind === 'edit-roles'
      ? '역할 변경'
      : pending.nextStatus === 'SUSPENDED'
        ? '정지'
        : '활성화';
}
