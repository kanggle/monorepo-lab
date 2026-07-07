'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { usePickAction, usePackAction, useShipAction } from './use-outbound-ops';
import type { ActionKind } from '../components/outbound-ops-helpers';

/**
 * Confirm-gated lifecycle-advance dialog lifecycle (Pick / Pack / Ship) for the
 * wms outbound screen (TASK-PC-FE-214 split — extracted verbatim from the
 * `OutboundOpsScreen` container; no behaviour change).
 *
 * Owns the action target (kind + orderId + per-attempt Idempotency-Key),
 * the inline error, and the 409-conflict retry flag. A fresh confirmed attempt
 * gets a fresh `crypto.randomUUID()` key (§ 2.4.5.1 stable-per-action /
 * fresh-per-attempt). On `409 CONFLICT` the drill is refetched and a "state
 * changed — review and retry" prompt surfaces (NEVER a silent auto-retry).
 */
export interface OutboundActionDialogState {
  action: { kind: ActionKind; orderId: string; idempotencyKey: string } | null;
  actionError: string | null;
  actionConflict: boolean;
  /** pick || pack || ship pending — drives the drill's action-button spinners. */
  actionPending: boolean;
  /** The currently-active mutation's pending flag — the confirm dialog spinner. */
  activeMutationPending: boolean;
  openAction: (kind: ActionKind, orderId: string) => void;
  confirmAction: () => void;
  closeAction: () => void;
}

export function useOutboundActionDialog(
  refetchDrill: () => void,
): OutboundActionDialogState {
  const pick = usePickAction();
  const pack = usePackAction();
  const ship = useShipAction();
  const [action, setAction] = useState<{
    kind: ActionKind;
    orderId: string;
    idempotencyKey: string;
  } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionConflict, setActionConflict] = useState(false);

  const activeMutation =
    action?.kind === 'pick' ? pick : action?.kind === 'pack' ? pack : ship;
  const actionPending = pick.isPending || pack.isPending || ship.isPending;

  function openAction(kind: ActionKind, orderId: string) {
    setActionError(null);
    setActionConflict(false);
    // A fresh confirmed attempt → a fresh Idempotency-Key (§ 2.4.5.1
    // stable-per-action / fresh-per-attempt).
    setAction({ kind, orderId, idempotencyKey: crypto.randomUUID() });
  }

  function confirmAction() {
    if (!action) return;
    const m =
      action.kind === 'pick' ? pick : action.kind === 'pack' ? pack : ship;
    m.mutate(
      { orderId: action.orderId, idempotencyKey: action.idempotencyKey },
      {
        onSuccess: () => {
          setAction(null);
          setActionError(null);
          setActionConflict(false);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          const status = e instanceof ApiError ? e.status : 0;
          if (status === 409 && code === 'CONFLICT') {
            // Optimistic-lock stale version: refetch the order, prompt retry —
            // NEVER silently auto-retry with a bumped version.
            refetchDrill();
            setActionConflict(true);
            setActionError(messageForCode('CONFLICT'));
            return;
          }
          setActionConflict(false);
          setActionError(
            messageForCode(code, '작업을 처리하지 못했습니다.'),
          );
        },
      },
    );
  }

  function closeAction() {
    setAction(null);
    setActionError(null);
    setActionConflict(false);
  }

  return {
    action,
    actionError,
    actionConflict,
    actionPending,
    activeMutationPending: activeMutation.isPending,
    openAction,
    confirmAction,
    closeAction,
  };
}
