'use client';

import { useId, useState } from 'react';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Carrier + trackingNumber input dialog for the PREPARING → SHIPPED transition
 * (TASK-PC-FE-088 — ADR-031 Phase 4b). The producer rejects a SHIPPED status
 * update without these two fields (400 InvalidShipping) — the UI enforces the
 * requirement before the confirm button is unlocked.
 *
 * Mirrors `CouponIssueDialog` / `StockAdjustDialog` shape: a form-wrapped
 * `ConfirmDialog`. The caller receives the submitted { carrier, trackingNumber }
 * payload via `onConfirm` so it can call `useUpdateShippingStatus`.
 *
 * NO `Idempotency-Key` (producer defines none — § 2.4.10); confirm-gate is
 * the sole double-submit guard.
 */
export interface ShipFormDialogProps {
  open: boolean;
  shippingId: string;
  pending?: boolean;
  errorMessage?: string | null;
  onConfirm: (payload: { carrier: string; trackingNumber: string }) => void;
  onCancel: () => void;
}

export function ShipFormDialog({
  open,
  shippingId,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: ShipFormDialogProps) {
  const carrierId = useId();
  const trackingId = useId();

  const [carrier, setCarrier] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');

  const valid = carrier.trim().length > 0 && trackingNumber.trim().length > 0;

  function reset() {
    setCarrier('');
    setTrackingNumber('');
  }

  function confirm() {
    if (!valid) return;
    onConfirm({ carrier: carrier.trim(), trackingNumber: trackingNumber.trim() });
  }

  function cancel() {
    reset();
    onCancel();
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

  return (
    <ConfirmDialog
      open={open}
      title="배송 시작 — 운송장 정보 입력"
      description={`배송 #${shippingId} 상태를 SHIPPED로 변경합니다. 택배사와 운송장 번호를 입력하세요.`}
      confirmLabel="배송 시작"
      pending={pending}
      confirmDisabled={!valid}
      errorMessage={errorMessage}
      onConfirm={confirm}
      onCancel={cancel}
    >
      <div className="space-y-3" data-testid="ship-form">
        <div>
          <label
            htmlFor={carrierId}
            className="block text-sm font-medium text-foreground"
          >
            택배사 <span className="text-destructive">*</span>
          </label>
          <input
            id={carrierId}
            type="text"
            value={carrier}
            onChange={(e) => setCarrier(e.target.value)}
            placeholder="예: CJ대한통운"
            className={inputCls}
            data-testid="ship-form-carrier"
          />
        </div>
        <div>
          <label
            htmlFor={trackingId}
            className="block text-sm font-medium text-foreground"
          >
            운송장 번호 <span className="text-destructive">*</span>
          </label>
          <input
            id={trackingId}
            type="text"
            value={trackingNumber}
            onChange={(e) => setTrackingNumber(e.target.value)}
            placeholder="예: 1234567890"
            className={inputCls}
            data-testid="ship-form-tracking-number"
          />
        </div>
        {!valid && (carrier.length > 0 || trackingNumber.length > 0) && (
          <p className="text-xs text-muted-foreground">
            택배사와 운송장 번호를 모두 입력해야 합니다.
          </p>
        )}
      </div>
    </ConfirmDialog>
  );
}
