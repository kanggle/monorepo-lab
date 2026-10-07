import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GroupMemberDialog } from '@/features/operator-groups/components/GroupMemberDialog';

/**
 * TASK-PC-FE-317 — the operator-group «멤버 추가» picker (AC-1 through AC-5).
 * `apiClient.get` is mocked so the candidate list is asserted without a real
 * backend (mirrors `OperatorGroupsScreen.test.tsx` conventions).
 */

const get = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
  },
}));

function page(
  content: Array<{
    operatorId: string;
    email: string;
    displayName: string;
    status: string;
  }>,
  opts: { page?: number; totalPages?: number } = {},
) {
  return {
    content: content.map((c) => ({ ...c, roles: [], createdAt: '2026-01-01T00:00:00Z' })),
    totalElements: content.length,
    page: opts.page ?? 0,
    size: 100,
    totalPages: opts.totalPages ?? 1,
  };
}

function renderDialog(
  props: Partial<React.ComponentProps<typeof GroupMemberDialog>> = {},
) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <QueryClientProvider client={qc}>
      <GroupMemberDialog
        groupName="테스트 그룹"
        groupTenantId="acme-corp"
        existingMemberIds={[]}
        pending={false}
        error={null}
        onConfirm={onConfirm}
        onCancel={onCancel}
        {...props}
      />
    </QueryClientProvider>,
  );
  return { onConfirm, onCancel };
}

beforeEach(() => {
  get.mockReset();
});

describe('GroupMemberDialog — AC-1 the GROUP tenant is queried (not the active tenant)', () => {
  it('requests /api/operators with the groupTenantId prop', async () => {
    get.mockResolvedValue(page([]));
    renderDialog({ groupTenantId: 'other-tenant-than-active' });

    await waitFor(() => expect(get).toHaveBeenCalled());
    const url = get.mock.calls[0][0] as string;
    expect(url).toContain('tenantId=other-tenant-than-active');
  });
});

describe('GroupMemberDialog — AC-2 client-side filter + 0-result copy', () => {
  it('filters by email/displayName case-insensitively and shows the empty-state message', async () => {
    get.mockResolvedValue(
      page([
        { operatorId: 'op-1', email: 'alice@x.com', displayName: 'Alice', status: 'ACTIVE' },
        { operatorId: 'op-2', email: 'bob@x.com', displayName: 'Bob', status: 'ACTIVE' },
      ]),
    );
    renderDialog();
    await waitFor(() =>
      expect(screen.getByTestId('group-member-candidates-list')).toBeInTheDocument(),
    );

    const user = userEvent.setup();
    await user.type(screen.getByTestId('group-member-search-input'), 'ALICE');
    expect(screen.getByTestId('group-member-candidate-op-1')).toBeInTheDocument();
    expect(screen.queryByTestId('group-member-candidate-op-2')).not.toBeInTheDocument();

    await user.clear(screen.getByTestId('group-member-search-input'));
    await user.type(screen.getByTestId('group-member-search-input'), 'no-such-operator');
    expect(screen.getByTestId('group-member-candidates-empty')).toHaveTextContent(
      '일치하는 운영자 없음 — 운영자 관리에서 먼저 등록',
    );
  });
});

describe('GroupMemberDialog — AC-3 existing members are excluded from selection', () => {
  it('disables the existing member row so it can never become the selection', async () => {
    get.mockResolvedValue(
      page([{ operatorId: 'op-existing', email: 'exist@x.com', displayName: 'Exist', status: 'ACTIVE' }]),
    );
    renderDialog({ existingMemberIds: ['op-existing'] });

    await waitFor(() =>
      expect(screen.getByTestId('group-member-candidate-op-existing')).toBeInTheDocument(),
    );
    const row = screen.getByTestId('group-member-candidate-op-existing');
    expect(row).toBeDisabled();
    expect(screen.getByTestId('group-member-next')).toBeDisabled();

    const user = userEvent.setup();
    await user.click(row);
    expect(screen.getByTestId('group-member-next')).toBeDisabled();
  });
});

describe('GroupMemberDialog — AC-4 confirm posts the chosen operatorId', () => {
  it('selects a candidate, confirms a reason, and calls onConfirm with that operatorId', async () => {
    get.mockResolvedValue(
      page([{ operatorId: 'op-pick', email: 'pick@x.com', displayName: 'Pick Me', status: 'ACTIVE' }]),
    );
    const { onConfirm } = renderDialog();

    await waitFor(() =>
      expect(screen.getByTestId('group-member-candidate-op-pick')).toBeInTheDocument(),
    );
    const user = userEvent.setup();
    await user.click(screen.getByTestId('group-member-candidate-op-pick'));
    await user.click(screen.getByTestId('group-member-next'));
    await user.type(screen.getByTestId('group-reason-input'), '승인된 신규 지원 인력');
    await user.click(screen.getByTestId('group-reason-submit'));

    expect(onConfirm).toHaveBeenCalledWith('op-pick', '승인된 신규 지원 인력');
  });
});

describe('GroupMemberDialog — AC-5 fallback to raw UUID input on list failure', () => {
  it('falls back to the operator-ID text input when the candidate list fails (network/403/503)', async () => {
    get.mockRejectedValue(new Error('network down'));
    const { onConfirm } = renderDialog();

    await waitFor(() =>
      expect(screen.getByTestId('group-member-picker-fallback-notice')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('group-member-search-input')).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.type(screen.getByTestId('group-member-operator-input'), 'raw-uuid-123');
    await user.click(screen.getByTestId('group-member-next'));
    await user.type(screen.getByTestId('group-reason-input'), '폴백 경로로 추가');
    await user.click(screen.getByTestId('group-reason-submit'));

    expect(onConfirm).toHaveBeenCalledWith('raw-uuid-123', '폴백 경로로 추가');
  });
});

describe('GroupMemberDialog — pagination past 100 (Edge Case: no silent truncation)', () => {
  it('shows "더 보기" when totalPages > 1 and appends the next page on click', async () => {
    get
      .mockResolvedValueOnce(
        page([{ operatorId: 'op-1', email: 'a@x.com', displayName: 'A', status: 'ACTIVE' }], {
          page: 0,
          totalPages: 2,
        }),
      )
      .mockResolvedValueOnce(
        page([{ operatorId: 'op-2', email: 'b@x.com', displayName: 'B', status: 'ACTIVE' }], {
          page: 1,
          totalPages: 2,
        }),
      );
    renderDialog();

    await waitFor(() =>
      expect(screen.getByTestId('group-member-candidate-op-1')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('group-member-load-more')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByTestId('group-member-load-more'));

    await waitFor(() =>
      expect(screen.getByTestId('group-member-candidate-op-2')).toBeInTheDocument(),
    );
    expect(get).toHaveBeenCalledTimes(2);
  });
});
