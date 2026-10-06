import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-117 — `/dashboards/overview` SSR fetch is parallelised: the
 * domain-health fan-out is fired up-front, concurrently with the
 * operator-overview fetch, instead of sequentially in the success branch.
 *
 * TASK-PC-FE-310 extends the same concurrency rule to the catalog leg (the
 * old `/console` page's `getCatalog()`), which now also lives on this page —
 * see `overview-catalog-fold.test.tsx` for the fold-specific DOM assertions
 * (grid presence, `<details>` open/closed, health dots, 401 redirect, h1
 * count). This file stays scoped to the ORIGINAL concurrency proof, extended
 * to cover the third fetcher.
 *
 * The decisive concurrency assertion exploits async-function semantics: the
 * page body runs synchronously up to its first `await`. With the parallel
 * shape, ALL THREE of `getDomainHealthState()`, `getCatalog()` and
 * `getOperatorOverviewState()` are invoked before that first await resolves
 * — so immediately after calling the page (without awaiting), all three
 * mocks are already called. A regression to a waterfall would only call a
 * later fetcher AFTER an earlier promise resolved.
 */

const getOperatorOverviewState = vi.fn();
const getDomainHealthState = vi.fn();
const getCatalog = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});

vi.mock('@/features/operator-overview', () => ({
  getOperatorOverviewState: () => getOperatorOverviewState(),
  OperatorOverviewScreen: () => <div data-testid="overview-screen" />,
}));
vi.mock('@/features/domain-health', () => ({
  getDomainHealthState: () => getDomainHealthState(),
  DomainHealthSummaryCard: () => <div data-testid="health-summary" />,
  deriveHealthByDomain: () => ({ healthByDomain: {}, healthNotice: 'ok' }),
}));
vi.mock('@/features/catalog', () => ({
  getCatalog: () => getCatalog(),
  ServiceCatalog: () => <div data-testid="service-catalog" />,
}));
// Not under test here (see `overview-catalog-fold.test.tsx`) — mocked so an
// empty catalog in the no-tenant branch below never reaches the REAL
// `noTenantNoticeKind()` (its own unmocked registry fetch).
vi.mock('@/widgets/no-tenant-notice', () => ({
  NoTenantNotice: () => <div data-testid="operator-overview-no-tenant" />,
}));
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import OperatorOverviewPage from '@/app/(console)/dashboards/overview/page';

const SUCCESS_STATE = {
  overview: { cards: [] },
  noTenant: false,
  unauthorized: false,
  bffUnavailable: false,
};

beforeEach(() => {
  getOperatorOverviewState.mockReset();
  getDomainHealthState.mockReset();
  getCatalog.mockReset();
  redirect.mockClear();
});

describe('OperatorOverviewPage — parallel SSR fetch (TASK-PC-FE-117 / TASK-PC-FE-310)', () => {
  it('fires the domain-health AND catalog fetches concurrently with the overview fetch (no waterfall)', () => {
    getOperatorOverviewState.mockReturnValue(new Promise(() => {}));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));
    getCatalog.mockReturnValue(new Promise(() => {}));

    // Call without awaiting: runs synchronously to the first `await`.
    void OperatorOverviewPage();

    expect(getOperatorOverviewState).toHaveBeenCalledTimes(1);
    // A waterfall regression would leave either of these at 0 until an
    // earlier promise resolved.
    expect(getDomainHealthState).toHaveBeenCalledTimes(1);
    expect(getCatalog).toHaveBeenCalledTimes(1);
  });

  it('renders both the overview screen and the health summary on the success path', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockResolvedValue({
      health: { cards: [] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });
    getCatalog.mockResolvedValue({ products: [], degraded: false });

    const ui = await OperatorOverviewPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('overview-screen')).toBeInTheDocument();
    expect(getByTestId('health-summary')).toBeInTheDocument();
  });

  it('renders the no-tenant gate unchanged; the speculative health promise does not affect output', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });
    // Speculative health resolves but must NOT influence the gated render.
    getDomainHealthState.mockResolvedValue({
      health: { cards: [] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });
    getCatalog.mockResolvedValue({ products: [], degraded: false });

    const ui = await OperatorOverviewPage();
    const { getByRole, queryByTestId } = render(ui);
    expect(
      getByRole('heading', { level: 1, name: '운영자 통합 개요' }),
    ).toBeInTheDocument();
    expect(queryByTestId('health-summary')).toBeNull();
    expect(queryByTestId('overview-screen')).toBeNull();
  });

  it('renders the bff-unavailable banner unchanged on a whole-overview failure', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    });
    getDomainHealthState.mockResolvedValue({
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    });
    getCatalog.mockResolvedValue({ products: [], degraded: false });

    const ui = await OperatorOverviewPage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('operator-overview-bff-unavailable')).toBeInTheDocument();
    expect(queryByTestId('health-summary')).toBeNull();
  });

  it('redirects to /login on unauthorized; the un-awaited health/catalog promises raise no unhandled rejection', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: false,
      unauthorized: true,
      bffUnavailable: false,
    });
    // REJECTING promises on the gated path must not crash the page — neither
    // is ever awaited once `state.unauthorized` short-circuits via redirect.
    getDomainHealthState.mockReturnValue(Promise.reject(new Error('ignored')));
    getCatalog.mockReturnValue(Promise.reject(new Error('ignored')));

    await expect(OperatorOverviewPage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login?error=session_expired');
  });

  it('redirects to /login on a 401 from the catalog leg alone (overview itself healthy)', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockReturnValue(new Promise(() => {}));
    getCatalog.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'nope'));

    await expect(OperatorOverviewPage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login?error=session_expired');
  });

  it('re-throws a non-401 catalog error (no redirect) — getCatalog itself only throws on 401 in prod', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockReturnValue(new Promise(() => {}));
    getCatalog.mockRejectedValue(new ApiError(500, 'BOOM', 'server'));

    await expect(OperatorOverviewPage()).rejects.toThrow('server');
    expect(redirect).not.toHaveBeenCalled();
  });
});
