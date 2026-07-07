'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { DomainSubscriptionRow } from '../lib/derive';
import { SubscriptionConfirmDialog } from './SubscriptionConfirmDialog';
import { SubscriptionRow } from './SubscriptionRow';
import { actionMeta, runMutation, type PendingAction } from './subscriptions-actions';

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
          <SubscriptionRow key={row.key} row={row} onOpen={open} />
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
