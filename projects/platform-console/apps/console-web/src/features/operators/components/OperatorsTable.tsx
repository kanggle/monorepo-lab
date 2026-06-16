'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  OPERATOR_STATUSES,
  type OperatorPage,
  type OperatorSummary,
  type OperatorStatus,
} from '../api/types';
import { OperatorRoleChips, OperatorStatusBadge } from './OperatorBadges';

/**
 * Operators list region of the operators-management screen (TASK-PC-FE-105
 * split) — the status-filter form, the transient list-error notice, the
 * operators table (with the per-row privilege-sensitive action buttons), and
 * the pagination nav. Pure presentation: all state + the action handlers live
 * in the `OperatorsScreen` container and arrive via props.
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
}: OperatorsTableProps) {
  return (
    <>
      <form
        onSubmit={onSubmitFilter}
        className="mb-6 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="운영자 목록 필터"
      >
        <div>
          <label
            htmlFor="operators-status-filter"
            className="block text-sm font-medium text-foreground"
          >
            상태 필터
          </label>
          <select
            id="operators-status-filter"
            value={statusFilter}
            onChange={(e) =>
              onStatusFilterChange(e.target.value as '' | OperatorStatus)
            }
            data-testid="operators-status-filter"
            className="mt-1 w-48 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {OPERATOR_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="operators-filter-submit">
          조회
        </Button>
      </form>

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
                <tr
                  key={op.operatorId}
                  data-testid={`operator-row-${op.operatorId}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{op.email}</td>
                  <td className="p-2">{op.displayName}</td>
                  <td className="p-2">
                    <OperatorStatusBadge status={op.status} />
                  </td>
                  <td className="p-2">
                    <OperatorRoleChips roles={op.roles} />
                  </td>
                  <td className="p-2 text-muted-foreground">
                    {op.createdAt}
                  </td>
                  <td className="p-2">
                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onEditRoles(op)}
                        data-testid={`action-edit-roles-${op.operatorId}`}
                      >
                        역할 변경
                      </Button>
                      <Button
                        variant="secondary"
                        size="sm"
                        className={
                          op.status === 'SUSPENDED'
                            ? undefined
                            : 'text-destructive'
                        }
                        onClick={() => onChangeStatus(op)}
                        data-testid={`action-status-${op.operatorId}`}
                      >
                        {op.status === 'SUSPENDED'
                          ? '활성화'
                          : '정지'}
                      </Button>
                      {/* TASK-PC-FE-017 — admin-on-behalf-of profile edit.
                          Disabled on self row (producer-side rejection
                          `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
                          is the fail-safe; UX layer hides the always-400). */}
                      <Button
                        variant="secondary"
                        size="sm"
                        disabled={
                          selfOperatorId !== null &&
                          selfOperatorId === op.operatorId
                        }
                        title={
                          selfOperatorId !== null &&
                          selfOperatorId === op.operatorId
                            ? '자기 자신은 /operators 의 "내 프로파일" 영역에서 변경하세요.'
                            : undefined
                        }
                        onClick={() => onEditProfile(op)}
                        data-testid={`action-edit-profile-${op.operatorId}`}
                      >
                        프로파일 편집
                      </Button>
                      {/* TASK-PC-FE-050 — org_scope (데이터-스코프) per the
                          active tenant's assignment row. */}
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => onOrgScope(op)}
                        data-testid={`action-org-scope-${op.operatorId}`}
                      >
                        조직 스코프
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
              disabled={currentPage <= 0}
              onClick={onPrevPage}
              data-testid="operators-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="operators-pageinfo"
            >
              {page
                ? `${page.page + 1} / ${Math.max(1, page.totalPages)} 페이지 · 총 ${page.totalElements}명`
                : '—'}
            </span>
            <Button
              variant="secondary"
              disabled={
                !page || page.page + 1 >= page.totalPages
              }
              onClick={onNextPage}
              data-testid="operators-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </>
  );
}
