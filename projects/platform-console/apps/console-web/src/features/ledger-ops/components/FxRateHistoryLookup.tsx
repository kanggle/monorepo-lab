'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * FX 환율 history 통화 조회 폼 (TASK-PC-FE-104 — § 2.4.7.1).
 *
 * The history drill is **per foreign-currency-pair** (`KRW/{foreign}` —
 * the base is the fixed reporting currency KRW in v1, so only the foreign
 * leg is an input, mirroring the poller's pairs-are-foreign-legs model).
 * The operator either types a foreign ISO-4217 code here OR clicks a pair
 * in the FX feed table above (which sets the same state). The currency is
 * upper-cased for convenience.
 *
 * Read-only — the form submits the foreign code upward and gates the
 * history query; no mutation, no confirm dialog. Submit is disabled until
 * the field is non-empty.
 */
export interface FxRateHistoryLookupProps {
  initialCurrency?: string;
  onSubmit: (foreignCurrency: string) => void;
}

export function FxRateHistoryLookup({
  initialCurrency,
  onSubmit,
}: FxRateHistoryLookupProps) {
  const currencyId = useId();
  const [currency, setCurrency] = useState(initialCurrency ?? '');

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = currency.trim().toUpperCase();
    if (!trimmed) return;
    onSubmit(trimmed);
  }

  const submittable = Boolean(currency.trim());

  return (
    <form
      onSubmit={submit}
      className="mt-8 flex flex-wrap items-end gap-3"
      role="search"
      aria-label="FX 환율 이력 (history) 조회"
    >
      <div className="w-36">
        <label
          htmlFor={currencyId}
          className="block text-sm font-medium text-foreground"
        >
          외화 통화 (foreign)
        </label>
        <input
          id={currencyId}
          type="text"
          value={currency}
          onChange={(e) => setCurrency(e.target.value)}
          data-testid="ledger-fx-history-currency-input"
          autoComplete="off"
          placeholder="USD"
          maxLength={3}
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm uppercase text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <Button
        type="submit"
        data-testid="ledger-fx-history-submit"
        disabled={!submittable}
      >
        이력 조회
      </Button>
      <p className="w-full text-xs text-muted-foreground">
        외화 통화(<code>USD/EUR/JPY</code>)로 조회하거나 위 피드 표의
        통화쌍을 클릭하면 해당 쌍의 환율 이력(최신순)이 표시됩니다.
      </p>
    </form>
  );
}
