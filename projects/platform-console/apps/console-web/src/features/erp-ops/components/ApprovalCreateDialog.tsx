'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { APPROVAL_SUBJECT_TYPES } from '../api/approval-types';
import { ERP_MAX_PAGE_SIZE, type Department, type Employee } from '../api/types';
import {
  useCreateApproval,
  useDepartments,
  useEmployees,
} from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';
import { SUBJECT_LABEL } from './approval-common';
import { ACCOUNT_UNLINKED_LABEL } from './AccountLinkBadge';
import { ErpEmployeeSelect, employeeOptionLabel } from './ErpEmployeeSelect';
import { employeeListFallbackText } from './employee-list-fallback';

// ===========================================================================
// Create dialog — DRAFT request.
// ===========================================================================

/**
 * One approver option's label (TASK-PC-FE-318 AC-4). An employee without a
 * linked IAM account is marked «연결된 계정 없음» — NOT disabled: the owner
 * decision is «미연결 승인자 = 상신 거절, 선택기가 표시» — the producer refuses at
 * submit with 422 `APPROVAL_APPROVER_UNLINKED` (worded by `approval-error.ts`).
 * A retired employee stays listed (existing FK-selector rule — nothing hidden)
 * and is marked; submit refuses it with `approver_unresolved`.
 */
export function approverOptionLabel(e: Employee): string {
  // TASK-PC-FE-335 — one label rule for every employee picker in erp-ops.
  return employeeOptionLabel(e);
}

/**
 * One department option's label (TASK-PC-FE-335 AC-1 — 대상 ID DEPARTMENT
 * route). Mirrors `approverOptionLabel`'s shape with the fields the
 * `Department` type actually carries (`code` · `name`); a retired department
 * stays listed (E2 — never hidden) and is marked, same convention.
 */
export function subjectDepartmentOptionLabel(d: Department): string {
  const parts = [`${d.code} · ${d.name}`];
  if (d.status === 'RETIRED') parts.push('폐기된 부서');
  return parts.join(' — ');
}

