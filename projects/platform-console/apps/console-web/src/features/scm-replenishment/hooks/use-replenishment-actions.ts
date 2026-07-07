'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useApproveSuggestion,
  useDismissSuggestion,
} from './use-scm-replenishment';
import type { Suggestion, ApproveResult } from '../api/types';

/**
 * Approve / dismiss action-model hook for {@link ReplenishmentScreen}
 * (TASK-PC-FE-215 split). Owns the confirm-dialog action state (which
 * suggestion + which kind), the optional note/reason, the inline action error,
 * the materialised DRAFT-PO success surface, and the confirm-gated mutations.
 * Behaviour byte-preserved from the pre-split screen (same approve/dismiss
 * hooks, same idempotent-success handling, same inline-error copy). The screen
 * keeps the list query wiring + JSX only.
 */

export type ActionKind = 'approve' | 'dismiss';

const ACTION_COPY: Record<
  ActionKind,
  { title: string; description: string; confirmLabel: string; noteLabel: string }
> = {
  approve: {
    title: '보충 발주를 승인할까요?',
    description:
      '공급사 매핑(sku_supplier_map)을 해석해 조달(Procurement)에 DRAFT 발주(PO)를 생성합니다. 생성된 PO 는 DRAFT 상태로만 만들어지며, 제출(SUBMIT)은 조달에서 별도로 진행합니다. 같은 추천을 다시 승인해도 기존 PO 가 반환됩니다(멱등).',
    confirmLabel: '승인 (DRAFT PO 생성)',
    noteLabel: '메모 (선택)',
  },
  dismiss: {
    title: '이 추천을 기각할까요?',
    description:
      '추천을 DISMISSED 상태로 변경하고 미해결 추천 가드를 해제합니다. 같은 추천을 다시 기각해도 변화가 없습니다(멱등).',
    confirmLabel: '기각',
    noteLabel: '사유 (선택)',
  },
};

export function useReplenishmentActions() {
  const approve = useApproveSuggestion();
  const dismiss = useDismissSuggestion();
  const [action, setAction] = useState<{
    kind: ActionKind;
    suggestion: Suggestion;
  } | null>(null);
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  // The materialised DRAFT PO surfaced after a successful approve (incl. the
  // idempotent re-approve path returning the existing poId).
  const [approved, setApproved] = useState<ApproveResult | null>(null);

  const activeMutation = action?.kind === 'approve' ? approve : dismiss;
  const actionPending = approve.isPending || dismiss.isPending;

  function openAction(kind: ActionKind, suggestion: Suggestion) {
    setActionError(null);
    setNote('');
    setAction({ kind, suggestion });
  }

  function confirmAction() {
    if (!action) return;
    const { kind, suggestion } = action;
    if (kind === 'approve') {
      approve.mutate(
        { id: suggestion.id, note },
        {
          onSuccess: (result) => {
            setAction(null);
            setActionError(null);
            // Surface the materialised DRAFT poId/poStatus (also the idempotent
            // re-approve path — the existing poId, no duplicate-PO assumption).
            setApproved(result);
          },
          onError: (e) => handleActionError(e),
        },
      );
    } else {
      dismiss.mutate(
        { id: suggestion.id, reason: note },
        {
          onSuccess: () => {
            setAction(null);
            setActionError(null);
          },
          onError: (e) => handleActionError(e),
        },
      );
    }
  }

  function handleActionError(e: unknown) {
    const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
    setActionError(messageForCode(code, '작업을 처리하지 못했습니다.'));
  }

  function cancelAction() {
    setAction(null);
    setActionError(null);
    setNote('');
  }

  const copy = action ? ACTION_COPY[action.kind] : null;

  return {
    action,
    copy,
    note,
    setNote,
    actionError,
    actionPending,
    activePending: activeMutation.isPending,
    approved,
    dismissApproved: () => setApproved(null),
    openAction,
    confirmAction,
    cancelAction,
  };
}
