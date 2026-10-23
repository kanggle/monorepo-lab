import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';

/**
 * TASK-MONO-810 (ADR-MONO-084 P5) — the two scm routes the «실적» strip joins:
 *   - `/scm` reads `?period=` (`week|month`; absent / unknown → the default)
 *     and hands it to `getScmOverviewState`.
 *   - `/scm/procurement` reads `?status=` / `?submittedFrom=` / `?submittedTo=`
 *     (the strip's card links), validates them with the REAL
 *     `parsePoListSearchParams`, seeds the first page with the applied filter
 *     and hands the screen both the filter and what was ignored.
 * The registry, the state fetchers and the screens are stubbed.
 */

const m = vi.hoisted(() => ({
  getCatalog: vi.fn(),
  getScmOverviewState: vi.fn(),
  getScmProcurementState: vi.fn(),
  procurementProps: vi.fn(),
}));
vi.mock('@/features/catalog', () => ({ getCatalog: m.getCatalog }));
vi.mock('@/features/scm-ops', async () => {
  const actual = await vi.importActual<typeof import('@/features/scm-ops')>('@/features/scm-ops');
  return {
    DEFAULT_OVERVIEW_PERIOD: actual.DEFAULT_OVERVIEW_PERIOD,
    parsePoListSearchParams: actual.parsePoListSearchParams,
    getScmOverviewState: m.getScmOverviewState,
    getScmProcurementState: m.getScmProcurementState,
    ScmOverview: () => <div data-testid="scm-overview" />,
    ScmProcurementScreen: (props: unknown) => {
      m.procurementProps(props);
      return <div data-testid="scm-procurement-screen" />;
    },
  };
});

import ScmPage from '@/app/(console)/scm/page';
import ScmProcurementPage from '@/app/(console)/scm/procurement/page';

const PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
  m.getCatalog.mockResolvedValue({
    products: [{ productKey: 'scm', available: true, tenants: ['scm'] }],
  });
  m.getScmOverviewState.mockResolvedValue({ notEligible: false });
  m.getScmProcurementState.mockImplementation(
    async (_eligible: boolean, filter: unknown) => ({
      notEligible: false,
      forbidden: false,
      rateLimited: false,
      degraded: false,
      poList: PAGE,
      filter,
    }),
  );
});

describe('/scm — ?period= (TASK-MONO-810)', () => {
  it('no searchParams → the default period', async () => {
    render(await ScmPage({}));
    expect(m.getScmOverviewState).toHaveBeenCalledWith(true, 'month');
    expect(screen.getByTestId('scm-overview')).toBeInTheDocument();
  });

  it('?period=week is passed through; an unknown value falls back to the default', async () => {
    render(await ScmPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(m.getScmOverviewState).toHaveBeenLastCalledWith(true, 'week');

    cleanup();
    render(await ScmPage({ searchParams: Promise.resolve({ period: 'year' }) }));
    expect(m.getScmOverviewState).toHaveBeenLastCalledWith(true, 'month');
  });

  it('not eligible → the notice, no overview read', async () => {
    m.getCatalog.mockResolvedValue({ products: [] });
    render(await ScmPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(screen.getByTestId('scm-not-eligible')).toBeInTheDocument();
    expect(m.getScmOverviewState).not.toHaveBeenCalled();
  });
});

describe('/scm/procurement — URL filter (TASK-MONO-810)', () => {
  it('no searchParams → an empty filter, nothing ignored', async () => {
    render(await ScmProcurementPage({}));
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {});
    expect(m.procurementProps.mock.calls[0][0]).toMatchObject({
      initialFilter: {},
      ignoredParams: [],
    });
  });

  it('a card link (submit window) seeds the first page with it and reaches the screen', async () => {
    const from = '2026-10-01T00:00:00+09:00';
    const to = '2026-10-24T14:05:00+09:00';
    render(
      await ScmProcurementPage({
        searchParams: Promise.resolve({ submittedFrom: from, submittedTo: to }),
      }),
    );
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {
      submittedFrom: from,
      submittedTo: to,
    });
    expect(m.procurementProps.mock.calls[0][0]).toMatchObject({
      initialFilter: { submittedFrom: from, submittedTo: to },
      ignoredParams: [],
    });
  });

  it('status combines with the submit window', async () => {
    render(
      await ScmProcurementPage({
        searchParams: Promise.resolve({
          status: 'RECEIVED',
          submittedFrom: '2026-10-01T00:00:00Z',
        }),
      }),
    );
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {
      status: 'RECEIVED',
      submittedFrom: '2026-10-01T00:00:00Z',
    });
  });

  it('the card link (window + excludeStatus=CANCELED) is applied whole', async () => {
    const from = '2026-10-01T00:00:00+09:00';
    const to = '2026-10-24T14:05:00+09:00';
    render(
      await ScmProcurementPage({
        searchParams: Promise.resolve({
          submittedFrom: from,
          submittedTo: to,
          excludeStatus: 'CANCELED',
        }),
      }),
    );
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {
      submittedFrom: from,
      submittedTo: to,
      excludeStatus: 'CANCELED',
    });
    expect(m.procurementProps.mock.calls[0][0]).toMatchObject({
      initialFilter: { excludeStatus: 'CANCELED' },
      ignoredParams: [],
    });
  });

  it('malformed values are dropped (never sent) and named for the screen', async () => {
    render(
      await ScmProcurementPage({
        searchParams: Promise.resolve({
          status: 'NOPE',
          submittedFrom: '2026-10-01',
          submittedTo: 'tomorrow',
          excludeStatus: 'CANCELLED',
        }),
      }),
    );
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {});
    expect(m.procurementProps.mock.calls[0][0]).toMatchObject({
      ignoredParams: ['status', 'submittedFrom', 'submittedTo', 'excludeStatus'],
    });
  });

  it('an un-encoded `+` (arrives as a space) is repaired, not rejected', async () => {
    render(
      await ScmProcurementPage({
        searchParams: Promise.resolve({ submittedFrom: '2026-10-01T00:00:00 09:00' }),
      }),
    );
    expect(m.getScmProcurementState).toHaveBeenCalledWith(true, {
      submittedFrom: '2026-10-01T00:00:00+09:00',
    });
  });
});
