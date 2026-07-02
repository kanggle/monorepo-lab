'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  type DelegationFactListResponse,
  type DelegationFactListQueryParams,
} from '../api/types';
import { useDelegationFacts } from '../hooks/use-erp-ops';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * ERP "위임 현황" read-only card (TASK-PC-FE-055 — ADR-MONO-013 §D3.1).
 *
 * Renders the `read-model-service` delegation-fact projection:
 * each delegation grant as a row with status badge (ACTIVE/REVOKED),
 * delegatorId, delegateId, validFrom, validTo (ABSENT→"무기한"), reason
 * (present when supplied), revokedAt (present when REVOKED).
 *
 * E5 eventually-consistent: `meta.warning` is shown as a banner at the
 * card top. ABSENT fields (validFrom/validTo/reason/revokedAt) are handled
 * gracefully — shown as "—" or hidden, NEVER throwing on absent values
 * (NON_NULL-absent tolerance, zod `.optional()` + `.passthrough()`).
 *
 * Filters: delegatorId, delegateId, status (ACTIVE|REVOKED), activeAt
 * (ISO instant). The console passes filters verbatim to the read-model;
 * the producer is the authority for filter semantics.
 *
 * READ-ONLY — there are NO write affordances on this card (PC-FE-054
 * "위임(관리)" is the write surface; this card is read-model org-scoped
 * reporting only). The distinction is also in the nav label: "위임 현황"
 * (this card) vs "위임(관리)" (PC-FE-054 DelegationScreen).
 */
export interface DelegationFactCardProps {
  initial?: DelegationFactListResponse;
}

