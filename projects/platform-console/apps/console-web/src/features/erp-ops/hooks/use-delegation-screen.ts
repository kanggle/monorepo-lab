'use client';

import { useState } from 'react';
import type { DelegationGrant } from '../api/delegation-types';
import { ERP_MAX_PAGE_SIZE } from '../api/types';
import {
  useDelegations,
  useCreateDelegation,
  useRevokeDelegation,
  useEmployeePicker,
} from './use-erp-ops';
import { employeePickerView } from '../components/ErpEmployeeSelect';

/**
 * `<DelegationScreen>` container hook (TASK-PC-FE-150 — behaviour-
 * preserving split of the former 466-line `DelegationScreen.tsx`). Owns
 * the screen's dialog open-state + both grant queries, and the create /
 * revoke dialog form-state + mutations. The presentational pieces
 * (`DelegationGrantList`, the two dialogs, the status badge) consume the
 * values returned here — no logic lives in the JSX.
 */

/** Generates an Idempotency-Key per attempt — `crypto.randomUUID()` with
 *  a defensive fallback (mirrors the operators `newIdemKey()`). */
export function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export function useDelegationScreen() {
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<DelegationGrant | null>(null);

  const delegatorQ = useDelegations('DELEGATOR');
  const delegateQ = useDelegations('DELEGATE');

  const delegatorGrants = delegatorQ.data?.data ?? [];
  const delegateGrants = delegateQ.data?.data ?? [];

  return {
    createOpen,
    openCreate: () => setCreateOpen(true),
    closeCreate: () => setCreateOpen(false),
    revokeTarget,
    openRevoke: (grant: DelegationGrant) => setRevokeTarget(grant),
    closeRevoke: () => setRevokeTarget(null),
    delegatorQ,
    delegateQ,
    delegatorGrants,
    delegateGrants,
  };
}

export function useDelegationCreate(onClose: () => void) {
  const [delegateId, setDelegateId] = useState('');
  const [validFrom, setValidFrom] = useState('');
  const [validTo, setValidTo] = useState('');
  const [reason, setReason] = useState('');
  const createM = useCreateDelegation();

  // TASK-PC-FE-335 AC-1/AC-4 — 대결자 is an EMPLOYEE SELECTOR now (was a raw-id
  // text input); same shape as the approval approver route (one page of up to
  // ERP_MAX_PAGE_SIZE, fallback to the id input on error, same testid). RETIRED
  // employees stay listed but are NOT selectable here — `delegateId` must be an
  // ACTIVE employee (the producer refuses otherwise with 422 `delegate_unresolved`).
  // TASK-ERP-BE-045 — reads the «선택용» picker list (open to 결재 변경, narrowed to the
  // operator's subtree); `picker.offerManual` drives the «ID 직접 입력» toggle.
  const employeesQ = useEmployeePicker({ page: 0, size: ERP_MAX_PAGE_SIZE });
  const picker = employeePickerView(employeesQ.data, employeesQ.isSuccess);
  const employees = picker.employees;

  const ok = delegateId.trim() !== '' && validFrom.trim() !== '';
  const canConfirm = ok && !createM.isPending;

  function onConfirm() {
    if (!canConfirm) return;
    createM.mutate(
      {
        input: {
          delegateId: delegateId.trim(),
          validFrom: validFrom.trim(),
          ...(validTo.trim() ? { validTo: validTo.trim() } : {}),
          ...(reason.trim() ? { reason: reason.trim() } : {}),
        },
        idempotencyKey: newIdemKey(),
      },
      { onSuccess: () => onClose() },
    );
  }

  return {
    delegateId,
    setDelegateId,
    validFrom,
    setValidFrom,
    validTo,
    setValidTo,
    reason,
    setReason,
    createM,
    canConfirm,
    onConfirm,
    employeesQ,
    employees,
    picker,
  };
}

export function useDelegationRevoke(grant: DelegationGrant, onClose: () => void) {
  const [reason, setReason] = useState('');
  const revokeM = useRevokeDelegation();
  const ok = reason.trim() !== '';

  function onConfirm() {
    if (!ok || revokeM.isPending) return;
    revokeM.mutate(
      {
        id: grant.id,
        reason: reason.trim(),
        idempotencyKey: newIdemKey(),
      },
      { onSuccess: () => onClose() },
    );
  }

  return { reason, setReason, revokeM, ok, onConfirm };
}
