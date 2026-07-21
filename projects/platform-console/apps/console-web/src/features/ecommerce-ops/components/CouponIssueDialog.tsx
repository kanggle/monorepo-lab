'use client';

import { useId, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useIssueCoupons } from '../hooks/use-ecommerce-promotions';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Confirm-gated coupon-issue dialog (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Mirrors StockAdjustDialog: a form-wrapped ConfirmDialog.
 *
 * The operator pastes / types a newline- or comma-separated list of userIds.
 * The input is split, trimmed, and de-duped before submitting to
 * `POST /api/promotions/{id}/coupons/issue` with `{ userIds: string[] }`.
 *
 * Validation: at least 1 non-empty userId required before the confirm button
 * is unlocked. On success: shows issuedCount in a success notice. On error:
 * COUPON_LIMIT_EXCEEDED / PROMOTION_NOT_ACTIVE 422 inline (no crash).
 *
 * NO `Idempotency-Key` (producer defines none — § 2.4.10); confirm-gate is
 * the sole double-submit guard.
 */
export interface CouponIssueDialogProps {
  open: boolean;
  promotionId: string;
  onClose: () => void;
  onIssued: () => void;
}

export function CouponIssueDialog({
  open,
  promotionId,
  onClose,
  onIssued,
}: CouponIssueDialogProps) {
  const textareaId = useId();
  const issue = useIssueCoupons();

  const [rawUserIds, setRawUserIds] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [successCount, setSuccessCount] = useState<number | null>(null);
  // Idempotency-Key for THIS issue intent (TASK-PC-FE-252). Minted lazily on
  // confirm and reused across a retry of the same batch (so a double-submit
  // dedupes). Editing the userId list, a successful issue, or cancel clears it —
  // re-issuing the SAME batch (a legitimate second issue, TASK-BE-536) then mints
  // a fresh key instead of being swallowed as a replay.
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  /** Split by newline or comma, trim, filter empty. */
  function parseUserIds(raw: string): string[] {
    return raw
      .split(/[\n,]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  const userIds = parseUserIds(rawUserIds);
  const valid = userIds.length > 0;

  function reset() {
    setRawUserIds('');
    setError(null);
    setSuccessCount(null);
    setIdempotencyKey(null);
  }

  function confirm() {
    if (!valid) return;
    setError(null);
    setSuccessCount(null);
    // Reuse the held key across a retry of the same batch; mint on first confirm.
    const key = idempotencyKey ?? crypto.randomUUID();
    setIdempotencyKey(key);
    issue.mutate(
      { id: promotionId, body: { userIds }, idempotencyKey: key },
      {
        onSuccess: (result) => {
          const count =
            result && typeof (result as { issuedCount?: number }).issuedCount === 'number'
              ? (result as { issuedCount: number }).issuedCount
              : userIds.length;
          setSuccessCount(count);
          // The dialog stays open on success; drop the key so a deliberate
          // re-issue is a fresh intent, not a replay (TASK-PC-FE-252, AC-4).
          setIdempotencyKey(null);
          onIssued();
        },
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setError(messageForCode(code, '쿠폰을 발급하지 못했습니다.'));
        },
      },
    );
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

  return (
    <ConfirmDialog
      open={open}
      title="쿠폰을 발급할까요?"
      description="발급할 사용자 ID 목록을 입력하세요 (줄바꿈 또는 쉼표로 구분). 최소 1명 이상이어야 합니다."
      confirmLabel="발급"
      pending={issue.isPending}
      confirmDisabled={!valid}
      errorMessage={error}
      onConfirm={confirm}
      onCancel={() => {
        reset();
        onClose();
      }}
    >
      <div className="space-y-3" data-testid="coupon-issue-form">
        <div>
          <label
            htmlFor={textareaId}
            className="block text-sm font-medium text-foreground"
          >
            사용자 ID 목록 <span className="text-destructive">*</span>
          </label>
          <textarea
            id={textareaId}
            value={rawUserIds}
            onChange={(e) => {
              setRawUserIds(e.target.value);
              // A different userId list = a different intent → drop the held key
              // (TASK-PC-FE-252, AC-3).
              setIdempotencyKey(null);
            }}
            rows={5}
            placeholder="user-uuid-1&#10;user-uuid-2&#10;user-uuid-3"
            className={inputCls}
            data-testid="coupon-issue-userids"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            {valid
              ? `${userIds.length}명이 입력되었습니다.`
              : '최소 1명의 사용자 ID를 입력하세요.'}
          </p>
        </div>
        {successCount !== null && (
          <p
            role="status"
            data-testid="coupon-issue-success"
            className="rounded-md border border-green-300/50 bg-green-50 px-3 py-2 text-sm text-green-900 dark:border-green-700/40 dark:bg-green-950/40 dark:text-green-200"
          >
            쿠폰 {successCount}건 발급 완료.
          </p>
        )}
      </div>
    </ConfirmDialog>
  );
}
