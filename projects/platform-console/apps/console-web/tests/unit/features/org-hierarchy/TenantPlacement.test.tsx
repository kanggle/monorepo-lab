import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/errors';
import { OrgNodeDetail } from '@/features/org-hierarchy/components/OrgNodeDetail';

/**
 * TASK-PC-FE-312 — 조직 계층 노드 상세의 «소속 테넌트» 에서 테넌트 추가 · 옮기기 ·
 * 빼기 (`TASK-BE-625`, `ADR-MONO-047` § 개정 2026-10-07). The REAL
 * `OrgNodeDetail` is rendered with React Query; only the same-origin
 * `apiClient` is mocked, routed by URL, so every assertion is on rendered DOM
 * driven by the real hooks.
 *
 *   AC-1 — add / move / detach work and the tenant list refreshes after.
 *   AC-2 — moving under a ceiling names the lost domains; no lost domains →
 *          that paragraph is absent (control).
 *   AC-3 — any 404 → «관리할 권한이 없습니다» (no existence guess).
 *
 * The fixture: two reach roots — 본사 A (UNBOUNDED, shown) and 계열사 B
 * (BOUNDED wms). Moving `acme` from A to B loses finance + erp.
 */

const get = vi.fn();
const put = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    put: (...a: unknown[]) => put(...a),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

const NODE_A = {
  orgNodeId: 'n-a',
  parentId: null,
  name: '본사 A',
  depth: 1,
  ceiling: { mode: 'UNBOUNDED' as const },
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
};
const NODE_B = {
  orgNodeId: 'n-b',
  parentId: null,
  name: '계열사 B',
  depth: 1,
  ceiling: { mode: 'BOUNDED' as const, domains: ['wms'] },
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
};
const NODES = [NODE_A, NODE_B];

interface World {
  tenantsOf: Record<string, string[]>;
  /** `GET /api/tenants` — null ⇒ 403 (not SUPER_ADMIN). */
  allTenants: string[] | null;
  /** preview key `${tenantId}|${to ?? ''}` → effect, or an ApiError to throw. */
  previews: Record<string, unknown>;
  /** pending preview — never resolves (confirm must stay blocked). */
  hangPreview: boolean;
}

let world: World;

function effect(
  tenantId: string,
  from: string | null,
  to: string | null,
  lost: string[],
  gained: string[] = [],
) {
  return {
    tenantId,
    fromOrgNodeId: from,
    toOrgNodeId: to,
    domainsBefore: ['wms', 'finance', 'erp'],
    domainsAfter: ['wms', 'finance', 'erp'].filter((d) => !lost.includes(d)),
    lostDomains: lost,
    gainedDomains: gained,
  };
}

function routeGet(path: string): Promise<unknown> {
  const url = new URL(path, 'http://console.local');
  const p = url.pathname;
  let m = p.match(/^\/api\/org-nodes\/([^/]+)\/tenants$/);
  if (m) {
    return Promise.resolve({ tenantIds: [...(world.tenantsOf[decodeURIComponent(m[1])] ?? [])] });
  }
  if (/^\/api\/org-nodes\/[^/]+\/admins$/.test(p)) return Promise.resolve({ items: [] });
  if (p === '/api/tenants') {
    if (world.allTenants === null) {
      return Promise.reject(new ApiError(403, 'TENANT_SCOPE_DENIED', 'no'));
    }
    return Promise.resolve({
      items: world.allTenants.map((tenantId) => ({ tenantId })),
      page: 0,
      size: 100,
      totalElements: world.allTenants.length,
      totalPages: 1,
    });
  }
  m = p.match(/^\/api\/tenants\/([^/]+)\/org-node\/preview$/);
  if (m) {
    if (world.hangPreview) return new Promise(() => {});
    const key = `${decodeURIComponent(m[1])}|${url.searchParams.get('orgNodeId') ?? ''}`;
    const found = world.previews[key];
    if (found instanceof Error) return Promise.reject(found);
    if (found) return Promise.resolve(found);
    return Promise.reject(new Error(`no preview fixture for ${key}`));
  }
  return Promise.reject(new Error(`unrouted GET ${path}`));
}

/** The write moves the tenant in the fake world, like the server would. */
function routePut(path: string, body: { orgNodeId: string | null }): Promise<unknown> {
  const m = path.match(/^\/api\/tenants\/([^/]+)\/org-node$/);
  if (!m) return Promise.reject(new Error(`unrouted PUT ${path}`));
  const tenantId = decodeURIComponent(m[1]);
  let from: string | null = null;
  for (const [id, list] of Object.entries(world.tenantsOf)) {
    if (list.includes(tenantId)) {
      from = id;
      world.tenantsOf[id] = list.filter((t) => t !== tenantId);
    }
  }
  if (body.orgNodeId !== null) {
    world.tenantsOf[body.orgNodeId] = [...(world.tenantsOf[body.orgNodeId] ?? []), tenantId];
  }
  return Promise.resolve({ ...effect(tenantId, from, body.orgNodeId, []), changed: true });
}

