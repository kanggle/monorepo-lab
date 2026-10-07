/**
 * «연결된 계정» cell for an employee (TASK-PC-FE-318 — `TASK-MONO-774` S4).
 *
 * `accountId` present → «연결됨»; ABSENT → «연결된 계정 없음». 🔴 The raw
 * account UUID is NOT printed as a reference label (`TASK-PC-FE-309`
 * discipline — a bare UUID tells the reader nothing): it rides in `title`
 * (hover) and `data-account-id` only. The console cannot resolve an account
 * id to an e-mail/name without an IAM permission an erp HR user need not hold
 * (AC-0 ①), so «연결됨» is the honest label.
 */
export const ACCOUNT_LINKED_LABEL = '연결됨';
export const ACCOUNT_UNLINKED_LABEL = '연결된 계정 없음';

export function AccountLinkBadge({
  accountId,
  testId,
}: {
  accountId?: string | null;
  testId?: string;
}) {
  const linked = typeof accountId === 'string' && accountId.trim() !== '';
  return (
    <span
      data-testid={testId}
      data-linked={linked ? 'true' : 'false'}
      data-account-id={linked ? accountId : undefined}
      title={linked ? `계정 ID: ${accountId}` : undefined}
      className={linked ? 'text-foreground' : 'text-muted-foreground'}
    >
      {linked ? ACCOUNT_LINKED_LABEL : ACCOUNT_UNLINKED_LABEL}
    </span>
  );
}
