'use client';

import { useState } from 'react';
import type { ApprovalRequest, ApprovalTransition } from '../api/approval-types';
import { allowedTransitionsFor, transitionRequiresReason } from '../api/approval-types';
import {
  useApprovalRequest,
  useSubmitApproval,
  useApproveApproval,
  useRejectApproval,
  useWithdrawApproval,
} from '../hooks/use-erp-ops';

/**
 * `<ApprovalDetail>` container hook (TASK-PC-FE-235 — behaviour-preserving
 * HOOK-ONLY split of the former ~450-line `ApprovalDetail.tsx`, matching
 * the `use-delegation-screen.ts` / `use-department-write.ts` /
 * `use-master-write.ts` precedent, TASK-PC-FE-150/151/152). Owns the
 * detail query, the 4 transition mutations, the `reasonFor` dialog-open
 * state, the idempotency-key + `runTransition`/`onAction` orchestration,
 * and the `actionError`/`actionErrorTransition`/`actions` derivations.
 * The nested `ApprovalReasonDialog`'s own `reason` text input is a pure
 * form-input local to that presentational component and stays there
 * (unmoved) — the hook only supplies `runTransition` for it to call.
 */

/** Generates an Idempotency-Key per attempt — `crypto.randomUUID()` with
 *  a defensive fallback (mirrors the sibling `newIdemKey()`s). */
export function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export function useApprovalDetail(id: string) {
  const q = useApprovalRequest(id);
  const [reasonFor, setReasonFor] = useState<ApprovalTransition | null>(null);

  const submitM = useSubmitApproval();
  const approveM = useApproveApproval();
  const rejectM = useRejectApproval();
  const withdrawM = useWithdrawApproval();

  const data: ApprovalRequest | undefined = q.data;
  const pending =
    submitM.isPending ||
    approveM.isPending ||
    rejectM.isPending ||
    withdrawM.isPending;

  // The most recent action error (for inline display) — whichever mutation
  // last failed.
  const actionError =
    submitM.error ?? approveM.error ?? rejectM.error ?? withdrawM.error;
  const actionErrorTransition: ApprovalTransition | undefined = submitM.error
    ? 'submit'
    : approveM.error
      ? 'approve'
      : rejectM.error
        ? 'reject'
        : withdrawM.error
          ? 'withdraw'
          : undefined;

  function runTransition(t: ApprovalTransition, reason?: string) {
    const idempotencyKey = newIdemKey();
    if (!data) return;
    const args = { id: data.id, idempotencyKey, reason };
    const done = () => setReasonFor(null);
    if (t === 'submit') submitM.mutate(args, { onSuccess: done });
    else if (t === 'approve') approveM.mutate(args, { onSuccess: done });
    else if (t === 'reject') rejectM.mutate(args, { onSuccess: done });
    else if (t === 'withdraw') withdrawM.mutate(args, { onSuccess: done });
  }

  function onAction(t: ApprovalTransition) {
    // reject / withdraw require a reason → open the reason dialog.
    if (transitionRequiresReason(t)) {
      setReasonFor(t);
      return;
    }
    runTransition(t);
  }

  const actions: ApprovalTransition[] = data
    ? allowedTransitionsFor(data.status)
    : [];

  return {
    q,
    data,
    pending,
    actionError,
    actionErrorTransition,
    actions,
    reasonFor,
    clearReasonFor: () => setReasonFor(null),
    onAction,
    runTransition,
  };
}
