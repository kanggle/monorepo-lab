'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useScmPoList, useScmSkuBreakdown } from '../hooks/use-scm-ops';
import {
  SCM_DEFAULT_PAGE_SIZE,
  type PoPage,
  type PurchaseOrder,
  type PoQueryParams,
  type SnapshotResponse,
  type SnapshotRow,
  type StalenessResponse,
} from '../api/types';
import { S5Warning } from './S5Warning';
import { PoDetailDialog } from './PoDetailDialog';

/**
 * scm operations section (TASK-PC-FE-008 — ADR-MONO-013 Phase 4 slice 2,
 * the SECOND non-IAM federated domain; completes Phase 4).
 *
 * STRICTLY READ-ONLY. The section renders:
 *   - procurement PO list (filters + pagination) + read-only PO detail;
 *   - inventory-visibility cross-node snapshot table;
 *   - inventory-visibility per-SKU breakdown (with the X-Cache freshness);
 *   - inventory-visibility node staleness panel.
 *
 * S5 (§ 2.4.6, NORMATIVE): EVERY inventory-visibility view renders the
 * producer `meta.warning` PROMINENTLY via <S5Warning>. It is never
 * stripped / hidden / de-emphasised — it is a required, surfaced field of
 * each view-model. The PO surface has no such warning (PO is the
 * authoritative procurement record).
 *
 * There is NO mutation affordance anywhere — no submit/confirm/cancel, no
 * idempotency, no reason capture, no confirm-to-mutate. PO write actions
 * are buyer/business mutations, explicitly out of console scope.
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login — not surfaced here); 403/404/429 → inline actionable;
 * 503/timeout → this section degrades only (the console shell + the
 * GAP/wms sections stay intact).
 */

export interface ScmOpsScreenProps {
  poList: PoPage;
  snapshot: SnapshotResponse;
  staleness: StalenessResponse;
}

interface PoFilterState {
  status: string;
  supplierId: string;
}

const EMPTY_PO_FILTERS: PoFilterState = { status: '', supplierId: '' };

/** Tolerant: an unknown / future PoStatus renders generically (no throw —
 *  the closed set is the producer's; the console never gates on it). */
const KNOWN_PO_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'ACKNOWLEDGED',
  'CONFIRMED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'SETTLED',
  'CLOSED',
  'CANCELED',
];

function snapshotRows(snap: SnapshotResponse): SnapshotRow[] {
  // The snapshot data is the paginated cross-node form OR the single-node
  // array form — both render as a flat row list (tolerant).
  return Array.isArray(snap.data) ? snap.data : snap.data.content;
}

