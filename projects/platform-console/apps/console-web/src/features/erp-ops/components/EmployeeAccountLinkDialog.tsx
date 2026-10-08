'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { CloseButton } from '@/shared/ui/CloseButton';
import { formatDateTime } from '@/shared/lib/datetime';
import type { Employee, EmployeeAccountLinkProposal } from '../api/types';
import {
  useAccountLinkProposalAction,
  useEmployeeAccountLinkProposals,
  useOperatorEmailLookup,
  useProposeAccountLink,
  useUnlinkEmployeeAccount,
} from '../hooks/use-erp-ops';
import { newIdemKey } from '../hooks/use-approval-detail';
import {
  ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE,
  accountLinkErrorMessage,
  accountLookupErrorMessage,
} from './account-link-error';
import { AccountLinkBadge } from './AccountLinkBadge';

/**
 * 직원 ↔ IAM 계정 연결 관리 창 (TASK-PC-FE-318 — `TASK-MONO-774` S4, 인사 쪽).
 *
 * The HR side of the owner decision «인사 제안 + 본인 수락»: an `erp.write`
 * holder PROPOSES «this employee ↔ that account», sees the pending proposal,
 * may REVOKE it, and may UNLINK an existing link. The account owner accepts
 * elsewhere (`MyAccountLinkProposalsCard` on `/erp`) — this dialog never
 * offers accept/decline (the producer would refuse an HR user anyway: 403
 * `EMPLOYEE_LINK_NOT_ADDRESSEE` / `EMPLOYEE_LINK_SELF_ACCEPT`).
 *
 * Account selection (TASK-MONO-777 — console contract § 2.4.8): «이메일로 찾기»
 * over the IAM operator e-mail lookup fills the account-id field with the
 * chosen operator's console `sub`; the typed account id (form-checked only,
 * 1~64) stays as the fallback — the producer does not check IAM existence at
 * proposal time, and the dialog SAYS so. «Not found» and «out of scope» share
 * ONE sentence (existence non-disclosure); a failed lookup (403/401/503) is
 * inline only and never logs the user out.
 *
 * The producer is the authority for every write (E6 fail-CLOSED): no button is
 * hidden by the console; a 403 / 409 / 422 renders inline with its own copy.
 * Sample visitors see the same buttons; the server refuses with
 * `403 SAMPLE_READ_ONLY`.
 */
export const PROPOSAL_STATUS_LABEL: Record<string, string> = {
  PENDING: '대기',
  ACCEPTED: '수락됨',
  DECLINED: '거절됨',
  REVOKED: '철회됨',
};

export function proposalStatusLabel(s: string): string {
  return PROPOSAL_STATUS_LABEL[s] ?? s;
}

