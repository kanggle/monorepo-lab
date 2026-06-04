'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  type ApprovalListResponse,
  type ApprovalSummary,
  type ApprovalRequest,
  type ApprovalHistoryEntry,
  type ApprovalTransition,
  APPROVAL_STATUSES,
  APPROVAL_SUBJECT_TYPES,
  allowedTransitionsFor,
  transitionRequiresReason,
  isTerminalApprovalStatus,
} from '../api/approval-types';
import {
  useApprovalRequests,
  useApprovalInbox,
  useApprovalRequest,
  useCreateApproval,
  useSubmitApproval,
  useApproveApproval,
  useRejectApproval,
  useWithdrawApproval,
} from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';

/**
 * ERP "결재함" screen (TASK-PC-FE-051 — ADR-MONO-016 § D3.1 parity slice).
 *
 * Surfaces the `approval-service` single-stage state machine
 * (`DRAFT → SUBMITTED → APPROVED | REJECTED | WITHDRAWN`) in the console:
 *   - requests LIST with a status filter + the INBOX (my pending SUBMITTED);
 *   - DETAIL view (status badge + immutable history timeline; absent
 *     reason / submittedAt / finalizedAt render as "—" / hidden — NON_NULL);
 *   - CREATE dialog (subjectType / subjectId / title / approverId, optional
 *     reason; Idempotency-Key generated per attempt);
 *   - state-machine transition actions (submit / approve / reject /
 *     withdraw) — only the legal-for-status actions are offered; terminal
 *     states offer none.
 *
 * Graceful errors (AC-4): every producer error code maps to an inline
 * actionable message via `approvalErrorMessage` — NO crash. The console
 * operator may not be the authorized approver: a 403
 * `APPROVAL_NOT_AUTHORIZED_APPROVER` is surfaced as a clear inline notice.
 *
 * First increment only — no IN_REVIEW / multi-stage / delegation / inbox
 * filtering (the backend lacks them).
 */
export interface ApprovalScreenProps {
  /** Optional server-seeded first-page snapshots (the section landing). */
  initialRequests?: ApprovalListResponse | null;
  initialInbox?: ApprovalListResponse | null;
}

const SUBJECT_LABEL: Record<string, string> = {
  DEPARTMENT: '부서',
  EMPLOYEE: '직원',
};

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '작성중',
  SUBMITTED: '상신됨',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  WITHDRAWN: '회수됨',
};

function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

function StatusBadge({ status }: { status: string }) {
  const terminal = isTerminalApprovalStatus(status);
  const tone =
    status === 'APPROVED'
      ? 'bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100'
      : status === 'REJECTED' || status === 'WITHDRAWN'
        ? 'bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-100'
        : status === 'SUBMITTED'
          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-100'
          : 'bg-muted text-muted-foreground';
  return (
    <span
      data-testid="approval-status-badge"
      data-status={status}
      data-terminal={terminal ? 'true' : 'false'}
      className={`inline-block rounded px-1.5 py-0.5 text-xs ${tone}`}
    >
      {statusLabel(status)}
    </span>
  );
}

