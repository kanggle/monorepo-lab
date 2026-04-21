import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import type { NotificationDetail as NotificationDetailType } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/my/notifications',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/notification/api/notification-api');

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
}));

import { getMyNotification } from '@/features/notification/api/notification-api';
import { NotificationDetail } from '@/features/notification/ui/NotificationDetail';

const mockGetMyNotification = vi.mocked(getMyNotification);

const MOCK_NOTIFICATION: NotificationDetailType = {
  notificationId: 'notif-1',
  channel: 'EMAIL',
  subject: '주문이 접수되었습니다',
  body: '주문번호 ORDER-001이 접수되었습니다. 결제가 완료되면 배송이 시작됩니다.',
  status: 'SENT',
  sentAt: '2026-04-01T10:00:00Z',
  createdAt: '2026-04-01T10:00:00Z',
};

describe('NotificationDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 상세 내용이 표시되지 않는다', () => {
    mockGetMyNotification.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><NotificationDetail notificationId="notif-1" /></TestQueryProvider>);

    expect(screen.queryByText('주문이 접수되었습니다')).not.toBeInTheDocument();
  });

  it('알림 상세 내용을 렌더링한다', async () => {
    mockGetMyNotification.mockResolvedValueOnce(MOCK_NOTIFICATION);

    render(<TestQueryProvider><NotificationDetail notificationId="notif-1" /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
    });
    expect(screen.getByText(/주문번호 ORDER-001/)).toBeInTheDocument();
    expect(screen.getByText('이메일')).toBeInTheDocument();
  });

  it('알림 목록으로 돌아가는 링크를 표시한다', async () => {
    mockGetMyNotification.mockResolvedValueOnce(MOCK_NOTIFICATION);

    render(<TestQueryProvider><NotificationDetail notificationId="notif-1" /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
    });

    const backLink = screen.getByText(/알림 목록/);
    expect(backLink.closest('a')).toHaveAttribute('href', '/my/notifications');
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyNotification.mockRejectedValueOnce(new Error('fail'));

    render(<TestQueryProvider><NotificationDetail notificationId="notif-1" /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('알림 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('알림을 찾을 수 없는 경우 적절한 에러 메시지를 표시한다', async () => {
    mockGetMyNotification.mockRejectedValueOnce({ code: 'NOTIFICATION_NOT_FOUND', message: 'Not found', timestamp: '' });

    render(<TestQueryProvider><NotificationDetail notificationId="notif-999" /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('알림을 찾을 수 없습니다.')).toBeInTheDocument();
  });
});
