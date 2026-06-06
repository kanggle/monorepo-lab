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
import { AccountStatusBadge } from './AccountStatusBadge';
import { ConfirmActionDialog } from './ConfirmActionDialog';

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
 */

type ActionKind =
  | 'lock'
  | 'unlock'
  | 'revoke-session'
  | 'gdpr-delete'
  | 'bulk-lock';

interface PendingAction {
  kind: ActionKind;
  /** Single-target ops. */
  account?: AccountSummary;
  /** bulk-lock targets. */
  accountIds?: string[];
  /** Stable across retries of THIS confirmed action. */
  idempotencyKey: string;
}

const ACTION_META: Record<
  ActionKind,
  { title: string; confirm: string; destructive: boolean }
> = {
  lock: { title: '계정 잠금', confirm: '잠금', destructive: true },
  unlock: { title: '계정 잠금 해제', confirm: '잠금 해제', destructive: true },
  'revoke-session': {
    title: '모든 세션 강제 종료',
    confirm: '세션 종료',
    destructive: true,
  },
  'gdpr-delete': {
    title: 'GDPR 삭제 (되돌릴 수 없음)',
    confirm: '영구 삭제',
    destructive: true,
  },
  'bulk-lock': {
    title: '선택 계정 일괄 잠금',
    confirm: '일괄 잠금',
    destructive: true,
  },
};

function newIdemKey(): string {
  const g = globalThis as unknown as { crypto?: { randomUUID?: () => string } };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export function AccountsScreen({ initial }: { initial: AccountPage }) {
  const [emailInput, setEmailInput] = useState('');
  const [query, setQuery] = useState<{
    email?: string;
    page: number;
    size: number;
  }>({ page: initial.page, size: initial.size });

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

      {search.isError && (
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
        <p
          className="text-sm text-muted-foreground"
          data-testid="accounts-empty"
        >
          {search.isError
            ? '목록을 불러올 수 없습니다.'
            : '표시할 계정이 없습니다. (검색 결과 없음 또는 조회 권한 없음)'}
        </p>
      ) : (
        <>
          <table
            className="data-table"
            data-testid="accounts-table"
          >
            <caption className="sr-only">계정 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  <span className="sr-only">일괄 선택</span>
                </th>
                <th scope="col" className="p-2">
                  이메일
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  생성일
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((acc) => (
                <tr
                  key={acc.id}
                  data-testid={`account-row-${acc.id}`}
                  className="border-b border-border"
                >
                  <td className="p-2">
                    <input
                      type="checkbox"
                      checked={selected.has(acc.id)}
                      onChange={() => toggleSelect(acc.id)}
                      aria-label={`${acc.email} 일괄 작업 선택`}
                      data-testid={`account-select-${acc.id}`}
                    />
                  </td>
                  <td className="p-2">{acc.email}</td>
                  <td className="p-2">
                    <AccountStatusBadge status={acc.status} />
                  </td>
                  <td className="p-2 text-muted-foreground">
                    {acc.createdAt}
                  </td>
                  <td className="p-2">
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        onClick={() => openAction('lock', acc)}
                        data-testid={`action-lock-${acc.id}`}
                      >
                        잠금
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => openAction('unlock', acc)}
                        data-testid={`action-unlock-${acc.id}`}
                      >
                        잠금 해제
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => openAction('revoke-session', acc)}
                        data-testid={`action-revoke-${acc.id}`}
                      >
                        세션 종료
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => exportAccount(acc)}
                        data-testid={`action-export-${acc.id}`}
                      >
                        내보내기
                      </Button>
                      <Button
                        variant="secondary"
                        className="text-destructive"
                        onClick={() => openAction('gdpr-delete', acc)}
                        data-testid={`action-gdpr-${acc.id}`}
                      >
                        GDPR 삭제
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <nav
            className="mt-4 flex items-center justify-between"
            aria-label="페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={query.page <= 0 || !!query.email}
              onClick={() =>
                setQuery((q) => ({ ...q, page: Math.max(0, q.page - 1) }))
              }
              data-testid="accounts-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="accounts-pageinfo"
            >
              {query.email
                ? '단건 검색'
                : `${page.page + 1} / ${Math.max(1, page.totalPages)} 페이지 · 총 ${page.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={
                !!query.email || page.page + 1 >= page.totalPages
              }
              onClick={() => setQuery((q) => ({ ...q, page: q.page + 1 }))}
              data-testid="accounts-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {pending && (
        <ConfirmActionDialog
          open
          title={ACTION_META[pending.kind].title}
          description={
            pending.kind === 'bulk-lock' ? (
              <>
                선택한 <strong>{pending.accountIds?.length ?? 0}</strong>개
                계정을 일괄 잠금합니다. 계정별 결과가 표시되며 일부 실패해도
                나머지는 처리됩니다.
              </>
            ) : pending.kind === 'gdpr-delete' ? (
              <>
                <strong>{pending.account?.email}</strong> 계정을 GDPR 삭제
                합니다. 이 작업은 <strong>되돌릴 수 없으며</strong> 개인정보가
                즉시 마스킹됩니다.
              </>
            ) : (
              <>
                <strong>{pending.account?.email}</strong> 계정에 대해 이
                작업을 수행합니다.
              </>
            )
          }
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
