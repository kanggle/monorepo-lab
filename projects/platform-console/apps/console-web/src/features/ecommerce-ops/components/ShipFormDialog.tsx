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
 * `ConfirmDialog`. The caller receives the submitted
 * { carrier, trackingNumber, deductWmsInventory } payload via `onConfirm` so it
 * can call `useUpdateShippingStatus`.
 *
 * WMS-deduct toggle (ADR-MONO-022 D4 v2(c)): the "WMS 재고 차감" checkbox is
 * rendered ONLY when the row is `wmsRouted` (a WMS-fulfilled order). For a
 * non-wmsRouted row the checkbox is absent and `deductWmsInventory` is never
 * sent (the producer no-ops the flag anyway, but the UI keeps it off the wire).
 *
 * NO `Idempotency-Key` (producer defines none — § 2.4.10); confirm-gate is
 * the sole double-submit guard.
 */
export interface ShipFormDialogProps {
  open: boolean;
  shippingId: string;
  /** Whether the row's order is routed through wms fulfillment — gates the
   *  "WMS 재고 차감" checkbox. Defaults to false (checkbox hidden). */
  wmsRouted?: boolean;
  pending?: boolean;
  errorMessage?: string | null;
  onConfirm: (payload: {
    carrier: string;
    trackingNumber: string;
    deductWmsInventory: boolean;
  }) => void;
  onCancel: () => void;
}

export function ShipFormDialog({
  open,
  shippingId,
  wmsRouted = false,
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: ShipFormDialogProps) {
  const carrierId = useId();
  const trackingId = useId();
  const deductId = useId();

  const [carrier, setCarrier] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [deductWmsInventory, setDeductWmsInventory] = useState(false);

  const valid = carrier.trim().length > 0 && trackingNumber.trim().length > 0;

  function reset() {
    setCarrier('');
    setTrackingNumber('');
    setDeductWmsInventory(false);
  }

  function confirm() {
    if (!valid) return;
    onConfirm({
      carrier: carrier.trim(),
      trackingNumber: trackingNumber.trim(),
      // Only a wmsRouted row can carry a true flag; defensively force false
      // otherwise so a stale toggle never leaks onto a non-WMS row.
      deductWmsInventory: wmsRouted && deductWmsInventory,
    });
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
        {wmsRouted && (
          <div className="rounded-md border border-border bg-muted/40 px-3 py-2">
            <label
              htmlFor={deductId}
              className="flex items-start gap-2 text-sm font-medium text-foreground"
            >
              <input
                id={deductId}
                type="checkbox"
                checked={deductWmsInventory}
                onChange={(e) => setDeductWmsInventory(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-border text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                data-testid="ship-form-deduct-wms"
              />
              <span>
                WMS 재고 차감
                <span className="mt-0.5 block text-xs font-normal text-muted-foreground">
                  WMS 경유 주문입니다. 체크하면 발송과 함께 WMS 실물 재고를
                  차감합니다.
                </span>
              </span>
            </label>
          </div>
        )}
      </div>
    </ConfirmDialog>
  );
}
