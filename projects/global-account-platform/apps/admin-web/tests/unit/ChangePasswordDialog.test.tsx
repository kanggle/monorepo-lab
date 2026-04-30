import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { ApiError } from '@/shared/api/errors';

const mutateMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/features/operators/hooks/useChangePassword', () => ({
  useChangePassword: () => ({
    mutateAsync: mutateMock,
    isPending: false,
  }),
}));

import { ChangePasswordDialog } from '@/features/operators/components/ChangePasswordDialog';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

describe('ChangePasswordDialog', () => {
  const onOpenChange = vi.fn();

  beforeEach(() => {
    mutateMock.mockReset();
    onOpenChange.mockReset();
  });

  it('renders three password fields when open', () => {
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));
    expect(screen.getByLabelText('현재 비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('새 비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호 확인')).toBeInTheDocument();
  });

  it('shows error when confirmPassword does not match newPassword', async () => {
    const user = userEvent.setup();
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));

    await user.type(screen.getByLabelText('현재 비밀번호'), 'OldPass1!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPass2@');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'DifferentPass3#');
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() =>
      expect(screen.getByText('비밀번호가 일치하지 않습니다')).toBeInTheDocument(),
    );
    expect(mutateMock).not.toHaveBeenCalled();
  });

  it('shows error when newPassword fails complexity policy', async () => {
    const user = userEvent.setup();
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));

    await user.type(screen.getByLabelText('현재 비밀번호'), 'OldPass1!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'alllower1');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'alllower1');
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() =>
      expect(screen.getByText('대문자·소문자·숫자·특수문자 중 3종 이상 포함하세요')).toBeInTheDocument(),
    );
    expect(mutateMock).not.toHaveBeenCalled();
  });

  it('calls mutateAsync and shows success toast on valid submission', async () => {
    mutateMock.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));

    await user.type(screen.getByLabelText('현재 비밀번호'), 'OldPass1!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPass2@');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'NewPass2@');
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() => expect(mutateMock).toHaveBeenCalledWith({
      currentPassword: 'OldPass1!',
      newPassword: 'NewPass2@',
    }));
    await waitFor(() =>
      expect(screen.getByText('비밀번호가 변경되었습니다.')).toBeInTheDocument(),
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('shows CURRENT_PASSWORD_MISMATCH toast on 400 error', async () => {
    mutateMock.mockRejectedValue(new ApiError(400, 'CURRENT_PASSWORD_MISMATCH', 'mismatch'));
    const user = userEvent.setup();
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));

    await user.type(screen.getByLabelText('현재 비밀번호'), 'WrongPass1!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPass2@');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'NewPass2@');
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() =>
      expect(screen.getByText('현재 비밀번호가 올바르지 않습니다.')).toBeInTheDocument(),
    );
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it('shows 서버 오류 toast on 500 error', async () => {
    mutateMock.mockRejectedValue(new ApiError(500, 'INTERNAL_SERVER_ERROR', 'Internal Server Error'));
    const user = userEvent.setup();
    render(wrap(<ChangePasswordDialog open={true} onOpenChange={onOpenChange} />));

    await user.type(screen.getByLabelText('현재 비밀번호'), 'OldPass1!');
    await user.type(screen.getByLabelText('새 비밀번호'), 'NewPass2@');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'NewPass2@');
    await user.click(screen.getByRole('button', { name: '변경' }));

    await waitFor(() =>
      expect(screen.getByText('서버 오류가 발생했습니다.')).toBeInTheDocument(),
    );
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });
});
