import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';

const listFixture = {
  content: [
    {
      operatorId: 'op-self',
      email: 'self@example.com',
      displayName: 'Self User',
      status: 'ACTIVE' as const,
      roles: ['SUPER_ADMIN' as const],
      totpEnrolled: true,
      lastLoginAt: '2026-04-24T09:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
    },
    {
      operatorId: 'op-other',
      email: 'other@example.com',
      displayName: 'Other User',
      status: 'SUSPENDED' as const,
      roles: ['SUPPORT_LOCK' as const],
      totpEnrolled: false,
      lastLoginAt: null,
      createdAt: '2026-02-01T00:00:00Z',
    },
  ],
  totalElements: 2,
  page: 0,
  size: 20,
  totalPages: 1,
};

vi.mock('@/features/operators/hooks/useOperatorList', () => ({
  useOperatorList: () => ({ data: listFixture, isLoading: false, isError: false }),
}));

vi.mock('@/features/auth/hooks/useOperatorSession', () => ({
  useOperatorSession: () => ({
    data: { operatorId: 'op-self', email: 'self@example.com', roles: ['SUPER_ADMIN'] },
    isLoading: false,
    isError: false,
  }),
}));

import { OperatorList } from '@/features/operators/components/OperatorList';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

describe('OperatorList', () => {
  it('renders every operator row with email, roles, and status', async () => {
    render(wrap(<OperatorList />));
    await waitFor(() => expect(screen.getByText('self@example.com')).toBeInTheDocument());
    expect(screen.getByText('other@example.com')).toBeInTheDocument();
    expect(screen.getByText('SUPER_ADMIN')).toBeInTheDocument();
    expect(screen.getByText('SUPPORT_LOCK')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('SUSPENDED')).toBeInTheDocument();
  });

  it('disables the 정지 button for the current logged-in operator', async () => {
    render(wrap(<OperatorList />));
    await waitFor(() => expect(screen.getByText('self@example.com')).toBeInTheDocument());

    // Self row: ACTIVE + same operatorId → button must be "정지" and disabled.
    const suspendButtons = screen.getAllByRole('button', { name: '정지' });
    expect(suspendButtons.length).toBeGreaterThan(0);
    // The self row has the 정지 button, which must be disabled.
    expect(suspendButtons[0]).toBeDisabled();

    // The other suspended row offers 활성화, which must remain enabled.
    const activateBtn = screen.getByRole('button', { name: '활성화' });
    expect(activateBtn).not.toBeDisabled();
  });

  it('renders "등록된 운영자가 없습니다" when list is empty', async () => {
    vi.resetModules();
    vi.doMock('@/features/operators/hooks/useOperatorList', () => ({
      useOperatorList: () => ({
        data: { content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 },
        isLoading: false,
        isError: false,
      }),
    }));
    vi.doMock('@/features/auth/hooks/useOperatorSession', () => ({
      useOperatorSession: () => ({
        data: { operatorId: 'op-self', email: 'self@example.com', roles: ['SUPER_ADMIN'] },
        isLoading: false,
        isError: false,
      }),
    }));
    const { OperatorList: OperatorListReloaded } = await import(
      '@/features/operators/components/OperatorList'
    );
    render(wrap(<OperatorListReloaded />));
    expect(await screen.findByText('등록된 운영자가 없습니다.')).toBeInTheDocument();
  });
});
