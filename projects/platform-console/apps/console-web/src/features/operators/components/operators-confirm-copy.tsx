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

export type PendingKind =
  | 'create'
  | 'edit-roles'
  | 'change-status'
  // TASK-PC-FE-157 — tenant-assignment lifecycle (create / remove the
  // (operator, active-tenant) `operator_tenant_assignment` row).
  | 'assign'
  | 'unassign';

export interface PendingAction {
  kind: PendingKind;
  operator?: OperatorSummary;
  /** create draft. */
  draft?: CreateOperatorInput;
  /** change-status target value. */
  nextStatus?: OperatorStatus;
  /** create only — stable across retries of THIS confirmed create. */
  idempotencyKey?: string;
  /** assign only — the free-text target operatorId (the target operator may
   *  be outside the active-tenant list scope, so it is not an OperatorSummary
   *  row). */
  assignOperatorId?: string;
  /** assign / unassign — the target tenant (= the active tenant slug). */
  tenantId?: string;
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
  switch (pending.kind) {
    case 'create':
      return '운영자 계정 생성 (특권 작업)';
    case 'edit-roles':
      return '운영자 역할 변경 (특권 작업)';
    case 'assign':
      return '테넌트 배정 (특권 작업)';
    case 'unassign':
      return '테넌트 배정 해제 (특권 작업)';
    default:
      return pending.nextStatus === 'SUSPENDED'
        ? '운영자 정지 (특권 작업)'
        : '운영자 활성화';
  }
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
  ) : pending.kind === 'assign' ? (
    <>
      운영자{' '}
      <strong>{pending.assignOperatorId}</strong> 를{' '}
      <strong>{pending.tenantId}</strong> 테넌트에 배정합니다. 이 운영자는 해당
      테넌트 범위의 역할로 로그인·조작할 수 있게 됩니다 (부서 범위는 배정 후
      &ldquo;조직 스코프&rdquo;로 좁힐 수 있습니다). 사유가 요구됩니다.
    </>
  ) : pending.kind === 'unassign' ? (
    <>
      <strong>{pending.operator?.email}</strong> 운영자의{' '}
      <strong>{pending.tenantId}</strong> 테넌트 배정을 해제합니다. 이후 이
      운영자는 (홈 테넌트가 아니라면) 해당 테넌트에 접근할 수 없습니다. 사유가
      요구됩니다.
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
  switch (pending.kind) {
    case 'create':
      return '운영자 계정 생성';
    case 'edit-roles':
      return '역할 변경';
    case 'assign':
      return '배정';
    case 'unassign':
      return '배정 해제';
    default:
      return pending.nextStatus === 'SUSPENDED' ? '정지' : '활성화';
  }
}
