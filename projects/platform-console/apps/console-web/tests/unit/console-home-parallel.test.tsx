import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-117 — `/console` (catalog home) SSR fetch is parallelised: the
 * catalog (IAM registry) and the per-domain health fan-out are fired
 * concurrently instead of `await getCatalog(); await getDomainHealthState();`.
 *
 * Same concurrency proof as the overview sibling: the async page body runs
 * synchronously to its first `await`, by which point BOTH fetchers must have
 * been invoked. The old waterfall would only call `getDomainHealthState`
 * after the catalog promise resolved.
 */

const getCatalog = vi.fn();
const getDomainHealthState = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});

vi.mock('@/features/catalog', () => ({
  getCatalog: () => getCatalog(),
  ServiceCatalog: ({
    healthByDomain,
    healthState,
  }: {
    healthByDomain: Record<string, string>;
    healthState?: string;
  }) => (
    <div
      data-testid="service-catalog"
      data-dots={Object.keys(healthByDomain).join(',')}
      // TASK-MONO-711 ③ — «점이 없다» 와 «왜 없는지» 는 다른 값이다. 이 모의는
      // 둘 다 내보내야 «점 0개 + 사유 없음» 이라는 옛 상태가 초록이 될 수 없다.
      data-health-state={healthState}
    />
  ),
}));
vi.mock('@/features/domain-health', () => ({
  getDomainHealthState: () => getDomainHealthState(),
  healthTone: () => 'ok',
}));
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));

import ConsoleHomePage from '@/app/(console)/console/page';

beforeEach(() => {
  getCatalog.mockReset();
  getDomainHealthState.mockReset();
  redirect.mockClear();
});

describe('ConsoleHomePage — parallel SSR fetch (TASK-PC-FE-117)', () => {
  it('fires the catalog and domain-health fetches concurrently (no waterfall)', () => {
    getCatalog.mockReturnValue(new Promise(() => {}));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    void ConsoleHomePage();

    expect(getCatalog).toHaveBeenCalledTimes(1);
    // Regression to the waterfall would leave this at 0 until catalog resolves.
    expect(getDomainHealthState).toHaveBeenCalledTimes(1);
  });

  it('renders the catalog with per-domain health dots on the success path', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: { cards: [{ domain: 'wms' }, { domain: 'finance' }] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    const el = getByTestId('service-catalog');
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute('data-dots', 'wms,finance');
    // 대조군 — 성공 경로는 사유를 붙이지 않는다. 없으면 «항상 unavailable» 이 통과한다.
    expect(el).toHaveAttribute('data-health-state', 'ok');
  });

  it('renders the catalog without dots when health degrades (null health)', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    const el = getByTestId('service-catalog');
    expect(el).toHaveAttribute('data-dots', '');
    // 🔴 TASK-MONO-711 ③ — 점이 없는 것만으로는 부족하다. 실패는 **사유를 들고**
    //    내려가야 화면이 그것을 마커로 그릴 수 있다. 이 단언이 없으면 「원소가
    //    사라지는 것이 유일한 자국」이라는 옛 동작이 그대로 초록이다.
    expect(el).toHaveAttribute('data-health-state', 'unavailable');
  });

  it('distinguishes «no active tenant» from a failed health leg (TASK-MONO-711 ③)', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    const el = getByTestId('service-catalog');
    expect(el).toHaveAttribute('data-dots', '');
    // 🔵 둘 다 «점 0개» 지만 같은 값이면 안 된다 — 이 페이지에서 테넌트 미선택은
    //    정상 상태이고, 저하로 세면 촬영이 정상 화면을 후보에서 뺀다.
    expect(el).toHaveAttribute('data-health-state', 'no-tenant');
  });

  it('treats a 401 on the health leg as unavailable, not as «no tenant»', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: null,
      noTenant: false,
      unauthorized: true,
      bffUnavailable: false,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    expect(getByTestId('service-catalog')).toHaveAttribute(
      'data-health-state',
      'unavailable',
    );
  });

  it('redirects to /login on catalog 401; the un-awaited health promise raises no unhandled rejection', async () => {
    getCatalog.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'nope'));
    getDomainHealthState.mockReturnValue(Promise.reject(new Error('ignored')));

    await expect(ConsoleHomePage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login?error=session_expired');
  });

  it('re-throws a non-401 catalog error (no redirect)', async () => {
    getCatalog.mockRejectedValue(new ApiError(500, 'BOOM', 'server'));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    await expect(ConsoleHomePage()).rejects.toThrow('server');
    expect(redirect).not.toHaveBeenCalled();
  });
});
