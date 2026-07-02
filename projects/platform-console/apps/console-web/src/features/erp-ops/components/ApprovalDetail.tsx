'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  type ApprovalRequest,
  type ApprovalHistoryEntry,
  type ApprovalStage,
  type ApprovalTransition,
  allowedTransitionsFor,
  transitionRequiresReason,
} from '../api/approval-types';
import {
  useApprovalRequest,
  useSubmitApproval,
  useApproveApproval,
  useRejectApproval,
  useWithdrawApproval,
} from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';
import { formatDateTime } from '@/shared/lib/datetime';
import { SUBJECT_LABEL, statusLabel, StatusBadge } from './approval-common';

// ===========================================================================
// Detail view — status badge + history timeline + state-gated actions.
// ===========================================================================

function fmt(ts: string | undefined): string {
  return formatDateTime(ts, '—');
}

export function ApprovalDetail({
  id,
  onClose,
}: {
  id: string;
  onClose: () => void;
}) {
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

  function newIdemKey(): string {
    const g = globalThis as unknown as {
      crypto?: { randomUUID?: () => string };
    };
    return (
      g.crypto?.randomUUID?.() ??
      `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
    );
  }

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

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="approval-detail-overlay"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="결재 상세"
        data-testid="approval-detail"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        {q.isLoading && (
          <p data-testid="approval-detail-loading" className="text-sm text-muted-foreground">
            불러오는 중…
          </p>
        )}
        {q.isError && (
          <p
            className="text-sm text-destructive"
            role="status"
            data-testid="approval-detail-error"
          >
            {approvalErrorMessage(q.error)}
          </p>
        )}

        {data && (
          <>
            <div className="mb-3 flex items-start justify-between">
              <h2 className="text-lg font-semibold text-foreground">
                {data.title}
              </h2>
              <StatusBadge status={data.status} />
            </div>

            <dl className="mb-4 grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
              <dt className="text-muted-foreground">대상</dt>
              <dd>
                {SUBJECT_LABEL[data.subjectType] ?? data.subjectType} ·{' '}
                {data.subjectId}
              </dd>
              <dt className="text-muted-foreground">기안자</dt>
              <dd>{data.submitterId}</dd>
              {data.reason ? (
                <>
                  <dt className="text-muted-foreground">사유</dt>
                  <dd data-testid="approval-detail-reason">{data.reason}</dd>
                </>
              ) : null}
              <dt className="text-muted-foreground">생성</dt>
              <dd>{fmt(data.createdAt)}</dd>
              <dt className="text-muted-foreground">상신</dt>
              <dd data-testid="approval-detail-submittedAt">
                {fmt(data.submittedAt)}
              </dd>
              <dt className="text-muted-foreground">완료</dt>
              <dd data-testid="approval-detail-finalizedAt">
                {fmt(data.finalizedAt)}
              </dd>
            </dl>

            {/* stage-progress timeline (v2.0 multi-stage) */}
            <h3 className="mb-1 text-sm font-semibold text-foreground">
              결재선
              {data.stages && data.currentStage !== undefined && data.totalStages !== undefined
                ? ` (${data.currentStage + 1}/${data.totalStages} 단계)`
                : null}
            </h3>
            {data.stages && data.stages.length > 0 ? (
              <ol
                className="mb-4 space-y-1"
                data-testid="approval-stages"
              >
                {data.stages.map((stage: ApprovalStage) => {
                  const isCurrent =
                    data.currentStage !== undefined &&
                    stage.stageIndex === data.currentStage;
                  const stageBadgeTone =
                    stage.status === 'APPROVED'
                      ? 'bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100'
                      : isCurrent
                        ? 'bg-blue-100 text-blue-800 dark:bg-blue-950/60 dark:text-blue-100'
                        : 'bg-muted text-muted-foreground';
                  return (
                    <li
                      key={stage.stageIndex}
                      data-testid={
                        isCurrent
                          ? 'approval-stage-current'
                          : `approval-stage-${stage.stageIndex}`
                      }
                      data-stage-index={stage.stageIndex}
                      className={`flex items-center gap-2 rounded px-2 py-1 text-sm ${
                        isCurrent
                          ? 'border border-blue-300 bg-blue-50 dark:border-blue-700 dark:bg-blue-950/30'
                          : ''
                      }`}
                    >
                      <span className="text-muted-foreground w-10 shrink-0">
                        {stage.stageIndex + 1}단계
                      </span>
                      <span className="flex-1">{stage.approverId}</span>
                      <span
                        className={`inline-block rounded px-1.5 py-0.5 text-xs ${stageBadgeTone}`}
                      >
                        {stage.status === 'APPROVED'
                          ? '승인됨'
                          : stage.status === 'PENDING'
                            ? (isCurrent ? '진행중' : '대기중')
                            : stage.status}
                      </span>
                      {isCurrent && (
                        <span className="text-xs text-blue-700 dark:text-blue-300">
                          ← 현재
                        </span>
                      )}
                    </li>
                  );
                })}
              </ol>
            ) : (
              /* Legacy / single-stage fallback — show approverId as before */
              <dl className="mb-4 grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
                <dt className="text-muted-foreground">결재자</dt>
                <dd data-testid="approval-approverId">{data.approverId}</dd>
              </dl>
            )}

            {/* history timeline (E4 immutable audit) */}
            <h3 className="mb-1 text-sm font-semibold text-foreground">
              이력 (history)
            </h3>
            {data.history.length === 0 ? (
              <p
                className="mb-4 text-sm text-muted-foreground"
                data-testid="approval-history-empty"
              >
                아직 전이 이력이 없습니다.
              </p>
            ) : (
              <ol
                className="mb-4 space-y-1 border-l border-border pl-4"
                data-testid="approval-history"
              >
                {data.history.map((h: ApprovalHistoryEntry, i: number) => (
                  <li
                    key={`${h.transition}-${h.at}-${i}`}
                    data-testid={`approval-history-${i}`}
                    className="text-sm"
                  >
                    <span className="font-medium">
                      {statusLabel(h.transition)}
                    </span>{' '}
                    <span className="text-muted-foreground">
                      · {h.actor} · {h.at}
                      {/* v2.0 — stage annotation */}
                      {h.stage !== undefined
                        ? ` · ${h.stage + 1}단계`
                        : null}
                    </span>
                    {/* v2.1 — delegation marker */}
                    {h.actingForApproverId ? (
                      <span
                        className="ml-1 text-xs text-muted-foreground"
                        data-testid={`approval-history-delegated-${i}`}
                      >
                        (대결: {h.actingForApproverId} 대신)
                      </span>
                    ) : null}
                    {h.reason ? (
                      <span
                        className="block text-xs text-muted-foreground"
                        data-testid={`approval-history-reason-${i}`}
                      >
                        사유: {h.reason}
                      </span>
                    ) : null}
                  </li>
                ))}
              </ol>
            )}

            {/* inline action error (AC-4 — graceful, no crash) */}
            {actionError ? (
              <p
                className="mb-3 text-sm text-destructive"
                role="status"
                data-testid="approval-action-error"
              >
                {approvalErrorMessage(actionError, actionErrorTransition)}
              </p>
            ) : null}

            {/* state-gated transition actions */}
            <div className="flex flex-wrap justify-end gap-2">
              {actions.length === 0 && (
                <span
                  className="mr-auto text-xs text-muted-foreground"
                  data-testid="approval-no-actions"
                >
                  완료된 결재입니다 (전이 불가).
                </span>
              )}
              {actions.includes('submit') && (
                <Button
                  variant="primary"
                  onClick={() => onAction('submit')}
                  disabled={pending}
                  data-testid="approval-action-submit"
                >
                  상신
                </Button>
              )}
              {actions.includes('approve') && (
                <Button
                  variant="primary"
                  onClick={() => onAction('approve')}
                  disabled={pending}
                  data-testid="approval-action-approve"
                >
                  승인
                </Button>
              )}
              {actions.includes('reject') && (
                <Button
                  variant="secondary"
                  onClick={() => onAction('reject')}
                  disabled={pending}
                  data-testid="approval-action-reject"
                  className="text-destructive"
                >
                  반려
                </Button>
              )}
              {actions.includes('withdraw') && (
                <Button
                  variant="secondary"
                  onClick={() => onAction('withdraw')}
                  disabled={pending}
                  data-testid="approval-action-withdraw"
                >
                  회수
                </Button>
              )}
              <Button
                variant="secondary"
                onClick={onClose}
                disabled={pending}
                data-testid="approval-detail-close"
              >
                닫기
              </Button>
            </div>
          </>
        )}

        {reasonFor && (
          <ApprovalReasonDialog
            transition={reasonFor}
            pending={pending}
            onCancel={() => setReasonFor(null)}
            onConfirm={(reason) => runTransition(reasonFor, reason)}
          />
        )}
      </div>
    </div>
  );
}

// ===========================================================================
// Reason-required dialog (reject / withdraw).
// ===========================================================================

function ApprovalReasonDialog({
  transition,
  pending,
  onCancel,
  onConfirm,
}: {
  transition: ApprovalTransition;
  pending: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  const ok = reason.trim() !== '';
  const verb = transition === 'reject' ? '반려' : '회수';
  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
      data-testid="approval-reason-overlay"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`${verb} 사유`}
        data-testid="approval-reason-dialog"
        data-transition={transition}
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">{verb} 사유</h2>
        <label
          htmlFor="approval-reason"
          className="mt-4 block text-sm font-medium text-foreground"
        >
          사유 <span aria-hidden="true">*</span>
        </label>
        <textarea
          id="approval-reason"
          data-testid="approval-reason-input"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          maxLength={512}
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          placeholder={`${verb} 사유를 입력하세요 (감사 기록에 남습니다)`}
        />
        {!ok && (
          <p
            className="mt-1 text-xs text-destructive"
            role="status"
            data-testid="approval-reason-error"
          >
            {verb}에는 사유가 필요합니다.
          </p>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onCancel}
            disabled={pending}
            data-testid="approval-reason-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={() => ok && onConfirm(reason.trim())}
            disabled={!ok || pending}
            data-testid="approval-reason-confirm"
            className="bg-destructive text-destructive-foreground hover:opacity-90"
          >
            {pending ? '처리 중…' : verb}
          </Button>
        </div>
      </div>
    </div>
  );
}
