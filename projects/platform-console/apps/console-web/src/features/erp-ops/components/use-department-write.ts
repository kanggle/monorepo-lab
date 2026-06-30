'use client';

import { useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { isRetired, type Department } from '../api/types';
import {
  useCreateDepartment,
  useUpdateDepartment,
  useRetireDepartment,
  useMoveDepartmentParent,
} from '../hooks/use-erp-ops';

/**
 * `<DepartmentWriteDialog>` container hook (TASK-PC-FE-151 — behaviour-
 * preserving split of the former 415-line `DepartmentWriteDialog.tsx`).
 * The dialog is a single flat form whose JSX is heavily mode-conditional
 * (create / update / retire / move-parent) with no repeating extractable
 * sub-block, so the split is HOOK-ONLY (PC-FE-141 PromotionForm pattern):
 * all form state, validation predicates, parent-option derivations,
 * mutations, `onConfirm` wiring, and the inline error mapper live here;
 * the component keeps only the presentation. No value/setter prop-drilling
 * is introduced.
 */

export type DeptWriteMode = 'create' | 'update' | 'retire' | 'move-parent';

export interface DeptWriteRequest {
  mode: DeptWriteMode;
  /** Target row for update / retire / move-parent (absent for create). */
  target?: Department;
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

export function departmentWriteErrorMessage(err: unknown): string {
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

export function useDepartmentWrite(
  request: DeptWriteRequest,
  onClose: () => void,
  departments: Department[],
) {
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

  return {
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
  };
}
