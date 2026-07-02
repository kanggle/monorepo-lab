import type { Account } from '../api/types';
import { KNOWN_ACCOUNT_STATUSES, accountStatusTone } from '../api/types';
import { StatusBadge } from '@/shared/ui/StatusBadge';

/**
 * Account detail (TASK-PC-FE-009 — § 2.4.7).
 *
 * STRICTLY READ-ONLY — no mutation affordance. Renders status, KYC
 * level, currency, accountId honestly:
 *   - producer-known account statuses (PENDING_KYC / ACTIVE /
 *     RESTRICTED / FROZEN / CLOSED) are surfaced as-is — a
 *     FROZEN / RESTRICTED / CLOSED account is shown as such, NEVER
 *     hidden or de-emphasised (§ 2.4.7 honest regulated-state
 *     surfacing).
 *   - an unknown / future status degrades to a generic label (no
 *     parser throw, no crash — tolerant-parser discipline).
 *
 * Confidential / F7 (§ 2.4.7): nothing about this account is logged
 * (the API layer logs only status + sanitised path); the UI renders
 * the values into the DOM and no further.
 */
export interface AccountDetailProps {
  account: Account;
}

export function AccountDetail({ account }: AccountDetailProps) {
  const known = (KNOWN_ACCOUNT_STATUSES as readonly string[]).includes(
    account.status,
  );
  const label = known ? account.status : `${account.status} (unknown)`;

  return (
    <section
      aria-labelledby="finance-account-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="finance-account-detail"
    >
      <h2
        id="finance-account-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        계정 정보
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">accountId</dt>
          <dd
            className="text-foreground"
            data-testid="finance-account-id"
          >
            {account.accountId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={accountStatusTone(account.status)}
              data-testid="finance-account-status"
            >
              {label}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">통화</dt>
          <dd
            className="text-foreground"
            data-testid="finance-account-currency"
          >
            {account.currency}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">KYC 레벨</dt>
          <dd
            className="text-foreground"
            data-testid="finance-account-kyc"
          >
            {account.kycLevel ?? '—'}
          </dd>
        </div>
      </dl>
    </section>
  );
}
