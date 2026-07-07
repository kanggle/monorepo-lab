'use client';

import { useEffect, useId, useRef } from 'react';
import { useOrgScopeForm } from '../hooks/use-org-scope-form';
import { OrgScopeDialogBody } from './OrgScopeDialogBody';

/**
 * Per-operator org_scope (데이터-스코프) dialog (TASK-PC-FE-050 — sibling of
 * `OperatorConfirmDialog` / `OperatorProfileEditDialog`). Reads the
 * operator's assignment row for the ACTIVE tenant
 * (`GET .../{operatorId}/assignments`) and sets / clears its `org_scope`
 * (`PUT .../assignments/{tenantId}/org-scope`) — the source half of the
 * org_scope end-to-end loop (설정 → IAM 저장(BE-339) → assume-tenant 전파 →
 * erp 소비(ERP-BE-008) → read 카드(PC-FE-049)).
 *
 * TASK-PC-FE-112 split: this component is now a presentational shell — the
 * tri-state form logic (seed / degrade-aware id derivation / submit mutation)
 * lives in `useOrgScopeForm` (the PC-FE-106 fat-container → custom-hook
 * pattern). The dialog owns only the view concerns (ids, focus container,
 * Escape-to-close) + the JSX. 0 behavior change.
 *
 * TRI-STATE (null ≠ [] is unambiguous in BOTH the UI and the wire):
 *   - 전체 (net-zero)        → `orgScope: null`  (clear; default for a
 *                             currently-null assignment; "전 부서 — 데이터-
 *                             스코프 미적용").
 *   - 선택 부서 (subtree)     → `orgScope: [<dept-id>...]` (department
 *                             multi-select; subtree-root; active depts only,
 *                             label `code · name`).
 *   - 차단 (zero-scope, adv.) → `orgScope: []`     (explicit; an extra warning
 *                             + confirm — the operator will see/write NOTHING
 *                             in this tenant).
 *
 * DEGRADE (green-wash 금지):
 *   - erp departments fetch fails (503 / tenant not erp-entitled) → the
 *     dialog does NOT fail; it falls back to a manual id entry (textarea,
 *     one id per line / comma-separated) + a warning banner.
 *   - the operator has NO assignment row in the active tenant (GET returns an
 *     empty array → the BE returns 404 on PUT) → guidance ("이 테넌트에 명시
 *     배정 없음 → org_scope 부적용(전체)") + Save disabled.
 *
 * reason-gated (same posture as the other operator mutations): Save is
 * disabled until a non-empty reason is entered; the proxy forwards it as
 * `X-Operator-Reason` (the producer rejects an empty reason with
 * `400 REASON_REQUIRED`).
 */

export interface OrgScopeDialogProps {
  open: boolean;
  /** Target operator id (path var for both GET + PUT). */
  operatorId: string;
  /** Human-friendly label (email or operatorId) for the heading. */
  operatorLabel: string;
  onClose: () => void;
}

export function OrgScopeDialog({
  open,
  operatorId,
  operatorLabel,
  onClose,
}: OrgScopeDialogProps) {
  const titleId = useId();
  const descId = useId();
  const reasonId = useId();
  const manualId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  const f = useOrgScopeForm({ open, operatorId, onClose });

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="org-scope-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="org-scope-dialog"
        className="w-full max-w-lg overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-lg"
        style={{ maxHeight: '90vh' }}
      >
        <OrgScopeDialogBody
          f={f}
          operatorLabel={operatorLabel}
          titleId={titleId}
          descId={descId}
          reasonId={reasonId}
          manualId={manualId}
          onClose={onClose}
        />
      </div>
    </div>
  );
}
