'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError } from '@/shared/api/errors';
import { isRetired, type Department } from '../api/types';
import {
  useCreateDepartment,
  useUpdateDepartment,
  useRetireDepartment,
  useMoveDepartmentParent,
} from '../hooks/use-erp-ops';

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
 */

export type DeptWriteMode = 'create' | 'update' | 'retire' | 'move-parent';

export interface DeptWriteRequest {
  mode: DeptWriteMode;
  /** Target row for update / retire / move-parent (absent for create). */
  target?: Department;
}

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

/** Generates an Idempotency-Key (E1 / T1). Mirrors the operators
 *  `newIdemKey()` — `crypto.randomUUID()` with a defensive fallback. */
function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'MASTERDATA_DUPLICATE_KEY':
        return '같은 코드의 부서가 이미 있습니다.';
      case 'MASTERDATA_REFERENCE_VIOLATION':
        return '이 부서를 참조하는 직원·비용센터·하위부서가 있어 폐기할 수 없습니다.';
      case 'MASTERDATA_PARENT_CYCLE':
        return '상위 부서로 이동할 수 없습니다 (순환 구조).';
      case 'MASTERDATA_EFFECTIVE_PERIOD_INVALID':
        return '유효 시작일이 올바르지 않습니다.';
      case 'IDEMPOTENCY_KEY_CONFLICT':
      case 'CONCURRENT_MODIFICATION':
        return '동시 변경이 감지되었습니다. 새로고침 후 다시 시도하세요.';
      case 'MASTERDATA_NOT_FOUND':
        return '대상 부서를 찾을 수 없습니다.';
      case 'PERMISSION_DENIED':
      case 'DATA_SCOPE_FORBIDDEN':
      case 'TENANT_FORBIDDEN':
        return '이 작업을 수행할 권한이 없습니다.';
      default:
        return err.message || '요청을 처리하지 못했습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}

export function DepartmentWriteDialog({
  request,
  onClose,
  departments = [],
}: DepartmentWriteDialogProps) {
  const { mode, target } = request;
  const create = useCreateDepartment();
  const update = useUpdateDepartment();
  const retire = useRetireDepartment();
  const move = useMoveDepartmentParent();

  // TASK-PC-FE-047: active (non-retired) departments offered as parent
  // options. move-parent additionally excludes the target itself (a
  // department cannot be its own parent; the producer also rejects deeper
  // cycles with MASTERDATA_PARENT_CYCLE).
  const parentOptions = departments.filter((d) => !isRetired(d.effectivePeriod));
  const moveParentOptions = parentOptions.filter((d) => d.id !== target?.id);

  // create
  const [code, setCode] = useState('');
  const [name, setName] = useState(target?.name ?? '');
  const [parentId, setParentId] = useState(target?.parentId ?? '');
  const [effectiveFrom, setEffectiveFrom] = useState('');
  // retire / move-parent
  const [reason, setReason] = useState('');
  const [newParentId, setNewParentId] = useState('');

  const pending =
    create.isPending || update.isPending || retire.isPending || move.isPending;
  const error =
    create.error || update.error || retire.error || move.error || null;

  const title =
    mode === 'create'
      ? '부서 생성'
      : mode === 'update'
        ? '부서 수정'
        : mode === 'retire'
          ? '부서 폐기 (파괴적)'
          : '상위 부서 이동';
  const destructive = mode === 'retire' || mode === 'move-parent';

  const reasonOk = mode === 'retire' ? reason.trim().length > 0 : true;
  const fieldsOk =
    mode === 'create'
      ? code.trim().length > 0 && name.trim().length > 0
      : mode === 'update'
        ? name.trim().length > 0
        : mode === 'move-parent'
          ? effectiveFrom.trim().length > 0
          : true;
  const canConfirm = !pending && reasonOk && fieldsOk;

  function onConfirm() {
    if (!canConfirm) return;
    const idem = newIdemKey();
    const done = { onSuccess: () => onClose() };
    if (mode === 'create') {
      create.mutate(
        {
          input: {
            code: code.trim(),
            name: name.trim(),
            parentId: parentId.trim() ? parentId.trim() : null,
            ...(effectiveFrom.trim()
              ? { effectiveFrom: effectiveFrom.trim() }
              : {}),
          },
          idempotencyKey: idem,
        },
        done,
      );
    } else if (mode === 'update' && target) {
      update.mutate(
        {
          id: target.id,
          input: {
            name: name.trim(),
            ...(effectiveFrom.trim()
              ? { effectiveFrom: effectiveFrom.trim() }
              : {}),
          },
          idempotencyKey: idem,
        },
        done,
      );
    } else if (mode === 'retire' && target) {
      retire.mutate(
        { id: target.id, reason: reason.trim(), idempotencyKey: idem },
        done,
      );
    } else if (mode === 'move-parent' && target) {
      move.mutate(
        {
          id: target.id,
          input: {
            newParentId: newParentId.trim() ? newParentId.trim() : null,
            effectiveFrom: effectiveFrom.trim(),
            ...(reason.trim() ? { reason: reason.trim() } : {}),
          },
          idempotencyKey: idem,
        },
        done,
      );
    }
  }

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
            {errorMessage(error)}
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
