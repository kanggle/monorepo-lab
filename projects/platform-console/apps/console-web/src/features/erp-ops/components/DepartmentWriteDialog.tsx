'use client';

import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import { type Department } from '../api/types';
import {
  useDepartmentWrite,
  departmentWriteErrorMessage,
  type DeptWriteMode,
  type DeptWriteRequest,
} from './use-department-write';

/**
 * Department write PILOT dialog (TASK-PC-FE-046 / § 2.4.8 *Department
 * write binding (PILOT)*). The single confirm-gated surface for the
 * four department mutations — clones the operators reason+confirm+
 * `Idempotency-Key` pattern, adapted to the erp producer contract:
 *
 *   - **create / update**: NO reason field (the producer has no reason
 *     slot — capturing a phantom reason would be dishonest); confirm
 *     enables once the required fields are valid.
 *   - **retire / move-parent**: a typed `reason` (the producer DOES
 *     have a body slot) — retire requires it, move-parent accepts it.
 *
 * Every attempt generates a fresh `Idempotency-Key` (E1 / T1; the
 * `crypto.randomUUID()` pattern) threaded through the body → the
 * same-origin POST proxy → the api-client header. Producer errors
 * (`409` duplicate/reference/cycle/idempotency-conflict, `422`
 * effective-period, `403 PERMISSION_DENIED`) surface inline-actionably
 * — the console never pre-judges write authority (producer is the
 * authority).
 *
 * TASK-PC-FE-151 — behaviour-preserving HOOK-ONLY split: all state /
 * validation / mutations / `onConfirm` / error mapping live in
 * `use-department-write.ts`; this component is presentation only. Render
 * output / DOM / data-testid / ARIA / wire bodies are unchanged.
 */

export type { DeptWriteMode, DeptWriteRequest };

export interface DepartmentWriteDialogProps {
  request: DeptWriteRequest;
  onClose: () => void;
  /** TASK-PC-FE-047: existing departments for the parent picker (create's
   *  상위 부서 + move-parent's 새 상위 부서). The operator selects an existing
   *  department instead of typing a raw UUID. Passed by `DepartmentList` from
   *  its loaded rows (current page — sufficient at the pilot's scale; a full-
   *  list fetch is a future enhancement). Retired departments are excluded as
   *  parent options (the producer rejects parenting under a retired master);
   *  move-parent additionally excludes the target itself. */
  departments?: Department[];
}

