import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * TASK-PC-FE-118 — `/operators` parallelises the independent success-path
 * fetches that previously ran as a waterfall: the catalog (`getCatalog` — the
 * 업무 권한 dialog's subscribed-domain source), the caller's own `/me`
 * (`getSelfOrNull`), and (feat/iam-grantable-roles-filter) the caller's
 * grantable role set (`getGrantableRolesOrNull`). All three run only past the
 * noTenant/permissionError/degraded gates, so this is a pure post-gate
 * parallelisation (no speculative authorized fetch).
 *
 * Concurrency proof: with `getCatalog` left pending, the page still calls
 * `getSelfOrNull` + `getGrantableRolesOrNull` (all promises are created
 * before any await).
 *
 * TASK-PC-FE-354 — the platform-scope judgement (`isPlatformOperator`) now
 * comes from `/me`: `homeTenantId === '*'` + `operator.manage` (the
 * `new-tenant/page.tsx` precedent). It used to be
 * `selectableTenants(catalog).includes('*')`, mocked here as `['wms','*']` —
 * a shape the registry never returns (`*` is not a switcher entry), which is
 * what kept the «platform hint = true» assertion green while the live form
 * never rendered. That fixture is gone; `/me` is mocked with the producer's
 * real summary shape.
 */

const getOperatorsListState = vi.fn();
const getSelfOrNull = vi.fn();
const getGrantableRolesOrNull = vi.fn();
const getCatalog = vi.fn();

/** A `GET /api/admin/me` body in the producer's shape (OperatorSummarySchema). */
function me(over: Record<string, unknown> = {}) {
  return {
    operatorId: 'op-self',
    email: 'self@x.com',
    displayName: 'Self',
    status: 'ACTIVE',
    roles: ['TENANT_ADMIN'],
    createdAt: '2026-01-01T00:00:00Z',
    homeTenantId: 'demo-corp',
    memberships: [{ tenantId: 'demo-corp', roles: ['TENANT_ADMIN'] }],
    ...over,
  };
}
/** A platform-scope operator: home `'*'`, SUPER_ADMIN on `'*'`, no memberships. */
const STAR_OWNER = me({
  operatorId: 'op-star',
  homeTenantId: '*',
  roles: ['SUPER_ADMIN'],
  memberships: [],
});

vi.mock('@/features/operators', () => ({
  getOperatorsListState: (a: unknown) => getOperatorsListState(a),
  OperatorsScreen: ({
    isPlatformOperator,
    selfOperatorId,
    grantableRoles,
  }: {
    isPlatformOperator: boolean;
    selfOperatorId: string | null;
    grantableRoles: string[] | null;
  }) => (
    <div
      data-testid="operators-screen"
      data-platform={String(isPlatformOperator)}
      data-self={selfOperatorId ?? ''}
      data-grantable-roles={(grantableRoles ?? []).join(',')}
    />
  ),
}));
vi.mock('@/features/operators/api/operators-api', () => ({
  getGrantableRolesOrNull: () => getGrantableRolesOrNull(),
}));
vi.mock('@/shared/api/iam-operators-read', () => ({
  getSelfOrNull: () => getSelfOrNull(),
}));
vi.mock('@/features/catalog', () => ({ getCatalog: () => getCatalog() }));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

/**
 * TASK-PC-FE-301 — the no-tenant branch renders `NoTenantNotice`, which
 * resolves its OWN judgement via `noTenantNoticeKind`
 * (`@/shared/lib/active-tenant-default`) through `fetchRegistry` — a
 * DIFFERENT seam than `@/features/catalog`'s `getCatalog` mocked above.
 * Defaults to 'select' so the pre-301 no-tenant test below (which only
 * asserts the gate's testid, not its copy) is unaffected.
 */
let noTenantNoticeKindResult: 'zero' | 'select' = 'select';
vi.mock('@/shared/lib/active-tenant-default', () => ({
  noTenantNoticeKind: async () => noTenantNoticeKindResult,
}));

import OperatorsPage from '@/app/(console)/operators/page';

const SUCCESS_STATE = {
  page: { content: [], page: 0, size: 20, totalElements: 0 },
  noTenant: false,
  permissionError: null,
  degraded: false,
};
const tick = () => new Promise((r) => setTimeout(r, 0));

async function renderedScreen() {
  const ui = await OperatorsPage();
  const { getByTestId } = render(ui);
  return getByTestId('operators-screen');
}

beforeEach(() => {
  getOperatorsListState.mockReset();
  getSelfOrNull.mockReset();
  getGrantableRolesOrNull.mockReset();
  getGrantableRolesOrNull.mockResolvedValue(null);
  getCatalog.mockReset();
  noTenantNoticeKindResult = 'select';
});

describe('OperatorsPage — parallel post-gate SSR fetch (TASK-PC-FE-118)', () => {
  it('starts the /me + grantable-roles fetches concurrently with the catalog fetch (no waterfall)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockReturnValue(new Promise(() => {})); // hangs forever
    getSelfOrNull.mockReturnValue(new Promise(() => {})); // hangs forever
    getGrantableRolesOrNull.mockResolvedValue(['TENANT_ADMIN']);

    void OperatorsPage();
    await tick();

    expect(getCatalog).toHaveBeenCalledTimes(1);
    expect(getSelfOrNull).toHaveBeenCalledTimes(1);
    expect(getGrantableRolesOrNull).toHaveBeenCalledTimes(1);
  });

  it('renders the screen with the self id (from /me) + grantable roles on the success path', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOrNull.mockResolvedValue(me());
    getGrantableRolesOrNull.mockResolvedValue(['TENANT_ADMIN', 'SUPPORT_LOCK']);

    const el = await renderedScreen();
    expect(el).toHaveAttribute('data-self', 'op-self');
    expect(el).toHaveAttribute('data-grantable-roles', 'TENANT_ADMIN,SUPPORT_LOCK');
  });

  it('passes null through when the grantable-roles fetch fails (fail-graceful wrapper)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOrNull.mockResolvedValue(me());
    getGrantableRolesOrNull.mockResolvedValue(null);

    const el = await renderedScreen();
    expect(el).toHaveAttribute('data-grantable-roles', '');
  });

  it('keeps the independent /me result when the catalog fetch rejects (registry down)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockRejectedValue(new Error('registry down'));
    getSelfOrNull.mockResolvedValue(STAR_OWNER);

    const el = await renderedScreen();
    expect(el).toHaveAttribute('data-self', 'op-star');
    // The platform judgement no longer depends on the registry at all.
    expect(el).toHaveAttribute('data-platform', 'true');
  });

  it('does not fetch catalog/self on the no-tenant gate (gate decided before the parallel block)', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: true,
      permissionError: null,
      degraded: false,
    });

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operators-no-tenant')).toBeInTheDocument();
    expect(getCatalog).not.toHaveBeenCalled();
    expect(getSelfOrNull).not.toHaveBeenCalled();
    expect(getGrantableRolesOrNull).not.toHaveBeenCalled();
  });

  it('🔴🔴 TASK-PC-FE-301 — 0 selectable tenants → the no-reachable-tenant notice (uses the SAME shared judgement as DomainTenantGate)', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: true,
      permissionError: null,
      degraded: false,
    });
    noTenantNoticeKindResult = 'zero';

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    const el = getByTestId('operators-no-tenant');
    expect(el).toHaveTextContent('이 계정에는 접근 가능한 테넌트가 없습니다');
    expect(el).not.toHaveTextContent('테넌트를 먼저 선택하세요');
  });

  it('TASK-PC-FE-301 control — ≥2 selectable, none chosen → the ORIGINAL «select a tenant» text, unchanged', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: true,
      permissionError: null,
      degraded: false,
    });
    noTenantNoticeKindResult = 'select';

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    const el = getByTestId('operators-no-tenant');
    expect(el).toHaveTextContent('테넌트를 먼저 선택하세요');
    expect(el).not.toHaveTextContent('접근 가능한 테넌트가 없습니다');
  });

  it('does not fetch catalog/self on the permission-denied gate', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: false,
      permissionError: { code: 'TENANT_SCOPE_DENIED' },
      degraded: false,
    });

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operators-permission-denied')).toBeInTheDocument();
    expect(getCatalog).not.toHaveBeenCalled();
    expect(getSelfOrNull).not.toHaveBeenCalled();
    expect(getGrantableRolesOrNull).not.toHaveBeenCalled();
  });
});

