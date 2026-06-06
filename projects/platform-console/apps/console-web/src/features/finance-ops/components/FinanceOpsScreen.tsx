'use client';

import { useState } from 'react';
import type {
  Account,
  BalancesResponse,
  TransactionsResponse,
} from '../api/types';
import { AccountLookup } from './AccountLookup';
import { AccountDetail } from './AccountDetail';
import { BalancesTable } from './BalancesTable';
import { TransactionsTable } from './TransactionsTable';
import {
  useFinanceAccount,
  useFinanceBalances,
} from '../hooks/use-finance-ops';
import { ApiError, messageForCode } from '@/shared/api/errors';

/**
 * finance operations section (TASK-PC-FE-009 — ADR-MONO-013 Phase 5, the
 * THIRD non-IAM federated domain; closes the non-IAM federation cycle).
 *
 * STRICTLY READ-ONLY. The section renders:
 *   - AccountLookup (accountId entry — honest finance constraint: v1
 *     has NO account list/search GET);
 *   - AccountDetail (status — honest regulated-state surfacing
 *     including FROZEN/RESTRICTED/CLOSED; KYC; currency);
 *   - BalancesTable (per-currency ledger / available / held rendered
 *     scale-correct via `formatMoney` from the minor-units **string**
 *     — F5);
 *   - TransactionsTable (paginated; type + status filters; FAILED /
 *     REVERSED rows surfaced honestly; counterparty + reversal columns).
 *
 * Initial seed: server-side via `getFinanceSectionState(eligible,
 * accountId)`; subsequent re-queries (a different accountId / filter /
 * page change) go through the same-origin `/api/finance/**` proxy via
 * the client hooks.
 *
 * F5 (§ 2.4.7, NORMATIVE): every money render goes through
 * `formatMoney(...)` (string-based scale-correct rendering). NO
 * `Number()` / `parseFloat()` / `parseInt()` is applied to any `amount`
 * value anywhere in `features/finance-ops/` (a test grep-asserts this
 * against the on-disk source).
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login — not surfaced here); 403 / 404 → inline actionable;
 * 503 / timeout → this section degrades only (the console shell + the
 * IAM / wms / scm sections stay intact). **No 429 handling** (§ 2.4.7
 * — finance has no documented 429; a stray 429 is rendered as a
 * generic error, NOT retried).
 */

export interface FinanceOpsScreenProps {
  initialAccountId: string | null;
  initialAccount: Account | null;
  initialBalances: BalancesResponse | null;
  initialTransactions: TransactionsResponse | null;
}

export function FinanceOpsScreen({
  initialAccountId,
  initialAccount,
  initialBalances,
  initialTransactions,
}: FinanceOpsScreenProps) {
  const [accountId, setAccountId] = useState<string | null>(initialAccountId);

  // Hooks: when the operator changes the accountId, fetch the new
  // account + balances client-side via the proxy. The transactions
  // table owns its own filter/page state and hook subscription.
  const accountQ = useFinanceAccount(accountId);
  const balancesQ = useFinanceBalances(accountId, initialBalances ?? undefined);

  const account: Account | null =
    accountQ.data ?? (accountId === initialAccountId ? initialAccount : null);
  const balances: BalancesResponse | null =
    balancesQ.data ?? (accountId === initialAccountId ? initialBalances : null);

  const apiErr =
    (accountQ.error instanceof ApiError && accountQ.error) ||
    (balancesQ.error instanceof ApiError && balancesQ.error) ||
    null;
  const notFound = apiErr?.status === 404;
  const forbidden = apiErr?.status === 403;
  const degraded =
    !apiErr && (accountQ.isError || balancesQ.isError) && Boolean(accountId);

  return (
    <section aria-labelledby="finance-heading">
      <h1 id="finance-heading" className="mb-2 text-2xl font-semibold">
        Finance 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        계정 · 잔액 · 거래 조회 (읽기 전용). finance 운영 표면을 콘솔
        안에서 조회합니다. 자금 이동(생성/홀드/캡처/이체) 작업은 콘솔
        범위가 아닙니다.
      </p>

      <AccountLookup
        initialAccountId={accountId ?? undefined}
        onSubmit={setAccountId}
      />

      {!accountId ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="finance-no-account"
        >
          조회할 accountId 를 입력하세요.
        </p>
      ) : forbidden ? (
        <div
          role="status"
          data-testid="finance-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : notFound ? (
        <div
          role="status"
          data-testid="finance-not-found"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('ACCOUNT_NOT_FOUND')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="finance-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          finance 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : account && balances ? (
        <>
          <AccountDetail account={account} />
          <h2 className="mb-3 text-lg font-medium text-foreground">잔액</h2>
          <BalancesTable balances={balances.data} />
          {initialTransactions && initialAccountId === accountId ? (
            <TransactionsTable
              accountId={accountId}
              initial={initialTransactions}
            />
          ) : (
            <TransactionsTable
              accountId={accountId}
              initial={{
                data: [],
                meta: { page: 0, size: 20, totalElements: 0 },
              }}
            />
          )}
        </>
      ) : (
        <p
          className="text-sm text-muted-foreground"
          data-testid="finance-loading"
        >
          불러오는 중…
        </p>
      )}
    </section>
  );
}
