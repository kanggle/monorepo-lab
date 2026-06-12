import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { LedgerOpsScreen } from '@/features/ledger-ops';
import { TrialBalanceTable } from '@/features/ledger-ops/components/TrialBalanceTable';
import { PeriodDetail } from '@/features/ledger-ops/components/PeriodDetail';
import { JournalEntryDetail } from '@/features/ledger-ops/components/JournalEntryDetail';
import { DiscrepancyQueue } from '@/features/ledger-ops/components/DiscrepancyQueue';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  JournalEntry,
  Period,
} from '@/features/ledger-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/ledger-ops` component behaviour (TASK-PC-FE-072 — STRICTLY
 * READ-ONLY finance ledger surface):
 *   - TrialBalanceTable renders per-account + grand base totals scale-
 *     correct from the minor-units **string** via `formatMoney` (NO
 *     `Number()` coercion) + an honest `inBalance` badge;
 *   - PeriodDetail renders the close snapshot for a CLOSED period, and a
 *     "snapshot 없음 (open)" notice for an OPEN one (snapshot null is NOT
 *     an error);
 *   - JournalEntryDetail surfaces sourceType + multi-currency lines
 *     (money + exchangeRate verbatim + base) + a revaluation 0-line
 *     highlight; an unknown sourceType → generic label;
 *   - DiscrepancyQueue has a status filter and renders the AMOUNT_MISMATCH
 *     matched pair (BOTH externalRef + journalEntryId);
 *   - no mutation affordance anywhere; WCAG AA axe-clean.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

const TRIAL_BALANCE: TrialBalance = {
  accounts: [
    {
      ledgerAccountCode: '1000',
      debitTotal: M('1234567890123'), // large minor-units string — F5
      creditTotal: M('0'),
      baseDebitTotal: M('1234567890123'),
      baseCreditTotal: M('0'),
    },
  ],
  grandDebitTotal: M('1234567890123'),
  grandCreditTotal: M('1234567890123'),
  grandBaseDebitTotal: M('1234567890123'),
  grandBaseCreditTotal: M('1234567890123'),
  inBalance: true,
};

const PERIODS: PeriodsResponse = {
  data: [
    { periodId: '2026-05', status: 'OPEN', from: 'a', to: 'b', entryCount: 3 },
    { periodId: '2026-04', status: 'CLOSED', from: 'c', to: 'd', entryCount: 9 },
  ],
  meta: { page: 0, size: 20, totalElements: 2 },
};

