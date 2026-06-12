'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Journal entry lookup (TASK-PC-FE-072 — § 2.4.7.1).
 *
 * Honest ledger constraint: the ledger exposes NO list/search GET over
 * journal entries (`ledger-api.md` only carries `GET /entries/{entryId}`).
 * The journal-entry view is therefore **entry-id-driven** — the operator
 * enters an entryId; the console does NOT fabricate a non-existent ledger
 * entry list/search endpoint.
 *
 * Read-only — the form submits the entryId upward; no mutation, no confirm
 * dialog.
 */
export interface JournalEntryLookupProps {
  initialEntryId?: string;
  onSubmit: (entryId: string) => void;
}

export function JournalEntryLookup({
  initialEntryId,
  onSubmit,
}: JournalEntryLookupProps) {
  const fid = useId();
  const [value, setValue] = useState(initialEntryId ?? '');

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
      aria-label="분개 (journal entry) 조회"
    >
      <div className="flex-1">
        <label
          htmlFor={fid}
          className="block text-sm font-medium text-foreground"
        >
          entryId
        </label>
        <input
          id={fid}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          data-testid="ledger-entry-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          ledger 는 분개 목록/검색 GET 을 제공하지 않습니다 — entryId 로
          조회하세요.
        </p>
      </div>
      <Button type="submit" data-testid="ledger-entry-submit">
        조회
      </Button>
    </form>
  );
}
