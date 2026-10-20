import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';

/**
 * TASK-PC-FE-354 — the `'*'` paths of `/operators`, through `OperatorsScreen`:
 *
 *   1. the «모든 테넌트» view toggle — a platform-scope caller only; it lists
 *      with `tenantId='*'` (the BFF's existing override);
 *   2. in that view: a company row hides the active-tenant actions, a `'*'`
 *      row hides 조직 스코프 / 내보내기, and its 역할 변경 confirm names '*';
 *   3. the 업무 권한 dialog's header-selection matrix, seen as the BFF body
 *      (`allTenants: true` ⇔ `X-Tenant-Id: *` — pinned server-side in
 *      `operators-domain-access-tenant-header.test.ts`):
 *        '*' target + '*' caller + «모든 테넌트» choice → allTenants
 *        '*' target + '*' caller + «현재 테넌트» choice → active
 *        '*' target + '*' caller, revoke of the '*' source → allTenants
 *        '*' target + '*' caller, revoke of the T source   → active
 *        company target + '*' caller                        → active, no choice
 *        '*' target + non-'*' caller                        → active, no choice
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const COMPANY_OP = {
  operatorId: 'op-1',
  email: 'alice@x.com',
  displayName: 'Alice',
  status: 'ACTIVE',
  roles: ['SUPPORT_LOCK'],
  createdAt: '2026-01-01',
  homeTenantId: 'demo-corp',
};
const STAR_OP = {
  operatorId: 'op-star',
  email: 'star@x.com',
  displayName: 'Star',
  status: 'ACTIVE',
  roles: ['SUPER_ADMIN'],
  createdAt: '2026-01-01',
  homeTenantId: '*',
};

function page(content: unknown[]): OperatorPage {
  return {
    content: content as OperatorPage['content'],
    totalElements: content.length,
    page: 0,
    size: 20,
    totalPages: 1,
  };
}

const TENANT_PAGE = page([COMPANY_OP]);
const ALL_TENANTS_PAGE = page([COMPANY_OP, STAR_OP]);

const EMPTY_INVITATIONS = { content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 };

/** A '*' target holding the same preset on its T row AND its '*' row. */
const STAR_ACCESS = {
  operatorId: 'op-star',
  tenantId: 'demo-corp',
  roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
  sets: [
    {
      setId: 'preset-1',
      name: '재고 조회',
      systemKey: 'wms-inventory-read',
      sources: [
        { source: 'DIRECT', tenantId: 'demo-corp', grantedAt: '2026-10-01T00:00:00Z', grantedBy: 'op-9' },
        { source: 'MIGRATION', tenantId: '*', grantedAt: '2026-10-01T00:00:00Z', grantedBy: null },
      ],
    },
  ],
};
const COMPANY_ACCESS = {
  operatorId: 'op-1',
  tenantId: 'demo-corp',
  roles: [],
  sets: [
    {
      setId: 's1',
      name: '재고팀',
      systemKey: null,
      sources: [
        { source: 'DIRECT', tenantId: 'demo-corp', grantedAt: '2026-10-01T00:00:00Z', grantedBy: 'op-9' },
      ],
    },
  ],
};

const GRANTABLE_SETS = {
  items: [
    { setId: 'preset-1', systemKey: 'wms-inventory-read', readOnly: true, name: '재고 조회', domains: ['wms'] },
    { setId: 'tenant-set', systemKey: null, readOnly: false, name: '우리 팀', domains: ['wms'] },
  ],
};

