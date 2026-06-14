'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * FX position open-lots lookup (TASK-PC-FE-091 — § 2.4.7.1).
 *
 * Honest ledger constraint: the ledger exposes NO position list/search GET —
 * the lots view is **`(account, currency)`-driven**. The operator enters a
 * ledger account code + an ISO-4217 currency; the console does NOT fabricate
 * a non-existent position list. The trial balance (`TrialBalanceTable`) is
 * the browsable account index; a code can be hand-entered or copied from it.
 *
 * This form accepts the colon-form account code (e.g.
 * `CUSTOMER_WALLET:acc-1`); `encodeURIComponent` is applied by the api client
 * layer before the producer path is assembled. The currency is upper-cased
 * for convenience (the producer accepts `USD/EUR/JPY/KRW`).
 *
 * Read-only — the form submits `(code, currency)` upward and gates the query;
 * no mutation, no confirm dialog. Submit is disabled until BOTH fields are
 * non-empty.
 */
export interface PositionLotsLookupProps {
  initialCode?: string;
  initialCurrency?: string;
  onSubmit: (code: string, currency: string) => void;
}

export function PositionLotsLookup({
  initialCode,
  initialCurrency,
  onSubmit,
}: PositionLotsLookupProps) {
  const codeId = useId();
  const currencyId = useId();
  const [code, setCode] = useState(initialCode ?? '');
  const [currency, setCurrency] = useState(initialCurrency ?? '');

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmedCode = code.trim();
    const trimmedCurrency = currency.trim().toUpperCase();
    if (!trimmedCode || !trimmedCurrency) return;
    onSubmit(trimmedCode, trimmedCurrency);
  }

  const submittable = Boolean(code.trim() && currency.trim());

  return (
    <form
      onSubmit={submit}
      className="mb-6 flex flex-wrap items-end gap-3"
      role="search"
      aria-label="외화 포지션 로트 (FX position lots) 조회"
    >
      <div className="flex-1 min-w-[16rem]">
        <label
          htmlFor={codeId}
          className="block text-sm font-medium text-foreground"
        >
          계정 코드 (ledgerAccountCode)
        </label>
        <input
          id={codeId}
          type="text"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          data-testid="ledger-lots-account-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div className="w-36">
        <label
          htmlFor={currencyId}
          className="block text-sm font-medium text-foreground"
        >
          통화 (currency)
        </label>
        <input
          id={currencyId}
          type="text"
          value={currency}
          onChange={(e) => setCurrency(e.target.value)}
          data-testid="ledger-lots-currency-input"
          autoComplete="off"
          placeholder="USD"
          maxLength={3}
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm uppercase text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <Button
        type="submit"
        data-testid="ledger-lots-submit"
        disabled={!submittable}
      >
        조회
      </Button>
      <p className="w-full text-xs text-muted-foreground">
        외화 통화(<code>USD/EUR/JPY</code>)로 조회하면 열린 취득 로트가
        표시됩니다. 기준 통화(<code>KRW</code>)에는 외화 로트가 없습니다.
      </p>
    </form>
  );
}