const DISCREPANCIES: DiscrepanciesResponse = {
  data: [
    {
      // The 11th-increment FX-difference AMOUNT_MISMATCH — the matched
      // pair carries BOTH externalRef AND journalEntryId.
      discrepancyId: 'd-1',
      type: 'AMOUNT_MISMATCH',
      externalRef: 'bank-ref-1',
      journalEntryId: 'je-9',
      expectedMinor: '100',
      actualMinor: '105',
      currency: 'KRW',
      status: 'OPEN',
    },
    {
      discrepancyId: 'd-2',
      type: 'FUTURE_DISCREPANCY_TYPE', // unknown → generic label
      externalRef: null,
      journalEntryId: null,
      expectedMinor: '0',
      actualMinor: '50',
      currency: 'KRW',
      status: 'OPEN',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 2 },
};

const MULTI_CURRENCY_ENTRY: JournalEntry = {
  entryId: 'je-multi',
  postedAt: '2026-05-19T10:00:00Z',
  source: { sourceType: 'TRANSACTION' },
  lines: [
    {
      ledgerAccountCode: '1000',
      direction: 'DEBIT',
      money: M('13500', 'USD'),
      exchangeRate: '13.5', // verbatim decimal string
      baseAmount: M('182250'),
    },
    {
      // A 9th-increment revaluation line — money.amount '0' (foreign) with
      // a non-zero KRW base.
      ledgerAccountCode: '7800',
      direction: 'CREDIT',
      money: M('0', 'USD'),
      exchangeRate: '1',
      baseAmount: M('5000'),
    },
  ],
  balanced: true,
};

const UNKNOWN_SOURCE_ENTRY: JournalEntry = {
  entryId: 'je-unknown',
  source: { sourceType: 'FUTURE_SOURCE_TYPE' },
  lines: [
    {
      ledgerAccountCode: '1000',
      direction: 'DEBIT',
      money: M('100'),
      exchangeRate: '1',
      baseAmount: M('100'),
    },
  ],
  balanced: false,
};

const CLOSED_PERIOD: Period = {
  periodId: '2026-04',
  status: 'CLOSED',
  from: 'c',
  to: 'd',
  snapshot: {
    accounts: [
      { ledgerAccountCode: '1000', debitTotal: M('500'), creditTotal: M('0') },
    ],
    grandDebitTotal: M('500'),
    grandCreditTotal: M('500'),
    inBalance: true,
  },
};

const OPEN_PERIOD: Period = {
  periodId: '2026-05',
  status: 'OPEN',
  from: 'a',
  to: 'b',
  snapshot: null,
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ---------------------------------------------------------------------------
// TrialBalanceTable
// ---------------------------------------------------------------------------

describe('TrialBalanceTable — F5 money + honest inBalance', () => {
  it('renders KRW totals bit-exact from the minor-units string + an in-balance badge', () => {
    render(<TrialBalanceTable trialBalance={TRIAL_BALANCE} />);
    const debit = screen.getByTestId('ledger-tb-debit-0');
    expect(debit.textContent).toContain('1234567890123');
    expect(debit.textContent).toContain('KRW');
    expect(screen.getByTestId('ledger-tb-grand-base-debit').textContent).toContain(
      '1234567890123',
    );
    expect(screen.getByTestId('ledger-tb-inbalance').textContent).toContain(
      'in balance',
    );
  });

  it('an OUT-of-balance trial balance is surfaced honestly (danger badge)', () => {
    render(
      <TrialBalanceTable
        trialBalance={{ ...TRIAL_BALANCE, inBalance: false }}
      />,
    );
    expect(screen.getByTestId('ledger-tb-inbalance').textContent).toContain(
      'out of balance',
    );
  });
});

// ---------------------------------------------------------------------------
// PeriodDetail — snapshot (CLOSED) vs open
// ---------------------------------------------------------------------------

describe('PeriodDetail — close snapshot (CLOSED) vs open (no snapshot is NOT an error)', () => {
  it('a CLOSED period renders its close snapshot (accounts + grand totals + inBalance)', () => {
    render(<PeriodDetail periodId="2026-04" initial={CLOSED_PERIOD} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('ledger-period-snapshot')).toBeInTheDocument();
    expect(
      screen.getByTestId('ledger-snapshot-grand-debit').textContent,
    ).toContain('500');
    expect(screen.getByTestId('ledger-snapshot-inbalance').textContent).toContain(
      'in balance',
    );
  });

  it('an OPEN period shows "snapshot 없음 (open)" — NOT an error / crash', () => {
    expect(() =>
      render(<PeriodDetail periodId="2026-05" initial={OPEN_PERIOD} />, {
        wrapper: wrapper(),
      }),
    ).not.toThrow();
    expect(screen.getByTestId('ledger-period-no-snapshot')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-period-snapshot')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// JournalEntryDetail — sourceType + multi-currency lines + revaluation 0-line
// ---------------------------------------------------------------------------

describe('JournalEntryDetail — sourceType + multi-currency F5 lines + revaluation highlight', () => {
  it('renders the multi-currency line triple (money + verbatim exchangeRate + base) + the revaluation 0-line highlight', () => {
    render(<JournalEntryDetail entry={MULTI_CURRENCY_ENTRY} />);
    expect(screen.getByTestId('ledger-entry-source').textContent).toContain(
      'TRANSACTION',
    );
    // Line 0 — USD money rendered scale-correct (scale 2), exchangeRate
    // verbatim, base KRW rendered.
    expect(screen.getByTestId('ledger-line-money-0').textContent).toContain(
      '135.00',
    );
    expect(screen.getByTestId('ledger-line-rate-0').textContent).toBe('13.5');
    expect(screen.getByTestId('ledger-line-base-0').textContent).toContain(
      '182250',
    );
    // Line 1 — the revaluation 0-line (money.amount '0', non-zero base) is
    // highlighted.
    expect(screen.getByTestId('ledger-line-reval-1')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-line-money-1').textContent).toContain(
      '0.00',
    );
  });

  it('an unknown / future sourceType renders with a generic label (no throw); unbalanced surfaced honestly', () => {
    render(<JournalEntryDetail entry={UNKNOWN_SOURCE_ENTRY} />);
    const label = screen.getByTestId('ledger-entry-source').textContent;
    expect(label).toContain('FUTURE_SOURCE_TYPE');
    expect(label).toContain('unknown');
    expect(screen.getByTestId('ledger-entry-balanced').textContent).toContain(
      'unbalanced',
    );
  });
});

// ---------------------------------------------------------------------------
// DiscrepancyQueue — status filter + AMOUNT_MISMATCH matched pair
// ---------------------------------------------------------------------------

describe('DiscrepancyQueue — status filter + AMOUNT_MISMATCH matched pair (both refs)', () => {
  it('the AMOUNT_MISMATCH row shows BOTH externalRef and journalEntryId (the matched pair)', () => {
    render(
      <DiscrepancyQueue
        initial={DISCREPANCIES}
        selectedDiscrepancyId={null}
        onSelect={() => {}}
      />,
      { wrapper: wrapper() },
    );
    const table = screen.getByTestId('ledger-recon-table');
    expect(within(table).getByTestId('ledger-recon-type-0').textContent).toContain(
      'AMOUNT_MISMATCH',
    );
    expect(
      within(table).getByTestId('ledger-recon-extref-0').textContent,
    ).toContain('bank-ref-1');
    expect(
      within(table).getByTestId('ledger-recon-journal-0').textContent,
    ).toContain('je-9');
    // expected / actual via discrepancyMoney + formatMoney (string).
    expect(
      within(table).getByTestId('ledger-recon-expected-0').textContent,
    ).toContain('100');
    expect(
      within(table).getByTestId('ledger-recon-actual-0').textContent,
    ).toContain('105');
  });

  it('an unknown discrepancy type renders with a generic label (no throw)', () => {
    render(
      <DiscrepancyQueue
        initial={DISCREPANCIES}
        selectedDiscrepancyId={null}
        onSelect={() => {}}
      />,
      { wrapper: wrapper() },
    );
    const t = screen.getByTestId('ledger-recon-type-1').textContent;
    expect(t).toContain('FUTURE_DISCREPANCY_TYPE');
    expect(t).toContain('unknown');
  });

  it('exposes a status filter (OPEN / RESOLVED / all)', () => {
    render(
      <DiscrepancyQueue
        initial={DISCREPANCIES}
        selectedDiscrepancyId={null}
        onSelect={() => {}}
      />,
      { wrapper: wrapper() },
    );
    const filter = screen.getByTestId('ledger-recon-filter-status');
    expect(filter).toBeInTheDocument();
    const opts = within(filter).getAllByRole('option').map((o) => o.textContent);
    expect(opts).toContain('OPEN');
    expect(opts).toContain('RESOLVED');
    expect(opts).toContain('전체');
  });
});

// ---------------------------------------------------------------------------
// LedgerOpsScreen — shell, tabs, inline notFound, read-only, axe
// ---------------------------------------------------------------------------

describe('LedgerOpsScreen — tabbed shell', () => {
  function renderScreen(overrides: Partial<Parameters<typeof LedgerOpsScreen>[0]> = {}) {
    return render(
      <LedgerOpsScreen
        initialEntryId={null}
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={null}
        {...overrides}
      />,
      { wrapper: wrapper() },
    );
  }

  it('renders four keyboard-operable tabs and the trial balance by default', () => {
    renderScreen();
    expect(screen.getByRole('tablist')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-tab-trial-balance')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByTestId('ledger-tb-table')).toBeInTheDocument();
  });

  it('ArrowRight on the tablist moves the active tab (roving keyboard nav)', () => {
    renderScreen();
    const tab = screen.getByTestId('ledger-tab-trial-balance');
    fireEvent.keyDown(tab, { key: 'ArrowRight' });
    expect(screen.getByTestId('ledger-tab-periods')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('clicking the 분개 tab reveals the entry lookup (entry-id-driven)', () => {
    renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-entry'));
    expect(screen.getByTestId('ledger-entry-input')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-entry-none')).toBeInTheDocument();
  });

  it('a seeded entryId that 404\'d renders notFound INLINE with the lookup still mounted', () => {
    renderScreen({ initialEntryId: 'nope', initialNotFound: true });
    // The screen opens on the entry tab when an entryId is supplied.
    expect(screen.getByTestId('ledger-entry-not-found')).toBeInTheDocument();
    // The lookup form stays mounted (not a whole-section block).
    expect(screen.getByTestId('ledger-entry-input')).toBeInTheDocument();
  });

  it('a seeded entry renders the entry detail in the 분개 tab', () => {
    renderScreen({
      initialEntryId: 'je-multi',
      initialEntry: MULTI_CURRENCY_ENTRY,
    });
    expect(screen.getByTestId('ledger-entry-detail')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-line-rate-0').textContent).toBe('13.5');
  });
});

describe('LedgerOpsScreen — read-only (NO mutation affordance anywhere)', () => {
  it('renders no submit-mutation / confirm / reason / idempotency / resolve UI', () => {
    const { container } = render(
      <LedgerOpsScreen
        initialEntryId={null}
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={null}
      />,
      { wrapper: wrapper() },
    );
    expect(container.querySelector('[data-testid*="reason"]')).toBeNull();
    expect(container.querySelector('[data-testid*="confirm"]')).toBeNull();
    expect(container.querySelector('[data-testid*="idempotency"]')).toBeNull();
    expect(container.querySelector('[data-testid*="resolve-submit"]')).toBeNull();
    expect(container.querySelector('[data-testid*="post-entry"]')).toBeNull();
    expect(container.querySelector('[data-testid*="close-period"]')).toBeNull();
  });

  it('console spy: no balance / line / account-code / token value reaches the console (confidential / F7)', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <LedgerOpsScreen
        initialEntryId="je-multi"
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={MULTI_CURRENCY_ENTRY}
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
    expect(all).not.toContain('1234567890123'); // trial-balance minor units
    expect(all).not.toContain('182250'); // entry base line value
    expect(all).not.toContain('je-multi'); // entry id
  });
});

describe('LedgerOpsScreen — WCAG AA (axe-clean)', () => {
  it('a fully populated section has zero axe violations', async () => {
    const { container } = render(
      <LedgerOpsScreen
        initialEntryId={null}
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={null}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });

  it('the entry tab (with the lookup empty state) is axe-clean', async () => {
    const { container } = render(
      <LedgerOpsScreen
        initialEntryId="je-multi"
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={MULTI_CURRENCY_ENTRY}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
