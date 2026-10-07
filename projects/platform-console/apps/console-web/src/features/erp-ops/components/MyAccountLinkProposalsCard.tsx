'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import type { EmployeeAccountLinkProposal } from '../api/types';
import {
  useAccountLinkProposalAction,
  useMyAccountLinkProposals,
} from '../hooks/use-erp-ops';
import { newIdemKey } from '../hooks/use-approval-detail';
import { accountLinkErrorMessage } from './account-link-error';

/**
 * «내 계정 앞으로 온 직원 연결 제안» 카드 (TASK-PC-FE-318 — `TASK-MONO-774`
 * S4, 계정 주인 쪽). Placement = the `/erp` overview page (AC-0 ②): the
 * acceptor is the ACCOUNT OWNER — `erp.read` or more, no department data scope
 * (`masterdata-api.md` accept) — not an HR user, and `/erp/masters` blanks as a
 * whole when any one master read is out of the caller's scope. This card
 * degrades on its own: a failure here never touches the overview tiles.
 *
 * accept / decline are the owner's writes (NOT `erp.write`). Each refusal has
 * its own copy (`EMPLOYEE_LINK_SELF_ACCEPT` «제안한 사람이 수락할 수 없습니다»
 * vs `EMPLOYEE_LINK_NOT_ADDRESSEE`). Buttons are never hidden for a sample
 * visitor — the server refuses with `403 SAMPLE_READ_ONLY`.
 */
export function MyAccountLinkProposalsCard() {
  const q = useMyAccountLinkProposals();
  const rows: EmployeeAccountLinkProposal[] = q.data?.data ?? [];

  return (
    <section
      aria-labelledby="erp-my-link-proposals-heading"
      className="mt-8 rounded-md border border-border bg-background p-4"
      data-testid="erp-my-link-proposals"
    >
      <h2
        id="erp-my-link-proposals-heading"
        className="mb-1 text-lg font-medium text-foreground"
      >
        내 계정 앞으로 온 직원 연결 제안
      </h2>
      <p className="mb-3 text-xs text-muted-foreground">
        인사 담당자가 내 계정을 직원과 연결하자고 제안하면 여기에 보입니다. 수락하면
        그 직원으로 결재함·알림을 받습니다.
      </p>

      {q.isLoading && (
        <p className="text-sm text-muted-foreground" data-testid="erp-my-link-proposals-loading">
          불러오는 중…
        </p>
      )}
      {q.isError && (
        <p
          className="text-sm text-destructive"
          role="status"
          data-testid="erp-my-link-proposals-error"
        >
          {accountLinkErrorMessage(q.error, 'read')}
        </p>
      )}
      {!q.isLoading && !q.isError && rows.length === 0 && (
        <p className="text-sm text-muted-foreground" data-testid="erp-my-link-proposals-empty">
          나에게 온 직원 연결 제안이 없습니다.
        </p>
      )}
      {rows.length > 0 && (
        <ul className="space-y-2" data-testid="erp-my-link-proposals-list">
          {rows.map((p) => (
            <ProposalRow key={p.id} proposal={p} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ProposalRow({ proposal }: { proposal: EmployeeAccountLinkProposal }) {
  const accept = useAccountLinkProposalAction('accept');
  const decline = useAccountLinkProposalAction('decline');
  const [lastOp, setLastOp] = useState<'accept' | 'decline' | null>(null);
  const pending = accept.isPending || decline.isPending;
  const err = lastOp === 'accept' ? accept.error : lastOp === 'decline' ? decline.error : null;

  const who =
    proposal.employeeNumber || proposal.employeeName
      ? [proposal.employeeNumber, proposal.employeeName].filter(Boolean).join(' · ')
      : '직원 정보 없음';

  return (
    <li
      className="rounded border border-border px-3 py-2 text-sm"
      data-testid={`erp-my-link-proposal-${proposal.id}`}
    >
      <p className="text-foreground">
        <span className="font-medium">{who}</span> 직원과 내 계정을 연결하자는 제안
      </p>
      <p className="text-xs text-muted-foreground">
        {formatDateTime(proposal.proposedAt, '—')} 제안
        {proposal.reason ? ` · 사유: ${proposal.reason}` : ''}
      </p>
      <div className="mt-2 flex gap-2">
        <Button
          variant="primary"
          size="sm"
          disabled={pending}
          onClick={() => {
            setLastOp('accept');
            accept.mutate({ proposalId: proposal.id, idempotencyKey: newIdemKey() });
          }}
          data-testid={`erp-my-link-proposal-accept-${proposal.id}`}
        >
          수락
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={pending}
          onClick={() => {
            setLastOp('decline');
            decline.mutate({ proposalId: proposal.id, idempotencyKey: newIdemKey() });
          }}
          data-testid={`erp-my-link-proposal-decline-${proposal.id}`}
        >
          거절
        </Button>
      </div>
      {err ? (
        <p
          className="mt-2 text-sm text-destructive"
          role="status"
          data-testid={`erp-my-link-proposal-error-${proposal.id}`}
        >
          {accountLinkErrorMessage(err, lastOp ?? 'accept')}
        </p>
      ) : null}
    </li>
  );
}
