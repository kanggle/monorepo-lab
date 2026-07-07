'use client';

import type { Partnership, ScopeSet } from '../api/types';
import { STATUS_LABEL, type PendingAction } from './partnerships-actions';
import { ParticipantManager } from './ParticipantManager';

function ScopeSummary({ scope }: { scope: ScopeSet }) {
  return (
    <span className="text-xs text-muted-foreground">
      도메인: {scope.domains.length ? scope.domains.join(', ') : '—'} · 역할:{' '}
      {scope.roles.length ? scope.roles.join(', ') : '—'}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Row + per-row action gating.
// ---------------------------------------------------------------------------

export function PartnershipRow({
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
