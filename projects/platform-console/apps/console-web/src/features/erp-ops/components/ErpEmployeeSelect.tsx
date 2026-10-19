'use client';

import { useState } from 'react';
import type {
  EmployeePickerEntry,
  EmployeePickerListResponse,
} from '../api/types';
import { ACCOUNT_UNLINKED_LABEL } from './AccountLinkBadge';
import {
  EMPLOYEE_PICKER_LIST_LABEL,
  EMPLOYEE_PICKER_MANUAL_LABEL,
} from './employee-list-fallback';

/**
 * Shared employee-picker primitive (TASK-PC-FE-335 — 묶음 ② ID-input →
 * selector migration for the raw employee-id text inputs:
 * `ApprovalCreateDialog` 대상 ID (subjectType=EMPLOYEE) + 결재선 rows,
 * `DelegationScreen` create 대결자, `DelegationFactCard` 위임자/대행자 필터).
 * One page of up to `ERP_MAX_PAGE_SIZE` employees; the caller passes the
 * already-fetched `employees`/`isError`/`isLoading` (one picker query per
 * screen, reused across every select that screen renders — e.g.
 * `DelegationFactCard`'s two filters share one fetch). When the list cannot
 * be read the row falls back to a plain `<input>` with the SAME
 * `data-testid` so the screen keeps working either way — the producer is
 * the authority either way.
 *
 * TASK-ERP-BE-045 — the list is the «선택용» picker (`GET /employees/picker`),
 * whose elements carry the boolean `accountLinked` (never the account id).
 * The list may be narrowed to the operator's department subtree or truncated
 * at one page, so when the caller says `offerManual` the row shows a toggle
 * (`${testId}-manual-toggle`): «ID 직접 입력» swaps the select for the raw-id
 * input under the SAME `data-testid`, «목록에서 고르기» swaps back. A read
 * failure (403 included) still degrades straight to the input, no toggle.
 *
 * `employeeOptionLabel` is the ONE label rule (사번 · 이름 · 폐기 · 계정
 * 미연결); `ApprovalCreateDialog`'s `approverOptionLabel` delegates to it, so
 * the approver route and these pickers cannot drift apart.
 */
export function employeeOptionLabel(e: EmployeePickerEntry): string {
  const parts = [`${e.employeeNumber} · ${e.name}`];
  if (e.status === 'RETIRED') parts.push('폐기된 직원');
  if (!e.accountLinked) parts.push(ACCOUNT_UNLINKED_LABEL);
  return parts.join(' — ');
}

/**
 * What a screen needs from one picker response (TASK-ERP-BE-045).
 *   - `scoped`      — the producer narrowed the list to the subtree (`meta.scope === 'SUBTREE'`):
 *                     the screen shows the scoped notice.
 *   - `truncated`   — `totalElements` exceeds the rows shown (one page).
 *   - `offerManual` — the «ID 직접 입력» toggle is offered: the list is scoped OR truncated.
 *                     🔴 Fails toward offering it: a `scope` other than the documented `TENANT`
 *                     (absent or an unknown value) cannot prove the list is complete, so the
 *                     toggle stays available — hiding it would make an unlisted employee
 *                     impossible to name.
 */
export interface EmployeePickerView {
  employees: EmployeePickerEntry[];
  total: number;
  scoped: boolean;
  truncated: boolean;
  offerManual: boolean;
}

export function employeePickerView(
  data: EmployeePickerListResponse | undefined,
  isSuccess: boolean,
): EmployeePickerView {
  const employees = data?.data ?? [];
  const total = data?.meta.totalElements ?? employees.length;
  if (!isSuccess || !data) {
    return { employees, total, scoped: false, truncated: false, offerManual: false };
  }
  const scope = data.meta.scope;
  const scoped = scope === 'SUBTREE';
  const truncated = total > employees.length;
  return {
    employees,
    total,
    scoped,
    truncated,
    offerManual: scope !== 'TENANT' || truncated,
  };
}

export interface ErpEmployeeSelectProps {
  employees: EmployeePickerEntry[];
  isError: boolean;
  isLoading: boolean;
  value: string;
  onChange: (value: string) => void;
  testId: string;
  id?: string;
  ariaLabel?: string;
  /** `aria-label` of the raw-id input (fallback / manual mode); defaults to `ariaLabel`. */
  inputAriaLabel?: string;
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
  /** Raw-id `<input>` placeholder (read failure or manual mode). */
  inputPlaceholder?: string;
  className?: string;
  /** TASK-ERP-BE-045 — offer the «ID 직접 입력» toggle (`employeePickerView().offerManual`). */
  offerManual?: boolean;
  /** Controlled manual mode (optional) — a parent that renders a variable list of
   *  pickers (the 결재선 rows) keeps the flag beside the row value so removing a
   *  row cannot hand its mode to the next one. Uncontrolled when omitted. */
  manual?: boolean;
  onManualChange?: (manual: boolean) => void;
}

const ERP_EMPLOYEE_SELECT_CLASS =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground';
const ERP_EMPLOYEE_TOGGLE_CLASS =
  'mt-1 shrink-0 text-xs text-blue-700 hover:underline dark:text-blue-300';

export function ErpEmployeeSelect({
  employees,
  isError,
  isLoading,
  value,
  onChange,
  testId,
  id,
  ariaLabel,
  inputAriaLabel,
  blankOptionLabel,
  disableRetired,
  inputPlaceholder,
  className,
  offerManual,
  manual: manualProp,
  onManualChange,
}: ErpEmployeeSelectProps) {
  const [manualState, setManualState] = useState(false);
  const manualFlag = manualProp ?? manualState;
  function setManual(next: boolean) {
    if (onManualChange) onManualChange(next);
    if (manualProp === undefined) setManualState(next);
  }

  const input = (
    <input
      id={id}
      data-testid={testId}
      aria-label={inputAriaLabel ?? ariaLabel}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={inputPlaceholder ?? 'emp-…'}
      className={className ?? ERP_EMPLOYEE_SELECT_CLASS}
    />
  );

  // A read failure degrades straight to the input — there is no list to go back to.
  if (isError) return input;

  const toggleOn = Boolean(offerManual);
  const manualMode = toggleOn && manualFlag;

  function toggle() {
    if (manualMode) {
      // back to the list: a typed id the list does not hold would sit invisibly
      // behind the blank option and still be submitted — clear it instead.
      if (value && !employees.some((e) => e.id === value)) onChange('');
      setManual(false);
    } else {
      setManual(true);
    }
  }

  const toggleButton = toggleOn ? (
    <button
      type="button"
      data-testid={`${testId}-manual-toggle`}
      onClick={toggle}
      className={ERP_EMPLOYEE_TOGGLE_CLASS}
    >
      {manualMode ? EMPLOYEE_PICKER_LIST_LABEL : EMPLOYEE_PICKER_MANUAL_LABEL}
    </button>
  ) : null;

  if (manualMode) {
    return (
      <>
        {input}
        {toggleButton}
      </>
    );
  }

  return (
    <>
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
            data-unlinked={e.accountLinked ? undefined : 'true'}
          >
            {employeeOptionLabel(e)}
          </option>
        ))}
      </select>
      {toggleButton}
    </>
  );
}
