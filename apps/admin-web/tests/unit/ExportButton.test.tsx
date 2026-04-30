import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { ApiError } from '@/shared/api/errors';

const detailFixture = {
  id: 'acc-1',
  email: 'user@example.com',
  status: 'ACTIVE' as const,
  createdAt: '2026-01-01T12:34:56Z',
  lastLoginAt: '2026-04-01T09:00:00Z',
  recentLogins: [],
};

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/features/accounts/hooks/useAccountDetail', () => ({
  useAccountDetail: () => ({ data: detailFixture, isLoading: false, isError: false }),
}));

const mutateAsyncMock = vi.fn();
const exportHookState: { isPending: boolean } = { isPending: false };
vi.mock('@/features/accounts/hooks/useExportAccount', () => ({
  useExportAccount: () => ({
    mutateAsync: mutateAsyncMock,
    get isPending() {
      return exportHookState.isPending;
    },
  }),
}));

import { AccountDetail } from '@/features/accounts/components/AccountDetail';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

describe('AccountDetail export button', () => {
  beforeEach(() => {
    mutateAsyncMock.mockReset();
    exportHookState.isPending = false;
  });

  it('shows 데이터 내보내기 button for SUPPORT_READONLY role', async () => {
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '데이터 내보내기' })).toBeInTheDocument();
  });

  it('shows 데이터 내보내기 button for SUPER_ADMIN role', async () => {
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPER_ADMIN']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '데이터 내보내기' })).toBeInTheDocument();
  });

  it('shows 데이터 내보내기 button for SECURITY_ANALYST role', async () => {
    render(wrap(<AccountDetail accountId="acc-1" roles={['SECURITY_ANALYST']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '데이터 내보내기' })).toBeInTheDocument();
  });

  it('hides 데이터 내보내기 button for SUPPORT_LOCK role', async () => {
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_LOCK']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '데이터 내보내기' })).not.toBeInTheDocument();
  });
});

describe('AccountDetail export button — behavior', () => {
  const originalCreateObjectURL = URL.createObjectURL;
  const originalRevokeObjectURL = URL.revokeObjectURL;

  beforeEach(() => {
    mutateAsyncMock.mockReset();
    exportHookState.isPending = false;
    vi.useFakeTimers({ shouldAdvanceTime: true });
    URL.createObjectURL = vi.fn(() => 'blob:mock-url');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('on success: creates blob URL, clicks anchor, revokes URL asynchronously, and shows success toast', async () => {
    mutateAsyncMock.mockResolvedValue({ accountId: 'acc-1', data: { email: 'user@example.com' } });

    const clickSpy = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag) as HTMLElement;
      if (tag === 'a') {
        (el as HTMLAnchorElement).click = clickSpy;
      }
      return el;
    });

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: '데이터 내보내기' }));

    await waitFor(() => expect(mutateAsyncMock).toHaveBeenCalledWith({ accountId: 'acc-1' }));
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled());
    expect(clickSpy).toHaveBeenCalled();

    // URL.revokeObjectURL must NOT be called synchronously.
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();

    // Advance timers past setTimeout(100) to trigger revoke.
    vi.advanceTimersByTime(150);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');

    await waitFor(() => expect(screen.getByText('데이터를 내보냈습니다.')).toBeInTheDocument());

    createElementSpy.mockRestore();
  });

  it('download filename follows export-{accountId}-YYYYMMDD.json format', async () => {
    mutateAsyncMock.mockResolvedValue({ accountId: 'acc-1', data: {} });

    let capturedAnchor: HTMLAnchorElement | null = null;
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag) as HTMLElement;
      if (tag === 'a') {
        (el as HTMLAnchorElement).click = vi.fn();
        capturedAnchor = el as HTMLAnchorElement;
      }
      return el;
    });

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: '데이터 내보내기' }));

    await waitFor(() => expect(capturedAnchor).not.toBeNull());
    // Pattern: export-acc-1-YYYYMMDD.json
    expect(capturedAnchor!.download).toMatch(/^export-acc-1-\d{8}\.json$/);

    createElementSpy.mockRestore();
  });

  it('on error (ApiError): shows error toast', async () => {
    mutateAsyncMock.mockRejectedValue(new ApiError(403, 'PERMISSION_DENIED', 'denied'));

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: '데이터 내보내기' }));

    await waitFor(() =>
      expect(screen.getByText('이 작업을 수행할 권한이 없습니다.')).toBeInTheDocument(),
    );
    // Success toast must not appear.
    expect(screen.queryByText('데이터를 내보냈습니다.')).not.toBeInTheDocument();
  });

  it('when isPending is true: button is disabled and shows 처리 중...', async () => {
    exportHookState.isPending = true;

    render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());

    const button = screen.getByRole('button', { name: '처리 중...' });
    expect(button).toBeDisabled();
    expect(screen.queryByRole('button', { name: '데이터 내보내기' })).not.toBeInTheDocument();
  });

  it('on unmount before revoke timer fires: clearTimeout prevents URL.revokeObjectURL', async () => {
    mutateAsyncMock.mockResolvedValue({ accountId: 'acc-1', data: {} });

    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag) as HTMLElement;
      if (tag === 'a') {
        (el as HTMLAnchorElement).click = vi.fn();
      }
      return el;
    });

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { unmount } = render(wrap(<AccountDetail accountId="acc-1" roles={['SUPPORT_READONLY']} />));
    await waitFor(() => expect(screen.getByText('user@example.com')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: '데이터 내보내기' }));

    // Ensure createObjectURL was called (timer has been scheduled).
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled());

    // revokeObjectURL must not yet have been called before the 100ms timer fires.
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();

    // Unmount the component before the 100ms timer fires — useEffect cleanup should clearTimeout.
    unmount();

    // Advance timers past setTimeout(100). Since the timer was cleared, revoke must not run.
    vi.advanceTimersByTime(200);
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();

    createElementSpy.mockRestore();
  });
});
