import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorOverviewScreen } from '@/features/dashboards';
import type { OperatorOverview } from '@/features/dashboards';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/dashboards` component behaviour (TASK-PC-FE-005 — READ-ONLY
 * composed overview):
 *   - all three cards render their data (ok)
 *   - a degraded card shows its OWN placeholder; the other cards + the
 *     shell still render (per-source isolation — never blank)
 *   - a forbidden card (operators non-SUPER_ADMIN / audit intersection-
 *     permission) shows "not available to your role" inline (no crash)
 *   - quick-links resolve to the EXISTING /accounts /audit /operators
 *   - all-degraded state renders with a retry affordance (no crash)
 *   - zero / empty values render without crashing
 *   - NO destructive/confirm dialog exists (read-only) + WCAG AA axe-clean
 *
 * Client re-query goes to the same-origin `/api/dashboards` proxy via
 * `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const OK: OperatorOverview = {
  accounts: { status: 'ok', totalElements: 150, sampleCount: 20 },
  audit: {
    status: 'ok',
    totalElements: 42,
    recentCount: 20,
    latestOccurredAt: '2026-04-12T10:00:00Z',
  },
  operators: {
    status: 'ok',
    totalElements: 7,
    activeCount: 6,
    suspendedCount: 1,
  },
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('OperatorOverviewScreen — render all cards', () => {
  it('renders the server-provided overview: accounts / audit / operators', () => {
    render(<OperatorOverviewScreen initial={OK} />, { wrapper: wrapper() });

    expect(
      screen.getByRole('heading', { name: 'IAM 상세 (계정 · 감사 · 운영자)' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('overview-accounts-total')).toHaveTextContent(
      '150',
    );
    expect(screen.getByTestId('overview-audit-total')).toHaveTextContent(
      '42',
    );
    expect(screen.getByTestId('overview-audit-latest')).toHaveTextContent(
      '2026. 4. 12. 19:00:00',
    );
    expect(
      screen.getByTestId('overview-operators-active'),
    ).toHaveTextContent('6');
    expect(
      screen.getByTestId('overview-operators-suspended'),
    ).toHaveTextContent('1');
  });

  it('has NO destructive/confirm dialog (read-only — FE-002/004 scaffolding absent)', () => {
    render(<OperatorOverviewScreen initial={OK} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('quick-links resolve to the EXISTING /accounts /audit /operators routes', () => {
    render(<OperatorOverviewScreen initial={OK} />, { wrapper: wrapper() });
    expect(
      screen.getByTestId('overview-card-accounts-quicklink'),
    ).toHaveAttribute('href', '/accounts');
    expect(
      screen.getByTestId('overview-card-audit-quicklink'),
    ).toHaveAttribute('href', '/audit');
    expect(
      screen.getByTestId('overview-card-operators-quicklink'),
    ).toHaveAttribute('href', '/operators');
  });

  it('zero / empty values render without crashing', () => {
    const zero: OperatorOverview = {
      accounts: { status: 'ok', totalElements: 0, sampleCount: 0 },
      audit: {
        status: 'ok',
        totalElements: 0,
        recentCount: 0,
        latestOccurredAt: null,
      },
      operators: {
        status: 'ok',
        totalElements: 0,
        activeCount: 0,
        suspendedCount: 0,
      },
    };
    render(<OperatorOverviewScreen initial={zero} />, { wrapper: wrapper() });
    expect(screen.getByTestId('overview-accounts-total')).toHaveTextContent(
      '0',
    );
    expect(screen.getByTestId('overview-audit-latest')).toHaveTextContent(
      '표시할 최근 활동이 없습니다.',
    );
  });
});

describe('OperatorOverviewScreen — per-source isolation', () => {
  it('an accounts-degraded card shows its OWN placeholder; audit + operators still render', () => {
    const partial: OperatorOverview = {
      ...OK,
      accounts: { status: 'degraded', totalElements: null, sampleCount: null },
    };
    render(<OperatorOverviewScreen initial={partial} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('overview-card-accounts-degraded'),
    ).toBeInTheDocument();
    // other cards still render their data — never blank
    expect(screen.getByTestId('overview-audit-total')).toHaveTextContent(
      '42',
    );
    expect(
      screen.getByTestId('overview-operators-total'),
    ).toHaveTextContent('7');
    // the shell heading is intact
    expect(
      screen.getByRole('heading', { name: 'IAM 상세 (계정 · 감사 · 운영자)' }),
    ).toBeInTheDocument();
  });

  it('an operators-forbidden card shows "not available to your role"; others fine', () => {
    const partial: OperatorOverview = {
      ...OK,
      operators: {
        status: 'forbidden',
        totalElements: null,
        activeCount: null,
        suspendedCount: null,
      },
    };
    render(<OperatorOverviewScreen initial={partial} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('overview-card-operators-forbidden'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('overview-card-operators-forbidden'),
    ).toHaveTextContent(/권한/);
    expect(screen.getByTestId('overview-accounts-total')).toHaveTextContent(
      '150',
    );
  });

  it('an audit-forbidden card (intersection-permission) renders inline (no crash)', () => {
    const partial: OperatorOverview = {
      ...OK,
      audit: {
        status: 'forbidden',
        totalElements: null,
        recentCount: null,
        latestOccurredAt: null,
      },
    };
    render(<OperatorOverviewScreen initial={partial} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('overview-card-audit-forbidden'),
    ).toBeInTheDocument();
  });

  it('all-degraded → an all-degraded notice + a retry affordance (no crash)', () => {
    const allDown: OperatorOverview = {
      accounts: { status: 'degraded', totalElements: null, sampleCount: null },
      audit: {
        status: 'degraded',
        totalElements: null,
        recentCount: null,
        latestOccurredAt: null,
      },
      operators: {
        status: 'degraded',
        totalElements: null,
        activeCount: null,
        suspendedCount: null,
      },
    };
    render(<OperatorOverviewScreen initial={allDown} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('overview-all-degraded'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('overview-refresh')).toBeInTheDocument();
    // the shell is intact, not a blank crash
    expect(
      screen.getByRole('heading', { name: 'IAM 상세 (계정 · 감사 · 운영자)' }),
    ).toBeInTheDocument();
  });
});

describe('OperatorOverviewScreen — explicit retry (one bounded call set, no auto-refetch)', () => {
  it('the refresh button issues exactly one re-query to the proxy (no storm)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(OK));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OperatorOverviewScreen initial={OK} />, { wrapper: wrapper() });

    // seeded initialData ⇒ NO fetch on mount (no auto-refetch storm)
    expect(fetchMock).not.toHaveBeenCalled();

    await user.click(screen.getByTestId('overview-refresh'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/dashboards');
  });
});

describe('OperatorOverviewScreen — accessibility (WCAG AA)', () => {
  it('the overview is axe-clean and keyboard-operable', async () => {
    const { container } = render(<OperatorOverviewScreen initial={OK} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
