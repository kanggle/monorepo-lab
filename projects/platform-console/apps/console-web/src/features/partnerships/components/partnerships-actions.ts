import { apiClient } from '@/shared/api/client';
import type { Partnership, ScopeSet } from '../api/types';

/**
 * Pure action model for the partnerships surface (TASK-PC-FE-211 split from
 * `PartnershipsScreen`). Holds the {@link PendingAction} shape, the mutation
 * dispatcher {@link runMutation}, the dialog-copy resolver {@link actionMeta},
 * the scope-input parsers, and the status label map — no JSX, so the container
 * + row + participant components share one authoritative action contract.
 */

export type ActionKind =
  | 'invite'
  | 'accept'
  | 'suspend'
  | 'reactivate'
  | 'terminate'
  | 'participant-add'
  | 'participant-remove';

export interface PendingAction {
  kind: ActionKind;
  partnershipId?: string;
  operatorId?: string;
  partnerTenantId?: string;
  delegatedScope?: ScopeSet;
  participantScope?: ScopeSet | null;
  /** Human label for the dialog title (the other-side tenant, or operator id). */
  label: string;
  destructive: boolean;
}

export function parseList(raw: string): string[] {
  return raw
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
}

/** Build a ScopeSet from comma inputs, or `null` when both are empty (⟺ full
 *  delegatedScope net-zero default for a participant). */
export function scopeOrNull(domains: string, roles: string): ScopeSet | null {
  const d = parseList(domains);
  const r = parseList(roles);
  if (d.length === 0 && r.length === 0) return null;
  return { domains: d, roles: r };
}

export async function runMutation(
  a: PendingAction,
  reason: string,
): Promise<void> {
  const base = a.partnershipId
    ? `/api/partnerships/${encodeURIComponent(a.partnershipId)}`
    : '/api/partnerships';
  switch (a.kind) {
    case 'invite':
      await apiClient.post('/api/partnerships', {
        partnerTenantId: a.partnerTenantId,
        delegatedScope: a.delegatedScope,
        reason,
      });
      return;
    case 'accept':
      await apiClient.post(`${base}/accept`, { reason });
      return;
    case 'suspend':
      await apiClient.post(`${base}/suspend`, { reason });
      return;
    case 'reactivate':
      await apiClient.post(`${base}/reactivate`, { reason });
      return;
    case 'terminate':
      await apiClient.post(`${base}/terminate`, { reason });
      return;
    case 'participant-add':
      await apiClient.post(
        `${base}/participants/${encodeURIComponent(a.operatorId!)}`,
        a.participantScope
          ? { participantScope: a.participantScope, reason }
          : { reason },
      );
      return;
    case 'participant-remove':
      // DELETE carries the audit reason in the body (the proxy DELETE handler
      // reads it); apiClient.delete forwards `opts.body`.
      await apiClient.delete(
        `${base}/participants/${encodeURIComponent(a.operatorId!)}`,
        { body: { reason } },
      );
      return;
  }
}

export interface ActionMeta {
  title: string;
  description: string;
  confirmLabel: string;
}

export function actionMeta(a: PendingAction): ActionMeta {
  switch (a.kind) {
    case 'invite':
      return {
        title: `${a.label} 파트너십 초대`,
        description: `파트너 조직(${a.label})에 위임 범위를 부여하는 파트너십을 생성합니다. 상대 조직이 수락하면 활성화됩니다.`,
        confirmLabel: '초대',
      };
    case 'accept':
      return {
        title: `${a.label} 파트너십 수락`,
        description: `${a.label} 조직이 보낸 파트너십 초대를 수락하고 활성화합니다.`,
        confirmLabel: '수락',
      };
    case 'suspend':
      return {
        title: `${a.label} 파트너십 일시중지`,
        description: `${a.label} 파트너십을 일시중지합니다. 재개하기 전까지 참여자의 파생 접근이 즉시 소멸합니다 (D6).`,
        confirmLabel: '일시중지',
      };
    case 'reactivate':
      return {
        title: `${a.label} 파트너십 재개`,
        description: `일시중지된 ${a.label} 파트너십을 다시 활성화합니다.`,
        confirmLabel: '재개',
      };
    case 'terminate':
      return {
        title: `${a.label} 파트너십 종료`,
        description: `${a.label} 파트너십을 종료합니다. 종료는 되돌릴 수 없으며 모든 참여자의 파생 접근이 소멸합니다 (D6).`,
        confirmLabel: '종료',
      };
    case 'participant-add':
      return {
        title: `참여자 배정 (${a.operatorId})`,
        description: `운영자 ${a.operatorId} 를 이 파트너십의 참여자로 배정합니다. 범위를 지정하지 않으면 위임 범위 전체가 적용됩니다.`,
        confirmLabel: '배정',
      };
    case 'participant-remove':
      return {
        title: `참여자 해제 (${a.operatorId})`,
        description: `운영자 ${a.operatorId} 의 참여자 배정을 해제합니다. 해당 운영자의 파생 접근이 즉시 소멸합니다.`,
        confirmLabel: '해제',
      };
  }
}

export const STATUS_LABEL: Record<Partnership['status'], string> = {
  PENDING: '대기 중',
  ACTIVE: '활성',
  SUSPENDED: '일시중지',
  TERMINATED: '종료됨',
};
