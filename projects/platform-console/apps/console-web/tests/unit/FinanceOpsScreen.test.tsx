import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { FinanceOpsScreen } from '@/features/finance-ops';
import type {
  Account,
  BalancesResponse,
  TransactionsResponse,
} from '@/features/finance-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/finance-ops` component behaviour (TASK-PC-FE-009 —
 * STRICTLY READ-ONLY account / balances / transactions surface):
 *   - AccountDetail honestly surfaces FROZEN / RESTRICTED / CLOSED;
 *   - BalancesTable renders per-currency `ledger` / `available` /
 *     `held` scale-correct from the minor-units **string** via
 *     `formatMoney` (NO `Number()` coercion of `amount`);
 *   - TransactionsTable surfaces FAILED / REVERSED rows + the
 *     reversalOfTransactionId column honestly;
 *   - unknown / future enum values render with a generic label
 *     (no parser throw);
 *   - no mutation affordance anywhere (no confirm dialog, no submit /
 *     reason / idempotency / finance write);
 *   - WCAG AA axe-clean.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const FROZEN_ACCOUNT: Account = {
  accountId: 'acct-frozen',
  status: 'FROZEN',
  currency: 'KRW',
  kycLevel: 'FULL',
};
const CLOSED_ACCOUNT: Account = {
  accountId: 'acct-closed',
  status: 'CLOSED',
  currency: 'KRW',
  kycLevel: 'BASIC',
};
const RESTRICTED_ACCOUNT: Account = {
  accountId: 'acct-restricted',
  status: 'RESTRICTED',
  currency: 'USD',
  kycLevel: 'BASIC',
};
const UNKNOWN_STATUS_ACCOUNT: Account = {
  accountId: 'acct-future',
  status: 'FUTURE_LIMBO_STATE',
  currency: 'KRW',
  kycLevel: 'NONE',
};

const KRW_BALANCES: BalancesResponse = {
  data: [
    {
      currency: 'KRW',
      // F5 — a large minor-units string; the table renders it
      // scale-correct from the STRING (no Number coercion).
      ledger: '1234567890123',
      available: '1234567000000',
      held: '890123',
    },
  ],
  meta: { timestamp: '2026-05-20T00:00:00Z' },
};

const TXNS_WITH_FAILED_AND_REVERSED: TransactionsResponse = {
  data: [
    {
      transactionId: 'txn-ok',
      type: 'HOLD',
      status: 'ACTIVE',
      money: { amount: '150000', currency: 'KRW' },
    },
    {
      transactionId: 'txn-fail',
      type: 'TRANSFER',
      status: 'FAILED',
      money: { amount: '50000', currency: 'KRW' },
      counterpartyAccountId: 'acct-other',
    },
    {
      transactionId: 'txn-rev',
      type: 'REVERSAL',
      status: 'REVERSED',
      money: { amount: '50000', currency: 'KRW' },
      counterpartyAccountId: 'acct-other',
      reversalOfTransactionId: 'txn-fail',
    },
    {
      transactionId: 'txn-future',
      // tolerant: unknown enum → generic label, no throw.
      type: 'FUTURE_TXN_TYPE',
      status: 'FUTURE_TXN_STATUS',
      money: { amount: '1', currency: 'KRW' },
    },
  ],
  meta: { page: 0, size: 20, totalElements: 4 },
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('FinanceOpsScreen — honest regulated-state surfacing (§ 2.4.7)', () => {
  it('FROZEN account is rendered as FROZEN (honest — never hidden)', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-frozen"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('finance-account-status')).toHaveTextContent(
      'FROZEN',
    );
  });

  it('RESTRICTED account is rendered as RESTRICTED', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-restricted"
        initialAccount={RESTRICTED_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('finance-account-status')).toHaveTextContent(
      'RESTRICTED',
    );
  });

  it('CLOSED account is rendered as CLOSED', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-closed"
        initialAccount={CLOSED_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('finance-account-status')).toHaveTextContent(
      'CLOSED',
    );
  });

  it('unknown / future account status renders with a generic label (no throw)', () => {
    expect(() =>
      render(
        <FinanceOpsScreen
          initialAccountId="acct-future"
          initialAccount={UNKNOWN_STATUS_ACCOUNT}
          initialBalances={KRW_BALANCES}
          initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
        />,
        { wrapper: wrapper() },
      ),
    ).not.toThrow();
    const label = screen.getByTestId('finance-account-status').textContent;
    expect(label).toContain('FUTURE_LIMBO_STATE');
    expect(label).toContain('unknown');
  });

  it('FAILED and REVERSED txns render in the table (surfaced honestly, never hidden); REVERSED row carries its reversalOf', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    const table = screen.getByTestId('finance-txns-table');
    // 'txn-fail' appears in row 1 (as the txn id) AND in row 2's
    // reversal column (the REVERSED row pointing at it) — both are
    // intentional, so assert at least one (the surfaced row).
    expect(within(table).getAllByText('txn-fail').length).toBeGreaterThan(0);
    expect(within(table).getByText('txn-rev')).toBeInTheDocument();
    // The FAILED + REVERSED labels are surfaced honestly.
    expect(
      within(table).getByTestId('finance-txn-status-1').textContent,
    ).toContain('FAILED');
    expect(
      within(table).getByTestId('finance-txn-status-2').textContent,
    ).toContain('REVERSED');
    // The reversalOfTransactionId column carries the original txn id.
    expect(
      within(table).getByTestId('finance-txn-reversal-2').textContent,
    ).toContain('txn-fail');
  });

  it('unknown / future txn type and status render with a generic label (no throw)', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('finance-txn-type-3').textContent,
    ).toContain('FUTURE_TXN_TYPE');
    expect(
      screen.getByTestId('finance-txn-type-3').textContent,
    ).toContain('unknown');
    expect(
      screen.getByTestId('finance-txn-status-3').textContent,
    ).toContain('FUTURE_TXN_STATUS');
  });
});

