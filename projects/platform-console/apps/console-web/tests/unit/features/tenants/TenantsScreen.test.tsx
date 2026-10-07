import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantsScreen } from '@/features/tenants';

/**
 * `TenantsScreen` — the tenant-management list + create surface
 * (TASK-PC-FE-226). `apiClient` is mocked so the create mutation is asserted
 * without a real backend (mirrors `SubscriptionsScreen.test.tsx` /
 * `CreateOperatorForm.test.tsx` conventions: draft → reason+confirm dialog →
 * mutate).
 */

const get = vi.fn();
const post = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    patch: vi.fn(),
  },
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const INITIAL_PAGE = {
  items: [
    {
      tenantId: 'fan-platform',
      displayName: 'Fan Platform',
      tenantType: 'B2C_CONSUMER',
      status: 'ACTIVE',
      createdAt: '2026-04-01T00:00:00Z',
      updatedAt: '2026-04-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function renderScreen(
  orgNodeOptions?: { orgNodeId: string; name: string }[] | null,
) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TenantsScreen initial={INITIAL_PAGE} orgNodeOptions={orgNodeOptions} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('TenantsScreen — render (seeded)', () => {
  it('renders the seeded list without an extra fetch', () => {
    renderScreen();
    expect(screen.getByTestId('tenants-table')).toBeInTheDocument();
    expect(screen.getByTestId('tenants-row-fan-platform')).toBeInTheDocument();
    expect(screen.getByText('Fan Platform')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });
});

describe('TenantsScreen — create', () => {
  it('keeps submit disabled for an invalid tenantId format (fail-closed pre-gate)', async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'Bad_ID!');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'New Tenant');

    expect(screen.getByTestId('tenant-create-submit')).toBeDisabled();
    // A disabled submit never fires — the confirm dialog never opens.
    expect(screen.queryByTestId('tenant-confirm-submit')).not.toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('opens the reason+confirm dialog on a valid draft, then POSTs on confirm', async () => {
    post.mockResolvedValue({
      tenantId: 'new-tenant',
      displayName: 'New Tenant',
      tenantType: 'B2B_ENTERPRISE',
      status: 'ACTIVE',
      createdAt: '2026-07-08T00:00:00Z',
      updatedAt: '2026-07-08T00:00:00Z',
    });
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'new-tenant');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'New Tenant');
    await user.selectOptions(
      screen.getByTestId('tenant-form-tenant-type'),
      'B2B_ENTERPRISE',
    );
    await user.click(screen.getByTestId('tenant-create-submit'));

    const submit = screen.getByTestId('tenant-confirm-submit');
    expect(submit).toBeDisabled(); // no reason yet

    await user.type(screen.getByTestId('tenant-confirm-reason'), '신규 파트너 온보딩');
    expect(submit).not.toBeDisabled();
    await user.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/api/tenants',
        expect.objectContaining({
          tenantId: 'new-tenant',
          displayName: 'New Tenant',
          tenantType: 'B2B_ENTERPRISE',
          reason: '신규 파트너 온보딩',
          idempotencyKey: expect.any(String),
        }),
      ),
    );
  });

  // --- TASK-PC-FE-312 AC-4 — «소속 노드 (선택)» ---------------------------------

  async function fillAndConfirm(
    user: ReturnType<typeof userEvent.setup>,
    pickNode: string | null,
  ) {
    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'new-tenant');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'New Tenant');
    if (pickNode !== null) {
      await user.selectOptions(screen.getByTestId('tenant-form-org-node'), pickNode);
    }
    await user.click(screen.getByTestId('tenant-create-submit'));
    await user.type(screen.getByTestId('tenant-confirm-reason'), '온보딩');
    await user.click(screen.getByTestId('tenant-confirm-submit'));
    await waitFor(() => expect(post).toHaveBeenCalled());
    return post.mock.calls[0][1] as Record<string, unknown>;
  }

  const CREATED = {
    tenantId: 'new-tenant',
    displayName: 'New Tenant',
    tenantType: 'B2C_CONSUMER',
    status: 'ACTIVE',
    createdAt: '2026-10-08T00:00:00Z',
    updatedAt: '2026-10-08T00:00:00Z',
  };
  const NODE_OPTIONS = [
    { orgNodeId: 'node-hq', name: '본사' },
    { orgNodeId: 'node-wms', name: '물류센터' },
  ];

  it('AC-4 — a chosen node rides in the create body as orgNodeId', async () => {
    post.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderScreen(NODE_OPTIONS);

    const select = screen.getByTestId('tenant-form-org-node');
    expect(screen.getByLabelText('소속 노드 (선택)')).toBe(select);
    expect(
      Array.from((select as HTMLSelectElement).options).map((o) => o.textContent),
    ).toEqual(['소속 없음', '본사', '물류센터']);

    const body = await fillAndConfirm(user, 'node-wms');
    expect(body.orgNodeId).toBe('node-wms');
  });

  it('AC-4 (regression) — left empty, the create body has NO orgNodeId key at all', async () => {
    post.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderScreen(NODE_OPTIONS);

    const body = await fillAndConfirm(user, null);
    expect(body).not.toHaveProperty('orgNodeId');
    expect(Object.keys(body).sort()).toEqual(
      ['displayName', 'idempotencyKey', 'reason', 'tenantId', 'tenantType'],
    );
  });

  it('AC-4 — choosing a node then going back to «소속 없음» also leaves the key out', async () => {
    post.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderScreen(NODE_OPTIONS);

    await user.selectOptions(screen.getByTestId('tenant-form-org-node'), 'node-hq');
    const body = await fillAndConfirm(user, '');
    expect(body).not.toHaveProperty('orgNodeId');
  });

  it('the org-node list could not be read → the field still renders (소속 없음 only) and creates unplaced', async () => {
    post.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderScreen(null);

    const select = screen.getByTestId('tenant-form-org-node') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toEqual(['']);
    expect(screen.getByText(/조직 노드 목록을 불러오지 못했습니다/)).toBeInTheDocument();
    const body = await fillAndConfirm(user, null);
    expect(body).not.toHaveProperty('orgNodeId');
  });

  it('404 ORG_NODE_NOT_FOUND on a create says the node could not be used — and that the tenant may exist unplaced', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(404, 'ORG_NODE_NOT_FOUND', 'no'));
    const user = userEvent.setup();
    renderScreen(NODE_OPTIONS);

    await fillAndConfirm(user, 'node-hq');
    await waitFor(() =>
      expect(screen.getByTestId('tenant-confirm-error')).toHaveTextContent(
        '선택한 소속 노드에 둘 수 없습니다',
      ),
    );
    expect(screen.getByTestId('tenant-confirm-error')).toHaveTextContent(
      '무소속으로 이미 만들어졌을 수 있으니',
    );
  });

  it('surfaces a 409 TENANT_ALREADY_EXISTS inline without a fake success', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(409, 'TENANT_ALREADY_EXISTS', 'exists'));
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'fan-platform');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'Fan Platform 2');
    await user.click(screen.getByTestId('tenant-create-submit'));
    await user.type(screen.getByTestId('tenant-confirm-reason'), '재등록 시도');
    await user.click(screen.getByTestId('tenant-confirm-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('tenant-confirm-error')).toHaveTextContent(
        '이미 사용 중인 조직 ID',
      ),
    );
    // Dialog stays open on failure — no navigation / fake success.
    expect(screen.getByTestId('tenant-confirm-submit')).toBeInTheDocument();
  });
});
