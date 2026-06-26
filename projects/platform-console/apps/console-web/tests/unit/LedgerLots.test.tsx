import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { LedgerOpsScreen } from '@/features/ledger-ops';
import { PositionLotsTable } from '@/features/ledger-ops/components/PositionLotsTable';
import { PositionLotsLookup } from '@/features/ledger-ops/components/PositionLotsLookup';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  PositionLotsResponse,
} from '@/features/ledger-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/ledger-ops` FX position open-lots surface (TASK-PC-FE-091 —
 * STRICTLY READ-ONLY):
 *   - PositionLotsTable renders the open lots + a summary card
 *     (Σremaining / Σcarrying / lotCount) scale-correct from the minor-units
 *     STRING via `formatMoney` (NO Number coercion);
 *   - empty lots array → an empty-state message (NOT an error);
 *   - PositionLotsLookup gates submit on BOTH account + currency;
 *   - the LedgerOpsScreen exposes the 6th "FX 포지션 로트" tab; the lookup
 *     submit gates the query; no mutation affordance; WCAG AA axe-clean.
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
      debitTotal: M('1234567890123'),
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
  data: [{ periodId: '2026-05', status: 'OPEN', from: 'a', to: 'b', entryCount: 3 }],
  meta: { page: 0, size: 20, totalElements: 1 },
};

const DISCREPANCIES: DiscrepanciesResponse = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0 },
};

const LOTS: PositionLotsResponse = {
  lots: [
    {
      lotId: 'lot-1',
      currency: 'USD',
      acquiredAt: '2026-01-01T00:00:00Z',
      seq: 1,
      // > 2^53 — F5 precision must render from the string.
      originalForeignMinor: '9007199254740993',
      remainingForeignMinor: '9007199254740993',
      originalBaseMinor: '1300000',
      carryingBaseMinor: '1300000',
      sourceJournalEntryId: 'je-acq-1',
    },
  ],
  totalRemainingForeignMinor: '9007199254740993',
  totalCarryingBaseMinor: '1300000',
  lotCount: 1,
};

