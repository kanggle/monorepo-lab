'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import type { DelegationGrant } from '../api/delegation-types';
import { isActiveGrant } from '../api/delegation-types';
import {
  useDelegations,
  useCreateDelegation,
  useRevokeDelegation,
} from '../hooks/use-erp-ops';
import { approvalErrorMessage } from './approval-error';

/**
 * ERP "위임(대결) 관리" screen (TASK-PC-FE-054 — PC-FE-053 follow-up;
 * realises TASK-ERP-BE-013).
 *
 * Surfaces the `approval-service` delegation grant management in the console:
 *   - "내가 위임한" (as DELEGATOR) list: delegateId, validFrom–validTo /
 *     "무기한", status badge (ACTIVE=활성 / REVOKED=회수됨 / expired ACTIVE
 *     shows "만료"), + REVOKE action on ACTIVE grants;
 *   - "나에게 위임된" (as DELEGATE) list: delegatorId, period, status badge
 *     (no revoke action — revoke is delegator-only);
 *   - CREATE dialog: delegateId + validFrom + validTo (optional) + reason
 *     (optional), console-generated Idempotency-Key per attempt;
 *   - REVOKE dialog: reason required (gated), console-generated Idempotency-Key.
 *
 * Graceful errors (AC-4): every producer error code maps to an inline
 * actionable message via `approvalErrorMessage` — NO crash.
 *   422 DELEGATION_INVALID (self-delegation / invalid period) → inline.
 *   404 DELEGATION_NOT_FOUND (already-revoked grant revoke) → inline.
 *   Self-delegation warning is best-effort only: the console does NOT
 *   reliably know the caller's own employee id client-side (JWT sub is
 *   server-side); the producer 422 DELEGATION_INVALID is the authoritative
 *   gate, surfaced inline.
 *
 * NON_NULL absent fields: validTo (→ "무기한"), reason (→ hidden),
 * revokedAt / revokedBy (→ hidden) — never a crash.
 */

function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

function fmt(ts: string | undefined): string {
  return ts ?? '—';
}

// ---------------------------------------------------------------------------
// Status badge.
// ---------------------------------------------------------------------------

function DelegationStatusBadge({ grant }: { grant: DelegationGrant }) {
  if (grant.status === 'REVOKED') {
    return (
      <span
        data-testid="delegation-status-badge"
        data-status="REVOKED"
        className="inline-block rounded px-1.5 py-0.5 text-xs bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-100"
      >
        회수됨
      </span>
    );
  }
  // ACTIVE — check if expired
  const active = isActiveGrant(grant);
  if (!active) {
    return (
      <span
        data-testid="delegation-status-badge"
        data-status="ACTIVE_EXPIRED"
        className="inline-block rounded px-1.5 py-0.5 text-xs bg-muted text-muted-foreground"
      >
        만료
      </span>
    );
  }
  return (
    <span
      data-testid="delegation-status-badge"
      data-status="ACTIVE"
      className="inline-block rounded px-1.5 py-0.5 text-xs bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100"
    >
      활성
    </span>
  );
}

// ---------------------------------------------------------------------------
// Period display helper.
// ---------------------------------------------------------------------------

function periodText(grant: DelegationGrant): string {
  const from = fmt(grant.validFrom);
  const to = grant.validTo ? fmt(grant.validTo) : '무기한';
  return `${from} ~ ${to}`;
}

// ---------------------------------------------------------------------------
// DelegationScreen.
// ---------------------------------------------------------------------------

