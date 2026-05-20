'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Account lookup (TASK-PC-FE-009 — § 2.4.7).
 *
 * Honest finance constraint: finance v1 exposes NO account list/search
 * GET (`account-api.md` only carries `GET /accounts/{id}`). The
 * section is therefore **account-id-driven** — the operator enters an
 * accountId; the console does NOT fabricate a non-existent finance
 * list/search endpoint.
 *
 * Read-only — the form submits the accountId upward; no mutation, no
 * confirm dialog.
 */
export interface AccountLookupProps {
  initialAccountId?: string;
  onSubmit: (accountId: string) => void;
}

export function AccountLookup({
  initialAccountId,
  onSubmit,
}: AccountLookupProps) {
  const fid = useId();
  const [value, setValue] = useState(initialAccountId ?? '');

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
      aria-label="finance 계정 조회"
    >
      <div className="flex-1">
        <label
          htmlFor={fid}
          className="block text-sm font-medium text-foreground"
        >
          accountId
        </label>
        <input
          id={fid}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          data-testid="finance-account-input"
          autoComplete="off"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          finance v1 은 계정 목록/검색 GET 을 제공하지 않습니다 —
          accountId 로 조회하세요.
        </p>
      </div>
      <Button type="submit" data-testid="finance-account-submit">
        조회
      </Button>
    </form>
  );
}
