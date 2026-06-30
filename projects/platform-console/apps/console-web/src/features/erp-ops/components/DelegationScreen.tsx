'use client';

import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import type { DelegationGrant } from '../api/delegation-types';
import { approvalErrorMessage } from './approval-error';
import { DelegationGrantList } from './DelegationGrantList';
import {
  useDelegationScreen,
  useDelegationCreate,
  useDelegationRevoke,
} from './use-delegation-screen';

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
 *
 * TASK-PC-FE-150 — behaviour-preserving split: the screen's state +
 * mutations live in `use-delegation-screen.ts`; the two grant lists in
 * the presentational `DelegationGrantList`. Render output / DOM /
 * data-testid / ARIA / wire bodies are unchanged.
 */

// ---------------------------------------------------------------------------
// DelegationScreen.
// ---------------------------------------------------------------------------

export function DelegationScreen() {
  const {
    createOpen,
    openCreate,
    closeCreate,
    revokeTarget,
    openRevoke,
    closeRevoke,
    delegatorQ,
    delegateQ,
    delegatorGrants,
    delegateGrants,
  } = useDelegationScreen();

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
          onClick={openCreate}
          data-testid="delegation-create"
        >
          위임 생성
        </Button>
      </div>

      {/* DELEGATOR — grants I issued */}
      <DelegationGrantList
        testid="delegation-list-delegator"
        heading="내가 위임한 (delegator)"
        query={delegatorQ}
        grants={delegatorGrants}
        emptyText="위임한 grant 가 없습니다."
        idField="delegateId"
        onRevoke={openRevoke}
      />

      {/* DELEGATE — grants delegated to me */}
      <DelegationGrantList
        testid="delegation-list-delegate"
        heading="나에게 위임된 (delegate)"
        query={delegateQ}
        grants={delegateGrants}
        emptyText="나에게 위임된 grant 가 없습니다."
        idField="delegatorId"
      />

      {createOpen && <DelegationCreateDialog onClose={closeCreate} />}
      {revokeTarget && (
        <DelegationRevokeDialog grant={revokeTarget} onClose={closeRevoke} />
      )}
    </section>
  );
}

// ===========================================================================
// Create dialog.
// ===========================================================================

function DelegationCreateDialog({ onClose }: { onClose: () => void }) {
  const {
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
  } = useDelegationCreate(onClose);

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
  const { reason, setReason, revokeM, ok, onConfirm } = useDelegationRevoke(
    grant,
    onClose,
  );

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
