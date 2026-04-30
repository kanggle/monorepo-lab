import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { ApiError } from '@/shared/api/errors';
import { runAxe } from '../a11y/axe-helper';

const pushMock = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

const mutateAsyncMock = vi.fn();
const gdprHookState: { isPending: boolean } = { isPending: false };
vi.mock('@/features/accounts/hooks/useGdprDelete', () => ({
  useGdprDelete: () => ({
    mutateAsync: mutateAsyncMock,
    get isPending() {
      return gdprHookState.isPending;
    },
  }),
}));

import { GdprDeleteDialog } from '@/features/accounts/components/GdprDeleteDialog';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

describe('GdprDeleteDialog', () => {
  beforeEach(() => {
    mutateAsyncMock.mockReset();
    pushMock.mockReset();
    gdprHookState.isPending = false;
  });

  it('has no axe violations when open', async () => {
    const { container } = render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });

  it('shows validation error when reason is empty on submit', async () => {
    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(mutateAsyncMock).not.toHaveBeenCalled();
  });

  it('shows validation error when reason is whitespace-only on submit', async () => {
    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), '   ');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(mutateAsyncMock).not.toHaveBeenCalled();
  });

  it('calls mutateAsync with accountId, reason, and empty ticketId when ticketId is omitted', async () => {
    mutateAsyncMock.mockResolvedValue({
      accountId: 'acc-1',
      status: 'DELETED',
      maskedAt: '2026-04-18T10:00:00Z',
      auditId: 'a-1',
    });

    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'gdpr request from user');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    await waitFor(() =>
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        accountId: 'acc-1',
        reason: 'gdpr request from user',
        ticketId: '',
      }),
    );
  });

  it('calls mutateAsync with provided ticketId when entered', async () => {
    mutateAsyncMock.mockResolvedValue({
      accountId: 'acc-1',
      status: 'DELETED',
      maskedAt: '2026-04-18T10:00:00Z',
      auditId: 'a-1',
    });

    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'gdpr request from user');
    await user.type(screen.getByLabelText('티켓 번호 (선택)'), 'TICKET-42');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    await waitFor(() =>
      expect(mutateAsyncMock).toHaveBeenCalledWith({
        accountId: 'acc-1',
        reason: 'gdpr request from user',
        ticketId: 'TICKET-42',
      }),
    );
  });

  it('on success: shows success toast and navigates to /accounts', async () => {
    mutateAsyncMock.mockResolvedValue({
      accountId: 'acc-1',
      status: 'DELETED',
      maskedAt: '2026-04-18T10:00:00Z',
      auditId: 'a-1',
    });

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={onOpenChange} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'gdpr request from user');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    await waitFor(() =>
      expect(screen.getByText('계정이 삭제(마스킹)되었습니다.')).toBeInTheDocument(),
    );
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith('/accounts'));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('on ApiError: shows mapped error toast and does not navigate', async () => {
    mutateAsyncMock.mockRejectedValue(new ApiError(403, 'PERMISSION_DENIED', 'denied'));

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={onOpenChange} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'gdpr request from user');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    await waitFor(() =>
      expect(screen.getByText('이 작업을 수행할 권한이 없습니다.')).toBeInTheDocument(),
    );
    expect(pushMock).not.toHaveBeenCalled();
    // Dialog stays open — onOpenChange(false) should not have been called.
    expect(onOpenChange).not.toHaveBeenCalled();
    expect(screen.queryByText('계정이 삭제(마스킹)되었습니다.')).not.toBeInTheDocument();
  });

  it('on non-ApiError: shows fallback "작업에 실패했습니다." toast', async () => {
    mutateAsyncMock.mockRejectedValue(new Error('network down'));

    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'gdpr request from user');
    await user.click(screen.getByRole('button', { name: '영구 삭제' }));

    await waitFor(() =>
      expect(screen.getByText('작업에 실패했습니다.')).toBeInTheDocument(),
    );
    expect(pushMock).not.toHaveBeenCalled();
  });

  it('when isPending is true: submit button shows "처리 중..." and is disabled', async () => {
    gdprHookState.isPending = true;

    render(wrap(<GdprDeleteDialog open onOpenChange={() => {}} accountId="acc-1" />));

    const button = screen.getByRole('button', { name: '처리 중...' });
    expect(button).toBeDisabled();
    expect(screen.queryByRole('button', { name: '영구 삭제' })).not.toBeInTheDocument();
  });

  it('cancel button calls onOpenChange(false)', async () => {
    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<GdprDeleteDialog open onOpenChange={onOpenChange} accountId="acc-1" />));

    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(mutateAsyncMock).not.toHaveBeenCalled();
  });
});