describe('OperatorsPage — platform-scope judgement from /me (TASK-PC-FE-354 AC-2)', () => {
  beforeEach(() => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockResolvedValue({ products: [] });
  });

  it("home '*' + operator.manage (SUPER_ADMIN) ⇒ platform (the «플랫폼 운영자 등록» form is offered)", async () => {
    getSelfOrNull.mockResolvedValue(STAR_OWNER);
    expect(await renderedScreen()).toHaveAttribute('data-platform', 'true');
  });

  it('a company operator (home demo-corp, TENANT_ADMIN) ⇒ NOT platform', async () => {
    getSelfOrNull.mockResolvedValue(me());
    expect(await renderedScreen()).toHaveAttribute('data-platform', 'false');
  });

  it("home '*' but no operator.manage (demo-platform: no role rows) ⇒ NOT platform", async () => {
    getSelfOrNull.mockResolvedValue(me({ homeTenantId: '*', roles: [], memberships: [] }));
    expect(await renderedScreen()).toHaveAttribute('data-platform', 'false');
  });

  it('SUPER_ADMIN whose homeTenantId is ABSENT (optional field) ⇒ NOT platform', async () => {
    const body: Record<string, unknown> = me({ roles: ['SUPER_ADMIN'] });
    delete body.homeTenantId;
    getSelfOrNull.mockResolvedValue(body);
    expect(await renderedScreen()).toHaveAttribute('data-platform', 'false');
  });

  it('a failed /me (null) ⇒ NOT platform, and no self id', async () => {
    getSelfOrNull.mockResolvedValue(null);
    const el = await renderedScreen();
    expect(el).toHaveAttribute('data-platform', 'false');
    expect(el).toHaveAttribute('data-self', '');
  });
});
