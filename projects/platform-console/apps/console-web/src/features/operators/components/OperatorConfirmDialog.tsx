'use client';

import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { KNOWN_OPERATOR_ROLES, ELEVATED_ROLE } from '../api/types';
import { OperatorConfirmRoleEditor } from './OperatorConfirmRoleEditor';

/**
 * Reason-capture + confirm dialog for the privilege-sensitive operators
 * mutations (console-integration-contract § 2.4.3 / audit-heavy / saas S5).
 *
 * Thin wrapper over the shared {@link ConfirmDialog} primitive
 * (TASK-PC-FE-262): the shell (backdrop / `role="dialog"` frame / ARIA /
 * Escape / focus trap / error banner / footer) lives in `shared/ui`, and this
 * file owns only the reason state + the domain body (the required reason
 * textarea and the optional role multi-select), passed as `children`. The
 * public prop API is UNCHANGED — including the `elevated` prop name, which is
 * mapped to the primitive's `destructive` internally, so no caller changes.
 *
 * Invariants:
 *   - `onConfirm` is NOT called until a NON-EMPTY operator reason is
 *     entered — the producer call cannot fire without it (task Failure
 *     Scenario "privilege action with no confirm/reason gate"). No
 *     one-click create / role-grant / suspend.
 *   - When `roleEditor` is set the dialog ALSO renders the role multi-
 *     select (edit-roles); confirming returns the selected roles. An empty
 *     selection (`[]` = remove all roles) is permitted by the producer but
 *     gets `elevatedCopy` strong-confirm wording from the caller.
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open (the
 *     reason textarea — passed as the primitive's `initialFocusRef`),
 *     `Escape` cancels, focus is trapped, `role="dialog"` + `aria-modal` +
 *     labelled/described. axe-clean.
 */

export interface OperatorConfirmDialogProps {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  /** Privilege-high action → destructive styling + elevated copy. */
  elevated?: boolean;
  pending?: boolean;
  /** Inline actionable error from the last attempt (no crash). */
  errorMessage?: string | null;
  /** When set, render the role multi-select (edit-roles); seed selection. */
  roleEditor?: { initialRoles: string[] };
  /** feat/iam-grantable-roles-filter — the seed role names the CALLING
   *  operator may grant. The edit-roles checkboxes render the intersection
   *  of `KNOWN_OPERATOR_ROLES` and this set, UNIONED with the operator's
   *  `roleEditor.initialRoles` (a role the operator already holds stays
   *  visible even when it falls outside the caller's grantable set — e.g. a
   *  non-platform admin editing a `SUPER_ADMIN` row — so the display never
   *  silently drops an already-granted role; it can still be seen/removed).
   *  `null` / `undefined` ⇒ render the full `KNOWN_OPERATOR_ROLES` set
   *  (fallback — the producer 403 stays the final no-escalation authority
   *  either way). */
  grantableRoles?: string[] | null;
  onConfirm: (reason: string, roles?: string[]) => void;
  onCancel: () => void;
}

export function OperatorConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  elevated = false,
  pending = false,
  errorMessage,
  roleEditor,
  grantableRoles = null,
  onConfirm,
  onCancel,
}: OperatorConfirmDialogProps) {
  const reasonId = useId();
  const rolesId = useId();
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');
  const [roles, setRoles] = useState<string[]>([]);

  // Reset transient input each time the dialog opens (a fresh user action →
  // a fresh reason). Focus-into-dialog is the shared primitive's job — it
  // focuses `initialFocusRef` (the reason textarea) on open.
  useEffect(() => {
    if (open) {
      setReason('');
      setRoles(roleEditor?.initialRoles ?? []);
    }
  }, [open, roleEditor]);

  // feat/iam-grantable-roles-filter — render KNOWN_OPERATOR_ROLES ∩
  // grantableRoles, UNIONED with the operator's initialRoles (an
  // already-held role stays visible/removable even outside the caller's
  // grantable set). `null` ⇒ render every KNOWN_OPERATOR_ROLES (fallback).
  // Plain (non-memoised) derivation — cheap over a ≤6-item constant array,
  // and this component already returns `null` below for the closed state,
  // so a `useMemo` here would run conditionally (rules-of-hooks violation).
  const renderableRoles =
    grantableRoles === null
      ? KNOWN_OPERATOR_ROLES
      : KNOWN_OPERATOR_ROLES.filter(
          (role) =>
            grantableRoles.includes(role) ||
            (roleEditor?.initialRoles ?? []).includes(role),
        );

  if (!open) return null;

  const reasonOk = reason.trim().length > 0;
  const removingAll = roleEditor !== undefined && roles.length === 0;
  const grantingElevated =
    roleEditor !== undefined && roles.includes(ELEVATED_ROLE);

  function toggleRole(role: string) {
    setRoles((prev) =>
      prev.includes(role)
        ? prev.filter((r) => r !== role)
        : [...prev, role],
    );
  }

  return (
    <ConfirmDialog
      open={open}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      destructive={elevated}
      pending={pending}
      confirmDisabled={!reasonOk}
      errorMessage={errorMessage}
      dialogTestId="operator-confirm-dialog"
      overlayTestId="operator-confirm-overlay"
      cancelTestId="operator-confirm-cancel"
      confirmTestId="operator-confirm-submit"
      errorTestId="operator-confirm-error"
      initialFocusRef={reasonRef}
      onConfirm={() =>
        reasonOk &&
        !pending &&
        onConfirm(reason.trim(), roleEditor ? roles : undefined)
      }
      onCancel={onCancel}
    >
      {roleEditor && (
        <OperatorConfirmRoleEditor
          rolesId={rolesId}
          renderableRoles={renderableRoles}
          roles={roles}
          toggleRole={toggleRole}
          removingAll={removingAll}
          grantingElevated={grantingElevated}
        />
      )}

      <div className="mt-4">
        <label
          htmlFor={reasonId}
          className="block text-sm font-medium text-foreground"
        >
          감사 사유 <span aria-hidden="true">*</span>
          <span className="sr-only">(필수)</span>
        </label>
        <textarea
          id={reasonId}
          ref={reasonRef}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          aria-required="true"
          rows={3}
          data-testid="operator-confirm-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="이 운영 작업의 사유를 입력하세요 (감사 기록에 남습니다)"
        />
        {!reasonOk && (
          <p
            className="mt-1 text-xs text-muted-foreground"
            data-testid="operator-reason-required-hint"
          >
            사유를 입력해야 작업을 진행할 수 있습니다.
          </p>
        )}
      </div>
    </ConfirmDialog>
  );
}