function ok(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function routedFetch() {
  return vi.fn(async (url: string, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (u.startsWith('/api/operator-invitations')) return ok(EMPTY_INVITATIONS);
    if (method === 'GET' && u.startsWith('/api/operators?')) {
      return ok(u.includes('tenantId=*') ? ALL_TENANTS_PAGE : TENANT_PAGE);
    }
    if (method === 'GET' && u === '/api/operators/op-star/domain-access') return ok(STAR_ACCESS);
    if (method === 'GET' && u === '/api/operators/op-1/domain-access') return ok(COMPANY_ACCESS);
    if (method === 'GET' && u === '/api/domain-permission-sets') return ok(GRANTABLE_SETS);
    if (method === 'POST') {
      return new Response(
        JSON.stringify({
          operatorId: 'x',
          tenantId: 'x',
          setId: 'x',
          source: 'DIRECT',
          grantedAt: '2026-10-20T00:00:00Z',
          grantedBy: null,
          selfGrant: false,
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      );
    }
    return new Response(null, { status: 204 });
  });
}

let fetchMock: ReturnType<typeof routedFetch>;

beforeEach(() => {
  vi.unstubAllGlobals();
  fetchMock = routedFetch();
  vi.stubGlobal('fetch', fetchMock);
});

function listCalls() {
  return fetchMock.mock.calls
    .map(([u]) => String(u))
    .filter((u) => u.startsWith('/api/operators?'));
}

function mutationBodies(method: 'POST' | 'DELETE', prefix: string) {
  return fetchMock.mock.calls
    .filter(
      ([u, init]) =>
        String(u).startsWith(prefix) &&
        ((init as RequestInit | undefined)?.method ?? 'GET').toUpperCase() === method,
    )
    .map(([, init]) => JSON.parse((init as RequestInit).body as string));
}

function renderScreen(opts: { isPlatformOperator: boolean; initial?: OperatorPage }) {
  render(
    <OperatorsScreen
      initial={opts.initial ?? TENANT_PAGE}
      activeTenant="demo-corp"
      isPlatformOperator={opts.isPlatformOperator}
      subscribedDomains={['wms']}
    />,
    { wrapper: wrapper() },
  );
}

async function switchToAllTenants(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByTestId('operators-view-all-tenants'));
  await screen.findByTestId('operator-row-op-star');
}

async function submitReason(user: ReturnType<typeof userEvent.setup>, reason: string) {
  const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
  await user.type(within(dialog).getByTestId('operator-domain-access-reason-input'), reason);
  await user.click(within(dialog).getByTestId('operator-domain-access-reason-submit'));
  return dialog;
}

describe('«모든 테넌트» view toggle', () => {
  it('a non-platform caller sees no toggle and the list is never asked for tenantId=*', async () => {
    renderScreen({ isPlatformOperator: false });
    expect(screen.queryByTestId('operators-view-toggle')).toBeNull();
    expect(screen.getByTestId('operator-row-op-1')).toBeInTheDocument();
    expect(listCalls().some((u) => u.includes('tenantId='))).toBe(false);
  });

  it('a platform caller sees the toggle; «모든 테넌트» lists with tenantId=* and shows the \'*\' operator', async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    expect(screen.getByTestId('operators-view-toggle')).toBeInTheDocument();
    // Default view: the seeded active-tenant page, no '*' row, no note.
    expect(screen.queryByTestId('operator-row-op-star')).toBeNull();
    expect(screen.queryByTestId('operators-all-tenants-note')).toBeNull();

    await switchToAllTenants(user);
    expect(listCalls().some((u) => u.includes('tenantId=*'))).toBe(true);
    expect(screen.getByTestId('operators-all-tenants-note')).toBeInTheDocument();

    // And back: the active-tenant list again.
    await user.click(screen.getByTestId('operators-view-tenant'));
    await waitFor(() => expect(screen.queryByTestId('operator-row-op-star')).toBeNull());
  });
});

describe('row actions in the «모든 테넌트» view', () => {
  it('a company row hides the active-tenant actions; status + profile stay', async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await switchToAllTenants(user);
    for (const a of ['edit-roles', 'org-scope', 'domain-access', 'unassign']) {
      expect(screen.queryByTestId(`action-${a}-op-1`)).toBeNull();
    }
    expect(screen.getByTestId('action-status-op-1')).toBeInTheDocument();
    expect(screen.getByTestId('action-edit-profile-op-1')).toBeInTheDocument();
  });

  it("a '*' row keeps 역할 변경 + 업무 권한, hides 조직 스코프 + 내보내기", async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await switchToAllTenants(user);
    expect(screen.getByTestId('action-edit-roles-op-star')).toBeInTheDocument();
    expect(screen.getByTestId('action-domain-access-op-star')).toBeInTheDocument();
    expect(screen.queryByTestId('action-org-scope-op-star')).toBeNull();
    expect(screen.queryByTestId('action-unassign-op-star')).toBeNull();
  });

  it("the '*' row's 역할 변경 confirm names «모든 테넌트(*)», never «이 회사(demo-corp)»", async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await switchToAllTenants(user);
    await user.click(screen.getByTestId('action-edit-roles-op-star'));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('모든 테넌트(*)');
    expect(dialog).not.toHaveTextContent('이 회사');
    expect(dialog).not.toHaveTextContent('다른 회사');
    expect(dialog).toHaveTextContent('역할 (모든 테넌트(*) — 전체 교체)');
  });

  it("control — a company row's 역할 변경 confirm still names «이 회사(demo-corp)»", async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await user.click(screen.getByTestId('action-edit-roles-op-1'));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('이 회사(demo-corp)에서의 역할');
    expect(dialog).toHaveTextContent('역할 (이 회사 — 전체 교체');
    expect(dialog).not.toHaveTextContent('모든 테넌트(*)');
  });

  it("the '*' row's 정지 confirm does not claim «여러 회사에 소속»", async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await switchToAllTenants(user);
    await user.click(screen.getByTestId('action-status-op-star'));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('정지(SUSPENDED)');
    expect(dialog).not.toHaveTextContent('여러 회사에 소속');
  });

  it('control — the active-tenant view keeps every action on a company row', () => {
    renderScreen({ isPlatformOperator: true });
    for (const a of ['edit-roles', 'status', 'edit-profile', 'org-scope', 'domain-access', 'unassign']) {
      expect(screen.getByTestId(`action-${a}-op-1`)).toBeInTheDocument();
    }
  });
});

