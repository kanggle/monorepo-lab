'use client';

import { useState } from 'react';
import { scopeOrNull, type PendingAction } from './partnerships-actions';

// ---------------------------------------------------------------------------
// Participant add/remove panel (ACTIVE partner-side rows only). The list gives
// only participantCount (not the operator ids), so the operator id is a manual
// input for both add and remove — the producer is authoritative on ownership
// (422 PARTICIPANT_NOT_OWN_OPERATOR) and existence (404 PARTICIPANT_NOT_FOUND).
// ---------------------------------------------------------------------------

export function ParticipantManager({
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
