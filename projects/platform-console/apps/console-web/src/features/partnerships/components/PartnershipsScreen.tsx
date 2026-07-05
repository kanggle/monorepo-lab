'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { apiClient } from '@/shared/api/client';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { Partnership, PartnershipList, ScopeSet } from '../api/types';
import { PartnershipConfirmDialog } from './PartnershipConfirmDialog';

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

type ActionKind =
  | 'invite'
  | 'accept'
  | 'suspend'
  | 'reactivate'
  | 'terminate'
  | 'participant-add'
  | 'participant-remove';

interface PendingAction {
  kind: ActionKind;
  partnershipId?: string;
  operatorId?: string;
  partnerTenantId?: string;
  delegatedScope?: ScopeSet;
  participantScope?: ScopeSet | null;
  /** Human label for the dialog title (the other-side tenant, or operator id). */
  label: string;
  destructive: boolean;
}

function parseList(raw: string): string[] {
  return raw
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
}

/** Build a ScopeSet from comma inputs, or `null` when both are empty (⟺ full
 *  delegatedScope net-zero default for a participant). */
function scopeOrNull(domains: string, roles: string): ScopeSet | null {
  const d = parseList(domains);
  const r = parseList(roles);
  if (d.length === 0 && r.length === 0) return null;
  return { domains: d, roles: r };
}

async function runMutation(a: PendingAction, reason: string): Promise<void> {
  const base = a.partnershipId
    ? `/api/partnerships/${encodeURIComponent(a.partnershipId)}`
    : '/api/partnerships';
  switch (a.kind) {
    case 'invite':
      await apiClient.post('/api/partnerships', {
        partnerTenantId: a.partnerTenantId,
        delegatedScope: a.delegatedScope,
        reason,
      });
      return;
    case 'accept':
      await apiClient.post(`${base}/accept`, { reason });
      return;
    case 'suspend':
      await apiClient.post(`${base}/suspend`, { reason });
      return;
    case 'reactivate':
      await apiClient.post(`${base}/reactivate`, { reason });
      return;
    case 'terminate':
      await apiClient.post(`${base}/terminate`, { reason });
      return;
    case 'participant-add':
      await apiClient.post(
        `${base}/participants/${encodeURIComponent(a.operatorId!)}`,
        a.participantScope
          ? { participantScope: a.participantScope, reason }
          : { reason },
      );
      return;
    case 'participant-remove':
      // DELETE carries the audit reason in the body (the proxy DELETE handler
      // reads it); apiClient.delete forwards `opts.body`.
      await apiClient.delete(
        `${base}/participants/${encodeURIComponent(a.operatorId!)}`,
        { body: { reason } },
      );
      return;
  }
}

interface ActionMeta {
  title: string;
  description: string;
  confirmLabel: string;
}

function actionMeta(a: PendingAction): ActionMeta {
  switch (a.kind) {
    case 'invite':
      return {
        title: `${a.label} 파트너십 초대`,
        description: `파트너 조직(${a.label})에 위임 범위를 부여하는 파트너십을 생성합니다. 상대 조직이 수락하면 활성화됩니다.`,
        confirmLabel: '초대',
      };
    case 'accept':
      return {
        title: `${a.label} 파트너십 수락`,
        description: `${a.label} 조직이 보낸 파트너십 초대를 수락하고 활성화합니다.`,
        confirmLabel: '수락',
      };
    case 'suspend':
      return {
        title: `${a.label} 파트너십 일시중지`,
        description: `${a.label} 파트너십을 일시중지합니다. 재개하기 전까지 참여자의 파생 접근이 즉시 소멸합니다 (D6).`,
        confirmLabel: '일시중지',
      };
    case 'reactivate':
      return {
        title: `${a.label} 파트너십 재개`,
        description: `일시중지된 ${a.label} 파트너십을 다시 활성화합니다.`,
        confirmLabel: '재개',
      };
    case 'terminate':
      return {
        title: `${a.label} 파트너십 종료`,
        description: `${a.label} 파트너십을 종료합니다. 종료는 되돌릴 수 없으며 모든 참여자의 파생 접근이 소멸합니다 (D6).`,
        confirmLabel: '종료',
      };
    case 'participant-add':
      return {
        title: `참여자 배정 (${a.operatorId})`,
        description: `운영자 ${a.operatorId} 를 이 파트너십의 참여자로 배정합니다. 범위를 지정하지 않으면 위임 범위 전체가 적용됩니다.`,
        confirmLabel: '배정',
      };
    case 'participant-remove':
      return {
        title: `참여자 해제 (${a.operatorId})`,
        description: `운영자 ${a.operatorId} 의 참여자 배정을 해제합니다. 해당 운영자의 파생 접근이 즉시 소멸합니다.`,
        confirmLabel: '해제',
      };
  }
}

