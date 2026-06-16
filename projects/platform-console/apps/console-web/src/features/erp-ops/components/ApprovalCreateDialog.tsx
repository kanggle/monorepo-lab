'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { APPROVAL_SUBJECT_TYPES } from '../api/approval-types';
import { useCreateApproval } from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';
import { SUBJECT_LABEL } from './approval-common';

// ===========================================================================
// Create dialog — DRAFT request.
// ===========================================================================

export function ApprovalCreateDialog({ onClose }: { onClose: () => void }) {
  const [subjectType, setSubjectType] = useState<string>(
    APPROVAL_SUBJECT_TYPES[0],
  );
  const [subjectId, setSubjectId] = useState('');
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
            onChange={(e) => setSubjectType(e.target.value)}
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
          <input
            id="approval-create-subjectId"
            data-testid="approval-create-subjectId"
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            placeholder="dept-… / emp-…"
          />
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
              (순서대로 단계 결재자 입력)
            </span>
          </p>
          <div className="mt-1 space-y-2">
            {approverRows.map((row, idx) => (
              <div key={idx} className="flex items-center gap-2">
                <span className="w-10 shrink-0 text-xs text-muted-foreground">
                  {idx + 1}단계
                </span>
                <input
                  data-testid={`approval-create-approver-${idx}`}
                  value={row}
                  onChange={(e) => setApproverRow(idx, e.target.value)}
                  className="flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                  placeholder="emp-…"
                />
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
            ))}
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