describe("업무 권한 dialog — '*' target + '*' caller (platform mode)", () => {
  async function openStar(user: ReturnType<typeof userEvent.setup>) {
    renderScreen({ isPlatformOperator: true });
    await switchToAllTenants(user);
    await user.click(screen.getByTestId('action-domain-access-op-star'));
    await screen.findByTestId('operator-domain-access-sets-list');
  }

  it('«모든 테넌트» choice offers presets only and sends allTenants: true', async () => {
    const user = userEvent.setup();
    await openStar(user);
    await user.click(screen.getByTestId('operator-domain-access-add'));
    await screen.findByTestId('operator-domain-access-grant-select');
    await user.click(screen.getByTestId('operator-domain-access-grant-scope-all-tenants'));

    const select = screen.getByTestId('operator-domain-access-grant-select');
    const values = within(select)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    expect(values).toEqual(['', 'preset-1']); // the tenant set is gone

    await user.selectOptions(select, 'preset-1');
    await user.click(screen.getByTestId('operator-domain-access-grant-next'));
    const dialog = await submitReason(user, 'platform');
    expect(dialog).toHaveTextContent('모든 테넌트에 적용됩니다');

    await waitFor(() =>
      expect(mutationBodies('POST', '/api/operators/op-star/domain-permission-sets')).toHaveLength(1),
    );
    expect(mutationBodies('POST', '/api/operators/op-star/domain-permission-sets')[0]).toEqual({
      setId: 'preset-1',
      reason: 'platform',
      allTenants: true,
    });
  });

  it('«현재 테넌트» choice (the default) offers every set and sends the active tenant', async () => {
    const user = userEvent.setup();
    await openStar(user);
    await user.click(screen.getByTestId('operator-domain-access-add'));
    const select = await screen.findByTestId('operator-domain-access-grant-select');
    expect(screen.getByTestId('operator-domain-access-grant-scope-tenant')).toBeChecked();
    await user.selectOptions(select, 'tenant-set');
    await user.click(screen.getByTestId('operator-domain-access-grant-next'));
    const dialog = await submitReason(user, 'tenant');
    expect(dialog).toHaveTextContent('현재 테넌트에만 적용됩니다');

    await waitFor(() =>
      expect(mutationBodies('POST', '/api/operators/op-star/domain-permission-sets')).toHaveLength(1),
    );
    expect(mutationBodies('POST', '/api/operators/op-star/domain-permission-sets')[0]).toEqual({
      setId: 'tenant-set',
      reason: 'tenant',
    });
  });

  it("revoke is per source tenant: the '*' source sends allTenants: true", async () => {
    const user = userEvent.setup();
    await openStar(user);
    expect(screen.queryByTestId('operator-domain-access-set-preset-1-revoke')).toBeNull();
    await user.click(screen.getByTestId('operator-domain-access-set-preset-1-revoke-all-tenants'));
    const dialog = await submitReason(user, 'cleanup');
    expect(dialog).toHaveTextContent('모든 테넌트 배정을 회수합니다');

    await waitFor(() =>
      expect(
        mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1'),
      ).toHaveLength(1),
    );
    expect(
      mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1')[0],
    ).toEqual({ reason: 'cleanup', allTenants: true });
  });

  it("the T source of the same '*' target sends the active tenant", async () => {
    const user = userEvent.setup();
    await openStar(user);
    await user.click(screen.getByTestId('operator-domain-access-set-preset-1-revoke-tenant'));
    const dialog = await submitReason(user, 'cleanup');
    expect(dialog).toHaveTextContent('현재 테넌트 배정을 회수합니다');

    await waitFor(() =>
      expect(
        mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1'),
      ).toHaveLength(1),
    );
    expect(
      mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1')[0],
    ).toEqual({ reason: 'cleanup' });
  });
});