function ScopeSummary({ scope }: { scope: ScopeSet }) {
  return (
    <span className="text-xs text-muted-foreground">
      도메인: {scope.domains.length ? scope.domains.join(', ') : '—'} · 역할:{' '}
      {scope.roles.length ? scope.roles.join(', ') : '—'}
    </span>
  );
}

const STATUS_LABEL: Record<Partnership['status'], string> = {
  PENDING: '대기 중',
  ACTIVE: '활성',
  SUSPENDED: '일시중지',
  TERMINATED: '종료됨',
};

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

        <form
          onSubmit={submitInvite}
          data-testid="partnership-invite-form"
          className="mb-4 rounded-md border border-border bg-muted/40 p-4"
        >
          <p className="mb-3 text-sm font-medium text-foreground">
            파트너 조직 초대
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">파트너 조직 ID</span>
              <input
                type="text"
                value={invitePartner}
                onChange={(e) => setInvitePartner(e.target.value)}
                data-testid="partnership-invite-partner"
                placeholder="globex-corp"
                className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">위임 도메인 (쉼표)</span>
              <input
                type="text"
                value={inviteDomains}
                onChange={(e) => setInviteDomains(e.target.value)}
                data-testid="partnership-invite-domains"
                placeholder="wms, scm"
                className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted-foreground">위임 역할 (쉼표)</span>
              <input
                type="text"
                value={inviteRoles}
                onChange={(e) => setInviteRoles(e.target.value)}
                data-testid="partnership-invite-roles"
                placeholder="WMS_OUTBOUND_OPERATOR"
                className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
          </div>
          <div className="mt-3 flex justify-end">
            <button
              type="submit"
              disabled={invitePartner.trim() === ''}
              data-testid="partnership-invite-submit"
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              초대
            </button>
          </div>
        </form>

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

// ---------------------------------------------------------------------------
// Row + per-row action gating.
// ---------------------------------------------------------------------------

