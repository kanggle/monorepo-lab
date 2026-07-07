'use client';

import type { FormEvent } from 'react';
import {
  type OperatorPage,
  type OperatorSummary,
  type OperatorStatus,
} from '../api/types';
import { OperatorsFilterBar } from './OperatorsFilterBar';
import { OperatorRow } from './OperatorRow';
import { OperatorsPagination } from './OperatorsPagination';

/**
 * Operators list region of the operators-management screen (TASK-PC-FE-105
 * split) — the status-filter form, the transient list-error notice, the
 * operators table (with the per-row privilege-sensitive action buttons), and
 * the pagination nav. Pure presentation: all state + the action handlers live
 * in the `OperatorsScreen` container and arrive via props.
 *
 * TASK-PC-FE-209 split — the filter bar, the per-row action cluster, and the
 * pagination nav are now the presentational siblings `OperatorsFilterBar` /
 * `OperatorRow` / `OperatorsPagination`; this file stays the thin list frame
 * (degraded notice + empty state + table shell).
 */
export interface OperatorsTableProps {
  statusFilter: '' | OperatorStatus;
  onStatusFilterChange: (value: '' | OperatorStatus) => void;
  onSubmitFilter: (e: FormEvent) => void;
  isListError: boolean;
  rows: OperatorSummary[];
  page: OperatorPage | undefined;
  currentPage: number;
  onPrevPage: () => void;
  onNextPage: () => void;
  /** Caller's own operatorId — the per-row "프로파일 편집" is disabled on the
   *  self row (producer 400 is the fail-safe). `null` ⇒ gate inactive. */
  selfOperatorId: string | null;
  onEditRoles: (op: OperatorSummary) => void;
  onChangeStatus: (op: OperatorSummary) => void;
  onEditProfile: (op: OperatorSummary) => void;
  onOrgScope: (op: OperatorSummary) => void;
  /** TASK-PC-FE-157 — remove this operator's assignment to the active tenant.
   *  Omitted (⇒ button hidden) when no active tenant is resolved. */
  onUnassign?: (op: OperatorSummary) => void;
}

export function OperatorsTable({
  statusFilter,
  onStatusFilterChange,
  onSubmitFilter,
  isListError,
  rows,
  page,
  currentPage,
  onPrevPage,
  onNextPage,
  selfOperatorId,
  onEditRoles,
  onChangeStatus,
  onEditProfile,
  onOrgScope,
  onUnassign,
}: OperatorsTableProps) {
  return (
    <>
      <OperatorsFilterBar
        statusFilter={statusFilter}
        onStatusFilterChange={onStatusFilterChange}
        onSubmitFilter={onSubmitFilter}
      />

      {isListError && (
        <div
          role="status"
          data-testid="operators-degraded"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          운영자 목록을 일시적으로 불러올 수 없습니다. 잠시 후 다시
          시도하세요.
        </div>
      )}

      {rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="operators-empty"
        >
          표시할 운영자가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="data-table"
            data-testid="operators-table"
          >
            <caption className="sr-only">운영자 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  이메일
                </th>
                <th scope="col" className="p-2">
                  표시 이름
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  역할
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
              {rows.map((op) => (
                <OperatorRow
                  key={op.operatorId}
                  op={op}
                  selfOperatorId={selfOperatorId}
                  onEditRoles={onEditRoles}
                  onChangeStatus={onChangeStatus}
                  onEditProfile={onEditProfile}
                  onOrgScope={onOrgScope}
                  onUnassign={onUnassign}
                />
              ))}
            </tbody>
          </table>

          <OperatorsPagination
            page={page}
            currentPage={currentPage}
            onPrevPage={onPrevPage}
            onNextPage={onNextPage}
          />
        </>
      )}
    </>
  );
}
