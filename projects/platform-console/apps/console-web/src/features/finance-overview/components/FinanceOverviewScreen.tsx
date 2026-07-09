import Link from 'next/link';
import { Card } from '@/shared/ui/Card';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { labelForUnknown } from '@/shared/lib/tolerant-label';
import {
  KNOWN_ACCOUNT_STATUSES,
  accountStatusTone,
  balanceMoney,
  formatMoney,
} from '@/features/finance-ops/api/types';
import type { FinanceOverviewState } from '../api/overview-state';

/**
 * Finance domain overview screen (TASK-PC-FE-229) — the `/finance`
 * landing. Server component (no client interactivity needed — a pure
 * read-only snapshot render, mirrors `OperatorOverviewScreen` /
 * `ScmOverview` presentational patterns).
 *
 * Renders TWO independently-degrading sections (§ overview-state.ts):
 *   - the ledger aggregate tiles (trial-balance `inBalance` / OPEN periods
 *     / OPEN discrepancies / FX feed freshness);
 *   - the operator's own default-account snapshot (status + per-currency
 *     balances via `formatMoney` — F5, NEVER `Number()`/`parseFloat()`/
 *     `parseInt()`).
 *
 * A `ledgerDegraded` never blanks the account snapshot and vice versa —
 * each section renders its OWN degrade/missing/not-found placeholder.
 */

export interface FinanceOverviewScreenProps {
  state: FinanceOverviewState;
  /** A `?accountId=` carried over from a pre-TASK-PC-FE-229 bookmark of the
   *  former `/finance` account-lookup destination (Edge Cases — the query
   *  is otherwise ignored on the overview; when present, a direct link to
   *  its new home `/finance/accounts?accountId=…` is surfaced so the
   *  bookmark isn't a hard break). `null` when absent (the common case). */
  legacyAccountId?: string | null;
}

function LedgerTiles({ state }: { state: FinanceOverviewState }) {
  if (state.ledgerDegraded) {
    return (
      <div
        role="status"
        data-testid="finance-overview-ledger-degraded"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        원장 집계 정보를 일시적으로 불러올 수 없습니다. 계좌 스냅샷은 계속
        표시됩니다. 잠시 후 다시 시도하세요.
      </div>
    );
  }

  const ledger = state.ledger;
  if (!ledger) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="finance-overview-ledger-empty"
      >
        원장 집계 정보가 없습니다.
      </p>
    );
  }

  return (
    <div
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
      data-testid="finance-overview-ledger-tiles"
    >
      <Card>
        <dl>
          <dt className="text-sm text-muted-foreground">시산표 균형</dt>
          <dd
            className="text-2xl font-semibold text-foreground"
            data-testid="finance-overview-ledger-inbalance"
          >
            <StatusBadge tone={ledger.inBalance ? 'success' : 'danger'}>
              {ledger.inBalance ? '균형' : '불균형'}
            </StatusBadge>
          </dd>
        </dl>
      </Card>
      <Card>
        <dl>
          <dt className="text-sm text-muted-foreground">미마감 기간 수</dt>
          <dd
            className="text-2xl font-semibold tabular-nums text-foreground"
            data-testid="finance-overview-ledger-periods"
          >
            {ledger.openPeriodsCount.toLocaleString()}
          </dd>
          <p
            className="mt-1 text-xs text-muted-foreground"
            data-testid="finance-overview-ledger-periods-caption"
          >
            최근 {ledger.periodsSampleSize}건 기준
          </p>
        </dl>
      </Card>
      <Card>
        <dl>
          <dt className="text-sm text-muted-foreground">미해소 대사 차이</dt>
          <dd
            className="text-2xl font-semibold tabular-nums text-foreground"
            data-testid="finance-overview-ledger-discrepancies"
          >
            {ledger.openDiscrepanciesCount.toLocaleString()}
          </dd>
        </dl>
      </Card>
      <Card>
        <dl>
          <dt className="text-sm text-muted-foreground">FX 피드 신선도</dt>
          <dd
            className="text-base font-medium text-foreground"
            data-testid="finance-overview-ledger-fx"
          >
            {ledger.fxFeedEnabled ? (
              <>
                {ledger.fxRatesCount.toLocaleString()}건 중{' '}
                {ledger.fxStaleCount.toLocaleString()}건 오래됨
              </>
            ) : (
              '피드 비활성'
            )}
          </dd>
          {ledger.fxLatestAsOf ? (
            <p
              className="mt-1 text-xs text-muted-foreground"
              data-testid="finance-overview-ledger-fx-asof"
            >
              최신 기준: {ledger.fxLatestAsOf}
            </p>
          ) : null}
        </dl>
      </Card>
    </div>
  );
}

