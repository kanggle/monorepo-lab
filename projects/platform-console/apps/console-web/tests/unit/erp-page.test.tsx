import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

/**
 * TASK-MONO-809 (ADR-MONO-084 P4) — the `/erp` 개요 page reads `?period=` for
 * the «실적» strip like `/wms` (TASK-MONO-808): `week|month` is passed to
 * `getErpOverviewState`; an absent or unknown value falls back to
 * `DEFAULT_OVERVIEW_PERIOD`. `?asOf=` keeps feeding the masterdata counts and
 * is handed to the screen (period links + note). The feature barrel is stubbed.
 */

const m = vi.hoisted(() => ({
  getErpOverviewState: vi.fn(),
  resolveErpEligibility: vi.fn(),
  screenProps: vi.fn(),
}));
vi.mock('@/features/erp-ops', () => ({
  DEFAULT_OVERVIEW_PERIOD: 'month',
  getErpOverviewState: m.getErpOverviewState,
  ErpOverviewScreen: (props: unknown) => {
    m.screenProps(props);
    return <div data-testid="erp-overview" />;
  },
  ErpSectionNotice: ({ kind }: { kind: string }) => <div data-testid={`notice-${kind}`} />,
  MyAccountLinkProposalsCard: () => <div data-testid="proposals" />,
}));
vi.mock('@/app/(console)/erp/_eligibility', () => ({
  resolveErpEligibility: m.resolveErpEligibility,
}));

import ErpOverviewPage from '@/app/(console)/erp/page';

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
  m.resolveErpEligibility.mockResolvedValue({ eligible: true, registryDegraded: false });
  m.getErpOverviewState.mockResolvedValue({ notEligible: false });
});

describe('ErpOverviewPage — ?period= (TASK-MONO-809)', () => {
  it('no searchParams -> the default period, no asOf', async () => {
    render(await ErpOverviewPage({}));
    expect(m.getErpOverviewState).toHaveBeenCalledWith(true, null, 'month');
    expect(screen.getByTestId('erp-overview')).toBeInTheDocument();
  });

  it('?period=week is passed through; an unknown value falls back to the default', async () => {
    render(await ErpOverviewPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(m.getErpOverviewState).toHaveBeenLastCalledWith(true, null, 'week');

    cleanup();
    render(await ErpOverviewPage({ searchParams: Promise.resolve({ period: 'year' }) }));
    expect(m.getErpOverviewState).toHaveBeenLastCalledWith(true, null, 'month');

    cleanup();
    render(await ErpOverviewPage({ searchParams: Promise.resolve({}) }));
    expect(m.getErpOverviewState).toHaveBeenLastCalledWith(true, null, 'month');
  });

  it('asOf and period are independent; asOf reaches the screen', async () => {
    render(
      await ErpOverviewPage({
        searchParams: Promise.resolve({ asOf: ' 2026-01-01 ', period: 'week' }),
      }),
    );
    expect(m.getErpOverviewState).toHaveBeenCalledWith(true, '2026-01-01', 'week');
    expect(m.screenProps.mock.calls[0][0]).toMatchObject({ asOf: '2026-01-01' });
  });

  it('not eligible -> notice; the period still reaches the (short-circuiting) read', async () => {
    m.resolveErpEligibility.mockResolvedValue({ eligible: false, registryDegraded: false });
    m.getErpOverviewState.mockResolvedValue({ notEligible: true });
    render(await ErpOverviewPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(m.getErpOverviewState).toHaveBeenCalledWith(false, null, 'week');
    expect(screen.getByTestId('notice-notEligible')).toBeInTheDocument();
  });
});