function renderDetail() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <OrgNodeDetail node={NODE_A} nodes={NODES} grantableRoles={null} />
    </QueryClientProvider>,
  );
}

async function listedTenants(): Promise<string[]> {
  const list = await screen.findByTestId('org-node-tenants-list');
  return within(list)
    .getAllByRole('listitem')
    .map((li) => li.querySelector('span.font-mono')?.textContent ?? '');
}

async function confirmWithReason(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByTestId('org-reason-input'), '조직 개편');
  await user.click(screen.getByTestId('org-reason-submit'));
}

beforeEach(() => {
  get.mockReset();
  put.mockReset();
  world = {
    tenantsOf: { 'n-a': ['acme', 'globex'], 'n-b': ['initech'] },
    allTenants: ['acme', 'globex', 'initech', 'umbrella'],
    previews: {
      'acme|n-b': effect('acme', 'n-a', 'n-b', ['finance', 'erp']),
      'globex|': effect('globex', 'n-a', null, []),
      'initech|n-a': effect('initech', 'n-b', 'n-a', [], ['finance']),
      'umbrella|n-a': effect('umbrella', null, 'n-a', []),
    },
    hangPreview: false,
  };
  get.mockImplementation((path: string) => routeGet(path));
  put.mockImplementation((path: string, body: { orgNodeId: string | null }) =>
    routePut(path, body),
  );
});

// ---------------------------------------------------------------------------
// AC-1 — add / move / detach, then the list refreshes
// ---------------------------------------------------------------------------

describe('TASK-PC-FE-312 AC-1 — add · move · detach on the node detail', () => {
  it('add: picks from tenants in reach (minus this subtree), writes, and the list shows it', async () => {
    const user = userEvent.setup();
    renderDetail();
    expect(await listedTenants()).toEqual(['acme', 'globex']);

    await user.click(screen.getByTestId('org-node-tenant-add'));
    const select = (await screen.findByTestId('org-node-tenant-add-select')) as HTMLSelectElement;
    // reach roots' tenants ∪ GET /api/tenants, minus what is already under 본사 A
    expect(Array.from(select.options).map((o) => o.value)).toEqual(['', 'initech', 'umbrella']);

    await user.selectOptions(select, 'umbrella');
    await user.click(screen.getByTestId('org-node-tenant-add-next'));
    await screen.findByTestId('org-placement-route');
    await confirmWithReason(user);

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith('/api/tenants/umbrella/org-node', {
        orgNodeId: 'n-a',
        reason: '조직 개편',
      }),
    );
    await waitFor(async () =>
      expect(await listedTenants()).toEqual(['acme', 'globex', 'umbrella']),
    );
    expect(screen.queryByTestId('org-reason-dialog')).not.toBeInTheDocument();
  });

  it('move: chooses another node, writes, and the tenant leaves this node’s list', async () => {
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-move-acme'));
    await user.selectOptions(screen.getByTestId('org-node-tenant-move-select-acme'), 'n-b');
    await user.click(screen.getByTestId('org-node-tenant-move-next-acme'));
    expect(await screen.findByTestId('org-placement-route')).toHaveTextContent(
      '지금 위치: 본사 A → 목적지: 계열사 B',
    );
    await confirmWithReason(user);

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith('/api/tenants/acme/org-node', {
        orgNodeId: 'n-b',
        reason: '조직 개편',
      }),
    );
    await waitFor(async () => expect(await listedTenants()).toEqual(['globex']));
  });

  it('detach: previews WITHOUT orgNodeId, sends the key as null, and the list refreshes', async () => {
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-detach-globex'));
    expect(await screen.findByTestId('org-placement-route')).toHaveTextContent(
      '목적지: 무소속',
    );
    expect(get).toHaveBeenCalledWith('/api/tenants/globex/org-node/preview');
    await confirmWithReason(user);

    await waitFor(() => expect(put).toHaveBeenCalled());
    const [path, body] = put.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe('/api/tenants/globex/org-node');
    expect(body).toHaveProperty('orgNodeId', null);
    await waitFor(async () => expect(await listedTenants()).toEqual(['acme']));
  });

  it('cannot be confirmed until the preview has loaded — even with a reason', async () => {
    world.hangPreview = true;
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-detach-acme'));
    expect(await screen.findByTestId('org-placement-preview-loading')).toBeInTheDocument();
    await user.type(screen.getByTestId('org-reason-input'), '조직 개편');
    expect(screen.getByTestId('org-reason-submit')).toBeDisabled();
    expect(put).not.toHaveBeenCalled();
  });

  it('edge — no tenant in reach to add: the button stays, the panel says so', async () => {
    world.tenantsOf = { 'n-a': ['acme'], 'n-b': [] };
    world.allTenants = null; // not SUPER_ADMIN → 403 on GET /api/tenants, swallowed
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    expect(screen.getByTestId('org-node-tenant-add')).toBeInTheDocument();
    await user.click(screen.getByTestId('org-node-tenant-add'));
    expect(await screen.findByTestId('org-node-tenant-add-empty')).toHaveTextContent(
      '추가할 수 있는 테넌트가 없습니다',
    );
  });

  it('edge — not SUPER_ADMIN: the candidates are the reach roots’ tenants only', async () => {
    world.allTenants = null;
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-add'));
    const select = (await screen.findByTestId('org-node-tenant-add-select')) as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toEqual(['', 'initech']);
  });
});

