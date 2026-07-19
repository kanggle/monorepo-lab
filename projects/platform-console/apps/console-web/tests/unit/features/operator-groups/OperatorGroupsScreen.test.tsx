import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { OperatorGroupsScreen } from '@/features/operator-groups';

/**
 * `OperatorGroupsScreen` — the 운영자 그룹 master-detail surface
 * (TASK-PC-FE-250 / ADR-MONO-046). `apiClient` is mocked so the create mutation
 * is asserted without a real backend (mirrors `TenantsScreen.test.tsx`
 * conventions: draft → reason+confirm dialog → mutate). A seeded render fires no
 * extra list GET.
 */

const get = vi.fn();
const post = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

const INITIAL_PAGE = {
  items: [
    {
      groupId: 'g-1',
      tenantId: 'acme-corp',
      name: '물류 지원팀',
      description: 'WMS 출고 지원 스쿼드',
      memberCount: 5,
      grantCount: 3,
      createdAt: '2026-07-19T09:00:00Z',
      updatedAt: '2026-07-19T09:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function renderScreen(grantableRoles: string[] | null = null) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <OperatorGroupsScreen initial={INITIAL_PAGE} grantableRoles={grantableRoles} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('OperatorGroupsScreen — render (seeded)', () => {
  it('renders the seeded list without an extra fetch', () => {
    renderScreen();
    expect(screen.getByTestId('groups-table')).toBeInTheDocument();
    expect(screen.getByTestId('groups-row-g-1')).toBeInTheDocument();
    expect(screen.getByText('물류 지원팀')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('shows the detail panel with member + grant sections on row select', async () => {
    // detail select triggers member + grant reads.
    get.mockResolvedValue({ items: [] });
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('groups-row-select-g-1'));
    expect(screen.getByTestId('group-detail')).toBeInTheDocument();
    expect(screen.getByTestId('group-member-add')).toBeInTheDocument();
    expect(screen.getByTestId('group-grant-add')).toBeInTheDocument();
  });
});

describe('OperatorGroupsScreen — create', () => {
  it('keeps submit disabled for a platform-global tenant (`*`, fail-closed pre-gate)', async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('group-form-tenant-id'), '*');
    await user.type(screen.getByTestId('group-form-name'), '새 그룹');

    expect(screen.getByTestId('group-create-submit')).toBeDisabled();
    expect(screen.queryByTestId('group-reason-submit')).not.toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('opens the reason+confirm dialog on a valid draft, then POSTs on confirm', async () => {
    post.mockResolvedValue({
      groupId: 'g-2',
      tenantId: 'acme-corp',
      name: '신규 그룹',
      description: null,
      memberCount: 0,
      grantCount: 0,
      createdAt: '2026-07-19T10:00:00Z',
      updatedAt: '2026-07-19T10:00:00Z',
    });
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('group-form-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('group-form-name'), '신규 그룹');
    await user.click(screen.getByTestId('group-create-submit'));

    const submit = screen.getByTestId('group-reason-submit');
    expect(submit).toBeDisabled(); // no reason yet

    await user.type(screen.getByTestId('group-reason-input'), '신규 스쿼드 편성');
    expect(submit).not.toBeDisabled();
    await user.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/api/groups',
        expect.objectContaining({
          tenantId: 'acme-corp',
          name: '신규 그룹',
          reason: '신규 스쿼드 편성',
          idempotencyKey: expect.any(String),
        }),
      ),
    );
  });

  it('surfaces a 409 GROUP_NAME_CONFLICT inline without a fake success', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(409, 'GROUP_NAME_CONFLICT', 'exists'));
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('group-form-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('group-form-name'), '물류 지원팀');
    await user.click(screen.getByTestId('group-create-submit'));
    await user.type(screen.getByTestId('group-reason-input'), '재등록 시도');
    await user.click(screen.getByTestId('group-reason-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('group-reason-error')).toHaveTextContent(
        '동일한 이름의 그룹이 이미',
      ),
    );
    // Dialog stays open on failure — no navigation / fake success.
    expect(screen.getByTestId('group-reason-submit')).toBeInTheDocument();
  });
});

describe('OperatorGroupsScreen — grant no-escalation pre-filter (AC-4)', () => {
  it('offers only grantable roles in the group-grant picker', async () => {
    get.mockResolvedValue({ items: [] });
    const user = userEvent.setup();
    renderScreen(['SUPPORT_LOCK']);

    await user.click(screen.getByTestId('groups-row-select-g-1'));
    await user.click(screen.getByTestId('group-grant-add'));

    const roleSelect = screen.getByTestId(
      'group-grant-role-select',
    ) as HTMLSelectElement;
    const values = Array.from(roleSelect.options).map((o) => o.value);
    // The empty "(부여 안 함)" option + the single grantable role — never a
    // non-grantable role, and never SUPER_ADMIN.
    expect(values).toContain('SUPPORT_LOCK');
    expect(values).not.toContain('TENANT_ADMIN');
    expect(values).not.toContain('SUPER_ADMIN');
  });
});
