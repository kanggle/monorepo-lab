import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * TASK-PC-FE-117 — `/dashboards/overview` SSR fetch is parallelised: the
 * domain-health fan-out is fired up-front, concurrently with the
 * operator-overview fetch, instead of sequentially in the success branch.
 *
 * The decisive concurrency assertion exploits async-function semantics: the
 * page body runs synchronously up to its first `await`. With the parallel
 * shape, BOTH `getDomainHealthState()` and `getOperatorOverviewState()` are
 * invoked before that first await resolves — so immediately after calling the
 * page (without awaiting), both mocks are already called. The old sequential
 * shape would only call `getDomainHealthState` AFTER the overview promise
 * resolved, so this test fails on a regression to the waterfall.
 */

const getOperatorOverviewState = vi.fn();
const getDomainHealthState = vi.fn();
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
  redirect.mockClear();
});

describe('OperatorOverviewPage — parallel SSR fetch (TASK-PC-FE-117)', () => {
  it('fires the domain-health fetch concurrently with the overview fetch (no waterfall)', () => {
    // Both fetchers return pending promises we never resolve here — we only
    // assert that BOTH were invoked synchronously, before any await resolves.
    getOperatorOverviewState.mockReturnValue(new Promise(() => {}));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    // Call without awaiting: runs synchronously to the first `await`.
    void OperatorOverviewPage();

    expect(getOperatorOverviewState).toHaveBeenCalledTimes(1);
    // The waterfall regression would leave this at 0 until overview resolves.
    expect(getDomainHealthState).toHaveBeenCalledTimes(1);
  });

  it('renders both the overview screen and the health summary on the success path', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockResolvedValue({
      health: { cards: [] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });

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

    const ui = await OperatorOverviewPage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('operator-overview-no-tenant')).toBeInTheDocument();
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

    const ui = await OperatorOverviewPage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('operator-overview-bff-unavailable')).toBeInTheDocument();
    expect(queryByTestId('health-summary')).toBeNull();
  });

  it('redirects to /login on unauthorized; the un-awaited health promise raises no unhandled rejection', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: false,
      unauthorized: true,
      bffUnavailable: false,
    });
    // A REJECTING health promise on the gated path must not crash the page —
    // it is never awaited, and getDomainHealthState never throws in prod. We
    // simulate a settled-but-ignored rejection to prove the gate is unaffected.
    getDomainHealthState.mockReturnValue(Promise.reject(new Error('ignored')));

    await expect(OperatorOverviewPage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login');
  });
});