function PartnershipRow({
  row,
  otherLabel,
  onOpen,
}: {
  row: Partnership;
  otherLabel: string;
  onOpen: (a: PendingAction) => void;
}) {
  const { partnershipId, status, myRole, delegatedScope, participantCount } =
    row;

  const canAccept = status === 'PENDING' && myRole === 'partner';
  const canTerminate = status !== 'TERMINATED';
  const canSuspend = status === 'ACTIVE';
  const canReactivate = status === 'SUSPENDED';
  const canManageParticipants = status === 'ACTIVE' && myRole === 'partner';

  return (
    <li
      data-testid={`partnership-row-${partnershipId}`}
      className="flex flex-col gap-3 px-4 py-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-foreground">
            {myRole === 'host' ? '파트너' : 'host'}: {otherLabel}
          </p>
          <p
            data-testid={`partnership-status-${partnershipId}`}
            className={
              status === 'ACTIVE'
                ? 'mt-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400'
                : 'mt-0.5 text-xs text-muted-foreground'
            }
          >
            {STATUS_LABEL[status]} · 참여자 {participantCount}명
          </p>
          <div className="mt-1">
            <ScopeSummary scope={delegatedScope} />
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
          {canAccept && (
            <button
              type="button"
              data-testid={`partnership-accept-${partnershipId}`}
              onClick={() =>
                onOpen({
                  kind: 'accept',
                  partnershipId,
                  label: otherLabel,
                  destructive: false,
                })
              }
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            >
              수락
            </button>
          )}
          {canSuspend && (
            <button
              type="button"
              data-testid={`partnership-suspend-${partnershipId}`}
              onClick={() =>
                onOpen({
                  kind: 'suspend',
                  partnershipId,
                  label: otherLabel,
                  destructive: false,
                })
              }
              className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              일시중지
            </button>
          )}
          {canReactivate && (
            <button
              type="button"
              data-testid={`partnership-reactivate-${partnershipId}`}
              onClick={() =>
                onOpen({
                  kind: 'reactivate',
                  partnershipId,
                  label: otherLabel,
                  destructive: false,
                })
              }
              className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              재개
            </button>
          )}
          {canTerminate && (
            <button
              type="button"
              data-testid={`partnership-terminate-${partnershipId}`}
              onClick={() =>
                onOpen({
                  kind: 'terminate',
                  partnershipId,
                  label: otherLabel,
                  destructive: true,
                })
              }
              className="rounded-md border border-destructive/40 px-3 py-1.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10"
            >
              {myRole === 'host' && status === 'PENDING' ? '철회' : '종료'}
            </button>
          )}
        </div>
      </div>

      {canManageParticipants && (
        <ParticipantManager partnershipId={partnershipId} onOpen={onOpen} />
      )}
    </li>
  );
}

// ---------------------------------------------------------------------------
// Participant add/remove panel (ACTIVE partner-side rows only). The list gives
// only participantCount (not the operator ids), so the operator id is a manual
// input for both add and remove — the producer is authoritative on ownership
// (422 PARTICIPANT_NOT_OWN_OPERATOR) and existence (404 PARTICIPANT_NOT_FOUND).
// ---------------------------------------------------------------------------

function ParticipantManager({
  partnershipId,
  onOpen,
}: {
  partnershipId: string;
  onOpen: (a: PendingAction) => void;
}) {
  const [operatorId, setOperatorId] = useState('');
  const [domains, setDomains] = useState('');
  const [roles, setRoles] = useState('');

  const opTrimmed = operatorId.trim();

  return (
    <div className="rounded-md border border-dashed border-border bg-muted/30 p-3">
      <p className="mb-2 text-xs font-medium text-foreground">참여자 관리</p>
      <div className="grid gap-2 sm:grid-cols-3">
        <input
          type="text"
          value={operatorId}
          onChange={(e) => setOperatorId(e.target.value)}
          data-testid={`partnership-participant-operator-${partnershipId}`}
          placeholder="운영자 ID"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <input
          type="text"
          value={domains}
          onChange={(e) => setDomains(e.target.value)}
          data-testid={`partnership-participant-domains-${partnershipId}`}
          placeholder="범위 도메인 (선택, 쉼표)"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <input
          type="text"
          value={roles}
          onChange={(e) => setRoles(e.target.value)}
          data-testid={`partnership-participant-roles-${partnershipId}`}
          placeholder="범위 역할 (선택, 쉼표)"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
      </div>
      <div className="mt-2 flex justify-end gap-2">
        <button
          type="button"
          disabled={opTrimmed === ''}
          data-testid={`partnership-participant-add-${partnershipId}`}
          onClick={() =>
            onOpen({
              kind: 'participant-add',
              partnershipId,
              operatorId: opTrimmed,
              participantScope: scopeOrNull(domains, roles),
              label: opTrimmed,
              destructive: false,
            })
          }
          className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          배정
        </button>
        <button
          type="button"
          disabled={opTrimmed === ''}
          data-testid={`partnership-participant-remove-${partnershipId}`}
          onClick={() =>
            onOpen({
              kind: 'participant-remove',
              partnershipId,
              operatorId: opTrimmed,
              label: opTrimmed,
              destructive: true,
            })
          }
          className="rounded-md border border-destructive/40 px-3 py-1.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10 disabled:cursor-not-allowed disabled:opacity-50"
        >
          해제
        </button>
      </div>
    </div>
  );
}
