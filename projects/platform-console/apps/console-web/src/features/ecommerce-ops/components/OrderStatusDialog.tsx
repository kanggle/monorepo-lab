'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useChangeOrderStatus } from '../hooks/use-ecommerce-orders';
import {
  allowedTransitions,
  type OrderStatus,
} from '../api/order-types';
import { ConfirmDialog } from './ConfirmDialog';
import { Button } from '@/shared/ui/Button';

/**
 * Confirm-gated order status change dialog for the ecommerce order detail
 * surface (TASK-PC-FE-083 — § 2.4.10 #17). Mirrors the `ConfirmDialog`
 * integration in `ProductDetail`.
 *
 * Only shows buttons for **allowed transitions** from the current status
 * (PENDING → {CONFIRMED, CANCELLED}, CONFIRMED → {SHIPPED, CANCELLED},
 * SHIPPED → {DELIVERED}, terminal → none). The UI is the UX defence; the
 * producer is the final authority — 400/422/409/404 are surfaced inline.
 *
 * Error mapping:
 *   - 400 InvalidOrder (wrong forward transition) → "전환이 허용되지 않습니다."
 *   - 422 OrderCannotBeCancelled → "취소할 수 없는 상태입니다."
 *   - 409 CONFLICT (optimistic lock) → conflict prompt (retry)
 *   - 404 ORDER_NOT_FOUND → "주문을 찾을 수 없습니다."
 *   - other inline → fallback message
 *
 * NO `Idempotency-Key` is sent (the producer defines none — § 2.4.10).
 */
export interface OrderStatusDialogProps {
  orderId: string;
  currentStatus: string;
  onSuccess?: () => void;
}

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: '확인',
  SHIPPED: '배송 시작',
  DELIVERED: '배송 완료',
  CANCELLED: '취소',
};

function labelFor(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

function inlineMessageForError(err: ApiError): string {
  // 422 = OrderCannotBeCancelled
  if (err.status === 422) {
    return messageForCode(
      'ORDER_CANNOT_BE_CANCELLED',
      '현재 상태에서는 취소할 수 없습니다.',
    );
  }
  // 400 = InvalidOrder (invalid forward transition) or InvalidOrderStatus
  if (err.status === 400) {
    return messageForCode(
      'INVALID_ORDER',
      '이 상태 전환은 허용되지 않습니다.',
    );
  }
  // 404
  if (err.status === 404) {
    return messageForCode(
      'ORDER_NOT_FOUND',
      '주문을 찾을 수 없습니다. 목록을 새로고침하세요.',
    );
  }
  // 409 is handled via `conflict` prop
  return messageForCode(err.code, '상태를 변경하지 못했습니다.');
}

export function OrderStatusDialog({
  orderId,
  currentStatus,
  onSuccess,
}: OrderStatusDialogProps) {
  const targets = allowedTransitions(currentStatus) as OrderStatus[];
  const change = useChangeOrderStatus();

  const [pendingTarget, setPendingTarget] = useState<OrderStatus | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  if (targets.length === 0) {
    return null;
  }

  function openDialog(target: OrderStatus) {
    setPendingTarget(target);
    setErrorMsg(null);
    setConflict(false);
  }

  function closeDialog() {
    setPendingTarget(null);
    setErrorMsg(null);
    setConflict(false);
  }

  function confirmChange() {
    if (!pendingTarget) return;
    setErrorMsg(null);
    setConflict(false);
    change.mutate(
      { id: orderId, body: { status: pendingTarget } },
      {
        onSuccess: () => {
          closeDialog();
          onSuccess?.();
        },
        onError: (e) => {
          if (e instanceof ApiError) {
            if (e.status === 409) {
              setConflict(true);
            } else {
              setErrorMsg(inlineMessageForError(e));
            }
          } else {
            setErrorMsg('상태를 변경하지 못했습니다. 잠시 후 다시 시도하세요.');
          }
        },
      },
    );
  }

  return (
    <div
      className="mt-6"
      data-testid="order-status-actions"
    >
      <h3 className="mb-2 text-base font-medium text-foreground">
        상태 전이
      </h3>
      <div className="flex flex-wrap gap-2">
        {targets.map((target) => (
          <Button
            key={target}
            variant="secondary"
            onClick={() => openDialog(target)}
            data-testid={`order-status-btn-${target.toLowerCase()}`}
          >
            {labelFor(target)}
          </Button>
        ))}
      </div>

      <ConfirmDialog
        open={pendingTarget !== null}
        title="주문 상태를 변경할까요?"
        description={
          pendingTarget
            ? `주문 #${orderId} 상태를 "${labelFor(pendingTarget)}"(으)로 변경합니다.`
            : ''
        }
        confirmLabel={pendingTarget ? labelFor(pendingTarget) : '확인'}
        tone="default"
        pending={change.isPending}
        errorMessage={errorMsg}
        conflict={conflict}
        onConfirm={confirmChange}
        onCancel={closeDialog}
      />
    </div>
  );
}
