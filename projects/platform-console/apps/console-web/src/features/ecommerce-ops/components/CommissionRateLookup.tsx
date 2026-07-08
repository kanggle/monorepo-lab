'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useCommissionRate } from '../hooks/use-ecommerce-settlements';
import {
  rateBpsToPercent,
  commissionSourceLabel,
} from '../api/settlement-types';

/**
 * Commission-rate lookup (TASK-PC-FE-221 Phase A). A `sellerId`-driven read of
 * the effective commission rate (basis points) + its resolution source
 * (SELLER_OVERRIDE / PLATFORM_DEFAULT). Read-only — setting the rate (PUT) is
 * Phase B, no form here.
 *
 * Resilience: 404 SETTLEMENT_NOT_FOUND → inline not-found; 403 → forbidden;
 * 503/timeout → inline degraded (never a crash).
 */
export function CommissionRateLookup() {
  const fieldId = useId();
  const [draft, setDraft] = useState('');
  const [sellerId, setSellerId] = useState<string | null>(null);

  const q = useCommissionRate(sellerId);
  const apiError = q.error instanceof ApiError ? (q.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const notFound = apiError?.status === 404;
  const degraded = q.isError && !forbidden && !notFound;
  const rate = q.data;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    setSellerId(trimmed);
  }

  return (
    <section aria-labelledby="settlements-rate-heading" className="mb-10">
      <h2 id="settlements-rate-heading" className="mb-1 text-lg font-semibold">
        수수료율 조회
      </h2>
      <p className="mb-3 text-sm text-muted-foreground">
        셀러 ID 로 적용 중인 수수료율(basis points)과 그 출처(셀러 개별 설정 /
        플랫폼 기본값)를 조회합니다.
      </p>

      <form
        onSubmit={submit}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="수수료율 조회"
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
            data-testid="settlements-rate-seller-input"
            autoComplete="off"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button
          type="submit"
          data-testid="settlements-rate-submit"
          disabled={!draft.trim()}
        >
          수수료율 조회
        </Button>
      </form>

      {sellerId === null ? null : forbidden ? (
        <div
          role="status"
          data-testid="settlements-rate-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : notFound ? (
        <div
          role="status"
          data-testid="settlements-rate-notfound"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          해당 셀러의 수수료율을 찾을 수 없습니다.
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="settlements-rate-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          수수료율을 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.
        </div>
      ) : q.isPending ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-rate-loading"
        >
          조회 중…
        </p>
      ) : rate ? (
        <dl
          data-testid="settlements-rate-detail"
          className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2"
        >
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">셀러 ID</dt>
            <dd className="font-mono text-sm">{rate.sellerId}</dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">수수료율</dt>
            <dd className="tabular-nums text-sm">
              {rateBpsToPercent(rate.rateBps)}{' '}
              <span className="text-muted-foreground">({rate.rateBps} bps)</span>
            </dd>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <dt className="text-sm text-muted-foreground">출처</dt>
            <dd className="text-sm">{commissionSourceLabel(rate.source)}</dd>
          </div>
        </dl>
      ) : null}
    </section>
  );
}
