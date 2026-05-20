import type { Account } from '../api/types';
import { KNOWN_ACCOUNT_STATUSES } from '../api/types';

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

function statusVariant(status: string): 'normal' | 'warn' | 'danger' {
  if (status === 'ACTIVE') return 'normal';
  if (status === 'FROZEN' || status === 'CLOSED' || status === 'RESTRICTED') {
    return 'danger';
  }
  if (status === 'PENDING_KYC') return 'warn';
  return 'normal';
}

const STATUS_CLASS: Record<'normal' | 'warn' | 'danger', string> = {
  normal: 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground',
  warn: 'rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/60 dark:text-amber-100',
  danger:
    'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive',
};

export function AccountDetail({ account }: AccountDetailProps) {
  const known = (KNOWN_ACCOUNT_STATUSES as readonly string[]).includes(
    account.status,
  );
  const label = known ? account.status : `${account.status} (unknown)`;
  const variant = statusVariant(account.status);

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
            <span
              className={STATUS_CLASS[variant]}
              data-testid="finance-account-status"
            >
              {label}
            </span>
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
