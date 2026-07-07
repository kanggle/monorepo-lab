import { apiClient } from '@/shared/api/client';
import type { SubscribableDomainKey } from '../api/domains';

/**
 * Pure action model for the subscriptions surface (TASK-PC-FE-211 split from
 * `SubscriptionsScreen`). Holds the {@link PendingAction} shape, the dialog-copy
 * resolver {@link actionMeta}, and the mutation dispatcher {@link runMutation} —
 * no JSX, so the container + row components share one authoritative contract.
 */

export type ActionKind = 'subscribe' | 'resume' | 'suspend' | 'cancel';

export interface PendingAction {
  domainKey: SubscribableDomainKey;
  label: string;
  kind: ActionKind;
}

export interface ActionMeta {
  title: string;
  description: string;
  confirmLabel: string;
  destructive: boolean;
  warning?: string;
}

export function actionMeta(
  kind: ActionKind,
  label: string,
  tenant: string,
): ActionMeta {
  switch (kind) {
    case 'subscribe':
      return {
        title: `${label} 구독`,
        description: `이 조직(${tenant})에 ${label} 도메인을 활성화합니다. 구독하면 콘솔에서 바로 사용할 수 있습니다.`,
        confirmLabel: '구독',
        destructive: false,
      };
    case 'resume':
      return {
        title: `${label} 재개`,
        description: `중지·해지되었던 ${label} 구독을 다시 활성화합니다.`,
        confirmLabel: '재개',
        destructive: false,
      };
    case 'suspend':
      return {
        title: `${label} 일시중지`,
        description: `${label} 도메인 접근을 중지합니다. 운영자 배정·권한은 보존되며 재개 시 즉시 복구됩니다 (ADR-023 평면 분리).`,
        confirmLabel: '일시중지',
        destructive: true,
        warning: '중지 중에는 이 도메인이 카탈로그·엔타이틀먼트에서 제외됩니다.',
      };
    case 'cancel':
      return {
        title: `${label} 해지`,
        description: `${label} 구독을 해지합니다.`,
        confirmLabel: '해지',
        destructive: true,
        warning: '해지 후에는 이 화면에서 재개할 수 없습니다 (상태 확인 필요).',
      };
  }
}

export async function runMutation(
  action: PendingAction,
  reason: string,
): Promise<void> {
  if (action.kind === 'subscribe') {
    await apiClient.post('/api/subscriptions', {
      domainKey: action.domainKey,
      reason,
    });
    return;
  }
  const status =
    action.kind === 'suspend'
      ? 'SUSPENDED'
      : action.kind === 'cancel'
        ? 'CANCELLED'
        : 'ACTIVE';
  await apiClient.patch(
    `/api/subscriptions/${encodeURIComponent(action.domainKey)}/status`,
    { status, reason },
  );
}
