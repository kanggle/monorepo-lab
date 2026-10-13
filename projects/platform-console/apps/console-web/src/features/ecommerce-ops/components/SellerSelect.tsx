'use client';

import { useSellers } from '../hooks/use-ecommerce-sellers';
import { SELLER_MAX_PAGE_SIZE, type SellerSummary } from '../api/seller-types';

/**
 * `<SellerSelect>` — TASK-PC-FE-336, replaces the raw sellerId text input on
 * four settlement screens (balance lookup / commission-rate lookup / set form
 * / accrual-line filter) with a one-page `<select>` of real sellers (판단:
 * `listSellers` has no server-side search and the seller count is small — a
 * single page of up to {@link SELLER_MAX_PAGE_SIZE} is enough; mirrors the
 * TASK-PC-FE-318 approver selector).
 *
 * Label: «표시명 · sellerId · 상태» (status is shown, not hidden — this is a
 * read screen, the producer stays the status authority; a SUSPENDED/CLOSED
 * seller is still selectable, Edge Case "정지된 셀러").
 *
 * Resilience (AC-3): a seller-list fetch/parse failure (`useSellers().isError`)
 * falls back to the OLD free-text id input, on the SAME `data-testid` — the
 * caller never needs to branch on selector-vs-fallback; it only ever reads
 * `value`/`onChange`.
 *
 * Truncation notice (Edge Case "셀러 100개 초과"): when the producer's
 * `totalElements` exceeds what this one page loaded, a «처음 N개만 표시» note
 * renders under the select (same convention as `ApprovalCreateDialog`
 * `approval-create-approvers-truncated`).
 */
export interface SellerSelectProps {
  id?: string;
  value: string;
  onChange: (sellerId: string) => void;
  /** Base testid — also the testid of the rendered `<select>`/`<input>` itself
   *  (kept IDENTICAL across selector/fallback mode so callers/tests never branch). */
  testid: string;
  /** Label of the leading blank option (value `''`). Defaults to a neutral
   *  "pick a seller" prompt; the accrual filter overrides with «전체 셀러». */
  blankOptionLabel?: string;
}

function sellerOptionLabel(s: SellerSummary): string {
  return `${s.displayName} · ${s.sellerId} · ${s.status}`;
}

export function SellerSelect({
  id,
  value,
  onChange,
  testid,
  blankOptionLabel = '— 셀러 선택 —',
}: SellerSelectProps) {
  const sellersQ = useSellers({ page: 0, size: SELLER_MAX_PAGE_SIZE });
  const sellers: SellerSummary[] = sellersQ.data?.content ?? [];
  const total = sellersQ.data?.totalElements ?? sellers.length;
  const selectorMode = !sellersQ.isError;
  const loading = sellersQ.isLoading;

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

  if (!selectorMode) {
    return (
      <>
        <input
          id={id}
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          data-testid={testid}
          autoComplete="off"
          className={inputCls}
        />
        <p
          className="mt-1 text-xs text-muted-foreground"
          role="status"
          data-testid={`${testid}-fallback`}
        >
          셀러 목록을 불러오지 못해 셀러 ID를 직접 입력합니다.
        </p>
      </>
    );
  }

  return (
    <>
      <select
        id={id}
        value={value}
        disabled={loading}
        onChange={(e) => onChange(e.target.value)}
        data-testid={testid}
        className={inputCls}
      >
        <option value="">
          {loading ? '셀러 목록 불러오는 중…' : blankOptionLabel}
        </option>
        {sellers.map((s) => (
          <option key={s.sellerId} value={s.sellerId}>
            {sellerOptionLabel(s)}
          </option>
        ))}
      </select>
      {sellersQ.isSuccess && total > sellers.length && (
        <p
          className="mt-1 text-xs text-muted-foreground"
          data-testid={`${testid}-truncated`}
        >
          셀러가 많아 처음 {sellers.length}개만 표시합니다.
        </p>
      )}
    </>
  );
}