export function ScmOpsScreen({
  poList,
  snapshot,
  staleness,
}: ScmOpsScreenProps) {
  const statusFid = useId();
  const supplierFid = useId();
  const skuFid = useId();

  // ── procurement PO list (filters + pagination) ──────────────────────
  const [poFilters, setPoFilters] =
    useState<PoFilterState>(EMPTY_PO_FILTERS);
  const [poQuery, setPoQuery] = useState<PoQueryParams>({
    page: 0,
    size: poList.size || SCM_DEFAULT_PAGE_SIZE,
  });

  const poSeeded =
    (poQuery.page ?? 0) === 0 &&
    !poQuery.status &&
    !poQuery.supplierId;

  const po = useScmPoList(poQuery, poSeeded ? poList : undefined);
  const poData = po.data ?? poList;

  const poApiError =
    po.error instanceof ApiError ? (po.error as ApiError) : null;
  const poForbidden = poApiError?.status === 403;
  const poRateLimited = poApiError?.code === 'RATE_LIMIT_EXCEEDED';
  const poDegraded =
    po.isError && !poForbidden && !poRateLimited;

  const [detail, setDetail] = useState<PurchaseOrder | null>(null);

  function submitPoFilters(e: React.FormEvent) {
    e.preventDefault();
    setPoQuery({
      status: poFilters.status.trim() || undefined,
      supplierId: poFilters.supplierId.trim() || undefined,
      page: 0,
      size: poList.size || SCM_DEFAULT_PAGE_SIZE,
    });
  }

  const poRows = poData.content;
  const poTotalPages = Math.max(1, poData.totalPages ?? 1);

  // ── inventory-visibility per-SKU breakdown (on demand) ──────────────
  const [skuInput, setSkuInput] = useState('');
  const [skuQuery, setSkuQuery] = useState<string | null>(null);
  const skuQ = useScmSkuBreakdown(skuQuery);
  const skuApiError =
    skuQ.error instanceof ApiError ? (skuQ.error as ApiError) : null;

  const snapRows = useMemo(() => snapshotRows(snapshot), [snapshot]);
  const stalenessRows = staleness.data;

  return (
    <section aria-labelledby="scm-heading">
      <h1 id="scm-heading" className="mb-2 text-2xl font-semibold">
        SCM 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        조달(발주) 조회 · 재고 가시성 (읽기 전용). scm 운영 표면을 콘솔
        안에서 조회합니다. 발주 쓰기 작업은 콘솔 범위가 아닙니다.
      </p>

      {/* ── Procurement: PO list (read-only) ──────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        조달 — 발주(PO) 목록
      </h2>
      <form
        onSubmit={submitPoFilters}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="발주 목록 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={poFilters.status}
            onChange={(e) =>
              setPoFilters((f) => ({ ...f, status: e.target.value }))
            }
            data-testid="scm-po-filter-status"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {KNOWN_PO_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label
            htmlFor={supplierFid}
            className="block text-sm font-medium text-foreground"
          >
            공급사 ID
          </label>
          <input
            id={supplierFid}
            type="text"
            value={poFilters.supplierId}
            onChange={(e) =>
              setPoFilters((f) => ({ ...f, supplierId: e.target.value }))
            }
            data-testid="scm-po-filter-supplier"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="scm-po-filter-submit">
            조회
          </Button>
        </div>
      </form>

      {poForbidden ? (
        <div
          role="status"
          data-testid="scm-po-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : poRateLimited ? (
        <div
          role="status"
          data-testid="scm-po-ratelimited"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('RATE_LIMIT_EXCEEDED')}
        </div>
      ) : poDegraded ? (
        <div
          role="status"
          data-testid="scm-po-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          scm 발주 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : poRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-po-empty"
        >
          표시할 발주가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="scm-po-table"
          >
            <caption className="sr-only">발주 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  PO 번호
                </th>
                <th scope="col" className="p-2">
                  공급사
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  총액
                </th>
                <th scope="col" className="p-2">
                  생성 (UTC)
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {poRows.map((p, i) => (
                <tr
                  key={p.id}
                  data-testid={`scm-po-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{p.poNumber ?? p.id}</td>
                  <td className="p-2">{p.supplierId ?? '—'}</td>
                  <td className="p-2">{p.status ?? '—'}</td>
                  <td className="p-2">
                    {p.totalAmount ?? '—'} {p.currency ?? ''}
                  </td>
                  <td className="p-2">{p.createdAt ?? '—'}</td>
                  <td className="p-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setDetail(p)}
                      data-testid={`scm-po-detail-${i}`}
                    >
                      상세
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="발주 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(poQuery.page ?? 0) <= 0}
              onClick={() =>
                setPoQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="scm-po-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="scm-po-pageinfo"
            >
              {`${poData.page + 1} / ${poTotalPages} 페이지 · 총 ${poData.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={poData.page + 1 >= poTotalPages}
              onClick={() =>
                setPoQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="scm-po-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {/* ── inventory-visibility: snapshot (S5 warning REQUIRED) ──────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — 스냅샷
      </h2>
      <S5Warning warning={snapshot.meta.warning} />
      {snapRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-snap-empty"
        >
          표시할 스냅샷이 없습니다.
        </p>
      ) : (
        <table
          className="mb-8 data-table"
          data-testid="scm-snap-table"
        >
          <caption className="sr-only">재고 가시성 스냅샷</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                노드
              </th>
              <th scope="col" className="p-2">
                SKU
              </th>
              <th scope="col" className="p-2">
                수량
              </th>
              <th scope="col" className="p-2">
                신선도
              </th>
              <th scope="col" className="p-2">
                마지막 이벤트 (UTC)
              </th>
            </tr>
          </thead>
          <tbody>
            {snapRows.map((r, i) => (
              <tr
                key={r.id ?? `${r.nodeId ?? 'n'}-${r.sku ?? 's'}-${i}`}
                data-testid={`scm-snap-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{r.nodeId ?? '—'}</td>
                <td className="p-2">{r.sku ?? '—'}</td>
                <td className="p-2">{r.quantity ?? '—'}</td>
                <td className="p-2">{r.staleness ?? '—'}</td>
                <td className="p-2">{r.lastEventAt ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* ── inventory-visibility: per-SKU breakdown (S5 + X-Cache) ────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — SKU별 분포
      </h2>
      <S5Warning warning={snapshot.meta.warning} />
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setSkuQuery(skuInput.trim() || null);
        }}
        className="mb-4 flex items-end gap-3"
        role="search"
        aria-label="SKU 분포 조회"
      >
        <div className="flex-1">
          <label
            htmlFor={skuFid}
            className="block text-sm font-medium text-foreground"
          >
            SKU 코드
          </label>
          <input
            id={skuFid}
            type="text"
            value={skuInput}
            onChange={(e) => setSkuInput(e.target.value)}
            data-testid="scm-sku-input"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button type="submit" data-testid="scm-sku-submit">
          조회
        </Button>
      </form>
      {skuApiError ? (
        <div
          role="status"
          data-testid="scm-sku-error"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {skuApiError.status === 403
            ? messageForCode('TENANT_FORBIDDEN')
            : skuApiError.code === 'RATE_LIMIT_EXCEEDED'
              ? messageForCode('RATE_LIMIT_EXCEEDED')
              : messageForCode(
                  skuApiError.code,
                  'SKU 분포를 불러올 수 없습니다.',
                )}
        </div>
      ) : skuQ.data ? (
        <div className="mb-8" data-testid="scm-sku-result">
          {/* The per-SKU response carries its OWN required S5 meta.warning
              — surfaced prominently, never stripped. */}
          <S5Warning warning={skuQ.data.meta.warning} />
          <p className="mb-2 text-sm text-foreground">
            <span className="font-medium">{skuQ.data.data.sku}</span> · 총{' '}
            {skuQ.data.data.totalQuantity ?? '—'}
          </p>
          <table
            className="data-table"
            data-testid="scm-sku-table"
          >
            <caption className="sr-only">SKU별 노드 분포</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  노드
                </th>
                <th scope="col" className="p-2">
                  수량
                </th>
                <th scope="col" className="p-2">
                  신선도
                </th>
              </tr>
            </thead>
            <tbody>
              {skuQ.data.data.nodes.map((n, i) => (
                <tr
                  key={`${n.nodeId}-${i}`}
                  data-testid={`scm-sku-node-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{n.nodeId}</td>
                  <td className="p-2">{n.quantity ?? '—'}</td>
                  <td className="p-2">{n.staleness ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-sku-prompt"
        >
          조회할 SKU 코드를 입력하세요.
        </p>
      )}

      {/* ── inventory-visibility: node staleness panel (S5 + honest) ──── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — 노드 신선도
      </h2>
      <S5Warning warning={staleness.meta.warning} />
      {stalenessRows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="scm-staleness-empty"
        >
          표시할 노드가 없습니다.
        </p>
      ) : (
        <table
          className="data-table"
          data-testid="scm-staleness-table"
        >
          <caption className="sr-only">노드 신선도</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                노드
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                마지막 이벤트 (UTC)
              </th>
              <th scope="col" className="p-2">
                마지막 점검 (UTC)
              </th>
            </tr>
          </thead>
          <tbody>
            {stalenessRows.map((s, i) => {
              const status = s.stalenessStatus ?? 'UNKNOWN';
              const bad = status === 'STALE' || status === 'UNREACHABLE';
              return (
                <tr
                  key={`${s.nodeId}-${i}`}
                  data-testid={`scm-staleness-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{s.nodeId}</td>
                  <td className="p-2">
                    {/* Honest: a STALE / UNREACHABLE node is shown as
                        such, never hidden (§ 2.4.6 freshness honesty). */}
                    <span
                      data-testid={`scm-staleness-status-${i}`}
                      className={
                        bad
                          ? 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive'
                          : 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
                      }
                    >
                      {status}
                    </span>
                  </td>
                  <td className="p-2">{s.lastEventAt ?? '—'}</td>
                  <td className="p-2">{s.lastCheckedAt ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <PoDetailDialog
        open={detail !== null}
        po={detail}
        onClose={() => setDetail(null)}
      />
    </section>
  );
}
