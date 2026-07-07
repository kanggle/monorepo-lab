'use client';

import { useMemo, useState } from 'react';
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
import { ConfirmActionDialog } from './ConfirmActionDialog';
import { AccountsTable } from './AccountsTable';
import { AccountsSearchBar } from './AccountsSearchBar';
import { BulkLockResult } from './BulkLockResult';
import { AccountsEmptyState } from './AccountsEmptyState';
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
 * Container / presentational (TASK-PC-FE-111, TASK-PC-FE-210): this container
 * owns ALL state + the 5 mutations + gating; the result table + pagination are
 * the prop-driven {@link AccountsTable}; the search bar
 * ({@link AccountsSearchBar}), the per-account bulk-lock result panel
 * ({@link BulkLockResult}) and the empty-state paragraph
 * ({@link AccountsEmptyState}) are prop-driven presentational siblings; the
 * pending-action model + confirm copy live in `accounts-screen-helpers`.
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

      <AccountsSearchBar
        emailInput={emailInput}
        onEmailInputChange={setEmailInput}
        onSubmit={submitSearch}
        selectedCount={selected.size}
        onBulkLock={() => openAction('bulk-lock', undefined, [...selected])}
      />

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

      {bulkResult && <BulkLockResult results={bulkResult} />}

      {!page || rows.length === 0 ? (
        <AccountsEmptyState
          isError={search.isError}
          error={search.error}
          hasEmailFilter={!!query.email}
        />
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
