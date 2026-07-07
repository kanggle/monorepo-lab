import type { OperatorsSummary, AccountsSummary } from '../api/overview-state';
import { CardShell, BigCount, Placeholder } from './IamOverviewPrimitives';

/**
 * IAM overview count/summary cards — operators (ACTIVE/SUSPENDED split) and
 * accounts (LOCKED sub-count) (TASK-PC-FE-180 — extracted from
 * {@link IamOverviewScreen}, TASK-PC-FE-212 presentational split). Server
 * components — STRICTLY READ-ONLY, no `'use client'`. Each card is a
 * quick-launch `Link` to its IAM screen; a non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks). All
 * markup / testids are byte-verbatim from the former god-file.
 */

export function OperatorsCard({ summary }: { summary: OperatorsSummary }) {
  const ok = summary.status === 'ok' && summary.total !== null;
  return (
    <CardShell
      href="/operators"
      testid="iam-overview-operators"
      label="운영자"
      status={summary.status}
    >
      {ok ? (
        <>
          <BigCount value={summary.total!} testid="iam-overview-operators-total" />
          <dl className="flex gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <dt>활성</dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-operators-active"
              >
                {summary.active !== null ? summary.active.toLocaleString() : '—'}
              </dd>
            </div>
            <div className="flex items-center gap-1">
              <dt>정지</dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-operators-suspended"
              >
                {summary.suspended !== null
                  ? summary.suspended.toLocaleString()
                  : '—'}
              </dd>
            </div>
          </dl>
        </>
      ) : (
        <Placeholder
          status={summary.status}
          testid="iam-overview-operators-degraded"
        />
      )}
    </CardShell>
  );
}

export function AccountsCard({ summary }: { summary: AccountsSummary }) {
  const ok = summary.status === 'ok' && summary.total !== null;
  return (
    <CardShell
      href="/accounts"
      testid="iam-overview-accounts"
      label="계정"
      status={summary.status}
    >
      {ok ? (
        <>
          <BigCount value={summary.total!} testid="iam-overview-accounts-total" />
          <dl className="flex gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <dt>잠금</dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-accounts-locked"
              >
                {summary.locked !== null ? summary.locked.toLocaleString() : '—'}
              </dd>
            </div>
          </dl>
        </>
      ) : (
        <Placeholder status={summary.status} testid="iam-overview-accounts-degraded" />
      )}
    </CardShell>
  );
}
