'use client';

import type { Employee } from '../api/types';
import { ACCOUNT_UNLINKED_LABEL } from './AccountLinkBadge';

/**
 * Shared employee-picker primitive (TASK-PC-FE-335 — 묶음 ② ID-input →
 * selector migration for the 4 remaining raw employee-id text inputs:
 * `ApprovalCreateDialog` 대상 ID (subjectType=EMPLOYEE), `DelegationScreen`
 * create 대결자, `DelegationFactCard` 위임자/대행자 필터). Mirrors the
 * TASK-PC-FE-318 `ApprovalCreateDialog` approver-route precedent exactly:
 * one page of up to `ERP_MAX_PAGE_SIZE` employees; the caller passes the
 * already-fetched `employees`/`isError`/`isLoading` (one `useEmployees()`
 * call per screen, reused across every select that screen renders — e.g.
 * `DelegationFactCard`'s two filters share one fetch). When the list cannot
 * be read the row falls back to a plain `<input>` with the SAME
 * `data-testid` so the screen keeps working either way — the producer is
 * the authority either way (same contract as the approver route).
 *
 * `employeeOptionLabel` is the ONE label rule (사번 · 이름 · 폐기 · 계정
 * 미연결); `ApprovalCreateDialog`'s `approverOptionLabel` delegates to it, so
 * the approver route and these pickers cannot drift apart.
 */
export function employeeOptionLabel(e: Employee): string {
  const parts = [`${e.employeeNumber} · ${e.name}`];
  if (e.status === 'RETIRED') parts.push('폐기된 직원');
  if (!e.accountId) parts.push(ACCOUNT_UNLINKED_LABEL);
  return parts.join(' — ');
}

export interface ErpEmployeeSelectProps {
  employees: Employee[];
  isError: boolean;
  isLoading: boolean;
  value: string;
  onChange: (value: string) => void;
  testId: string;
  id?: string;
  ariaLabel?: string;
  /** First option's label when nothing is picked yet — e.g. "— 대상 직원
   *  선택 —" for a required field, or "전체 위임자" for a filter's "선택
   *  안 함 = 전체" row (value stays `''` either way). */
  blankOptionLabel: string;
  /** RETIRED employees render (never hidden) but cannot be CHOSEN — the
   *  delegate route only (`delegateId` must be an ACTIVE employee; the
   *  producer refuses otherwise with 422 `delegate_unresolved`). Every
   *  other route (대상 ID, the delegation-fact filters) leaves retired
   *  employees selectable. */
  disableRetired?: boolean;
  /** Fallback `<input>` placeholder shown only once the list fails to
   *  load and the row degrades to a raw-id text input. */
  inputPlaceholder?: string;
  className?: string;
}

const ERP_EMPLOYEE_SELECT_CLASS =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground';

export function ErpEmployeeSelect({
  employees,
  isError,
  isLoading,
  value,
  onChange,
  testId,
  id,
  ariaLabel,
  blankOptionLabel,
  disableRetired,
  inputPlaceholder,
  className,
}: ErpEmployeeSelectProps) {
  if (isError) {
    return (
      <input
        id={id}
        data-testid={testId}
        aria-label={ariaLabel}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={inputPlaceholder ?? 'emp-…'}
        className={className ?? ERP_EMPLOYEE_SELECT_CLASS}
      />
    );
  }
  return (
    <select
      id={id}
      data-testid={testId}
      aria-label={ariaLabel}
      value={value}
      disabled={isLoading}
      onChange={(e) => onChange(e.target.value)}
      className={className ?? ERP_EMPLOYEE_SELECT_CLASS}
    >
      <option value="">
        {isLoading ? '직원 목록 불러오는 중…' : blankOptionLabel}
      </option>
      {employees.map((e) => (
        <option
          key={e.id}
          value={e.id}
          disabled={Boolean(disableRetired && e.status === 'RETIRED')}
          data-unlinked={e.accountId ? undefined : 'true'}
        >
          {employeeOptionLabel(e)}
        </option>
      ))}
    </select>
  );
}
