'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError } from '@/shared/api/errors';
import { messageForCode } from '@/shared/api/errors';
import {
  useAccountsSearch,
  useLockAccount,
  useUnlockAccount,
  useRevokeSessions,
  useGdprDeleteAccount,
  useBulkLockAccounts,
} from '../hooks/use-accounts';
import type { AccountPage, AccountSummary, BulkLockItem } from '../api/types';
import { classifyAccountsEmpty } from '../lib/classify-empty';
import { ConfirmActionDialog } from './ConfirmActionDialog';
import { AccountsTable } from './AccountsTable';
import {
  ACTION_META,
  accountActionDescription,
  isForbidden,
  newIdemKey,
  type ActionKind,
  type AccountsQuery,
  type PendingAction,
} from './accounts-screen-helpers';

/**
 * IAM accounts operator surface (TASK-PC-FE-002 — Phase 2 slice 1).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * pagination / search / post-mutation invalidation. Every destructive op
 * (lock/unlock/bulk-lock/revoke-session/gdpr-delete) is reason-gated +
 * confirm-gated via {@link ConfirmActionDialog}; gdpr-delete double-confirms
 * with a typed phrase. Export is a server download (no PII into client
 * state). 401/403 → the shared api client forces re-login; 503/timeout →
 * this section degrades only (the console shell stays intact).
 *
 * The `Idempotency-Key` is generated ONCE per confirmed action
 * (`crypto.randomUUID()` when the dialog confirms) and reused only if that
 * exact confirmed action is retried after a transient error — a brand-new
 * dialog open always yields a new key (no accidental dedupe / double-mutate).
 *
 * Container / presentational (TASK-PC-FE-111): this container owns ALL state +
 * the 5 mutations + gating; the result table + pagination are the prop-driven
 * {@link AccountsTable}; the pending-action model + confirm copy live in
 * `accounts-screen-helpers`.
 */

