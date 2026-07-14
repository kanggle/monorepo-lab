'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import {
  masterStatusTone,
  type EmployeeOrgViewListResponse,
  type OrgViewListQueryParams,
} from '../api/types';
import { useEmployeeOrgViews } from '../hooks/use-erp-ops';

/**
 * ERP "통합 조회" card (TASK-PC-FE-049 — ADR-MONO-016 § D3).
 *
 * Renders the `read-model-service` employee org-view projection:
 * employee per row with **department path breadcrumb** (root→leaf,
 * joined by ›), cost center and job grade.
 *
 * E5 eventually-consistent: `meta.warning` is shown as a banner at
 * the card top. A field that is `null` (reference not yet projected)
 * OR appears in `meta.unresolved` renders a **"동기화 중" badge**
 * instead of crashing or fabricating a value.
 *
 * READ-ONLY — there are NO write affordances on this card (the
 * read-model holds no domain logic; E5). `AsOfPicker` (E3) is
 * sourced from the shared URL param via `useEmployeeOrgViews` /
 * `useThreadedAsOf`.
 */
export interface EmployeeOrgViewCardProps {
  initial?: EmployeeOrgViewListResponse;
}

/** Formats the department path array as a ›-joined breadcrumb string.
 *  Returns `—` when the path is empty or absent. */
function formatDeptPath(
  path: Array<{ code: string; name: string }> | undefined | null,
): string {
  if (!path || path.length === 0) return '—';
  return path.map((n) => `${n.code} · ${n.name}`).join(' › ');
}

export function EmployeeOrgViewCard({ initial }: EmployeeOrgViewCardProps) {
  const [query, setQuery] = useState<OrgViewListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useEmployeeOrgViews(query, initial);

  const resp = q.data ?? initial ?? {
    data: [],
    meta: { page: 0, size: 20, totalElements: 0 },
  };
  const rows = resp.data ?? [];
  const warning: string | undefined = resp.meta.warning;
  const unresolved: string[] = resp.meta.unresolved ?? [];
  const totalElements = resp.meta.totalElements ?? rows.length;
  const size = resp.meta.size ?? 20;
  const page = resp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-orgview-heading" data-testid="erp-orgview-card">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="erp-orgview-heading"
          className="text-lg font-medium text-foreground"
        >
          통합 조회 (employee org-view)
        </h2>
      </div>

      {/* Eventually-consistent warning banner (AC-4) */}
      {warning && (
        <div
          role="status"
          data-testid="erp-orgview-warning"
          className="mb-3 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {warning}
        </div>
      )}

      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-orgview-empty"
        >
          조회된 통합 직원 정보가 없습니다.
          {warning ? ' (read-model 이벤트 투영 대기 중)' : ''}
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="erp-orgview-table">
            <caption className="sr-only">통합 조회 직원 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">사번</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">부서 경로</th>
                <th scope="col" className="p-2">비용센터</th>
                <th scope="col" className="p-2">직급</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((emp, i) => {
                // A field is "unresolved" when the field is null OR
                // the field name appears in meta.unresolved.
                const deptUnresolved =
                  emp.department === null || unresolved.includes('department');
                const ccUnresolved =
                  emp.costCenter === null || unresolved.includes('costCenter');
                const jgUnresolved =
                  emp.jobGrade === null || unresolved.includes('jobGrade');

                return (
                  <tr
                    key={emp.id}
                    data-testid={`erp-orgview-row-${i}`}
                    className="border-b border-border"
                  >
                    <td className="p-2">{emp.employeeNumber}</td>
                    <td className="p-2">{emp.name}</td>
                    <td className="p-2">
                      <StatusBadge
                        tone={masterStatusTone(emp.status)}
                        data-testid={`erp-orgview-status-${i}`}
                      >
                        {emp.status}
                      </StatusBadge>
                    </td>
                    <td className="p-2" data-testid={`erp-orgview-dept-${i}`}>
                      {deptUnresolved ? (
                        <SyncBadge />
                      ) : (
                        <span className="text-sm">
                          {formatDeptPath(emp.department?.path)}
                        </span>
                      )}
                    </td>
                    <td className="p-2" data-testid={`erp-orgview-cc-${i}`}>
                      {ccUnresolved ? (
                        <SyncBadge />
                      ) : (
                        <span className="text-sm">
                          {emp.costCenter
                            ? `${emp.costCenter.code} · ${emp.costCenter.name}`
                            : '—'}
                        </span>
                      )}
                    </td>
                    <td className="p-2" data-testid={`erp-orgview-jg-${i}`}>
                      {jgUnresolved ? (
                        <SyncBadge />
                      ) : (
                        <span className="text-sm">
                          {emp.jobGrade
                            ? `${emp.jobGrade.code} · ${emp.jobGrade.name}`
                            : '—'}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="통합 조회 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({ ...s, page: Math.max(0, (s.page ?? 0) - 1) }))
              }
              data-testid="erp-orgview-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-orgview-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-orgview-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}

/** "동기화 중" badge — shown when an org-view reference is unresolved
 *  (the master event has not yet been projected into the read-model).
 *  NEVER crash on an unresolved reference (AC-4). */
function SyncBadge() {
  return (
    <StatusBadge tone="warning" data-testid="erp-orgview-sync-badge">
      동기화 중
    </StatusBadge>
  );
}
