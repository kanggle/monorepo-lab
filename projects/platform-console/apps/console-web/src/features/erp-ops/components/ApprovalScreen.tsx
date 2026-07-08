'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  type ApprovalListResponse,
  type ApprovalSummary,
  APPROVAL_STATUSES,
} from '../api/approval-types';
import {
  useApprovalRequests,
  useApprovalInbox,
} from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';
import { SUBJECT_LABEL, statusLabel, StatusBadge } from './approval-common';
import { ApprovalDetail } from './ApprovalDetail';
import { ApprovalCreateDialog } from './ApprovalCreateDialog';

/**
 * ERP "결재함" screen (TASK-PC-FE-051 — ADR-MONO-016 § D3.1 parity slice).
 * Extended by TASK-PC-FE-053 (multi-stage approval + IN_REVIEW + delegation
 * read-only display).
 *
 * Surfaces the `approval-service` state machine
 * (`DRAFT → SUBMITTED → IN_REVIEW → APPROVED | REJECTED | WITHDRAWN`) in
 * the console:
 *   - requests LIST with a status filter + the INBOX (my pending);
 *   - DETAIL view (status badge + multi-stage timeline + immutable history
 *     timeline; absent fields render as "—" — NON_NULL);
 *   - CREATE dialog (subjectType / subjectId / title + ordered approver
 *     route 1~N rows, optional reason; Idempotency-Key per attempt);
 *   - state-machine transition actions (submit / approve / reject /
 *     withdraw) — only the legal-for-status actions are offered; terminal
 *     states offer none.
 *
 * Graceful errors (AC-4): every producer error code maps to an inline
 * actionable message via `approvalErrorMessage` — NO crash. The console
 * operator may not be the current stage's authorized approver: a 403
 * `APPROVAL_NOT_AUTHORIZED_APPROVER` is surfaced as a clear inline notice.
 *
 * Backward-compatible (AC-4): a detail response WITHOUT stages/currentStage
 * degrades gracefully (renders the single approverId as before; no crash).
 *
 * ── MODULE SPLIT (TASK-PC-FE-100) ── the detail dialog + reason dialog and
 * the create dialog now live in sibling files (`ApprovalDetail.tsx` /
 * `ApprovalCreateDialog.tsx`); the shared status/subject labels + status
 * badge live in `approval-common.tsx`. This module re-exports the two
 * dialogs so the `./ApprovalScreen` import path stays the stable public
 * surface (the feature `index.ts` + the unit test import them from here).
 */
export interface ApprovalScreenProps {
  /** Optional server-seeded first-page snapshots (the section landing). */
  initialRequests?: ApprovalListResponse | null;
  initialInbox?: ApprovalListResponse | null;
  /**
   * Deep-link target — the approval request to open on mount, seeded from
   * `/erp/approval?request=<id>` (the notification bell's approval fallback,
   * PC-FE-230). Absent → the plain list landing. An unknown / stale id opens
   * the detail dialog which shows a graceful not-found notice (`ApprovalDetail`
   * fetches by id and surfaces 404 inline) over the still-rendered list — never
   * a crash.
   */
  initialSelectedId?: string | null;
}

export function ApprovalScreen({
  initialRequests,
  initialInbox,
  initialSelectedId,
}: ApprovalScreenProps) {
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [selectedId, setSelectedId] = useState<string | null>(
    initialSelectedId ?? null,
  );
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
                    size="sm"
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

// Re-export the extracted dialogs so `./ApprovalScreen` stays the stable
// public surface (the feature index + the unit test import them from here).
export { ApprovalDetail } from './ApprovalDetail';
export { ApprovalCreateDialog } from './ApprovalCreateDialog';