export function AccountsScreen({ initial }: { initial: AccountPage }) {
  const [emailInput, setEmailInput] = useState('');
  const [query, setQuery] = useState<AccountsQuery>({
    page: initial.page,
    size: initial.size,
  });

  const search = useAccountsSearch(query, query.page === initial.page && !query.email ? initial : undefined);
  const page = search.data;

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [bulkResult, setBulkResult] = useState<BulkLockItem[] | null>(null);

  const lock = useLockAccount();
  const unlock = useUnlockAccount();
  const revoke = useRevokeSessions();
  const gdpr = useGdprDeleteAccount();
  const bulk = useBulkLockAccounts();

  const activeMutation = useMemo(() => {
    switch (pending?.kind) {
      case 'lock':
        return lock;
      case 'unlock':
        return unlock;
      case 'revoke-session':
        return revoke;
      case 'gdpr-delete':
        return gdpr;
      case 'bulk-lock':
        return bulk;
      default:
        return null;
    }
  }, [pending?.kind, lock, unlock, revoke, gdpr, bulk]);

  const dialogError =
    activeMutation?.error instanceof ApiError
      ? messageForCode(
          (activeMutation.error as ApiError).code,
          activeMutation.error.message,
        )
      : activeMutation?.error
        ? '작업을 완료하지 못했습니다. 잠시 후 다시 시도하세요.'
        : null;

  function openAction(
    kind: ActionKind,
    account?: AccountSummary,
    accountIds?: string[],
  ) {
    setBulkResult(null);
    lock.reset();
    unlock.reset();
    revoke.reset();
    gdpr.reset();
    bulk.reset();
    setPending({ kind, account, accountIds, idempotencyKey: newIdemKey() });
  }

  function closeDialog() {
    setPending(null);
  }

  function onConfirm(reason: string) {
    if (!pending) return;
    const { kind, account, accountIds, idempotencyKey } = pending;
    const common = { reason: { reason }, idempotencyKey };
    if (kind === 'bulk-lock' && accountIds) {
      bulk.mutate(
        { accountIds, reason: { reason }, idempotencyKey },
        {
          onSuccess: (r) => {
            setBulkResult(r.results);
            setSelected(new Set());
            setPending(null);
          },
        },
      );
      return;
    }
    if (!account) return;
    const onSuccess = () => setPending(null);
    if (kind === 'lock') {
      lock.mutate({ accountId: account.id, ...common }, { onSuccess });
    } else if (kind === 'unlock') {
      unlock.mutate({ accountId: account.id, ...common }, { onSuccess });
    } else if (kind === 'revoke-session') {
      revoke.mutate({ accountId: account.id, ...common }, { onSuccess });
    } else if (kind === 'gdpr-delete') {
      gdpr.mutate({ accountId: account.id, ...common }, { onSuccess });
    }
  }

  function toggleSelect(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function submitSearch(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = emailInput.trim();
    setSelected(new Set());
    setBulkResult(null);
    setQuery(
      trimmed === ''
        ? { page: 0, size: initial.size }
        : { email: trimmed, page: 0, size: initial.size },
    );
  }

  function exportAccount(account: AccountSummary) {
    const reason = window.prompt(
      '내보내기 감사 사유를 입력하세요 (필수):',
      '',
    );
    if (reason === null || reason.trim() === '') return;
    // Server-side download — PII is never buffered into client React state
    // (the browser saves the attachment from the proxy route).
    const url = `/api/accounts/${encodeURIComponent(
      account.id,
    )}/export?reason=${encodeURIComponent(reason.trim())}`;
    window.location.assign(url);
  }

  const rows = page?.content ?? [];

  return (
    <section aria-labelledby="accounts-heading">
      <h1 id="accounts-heading" className="mb-6 text-2xl font-semibold">
        계정 운영
      </h1>

      <form
        onSubmit={submitSearch}
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
            onChange={(e) => setEmailInput(e.target.value)}
            placeholder="비우면 전체 목록"
            data-testid="accounts-search-input"
            className="mt-1 w-72 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button type="submit" data-testid="accounts-search-submit">
          검색
        </Button>
        {selected.size > 0 && (
          <Button
            type="button"
            data-testid="accounts-bulk-lock-trigger"
            className="bg-destructive text-destructive-foreground hover:opacity-90"
            onClick={() =>
              openAction('bulk-lock', undefined, [...selected])
            }
          >
            선택 {selected.size}건 일괄 잠금
          </Button>
        )}
      </form>

      {search.isError && !isForbidden(search.error) && (
        <div
          role="status"
          data-testid="accounts-degraded"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          계정 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      )}

      {bulkResult && (
        <div
          data-testid="bulk-result"
          className="mb-6 rounded-md border border-border bg-background p-4"
        >
          <h2 className="mb-2 text-sm font-semibold text-foreground">
            일괄 잠금 결과 (계정별)
          </h2>
          <ul className="space-y-1 text-sm">
            {bulkResult.map((r) => (
              <li
                key={r.accountId}
                data-testid={`bulk-result-${r.accountId}`}
                className="flex items-center justify-between"
              >
                <span className="font-mono text-xs">{r.accountId}</span>
                <span
                  className={
                    r.outcome === 'LOCKED'
                      ? 'text-foreground'
                      : 'text-destructive'
                  }
                >
                  {r.outcome}
                  {r.error ? ` — ${r.error.code}` : ''}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {!page || rows.length === 0 ? (
        (() => {
          // Distinguish 검색 결과 없음 vs 조회 권한 없음 as far as the backend
          // allows (the producer returns empty-200 for no-permission, so the
          // unfiltered-empty case is an honest union). TASK-PC-FE-063.
          const empty = classifyAccountsEmpty(
            search.isError,
            search.error,
            !!query.email,
          );
          return (
            <p
              className="text-sm text-muted-foreground"
              data-testid="accounts-empty"
              data-empty-reason={empty.reason}
            >
              {empty.message}
            </p>
          );
        })()
      ) : (
        <AccountsTable
          rows={rows}
          page={page}
          query={query}
          selected={selected}
          onToggleSelect={toggleSelect}
          onAction={openAction}
          onExport={exportAccount}
          onPageChange={setQuery}
        />
      )}

      {pending && (
        <ConfirmActionDialog
          open
          title={ACTION_META[pending.kind].title}
          description={accountActionDescription(pending)}
          confirmLabel={ACTION_META[pending.kind].confirm}
          destructive={ACTION_META[pending.kind].destructive}
          requireTypedConfirmation={
            pending.kind === 'gdpr-delete' ? 'DELETE' : undefined
          }
          pending={activeMutation?.isPending ?? false}
          errorMessage={dialogError}
          onConfirm={onConfirm}
          onCancel={closeDialog}
        />
      )}
    </section>
  );
}