function AccountSnapshotCard({ state }: { state: FinanceOverviewState }) {
  if (state.defaultAccountMissing) {
    return (
      <div
        role="status"
        data-testid="finance-overview-account-missing"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        <p className="mb-2 font-medium text-foreground">
          기본 finance 계좌 미설정
        </p>
        <p>
          운영자 프로필에서 기본 finance 계좌를 설정하면 여기에 스냅샷이
          표시됩니다.
        </p>
        <Link
          href="/account"
          className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          계정 설정으로 이동
        </Link>
      </div>
    );
  }

  if (state.accountDegraded) {
    return (
      <div
        role="status"
        data-testid="finance-overview-account-degraded"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        기본계좌 스냅샷을 일시적으로 불러올 수 없습니다. 원장 집계는 계속
        표시됩니다. 잠시 후 다시 시도하세요.
      </div>
    );
  }

  if (state.accountNotFound) {
    return (
      <div
        role="status"
        data-testid="finance-overview-account-not-found"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        계좌를 찾을 수 없습니다. 운영자 프로필의 기본 finance 계좌 설정을
        확인하세요.
      </div>
    );
  }

  const snapshot = state.accountSnapshot;
  if (!snapshot) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="finance-overview-account-empty"
      >
        기본계좌 스냅샷이 없습니다.
      </p>
    );
  }

  const label = labelForUnknown(
    snapshot.account.status,
    KNOWN_ACCOUNT_STATUSES,
  );

  return (
    <div data-testid="finance-overview-account-snapshot">
      <dl className="mb-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">accountId</dt>
          <dd
            className="text-foreground"
            data-testid="finance-overview-account-id"
          >
            {snapshot.account.accountId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={accountStatusTone(snapshot.account.status)}
              data-testid="finance-overview-account-status"
            >
              {label}
            </StatusBadge>
          </dd>
        </div>
      </dl>
      {snapshot.balances.data.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="finance-overview-balances-empty"
        >
          잔액이 없습니다.
        </p>
      ) : (
        <ul
          className="space-y-1"
          data-testid="finance-overview-balances"
        >
          {snapshot.balances.data.map((b, i) => {
            // F5 — pure string passing into `formatMoney`. NO `Number()`
            // coercion of `amount` anywhere on this line.
            const m = balanceMoney(b);
            return (
              <li
                key={`${b.currency}-${i}`}
                data-testid={`finance-overview-balance-${i}`}
                className="text-sm text-foreground"
              >
                {b.currency}: {formatMoney(m.available)} (가용)
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export function FinanceOverviewScreen({
  state,
  legacyAccountId,
}: FinanceOverviewScreenProps) {
  return (
    <section aria-labelledby="finance-overview-heading" data-testid="finance-overview">
      <h1 id="finance-overview-heading" className="mb-2 text-2xl font-semibold">
        Finance 개요
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        원장 집계와 운영자 본인의 기본계좌 스냅샷을 한 번에 확인합니다. 계좌
        목록 집계는 제공하지 않습니다(finance v1 에 계좌 list/search GET
        없음) — 계좌 조회는 아래 계좌 화면에서 accountId 로 직접 하세요.
      </p>

      {legacyAccountId ? (
        <div
          role="status"
          data-testid="finance-overview-legacy-account-hint"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          계좌 조회는{' '}
          <Link
            href={`/finance/accounts?accountId=${encodeURIComponent(legacyAccountId)}`}
            data-testid="finance-overview-legacy-account-link"
            className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            계좌 화면
          </Link>
          으로 이동했습니다.
        </div>
      ) : null}

      <nav
        aria-label="Finance 화면 바로가기"
        className="mb-8 flex flex-wrap gap-4 text-sm"
      >
        <Link
          href="/finance/guide"
          data-testid="finance-overview-link-guide"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          가이드
        </Link>
        <Link
          href="/finance/accounts"
          data-testid="finance-overview-link-accounts"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          계좌
        </Link>
        <Link
          href="/ledger"
          data-testid="finance-overview-link-ledger"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          원장
        </Link>
      </nav>

      <h2 className="mb-3 text-lg font-medium text-foreground">원장 집계</h2>
      <div className="mb-8">
        <LedgerTiles state={state} />
      </div>

      <h2 className="mb-3 text-lg font-medium text-foreground">
        기본계좌 스냅샷
      </h2>
      <AccountSnapshotCard state={state} />
    </section>
  );
}