const EMPTY_LOTS: PositionLotsResponse = {
  lots: [],
  totalRemainingForeignMinor: '0',
  totalCarryingBaseMinor: '0',
  lotCount: 0,
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ---------------------------------------------------------------------------
// PositionLotsTable — table + summary, F5 money, empty-state
// ---------------------------------------------------------------------------

describe('PositionLotsTable — F5 money + summary + empty-state (TASK-PC-FE-091)', () => {
  it('renders the lots table with formatMoney-scaled amounts (F5 — large USD/KRW string, no Number coercion)', () => {
    render(<PositionLotsTable lots={LOTS} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-lots-table');
    const row0 = within(table).getByTestId('ledger-lots-row-0');
    // USD minor-units 9007199254740993 → scale 2 → integer portion present
    // (rendered via formatMoney from the string, never Number()).
    expect(row0.textContent).toContain('90071992547409.93');
    expect(row0.textContent).toContain('USD');
    // carrying base KRW (scale 0) present.
    expect(
      within(table).getByTestId('ledger-lots-carrying-0').textContent,
    ).toContain('1300000');
    // sourceJournalEntryId present.
    expect(row0.textContent).toContain('je-acq-1');
    // seq rendered (a number).
    expect(
      within(table).getByTestId('ledger-lots-seq-0').textContent,
    ).toBe('1');
  });

  it('renders the summary card (Σremaining foreign · Σcarrying base · lotCount)', () => {
    render(<PositionLotsTable lots={LOTS} />, { wrapper: wrapper() });
    const summary = screen.getByTestId('ledger-lots-summary');
    expect(
      within(summary).getByTestId('ledger-lots-total-remaining').textContent,
    ).toContain('90071992547409.93');
    expect(
      within(summary).getByTestId('ledger-lots-total-carrying').textContent,
    ).toContain('1300000');
    expect(
      within(summary).getByTestId('ledger-lots-count').textContent,
    ).toBe('1');
  });

  it('an empty lots array → empty-state message (NOT an error), summary still shows 0 / 0 / 0', () => {
    render(<PositionLotsTable lots={EMPTY_LOTS} />, { wrapper: wrapper() });
    expect(screen.getByTestId('ledger-lots-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-lots-table')).toBeNull();
    expect(
      screen.getByTestId('ledger-lots-count').textContent,
    ).toBe('0');
  });

  it('lots null → none placeholder (no crash)', () => {
    render(<PositionLotsTable lots={null} />, { wrapper: wrapper() });
    expect(screen.getByTestId('ledger-lots-none')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-lots-detail')).toBeNull();
  });

  it('clicking a lot sourceJournalEntryId cell calls onSelectEntry with the exact id', () => {
    const onSelectEntry = vi.fn();
    render(<PositionLotsTable lots={LOTS} onSelectEntry={onSelectEntry} />, {
      wrapper: wrapper(),
    });
    fireEvent.click(screen.getByTestId('ledger-lots-entry-0'));
    expect(onSelectEntry).toHaveBeenCalledWith('je-acq-1');
    expect(onSelectEntry).toHaveBeenCalledTimes(1);
  });

  it('without onSelectEntry the sourceJournalEntryId cell is a plain span', () => {
    render(<PositionLotsTable lots={LOTS} />, { wrapper: wrapper() });
    const cell = screen.getByTestId('ledger-lots-entry-0');
    expect(cell.tagName.toLowerCase()).toBe('span');
  });
});

// ---------------------------------------------------------------------------
// PositionLotsLookup — submit gating
// ---------------------------------------------------------------------------

describe('PositionLotsLookup — submit gates on BOTH account + currency (TASK-PC-FE-091)', () => {
  it('submit is disabled until BOTH fields are non-empty; onSubmit is called with (code, UPPER currency)', () => {
    const onSubmit = vi.fn();
    render(<PositionLotsLookup onSubmit={onSubmit} />, { wrapper: wrapper() });
    const submit = screen.getByTestId('ledger-lots-submit') as HTMLButtonElement;
    // Empty → disabled.
    expect(submit).toBeDisabled();

    // Only account → still disabled.
    fireEvent.change(screen.getByTestId('ledger-lots-account-input'), {
      target: { value: 'CUSTOMER_WALLET:acc-1' },
    });
    expect(submit).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();

    // Both → enabled; submit upper-cases the currency.
    fireEvent.change(screen.getByTestId('ledger-lots-currency-input'), {
      target: { value: 'usd' },
    });
    expect(submit).not.toBeDisabled();
    fireEvent.click(submit);
    expect(onSubmit).toHaveBeenCalledWith('CUSTOMER_WALLET:acc-1', 'USD');
  });
});

// ---------------------------------------------------------------------------
// LedgerOpsScreen — the 6th "FX 포지션 로트" tab + lookup-gated query
// ---------------------------------------------------------------------------

describe('LedgerOpsScreen — FX 포지션 로트 tab (TASK-PC-FE-091)', () => {
  function renderScreen(
    overrides: Partial<Parameters<typeof LedgerOpsScreen>[0]> = {},
  ) {
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

  it('the sixth tab "FX 포지션 로트" is present and labelled (7 tabs total with FX 환율 피드)', () => {
    renderScreen();
    const tab = screen.getByTestId('ledger-tab-lots');
    expect(tab).toBeInTheDocument();
    expect(tab.textContent).toContain('FX 포지션 로트');
    expect(screen.getAllByRole('tab')).toHaveLength(7);
  });

  it('clicking the tab reveals the lookup form; no (code,currency) yet → "none-input" placeholder, no fetch', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-lots'));
    // The lots panel is a lazy (next/dynamic) boundary (TASK-PC-FE-134).
    expect(
      await screen.findByTestId('ledger-lots-account-input'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('ledger-lots-currency-input')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-lots-none-input')).toBeInTheDocument();
    // The query is gated — no (code, currency) submitted yet → no fetch.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('submitting the lookup gates the query: the same-origin lots fetch fires with the (code, currency) path', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          lots: LOTS.lots,
          totalRemainingForeignMinor: LOTS.totalRemainingForeignMinor,
          totalCarryingBaseMinor: LOTS.totalCarryingBaseMinor,
          lotCount: LOTS.lotCount,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-lots'));
    // The lots panel mounts lazily — await its lookup form (TASK-PC-FE-134).
    fireEvent.change(await screen.findByTestId('ledger-lots-account-input'), {
      target: { value: 'CUSTOMER_WALLET:acc-1' },
    });
    fireEvent.change(screen.getByTestId('ledger-lots-currency-input'), {
      target: { value: 'USD' },
    });
    fireEvent.click(screen.getByTestId('ledger-lots-submit'));

    await screen.findByTestId('ledger-lots-table');
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('/api/ledger/settlements/');
    expect(url).toContain('CUSTOMER_WALLET%3Aacc-1');
    expect(url).toContain('/USD/lots');
    // The rendered table shows the F5-scaled amount.
    expect(screen.getByTestId('ledger-lots-table').textContent).toContain(
      '90071992547409.93',
    );
  });

  it('the FX 포지션 로트 tab has no mutation affordance (STRICTLY READ-ONLY)', async () => {
    const { container } = renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-lots'));
    // Await the lazily-mounted panel so the absence assertions are meaningful.
    await screen.findByTestId('ledger-lots-account-input');
    expect(container.querySelector('[data-testid*="resolve"]')).toBeNull();
    expect(container.querySelector('[data-testid*="reason"]')).toBeNull();
    expect(container.querySelector('[data-testid*="idempotency"]')).toBeNull();
    expect(container.querySelector('[data-testid*="confirm"]')).toBeNull();
  });

  it('the FX 포지션 로트 tab (lookup empty state) is axe-clean (WCAG AA)', async () => {
    const { container } = renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-lots'));
    // Await the lazily-mounted panel so axe runs on the real lookup form.
    await screen.findByTestId('ledger-lots-account-input');
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
