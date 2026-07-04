'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiClient } from '@/shared/api/client';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { DomainSubscriptionRow } from '../lib/derive';
import type { SubscribableDomainKey } from '../api/domains';
import { SubscriptionConfirmDialog } from './SubscriptionConfirmDialog';

type ActionKind = 'subscribe' | 'resume' | 'suspend' | 'cancel';

interface PendingAction {
  domainKey: SubscribableDomainKey;
  label: string;
  kind: ActionKind;
}

interface ActionMeta {
  title: string;
  description: string;
  confirmLabel: string;
  destructive: boolean;
  warning?: string;
}

function actionMeta(kind: ActionKind, label: string, tenant: string): ActionMeta {
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

async function runMutation(
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

export interface SubscriptionsScreenProps {
  activeTenant: string;
  rows: DomainSubscriptionRow[];
}

export function SubscriptionsScreen({
  activeTenant,
  rows,
}: SubscriptionsScreenProps) {
  const router = useRouter();
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function open(action: PendingAction) {
    setPendingAction(action);
    setError(null);
  }
  function close() {
    if (pending) return;
    setPendingAction(null);
    setError(null);
  }

  async function confirm(reason: string) {
    if (!pendingAction) return;
    setPending(true);
    setError(null);
    try {
      await runMutation(pendingAction, reason);
      setPendingAction(null);
      router.refresh();
    } catch (e) {
      const code = e instanceof ApiError ? e.code : '';
      if (
        pendingAction.kind === 'subscribe' &&
        code === 'SUBSCRIPTION_ALREADY_EXISTS'
      ) {
        // The domain has a suspended/cancelled row (invisible to the catalog
        // read). Offer resume in place — the dialog switches to the resume
        // action; the producer's state machine still guards CANCELLED.
        setPendingAction({ ...pendingAction, kind: 'resume' });
        setError(messageForCode(code));
      } else {
        setError(
          e instanceof ApiError
            ? messageForCode(e.code, e.message)
            : '요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.',
        );
      }
    } finally {
      setPending(false);
    }
  }

  const meta = pendingAction
    ? actionMeta(pendingAction.kind, pendingAction.label, activeTenant)
    : null;

  return (
    <div>
      <ul className="divide-y divide-border rounded-md border border-border">
        {rows.map((row) => (
          <li
            key={row.key}
            data-testid={`subscription-row-${row.key}`}
            className="flex items-center justify-between gap-4 px-4 py-4"
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-foreground">
                {row.label}
              </p>
              <p
                data-testid={`subscription-status-${row.key}`}
                className={
                  row.state === 'ACTIVE'
                    ? 'mt-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400'
                    : 'mt-0.5 text-xs text-muted-foreground'
                }
              >
                {row.state === 'ACTIVE' ? '● 구독 중' : '○ 미구독'}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {row.state === 'ACTIVE' ? (
                <>
                  <button
                    type="button"
                    data-testid={`subscription-suspend-${row.key}`}
                    onClick={() =>
                      open({ domainKey: row.key, label: row.label, kind: 'suspend' })
                    }
                    className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
                  >
                    일시중지
                  </button>
                  <button
                    type="button"
                    data-testid={`subscription-cancel-${row.key}`}
                    onClick={() =>
                      open({ domainKey: row.key, label: row.label, kind: 'cancel' })
                    }
                    className="rounded-md border border-destructive/40 px-3 py-1.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10"
                  >
                    해지
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  data-testid={`subscription-subscribe-${row.key}`}
                  onClick={() =>
                    open({ domainKey: row.key, label: row.label, kind: 'subscribe' })
                  }
                  className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                >
                  구독
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>

      {pendingAction && meta && (
        <SubscriptionConfirmDialog
          title={meta.title}
          description={meta.description}
          confirmLabel={meta.confirmLabel}
          warning={meta.warning}
          destructive={meta.destructive}
          pending={pending}
          error={error}
          onConfirm={confirm}
          onCancel={close}
        />
      )}
    </div>
  );
}