export function DepartmentWriteDialog({
  request,
  onClose,
  departments = [],
}: DepartmentWriteDialogProps) {
  const {
    mode,
    target,
    parentOptions,
    moveParentOptions,
    code,
    setCode,
    name,
    setName,
    parentId,
    setParentId,
    effectiveFrom,
    setEffectiveFrom,
    reason,
    setReason,
    newParentId,
    setNewParentId,
    pending,
    error,
    title,
    destructive,
    reasonOk,
    canConfirm,
    onConfirm,
  } = useDepartmentWrite(request, onClose, departments);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="erp-dept-write-overlay"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-testid="erp-dept-write-dialog"
        data-mode={mode}
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2
          className={
            destructive
              ? 'text-lg font-semibold text-destructive'
              : 'text-lg font-semibold text-foreground'
          }
        >
          {title}
        </h2>
        {target && (
          <p className="mt-1 text-sm text-muted-foreground">
            대상: <span className="font-medium">{target.code}</span> ·{' '}
            {target.name}
          </p>
        )}

        {/* create / update fields */}
        {mode === 'create' && (
          <div className="mt-4">
            <label
              htmlFor="erp-dept-code"
              className="block text-sm font-medium text-foreground"
            >
              코드 <span aria-hidden="true">*</span>
            </label>
            <input
              id="erp-dept-code"
              data-testid="erp-dept-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              maxLength={64}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
          </div>
        )}
        {(mode === 'create' || mode === 'update') && (
          <>
            <div className="mt-4">
              <label
                htmlFor="erp-dept-name"
                className="block text-sm font-medium text-foreground"
              >
                이름 <span aria-hidden="true">*</span>
              </label>
              <input
                id="erp-dept-name"
                data-testid="erp-dept-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={256}
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              />
            </div>
            {mode === 'create' && (
              <div className="mt-4">
                <label
                  htmlFor="erp-dept-parent-id"
                  className="block text-sm font-medium text-foreground"
                >
                  상위 부서 (선택 — 비우면 최상위)
                </label>
                <select
                  id="erp-dept-parent-id"
                  data-testid="erp-dept-parent-id"
                  value={parentId}
                  onChange={(e) => setParentId(e.target.value)}
                  className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                >
                  <option value="">— 최상위 (상위 없음) —</option>
                  {parentOptions.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.code} · {d.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <div className="mt-4">
              <label
                htmlFor="erp-dept-effective-from"
                className="block text-sm font-medium text-foreground"
              >
                유효 시작일 (선택 — 비우면 오늘)
              </label>
              <input
                id="erp-dept-effective-from"
                data-testid="erp-dept-effective-from"
                type="date"
                value={effectiveFrom}
                onChange={(e) => setEffectiveFrom(e.target.value)}
                onClick={showPickerOnClick}
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              />
            </div>
          </>
        )}

        {/* move-parent fields */}
        {mode === 'move-parent' && (
          <>
            <div className="mt-4">
              <label
                htmlFor="erp-dept-new-parent-id"
                className="block text-sm font-medium text-foreground"
              >
                새 상위 부서 (비우면 최상위로 승격)
              </label>
              <select
                id="erp-dept-new-parent-id"
                data-testid="erp-dept-new-parent-id"
                value={newParentId}
                onChange={(e) => setNewParentId(e.target.value)}
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              >
                <option value="">— 최상위로 승격 (상위 없음) —</option>
                {moveParentOptions.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.code} · {d.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="mt-4">
              <label
                htmlFor="erp-dept-effective-from"
                className="block text-sm font-medium text-foreground"
              >
                유효 시작일 <span aria-hidden="true">*</span>
              </label>
              <input
                id="erp-dept-effective-from"
                data-testid="erp-dept-effective-from"
                type="date"
                value={effectiveFrom}
                onChange={(e) => setEffectiveFrom(e.target.value)}
                onClick={showPickerOnClick}
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              />
            </div>
          </>
        )}

        {/* reason — retire (required) / move-parent (optional). create /
            update have NO reason field (no producer slot). */}
        {destructive && (
          <div className="mt-4">
            <label
              htmlFor="erp-dept-reason"
              className="block text-sm font-medium text-foreground"
            >
              사유{' '}
              {mode === 'retire' ? (
                <span aria-hidden="true">*</span>
              ) : (
                <span className="text-muted-foreground">(선택)</span>
              )}
            </label>
            <textarea
              id="erp-dept-reason"
              data-testid="erp-dept-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              maxLength={256}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              placeholder={
                mode === 'retire'
                  ? '폐기 사유를 입력하세요 (감사 기록에 남습니다)'
                  : '이동 사유 (선택, 감사 기록)'
              }
            />
            {mode === 'retire' && !reasonOk && (
              <p
                className="mt-1 text-xs text-destructive"
                data-testid="erp-dept-reason-error"
                role="status"
              >
                폐기에는 사유가 필요합니다.
              </p>
            )}
          </div>
        )}

        {error && (
          <p
            className="mt-4 text-sm text-destructive"
            data-testid="erp-dept-write-error"
            role="status"
          >
            {departmentWriteErrorMessage(error)}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={pending}
            data-testid="erp-dept-write-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!canConfirm}
            data-testid="erp-dept-write-submit"
            className={
              destructive
                ? 'bg-destructive text-destructive-foreground hover:opacity-90'
                : undefined
            }
          >
            {pending ? '처리 중…' : '확인'}
          </Button>
        </div>
      </div>
    </div>
  );
}
