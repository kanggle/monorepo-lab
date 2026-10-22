import type { OperatorsSummary, AccountsSummary } from '../api/overview-state';
import { BarSwatch, StackedBar } from '@/shared/ui/InlineBar';
import { CardShell, BigCount, Placeholder } from './IamOverviewPrimitives';

/**
 * IAM overview count/summary cards — operators (ACTIVE/SUSPENDED split) and
 * accounts (LOCKED sub-count) (TASK-PC-FE-180 — extracted from
 * {@link IamOverviewScreen}, TASK-PC-FE-212 presentational split). Server
 * components — STRICTLY READ-ONLY, no `'use client'`. Each card is a
 * quick-launch `Link` to its IAM screen; a non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks). All
 * markup / testids are byte-verbatim from the former god-file.
 *
 * TASK-PC-FE-362 adds ratio bars drawn from the numbers the cards already
 * print (full-population status-filtered `totalElements`, ZERO new reads).
 * Any missing number or an empty population → no bar (numbers stay). The
 * whole is the 총계: a remainder leaves the track empty, an overshoot scales
 * the bar to the sum (`StackedBar`'s `expected`).
 */

function isCount(n: number | null): n is number {
  return typeof n === 'number' && Number.isFinite(n);
}

function OperatorsRatioBar({ summary }: { summary: OperatorsSummary }) {
  const { total, active, suspended } = summary;
  if (!isCount(total) || total <= 0 || !isCount(active) || !isCount(suspended)) {
    return null;
  }
  return (
    <div className="my-2">
      <StackedBar
        expected={total}
        testid="iam-overview-operators-bar"
        segments={[
          { key: 'ACTIVE', value: active, tone: 'success' },
          { key: 'SUSPENDED', value: suspended, tone: 'warning' },
        ]}
      />
    </div>
  );
}

function AccountsRatioBar({ summary }: { summary: AccountsSummary }) {
  const { total, locked } = summary;
  if (!isCount(total) || total <= 0 || !isCount(locked)) return null;
  return (
    <div className="my-2">
      <StackedBar
        expected={total}
        testid="iam-overview-accounts-bar"
        segments={[{ key: 'LOCKED', value: locked, tone: 'danger' }]}
      />
    </div>
  );
}

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
          <OperatorsRatioBar summary={summary} />
          <dl className="flex gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <dt>
                <BarSwatch tone="success" />
                활성
              </dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-operators-active"
              >
                {summary.active !== null ? summary.active.toLocaleString() : '—'}
              </dd>
            </div>
            <div className="flex items-center gap-1">
              <dt>
                <BarSwatch tone="warning" />
                정지
              </dt>
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
          <AccountsRatioBar summary={summary} />
          <dl className="flex gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <dt>
                <BarSwatch tone="danger" />
                잠금
              </dt>
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
