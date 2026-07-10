'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  ELEVATED_ROLE_NEVER_GRANTABLE,
  KNOWN_ORG_ADMIN_ROLES,
  ORG_ADMIN_ROLE,
  type OrgAdmin,
  type OrgNode,
} from '../api/types';
import { OrgReasonDialog } from './OrgReasonDialog';

/**
 * Node-admin assignment panel (TASK-PC-FE-237 / ADR-047 D3 — subtree-scoped
 * `ORG_ADMIN`). Lists grants, a grant form (operatorId + roleName), and revoke.
 *
 * The role select is pre-filtered by the grantable-roles convention (reused
 * from `features/operators`): the offered roles are the intersection of the
 * known assignable set with the CALLER's server-provided grantable roles.
 * `SUPER_ADMIN` is NEVER offered (a node grant can't mint the platform role —
 * it is absent from `KNOWN_ORG_ADMIN_ROLES`). When the grantable-roles fetch
 * fails (`grantableRoles === null`), the panel offers the FULL known set —
 * never an empty select — mirroring `use-create-operator-form.ts`. The
 * pre-filter is UX only: the server stays the authority (403
 * `ROLE_GRANT_FORBIDDEN` / 422 `ORG_ADMIN_GRANT_OUT_OF_CEILING` are surfaced
 * verbatim via `grantError`).
 */
export interface OrgAdminPanelProps {
  node: OrgNode;
  admins: OrgAdmin[];
  adminsLoading: boolean;
  adminsError: string | null;
  /** The caller's grantable roles; `null` ⇒ fetch failed → full known set. */
  grantableRoles: string[] | null;
  onGrant: (operatorId: string, roleName: string, reason: string) => void;
  onRevoke: (operatorId: string, reason: string) => void;
  grantPending: boolean;
  grantError: string | null;
  revokePending: boolean;
  revokeError: string | null;
}

export function OrgAdminPanel({
  node,
  admins,
  adminsLoading,
  adminsError,
  grantableRoles,
  onGrant,
  onRevoke,
  grantPending,
  grantError,
  revokePending,
  revokeError,
}: OrgAdminPanelProps) {
  const roleOptions = useMemo<string[]>(() => {
    // A failed grantable-roles fetch offers the full known set rather than an
    // empty select (the `use-create-operator-form` convention) — the producer
    // remains the authority and 403s an over-grant.
    const base: string[] =
      grantableRoles === null
        ? [...KNOWN_ORG_ADMIN_ROLES]
        : KNOWN_ORG_ADMIN_ROLES.filter((r) => grantableRoles.includes(r));
    // SUPER_ADMIN is never offered from a node grant. It is already absent from
    // KNOWN_ORG_ADMIN_ROLES, so today this filter removes nothing — it is a
    // guard against a future edit to that constant, not dead weight. Typed as
    // string[] deliberately: narrowing to the union would make the guard
    // un-writable (TS2367) and silently drop the protection.
    return base.filter((r) => r !== ELEVATED_ROLE_NEVER_GRANTABLE);
  }, [grantableRoles]);

  const [operatorId, setOperatorId] = useState('');
  const [role, setRole] = useState<string>(
    roleOptions.includes(ORG_ADMIN_ROLE) ? ORG_ADMIN_ROLE : roleOptions[0] ?? '',
  );
  const [granting, setGranting] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);

  const effectiveRole = roleOptions.includes(role) ? role : roleOptions[0] ?? '';
  const canGrant = operatorId.trim().length > 0 && effectiveRole !== '';

  return (
    <section aria-labelledby="org-admins-heading" className="space-y-3">
      <h3
        id="org-admins-heading"
        className="text-base font-semibold text-foreground"
      >
        노드 관리자 (ORG_ADMIN)
      </h3>

      {adminsError ? (
        <p role="alert" data-testid="org-admins-error" className="text-sm text-destructive">
          {adminsError}
        </p>
      ) : adminsLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중…</p>
      ) : admins.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="org-admins-empty">
          배정된 노드 관리자가 없습니다.
        </p>
      ) : (
        <ul data-testid="org-admins-list" className="divide-y divide-border rounded-md border border-border">
          {admins.map((a) => (
            <li
              key={a.operatorId}
              data-testid={`org-admin-row-${a.operatorId}`}
              className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
            >
              <span className="min-w-0">
                <span className="font-medium text-foreground">{a.operatorId}</span>{' '}
                <span className="text-muted-foreground">
                  · {a.roleName} · {formatDateTime(a.grantedAt)}
                </span>
              </span>
              <Button
                variant="ghost"
                onClick={() => setRevokeTarget(a.operatorId)}
                disabled={revokePending}
                data-testid={`org-admin-revoke-${a.operatorId}`}
              >
                해제
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form
        className="space-y-2 rounded-md border border-border p-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (canGrant) setGranting(true);
        }}
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="org-admin-operator" className="text-sm font-medium text-foreground">
            운영자 ID
          </label>
          <input
            id="org-admin-operator"
            value={operatorId}
            onChange={(e) => setOperatorId(e.target.value)}
            data-testid="org-admin-operator-input"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="operator-uuid"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="org-admin-role" className="text-sm font-medium text-foreground">
            역할
          </label>
          <select
            id="org-admin-role"
            value={effectiveRole}
            onChange={(e) => setRole(e.target.value)}
            data-testid="org-admin-role-select"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {roleOptions.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>

        {grantError && (
          <p role="alert" data-testid="org-admin-grant-error" className="text-sm text-destructive">
            {grantError}
          </p>
        )}

        <Button type="submit" disabled={!canGrant || grantPending} data-testid="org-admin-grant">
          관리자 배정
        </Button>
      </form>

      {granting && (
        <OrgReasonDialog
          title="노드 관리자 배정"
          description={`${node.name} 노드에 운영자(${operatorId.trim()})를 ${effectiveRole} 로 배정합니다.`}
          confirmLabel="배정"
          pending={grantPending}
          error={grantError}
          onConfirm={(reason) => {
            onGrant(operatorId.trim(), effectiveRole, reason);
            setGranting(false);
          }}
          onCancel={() => setGranting(false)}
        />
      )}

      {revokeTarget !== null && (
        <OrgReasonDialog
          title="노드 관리자 해제"
          description={`운영자(${revokeTarget})의 이 노드 관리자 배정을 해제합니다.`}
          confirmLabel="해제"
          tone="destructive"
          pending={revokePending}
          error={revokeError}
          onConfirm={(reason) => {
            onRevoke(revokeTarget, reason);
            setRevokeTarget(null);
          }}
          onCancel={() => setRevokeTarget(null)}
        />
      )}
    </section>
  );
}
