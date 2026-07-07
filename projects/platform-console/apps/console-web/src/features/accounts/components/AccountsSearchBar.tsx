'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * `AccountsScreen` search bar (TASK-PC-FE-210 split): the email search form +
 * submit + the conditional bulk-lock trigger (shown only when ≥1 account is
 * selected). Presentational — the email input value, the selection set, the
 * submit handler and the bulk-lock opener all live in the container and arrive
 * via props. Every `data-testid` / aria / class / copy is byte-identical.
 */
export interface AccountsSearchBarProps {
  emailInput: string;
  onEmailInputChange: (value: string) => void;
  onSubmit: (e: FormEvent) => void;
  selectedCount: number;
  onBulkLock: () => void;
}

export function AccountsSearchBar({
  emailInput,
  onEmailInputChange,
  onSubmit,
  selectedCount,
  onBulkLock,
}: AccountsSearchBarProps) {
  return (
    <form
      onSubmit={onSubmit}
      className="mb-6 flex flex-wrap items-end gap-3"
      role="search"
      aria-label="계정 검색"
    >
      <div>
        <label
          htmlFor="account-email-search"
          className="block text-sm font-medium text-foreground"
        >
          이메일로 검색
        </label>
        <input
          id="account-email-search"
          type="email"
          value={emailInput}
          onChange={(e) => onEmailInputChange(e.target.value)}
          placeholder="비우면 전체 목록"
          data-testid="accounts-search-input"
          className="mt-1 w-72 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <Button type="submit" data-testid="accounts-search-submit">
        검색
      </Button>
      {selectedCount > 0 && (
        <Button
          type="button"
          data-testid="accounts-bulk-lock-trigger"
          className="bg-destructive text-destructive-foreground hover:opacity-90"
          onClick={onBulkLock}
        >
          선택 {selectedCount}건 일괄 잠금
        </Button>
      )}
    </form>
  );
}