describe('업무 권한 dialog — every other case is unchanged (active tenant, no choice)', () => {
  it("company target + '*' caller: no scope choice, one revoke per set, body without allTenants", async () => {
    const user = userEvent.setup();
    renderScreen({ isPlatformOperator: true });
    await user.click(screen.getByTestId('action-domain-access-op-1'));
    await screen.findByTestId('operator-domain-access-sets-list');
    expect(screen.queryByTestId('operator-domain-access-set-s1-revoke-all-tenants')).toBeNull();

    await user.click(screen.getByTestId('operator-domain-access-add'));
    await screen.findByTestId('operator-domain-access-grant-select');
    expect(screen.queryByTestId('operator-domain-access-grant-scope')).toBeNull();
    await user.click(screen.getByTestId('operator-domain-access-grant-select'));
    await user.selectOptions(screen.getByTestId('operator-domain-access-grant-select'), 'tenant-set');
    await user.click(screen.getByTestId('operator-domain-access-grant-next'));
    const dialog = await submitReason(user, 'r');
    expect(dialog).not.toHaveTextContent('적용됩니다');
    await waitFor(() =>
      expect(mutationBodies('POST', '/api/operators/op-1/domain-permission-sets')).toHaveLength(1),
    );
    expect(mutationBodies('POST', '/api/operators/op-1/domain-permission-sets')[0]).toEqual({
      setId: 'tenant-set',
      reason: 'r',
    });
  });

  it("'*' target + NON-'*' caller: no scope choice, the single per-set revoke, active tenant", async () => {
    const user = userEvent.setup();
    // A '*' row cannot reach a non-platform caller's list in production
    // (AC-0 ②); seeded here to pin the dialog's own gate on the caller.
    renderScreen({ isPlatformOperator: false, initial: page([STAR_OP]) });
    await user.click(screen.getByTestId('action-domain-access-op-star'));
    await screen.findByTestId('operator-domain-access-sets-list');
    expect(screen.queryByTestId('operator-domain-access-set-preset-1-revoke-all-tenants')).toBeNull();
    await user.click(screen.getByTestId('operator-domain-access-set-preset-1-revoke'));
    await submitReason(user, 'r');
    await waitFor(() =>
      expect(
        mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1'),
      ).toHaveLength(1),
    );
    expect(
      mutationBodies('DELETE', '/api/operators/op-star/domain-permission-sets/preset-1')[0],
    ).toEqual({ reason: 'r' });

    await user.click(screen.getByTestId('operator-domain-access-add'));
    await screen.findByTestId('operator-domain-access-grant-select');
    expect(screen.queryByTestId('operator-domain-access-grant-scope')).toBeNull();
  });
});