export function EmployeeAccountLinkDialog({
  employee,
  onClose,
}: {
  employee: Employee;
  onClose: () => void;
}) {
  const historyQ = useEmployeeAccountLinkProposals(employee.id);
  const propose = useProposeAccountLink();
  const revoke = useAccountLinkProposalAction('revoke');
  const unlink = useUnlinkEmployeeAccount();

  const [accountId, setAccountId] = useState('');
  const [lookupEmail, setLookupEmail] = useState('');
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);
  const lookup = useOperatorEmailLookup(submittedEmail);
  const [proposeReason, setProposeReason] = useState('');
  const [revokeReason, setRevokeReason] = useState('');
  const [unlinkReason, setUnlinkReason] = useState('');
  // The producer's answer after a successful write (the list cell refreshes
  // through the `employees` invalidation; this keeps the dialog honest
  // without waiting for that refetch).
  const [linkedOverride, setLinkedOverride] = useState<string | null | undefined>(
    undefined,
  );

  const proposals: EmployeeAccountLinkProposal[] = historyQ.data?.data ?? [];
  const pending = proposals.find((p) => p.status === 'PENDING') ?? null;
  const decided = proposals.filter((p) => p.status !== 'PENDING');
  const currentAccountId =
    linkedOverride !== undefined ? linkedOverride : (employee.accountId ?? null);
  const linked = Boolean(currentAccountId);
  const pendingAny = propose.isPending || revoke.isPending || unlink.isPending;

  const trimmedAccount = accountId.trim();
  const canPropose =
    !pendingAny && trimmedAccount.length > 0 && trimmedAccount.length <= 64;

  function onPropose() {
    if (!canPropose) return;
    propose.mutate(
      {
        employeeId: employee.id,
        accountId: trimmedAccount,
        reason: proposeReason.trim() || undefined,
        idempotencyKey: newIdemKey(),
      },
      {
        onSuccess: () => {
          setAccountId('');
          setProposeReason('');
        },
      },
    );
  }

  function onLookup() {
    const email = lookupEmail.trim();
    if (!email) return;
    setSubmittedEmail(email);
  }

  const lookupDone = submittedEmail !== null && !lookup.isFetching;
  const lookupHits = lookupDone && lookup.isSuccess ? lookup.data.content : [];
  const lookupMessage: string | null = !lookupDone
    ? null
    : lookup.isError
      ? accountLookupErrorMessage(lookup.error)
      : lookupHits.length === 0
        ? ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE
        : null;

  function onRevoke() {
    if (!pending || !revokeReason.trim() || pendingAny) return;
    revoke.mutate(
      {
        proposalId: pending.id,
        reason: revokeReason.trim(),
        idempotencyKey: newIdemKey(),
      },
      { onSuccess: () => setRevokeReason('') },
    );
  }

  function onUnlink() {
    if (!unlinkReason.trim() || pendingAny) return;
    unlink.mutate(
      {
        employeeId: employee.id,
        reason: unlinkReason.trim(),
        idempotencyKey: newIdemKey(),
      },
      {
        onSuccess: () => {
          setUnlinkReason('');
          setLinkedOverride(null);
        },
      },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="erp-account-link-overlay"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="계정 연결"
        data-testid="erp-account-link-dialog"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-foreground">
          계정 연결 — {employee.employeeNumber} · {employee.name}
        </h2>
        <p className="mt-1 text-xs text-muted-foreground">
          인사 담당자가 제안하고, 그 계정의 주인이 로그인해 수락해야 연결됩니다
          (제안한 사람은 수락할 수 없습니다).
        </p>

        <dl className="mt-4 grid grid-cols-[7rem_1fr] gap-y-1 text-sm">
          <dt className="text-muted-foreground">현재 연결</dt>
          <dd>
            <AccountLinkBadge
              accountId={currentAccountId}
              testId="erp-account-link-current"
            />
            {linked && (
              <code
                className="ml-2 break-all text-xs text-muted-foreground"
                data-testid="erp-account-link-current-id"
              >
                {currentAccountId}
              </code>
            )}
          </dd>
        </dl>

        {historyQ.isError && (
          <p
            className="mt-3 text-sm text-destructive"
            role="status"
            data-testid="erp-account-link-history-error"
          >
            {accountLinkErrorMessage(historyQ.error, 'read')}
          </p>
        )}

        {/* pending proposal → revoke */}
        {pending && (
          <section className="mt-4" data-testid="erp-account-link-pending">
            <h3 className="text-sm font-semibold text-foreground">대기 중인 제안</h3>
            <p className="mt-1 text-sm">
              계정 ID{' '}
              <code className="break-all text-xs" data-testid="erp-account-link-pending-account">
                {pending.accountId}
              </code>{' '}
              · {formatDateTime(pending.proposedAt, '—')} 제안
            </p>
            <label
              htmlFor="erp-account-link-revoke-reason"
              className="mt-2 block text-sm font-medium text-foreground"
            >
              철회 사유 <span aria-hidden="true">*</span>
            </label>
            <input
              id="erp-account-link-revoke-reason"
              data-testid="erp-account-link-revoke-reason"
              value={revokeReason}
              onChange={(e) => setRevokeReason(e.target.value)}
              maxLength={256}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
            <div className="mt-2 flex justify-end">
              <Button
                variant="secondary"
                onClick={onRevoke}
                disabled={pendingAny || !revokeReason.trim()}
                data-testid="erp-account-link-revoke"
              >
                제안 철회
              </Button>
            </div>
            {revoke.error ? (
              <p className="mt-2 text-sm text-destructive" role="status" data-testid="erp-account-link-revoke-error">
                {accountLinkErrorMessage(revoke.error, 'revoke')}
              </p>
            ) : null}
          </section>
        )}

        {/* linked → unlink */}
        {linked && (
          <section className="mt-4" data-testid="erp-account-link-unlink-section">
            <h3 className="text-sm font-semibold text-foreground">연결 해제</h3>
            <label
              htmlFor="erp-account-link-unlink-reason"
              className="mt-2 block text-sm font-medium text-foreground"
            >
              해제 사유 <span aria-hidden="true">*</span>
            </label>
            <input
              id="erp-account-link-unlink-reason"
              data-testid="erp-account-link-unlink-reason"
              value={unlinkReason}
              onChange={(e) => setUnlinkReason(e.target.value)}
              maxLength={256}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
            <div className="mt-2 flex justify-end">
              <Button
                variant="secondary"
                className="text-destructive"
                onClick={onUnlink}
                disabled={pendingAny || !unlinkReason.trim()}
                data-testid="erp-account-link-unlink"
              >
                연결 해제
              </Button>
            </div>
            {unlink.error ? (
              <p className="mt-2 text-sm text-destructive" role="status" data-testid="erp-account-link-unlink-error">
                {accountLinkErrorMessage(unlink.error, 'unlink')}
              </p>
            ) : null}
          </section>
        )}

        {/* not linked, nothing pending → propose */}
        {!linked && !pending && (
          <section className="mt-4" data-testid="erp-account-link-propose-section">
            <h3 className="text-sm font-semibold text-foreground">연결 제안</h3>
            <label
              htmlFor="erp-account-link-lookup-email"
              className="mt-2 block text-sm font-medium text-foreground"
            >
              이메일로 찾기
            </label>
            <div className="mt-1 flex gap-2">
              <input
                id="erp-account-link-lookup-email"
                data-testid="erp-account-link-lookup-email"
                type="email"
                value={lookupEmail}
                onChange={(e) => setLookupEmail(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    onLookup();
                  }
                }}
                maxLength={254}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              />
              <Button
                variant="secondary"
                onClick={onLookup}
                disabled={!lookupEmail.trim() || lookup.isFetching}
                data-testid="erp-account-link-lookup-submit"
              >
                {lookup.isFetching ? '찾는 중…' : '찾기'}
              </Button>
            </div>
            {lookupMessage !== null ? (
              // ONE element for every «no result» outcome — «not found» and «out
              // of scope» must not differ even in markup (AC-2).
              <p
                className={`mt-1 text-sm ${
                  lookupMessage === ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE
                    ? 'text-muted-foreground'
                    : 'text-destructive'
                }`}
                role="status"
                data-testid="erp-account-link-lookup-message"
              >
                {lookupMessage}
              </p>
            ) : null}
            {lookupDone && lookupHits.length > 0 ? (
              <ul className="mt-1 space-y-1" data-testid="erp-account-link-lookup-results">
                {lookupHits.map((hit, idx) => (
                  <li key={`${hit.accountId}-${hit.tenantId}`}>
                    <button
                      type="button"
                      onClick={() => setAccountId(hit.accountId)}
                      aria-pressed={accountId.trim() === hit.accountId}
                      data-testid={`erp-account-link-lookup-result-${idx}`}
                      className="w-full rounded-md border border-border px-3 py-1 text-left text-sm hover:bg-muted aria-pressed:border-primary"
                    >
                      {hit.displayName || '이름 없음'}{' '}
                      <span className="text-xs text-muted-foreground">
                        · {hit.tenantId === '*' ? '플랫폼' : hit.tenantId} 테넌트
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
            <label
              htmlFor="erp-account-link-account-id"
              className="mt-2 block text-sm font-medium text-foreground"
            >
              계정 ID <span aria-hidden="true">*</span>
            </label>
            <input
              id="erp-account-link-account-id"
              data-testid="erp-account-link-account-id"
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              maxLength={64}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground"
              aria-describedby="erp-account-link-account-id-help"
            />
            <p
              id="erp-account-link-account-id-help"
              className="mt-1 text-xs text-muted-foreground"
            >
              위에서 찾은 계정을 고르면 채워집니다. 직접 입력해도 됩니다. 계정이 실제로
              있는지는 제안할 때 확인하지 않으며, 계정 주인이 로그인해 수락해야 연결됩니다.
            </p>
            <label
              htmlFor="erp-account-link-propose-reason"
              className="mt-2 block text-sm font-medium text-foreground"
            >
              사유 (선택)
            </label>
            <input
              id="erp-account-link-propose-reason"
              data-testid="erp-account-link-propose-reason"
              value={proposeReason}
              onChange={(e) => setProposeReason(e.target.value)}
              maxLength={256}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
            <div className="mt-2 flex justify-end">
              <Button
                variant="primary"
                onClick={onPropose}
                disabled={!canPropose}
                data-testid="erp-account-link-propose"
              >
                {propose.isPending ? '처리 중…' : '제안'}
              </Button>
            </div>
            {propose.error ? (
              <p className="mt-2 text-sm text-destructive" role="status" data-testid="erp-account-link-propose-error">
                {accountLinkErrorMessage(propose.error, 'propose')}
              </p>
            ) : null}
            {propose.isSuccess && !propose.error ? (
              <p className="mt-2 text-sm text-foreground" role="status" data-testid="erp-account-link-propose-done">
                제안했습니다. 계정 주인이 수락하면 연결됩니다.
              </p>
            ) : null}
          </section>
        )}

        {decided.length > 0 && (
          <section className="mt-4">
            <h3 className="text-sm font-semibold text-foreground">지난 제안</h3>
            <ol className="mt-1 space-y-1 text-xs text-muted-foreground" data-testid="erp-account-link-history">
              {decided.map((p) => (
                <li key={p.id} data-testid={`erp-account-link-history-${p.id}`}>
                  {proposalStatusLabel(p.status)} · 계정 ID{' '}
                  <code className="break-all">{p.accountId}</code> ·{' '}
                  {formatDateTime(p.decidedAt ?? p.proposedAt, '—')}
                </li>
              ))}
            </ol>
          </section>
        )}

        <div className="mt-6 flex justify-end">
          <CloseButton
            onClick={onClose}
            disabled={pendingAny}
            data-testid="erp-account-link-close"
          />
        </div>
      </div>
    </div>
  );
}
