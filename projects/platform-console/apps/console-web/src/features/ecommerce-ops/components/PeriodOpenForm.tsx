'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useOpenPeriod } from '../hooks/use-ecommerce-settlements';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Settlement period OPEN form (TASK-PC-FE-221 Phase B). Confirm-gated POST of a
 * half-open `[from, to)` window. The operator picks local wall-clock instants
 * (`datetime-local`); they are converted to UTC ISO-8601 (`toISOString`) before
 * the request — the producer's `from`/`to` are ISO instants.
 *
 * Client validation: both bounds present + `from < to` (the UX gate). The
 * producer stays authoritative (422 PERIOD_WINDOW_INVALID surfaced inline). NO
 * `Idempotency-Key`. On success the periods list is invalidated by the hook.
 */
export function PeriodOpenForm() {
  const fromFieldId = useId();
  const toFieldId = useId();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const open = useOpenPeriod();
  const pending = open.isPending;

  const fromDate = from ? new Date(from) : null;
  const toDate = to ? new Date(to) : null;
  const orderingValid =
    fromDate !== null &&
    toDate !== null &&
    !Number.isNaN(fromDate.getTime()) &&
    !Number.isNaN(toDate.getTime()) &&
    fromDate.getTime() < toDate.getTime();
  const formValid = from !== '' && to !== '' && orderingValid;

  function toIso(local: string): string {
    return new Date(local).toISOString();
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConfirmOpen(true);
  }

  function confirmOpenPeriod() {
    setError(null);
    open.mutate(
      { from: toIso(from), to: toIso(to) },
      {
        onSuccess: () => {
          setConfirmOpen(false);
          setFrom('');
          setTo('');
        },
        onError: (err: unknown) => {
          const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
          setError(messageForCode(code, '정산 기간을 개시하지 못했습니다.'));
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
      className="mb-4 rounded-md border border-border p-4"
      data-testid="settlements-period-open-form"
      aria-label="정산 기간 개시"
    >
      <h3 className="mb-1 text-sm font-semibold text-foreground">정산 기간 개시</h3>
      <p className="mb-3 text-xs text-muted-foreground">
        반열림 구간 <code>[시작, 종료)</code> 로 새 정산 기간을 개시합니다. 시작이
        종료보다 앞서야 합니다.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <div className="w-60">
          <label htmlFor={fromFieldId} className={labelCls}>
            시작 <span className="text-destructive">*</span>
          </label>
          <input
            id={fromFieldId}
            type="datetime-local"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className={inputCls}
            data-testid="settlements-period-open-from"
          />
        </div>
        <div className="w-60">
          <label htmlFor={toFieldId} className={labelCls}>
            종료 <span className="text-destructive">*</span>
          </label>
          <input
            id={toFieldId}
            type="datetime-local"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className={inputCls}
            data-testid="settlements-period-open-to"
          />
        </div>
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="settlements-period-open-submit"
        >
          기간 개시
        </Button>
      </div>
      {from !== '' && to !== '' && !orderingValid && (
        <p
          role="alert"
          className="mt-2 text-xs text-destructive"
          data-testid="settlements-period-open-order-error"
        >
          시작 시각이 종료 시각보다 앞서야 합니다.
        </p>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="정산 기간을 개시할까요?"
        description={`시작 "${from}" ~ 종료 "${to}" 구간으로 새 정산 기간을 개시합니다.`}
        confirmLabel="개시"
        pending={pending}
        errorMessage={error}
        onConfirm={confirmOpenPeriod}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </form>
  );
}
