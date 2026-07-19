'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  ELEVATED_ROLE_NEVER_GRANTABLE,
  KNOWN_GROUP_ROLES,
  type AddGrantsInput,
} from '../api/types';
import { GroupReasonDialog } from './GroupReasonDialog';

/**
 * Add-grants dialog (TASK-PC-FE-250 / ADR-MONO-046 D4 — no-escalation). Adds a
 * role grant and/or a tenant-assignment grant template to the group; the
 * producer fans it out to every current member. At least one of a role /
 * tenant-assignment is required, then the reason-capture gate.
 *
 * The role select is pre-filtered by the grantable-roles convention (reused
 * from `features/operators` via `getGrantableRolesOrNull` threaded from the
 * screen): the offered roles are the intersection of {@link KNOWN_GROUP_ROLES}
 * with the CALLER's server-provided grantable roles. `SUPER_ADMIN` is NEVER
 * offered (already absent from `KNOWN_GROUP_ROLES`; the filter guards a future
 * edit to that constant). When the grantable-roles fetch fails
 * (`grantableRoles === null`), the picker offers the FULL known set — never an
 * empty select — mirroring `use-create-operator-form`. The pre-filter is UX
 * only: the server stays the authority (`403 ROLE_GRANT_FORBIDDEN` /
 * `422 GROUP_GRANT_NO_ESCALATION` surfaced verbatim via `error`).
 */
export interface GroupGrantDialogProps {
  groupName: string;
  /** The caller's grantable roles; `null` ⇒ fetch failed → full known set. */
  grantableRoles: string[] | null;
  pending: boolean;
  error: string | null;
  onConfirm: (input: AddGrantsInput, reason: string) => void;
  onCancel: () => void;
}

export function GroupGrantDialog({
  groupName,
  grantableRoles,
  pending,
  error,
  onConfirm,
  onCancel,
}: GroupGrantDialogProps) {
  const roleOptions = useMemo<string[]>(() => {
    const base: string[] =
      grantableRoles === null
        ? [...KNOWN_GROUP_ROLES]
        : KNOWN_GROUP_ROLES.filter((r) => grantableRoles.includes(r));
    // SUPER_ADMIN is never offered from a group grant. It is already absent from
    // KNOWN_GROUP_ROLES, so today this removes nothing — it is a guard against a
    // future widening of that constant, not dead weight. Typed as string[] so
    // the comparison stays writable (narrowing to the union makes it TS2367).
    return base.filter((r) => r !== ELEVATED_ROLE_NEVER_GRANTABLE);
  }, [grantableRoles]);

  const [role, setRole] = useState<string>('');
  const [tenantId, setTenantId] = useState('');
  const [confirming, setConfirming] = useState(false);

  const roleSelected = role !== '';
  const tenantSelected = tenantId.trim() !== '' && tenantId.trim() !== '*';
  const canSubmit = roleSelected || tenantSelected;

  function buildInput(): AddGrantsInput {
    const input: AddGrantsInput = {};
    if (roleSelected) input.roles = [role];
    if (tenantSelected) input.tenantAssignments = [{ tenantId: tenantId.trim() }];
    return input;
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-grant-add-title"
    >
      <div className="w-full max-w-md space-y-3 rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="group-grant-add-title"
          className="text-lg font-semibold text-foreground"
        >
          Grant 추가
        </h2>
        <p className="text-sm text-muted-foreground">
          「{groupName}」 그룹에 역할 또는 tenant-assignment 를 부여합니다. 전
          멤버에게 fan-out 되며, 본인이 보유하지 않은 역할·스코프 밖 테넌트는
          서버가 거부합니다 (no-escalation).
        </p>

        <div className="flex flex-col gap-1">
          <label htmlFor="group-grant-role" className="text-sm font-medium text-foreground">
            역할 (선택)
          </label>
          <select
            id="group-grant-role"
            value={role}
            onChange={(e) => setRole(e.target.value)}
            data-testid="group-grant-role-select"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <option value="">(부여 안 함)</option>
            {roleOptions.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="group-grant-tenant"
            className="text-sm font-medium text-foreground"
          >
            tenant-assignment 테넌트 ID (선택)
          </label>
          <input
            id="group-grant-tenant"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            data-testid="group-grant-tenant-input"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="acme-corp"
          />
        </div>

        {error && (
          <p role="alert" data-testid="group-grant-error" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            취소
          </Button>
          <Button
            onClick={() => setConfirming(true)}
            disabled={!canSubmit || pending}
            data-testid="group-grant-next"
          >
            다음
          </Button>
        </div>
      </div>

      {confirming && (
        <GroupReasonDialog
          title="Grant 추가"
          description={`「${groupName}」 그룹에 ${
            roleSelected ? `역할 ${role}` : ''
          }${roleSelected && tenantSelected ? ' · ' : ''}${
            tenantSelected ? `tenant-assignment ${tenantId.trim()}` : ''
          } 를 부여합니다.`}
          confirmLabel="부여"
          pending={pending}
          error={error}
          onConfirm={(reason) => {
            onConfirm(buildInput(), reason);
            setConfirming(false);
          }}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  );
}
