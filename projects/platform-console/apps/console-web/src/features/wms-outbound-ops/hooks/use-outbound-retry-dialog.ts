'use client';

import { useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useRetryTms } from './use-outbound-ops';
import { retryTmsErrorMessage } from '../components/outbound-ops-helpers';

/**
 * TMS-retry dialog lifecycle for the wms outbound screen (TASK-PC-FE-214 split
 * — extracted verbatim from the `OutboundOpsScreen` container; no behaviour
 * change).
 *
 * Reason-free admin action; the shipment-id is resolved server-side from the
 * admin read-model. Owns the retry target (orderId + a per-attempt
 * Idempotency-Key) and the inline error. On success the drill refetch reflects
 * the recovered saga (→ COMPLETED) / tmsStatus; if it stayed NOT_NOTIFIED the
 * action re-appears.
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
  const retry = useRetryTms();
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
          // The drill refetch reflects the recovered saga (→ COMPLETED) /
          // tmsStatus; if it stayed NOT_NOTIFIED the action re-appears.
          setRetryTarget(null);
          setRetryError(null);
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setRetryError(retryTmsErrorMessage(code));
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