describe('FinanceOpsScreen — F5 money rendering (string-exact, scale-correct; NO Number coercion)', () => {
  it('renders KRW (scale 0) from the minor-units string bit-exact', () => {
    render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    // KRW scale 0: the digit body is rendered as-is — a Number-coerced
    // path would lose precision on '1234567890123'. The rendered
    // string must CONTAIN the exact digit body.
    const ledgerCell = screen.getByTestId('finance-balance-ledger-0');
    expect(ledgerCell.textContent).toContain('1234567890123');
    expect(ledgerCell.textContent).toContain('KRW');
    const availableCell = screen.getByTestId(
      'finance-balance-available-0',
    );
    expect(availableCell.textContent).toContain('1234567000000');
    const heldCell = screen.getByTestId('finance-balance-held-0');
    expect(heldCell.textContent).toContain('890123');
  });

  it('renders USD (scale 2) with a decimal point inserted from the string', () => {
    const usdBalances: BalancesResponse = {
      data: [
        {
          currency: 'USD',
          ledger: '1000', // = $10.00
          available: '5', // = $0.05
          held: '0',
        },
      ],
      meta: { timestamp: 'x' },
    };
    render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={RESTRICTED_ACCOUNT}
        initialBalances={usdBalances}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('finance-balance-ledger-0').textContent,
    ).toContain('10.00');
    expect(
      screen.getByTestId('finance-balance-available-0').textContent,
    ).toContain('0.05');
  });
});

describe('FinanceOpsScreen — read-only (NO mutation affordance anywhere)', () => {
  it('renders no submit / confirm / cancel / reason capture / idempotency UI', () => {
    const { container } = render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    // No reason input.
    expect(
      container.querySelector('[data-testid*="reason"]'),
    ).toBeNull();
    // No idempotency / confirm / mutate affordance.
    expect(
      container.querySelector('[data-testid*="confirm"]'),
    ).toBeNull();
    expect(
      container.querySelector('[data-testid*="idempotency"]'),
    ).toBeNull();
    // No HOLD / TRANSFER / KYC mutation buttons (no finance write UI).
    expect(
      container.querySelector('[data-testid*="hold-submit"]'),
    ).toBeNull();
    expect(
      container.querySelector('[data-testid*="transfer-submit"]'),
    ).toBeNull();
    expect(
      container.querySelector('[data-testid*="kyc-upgrade"]'),
    ).toBeNull();
  });

  it('console.log spy: no balance / txn / token value reaches the console (confidential / F7 reinforced)', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});
    render(
      <FinanceOpsScreen
        initialAccountId="acct-9b1d4a8c"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    // No minor-units balance string in any console call.
    expect(all).not.toContain('1234567890123');
    // No txn-fail / txn-rev / counterparty ref.
    expect(all).not.toContain('txn-fail');
    expect(all).not.toContain('txn-rev');
    expect(all).not.toContain('acct-other');
    // No accountId.
    expect(all).not.toContain('acct-9b1d4a8c');
  });
});

describe('FinanceOpsScreen — WCAG AA (axe-clean)', () => {
  it('a fully populated section has zero axe violations', async () => {
    const { container } = render(
      <FinanceOpsScreen
        initialAccountId="acct-1"
        initialAccount={FROZEN_ACCOUNT}
        initialBalances={KRW_BALANCES}
        initialTransactions={TXNS_WITH_FAILED_AND_REVERSED}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });

  it('an empty (no-accountId) section is axe-clean', async () => {
    const { container } = render(
      <FinanceOpsScreen
        initialAccountId={null}
        initialAccount={null}
        initialBalances={null}
        initialTransactions={null}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
