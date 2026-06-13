'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Statement lookup (TASK-PC-FE-075 — § 2.4.7.1).
 *
 * Honest ledger constraint: the ledger exposes NO statement list/search GET
 * (`reconciliation-api.md` has no statement enumeration endpoint). The
 * statement view is therefore **id-driven** — the operator enters a
 * reconciliation statement id; the console does NOT fabricate a non-existent
 * statement list or search.
 *
 * Statement ids come from the ingest the operator's integration ran —
 * ingest is out of console scope. The operator either enters the id from
 * their integration records or retrieves it from the discrepancy queue
 * context.
 *
 * Read-only — the form submits the id upward; no mutation, no confirm dialog.
 */
export interface StatementLookupProps {
  initialStatementId?: string;
  onSubmit: (statementId: string) => void;
}

export function StatementLookup({
  initialStatementId,
  onSubmit,
}: StatementLookupProps) {
  const fid = useId();
  const [value, setValue] = useState(initialStatementId ?? '');

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
      aria-label="대사 statement 조회"
    >
      <div className="flex-1">
        <label
          htmlFor={fid}
          className="block text-sm font-medium text-foreground"
        >
          statement ID
        </label>
        <input
          id={fid}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          data-testid="ledger-statement-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          statement 검색 GET 은 없습니다. statement id 를 직접 입력하세요 —
          id 는 운영자의 인제스트 연동 결과에서 확인할 수 있습니다.
        </p>
      </div>
      <Button type="submit" data-testid="ledger-statement-submit">
        조회
      </Button>
    </form>
  );
}