export function DelegationFactCard({ initial }: DelegationFactCardProps) {
  const [query, setQuery] = useState<DelegationFactListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  // Filter inputs — local state (not URL-bound, unlike asOf which is
  // URL-bound for the masterdata E3 invariant; delegation-fact filters
  // are card-local, matching the EmployeeOrgViewCard precedent).
  const [filterDelegatorId, setFilterDelegatorId] = useState('');
  const [filterDelegateId, setFilterDelegateId] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterActiveAt, setFilterActiveAt] = useState('');

  const q = useDelegationFacts(query, initial);

  const resp = q.data ?? initial ?? {
    data: [],
    meta: { page: 0, size: 20, totalElements: 0 },
  };
  const rows = resp.data ?? [];
  const warning: string | undefined = resp.meta.warning;
  const totalElements = resp.meta.totalElements ?? rows.length;
  const size = resp.meta.size ?? 20;
  const page = resp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  function applyFilters() {
    setQuery({
      page: 0,
      size: initial?.meta.size ?? 20,
      ...(filterDelegatorId.trim() ? { delegatorId: filterDelegatorId.trim() } : {}),
      ...(filterDelegateId.trim() ? { delegateId: filterDelegateId.trim() } : {}),
      ...(filterStatus ? { status: filterStatus } : {}),
      ...(filterActiveAt.trim() ? { activeAt: filterActiveAt.trim() } : {}),
    });
  }

  function clearFilters() {
    setFilterDelegatorId('');
    setFilterDelegateId('');
    setFilterStatus('');
    setFilterActiveAt('');
    setQuery({ page: 0, size: initial?.meta.size ?? 20 });
  }

  return (
    <section aria-labelledby="delegation-facts-heading" data-testid="delegation-fact-card">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="delegation-facts-heading"
          className="text-lg font-medium text-foreground"
        >
          위임 현황 (delegation facts)
        </h2>
      </div>

      {/* Eventually-consistent warning banner (AC-2) */}
      {warning && (
        <div
          role="status"
          data-testid="delegation-fact-warning"
          className="mb-3 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {warning}
        </div>
      )}

      {/* Filters */}
      <div
        className="mb-3 flex flex-wrap gap-2 text-sm"
        data-testid="delegation-fact-filters"
      >
        <input
          type="text"
          placeholder="위임자 ID (delegatorId)"
          value={filterDelegatorId}
          onChange={(e) => setFilterDelegatorId(e.target.value)}
          data-testid="delegation-fact-filter-delegatorId"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
        />
        <input
          type="text"
          placeholder="대행자 ID (delegateId)"
          value={filterDelegateId}
          onChange={(e) => setFilterDelegateId(e.target.value)}
          data-testid="delegation-fact-filter-delegateId"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
        />
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          data-testid="delegation-fact-filter-status"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
        >
          <option value="">전체 상태</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="REVOKED">REVOKED</option>
        </select>
        <input
          type="text"
          placeholder="기준 시각 (activeAt ISO instant)"
          value={filterActiveAt}
          onChange={(e) => setFilterActiveAt(e.target.value)}
          data-testid="delegation-fact-filter-activeAt"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
        />
        <Button
          variant="secondary"
          onClick={applyFilters}
          data-testid="delegation-fact-filter-apply"
        >
          조회
        </Button>
        <Button
          variant="secondary"
          onClick={clearFilters}
          data-testid="delegation-fact-filter-clear"
        >
          초기화
        </Button>
      </div>

      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="delegation-fact-empty"
        >
          조회된 위임 현황이 없습니다.
          {warning ? ' (read-model 이벤트 투영 대기 중)' : ''}
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="delegation-fact-table">
            <caption className="sr-only">위임 현황 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">권한 ID</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">위임자</th>
                <th scope="col" className="p-2">대행자</th>
                <th scope="col" className="p-2">범위</th>
                <th scope="col" className="p-2">유효시작</th>
                <th scope="col" className="p-2">유효종료</th>
                <th scope="col" className="p-2">사유</th>
                <th scope="col" className="p-2">회수시각</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((fact, i) => (
                <tr
                  key={fact.grantId}
                  data-testid={`delegation-fact-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 text-sm font-mono">{fact.grantId}</td>
                  <td className="p-2">
                    <StatusBadge status={fact.status} index={i} />
                  </td>
                  <td className="p-2 text-sm">{fact.delegatorId}</td>
                  <td className="p-2 text-sm">{fact.delegateId}</td>
                  {/* scope: NON_NULL-absent → graceful "—" (BE-018) */}
                  <td className="p-2 text-sm" data-testid={`delegation-fact-scope-${i}`}>
                    <ScopeCell scope={fact.scope} scopeRequestId={fact.scopeRequestId} />
                  </td>
                  {/* validFrom: NON_NULL-absent → graceful "—" */}
                  <td className="p-2 text-sm" data-testid={`delegation-fact-validFrom-${i}`}>
                    {formatDateTime(fact.validFrom, '—')}
                  </td>
                  {/* validTo: NON_NULL-absent → "무기한" (open-ended) */}
                  <td className="p-2 text-sm" data-testid={`delegation-fact-validTo-${i}`}>
                    {formatDateTime(fact.validTo, '무기한')}
                  </td>
                  {/* reason: NON_NULL-absent → hidden (empty cell) */}
                  <td className="p-2 text-sm" data-testid={`delegation-fact-reason-${i}`}>
                    {fact.reason ?? ''}
                  </td>
                  {/* revokedAt: NON_NULL-absent → hidden (empty cell; only relevant when REVOKED) */}
                  <td className="p-2 text-sm" data-testid={`delegation-fact-revokedAt-${i}`}>
                    {formatDateTime(fact.revokedAt, '')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="위임 현황 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({ ...s, page: Math.max(0, (s.page ?? 0) - 1) }))
              }
              data-testid="delegation-fact-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="delegation-fact-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="delegation-fact-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}

/** Scope cell for delegation facts — GLOBAL / REQUEST / absent / unknown. */
function ScopeCell({
  scope,
  scopeRequestId,
}: {
  scope: string | undefined;
  scopeRequestId: string | undefined;
}) {
  if (scope === undefined) return <>—</>;
  if (scope === 'GLOBAL') {
    return (
      <span className="rounded bg-blue-100 px-1.5 py-0.5 text-xs text-blue-800 dark:bg-blue-950/60 dark:text-blue-100">
        전체
      </span>
    );
  }
  if (scope === 'REQUEST') {
    return (
      <>
        <span className="rounded bg-indigo-100 px-1.5 py-0.5 text-xs text-indigo-800 dark:bg-indigo-950/60 dark:text-indigo-100">
          특정 건
        </span>
        {scopeRequestId && (
          <> · <code className="text-xs font-mono">{scopeRequestId}</code></>
        )}
      </>
    );
  }
  // Unknown future scope value — render verbatim (free-string tolerance).
  return <>{scope}</>;
}

/** Status badge for delegation facts — ACTIVE / REVOKED. */
function StatusBadge({ status, index }: { status: string; index: number }) {
  const isActive = status === 'ACTIVE';
  const isRevoked = status === 'REVOKED';
  const label = isActive ? '활성' : isRevoked ? '회수됨' : status;
  const className = isActive
    ? 'rounded bg-green-100 px-1.5 py-0.5 text-xs text-green-800 dark:bg-green-950/60 dark:text-green-100'
    : isRevoked
      ? 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
      : 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground';
  return (
    <span
      data-testid={`delegation-fact-status-${index}`}
      data-status={status}
      className={className}
    >
      {label}
    </span>
  );
}
