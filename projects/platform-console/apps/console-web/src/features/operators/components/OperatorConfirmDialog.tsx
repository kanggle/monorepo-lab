'use client';

import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';
import { KNOWN_OPERATOR_ROLES, ELEVATED_ROLE } from '../api/types';

/**
 * Reason-capture + confirm dialog for the privilege-sensitive operators
 * mutations (console-integration-contract § 2.4.3 / audit-heavy / saas S5).
 *
 * This mirrors the FE-002 `features/accounts` `ConfirmActionDialog` reason-
 * capture pattern. It is a feature-LOCAL component (not a cross-feature
 * import — architecture.md § Forbidden Dependencies forbids
 * `features/operators → features/accounts`; the shared value would have to
 * be promoted to `shared/`, which is out of this slice's scope).
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
 *   - Keyboard-operable + WCAG AA: focus moves into the dialog on open,
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
  const titleId = useId();
  const descId = useId();
  const reasonId = useId();
  const rolesId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = useState('');
  const [roles, setRoles] = useState<string[]>([]);

  useEffect(() => {
    if (open) {
      setReason('');
      setRoles(roleEditor?.initialRoles ?? []);
      const t = setTimeout(() => reasonRef.current?.focus(), 0);
      return () => clearTimeout(t);
    }
  }, [open, roleEditor]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onCancel();
      }
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, textarea, input, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  // feat/iam-grantable-roles-filter — render KNOWN_OPERATOR_ROLES ∩
  // grantableRoles, UNIONED with the operator's initialRoles (an
  // already-held role stays visible/removable even outside the caller's
  // grantable set). `null` ⇒ render every KNOWN_OPERATOR_ROLES (fallback).
  // Plain (non-memoised) derivation — cheap over a ≤6-item constant array,
  // and this component already returns `null` above for the closed state,
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
  const canConfirm = reasonOk && !pending;
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
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="operator-confirm-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="operator-confirm-dialog"
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2
          id={titleId}
          className={
            elevated
              ? 'text-lg font-semibold text-destructive'
              : 'text-lg font-semibold text-foreground'
          }
        >
          {title}
        </h2>
        <div id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </div>

        {roleEditor && (
          <fieldset className="mt-4">
            <legend
              className="text-sm font-medium text-foreground"
              id={rolesId}
            >
              역할 (전체 교체 — 비우면 모든 역할 제거)
            </legend>
            <div
              className="mt-2 flex flex-wrap gap-3"
              role="group"
              aria-labelledby={rolesId}
            >
              {renderableRoles.map((role) => (
                <label
                  key={role}
                  className="flex items-center gap-2 text-sm text-foreground"
                >
                  <input
                    type="checkbox"
                    checked={roles.includes(role)}
                    onChange={() => toggleRole(role)}
                    data-testid={`edit-roles-${role}`}
                  />
                  <span
                    className={
                      role === ELEVATED_ROLE
                        ? 'font-medium text-destructive'
                        : undefined
                    }
                  >
                    {role}
                    {role === ELEVATED_ROLE ? ' (특권)' : ''}
                  </span>
                </label>
              ))}
            </div>
            {removingAll && (
              <p
                className="mt-2 text-xs text-destructive"
                data-testid="edit-roles-remove-all-warning"
                role="status"
              >
                이 운영자의 모든 역할이 제거됩니다. 이후 어떤 운영 권한도
                갖지 않습니다.
              </p>
            )}
            {grantingElevated && (
              <p
                className="mt-2 text-xs text-destructive"
                data-testid="edit-roles-elevated-warning"
                role="status"
              >
                이 운영자에게 SUPER_ADMIN 특권을 부여합니다.
              </p>
            )}
          </fieldset>
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

        {errorMessage && (
          <p
            role="alert"
            data-testid="operator-confirm-error"
            className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {errorMessage}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={onCancel}
            disabled={pending}
            data-testid="operator-confirm-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={() =>
              canConfirm &&
              onConfirm(reason.trim(), roleEditor ? roles : undefined)
            }
            disabled={!canConfirm}
            data-testid="operator-confirm-submit"
            className={
              elevated
                ? 'bg-destructive text-destructive-foreground hover:opacity-90'
                : undefined
            }
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
