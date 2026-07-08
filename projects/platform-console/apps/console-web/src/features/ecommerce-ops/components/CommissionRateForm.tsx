'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useSetCommissionRate } from '../hooks/use-ecommerce-settlements';
import {
  COMMISSION_RATE_MIN_BPS,
  COMMISSION_RATE_MAX_BPS,
  rateBpsToPercent,
} from '../api/settlement-types';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Commission-rate SET form (TASK-PC-FE-221 Phase B). Confirm-gated PUT of a
 * per-seller rate (basis points). PROSPECTIVE: existing accruals are NOT
 * recomputed — only future accruals use the new rate (stated in the confirm).
 *
 * Client validation: rateBps must be an integer in `[0, 10000]` (the UX gate);
 * the producer stays authoritative (422 COMMISSION_RATE_INVALID surfaced inline).
 * NO `Idempotency-Key`.
 *
 * On success it calls `onApplied(sellerId)` so the sibling lookup can display the
 * freshly-set rate (the hook also invalidates the commission-rate query).
 */
export interface CommissionRateFormProps {
  onApplied?: (sellerId: string) => void;
}

export function CommissionRateForm({ onApplied }: CommissionRateFormProps) {
  const sellerFieldId = useId();
  const bpsFieldId = useId();
  const [sellerId, setSellerId] = useState('');
  const [bpsDraft, setBpsDraft] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = useSetCommissionRate();
  const pending = set.isPending;

  const sellerTrimmed = sellerId.trim();
  const bps = Number(bpsDraft);
  const bpsValid =
    bpsDraft.trim() !== '' &&
    Number.isInteger(bps) &&
    bps >= COMMISSION_RATE_MIN_BPS &&
    bps <= COMMISSION_RATE_MAX_BPS;
  const formValid = sellerTrimmed !== '' && bpsValid;

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConfirmOpen(true);
  }

  function confirmSet() {
    setError(null);
    set.mutate(
      { sellerId: sellerTrimmed, rateBps: bps },
      {
        onSuccess: () => {
          setConfirmOpen(false);
          onApplied?.(sellerTrimmed);
        },
        onError: (err: unknown) => {
          const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
          setError(messageForCode(code, '수수료율을 설정하지 못했습니다.'));
        },
      },
    );
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
  const labelCls = 'block text-sm font-medium text-foreground';

  return (
    <form
      onSubmit={onSubmit}
      className="mt-4 rounded-md border border-border p-4"
      data-testid="settlements-rate-set-form"
      aria-label="수수료율 설정"
    >
      <h3 className="mb-1 text-sm font-semibold text-foreground">수수료율 설정</h3>
      <p className="mb-3 text-xs text-muted-foreground">
        셀러 개별 수수료율(basis points, 0~10000)을 설정합니다. 기존 적립 라인은
        변경되지 않고(prospective), 이후 적립부터 새 수수료율이 적용됩니다.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <div className="w-56">
          <label htmlFor={sellerFieldId} className={labelCls}>
            셀러 ID <span className="text-destructive">*</span>
          </label>
          <input
            id={sellerFieldId}
            type="text"
            value={sellerId}
            onChange={(e) => setSellerId(e.target.value)}
            autoComplete="off"
            className={inputCls}
            data-testid="settlements-rate-set-seller-input"
          />
        </div>
        <div className="w-40">
          <label htmlFor={bpsFieldId} className={labelCls}>
            수수료율 (bps) <span className="text-destructive">*</span>
          </label>
          <input
            id={bpsFieldId}
            type="number"
            inputMode="numeric"
            min={COMMISSION_RATE_MIN_BPS}
            max={COMMISSION_RATE_MAX_BPS}
            step={1}
            value={bpsDraft}
            onChange={(e) => setBpsDraft(e.target.value)}
            autoComplete="off"
            className={inputCls}
            data-testid="settlements-rate-set-bps-input"
          />
        </div>
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="settlements-rate-set-submit"
        >
          수수료율 설정
        </Button>
      </div>
      {bpsDraft.trim() !== '' && !bpsValid && (
        <p
          role="alert"
          className="mt-2 text-xs text-destructive"
          data-testid="settlements-rate-set-range-error"
        >
          수수료율은 {COMMISSION_RATE_MIN_BPS}~{COMMISSION_RATE_MAX_BPS} 사이의
          정수(basis points)여야 합니다.
        </p>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="수수료율을 설정할까요?"
        description={`셀러 "${sellerTrimmed}"의 수수료율을 ${bpsValid ? `${rateBpsToPercent(bps)} (${bps} bps)` : ''}로 설정합니다. 기존 적립은 변경되지 않으며, 이후 적립부터 적용됩니다.`}
        confirmLabel="설정"
        pending={pending}
        errorMessage={error}
        onConfirm={confirmSet}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </form>
  );
}