export function ApprovalScreen({
  initialRequests,
  initialInbox,
}: ApprovalScreenProps) {
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const listQ = useApprovalRequests(
    statusFilter ? { status: statusFilter } : {},
    statusFilter ? undefined : (initialRequests ?? undefined),
  );
  const inboxQ = useApprovalInbox({}, initialInbox ?? undefined);

  const listResp =
    listQ.data ??
    (statusFilter ? undefined : initialRequests) ??
    { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const inboxResp =
    inboxQ.data ?? initialInbox ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };

  const listRows = listResp.data ?? [];
  const inboxRows = inboxResp.data ?? [];

  return (
    <section aria-labelledby="approval-heading" data-testid="approval-screen">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="approval-heading"
          className="text-lg font-medium text-foreground"
        >
          결재함 (approval)
        </h2>
        <Button
          variant="primary"
          onClick={() => setCreateOpen(true)}
          data-testid="approval-create"
        >
          결재 요청 작성
        </Button>
      </div>

      {/* section-level degrade / error notice (AC-4) */}
      {listQ.isError && (
        <p
          className="mb-3 text-sm text-destructive"
          role="status"
          data-testid="approval-list-error"
        >
          {approvalErrorMessage(listQ.error)}
        </p>
      )}

      {/* INBOX — my pending SUBMITTED */}
      <div className="mb-6" data-testid="approval-inbox">
        <h3 className="mb-2 text-sm font-semibold text-foreground">
          내 미결함 (inbox)
        </h3>
        {inboxRows.length === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="approval-inbox-empty"
          >
            대기 중인 결재가 없습니다.
          </p>
        ) : (
          <ul className="space-y-1" data-testid="approval-inbox-list">
            {inboxRows.map((r: ApprovalSummary) => (
              <li key={r.id}>
                <button
                  type="button"
                  data-testid={`approval-inbox-item-${r.id}`}
                  onClick={() => setSelectedId(r.id)}
                  className="flex w-full items-center justify-between rounded border border-border px-3 py-2 text-left text-sm hover:bg-accent"
                >
                  <span>{r.title}</span>
                  <StatusBadge status={r.status} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* status filter */}
      <div className="mb-3 flex items-center gap-2">
        <label
          htmlFor="approval-status-filter"
          className="text-sm text-muted-foreground"
        >
          상태 필터
        </label>
        <select
          id="approval-status-filter"
          data-testid="approval-status-filter"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground"
        >
          <option value="">전체</option>
          {APPROVAL_STATUSES.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
      </div>

      {/* requests LIST */}
      {listRows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="approval-list-empty"
        >
          표시할 결재 요청이 없습니다.
        </p>
      ) : (
        <table className="mb-6 data-table" data-testid="approval-list-table">
          <caption className="sr-only">결재 요청 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">제목</th>
              <th scope="col" className="p-2">대상</th>
              <th scope="col" className="p-2">상태</th>
              <th scope="col" className="p-2"> </th>
            </tr>
          </thead>
          <tbody>
            {listRows.map((r: ApprovalSummary, i: number) => (
              <tr
                key={r.id}
                data-testid={`approval-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{r.title}</td>
                <td className="p-2 text-sm text-muted-foreground">
                  {SUBJECT_LABEL[r.subjectType] ?? r.subjectType} · {r.subjectId}
                </td>
                <td className="p-2">
                  <StatusBadge status={r.status} />
                </td>
                <td className="p-2">
                  <Button
                    variant="secondary"
                    onClick={() => setSelectedId(r.id)}
                    data-testid={`approval-open-${i}`}
                  >
                    상세
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {createOpen && (
        <ApprovalCreateDialog onClose={() => setCreateOpen(false)} />
      )}
      {selectedId && (
        <ApprovalDetail
          id={selectedId}
          onClose={() => setSelectedId(null)}
        />
      )}
    </section>
  );
}

// ===========================================================================
// Detail view — status badge + history timeline + state-gated actions.
// ===========================================================================

function fmt(ts: string | undefined): string {
  return ts ?? '—';
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
              <dt className="text-muted-foreground">결재자</dt>
              <dd>{data.approverId}</dd>
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
                    </span>
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

// ===========================================================================
// Create dialog — DRAFT request.
// ===========================================================================

export function ApprovalCreateDialog({ onClose }: { onClose: () => void }) {
  const [subjectType, setSubjectType] = useState<string>(
    APPROVAL_SUBJECT_TYPES[0],
  );
  const [subjectId, setSubjectId] = useState('');
  const [title, setTitle] = useState('');
  const [approverId, setApproverId] = useState('');
  const [reason, setReason] = useState('');
  const createM = useCreateApproval();

  const ok =
    subjectId.trim() !== '' &&
    title.trim() !== '' &&
    approverId.trim() !== '';
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

  function onConfirm() {
    if (!canConfirm) return;
    createM.mutate(
      {
        input: {
          subjectType,
          subjectId: subjectId.trim(),
          title: title.trim(),
          approverId: approverId.trim(),
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

        <div className="mt-4">
          <label
            htmlFor="approval-create-approverId"
            className="block text-sm font-medium text-foreground"
          >
            결재자 ID <span aria-hidden="true">*</span>
          </label>
          <input
            id="approval-create-approverId"
            data-testid="approval-create-approverId"
            value={approverId}
            onChange={(e) => setApproverId(e.target.value)}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            placeholder="emp-…"
          />
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
