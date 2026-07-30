'use client';

import { useId, useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { RESOLUTION_TYPES, type ResolutionType } from '../api/types';
import { discrepancyResolveErrorMessage } from './discrepancy-error';

/**
 * Reconciliation discrepancy *resolve* dialog (TASK-PC-FE-073 — § 2.4.7.1;
 * the ledger surface's FIRST and ONLY operator mutation). Confirm-gated,
 * mirroring the erp `ApprovalReasonDialog`:
 *
 *   - a `resolutionType` select offering EXACTLY the three producer values
 *     (`MATCHED_MANUALLY | WRITTEN_OFF | ACCEPTED`) with readable labels;
 *   - a **required** non-empty `note` textarea (maxLength 512) — the audit
 *     record; the confirm button is DISABLED while `note.trim() === ''` so
 *     an empty note never triggers a fetch;
 *   - a pending state ("처리 중…"); an inline error from
 *     `discrepancyResolveErrorMessage` (409 already-resolved → "새로고침",
 *     422 period-locked → "기간 마감", 404, ledger-unavailable → "일시적");
 *   - Escape / cancel; WCAG AA (role=dialog, aria-modal, aria-label, initial
 *     focus, keyboard).
 *
 * The dialog is PRESENTATIONAL — it does not call the mutation itself; the
 * parent passes `onConfirm(resolutionType, note)` (the parent owns the
 * `useResolveDiscrepancy` mutation + the success/refetch handling) and the
 * `pending` / `error` state.
 *
 * TASK-PC-FE-269 — thin wrapper over `shared/ui/ConfirmDialog`. Gains a
 * Tab-loop focus trap it did not have before (same precedented side effect
 * TASK-PC-FE-262/268 already documented for other migrated dialogs).
 */
export interface DiscrepancyResolveDialogProps {
  discrepancyId: string;
  pending: boolean;
  /** The mutation error (if any) — rendered inline via the error helper. */
  error?: unknown;
  onCancel: () => void;
  onConfirm: (resolutionType: ResolutionType, note: string) => void;
}

const RESOLUTION_TYPE_LABELS: Record<ResolutionType, string> = {
  MATCHED_MANUALLY: '수동 매칭 (MATCHED_MANUALLY)',
  WRITTEN_OFF: '손실 처리 (WRITTEN_OFF)',
  ACCEPTED: '차이 인정 (ACCEPTED)',
};

export function DiscrepancyResolveDialog({
  discrepancyId,
  pending,
  error,
  onCancel,
  onConfirm,
}: DiscrepancyResolveDialogProps) {
  const typeFid = useId();
  const noteFid = useId();
  const [resolutionType, setResolutionType] = useState<ResolutionType>(
    RESOLUTION_TYPES[0],
  );
  const [note, setNote] = useState('');
  const noteOk = note.trim() !== '';
  const noteRef = useRef<HTMLTextAreaElement | null>(null);

  const errMessage = error ? discrepancyResolveErrorMessage(error) : null;

  return (
    <ConfirmDialog
      open
      title="대사 차이 해소"
      description="이 OPEN 대사 차이를 해소합니다. 해소 유형과 사유는 감사 기록에 남습니다."
      confirmLabel="해소"
      confirmDisabled={!noteOk}
      errorMessage={errMessage}
      dialogAttrs={{ 'data-discrepancy-id': discrepancyId }}
      dialogTestId="ledger-recon-resolve-dialog"
      overlayTestId="ledger-recon-resolve-overlay"
      cancelTestId="ledger-recon-resolve-cancel"
      confirmTestId="ledger-recon-resolve-confirm"
      errorTestId="ledger-recon-resolve-error"
      initialFocusRef={noteRef}
      onConfirm={() => noteOk && onConfirm(resolutionType, note.trim())}
      onCancel={onCancel}
    >
      <label
        htmlFor={typeFid}
        className="mt-4 block text-sm font-medium text-foreground"
      >
        해소 유형
      </label>
      <select
        id={typeFid}
        value={resolutionType}
        onChange={(e) => setResolutionType(e.target.value as ResolutionType)}
        disabled={pending}
        data-testid="ledger-recon-resolve-type"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        {RESOLUTION_TYPES.map((t) => (
          <option key={t} value={t}>
            {RESOLUTION_TYPE_LABELS[t]}
          </option>
        ))}
      </select>

      <label
        htmlFor={noteFid}
        className="mt-4 block text-sm font-medium text-foreground"
      >
        사유 <span aria-hidden="true">*</span>
      </label>
      <textarea
        id={noteFid}
        ref={noteRef}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        rows={3}
        maxLength={512}
        disabled={pending}
        data-testid="ledger-recon-resolve-note"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        placeholder="해소 사유를 입력하세요 (감사 기록에 남습니다)"
      />
      {!noteOk && (
        <p
          className="mt-1 text-xs text-destructive"
          role="status"
          data-testid="ledger-recon-resolve-note-error"
        >
          해소에는 사유가 필요합니다.
        </p>
      )}
    </ConfirmDialog>
  );
}