// ---------------------------------------------------------------------------
// AC-2 — the confirmation names the lost domains (control: none → no paragraph)
// ---------------------------------------------------------------------------

describe('TASK-PC-FE-312 AC-2 — lost domains in the confirmation', () => {
  it('moving under a ceiling names the domains that turn off', async () => {
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-move-acme'));
    await user.selectOptions(screen.getByTestId('org-node-tenant-move-select-acme'), 'n-b');
    await user.click(screen.getByTestId('org-node-tenant-move-next-acme'));

    const lost = await screen.findByTestId('org-placement-lost-domains');
    expect(lost).toHaveTextContent('이동하면 이 도메인들이 꺼집니다: finance, erp');
    // it is a confirmation, not a refusal — confirm is reachable with a reason
    await user.type(screen.getByTestId('org-reason-input'), '조직 개편');
    expect(screen.getByTestId('org-reason-submit')).not.toBeDisabled();
  });

  it('control — nothing lost: the paragraph is absent', async () => {
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-detach-globex'));
    await screen.findByTestId('org-placement-route');
    expect(screen.queryByTestId('org-placement-lost-domains')).not.toBeInTheDocument();
    expect(screen.queryByText(/꺼집니다/)).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// AC-3 — any 404 → permission copy, never an existence guess
// ---------------------------------------------------------------------------

const NO_PERMISSION = '이 노드 또는 테넌트를 관리할 권한이 없습니다';

describe('TASK-PC-FE-312 AC-3 — 404 is «not mine to manage»', () => {
  it('preview 404 TENANT_NOT_FOUND → permission copy, no «not found», confirm blocked', async () => {
    world.previews['globex|'] = new ApiError(404, 'TENANT_NOT_FOUND', 'tenant not found');
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-detach-globex'));
    const err = await screen.findByTestId('org-placement-preview-error');
    expect(err).toHaveTextContent(NO_PERMISSION);
    expect(screen.getByTestId('org-reason-dialog')).not.toHaveTextContent(/찾을 수 없|없는 테넌트|not found/i);
    await user.type(screen.getByTestId('org-reason-input'), '조직 개편');
    expect(screen.getByTestId('org-reason-submit')).toBeDisabled();
  });

  it('write 404 ORG_NODE_NOT_FOUND → the same permission copy in the dialog', async () => {
    put.mockImplementation(() =>
      Promise.reject(new ApiError(404, 'ORG_NODE_NOT_FOUND', 'org node not found')),
    );
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();

    await user.click(screen.getByTestId('org-node-tenant-move-acme'));
    await user.selectOptions(screen.getByTestId('org-node-tenant-move-select-acme'), 'n-b');
    await user.click(screen.getByTestId('org-node-tenant-move-next-acme'));
    await screen.findByTestId('org-placement-route');
    await confirmWithReason(user);

    const err = await screen.findByTestId('org-reason-error');
    expect(err).toHaveTextContent(NO_PERMISSION);
    expect(err).not.toHaveTextContent(/찾을 수 없|not found/i);
  });

  it('write 409 TENANT_ORG_NODE_CONFLICT → «reload and retry», and the list is re-read', async () => {
    put.mockImplementation(() =>
      Promise.reject(new ApiError(409, 'TENANT_ORG_NODE_CONFLICT', 'conflict')),
    );
    const user = userEvent.setup();
    renderDetail();
    await listedTenants();
    const listReads = () =>
      get.mock.calls.filter((c) => c[0] === '/api/org-nodes/n-a/tenants').length;
    const before = listReads();

    await user.click(screen.getByTestId('org-node-tenant-detach-globex'));
    await screen.findByTestId('org-placement-route');
    await confirmWithReason(user);

    expect(await screen.findByTestId('org-reason-error')).toHaveTextContent(
      '다른 요청이 이 테넌트의 소속을 먼저 바꿨습니다',
    );
    await waitFor(() => expect(listReads()).toBeGreaterThan(before));
  });
});
