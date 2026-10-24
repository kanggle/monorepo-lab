'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  ERP_MAX_PAGE_SIZE,
  type DelegationFactListResponse,
  type DelegationFactListQueryParams,
} from '../api/types';
import { delegationStatusTone } from '../api/delegation-types';
import { useDelegationFacts, useEmployeePicker } from '../hooks/use-erp-ops';
import { formatDateTime } from '@/shared/lib/datetime';
import { statusToneClass } from '@/shared/ui/StatusBadge';
import { ErpEmployeeSelect, employeePickerView } from './ErpEmployeeSelect';
import { ApprovalEmployeeRef } from './approval-refs';
import {
  EMPLOYEE_PICKER_SCOPED_NOTICE,
  employeeListFallbackText,
} from './employee-list-fallback';

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

  // TASK-PC-FE-335 AC-1/AC-5 — the two filters are EMPLOYEE SELECTORS now
  // (were raw-id text inputs); one shared fetch feeds both (retired
  // employees stay selectable — this is a historical filter, not a write).
  // The first option is "전체 …" (value `''`) — unchanged '' ⇒ the param is
  // simply omitted from the query (see `applyFilters` below).
  // TASK-ERP-BE-045 — the «선택용» picker list (open to 결재 변경 — NOT to 결재 읽기, so a
  // read-only approval operator still lands on the id-input fallback here).
  const employeesQ = useEmployeePicker({ page: 0, size: ERP_MAX_PAGE_SIZE });
  const picker = employeePickerView(employeesQ.data, employeesQ.isSuccess);
  const employees = picker.employees;

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
        <ErpEmployeeSelect
          employees={employees}
          isError={employeesQ.isError}
          isLoading={employeesQ.isLoading}
          value={filterDelegatorId}
          onChange={setFilterDelegatorId}
          testId="delegation-fact-filter-delegatorId"
          ariaLabel="위임자 (delegatorId)"
          blankOptionLabel="전체 위임자"
          inputPlaceholder="위임자 ID (delegatorId)"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
          offerManual={picker.offerManual}
        />
        <ErpEmployeeSelect
          employees={employees}
          isError={employeesQ.isError}
          isLoading={employeesQ.isLoading}
          value={filterDelegateId}
          onChange={setFilterDelegateId}
          testId="delegation-fact-filter-delegateId"
          ariaLabel="대행자 (delegateId)"
          blankOptionLabel="전체 대행자"
          inputPlaceholder="대행자 ID (delegateId)"
          className="rounded border border-border px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
          offerManual={picker.offerManual}
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
      {employeesQ.isError && (
        <p
          className="mb-3 text-xs text-muted-foreground"
          role="status"
          data-testid="delegation-fact-filter-employees-fallback"
        >
          {employeeListFallbackText(employeesQ.error, '위임자/대행자')}
        </p>
      )}
      {picker.scoped && (
        <p
          className="mb-3 text-xs text-muted-foreground"
          data-testid="delegation-fact-filter-employees-scoped"
        >
          {EMPLOYEE_PICKER_SCOPED_NOTICE}
        </p>
      )}
      {picker.truncated && (
        <p
          className="mb-3 text-xs text-muted-foreground"
          data-testid="delegation-fact-filter-employees-truncated"
        >
          직원이 많아 처음 {employees.length}명만 표시합니다.
        </p>
      )}

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
                    <DelegationFactStatusBadge status={fact.status} index={i} />
                  </td>
                  {/* TASK-PC-FE-371: employee ids (approval v2.4) — `번호 · 이름`, the id only in `title`. */}
                  <td className="p-2 text-sm">
                    <ApprovalEmployeeRef employeeId={fact.delegatorId} field="delegationFact.delegatorId" />
                  </td>
                  <td className="p-2 text-sm">
                    <ApprovalEmployeeRef employeeId={fact.delegateId} field="delegationFact.delegateId" />
                  </td>
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

/**
 * Status badge for delegation facts — ACTIVE / REVOKED (unknown → verbatim).
 *
 * Takes its palette from the shared tone map (TASK-PC-FE-242) rather than a
 * local colour ladder. It composes its own `<span>` instead of using
 * `<StatusBadge>` because it carries `data-status`, which the shared component
 * does not forward — the sanctioned `statusToneClass` escape hatch.
 *
 * A fact row has no `validTo`, so the expired-vs-revoked distinction the grant
 * list makes does not exist here; `expired` is left at its default. Note this
 * changes REVOKED from grey to red: the fact card and the grant list disagreed
 * on the same status, and the grant list's mapping (revoked = a withdrawal, not
 * a lapse) is the one that carries information.
 */
function DelegationFactStatusBadge({
  status,
  index,
}: {
  status: string;
  index: number;
}) {
  const label =
    status === 'ACTIVE' ? '활성' : status === 'REVOKED' ? '회수됨' : status;
  return (
    <span
      data-testid={`delegation-fact-status-${index}`}
      data-status={status}
      className={statusToneClass(delegationStatusTone(status))}
    >
      {label}
    </span>
  );
}
