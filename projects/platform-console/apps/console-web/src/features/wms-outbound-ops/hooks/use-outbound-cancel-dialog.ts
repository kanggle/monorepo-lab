'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useCancelOrder } from './use-outbound-ops';
import { cancelNeedsAdmin } from '../api/types';
import { cancelErrorMessage } from '../components/outbound-ops-helpers';

/**
 * Reason-required, role-escalating, async-saga cancel dialog lifecycle for the
 * wms outbound screen (TASK-PC-FE-214 split — extracted verbatim from the
 * `OutboundOpsScreen` container; no behaviour change).
 *
 * Owns the cancel target (orderId + label + the post-pick `needsAdmin` gate +
 * a per-attempt Idempotency-Key), the inline error, and the 409-conflict retry
 * flag. On `409 CONFLICT` the drill is refetched and a retry prompt surfaces
 * (never a silent auto-retry).
 */
export interface OutboundCancelDialogState {
  cancelTarget: {
    orderId: string;
    orderLabel: string;
    needsAdmin: boolean;
    idempotencyKey: string;
  } | null;
  cancelError: string | null;
  cancelConflict: boolean;
  cancelPending: boolean;
  openCancel: (
    orderId: string,
    orderLabel: string,
    status: string | undefined,
  ) => void;
  confirmCancel: (reason: string) => void;
  closeCancel: () => void;
}

export function useOutboundCancelDialog(
  refetchDrill: () => void,
): OutboundCancelDialogState {
  const cancel = useCancelOrder();
  const [cancelTarget, setCancelTarget] = useState<{
    orderId: string;
    orderLabel: string;
    needsAdmin: boolean;
    idempotencyKey: string;
  } | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [cancelConflict, setCancelConflict] = useState(false);

  function openCancel(
    orderId: string,
    orderLabel: string,
    status: string | undefined,
  ) {
    setCancelError(null);
    setCancelConflict(false);
    // A fresh confirmed attempt → a fresh Idempotency-Key.
    setCancelTarget({
      orderId,
      orderLabel,
      needsAdmin: cancelNeedsAdmin(status),
      idempotencyKey: crypto.randomUUID(),
    });
  }

  function confirmCancel(reason: string) {
    if (!cancelTarget) return;
    cancel.mutate(
      {
        orderId: cancelTarget.orderId,
        reason,
        idempotencyKey: cancelTarget.idempotencyKey,
      },
      {
        onSuccess: () => {
          setCancelTarget(null);
          setCancelError(null);
          setCancelConflict(false);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          const status = e instanceof ApiError ? e.status : 0;
          if (status === 409 && code === 'CONFLICT') {
            // Optimistic-lock stale version → refetch + prompt retry (never a
            // silent auto-retry).
            refetchDrill();
            setCancelConflict(true);
            setCancelError(messageForCode('CONFLICT'));
            return;
          }
          setCancelConflict(false);
          setCancelError(cancelErrorMessage(code));
        },
      },
    );
  }

  function closeCancel() {
    setCancelTarget(null);
    setCancelError(null);
    setCancelConflict(false);
  }

  return {
    cancelTarget,
    cancelError,
    cancelConflict,
    cancelPending: cancel.isPending,
    openCancel,
    confirmCancel,
    closeCancel,
  };
}
