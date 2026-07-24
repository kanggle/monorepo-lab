'use client';

import { useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useRetryDispatch } from './use-outbound-ops';
import { retryDispatchErrorMessage } from '../components/outbound-ops-helpers';

/**
 * Dispatch-retry dialog lifecycle for the wms outbound screen (TASK-PC-FE-214
 * split; repointed by TASK-PC-FE-258 from the wms TMS side-channel to the
 * logistics carrier-dispatch retry).
 *
 * Reason-free recovery action ("발송 재시도"); the orderId → shipmentId →
 * dispatchId chain is resolved server-side in the proxy. Owns the retry target
 * (orderId + a per-attempt Idempotency-Key) and the inline error. On success the
 * drill refetch reflects the re-driven dispatch; a missing shipment
 * (`SHIPMENT_NOT_FOUND`) or a missing dispatch (`DISPATCH_NOT_FOUND`) surfaces as
 * an inline actionable message (no crash, no retry POST fired server-side).
 */
export interface OutboundRetryDialogState {
  retryTarget: { orderId: string; idempotencyKey: string } | null;
  retryError: string | null;
  retryPending: boolean;
  openRetry: (orderId: string) => void;
  confirmRetry: () => void;
  closeRetry: () => void;
}

export function useOutboundRetryDialog(): OutboundRetryDialogState {
  const retry = useRetryDispatch();
  const [retryTarget, setRetryTarget] = useState<{
    orderId: string;
    idempotencyKey: string;
  } | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);

  function openRetry(orderId: string) {
    setRetryError(null);
    // A fresh confirmed attempt → a fresh Idempotency-Key.
    setRetryTarget({ orderId, idempotencyKey: crypto.randomUUID() });
  }

  function confirmRetry() {
    if (!retryTarget) return;
    retry.mutate(
      { orderId: retryTarget.orderId, idempotencyKey: retryTarget.idempotencyKey },
      {
        onSuccess: () => {
          // The drill refetch reflects the re-driven dispatch status.
          setRetryTarget(null);
          setRetryError(null);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setRetryError(retryDispatchErrorMessage(code));
        },
      },
    );
  }

  function closeRetry() {
    setRetryTarget(null);
    setRetryError(null);
  }

  return {
    retryTarget,
    retryError,
    retryPending: retry.isPending,
    openRetry,
    confirmRetry,
    closeRetry,
  };
}
