import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * TASK-PC-FE-118 — `/operators` parallelises the independent success-path
 * fetches that previously ran as a waterfall: the create-form tenant options
 * (`getCatalog`), the caller's own operatorId (`getSelfOperatorIdOrNull`), and
 * (feat/iam-grantable-roles-filter) the caller's grantable role set
 * (`getGrantableRolesOrNull`). All three run only past the
 * noTenant/permissionError/degraded gates, so this is a pure post-gate
 * parallelisation (no speculative authorized fetch).
 *
 * Concurrency proof: with `getCatalog` left pending, the new shape still calls
 * `getSelfOperatorIdOrNull` + `getGrantableRolesOrNull` (all promises are
 * created before `await catalog`). The old waterfall would not call the
 * others until catalog resolved — so they'd stay at 0 calls while catalog
 * hangs.
 */

const getOperatorsListState = vi.fn();
const getSelfOperatorIdOrNull = vi.fn();
const getGrantableRolesOrNull = vi.fn();
const getCatalog = vi.fn();
const selectableTenants = vi.fn();

vi.mock('@/features/operators', () => ({
  getOperatorsListState: (a: unknown) => getOperatorsListState(a),
  OperatorsScreen: ({
    tenantOptions,
    isPlatformOperator,
    selfOperatorId,
    grantableRoles,
  }: {
    tenantOptions: string[];
    isPlatformOperator: boolean;
    selfOperatorId: string | null;
    grantableRoles: string[] | null;
  }) => (
    <div
      data-testid="operators-screen"
      data-tenants={tenantOptions.join(',')}
      data-platform={String(isPlatformOperator)}
      data-self={selfOperatorId ?? ''}
      data-grantable-roles={(grantableRoles ?? []).join(',')}
    />
  ),
}));
vi.mock('@/features/operators/api/operators-api', () => ({
  getSelfOperatorIdOrNull: () => getSelfOperatorIdOrNull(),
  getGrantableRolesOrNull: () => getGrantableRolesOrNull(),
}));
vi.mock('@/features/catalog', () => ({ getCatalog: () => getCatalog() }));
vi.mock('@/features/tenant', () => ({
  selectableTenants: (p: unknown) => selectableTenants(p),
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import OperatorsPage from '@/app/(console)/operators/page';

const SUCCESS_STATE = {
  page: { content: [], page: 0, size: 20, totalElements: 0 },
  noTenant: false,
  permissionError: null,
  degraded: false,
};
const tick = () => new Promise((r) => setTimeout(r, 0));

beforeEach(() => {
  getOperatorsListState.mockReset();
  getSelfOperatorIdOrNull.mockReset();
  getGrantableRolesOrNull.mockReset();
  getGrantableRolesOrNull.mockResolvedValue(null);
  getCatalog.mockReset();
  selectableTenants.mockReset();
  selectableTenants.mockReturnValue(['wms', '*']);
});

describe('OperatorsPage — parallel post-gate SSR fetch (TASK-PC-FE-118)', () => {
  it('starts the self-operator + grantable-roles fetches concurrently with the catalog fetch (no waterfall)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockReturnValue(new Promise(() => {})); // hangs forever
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');
    getGrantableRolesOrNull.mockResolvedValue(['TENANT_ADMIN']);

    // Page never settles (it awaits the hanging catalog); we only assert that
    // self + grantable-roles were started despite catalog still pending.
    void OperatorsPage();
    await tick();

    expect(getCatalog).toHaveBeenCalledTimes(1);
    // Waterfall regression: self would be 0 until catalog resolved.
    expect(getSelfOperatorIdOrNull).toHaveBeenCalledTimes(1);
    expect(getGrantableRolesOrNull).toHaveBeenCalledTimes(1);
  });

  it('renders the screen with tenant options + self id + grantable roles on the success path', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');
    getGrantableRolesOrNull.mockResolvedValue(['TENANT_ADMIN', 'SUPPORT_LOCK']);

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    const el = getByTestId('operators-screen');
    expect(el).toHaveAttribute('data-tenants', 'wms'); // '*' filtered out
    expect(el).toHaveAttribute('data-platform', 'true');
    expect(el).toHaveAttribute('data-self', 'op-self');
    expect(el).toHaveAttribute(
      'data-grantable-roles',
      'TENANT_ADMIN,SUPPORT_LOCK',
    );
  });

  it('passes null through when the grantable-roles fetch fails (fail-graceful wrapper)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');
    getGrantableRolesOrNull.mockResolvedValue(null);

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    // The mock renders `grantableRoles ?? []` joined — null renders as ''.
    expect(getByTestId('operators-screen')).toHaveAttribute(
      'data-grantable-roles',
      '',
    );
  });

  it('keeps the independent self result when the catalog fetch rejects (registry down → empty options)', async () => {
    getOperatorsListState.mockResolvedValue(SUCCESS_STATE);
    getCatalog.mockRejectedValue(new Error('registry down'));
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    const el = getByTestId('operators-screen');
    expect(el).toHaveAttribute('data-tenants', ''); // empty options on failure
    expect(el).toHaveAttribute('data-platform', 'false');
    // The self promise is independent — its result survives the catalog reject.
    expect(el).toHaveAttribute('data-self', 'op-self');
  });

  it('does not fetch catalog/self on the no-tenant gate (gate decided before the parallel block)', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: true,
      permissionError: null,
      degraded: false,
    });
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operators-no-tenant')).toBeInTheDocument();
    expect(getCatalog).not.toHaveBeenCalled();
    expect(getSelfOperatorIdOrNull).not.toHaveBeenCalled();
    expect(getGrantableRolesOrNull).not.toHaveBeenCalled();
  });

  it('does not fetch catalog/self on the permission-denied gate', async () => {
    getOperatorsListState.mockResolvedValue({
      page: null,
      noTenant: false,
      permissionError: { code: 'TENANT_SCOPE_DENIED' },
      degraded: false,
    });
    getCatalog.mockResolvedValue({ products: [] });
    getSelfOperatorIdOrNull.mockResolvedValue('op-self');

    const ui = await OperatorsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operators-permission-denied')).toBeInTheDocument();
    expect(getCatalog).not.toHaveBeenCalled();
    expect(getSelfOperatorIdOrNull).not.toHaveBeenCalled();
    expect(getGrantableRolesOrNull).not.toHaveBeenCalled();
  });
});
