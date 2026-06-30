'use client';

import { useState } from 'react';
import type { DelegationGrant } from '../api/delegation-types';
import {
  useDelegations,
  useCreateDelegation,
  useRevokeDelegation,
} from '../hooks/use-erp-ops';

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
