'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { PartnershipList } from '../api/types';
import { PartnershipConfirmDialog } from './PartnershipConfirmDialog';
import { PartnershipInviteForm } from './PartnershipInviteForm';
import { PartnershipRow } from './PartnershipRow';
import {
  actionMeta,
  parseList,
  runMutation,
  type PendingAction,
} from './partnerships-actions';

/**
 * Cross-org partnership management surface (TASK-PC-FE-187 / ADR-MONO-045 §3.4
 * step 3). host-side (내가 발행) + partner-side (나에게 위임됨) sections split by
 * `myRole`. Every mutation is reason-gated + confirm-gated via
 * {@link PartnershipConfirmDialog}, then `router.refresh()`.
 *
 * Status-transition button gating (contract § Status 전이 매트릭스):
 *   - PENDING + partner → accept, terminate
 *   - PENDING + host    → terminate (withdraw) only — NO accept (partner right)
 *   - ACTIVE  + either  → suspend, terminate (+ partner: participant add/remove)
 *   - SUSPENDED + either → reactivate, terminate
 *   - TERMINATED        → no actions (history)
 *
 * INVARIANT: the host tenant is NEVER client-supplied — the invite POST body
 * carries only `partnerTenantId`; the host tenant is the server-side active
 * tenant. The operator token is server-only (proxy attaches it). The reason is
 * captured by the dialog (submit disabled while empty) and percent-encoded onto
 * `X-Operator-Reason` server-side.
 */

export interface PartnershipsScreenProps {
  initial: PartnershipList;
  activeTenant: string;
}

export function PartnershipsScreen({
  initial,
  activeTenant,
}: PartnershipsScreenProps) {
  const router = useRouter();
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(
    null,
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // invite form
  const [invitePartner, setInvitePartner] = useState('');
  const [inviteDomains, setInviteDomains] = useState('');
  const [inviteRoles, setInviteRoles] = useState('');

  const rows = initial.items;
  const hostRows = rows.filter((r) => r.myRole === 'host');
  const partnerRows = rows.filter((r) => r.myRole === 'partner');

  function open(action: PendingAction) {
    setPendingAction(action);
    setError(null);
  }
  function close() {
    if (pending) return;
    setPendingAction(null);
    setError(null);
  }

  async function confirm(reason: string) {
    if (!pendingAction) return;
    setPending(true);
    setError(null);
    try {
      await runMutation(pendingAction, reason);
      setPendingAction(null);
      router.refresh();
    } catch (e) {
      setError(
        e instanceof ApiError
          ? messageForCode(e.code, e.message)
          : '요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.',
      );
    } finally {
      setPending(false);
    }
  }

  function submitInvite(e: React.FormEvent) {
    e.preventDefault();
    const partnerTenantId = invitePartner.trim();
    if (!partnerTenantId) return;
    open({
      kind: 'invite',
      partnerTenantId,
      delegatedScope: {
        domains: parseList(inviteDomains),
        roles: parseList(inviteRoles),
      },
      label: partnerTenantId,
      destructive: false,
    });
  }

  const meta = pendingAction ? actionMeta(pendingAction) : null;
  const destructive = pendingAction?.destructive ?? false;

  return (
    <section aria-labelledby="partnerships-heading">
      <h1 id="partnerships-heading" className="mb-2 text-2xl font-semibold">
        파트너십
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        조직 <span className="font-medium text-foreground">{activeTenant}</span>{' '}
        의 cross-org 파트너십을 관리합니다. 파트너십은 관계 상태만 다루며, 파생
        도메인 권한은 assume-tenant 발급 시 위임 범위로 캡됩니다 (파트너십 관리
        권한: TENANT_ADMIN).
      </p>

      {/* ── host-side (내가 발행) ─────────────────────────────────────── */}
      <div className="mb-10">
        <h2 className="mb-3 text-lg font-semibold text-foreground">
          우리 조직이 발행한 파트너십 (host)
        </h2>

        <PartnershipInviteForm
          invitePartner={invitePartner}
          setInvitePartner={setInvitePartner}
          inviteDomains={inviteDomains}
          setInviteDomains={setInviteDomains}
          inviteRoles={inviteRoles}
          setInviteRoles={setInviteRoles}
          onSubmit={submitInvite}
        />

        {hostRows.length === 0 ? (
          <p className="rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground">
            우리 조직이 발행한 파트너십이 없습니다.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {hostRows.map((row) => (
              <PartnershipRow
                key={row.partnershipId}
                row={row}
                otherLabel={row.partnerTenantId}
                onOpen={open}
              />
            ))}
          </ul>
        )}
      </div>

      {/* ── partner-side (나에게 위임됨) ──────────────────────────────── */}
      <div>
        <h2 className="mb-3 text-lg font-semibold text-foreground">
          우리 조직에 위임된 파트너십 (partner)
        </h2>
        {partnerRows.length === 0 ? (
          <p className="rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground">
            우리 조직에 위임된 파트너십이 없습니다.
          </p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {partnerRows.map((row) => (
              <PartnershipRow
                key={row.partnershipId}
                row={row}
                otherLabel={row.hostTenantId}
                onOpen={open}
              />
            ))}
          </ul>
        )}
      </div>

      {pendingAction && meta && (
        <PartnershipConfirmDialog
          title={meta.title}
          description={meta.description}
          confirmLabel={meta.confirmLabel}
          warning={
            destructive
              ? '이 작업은 파생 접근을 즉시 소멸시킵니다.'
              : undefined
          }
          destructive={destructive}
          pending={pending}
          error={error}
          onConfirm={confirm}
          onCancel={close}
        />
      )}
    </section>
  );
}
