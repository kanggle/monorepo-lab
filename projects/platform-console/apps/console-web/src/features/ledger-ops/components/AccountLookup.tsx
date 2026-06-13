'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Account lookup (TASK-PC-FE-074 — § 2.4.7.1).
 *
 * Honest ledger constraint: the ledger exposes NO account list/search GET
 * (`ledger-api.md` has no account enumeration endpoint). The account view
 * is therefore **account-code-driven** — the operator enters a ledger
 * account code; the console does NOT fabricate a non-existent account list.
 *
 * The trial balance (`TrialBalanceTable`) is the browsable account index —
 * a code from that table can be drilled into directly by clicking the row
 * (`onSelectAccount` prop). This form accepts the colon-form code
 * (e.g. `CUSTOMER_WALLET:acc-1`); `encodeURIComponent` is applied by the
 * api client layer before the producer path is assembled.
 *
 * Read-only — the form submits the code upward; no mutation, no confirm
 * dialog.
 */
export interface AccountLookupProps {
  initialCode?: string;
  onSubmit: (code: string) => void;
}

export function AccountLookup({ initialCode, onSubmit }: AccountLookupProps) {
  const fid = useId();
  const [value, setValue] = useState(initialCode ?? '');

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    onSubmit(trimmed);
  }

  return (
    <form
      onSubmit={submit}
      className="mb-6 flex items-end gap-3"
      role="search"
      aria-label="계정 원장 (ledger account) 조회"
    >
      <div className="flex-1">
        <label
          htmlFor={fid}
          className="block text-sm font-medium text-foreground"
        >
          계정 코드 (ledgerAccountCode)
        </label>
        <input
          id={fid}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          data-testid="ledger-account-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          콜론 형식(colon-form) 코드 예: <code>CUSTOMER_WALLET:acc-1</code> —
          계정 검색 GET 은 없습니다. 시산표의 계정 코드를 클릭하거나 직접
          입력하세요.
        </p>
      </div>
      <Button type="submit" data-testid="ledger-account-submit">
        조회
      </Button>
    </form>
  );
}
