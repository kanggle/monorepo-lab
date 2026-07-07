'use client';

import { ELEVATED_ROLE } from '../api/types';

/**
 * Role multi-select fieldset for the edit-roles path of
 * `OperatorConfirmDialog` (TASK-PC-FE-209 split). Presentational — the
 * renderable-role derivation, the current selection, and the toggle handler
 * live in the `OperatorConfirmDialog` container and arrive via props. The
 * privilege-sensitive copy (remove-all / grant-SUPER_ADMIN warnings, the
 * `(특권)` role annotation) is preserved verbatim.
 */
export interface OperatorConfirmRoleEditorProps {
  rolesId: string;
  /** KNOWN_OPERATOR_ROLES ∩ grantable, ∪ already-held (derived in container). */
  renderableRoles: readonly string[];
  /** The current selection. */
  roles: string[];
  toggleRole: (role: string) => void;
  /** True ⇒ the selection is empty (all roles removed). */
  removingAll: boolean;
  /** True ⇒ the selection includes the elevated (SUPER_ADMIN) role. */
  grantingElevated: boolean;
}

export function OperatorConfirmRoleEditor({
  rolesId,
  renderableRoles,
  roles,
  toggleRole,
  removingAll,
  grantingElevated,
}: OperatorConfirmRoleEditorProps) {
  return (
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
  );
}
