'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import { useSellerBalance } from '../hooks/use-ecommerce-settlements';
import { minorToWon } from '../api/settlement-types';

/**
 * Seller settlement balance lookup (TASK-PC-FE-221 Phase A). The balance is a
 * `sellerId`-driven read (the producer exposes no seller list here), so the
 * operator types a sellerId and the console gates the query — it never fabricates
 * a non-existent seller-balance list. Read-only, no confirm.
 *
 * Resilience: 404 SETTLEMENT_NOT_FOUND → inline not-found; 403 → forbidden;
 * 503/timeout → inline degraded (never a crash).
 */
export function SellerBalanceLookup() {
  const fieldId = useId();
  const [draft, setDraft] = useState('');
  const [sellerId, setSellerId] = useState<string | null>(null);

  const q = useSellerBalance(sellerId);
  const apiError = q.error instanceof ApiError ? (q.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const notFound = apiError?.status === 404;
  const degraded = q.isError && !forbidden && !notFound;
  const balance = q.data;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    setSellerId(trimmed);
  }

  return (
    <section aria-labelledby="settlements-balance-heading" className="mb-10">
      <h2 id="settlements-balance-heading" className="mb-1 text-lg font-semibold">
        셀러 잔액 조회
      </h2>
      <p className="mb-3 text-sm text-muted-foreground">
        셀러 ID 로 누적 정산 잔액(적립 정산금 · 플랫폼 수수료 · 총액)을
        조회합니다.
      </p>

      <form
        onSubmit={submit}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="셀러 잔액 조회"
      >
        <div className="w-64">
          <label
            htmlFor={fieldId}
            className="block text-sm font-medium text-foreground"
          >
            셀러 ID
          </label>
          <input
            id={fieldId}
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            data-testid="settlements-balance-seller-input"
            autoComplete="off"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button
          type="submit"
          data-testid="settlements-balance-submit"
          disabled={!draft.trim()}
        >
          잔액 조회
        </Button>
      </form>

      {sellerId === null ? null : forbidden ? (
        <div
          role="status"
          data-testid="settlements-balance-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : notFound ? (
        <div
          role="status"
          data-testid="settlements-balance-notfound"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          해당 셀러의 정산 잔액을 찾을 수 없습니다.
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="settlements-balance-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          셀러 잔액을 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.
        </div>
      ) : q.isPending ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-balance-loading"
        >
          조회 중…
        </p>
      ) : balance ? (
        <dl
          data-testid="settlements-balance-detail"
          className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2"
        >
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">셀러 ID</dt>
            <dd className="font-mono text-sm">{balance.sellerId}</dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">적립 정산금</dt>
            <dd className="tabular-nums text-sm">
              {minorToWon(balance.accruedNetMinor)}
            </dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">플랫폼 수수료</dt>
            <dd className="tabular-nums text-sm">
              {minorToWon(balance.platformCommissionMinor)}
            </dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">총액</dt>
            <dd className="tabular-nums text-sm">
              {minorToWon(balance.grossMinor)}
            </dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">적립 라인 수</dt>
            <dd className="tabular-nums text-sm">{balance.accrualCount}</dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">기준 시각</dt>
            <dd className="text-sm text-muted-foreground">
              {formatDateTime(balance.asOf)}
            </dd>
          </div>
        </dl>
      ) : null}
    </section>
  );
}
