'use client';

import { useId, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useAdjustStock } from '../hooks/use-ecommerce-products';
import type { Variant } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Confirm-gated stock adjustment (TASK-PC-FE-081 — § 2.4.10 #9,
 * `PATCH /admin/products/{id}/stock`). The producer AdjustStockRequest carries
 * a target `variantId`, a SIGNED `quantity` delta (positive = increment,
 * negative = decrement), and a required `reason` (in the body, NOT a header —
 * the surface defines no `X-Operator-Reason`).
 *
 * NO `Idempotency-Key` (producer defines none); the explicit confirm + the
 * producer `400 INSUFFICIENT_STOCK` guard are the safety gate.
 */
export interface StockAdjustDialogProps {
  open: boolean;
  productId: string;
  variant: Variant | null;
  onClose: () => void;
  onAdjusted: () => void;
}

export function StockAdjustDialog({
  open,
  productId,
  variant,
  onClose,
  onAdjusted,
}: StockAdjustDialogProps) {
  const qtyId = useId();
  const reasonId = useId();
  const adjust = useAdjustStock();
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  // Idempotency-Key for THIS adjustment intent (TASK-PC-FE-252). Minted lazily on
  // confirm and held; a retry of the unchanged delta (e.g. after a 409) reuses it
  // so the producer dedupes the double-submit. Editing the delta/reason or a
  // successful/ cancelled adjustment clears it, so a genuine second +10 gets a
  // fresh key (NOT a value hash — that would block legitimate repeats forever).
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  const qtyNum = Number(quantity);
  const qtyValid = quantity !== '' && quantity !== '-' && Number.isInteger(qtyNum) && qtyNum !== 0;
  const reasonValid = reason.trim() !== '';
  const valid = qtyValid && reasonValid && variant !== null;

  function reset() {
    setQuantity('');
    setReason('');
    setError(null);
    setConflict(false);
    setIdempotencyKey(null);
  }

  function confirm() {
    if (!valid || !variant) return;
    setError(null);
    // Reuse the held key across a retry of the same delta; mint on first confirm.
    const key = idempotencyKey ?? crypto.randomUUID();
    setIdempotencyKey(key);
    adjust.mutate(
      {
        productId,
        body: { variantId: variant.id, quantity: qtyNum, reason: reason.trim() },
        idempotencyKey: key,
      },
      {
        onSuccess: () => {
          reset();
          onAdjusted();
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          const status = e instanceof ApiError ? e.status : 0;
          if (status === 409 && code === 'CONFLICT') {
            setConflict(true);
            setError(messageForCode('CONFLICT'));
            return;
          }
          setConflict(false);
          setError(messageForCode(code, '재고를 조정하지 못했습니다.'));
        },
      },
    );
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

  return (
    <ConfirmDialog
      open={open}
      title="재고를 조정할까요?"
      description={
        variant
          ? `"${variant.optionName}" 옵션의 재고를 조정합니다. 현재 재고: ${variant.stock}. 증감 수량(양수=입고, 음수=차감)과 사유를 입력하세요.`
          : ''
      }
      confirmLabel="재고 조정"
      pending={adjust.isPending}
      confirmDisabled={!valid}
      errorMessage={error}
      conflict={conflict}
      onConfirm={confirm}
      onCancel={() => {
        reset();
        onClose();
      }}
    >
      <div className="space-y-3" data-testid="stock-adjust-form">
        <div>
          <label htmlFor={qtyId} className="block text-sm font-medium text-foreground">
            증감 수량 (+/-)
          </label>
          <input
            id={qtyId}
            inputMode="numeric"
            value={quantity}
            onChange={(e) => {
              setQuantity(e.target.value.replace(/[^0-9-]/g, ''));
              // Editing the delta = a different intent → drop the held key so the
              // next confirm mints a fresh one (TASK-PC-FE-252, AC-3).
              setIdempotencyKey(null);
            }}
            className={inputCls}
            data-testid="stock-adjust-quantity"
          />
        </div>
        <div>
          <label htmlFor={reasonId} className="block text-sm font-medium text-foreground">
            사유 <span className="text-destructive">*</span>
          </label>
          <input
            id={reasonId}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              setIdempotencyKey(null);
            }}
            className={inputCls}
            data-testid="stock-adjust-reason"
          />
        </div>
      </div>
    </ConfirmDialog>
  );
}