export function ApprovalCreateDialog({ onClose }: { onClose: () => void }) {
  // TASK-PC-FE-318 — the approver route is an EMPLOYEE SELECTOR (was a raw-id
  // text input). One page of up to ERP_MAX_PAGE_SIZE employees; if the list
  // cannot be read, the row falls back to the old id input (same testid) so
  // the dialog still works — the producer is the authority either way.
  const employeesQ = useEmployees({ page: 0, size: ERP_MAX_PAGE_SIZE });
  const employees: Employee[] = employeesQ.data?.data ?? [];
  const employeeById = new Map(employees.map((e) => [e.id, e]));
  const totalEmployees = employeesQ.data?.meta.totalElements ?? employees.length;
  const selectorMode = !employeesQ.isError;
  const employeesLoading = employeesQ.isLoading;

  // TASK-PC-FE-335 AC-1/AC-2 — 대상 ID is a selector too now: EMPLOYEE reuses
  // the SAME employee fetch above (one page, cached by React Query); DEPARTMENT
  // reads its own list the same shape (one page of up to ERP_MAX_PAGE_SIZE,
  // fallback to the raw-id input on error, truncated notice over the cap).
  const departmentsQ = useDepartments({ page: 0, size: ERP_MAX_PAGE_SIZE });
  const departments: Department[] = departmentsQ.data?.data ?? [];
  const totalDepartments =
    departmentsQ.data?.meta.totalElements ?? departments.length;
  const departmentSelectorMode = !departmentsQ.isError;
  const departmentsLoading = departmentsQ.isLoading;

  const [subjectType, setSubjectType] = useState<string>(
    APPROVAL_SUBJECT_TYPES[0],
  );
  const [subjectId, setSubjectId] = useState('');
  // AC-2 — switching subjectType clears the previously-picked id AND swaps
  // the list the selector draws from (no stale cross-type id survives).
  function changeSubjectType(next: string) {
    setSubjectType(next);
    setSubjectId('');
  }
  const [title, setTitle] = useState('');
  // Ordered approver route — each element is an approver id string.
  // Starts with a single blank row (legacy-compatible: 1 row → approverId).
  const [approverRows, setApproverRows] = useState<string[]>(['']);
  const [reason, setReason] = useState('');
  const createM = useCreateApproval();

  // Trim + drop trailing blanks → cleaned list; valid if at least 1 non-blank.
  const cleanedApprovers = approverRows
    .map((r) => r.trim())
    .filter((r) => r !== '');

  const ok =
    subjectId.trim() !== '' &&
    title.trim() !== '' &&
    cleanedApprovers.length > 0;
  const canConfirm = ok && !createM.isPending;

  function newIdemKey(): string {
    const g = globalThis as unknown as {
      crypto?: { randomUUID?: () => string };
    };
    return (
      g.crypto?.randomUUID?.() ??
      `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
    );
  }

  function addApproverRow() {
    setApproverRows((prev) => [...prev, '']);
  }

  function removeApproverRow(idx: number) {
    setApproverRows((prev) => {
      if (prev.length <= 1) return prev; // at least 1 row
      return prev.filter((_, i) => i !== idx);
    });
  }

  function setApproverRow(idx: number, value: string) {
    setApproverRows((prev) =>
      prev.map((v, i) => (i === idx ? value : v)),
    );
  }

  function onConfirm() {
    if (!canConfirm) return;
    // 1 stage → legacy `approverId`; 2+ stages → `approverIds`.
    const approverPayload =
      cleanedApprovers.length === 1
        ? { approverId: cleanedApprovers[0] }
        : { approverIds: cleanedApprovers };
    createM.mutate(
      {
        input: {
          subjectType,
          subjectId: subjectId.trim(),
          title: title.trim(),
          ...approverPayload,
          ...(reason.trim() ? { reason: reason.trim() } : {}),
        },
        idempotencyKey: newIdemKey(),
      },
      { onSuccess: () => onClose() },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="approval-create-overlay"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="결재 요청 작성"
        data-testid="approval-create-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">
          결재 요청 작성
        </h2>

        <div className="mt-4">
          <label
            htmlFor="approval-create-subjectType"
            className="block text-sm font-medium text-foreground"
          >
            대상 유형 <span aria-hidden="true">*</span>
          </label>
          <select
            id="approval-create-subjectType"
            data-testid="approval-create-subjectType"
            value={subjectType}
            onChange={(e) => changeSubjectType(e.target.value)}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          >
            {APPROVAL_SUBJECT_TYPES.map((t) => (
              <option key={t} value={t}>
                {SUBJECT_LABEL[t] ?? t}
              </option>
            ))}
          </select>
        </div>

        <div className="mt-4">
          <label
            htmlFor="approval-create-subjectId"
            className="block text-sm font-medium text-foreground"
          >
            대상 ID <span aria-hidden="true">*</span>
          </label>
          {subjectType === 'EMPLOYEE' ? (
            <ErpEmployeeSelect
              employees={employees}
              isError={employeesQ.isError}
              isLoading={employeesLoading}
              value={subjectId}
              onChange={setSubjectId}
              testId="approval-create-subjectId"
              id="approval-create-subjectId"
              blankOptionLabel="— 대상 직원 선택 —"
              inputPlaceholder="emp-…"
            />
          ) : departmentSelectorMode ? (
            <select
              id="approval-create-subjectId"
              data-testid="approval-create-subjectId"
              value={subjectId}
              disabled={departmentsLoading}
              onChange={(e) => setSubjectId(e.target.value)}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            >
              <option value="">
                {departmentsLoading
                  ? '부서 목록 불러오는 중…'
                  : '— 대상 부서 선택 —'}
              </option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {subjectDepartmentOptionLabel(d)}
                </option>
              ))}
            </select>
          ) : (
            <input
              id="approval-create-subjectId"
              data-testid="approval-create-subjectId"
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              placeholder="dept-…"
            />
          )}
          {subjectType === 'EMPLOYEE' && employeesQ.isError && (
            <p
              className="mt-1 text-xs text-muted-foreground"
              role="status"
              data-testid="approval-create-subjectId-fallback"
            >
              {employeeListFallbackText(employeesQ.error, '대상')}
            </p>
          )}
          {subjectType === 'EMPLOYEE' &&
            employeesQ.isSuccess &&
            totalEmployees > employees.length && (
              <p
                className="mt-1 text-xs text-muted-foreground"
                data-testid="approval-create-subjectId-truncated"
              >
                직원이 많아 처음 {employees.length}명만 표시합니다.
              </p>
            )}
          {subjectType === 'DEPARTMENT' && departmentsQ.isError && (
            <p
              className="mt-1 text-xs text-muted-foreground"
              role="status"
              data-testid="approval-create-subjectId-dept-fallback"
            >
              부서 목록을 불러오지 못해 대상 부서 ID 를 직접 입력합니다.
            </p>
          )}
          {subjectType === 'DEPARTMENT' &&
            departmentsQ.isSuccess &&
            totalDepartments > departments.length && (
              <p
                className="mt-1 text-xs text-muted-foreground"
                data-testid="approval-create-subjectId-dept-truncated"
              >
                부서가 많아 처음 {departments.length}개만 표시합니다.
              </p>
            )}
          {subjectType === 'DEPARTMENT' &&
            departmentsQ.isSuccess &&
            departments.length === 0 && (
              <p
                className="mt-1 text-xs text-muted-foreground"
                data-testid="approval-create-subjectId-dept-empty"
              >
                등록된 부서가 없습니다.
              </p>
            )}
        </div>

        <div className="mt-4">
          <label
            htmlFor="approval-create-title"
            className="block text-sm font-medium text-foreground"
          >
            제목 <span aria-hidden="true">*</span>
          </label>
          <input
            id="approval-create-title"
            data-testid="approval-create-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={256}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          />
        </div>

        {/* Ordered approver route — 1~N rows (v2.0 multi-stage) */}
        <div className="mt-4">
          <p className="block text-sm font-medium text-foreground">
            결재선 <span aria-hidden="true">*</span>
            <span className="ml-1 text-xs font-normal text-muted-foreground">
              (순서대로 단계 결재자 선택)
            </span>
          </p>
          {employeesQ.isError && (
            <p
              className="mt-1 text-xs text-muted-foreground"
              role="status"
              data-testid="approval-create-approvers-fallback"
            >
              {employeeListFallbackText(employeesQ.error, '결재자')}
            </p>
          )}
          {employeesQ.isSuccess && totalEmployees > employees.length && (
            <p
              className="mt-1 text-xs text-muted-foreground"
              data-testid="approval-create-approvers-truncated"
            >
              직원이 많아 처음 {employees.length}명만 표시합니다.
            </p>
          )}
          <div className="mt-1 space-y-2">
            {approverRows.map((row, idx) => {
              const picked = row ? employeeById.get(row) : undefined;
              return (
              <div key={idx}>
              <div className="flex items-center gap-2">
                <span className="w-10 shrink-0 text-xs text-muted-foreground">
                  {idx + 1}단계
                </span>
                {selectorMode ? (
                  <select
                    data-testid={`approval-create-approver-${idx}`}
                    aria-label={`${idx + 1}단계 결재자`}
                    value={row}
                    disabled={employeesLoading}
                    onChange={(e) => setApproverRow(idx, e.target.value)}
                    className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                  >
                    <option value="">
                      {employeesLoading ? '직원 목록 불러오는 중…' : '— 결재자 선택 —'}
                    </option>
                    {employees.map((e) => (
                      <option
                        key={e.id}
                        value={e.id}
                        data-unlinked={e.accountId ? undefined : 'true'}
                      >
                        {approverOptionLabel(e)}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    data-testid={`approval-create-approver-${idx}`}
                    aria-label={`${idx + 1}단계 결재자 직원 ID`}
                    value={row}
                    onChange={(e) => setApproverRow(idx, e.target.value)}
                    className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                    placeholder="직원 ID"
                  />
                )}
                <button
                  type="button"
                  data-testid={`approval-create-remove-stage-${idx}`}
                  onClick={() => removeApproverRow(idx)}
                  disabled={approverRows.length <= 1}
                  className="rounded px-2 py-1 text-xs text-destructive hover:bg-destructive/10 disabled:cursor-not-allowed disabled:opacity-40"
                  aria-label={`${idx + 1}단계 삭제`}
                >
                  삭제
                </button>
              </div>
              {picked && !picked.accountId && (
                <p
                  className="ml-12 mt-1 text-xs text-destructive"
                  data-testid={`approval-create-approver-unlinked-${idx}`}
                >
                  이 직원은 {ACCOUNT_UNLINKED_LABEL} — 결재함에서 이 건을 볼 수 없어 상신이
                  거절됩니다.
                </p>
              )}
              </div>
              );
            })}
          </div>
          <button
            type="button"
            data-testid="approval-create-add-stage"
            onClick={addApproverRow}
            className="mt-2 text-xs text-blue-700 hover:underline dark:text-blue-300"
          >
            + 단계 추가
          </button>
        </div>

        <div className="mt-4">
          <label
            htmlFor="approval-create-reason"
            className="block text-sm font-medium text-foreground"
          >
            사유 (선택)
          </label>
          <textarea
            id="approval-create-reason"
            data-testid="approval-create-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            maxLength={512}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          />
        </div>

        {createM.error ? (
          <p
            className="mt-4 text-sm text-destructive"
            role="status"
            data-testid="approval-create-error"
          >
            {approvalErrorMessage(createM.error)}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={createM.isPending}
            data-testid="approval-create-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!canConfirm}
            data-testid="approval-create-submit"
          >
            {createM.isPending ? '처리 중…' : '작성'}
          </Button>
        </div>
      </div>
    </div>
  );
}