export function DelegationScreen() {
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<DelegationGrant | null>(null);

  const delegatorQ = useDelegations('DELEGATOR');
  const delegateQ = useDelegations('DELEGATE');

  const delegatorGrants = delegatorQ.data?.data ?? [];
  const delegateGrants = delegateQ.data?.data ?? [];

  return (
    <section
      aria-labelledby="delegation-heading"
      data-testid="delegation-screen"
    >
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="delegation-heading"
          className="text-lg font-medium text-foreground"
        >
          위임(대결) 관리
        </h2>
        <Button
          variant="primary"
          onClick={() => setCreateOpen(true)}
          data-testid="delegation-create"
        >
          위임 생성
        </Button>
      </div>

      {/* DELEGATOR — grants I issued */}
      <div className="mb-6" data-testid="delegation-list-delegator">
        <h3 className="mb-2 text-sm font-semibold text-foreground">
          내가 위임한 (delegator)
        </h3>
        {delegatorQ.isError && (
          <p
            className="mb-2 text-sm text-destructive"
            role="status"
            data-testid="delegation-error"
          >
            {approvalErrorMessage(delegatorQ.error)}
          </p>
        )}
        {delegatorQ.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : delegatorGrants.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            위임한 grant 가 없습니다.
          </p>
        ) : (
          <ul className="space-y-1">
            {delegatorGrants.map((g: DelegationGrant) => (
              <li
                key={g.id}
                data-testid={`delegation-row-${g.id}`}
                className="flex items-center justify-between rounded border border-border px-3 py-2 text-sm"
              >
                <div className="flex-1 min-w-0">
                  <span className="font-medium">{g.delegateId}</span>
                  <span className="ml-2 text-xs text-muted-foreground">
                    {periodText(g)}
                  </span>
                </div>
                <div className="ml-2 flex items-center gap-2">
                  <DelegationStatusBadge grant={g} />
                  {/* Revoke action only on ACTIVE (not expired) delegator grants */}
                  {isActiveGrant(g) && (
                    <Button
                      variant="secondary"
                      onClick={() => setRevokeTarget(g)}
                      data-testid={`delegation-revoke-${g.id}`}
                      className="text-xs text-destructive"
                    >
                      회수
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* DELEGATE — grants delegated to me */}
      <div className="mb-6" data-testid="delegation-list-delegate">
        <h3 className="mb-2 text-sm font-semibold text-foreground">
          나에게 위임된 (delegate)
        </h3>
        {delegateQ.isError && (
          <p
            className="mb-2 text-sm text-destructive"
            role="status"
            data-testid="delegation-error"
          >
            {approvalErrorMessage(delegateQ.error)}
          </p>
        )}
        {delegateQ.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : delegateGrants.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            나에게 위임된 grant 가 없습니다.
          </p>
        ) : (
          <ul className="space-y-1">
            {delegateGrants.map((g: DelegationGrant) => (
              <li
                key={g.id}
                data-testid={`delegation-row-${g.id}`}
                className="flex items-center justify-between rounded border border-border px-3 py-2 text-sm"
              >
                <div className="flex-1 min-w-0">
                  <span className="font-medium">{g.delegatorId}</span>
                  <span className="ml-2 text-xs text-muted-foreground">
                    {periodText(g)}
                  </span>
                </div>
                <div className="ml-2">
                  <DelegationStatusBadge grant={g} />
                  {/* NO revoke action — revoke is delegator-only */}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {createOpen && (
        <DelegationCreateDialog onClose={() => setCreateOpen(false)} />
      )}
      {revokeTarget && (
        <DelegationRevokeDialog
          grant={revokeTarget}
          onClose={() => setRevokeTarget(null)}
        />
      )}
    </section>
  );
}

// ===========================================================================
// Create dialog.
// ===========================================================================

function DelegationCreateDialog({ onClose }: { onClose: () => void }) {
  const [delegateId, setDelegateId] = useState('');
  const [validFrom, setValidFrom] = useState('');
  const [validTo, setValidTo] = useState('');
  const [reason, setReason] = useState('');
  const createM = useCreateDelegation();

  const ok =
    delegateId.trim() !== '' &&
    validFrom.trim() !== '';
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

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="delegation-create-dialog"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="위임 생성"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">위임 생성</h2>

        <div className="mt-4">
          <label
            htmlFor="delegation-create-delegateId"
            className="block text-sm font-medium text-foreground"
          >
            대결자 ID <span aria-hidden="true">*</span>
          </label>
          <input
            id="delegation-create-delegateId"
            data-testid="delegation-create-delegateId"
            value={delegateId}
            onChange={(e) => setDelegateId(e.target.value)}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            placeholder="emp-…"
          />
        </div>

        <div className="mt-4">
          <label
            htmlFor="delegation-create-validFrom"
            className="block text-sm font-medium text-foreground"
          >
            시작일 (validFrom) <span aria-hidden="true">*</span>
          </label>
          <input
            id="delegation-create-validFrom"
            data-testid="delegation-create-validFrom"
            type="date"
            value={validFrom}
            onChange={(e) => setValidFrom(e.target.value)}
            onClick={showPickerOnClick}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          />
        </div>

        <div className="mt-4">
          <label
            htmlFor="delegation-create-validTo"
            className="block text-sm font-medium text-foreground"
          >
            종료일 (validTo) — 선택(빈 값=무기한)
          </label>
          <input
            id="delegation-create-validTo"
            data-testid="delegation-create-validTo"
            type="date"
            value={validTo}
            onChange={(e) => setValidTo(e.target.value)}
            onClick={showPickerOnClick}
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          />
        </div>

        <div className="mt-4">
          <label
            htmlFor="delegation-create-reason"
            className="block text-sm font-medium text-foreground"
          >
            사유 (선택)
          </label>
          <textarea
            id="delegation-create-reason"
            data-testid="delegation-create-reason"
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
            data-testid="delegation-error"
          >
            {approvalErrorMessage(createM.error)}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={createM.isPending}
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!canConfirm}
            data-testid="delegation-create-submit"
          >
            {createM.isPending ? '처리 중…' : '위임 생성'}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ===========================================================================
// Revoke dialog — reason required.
// ===========================================================================

function DelegationRevokeDialog({
  grant,
  onClose,
}: {
  grant: DelegationGrant;
  onClose: () => void;
}) {
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

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
      data-testid="delegation-revoke-dialog"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="위임 회수"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">위임 회수</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          대결자: <span className="font-medium text-foreground">{grant.delegateId}</span>
        </p>
        <label
          htmlFor="delegation-revoke-reason"
          className="mt-4 block text-sm font-medium text-foreground"
        >
          사유 <span aria-hidden="true">*</span>
        </label>
        <textarea
          id="delegation-revoke-reason"
          data-testid="delegation-revoke-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          maxLength={512}
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
          placeholder="회수 사유를 입력하세요 (필수)"
        />
        {!ok && (
          <p
            className="mt-1 text-xs text-destructive"
            role="status"
          >
            회수에는 사유가 필요합니다.
          </p>
        )}

        {revokeM.error ? (
          <p
            className="mt-4 text-sm text-destructive"
            role="status"
            data-testid="delegation-error"
          >
            {approvalErrorMessage(revokeM.error)}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={revokeM.isPending}
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!ok || revokeM.isPending}
            data-testid="delegation-revoke-confirm"
            className="bg-destructive text-destructive-foreground hover:opacity-90"
          >
            {revokeM.isPending ? '처리 중…' : '회수'}
          </Button>
        </div>
      </div>
    </div>
  );
}
