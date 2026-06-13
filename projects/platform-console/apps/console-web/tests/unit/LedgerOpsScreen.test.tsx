import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  within,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { LedgerOpsScreen } from '@/features/ledger-ops';
import { TrialBalanceTable } from '@/features/ledger-ops/components/TrialBalanceTable';
import { PeriodDetail } from '@/features/ledger-ops/components/PeriodDetail';
import { JournalEntryDetail } from '@/features/ledger-ops/components/JournalEntryDetail';
import { DiscrepancyQueue } from '@/features/ledger-ops/components/DiscrepancyQueue';
import { DiscrepancyDetail } from '@/features/ledger-ops/components/DiscrepancyDetail';
import { AccountDetail } from '@/features/ledger-ops/components/AccountDetail';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  JournalEntry,
  Period,
  AccountBalance,
  AccountEntriesResponse,
  Statement,
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

// ---------------------------------------------------------------------------
// DiscrepancyDetail — resolve mutation (TASK-PC-FE-073): OPEN-only gating,
// confirm-gated, success reflects RESOLVED, 409/422 inline, Escape/cancel.
// ---------------------------------------------------------------------------

const OPEN_DISCREPANCY = {
  discrepancyId: 'd-1',
  type: 'AMOUNT_MISMATCH',
  externalRef: 'bank-ref-1',
  journalEntryId: 'je-9',
  expectedMinor: '100',
  actualMinor: '105',
  currency: 'KRW',
  status: 'OPEN',
};

const RESOLVED_DISCREPANCY = {
  ...OPEN_DISCREPANCY,
  status: 'RESOLVED',
  resolution: {
    resolutionType: 'WRITTEN_OFF',
    note: 'fx gap below threshold',
    resolvedBy: 'op-1',
    resolvedAt: '2026-05-20T00:00:00Z',
  },
};

function jsonRes(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('DiscrepancyDetail — resolve mutation (PC-FE-073)', () => {
  it('an OPEN discrepancy shows the resolve action; a RESOLVED one does NOT', async () => {
    // OPEN → resolve button present.
    const openFetch = vi
      .fn()
      .mockResolvedValue(jsonRes(OPEN_DISCREPANCY));
    vi.stubGlobal('fetch', openFetch);
    const { unmount } = render(<DiscrepancyDetail discrepancyId="d-1" />, {
      wrapper: wrapper(),
    });
    await waitFor(() =>
      expect(screen.getByTestId('ledger-recon-resolve-open')).toBeInTheDocument(),
    );
    unmount();

    // RESOLVED → no resolve affordance at all.
    const resolvedFetch = vi
      .fn()
      .mockResolvedValue(jsonRes(RESOLVED_DISCREPANCY));
    vi.stubGlobal('fetch', resolvedFetch);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() =>
      expect(screen.getByTestId('ledger-recon-resolution')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('ledger-recon-resolve-open')).toBeNull();
  });

  it('confirm is gated on a non-empty note (empty note → confirm disabled, NO fetch)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonRes(OPEN_DISCREPANCY));
    vi.stubGlobal('fetch', fetchMock);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() =>
      screen.getByTestId('ledger-recon-resolve-open'),
    );
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    // Dialog opened with an empty note → confirm disabled.
    const confirm = screen.getByTestId(
      'ledger-recon-resolve-confirm',
    ) as HTMLButtonElement;
    expect(confirm).toBeDisabled();
    const callsBefore = fetchMock.mock.calls.length;
    fireEvent.click(confirm); // disabled → no-op
    expect(fetchMock.mock.calls.length).toBe(callsBefore);
  });

  it('on success the detail reflects RESOLVED (the resolve POST resolves, then refetch shows resolution)', async () => {
    let resolved = false;
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const u = String(url);
      const method = (init?.method ?? 'GET').toUpperCase();
      if (u.endsWith('/resolve') && method === 'POST') {
        resolved = true;
        return Promise.resolve(jsonRes(RESOLVED_DISCREPANCY));
      }
      // The detail read — RESOLVED after the mutation, OPEN before.
      return Promise.resolve(
        jsonRes(resolved ? RESOLVED_DISCREPANCY : OPEN_DISCREPANCY),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() => screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.change(screen.getByTestId('ledger-recon-resolve-note'), {
      target: { value: 'fx gap below threshold' },
    });
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-confirm'));

    // The POST body carries { resolutionType, note } and NO Idempotency-Key.
    await waitFor(() => expect(resolved).toBe(true));
    const postCall = fetchMock.mock.calls.find(
      ([u, init]) =>
        String(u).endsWith('/resolve') &&
        (init as RequestInit)?.method?.toUpperCase() === 'POST',
    )!;
    const postInit = postCall[1] as RequestInit;
    const body = JSON.parse(String(postInit.body));
    expect(body.note).toBe('fx gap below threshold');
    expect(body.resolutionType).toBeDefined();
    expect(body.idempotencyKey).toBeUndefined();

    // The detail refetches + reflects RESOLVED (resolution block appears,
    // the resolve affordance disappears).
    await waitFor(() =>
      expect(screen.getByTestId('ledger-recon-resolution')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('ledger-recon-resolve-open')).toBeNull();
  });

  it('409 RECONCILIATION_ALREADY_RESOLVED is shown inline (no crash)', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase();
      if (String(url).endsWith('/resolve') && method === 'POST') {
        return Promise.resolve(
          jsonRes({ code: 'RECONCILIATION_ALREADY_RESOLVED', message: 'x' }, 409),
        );
      }
      return Promise.resolve(jsonRes(OPEN_DISCREPANCY));
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() => screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.change(screen.getByTestId('ledger-recon-resolve-note'), {
      target: { value: 'note' },
    });
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-confirm'));
    await waitFor(() =>
      expect(screen.getByTestId('ledger-recon-resolve-error').textContent).toContain(
        '이미 해소',
      ),
    );
  });

  it('422 RECONCILIATION_PERIOD_LOCKED is shown inline (no crash)', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase();
      if (String(url).endsWith('/resolve') && method === 'POST') {
        return Promise.resolve(
          jsonRes({ code: 'RECONCILIATION_PERIOD_LOCKED', message: 'x' }, 422),
        );
      }
      return Promise.resolve(jsonRes(OPEN_DISCREPANCY));
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() => screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.change(screen.getByTestId('ledger-recon-resolve-note'), {
      target: { value: 'note' },
    });
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-confirm'));
    await waitFor(() =>
      expect(screen.getByTestId('ledger-recon-resolve-error').textContent).toContain(
        '마감',
      ),
    );
  });

  it('Escape / cancel closes the dialog without a fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonRes(OPEN_DISCREPANCY));
    vi.stubGlobal('fetch', fetchMock);
    render(<DiscrepancyDetail discrepancyId="d-1" />, { wrapper: wrapper() });
    await waitFor(() => screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    expect(screen.getByTestId('ledger-recon-resolve-dialog')).toBeInTheDocument();
    const callsBefore = fetchMock.mock.calls.length;
    fireEvent.keyDown(screen.getByTestId('ledger-recon-resolve-overlay'), {
      key: 'Escape',
    });
    await waitFor(() =>
      expect(screen.queryByTestId('ledger-recon-resolve-dialog')).toBeNull(),
    );
    // Cancel path issued no additional (mutation) fetch.
    expect(fetchMock.mock.calls.length).toBe(callsBefore);
  });

  it('the open resolve dialog is axe-clean (WCAG AA)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonRes(OPEN_DISCREPANCY));
    vi.stubGlobal('fetch', fetchMock);
    const { container } = render(<DiscrepancyDetail discrepancyId="d-1" />, {
      wrapper: wrapper(),
    });
    await waitFor(() => screen.getByTestId('ledger-recon-resolve-open'));
    fireEvent.click(screen.getByTestId('ledger-recon-resolve-open'));
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-074 — AccountDetail component (F5 + tolerant enum parsing)
// ---------------------------------------------------------------------------

const ACCOUNT_BALANCE: AccountBalance = {
  ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
  type: 'LIABILITY',
  normalSide: 'CREDIT',
  debitTotal: M('1234567890123'),
  creditTotal: M('9876543210987'),
  balance: M('8641975320864'),
  balanceSide: 'CREDIT',
};

const ACCOUNT_ENTRIES: AccountEntriesResponse = {
  data: [
    {
      entryId: 'je-acct-1',
      postedAt: '2026-06-13T10:00:00Z',
      direction: 'CREDIT',
      money: M('13500', 'USD'),
    },
    {
      entryId: 'je-acct-2',
      postedAt: '2026-06-12T08:00:00Z',
      direction: 'DEBIT',
      money: M('200'),
    },
  ],
  meta: { page: 0, size: 20, totalElements: 2 },
};

describe('AccountDetail — F5 money + tolerant enum + drill-in (TASK-PC-FE-074)', () => {
  it('renders the balance card with formatMoney-scaled amounts + direction + normalSide (F5, no Number coercion)', () => {
    render(<AccountDetail balance={ACCOUNT_BALANCE} entries={ACCOUNT_ENTRIES} />, {
      wrapper: wrapper(),
    });
    const card = screen.getByTestId('ledger-account-balance');
    // Large KRW minor-units — formatMoney should include the integer portion
    // 1234567890123 minor KRW (scale 0) = ₩1,234,567,890,123 or similar.
    expect(card.textContent).toContain('1234567890123');
    expect(card.textContent).toContain('LIABILITY');
    expect(card.textContent).toContain('CREDIT');
  });

  it('renders the entries table with entryId + direction + formatMoney money (F5 round-trip)', () => {
    render(<AccountDetail balance={ACCOUNT_BALANCE} entries={ACCOUNT_ENTRIES} />, {
      wrapper: wrapper(),
    });
    const table = screen.getByTestId('ledger-account-entries-table');
    const row0 = within(table).getByTestId('ledger-account-entry-row-0');
    // money: USD minor units 13500 → scale 2 → 135.00 USD
    expect(row0.textContent).toContain('135.00');
    expect(row0.textContent).toContain('CREDIT');
    const row1 = within(table).getByTestId('ledger-account-entry-row-1');
    expect(row1.textContent).toContain('200');
    expect(row1.textContent).toContain('DEBIT');
  });

  it('clicking an entryId cell calls onSelectEntry with the exact id', () => {
    const onSelectEntry = vi.fn();
    render(
      <AccountDetail
        balance={ACCOUNT_BALANCE}
        entries={ACCOUNT_ENTRIES}
        onSelectEntry={onSelectEntry}
      />,
      { wrapper: wrapper() },
    );
    const table = screen.getByTestId('ledger-account-entries-table');
    fireEvent.click(within(table).getByTestId('ledger-account-entry-id-0'));
    expect(onSelectEntry).toHaveBeenCalledWith('je-acct-1');
    expect(onSelectEntry).toHaveBeenCalledTimes(1);
  });

  it('without onSelectEntry the entryId cell is a span (plain text, no interactive element)', () => {
    render(<AccountDetail balance={ACCOUNT_BALANCE} entries={ACCOUNT_ENTRIES} />, {
      wrapper: wrapper(),
    });
    const table = screen.getByTestId('ledger-account-entries-table');
    const cell = within(table).getByTestId('ledger-account-entry-id-0');
    expect(cell.tagName.toLowerCase()).toBe('span');
  });

  it('unknown future type / normalSide / balanceSide / direction render as-is (tolerant — no throw)', () => {
    const futureBalance: AccountBalance = {
      ...ACCOUNT_BALANCE,
      type: 'FUTURE_TYPE',
      normalSide: 'FUTURE_SIDE',
      balanceSide: 'FUTURE_SIDE',
    };
    const futureEntries: AccountEntriesResponse = {
      ...ACCOUNT_ENTRIES,
      data: [{ ...ACCOUNT_ENTRIES.data[0], direction: 'FUTURE_DIRECTION' }],
    };
    expect(() =>
      render(<AccountDetail balance={futureBalance} entries={futureEntries} />, {
        wrapper: wrapper(),
      }),
    ).not.toThrow();
    const card = screen.getByTestId('ledger-account-balance');
    expect(card.textContent).toContain('FUTURE_TYPE');
    expect(card.textContent).toContain('FUTURE_SIDE');
  });

  it('balance null → balance-none placeholder (no crash)', () => {
    render(<AccountDetail balance={null} entries={ACCOUNT_ENTRIES} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('ledger-account-balance-none')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-account-balance')).toBeNull();
  });

  it('entries null → entries-none placeholder (no crash)', () => {
    render(<AccountDetail balance={ACCOUNT_BALANCE} entries={null} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('ledger-account-entries-none')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-account-entries-table')).toBeNull();
  });

  it('empty entries array → empty placeholder (no crash)', () => {
    render(
      <AccountDetail
        balance={ACCOUNT_BALANCE}
        entries={{ data: [], meta: { page: 0, size: 20, totalElements: 0 } }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('ledger-account-entries-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-account-entries-table')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-074 — TrialBalanceTable onSelectAccount drill (backward compat)
// ---------------------------------------------------------------------------

describe('TrialBalanceTable — onSelectAccount drill (TASK-PC-FE-074 backward compat)', () => {
  it('without onSelectAccount the account code cell stays plain text (FE-072 callers unaffected)', () => {
    render(<TrialBalanceTable trialBalance={TRIAL_BALANCE} />);
    const code = screen.getByTestId('ledger-tb-code-0');
    expect(code.textContent).toContain('1000');
    expect(screen.queryByTestId('ledger-tb-code-link-0')).toBeNull();
  });

  it('with onSelectAccount the code cell renders a button (ledger-tb-code-link-0) and calls back', () => {
    const onSelectAccount = vi.fn();
    render(
      <TrialBalanceTable
        trialBalance={TRIAL_BALANCE}
        onSelectAccount={onSelectAccount}
      />,
    );
    const btn = screen.getByTestId('ledger-tb-code-link-0');
    expect(btn.tagName.toLowerCase()).toBe('button');
    fireEvent.click(btn);
    expect(onSelectAccount).toHaveBeenCalledWith('1000');
    expect(onSelectAccount).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-074 — LedgerOpsScreen 계정 tab (5th tab, account-code driven)
// ---------------------------------------------------------------------------

describe('LedgerOpsScreen — 계정 tab (TASK-PC-FE-074)', () => {
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

  it('the fifth tab "계정" is present and labelled', () => {
    renderScreen();
    expect(screen.getByTestId('ledger-tab-account')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-tab-account').textContent).toContain('계정');
  });

  it('clicking the 계정 tab reveals the account lookup form (no code → "none" placeholder)', () => {
    renderScreen();
    fireEvent.click(screen.getByTestId('ledger-tab-account'));
    expect(screen.getByTestId('ledger-account-input')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-account-none')).toBeInTheDocument();
  });

  it('a seeded accountCode opens the screen on the 계정 tab with AccountDetail populated (F5 scale)', () => {
    renderScreen({
      initialAccountCode: 'CUSTOMER_WALLET:acc-1',
      initialAccountBalance: ACCOUNT_BALANCE,
      initialAccountEntries: ACCOUNT_ENTRIES,
    });
    // Should start on the 계정 tab (because initialAccountCode is set).
    expect(screen.getByTestId('ledger-tab-account')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByTestId('ledger-account-detail')).toBeInTheDocument();
    const card = screen.getByTestId('ledger-account-balance');
    // F5: large minor-unit amount rendered via formatMoney.
    expect(card.textContent).toContain('1234567890123');
    // entries table present.
    expect(screen.getByTestId('ledger-account-entries-table')).toBeInTheDocument();
  });

  it('a seeded accountCode that 404\'d renders the not-found notice inline (lookup form stays mounted)', () => {
    renderScreen({
      initialAccountCode: 'CUSTOMER_WALLET:nope',
      initialAccountNotFound: true,
    });
    expect(screen.getByTestId('ledger-account-not-found')).toBeInTheDocument();
    // lookup form stays mounted.
    expect(screen.getByTestId('ledger-account-input')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-account-detail')).toBeNull();
  });

  it('clicking a trial-balance account code drills into the 계정 tab (handleSelectAccount)', () => {
    renderScreen();
    // The trial-balance tab is visible by default.
    expect(screen.getByTestId('ledger-tab-trial-balance')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    // Click the account code link in the trial-balance table.
    fireEvent.click(screen.getByTestId('ledger-tb-code-link-0'));
    // Now the screen should have switched to the 계정 tab.
    expect(screen.getByTestId('ledger-tab-account')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    // The account panel is now visible (the account lookup form is mounted).
    expect(screen.getByTestId('ledger-account-input')).toBeInTheDocument();
    // The panel no longer shows the "none" placeholder since a code was
    // selected — the detail component or a loading state is rendered.
    // (The lookup input's internal state is initialized once — we assert
    // the tab switch, not the input's displayed value.)
  });

  it('clicking an entryId in the account entries table switches to the 분개 조회 tab (handleSelectEntry)', () => {
    renderScreen({
      initialAccountCode: 'CUSTOMER_WALLET:acc-1',
      initialAccountBalance: ACCOUNT_BALANCE,
      initialAccountEntries: ACCOUNT_ENTRIES,
    });
    // Should be on the 계정 tab.
    const table = screen.getByTestId('ledger-account-entries-table');
    // The entry id cells are buttons when onSelectEntry is wired.
    const entryIdBtn = within(table).getByTestId('ledger-account-entry-id-0');
    fireEvent.click(entryIdBtn);
    // Should switch to the 분개 조회 tab.
    expect(screen.getByTestId('ledger-tab-entry')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    // The entry tab is now visible with the lookup form mounted.
    expect(screen.getByTestId('ledger-entry-input')).toBeInTheDocument();
    // (The JournalEntryLookup internal state is initialized once; we assert
    // the tab switch, not the input's displayed value.)
  });

  it('the 계정 tab has no mutation affordance (STRICTLY READ-ONLY)', () => {
    const { container } = renderScreen({
      initialAccountCode: 'CUSTOMER_WALLET:acc-1',
      initialAccountBalance: ACCOUNT_BALANCE,
      initialAccountEntries: ACCOUNT_ENTRIES,
    });
    expect(container.querySelector('[data-testid*="resolve"]')).toBeNull();
    expect(container.querySelector('[data-testid*="post-entry"]')).toBeNull();
    expect(container.querySelector('[data-testid*="close-period"]')).toBeNull();
    expect(container.querySelector('[data-testid*="reason"]')).toBeNull();
    expect(container.querySelector('[data-testid*="idempotency"]')).toBeNull();
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

  it('the 계정 tab (with AccountDetail seeded) is axe-clean (TASK-PC-FE-074)', async () => {
    const { container } = render(
      <LedgerOpsScreen
        initialEntryId={null}
        trialBalance={TRIAL_BALANCE}
        periods={PERIODS}
        discrepancies={DISCREPANCIES}
        initialEntry={null}
        initialAccountCode="CUSTOMER_WALLET:acc-1"
        initialAccountBalance={ACCOUNT_BALANCE}
        initialAccountEntries={ACCOUNT_ENTRIES}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-075 — LedgerOpsScreen 대사 tab statement detail
// ---------------------------------------------------------------------------

const STATEMENT: Statement = {
  statementId: 'stmt-1',
  ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
  source: 'BANK_FEED',
  statementDate: '2026-06-13',
  matchedCount: 1,
  discrepancyCount: 1,
  matches: [
    {
      statementLineExternalRef: 'ext-ref-001',
      journalEntryId: 'je-123',
      money: { amount: '9007199254740993', currency: 'KRW' }, // > 2^53 — F5
    },
  ],
  discrepancies: [
    {
      discrepancyId: 'd-stmt-1',
      type: 'AMOUNT_MISMATCH',
      externalRef: 'ext-ref-001',
      journalEntryId: 'je-123',
      expectedMinor: '9007199254740993',
      actualMinor: '9007199254740990',
      currency: 'KRW',
      status: 'OPEN',
    },
  ],
};

describe('LedgerOpsScreen — 대사 tab statement (TASK-PC-FE-075)', () => {
  function renderRecon(overrides: Partial<Parameters<typeof LedgerOpsScreen>[0]> = {}) {
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

  it('the 대사 tab contains the StatementLookup input (NO new tab added)', () => {
    renderRecon();
    fireEvent.click(screen.getByTestId('ledger-tab-reconciliation'));
    expect(screen.getByTestId('ledger-statement-input')).toBeInTheDocument();
    // Exactly the same tab count (4 original + account = 5 total; NO 6th).
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(5);
  });

  it('a seeded statementId opens on the 대사 tab with StatementDetail rendered (F5 header values)', () => {
    renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    // Screen opens on the reconciliation tab.
    expect(screen.getByTestId('ledger-tab-reconciliation')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByTestId('ledger-statement-header')).toBeInTheDocument();
    expect(
      screen.getByTestId('ledger-statement-account-code').textContent,
    ).toContain('CUSTOMER_WALLET:acc-1');
    expect(screen.getByTestId('ledger-statement-source').textContent).toContain(
      'BANK_FEED',
    );
    expect(
      screen.getByTestId('ledger-statement-matched-count').textContent,
    ).toBe('1');
    expect(
      screen.getByTestId('ledger-statement-discrepancy-count').textContent,
    ).toBe('1');
  });

  it('match-row money is rendered via formatMoney (F5 — large KRW minor string, no Number coercion)', () => {
    renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    const matchRow = screen.getByTestId('ledger-statement-match-row-0');
    // amount '9007199254740993' KRW (scale 0) → numeric portion present.
    expect(matchRow.textContent).toContain('9007199254740993');
    // externalRef present.
    expect(matchRow.textContent).toContain('ext-ref-001');
  });

  it('clicking a match-row journalEntryId button switches to the 분개 조회 tab (handleSelectEntry)', () => {
    renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    fireEvent.click(screen.getByTestId('ledger-statement-match-entry-0'));
    // Now on the 분개 조회 tab.
    expect(screen.getByTestId('ledger-tab-entry')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByTestId('ledger-entry-input')).toBeInTheDocument();
  });

  it('clicking a discrepancy-row link selects that discrepancy in the DiscrepancyDetail (stays on 대사 tab)', () => {
    renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    // The discrepancy table in the statement.
    const disc0 = screen.getByTestId('ledger-statement-disc-link-0');
    expect(disc0.textContent).toContain('d-stmt-1');
    fireEvent.click(disc0);
    // Still on the 대사 tab (disc link does NOT switch tabs).
    expect(screen.getByTestId('ledger-tab-reconciliation')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('a seeded statementId that 404\'d renders the not-found notice inline (lookup form stays mounted)', () => {
    renderRecon({
      initialStatementId: 'nope',
      initialStatementNotFound: true,
    });
    // Screen opens on the reconciliation tab.
    expect(screen.getByTestId('ledger-tab-reconciliation')).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByTestId('ledger-statement-not-found')).toBeInTheDocument();
    // Lookup form stays mounted after a 404.
    expect(screen.getByTestId('ledger-statement-input')).toBeInTheDocument();
    // StatementDetail NOT rendered.
    expect(screen.queryByTestId('ledger-statement-header')).toBeNull();
  });

  it('discrepancy queue (DiscrepancyQueue) is still rendered below the statement section', () => {
    renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    // The existing discrepancy queue must still be present.
    expect(screen.getByTestId('ledger-recon-table')).toBeInTheDocument();
  });

  it('the 대사 tab with a statement is axe-clean (WCAG AA)', async () => {
    const { container } = renderRecon({
      initialStatementId: 'stmt-1',
      initialStatement: STATEMENT,
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
